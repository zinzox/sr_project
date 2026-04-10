import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/login")
public class LoginVerificationServlet extends HttpServlet {

  private static final String ADMIN_CONTACT_EMAIL = "roheksarbi@gmail.com";

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String email = clean(request.getParameter("email"));
    String password = clean(request.getParameter("password"));

    if (email.isEmpty() || password.isEmpty()) {
      redirectLoginError(request, response, "Email et mot de passe obligatoires.");
      return;
    }

    if (!EMAIL_PATTERN.matcher(email).matches()) {
      redirectLoginError(request, response, "Format email invalide.");
      return;
    }

    if (password.length() < 8) {
      redirectLoginError(request, response, "Le mot de passe doit contenir au moins 8 caracteres.");
      return;
    }

    ProviderRepository repository = ProviderRepository.getInstance();
    ProviderRepository.Account account = repository.findByEmail(email);

    if (account == null) {
      response.sendRedirect(request.getContextPath() + "/register.html?error="
          + encode("Compte introuvable. Inscris-toi d'abord."));
      return;
    }

    ModerationRepository moderationRepository = ModerationRepository.getInstance();
    if (moderationRepository.isBlacklisted(account.email, account.phone)) {
      redirectLoginError(request, response,
          "Compte blacklisté. Contactez l'administrateur: " + ADMIN_CONTACT_EMAIL);
      return;
    }

    if (moderationRepository.isTemporarilyBanned(account.email)) {
      redirectLoginError(request, response, "Compte temporairement suspendu (5 minutes).");
      return;
    }

    String receivedPasswordHash = ProviderRepository.hashPassword(password);
    if (!receivedPasswordHash.equals(account.passwordHash)) {
      redirectLoginError(request, response, "Mot de passe incorrect.");
      return;
    }

    HttpSession session = request.getSession(true);
    session.setAttribute("loggedIn", Boolean.TRUE);
    session.setAttribute("userEmail", account.email);
    session.setAttribute("userRole", account.role);
    session.setAttribute("userFirstName", account.firstName);
    session.setAttribute("userLastName", account.lastName);

    if ("PROVIDER".equals(account.role)) {
      response.sendRedirect(request.getContextPath() + "/provider-space.html");
      return;
    }

    response.sendRedirect(request.getContextPath() + "/client-space.html");
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private void redirectLoginError(
      HttpServletRequest request,
      HttpServletResponse response,
      String message) throws IOException {
    response.sendRedirect(request.getContextPath() + "/login.html?error=" + encode(message));
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
