import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/auth/status")
public class AuthStatusServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);

    boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    String role = loggedIn ? sessionValue(session, "userRole") : "";
    String email = loggedIn ? sessionValue(session, "userEmail") : "";
    String firstName = loggedIn ? sessionValue(session, "userFirstName") : "";
    String lastName = loggedIn ? sessionValue(session, "userLastName") : "";

    if (loggedIn && !email.isEmpty() && (firstName.isEmpty() || lastName.isEmpty())) {
      try {
        ProviderRepository.Account account = ProviderRepository.getInstance().findByEmail(email);
        if (account != null) {
          if (firstName.isEmpty()) {
            firstName = safe(account.firstName);
          }
          if (lastName.isEmpty()) {
            lastName = safe(account.lastName);
          }

          if (session != null) {
            session.setAttribute("userFirstName", firstName);
            session.setAttribute("userLastName", lastName);
          }
        }
      } catch (IOException ignored) {
        // Keep session values when DB lookup fails.
      }
    }

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{" +
        "\"loggedIn\":" + loggedIn + "," +
        "\"role\":\"" + escape(role) + "\"," +
      "\"email\":\"" + escape(email) + "\"," +
      "\"firstName\":\"" + escape(firstName) + "\"," +
      "\"lastName\":\"" + escape(lastName) + "\"" +
        "}");
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
