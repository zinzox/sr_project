import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/notifications/*")
public class NotificationsServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    List<ProductFeaturesRepository.NotificationRecord> rows = ProductFeaturesRepository.getInstance()
        .listNotifications(user.email);

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"notifications\":" + rowsToJson(rows) + "}");
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    String path = request.getPathInfo() == null ? "" : request.getPathInfo();
    if (!"/mark-read".equals(path)) {
      writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Action notifications introuvable.");
      return;
    }

    long id = parseLong(request.getParameter("id"), -1L);
    if (id <= 0) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "id requis.");
      return;
    }

    ProductFeaturesRepository.getInstance().markNotificationRead(id, user.email);
    writeJsonOk(response, "Notification mise a jour.");
  }

  private String rowsToJson(List<ProductFeaturesRepository.NotificationRecord> rows) {
    return "[" + rows.stream().map(n -> "{" +
        "\"id\":" + n.id + "," +
        "\"userEmail\":\"" + escape(n.userEmail) + "\"," +
        "\"type\":\"" + escape(n.type) + "\"," +
        "\"payload\":\"" + escape(n.payloadJson) + "\"," +
        "\"isRead\":" + n.read + "," +
        "\"createdAt\":\"" + escape(n.createdAt) + "\"" +
        "}").collect(Collectors.joining(",")) + "]";
  }

  private SessionUser requireUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
    HttpSession session = request.getSession(false);
    boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    String email = loggedIn ? clean(String.valueOf(session.getAttribute("userEmail"))) : "";

    if (!loggedIn || email.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Connexion requise.");
      return null;
    }

    return new SessionUser(email);
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

    SessionUser(String email) {
      this.email = email;
    }
  }
}
