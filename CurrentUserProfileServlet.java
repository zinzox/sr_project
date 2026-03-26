import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/auth/profile")
public class CurrentUserProfileServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    if (!loggedIn) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Connexion requise.");
      return;
    }

    String email = sessionValue(session, "userEmail");
    if (email.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Session invalide.");
      return;
    }

    ProviderRepository.Account account = ProviderRepository.getInstance().findByEmail(email);
    if (account == null) {
      writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Compte introuvable.");
      return;
    }

    String firstName = safe(account.firstName);
    String lastName = safe(account.lastName);
    String role = safe(account.role);

    // Refresh session identity to avoid stale values.
    session.setAttribute("userFirstName", firstName);
    session.setAttribute("userLastName", lastName);
    session.setAttribute("userRole", role);

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{" +
        "\"email\":\"" + escape(email) + "\"," +
        "\"role\":\"" + escape(role) + "\"," +
        "\"firstName\":\"" + escape(firstName) + "\"," +
        "\"lastName\":\"" + escape(lastName) + "\"" +
        "}");
  }

  private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"message\":\"" + escape(message) + "\"}");
  }

  private String sessionValue(HttpSession session, String key) {
    Object value = session.getAttribute(key);
    return value == null ? "" : String.valueOf(value).trim();
  }

  private String safe(String value) {
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
}