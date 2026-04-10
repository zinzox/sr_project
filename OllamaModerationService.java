import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;

public final class OllamaModerationService {

  private static final OllamaModerationService INSTANCE = new OllamaModerationService();
  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  private static final String OLLAMA_BASE_URL = envOrDefault("OLLAMA_URL", "http://localhost:11434");
  private static final String OLLAMA_MODEL = envOrDefault("OLLAMA_MODEL", "llama3.1:8b-instruct");
    private static final Set<String> SAFE_GREETINGS = new HashSet<>(Arrays.asList(
      "hello", "hi", "salut", "bonjour", "bonsoir", "cc", "coucou", "yo", "salam", "slm"));
    private static final Set<String> STRONG_INSULT_PATTERNS = new HashSet<>(Arrays.asList(
      "fils de pute", "fdp", "encule", "sale pute", "nique ta mere"));
    private static final Set<String> INSULT_WORDS = new HashSet<>(Arrays.asList(
      "pute", "salope", "connard", "con", "batard", "merde"));

  private OllamaModerationService() {
  }

  public static OllamaModerationService getInstance() {
    return INSTANCE;
  }

  public ModerationResult analyzeMessage(String messageText) {
    String normalized = normalizeForRules(messageText);

    if (isSafeGreetingOnly(normalized)) {
      return ModerationResult.ok(false, "LOW", 0, List.of("SAFE"), "Message neutre (salutation).");
    }

    String prompt = "Analyse ce message et retourne EXACTEMENT ces 5 lignes:\n"
        + "FLAGGED: YES|NO\n"
        + "RISK_SCORE: 0-100\n"
        + "SEVERITY: LOW|MEDIUM|HIGH\n"
        + "CATEGORIES: category1,category2\n"
        + "REASON: phrase courte\n"
        + "Regles importantes:\n"
        + "- Une simple salutation (ex: hello, bonjour, salut) n'est PAS une alerte.\n"
        + "- Les insultes directes (ex: fils de pute) sont au minimum HIGH et risk >= 85.\n"
        + "- Reponds strictement au format demande, sans texte additionnel.\n"
        + "Considere injures, menaces, extorsion, violence, harcelement, illegalite.\n"
        + "Message: " + messageText;

    String content;
    try {
      content = callOllama(prompt);
    } catch (Exception ex) {
      return ModerationResult.error("OLLAMA_UNAVAILABLE", "Moderation IA indisponible: " + ex.getMessage());
    }

    ModerationResult ai = parseModerationLines(content);
    return calibrateWithRules(normalized, ai);
  }

  public String generateWarningEmailText(String messageText, String reason) {
    String prompt = "Redige un avertissement en francais, ton professionnel, court (max 120 mots). "
        + "Explique que le message envoye enfreint les regles de la plateforme, mentionne la raison: "
        + reason + ". Message concerne: " + messageText;

    try {
      String content = callOllama(prompt).trim();
      if (!content.isEmpty()) {
        return content;
      }
    } catch (Exception ignored) {
      // Fallback below.
    }

    return "Bonjour,\n\n"
        + "Votre dernier message a enfreint les regles de la plateforme (" + reason + "). "
        + "Merci de respecter les conditions d'utilisation. En cas de repetition, des sanctions seront appliquees.\n\n"
        + "Equipe Sarbi Rohek";
  }

  private String callOllama(String prompt) throws IOException, InterruptedException {
    String requestBody = "{"
        + "\"model\":\"" + jsonEscape(OLLAMA_MODEL) + "\"," 
        + "\"stream\":false,"
        + "\"messages\":[{\"role\":\"user\",\"content\":\"" + jsonEscape(prompt) + "\"}]"
        + "}";

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(OLLAMA_BASE_URL + "/api/chat"))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(20))
        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
        .build();

    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("HTTP " + response.statusCode() + " depuis Ollama");
    }

    String body = response.body();
    String marker = "\"content\":\"";
    int idx = body.lastIndexOf(marker);
    if (idx < 0) {
      throw new IOException("Reponse Ollama invalide (content absent)");
    }

    int start = idx + marker.length();
    StringBuilder sb = new StringBuilder();
    boolean escaped = false;
    for (int i = start; i < body.length(); i++) {
      char ch = body.charAt(i);
      if (escaped) {
        switch (ch) {
          case 'n':
            sb.append('\n');
            break;
          case 'r':
            sb.append('\r');
            break;
          case 't':
            sb.append('\t');
            break;
          case '\\':
            sb.append('\\');
            break;
          case '"':
            sb.append('"');
            break;
          default:
            sb.append(ch);
            break;
        }
        escaped = false;
        continue;
      }

      if (ch == '\\') {
        escaped = true;
        continue;
      }

      if (ch == '"') {
        break;
      }

      sb.append(ch);
    }

    return sb.toString();
  }

  private ModerationResult parseModerationLines(String content) {
    String[] lines = content.split("\\r?\\n");
    String flaggedLine = "";
    String riskLine = "";
    String severityLine = "";
    String categoriesLine = "";
    String reasonLine = "";

    for (String raw : lines) {
      String line = raw == null ? "" : raw.trim();
      String upper = line.toUpperCase(Locale.ROOT);
      if (upper.startsWith("FLAGGED:")) {
        flaggedLine = line;
      } else if (upper.startsWith("RISK_SCORE:")) {
        riskLine = line;
      } else if (upper.startsWith("SEVERITY:")) {
        severityLine = line;
      } else if (upper.startsWith("CATEGORIES:")) {
        categoriesLine = line;
      } else if (upper.startsWith("REASON:")) {
        reasonLine = line;
      }
    }

    boolean flagged = flaggedLine.toUpperCase(Locale.ROOT).contains("YES");
    int riskScore = parseRiskScore(extractValue(riskLine, "RISK_SCORE:"));
    String severity = extractValue(severityLine, "SEVERITY:").toUpperCase(Locale.ROOT);
    if (!"HIGH".equals(severity) && !"MEDIUM".equals(severity)) {
      if (riskScore >= 80) {
        severity = "HIGH";
      } else if (riskScore >= 45) {
        severity = "MEDIUM";
      } else {
        severity = "LOW";
      }
    }

    String categoriesValue = extractValue(categoriesLine, "CATEGORIES:");
    List<String> categories = new ArrayList<>();
    if (!categoriesValue.isBlank()) {
      for (String c : categoriesValue.split(",")) {
        String clean = c.trim();
        if (!clean.isEmpty()) {
          categories.add(clean);
        }
      }
    }

    String reason = extractValue(reasonLine, "REASON:");
    if (reason.isEmpty()) {
      reason = flagged ? "Contenu potentiellement dangereux" : "RAS";
    }

    return ModerationResult.ok(flagged, severity, riskScore, categories, reason);
  }

  private ModerationResult calibrateWithRules(String normalizedMessage, ModerationResult aiResult) {
    List<String> categories = new ArrayList<>(aiResult.categories);
    boolean flagged = aiResult.flagged;
    String severity = aiResult.severity;
    int riskScore = aiResult.riskScore;
    String reason = aiResult.reason;

    if (containsStrongInsult(normalizedMessage)) {
      flagged = true;
      severity = "HIGH";
      riskScore = Math.max(90, riskScore);
      if (!categories.contains("INSULT")) {
        categories.add("INSULT");
      }
      reason = "Insulte grave detectee.";
      return ModerationResult.ok(flagged, severity, riskScore, categories, reason);
    }

    if (containsInsultWord(normalizedMessage)) {
      flagged = true;
      if (!"HIGH".equals(severity)) {
        severity = "MEDIUM";
      }
      riskScore = Math.max(60, riskScore);
      if (!categories.contains("INSULT")) {
        categories.add("INSULT");
      }
      if (reason == null || reason.isBlank() || "RAS".equalsIgnoreCase(reason)) {
        reason = "Langage injurieux detecte.";
      }
      return ModerationResult.ok(flagged, severity, riskScore, categories, reason);
    }

    if (isSafeGreetingOnly(normalizedMessage)) {
      return ModerationResult.ok(false, "LOW", 0, List.of("SAFE"), "Message neutre (salutation).");
    }

    return aiResult;
  }

  private String normalizeForRules(String value) {
    if (value == null) {
      return "";
    }

    String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim();

    return normalized;
  }

  private boolean isSafeGreetingOnly(String normalized) {
    if (normalized.isBlank()) {
      return true;
    }

    String[] tokens = normalized.split("\\s+");
    if (tokens.length > 3) {
      return false;
    }

    for (String token : tokens) {
      if (!SAFE_GREETINGS.contains(token)) {
        return false;
      }
    }

    return true;
  }

  private boolean containsStrongInsult(String normalized) {
    for (String pattern : STRONG_INSULT_PATTERNS) {
      if (normalized.contains(pattern)) {
        return true;
      }
    }

    return false;
  }

  private boolean containsInsultWord(String normalized) {
    if (normalized.isBlank()) {
      return false;
    }

    String[] tokens = normalized.split("\\s+");
    for (String token : tokens) {
      if (INSULT_WORDS.contains(token)) {
        return true;
      }
    }

    return false;
  }

  private int parseRiskScore(String rawValue) {
    try {
      int parsed = Integer.parseInt(rawValue.replaceAll("[^0-9]", "").trim());
      if (parsed < 0) {
        return 0;
      }
      if (parsed > 100) {
        return 100;
      }
      return parsed;
    } catch (Exception ex) {
      return 0;
    }
  }

  private String extractValue(String line, String prefix) {
    if (line == null) {
      return "";
    }

    String upperPrefix = prefix.toUpperCase(Locale.ROOT);
    String upperLine = line.toUpperCase(Locale.ROOT);
    if (!upperLine.startsWith(upperPrefix)) {
      return "";
    }

    return line.substring(prefix.length()).trim();
  }

  private String jsonEscape(String value) {
    if (value == null) {
      return "";
    }

    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "")
        .replace("\n", "\\n");
  }

  private static String envOrDefault(String key, String defaultValue) {
    String value = System.getenv(key);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }

    return value.trim();
  }

  public static class ModerationResult {
    public final boolean available;
    public final boolean flagged;
    public final String severity;
    public final int riskScore;
    public final List<String> categories;
    public final String reason;
    public final String errorCode;

    private ModerationResult(boolean available, boolean flagged, String severity, int riskScore, List<String> categories,
        String reason, String errorCode) {
      this.available = available;
      this.flagged = flagged;
      this.severity = severity;
      this.riskScore = riskScore;
      this.categories = categories;
      this.reason = reason;
      this.errorCode = errorCode;
    }

    public static ModerationResult ok(boolean flagged, String severity, int riskScore, List<String> categories, String reason) {
      return new ModerationResult(true, flagged, severity, riskScore, categories, reason, "");
    }

    public static ModerationResult error(String errorCode, String reason) {
      return new ModerationResult(false, false, "LOW", 0, List.of("SYSTEM"), reason, errorCode);
    }
  }
}
