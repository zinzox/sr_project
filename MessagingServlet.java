import java.io.IOException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/messages/*")
public class MessagingServlet extends HttpServlet {

  private static final String ADMIN_CONTACT_EMAIL = "roheksarbi@gmail.com";

  private static final Set<String> BLOCKED_WORDS = new HashSet<>(Arrays.asList(
      "fuck",
      "shit",
      "bitch",
      "putain",
      "merde",
      "con",
      "connard",
      "salope",
      "salaud"));

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    String path = request.getPathInfo() == null ? "" : request.getPathInfo();
    if ("/conversations".equals(path)) {
      try {
        handleConversations(user, response);
      } catch (IOException ex) {
        writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur serveur messages.");
      }
      return;
    }

    if ("/thread".equals(path)) {
      try {
        handleThread(user, request, response);
      } catch (IOException ex) {
        writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur serveur messages.");
      }
      return;
    }

    writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Endpoint messages introuvable.");
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    String path = request.getPathInfo() == null ? "" : request.getPathInfo();
    if (!"/send".equals(path)) {
      writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Endpoint messages introuvable.");
      return;
    }

    String recipientEmail = clean(request.getParameter("recipientEmail")).toLowerCase();
    String message = cleanMessage(request.getParameter("message"));

    if (recipientEmail.isEmpty() || message.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Destinataire et message requis.");
      return;
    }

    if (recipientEmail.equalsIgnoreCase(user.email)) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Impossible d'envoyer un message a soi-meme.");
      return;
    }

    ProviderRepository repository = ProviderRepository.getInstance();
    ProviderRepository.Account sender;
    try {
      sender = repository.findByEmail(user.email);
    } catch (IOException ex) {
      writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur serveur messages.");
      return;
    }

    String senderPhone = sender == null ? "" : safe(sender.phone);

    ModerationRepository moderationRepository = ModerationRepository.getInstance();
    if (moderationRepository.isBlacklisted(user.email, senderPhone)) {
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
          "Votre compte est blacklisté. Contactez l'administrateur: " + ADMIN_CONTACT_EMAIL);
      return;
    }

    if (moderationRepository.isTemporarilyBanned(user.email)) {
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
          "Votre compte est temporairement suspendu (ban 5 minutes). Réessayez plus tard.");
      return;
    }

    if (containsInappropriateContent(message)) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST,
          "Message bloque: contenu inapproprie detecte.");
      return;
    }

    ProviderRepository.Account recipient;
    try {
      recipient = repository.findByEmail(recipientEmail);
    } catch (IOException ex) {
      writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur serveur messages.");
      return;
    }
    if (recipient == null) {
      writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Destinataire introuvable.");
      return;
    }

    OllamaModerationService.ModerationResult moderation =
        OllamaModerationService.getInstance().analyzeMessage(message);
    if (moderation.available && moderation.flagged) {
      String categoriesCsv = moderation.categories.stream().collect(Collectors.joining(","));
      moderationRepository.saveAlert(
          user.email,
          senderPhone,
          recipientEmail,
          message,
          categoriesCsv,
          moderation.severity,
          moderation.riskScore,
          moderation.reason,
          "OLLAMA:" + System.getenv().getOrDefault("OLLAMA_MODEL", "llama3.1:8b-instruct"));

        tryAutoEscalation(moderationRepository, user.email, senderPhone, moderation.riskScore);

      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST,
          "Message bloque par l'IA: " + moderation.reason);
      return;
    }

    if (!moderation.available) {
      moderationRepository.saveAlert(
          user.email,
          senderPhone,
          recipientEmail,
          message,
          "SYSTEM",
          "LOW",
          0,
          moderation.reason,
          "OLLAMA:UNAVAILABLE");
    }

    try {
      repository.saveMessage(user.email, recipientEmail, message);
    } catch (IOException ex) {
      writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur serveur messages.");
      return;
    }

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"message\":\"Message envoye.\"}");
  }

  private void handleConversations(SessionUser user, HttpServletResponse response) throws IOException {
    ProviderRepository repository = ProviderRepository.getInstance();
    List<ProviderRepository.MessageRecord> all = repository.listMessagesForUser(user.email);
    Map<String, ProviderRepository.MessageRecord> latestByCounterpart = new LinkedHashMap<>();

    for (ProviderRepository.MessageRecord msg : all) {
      String counterpart = msg.senderEmail.equalsIgnoreCase(user.email)
          ? msg.recipientEmail
          : msg.senderEmail;

      if (!latestByCounterpart.containsKey(counterpart)) {
        latestByCounterpart.put(counterpart, msg);
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append("[");
    int index = 0;
    for (Map.Entry<String, ProviderRepository.MessageRecord> entry : latestByCounterpart.entrySet()) {
      String counterpartEmail = entry.getKey();
      ProviderRepository.MessageRecord latest = entry.getValue();
      ProviderRepository.Account counterpart = repository.findByEmail(counterpartEmail);

      if (index++ > 0) {
        sb.append(",");
      }

      String counterpartName = counterpart == null
          ? counterpartEmail
          : (safe(counterpart.firstName) + " " + safe(counterpart.lastName)).trim();

      sb.append("{")
          .append("\"counterpartEmail\":\"").append(escape(counterpartEmail)).append("\",")
          .append("\"counterpartName\":\"").append(escape(counterpartName)).append("\",")
          .append("\"counterpartRole\":\"").append(escape(counterpart == null ? "" : counterpart.role)).append("\",")
          .append("\"lastMessage\":\"").append(escape(latest.messageText)).append("\",")
          .append("\"lastCreatedAt\":\"").append(escape(latest.createdAt)).append("\",")
          .append("\"lastFromSelf\":").append(latest.senderEmail.equalsIgnoreCase(user.email))
          .append("}");
    }
    sb.append("]");

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(sb.toString());
  }

  private void handleThread(SessionUser user, HttpServletRequest request, HttpServletResponse response) throws IOException {
    String withEmail = clean(request.getParameter("with")).toLowerCase();
    if (withEmail.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Parametre 'with' requis.");
      return;
    }

    ProviderRepository repository = ProviderRepository.getInstance();
    List<ProviderRepository.MessageRecord> thread = repository.listConversation(user.email, withEmail);

    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < thread.size(); i++) {
      ProviderRepository.MessageRecord msg = thread.get(i);
      if (i > 0) {
        sb.append(",");
      }

      sb.append("{")
          .append("\"senderEmail\":\"").append(escape(msg.senderEmail)).append("\",")
          .append("\"recipientEmail\":\"").append(escape(msg.recipientEmail)).append("\",")
          .append("\"message\":\"").append(escape(msg.messageText)).append("\",")
          .append("\"createdAt\":\"").append(escape(msg.createdAt)).append("\"")
          .append("}");
    }
    sb.append("]");

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(sb.toString());
  }

  private SessionUser requireUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
    HttpSession session = request.getSession(false);
    boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    String email = "";
    String role = "";
    if (loggedIn && session != null) {
      Object emailObj = session.getAttribute("userEmail");
      Object roleObj = session.getAttribute("userRole");
      email = emailObj == null ? "" : clean(String.valueOf(emailObj)).toLowerCase();
      role = roleObj == null ? "" : clean(String.valueOf(roleObj));
    }

    if (!loggedIn || email.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Connexion requise.");
      return null;
    }

    return new SessionUser(email, role);
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private String cleanMessage(String value) {
    if (value == null) {
      return "";
    }

    String normalized = value.replace("\r", "").trim();
    return normalized.length() > 2000 ? normalized.substring(0, 2000) : normalized;
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private boolean containsInappropriateContent(String message) {
    if (message == null || message.isEmpty()) {
      return false;
    }

    String normalized = Normalizer.normalize(message, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim();

    if (normalized.isEmpty()) {
      return false;
    }

    String[] tokens = normalized.split("\\s+");
    for (String token : tokens) {
      if (BLOCKED_WORDS.contains(token)) {
        return true;
      }
    }

    return false;
  }

  private void tryAutoEscalation(ModerationRepository moderationRepository, String senderEmail,
      String senderPhone, int riskScore) throws IOException {
    int recent = moderationRepository.countRecentAlertsForSender(senderEmail, 24);
    if (recent >= 5 || riskScore >= 95) {
      moderationRepository.applyBlacklist(senderEmail, senderPhone,
          "Escalade auto IA: recidive ou risque critique");
      moderationRepository.logAdminAction(
          "AUTO_BLACKLIST",
          senderEmail,
          senderPhone,
          "Escalade automatique declenchee",
          "SYSTEM",
          -1L);
      return;
    }

    if (recent >= 3 || riskScore >= 80) {
      moderationRepository.applyTemporaryBan(senderEmail, senderPhone, 5,
          "Escalade auto IA: plusieurs alertes recentes");
      moderationRepository.logAdminAction(
          "AUTO_TEMP_BAN_5_MIN",
          senderEmail,
          senderPhone,
          "Escalade automatique declenchee",
          "SYSTEM",
          -1L);
    }
  }

  private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"message\":\"" + escape(message) + "\"}");
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }

    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "")
        .replace("\n", "\\n");
  }

  private static class SessionUser {
    final String email;
    final String role;

    SessionUser(String email, String role) {
      this.email = email;
      this.role = role;
    }
  }
}
