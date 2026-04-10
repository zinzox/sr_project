import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet pour gérer les conversations avec le chatbot Ollama.
 * Répond aux questions des clients sur leurs problèmes.
 */
public class ChatbotServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(30))
      .build();

  private static final String OLLAMA_BASE_URL = getOllamaUrl();
  private static final String OLLAMA_MODEL = getOllamaModel();
  private static final String SYSTEM_PROMPT = 
      "Tu es un assistant client francophone pour une plateforme de services. " +
      "Tu aides les clients avec leurs questions et problèmes. " +
      "Sois sympathique, professionnel et concis. " +
      "Si tu ne peux pas résoudre un problème, suggère de contacter le support.";

  private static String getOllamaUrl() {
    String url = System.getenv("OLLAMA_URL");
    return url != null ? url : "http://localhost:11434";
  }

  private static String getOllamaModel() {
    String model = System.getenv("OLLAMA_MODEL");
    return model != null ? model : "qwen3:8b";
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    try {
      // Lecture du corps de la requête
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = request.getReader().readLine()) != null) {
        sb.append(line);
      }
      
      String requestBody = sb.toString();
      String userMessage = extractJsonField(requestBody, "message").trim();
      
      if (userMessage.isEmpty()) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().write("{\"error\":\"Message vide\"}");
        return;
      }

      String botResponse = getChatbotResponse(userMessage);
      
      response.setStatus(HttpServletResponse.SC_OK);
      String jsonResponse = "{\"success\":true,\"message\":\"" + escape(botResponse) + "\"}";
      response.getWriter().write(jsonResponse);

    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getWriter().write("{\"error\":\"Erreur lors du traitement: " + escape(e.getMessage()) + "\"}");
    }
  }

  private String getChatbotResponse(String userMessage) throws Exception {
    // Construire la requête pour Ollama en JSON
    String ollamaRequest = "{"
        + "\"model\":\"" + OLLAMA_MODEL + "\","
        + "\"prompt\":\"" + escape(SYSTEM_PROMPT + "\n\nClient: " + userMessage + "\n\nAssistant:") + "\","
        + "\"stream\":false,"
        + "\"temperature\":0.7"
        + "}";

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(OLLAMA_BASE_URL + "/api/generate"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(ollamaRequest))
        .timeout(Duration.ofSeconds(90))
        .build();

    HttpResponse<String> httpResponse = HTTP_CLIENT.send(httpRequest,
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    if (httpResponse.statusCode() == 200) {
      String responseBody = httpResponse.body();
      String response = extractJsonField(responseBody, "response").trim();
      
      if (response.isEmpty()) {
        return "Je n'ai pas pu générer une réponse. Veuillez réessayer.";
      }
      
      // Nettoyer la réponse
      return response.replaceAll("(?s)Assistant:.*", "").trim();
    } else {
      throw new IOException("Erreur Ollama: " + httpResponse.statusCode());
    }
  }

  /**
   * Extrait une valeur de champ JSON simple (sans support pour JSON imbriqué complexe)
   */
  private static String extractJsonField(String json, String fieldName) {
    String pattern = "\"" + fieldName + "\":\"";
    int startIndex = json.indexOf(pattern);
    if (startIndex == -1) {
      return "";
    }
    
    startIndex += pattern.length();
    int endIndex = json.indexOf("\"", startIndex);
    
    if (endIndex == -1) {
      return "";
    }
    
    return json.substring(startIndex, endIndex)
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\\\", "\\");
  }

  /**
   * Échappe les caractères spéciaux pour JSON
   */
  private static String escape(String value) {
    if (value == null) {
      return "";
    }

    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
  }
}

