import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/dashboard/*")
public class DashboardServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    String path = request.getPathInfo() == null ? "" : request.getPathInfo();
    ProductFeaturesRepository repository = ProductFeaturesRepository.getInstance();

    if ("/client".equals(path)) {
      if (!"CLIENT".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Espace client uniquement.");
        return;
      }
      ProductFeaturesRepository.DashboardSummary d = repository.clientDashboard(user.email);
      writeDashboard(response, d, "activeQuotes", "favorites", "spent");
      return;
    }

    if ("/provider".equals(path)) {
      if (!"PROVIDER".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Espace prestataire uniquement.");
        return;
      }
      ProductFeaturesRepository.DashboardSummary d = repository.providerDashboard(user.email);
      writeDashboard(response, d, "pendingQuotes", "inProgress", "earned");
      return;
    }

    writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Dashboard introuvable.");
  }

  private void writeDashboard(HttpServletResponse response, ProductFeaturesRepository.DashboardSummary d,
      String keyA, String keyB, String keyAmount) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{" +
        "\"" + keyA + "\":" + d.metricA + "," +
        "\"" + keyB + "\":" + d.metricB + "," +
        "\"" + keyAmount + "\":" + String.format(java.util.Locale.US, "%.2f", d.amount) +
        "}");
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
