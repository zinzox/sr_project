import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/register")
public class RegisterVerificationServlet extends HttpServlet {

  private static final long VERIFICATION_CODE_TTL_MILLIS = 10 * 60 * 1000;
  private static final String SESSION_PENDING = "pendingRegistration";
  private static final String ADMIN_CONTACT_EMAIL = "roheksarbi@gmail.com";

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

  private static final Pattern PHONE_PATTERN =
      Pattern.compile("^[0-9+ ]{8,20}$");

  private static final Pattern CIN_PATTERN =
      Pattern.compile("^[0-9]{8,12}$");

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String step = clean(request.getParameter("step"));
    if ("verify-code".equals(step)) {
      verifyCodeAndCreateAccount(request, response);
      return;
    }

    requestVerificationCode(request, response);
  }

  private void requestVerificationCode(HttpServletRequest request, HttpServletResponse response) throws IOException {
    RegistrationInput input = parseAndValidateInput(request, response);
    if (input == null) {
      return;
    }

    ProviderRepository repository = ProviderRepository.getInstance();
    ModerationRepository moderationRepository = ModerationRepository.getInstance();

    if (moderationRepository.isBlacklisted(input.email, input.phone)) {
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
          "Inscription refusee: email/telephone blacklisté. Contact admin: " + ADMIN_CONTACT_EMAIL);
      return;
    }

    if (moderationRepository.isTemporarilyBanned(input.email)) {
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
          "Compte temporairement suspendu. Reessayez plus tard.");
      return;
    }

    if (repository.emailExists(input.email)) {
      writeJsonError(response, HttpServletResponse.SC_CONFLICT, "Compte existant. Connectez-vous.");
      return;
    }

    HttpSession session = request.getSession(true);
    PendingRegistration existingPending = (PendingRegistration) session.getAttribute(SESSION_PENDING);

    // Do not resend multiple times for the same pending registration.
    if (existingPending != null
        && existingPending.email.equalsIgnoreCase(input.email)
        && System.currentTimeMillis() <= existingPending.expiresAt) {
      writeJson(response, HttpServletResponse.SC_OK,
          "{\"message\":\"Code deja envoye. Verifiez votre boite mail et saisissez ce code.\",\"alreadySent\":true}");
      return;
    }

    String code = generateVerificationCode();
    long expiresAt = System.currentTimeMillis() + VERIFICATION_CODE_TTL_MILLIS;

    PendingRegistration pending = new PendingRegistration(input, code, expiresAt);

    try {
      SimpleSmtpMailer.sendVerificationCode(input.email, code);
    } catch (IOException ex) {
      ex.printStackTrace();
      writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Impossible d'envoyer l'email de verification: " + ex.getMessage());
      return;
    }

    session.setAttribute(SESSION_PENDING, pending);

    writeJson(response, HttpServletResponse.SC_OK,
        "{\"message\":\"Code envoye par email. Saisissez-le pour finaliser l'inscription.\"}");
  }

  private void verifyCodeAndCreateAccount(HttpServletRequest request, HttpServletResponse response) throws IOException {
    HttpSession session = request.getSession(false);
    PendingRegistration pending = session == null ? null : (PendingRegistration) session.getAttribute(SESSION_PENDING);
    if (pending == null) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Aucune verification en cours. Recommencez l'inscription.");
      return;
    }

    if (System.currentTimeMillis() > pending.expiresAt) {
      session.removeAttribute(SESSION_PENDING);
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Code expire. Demandez un nouveau code.");
      return;
    }

    String providedCode = clean(request.getParameter("verificationCode"));
    if (providedCode.isEmpty() || !providedCode.equals(pending.verificationCode)) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Code de verification incorrect.");
      return;
    }

    ProviderRepository repository = ProviderRepository.getInstance();
    ModerationRepository moderationRepository = ModerationRepository.getInstance();

    if (moderationRepository.isBlacklisted(pending.email, pending.phone)) {
      session.removeAttribute(SESSION_PENDING);
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
          "Inscription refusee: compte blacklisté. Contact admin: " + ADMIN_CONTACT_EMAIL);
      return;
    }

    if (moderationRepository.isTemporarilyBanned(pending.email)) {
      session.removeAttribute(SESSION_PENDING);
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
          "Compte temporairement suspendu. Reessayez plus tard.");
      return;
    }

    if (repository.emailExists(pending.email)) {
      session.removeAttribute(SESSION_PENDING);
      writeJsonError(response, HttpServletResponse.SC_CONFLICT, "Compte deja cree. Connectez-vous.");
      return;
    }

    ProviderRepository.Account account = new ProviderRepository.Account(
        pending.role,
        pending.firstName,
        pending.lastName,
        String.valueOf(pending.numericAge),
        pending.cin,
        pending.serviceType,
        pending.mainActivity,
        pending.workTitle,
        pending.workDescription,
        pending.phone,
        pending.email,
        ProviderRepository.hashPassword(pending.password));

    repository.save(account);

    session.setAttribute("loggedIn", Boolean.TRUE);
    session.setAttribute("userEmail", pending.email);
    session.setAttribute("userRole", pending.role);
    session.setAttribute("userFirstName", pending.firstName);
    session.setAttribute("userLastName", pending.lastName);
    session.removeAttribute(SESSION_PENDING);

    if ("PROVIDER".equals(pending.role)) {
      writeJson(response, HttpServletResponse.SC_OK, "{\"redirect\":\"/sarbi_rohek/provider-space.html\"}");
      return;
    }

    writeJson(response, HttpServletResponse.SC_OK, "{\"redirect\":\"/sarbi_rohek/client-space.html\"}");
  }

  private RegistrationInput parseAndValidateInput(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Le prenom doit contenir au moins 2 caracteres.");
      return null;
    }

    if (lastName.length() < 2) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Le nom doit contenir au moins 2 caracteres.");
      return null;
    }

    if (!EMAIL_PATTERN.matcher(email).matches()) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Format email invalide.");
      return null;
    }

    if (!PHONE_PATTERN.matcher(phone).matches()) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Numero de telephone invalide.");
      return null;
    }

    if (!"CLIENT".equals(role) && !"PROVIDER".equals(role)) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Type de compte invalide.");
      return null;
    }

    if (password.length() < 8) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Le mot de passe doit contenir au moins 8 caracteres.");
      return null;
    }

    if (!password.equals(confirmPassword)) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "La confirmation du mot de passe ne correspond pas.");
      return null;
    }

    int numericAge = parseAge(age);
    if (numericAge < 16 || numericAge > 100) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Age invalide. Entrez un age entre 16 et 100 ans.");
      return null;
    }

    if ("PROVIDER".equals(role)) {
      if (!CIN_PATTERN.matcher(cin).matches()) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "CIN invalide. Utilisez 8 a 12 chiffres.");
        return null;
      }

      if (serviceType.length() < 3) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Type de service obligatoire.");
        return null;
      }

      if (mainActivity.length() < 3) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Activite principale obligatoire.");
        return null;
      }

      if (workTitle.length() < 3) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Titre de travail obligatoire.");
        return null;
      }

      if (workDescription.length() < 10) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST,
            "Description du travail trop courte (minimum 10 caracteres).");
        return null;
      }
    } else {
      cin = "";
      serviceType = "";
      mainActivity = "";
      workTitle = "";
      workDescription = "";
    }

    return new RegistrationInput(
        firstName,
        lastName,
        numericAge,
        cin,
        serviceType,
        mainActivity,
        workTitle,
        workDescription,
        email,
        phone,
        role,
        password);
  }

  private String generateVerificationCode() {
    int value = ThreadLocalRandom.current().nextInt(100000, 1000000);
    return String.valueOf(value);
  }

  private void writeJson(HttpServletResponse response, int status, String payload) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(payload);
  }

  private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
    writeJson(response, status, "{\"message\":\"" + escape(message) + "\"}");
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

  private static class RegistrationInput {
    private final String firstName;
    private final String lastName;
    private final int numericAge;
    private final String cin;
    private final String serviceType;
    private final String mainActivity;
    private final String workTitle;
    private final String workDescription;
    private final String email;
    private final String phone;
    private final String role;
    private final String password;

    private RegistrationInput(
        String firstName,
        String lastName,
        int numericAge,
        String cin,
        String serviceType,
        String mainActivity,
        String workTitle,
        String workDescription,
        String email,
        String phone,
        String role,
        String password) {
      this.firstName = firstName;
      this.lastName = lastName;
      this.numericAge = numericAge;
      this.cin = cin;
      this.serviceType = serviceType;
      this.mainActivity = mainActivity;
      this.workTitle = workTitle;
      this.workDescription = workDescription;
      this.email = email;
      this.phone = phone;
      this.role = role;
      this.password = password;
    }
  }

  private static class PendingRegistration {
    private final String firstName;
    private final String lastName;
    private final int numericAge;
    private final String cin;
    private final String serviceType;
    private final String mainActivity;
    private final String workTitle;
    private final String workDescription;
    private final String email;
    private final String phone;
    private final String role;
    private final String password;
    private final String verificationCode;
    private final long expiresAt;

    private PendingRegistration(RegistrationInput input, String verificationCode, long expiresAt) {
      this.firstName = input.firstName;
      this.lastName = input.lastName;
      this.numericAge = input.numericAge;
      this.cin = input.cin;
      this.serviceType = input.serviceType;
      this.mainActivity = input.mainActivity;
      this.workTitle = input.workTitle;
      this.workDescription = input.workDescription;
      this.email = input.email;
      this.phone = input.phone;
      this.role = input.role;
      this.password = input.password;
      this.verificationCode = verificationCode;
      this.expiresAt = expiresAt;
    }
  }
}
