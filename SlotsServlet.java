import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/slots/*")
public class SlotsServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String providerEmail = clean(request.getParameter("providerEmail"));
    if (providerEmail.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "providerEmail requis.");
      return;
    }

    List<ProductFeaturesRepository.SlotRecord> rows = ProductFeaturesRepository.getInstance().listSlots(providerEmail);
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"slots\":" + rowsToJson(rows) + "}");
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

    if ("/create".equals(path)) {
      if (!"PROVIDER".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Action reservee au prestataire.");
        return;
      }

      String startAt = clean(request.getParameter("startAt"));
      String endAt = clean(request.getParameter("endAt"));
      if (startAt.isEmpty() || endAt.isEmpty()) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "startAt et endAt requis.");
        return;
      }

      repository.createPauseWindow(user.email, startAt, endAt);
      writeJsonOk(response, "Intervalle de pause cree.");
      return;
    }

    if ("/delete".equals(path)) {
      if (!"PROVIDER".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Action reservee au prestataire.");
        return;
      }

      long slotId = parseLong(request.getParameter("slotId"), -1L);
      if (slotId <= 0) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "slotId requis.");
        return;
      }

      repository.deleteSlot(slotId, user.email);
      writeJsonOk(response, "Creneau supprime.");
      return;
    }

    if ("/book".equals(path)) {
      if (!"CLIENT".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Action reservee au client.");
        return;
      }

      long slotId = parseLong(request.getParameter("slotId"), -1L);
      if (slotId <= 0) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "slotId requis.");
        return;
      }

      repository.bookSlot(slotId, user.email);
      writeJsonOk(response, "Creneau reserve.");
      return;
    }

    writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Action creneaux introuvable.");
  }

  private String rowsToJson(List<ProductFeaturesRepository.SlotRecord> rows) {
    return "[" + rows.stream().map(s -> "{" +
        "\"id\":" + s.id + "," +
        "\"providerEmail\":\"" + escape(s.providerEmail) + "\"," +
        "\"startAt\":\"" + escape(s.startAt) + "\"," +
        "\"endAt\":\"" + escape(s.endAt) + "\"," +
        "\"status\":\"" + escape(s.status) + "\"," +
        "\"bookedBy\":\"" + escape(s.bookedBy) + "\"," +
        "\"createdAt\":\"" + escape(s.createdAt) + "\"" +
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
