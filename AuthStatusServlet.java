import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/auth/status")
public class AuthStatusServlet extends HttpServlet {

  private static final String ADMIN_CONTACT_EMAIL = "roheksarbi@gmail.com";

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);

    boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    String role = loggedIn ? sessionValue(session, "userRole") : "";
    String email = loggedIn ? sessionValue(session, "userEmail") : "";
    String firstName = loggedIn ? sessionValue(session, "userFirstName") : "";
    String lastName = loggedIn ? sessionValue(session, "userLastName") : "";
    String sessionMessage = "";

    if (loggedIn && session != null && !email.isEmpty()) {
      try {
        ProviderRepository.Account account = ProviderRepository.getInstance().findByEmail(email);
        String phone = account == null ? "" : safe(account.phone);
        ModerationRepository moderation = ModerationRepository.getInstance();
        if (moderation.isBlacklisted(email, phone)) {
          session.invalidate();
          loggedIn = false;
          role = "";
          email = "";
          firstName = "";
          lastName = "";
          sessionMessage = "Compte blacklisté. Contactez l'administrateur: " + ADMIN_CONTACT_EMAIL;
        } else if (moderation.isTemporarilyBanned(email)) {
          session.invalidate();
          loggedIn = false;
          role = "";
          email = "";
          firstName = "";
          lastName = "";
          sessionMessage = "Compte suspendu temporairement (5 minutes). Reessayez plus tard.";
        }
      } catch (IOException ignored) {
        // Keep current session if blacklist check fails unexpectedly.
      }
    }

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
      "\"lastName\":\"" + escape(lastName) + "\"," +
      "\"message\":\"" + escape(sessionMessage) + "\"" +
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
