import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/favorites/*")
public class FavoritesServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    List<String> favorites = ProductFeaturesRepository.getInstance().listFavorites(user.email);
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{" +
        "\"favorites\":" + toJsonArray(favorites) +
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
    String providerEmail = clean(request.getParameter("providerEmail"));
    if (providerEmail.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "providerEmail requis.");
      return;
    }

    if ("/add".equals(path)) {
      ProductFeaturesRepository.getInstance().addFavorite(user.email, providerEmail);
      writeJsonOk(response, "Ajoute aux favoris.");
      return;
    }

    if ("/remove".equals(path)) {
      ProductFeaturesRepository.getInstance().removeFavorite(user.email, providerEmail);
      writeJsonOk(response, "Retire des favoris.");
      return;
    }

    writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Action favoris introuvable.");
  }

  private String toJsonArray(List<String> values) {
    return "[" + values.stream().map(v -> "\"" + escape(v) + "\"").collect(Collectors.joining(",")) + "]";
  }

  private SessionUser requireUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
    HttpSession session = request.getSession(false);
    boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    String email = loggedIn ? clean(String.valueOf(session.getAttribute("userEmail"))) : "";
    String role = loggedIn ? clean(String.valueOf(session.getAttribute("userRole"))) : "";

    if (!loggedIn || email.isEmpty() || !"CLIENT".equalsIgnoreCase(role)) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Connexion client requise.");
      return null;
    }

    return new SessionUser(email);
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
