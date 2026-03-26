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

@WebServlet("/api/register")
public class RegisterVerificationServlet extends HttpServlet {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

  private static final Pattern PHONE_PATTERN =
      Pattern.compile("^[0-9+ ]{8,20}$");

    private static final Pattern CIN_PATTERN =
      Pattern.compile("^[0-9]{8,12}$");

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String firstName = clean(request.getParameter("firstName"));
    String lastName = clean(request.getParameter("lastName"));
    String age = clean(request.getParameter("age"));
    String cin = clean(request.getParameter("cin"));
    String serviceType = clean(request.getParameter("serviceType"));
    String mainActivity = clean(request.getParameter("mainActivity"));
    String workTitle = clean(request.getParameter("workTitle"));
    String workDescription = clean(request.getParameter("workDescription"));
    String email = clean(request.getParameter("email"));
    String phone = clean(request.getParameter("phone"));
    String role = clean(request.getParameter("role"));
    String password = clean(request.getParameter("password"));
    String confirmPassword = clean(request.getParameter("confirmPassword"));

    if (firstName.length() < 2) {
      redirectRegisterError(request, response, "Le prenom doit contenir au moins 2 caracteres.");
      return;
    }

    if (lastName.length() < 2) {
      redirectRegisterError(request, response, "Le nom doit contenir au moins 2 caracteres.");
      return;
    }

    if (!EMAIL_PATTERN.matcher(email).matches()) {
      redirectRegisterError(request, response, "Format email invalide.");
      return;
    }

    if (!PHONE_PATTERN.matcher(phone).matches()) {
      redirectRegisterError(request, response, "Numero de telephone invalide.");
      return;
    }

    if (!"CLIENT".equals(role) && !"PROVIDER".equals(role)) {
      redirectRegisterError(request, response, "Type de compte invalide.");
      return;
    }

    if (password.length() < 8) {
      redirectRegisterError(request, response, "Le mot de passe doit contenir au moins 8 caracteres.");
      return;
    }

    if (!password.equals(confirmPassword)) {
      redirectRegisterError(request, response, "La confirmation du mot de passe ne correspond pas.");
      return;
    }

    int numericAge = parseAge(age);
    if (numericAge < 16 || numericAge > 100) {
      redirectRegisterError(request, response, "Age invalide. Entrez un age entre 16 et 100 ans.");
      return;
    }

    if ("PROVIDER".equals(role)) {
      if (!CIN_PATTERN.matcher(cin).matches()) {
        redirectRegisterError(request, response, "CIN invalide. Utilisez 8 a 12 chiffres.");
        return;
      }

      if (serviceType.length() < 3) {
        redirectRegisterError(request, response, "Type de service obligatoire.");
        return;
      }

      if (mainActivity.length() < 3) {
        redirectRegisterError(request, response, "Activite principale obligatoire.");
        return;
      }

      if (workTitle.length() < 3) {
        redirectRegisterError(request, response, "Titre de travail obligatoire.");
        return;
      }

      if (workDescription.length() < 10) {
        redirectRegisterError(request, response, "Description du travail trop courte (minimum 10 caracteres).");
        return;
      }
    } else {
      cin = "";
      serviceType = "";
      mainActivity = "";
      workTitle = "";
      workDescription = "";
    }

    ProviderRepository repository = ProviderRepository.getInstance();
    if (repository.emailExists(email)) {
      response.sendRedirect(request.getContextPath() + "/login.html?error="
          + encode("Compte existant. Connectez-vous."));
      return;
    }

    ProviderRepository.Account account = new ProviderRepository.Account(
        role,
        firstName,
        lastName,
        String.valueOf(numericAge),
        cin,
        serviceType,
        mainActivity,
        workTitle,
        workDescription,
        phone,
        email,
        ProviderRepository.hashPassword(password));

    repository.save(account);

    HttpSession session = request.getSession(true);
    session.setAttribute("loggedIn", Boolean.TRUE);
    session.setAttribute("userEmail", email);
    session.setAttribute("userRole", role);
    session.setAttribute("userFirstName", firstName);
    session.setAttribute("userLastName", lastName);

    if ("PROVIDER".equals(role)) {
      response.sendRedirect(request.getContextPath() + "/provider-space.html");
      return;
    }

    response.sendRedirect(request.getContextPath() + "/client-space.html");
  }

  private void redirectRegisterError(
      HttpServletRequest request,
      HttpServletResponse response,
      String message) throws IOException {
    response.sendRedirect(request.getContextPath() + "/register.html?error=" + encode(message));
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private int parseAge(String age) {
    try {
      return Integer.parseInt(age);
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
