import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/provider-profile")
public class ProviderProfileServlet extends HttpServlet {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    String sessionRole = session == null ? "" : String.valueOf(session.getAttribute("userRole"));
    String sessionEmail = session == null ? "" : String.valueOf(session.getAttribute("userEmail"));

    if (!"PROVIDER".equals(sessionRole) || sessionEmail.isEmpty()) {
      sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Connexion prestataire requise.");
      return;
    }

    String email = clean(request.getParameter("email"));

    if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
      sendJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Email invalide.");
      return;
    }

    if (!sessionEmail.equalsIgnoreCase(email)) {
      sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Action non autorisee.");
      return;
    }

    ProviderRepository repository = ProviderRepository.getInstance();
    ProviderRepository.Account account = repository.findByEmail(email);

    if (account == null) {
      sendJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Compte introuvable.");
      return;
    }

    if (!"PROVIDER".equals(account.role)) {
      sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Ce compte n'est pas un prestataire.");
      return;
    }

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(toJson(account));
  }

  @Override
  protected void doPut(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    String sessionRole = session == null ? "" : String.valueOf(session.getAttribute("userRole"));
    String sessionEmail = session == null ? "" : String.valueOf(session.getAttribute("userEmail"));

    if (!"PROVIDER".equals(sessionRole) || sessionEmail.isEmpty()) {
      sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Connexion prestataire requise.");
      return;
    }

    String email = clean(request.getParameter("email"));
    if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
      sendJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Email invalide.");
      return;
    }

    if (!sessionEmail.equalsIgnoreCase(email)) {
      sendJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Action non autorisee.");
      return;
    }

    ProviderRepository repository = ProviderRepository.getInstance();

    String photoUrlParam = request.getParameter("photoUrl");
    String serviceTypeParam = request.getParameter("serviceType");
    String mainActivityParam = request.getParameter("mainActivity");
    String workTitleParam = request.getParameter("workTitle");
    String workDescriptionParam = request.getParameter("workDescription");
    String phoneParam = request.getParameter("phone");

    boolean hasPhotoPayload = photoUrlParam != null;
    boolean hasProfilePayload = serviceTypeParam != null
        || mainActivityParam != null
        || workTitleParam != null
        || workDescriptionParam != null
        || phoneParam != null;

    if (!hasPhotoPayload && !hasProfilePayload) {
      sendJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Aucune donnee a mettre a jour.");
      return;
    }

    boolean updated = true;

    if (hasProfilePayload) {
      updated = repository.updateProviderProfile(
          email,
          clean(serviceTypeParam),
          clean(mainActivityParam),
          clean(workTitleParam),
          clean(workDescriptionParam),
          clean(phoneParam));
    }

    if (updated && hasPhotoPayload) {
      updated = repository.updateProviderPhoto(email, clean(photoUrlParam));
    }

    if (!updated) {
      sendJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Compte prestataire introuvable.");
      return;
    }

    ProviderRepository.Account account = repository.findByEmail(email);
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(toJson(account));
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private void sendJsonError(HttpServletResponse response, int status, String message) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"message\":\"" + escape(message) + "\"}");
  }

  private String toJson(ProviderRepository.Account account) {
    return "{" +
        "\"email\":\"" + escape(account.email) + "\"," +
        "\"firstName\":\"" + escape(account.firstName) + "\"," +
        "\"lastName\":\"" + escape(account.lastName) + "\"," +
        "\"phone\":\"" + escape(account.phone) + "\"," +
        "\"serviceType\":\"" + escape(account.serviceType) + "\"," +
        "\"mainActivity\":\"" + escape(account.mainActivity) + "\"," +
        "\"workTitle\":\"" + escape(account.workTitle) + "\"," +
          "\"workDescription\":\"" + escape(account.workDescription) + "\"," +
          "\"photoUrl\":\"" + escape(account.photoUrl) + "\"" +
        "}";
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
