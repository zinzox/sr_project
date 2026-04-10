import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/quotes/*")
public class QuotesServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    List<ProductFeaturesRepository.QuoteRecord> rows = ProductFeaturesRepository.getInstance()
        .listQuotesForUser(user.email, user.role);

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{" +
        "\"quotes\":" + rowsToJson(rows) +
        "}");
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    String path = request.getPathInfo() == null ? "" : request.getPathInfo();
    ProductFeaturesRepository repository = ProductFeaturesRepository.getInstance();

    if ("/request".equals(path)) {
      if (!"CLIENT".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Action reservee au client.");
        return;
      }

      String providerEmail = clean(request.getParameter("providerEmail"));
      String description = clean(request.getParameter("description"));
      String budget = clean(request.getParameter("budget"));
      String deadlineAt = clean(request.getParameter("deadlineAt"));

      if (providerEmail.isEmpty() || description.isEmpty()) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "providerEmail et description requis.");
        return;
      }

      long id = repository.createQuoteRequest(user.email, providerEmail, description, budget, deadlineAt);
      repository.createNotification(providerEmail, "NEW_QUOTE", "{\"quoteId\":" + id + "}");
      writeJsonOk(response, "Demande de devis envoyee.");
      return;
    }

    if ("/respond".equals(path)) {
      if (!"PROVIDER".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Action reservee au prestataire.");
        return;
      }

      long quoteId = parseLong(request.getParameter("quoteId"), -1L);
      String status = clean(request.getParameter("status"));
      String providerResponse = clean(request.getParameter("providerResponse"));

      if (quoteId <= 0 || status.isEmpty()) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "quoteId et status requis.");
        return;
      }

      repository.respondQuote(quoteId, status, providerResponse);
      writeJsonOk(response, "Reponse au devis enregistree.");
      return;
    }

    writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Action devis introuvable.");
  }

  private String rowsToJson(List<ProductFeaturesRepository.QuoteRecord> rows) {
    return "[" + rows.stream().map(r -> "{" +
        "\"id\":" + r.id + "," +
        "\"clientEmail\":\"" + escape(r.clientEmail) + "\"," +
        "\"providerEmail\":\"" + escape(r.providerEmail) + "\"," +
        "\"description\":\"" + escape(r.description) + "\"," +
        "\"budget\":\"" + escape(r.budget) + "\"," +
        "\"deadlineAt\":\"" + escape(r.deadlineAt) + "\"," +
        "\"status\":\"" + escape(r.status) + "\"," +
        "\"providerResponse\":\"" + escape(r.providerResponse) + "\"," +
        "\"createdAt\":\"" + escape(r.createdAt) + "\"," +
        "\"updatedAt\":\"" + escape(r.updatedAt) + "\"" +
        "}").collect(Collectors.joining(",")) + "]";
  }

  private SessionUser requireUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
    HttpSession session = request.getSession(false);
    boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    String email = loggedIn ? clean(String.valueOf(session.getAttribute("userEmail"))) : "";
    String role = loggedIn ? clean(String.valueOf(session.getAttribute("userRole"))) : "";

    if (!loggedIn || email.isEmpty() || role.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Connexion requise.");
      return null;
    }

    return new SessionUser(email, role);
  }

  private long parseLong(String value, long defaultValue) {
    try {
      return Long.parseLong(clean(value));
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  private void writeJsonOk(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"message\":\"" + escape(message) + "\"}");
  }

  private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"message\":\"" + escape(message) + "\"}");
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
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
