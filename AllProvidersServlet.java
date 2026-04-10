import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/providers/all")
public class AllProvidersServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    String role = loggedIn ? sessionValue(session, "userRole") : "";
    String email = loggedIn ? sessionValue(session, "userEmail") : "";

    if (!loggedIn) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Connexion requise.");
      return;
    }

    if (role.isEmpty() && !email.isEmpty()) {
      ProviderRepository.Account account = ProviderRepository.getInstance().findByEmail(email);
      if (account != null) {
        role = safe(account.role);
        session.setAttribute("userRole", role);
      }
    }

    if (!"CLIENT".equals(role)) {
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Acces reserve au client.");
      return;
    }

    ProviderRepository repository = ProviderRepository.getInstance();
    List<ProviderRepository.Account> providers = repository.listAllProviders();
    Set<String> pausedProviders = new HashSet<>(ProductFeaturesRepository.getInstance().listProvidersPausedNow());

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(toJson(providers, pausedProviders));
  }

  private String toJson(List<ProviderRepository.Account> providers, Set<String> pausedProviders) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");

    for (int i = 0; i < providers.size(); i++) {
      ProviderRepository.Account p = providers.get(i);
      boolean paused = pausedProviders.contains(safe(p.email).toLowerCase());
      if (i > 0) {
        sb.append(",");
      }

      sb.append("{")
          .append("\"role\":\"").append(escape(p.role)).append("\",")
          .append("\"firstName\":\"").append(escape(p.firstName)).append("\",")
          .append("\"lastName\":\"").append(escape(p.lastName)).append("\",")
          .append("\"age\":\"").append(escape(p.age)).append("\",")
          .append("\"cin\":\"").append(escape(p.cin)).append("\",")
          .append("\"serviceType\":\"").append(escape(p.serviceType)).append("\",")
          .append("\"mainActivity\":\"").append(escape(p.mainActivity)).append("\",")
          .append("\"workTitle\":\"").append(escape(p.workTitle)).append("\",")
          .append("\"workDescription\":\"").append(escape(p.workDescription)).append("\",")
          .append("\"phone\":\"").append(escape(p.phone)).append("\",")
          .append("\"email\":\"").append(escape(p.email)).append("\",")
          .append("\"createdAt\":\"").append(escape(p.createdAt)).append("\",")
              .append("\"photoUrl\":\"").append(escape(p.photoUrl)).append("\",")
              .append("\"paused\":").append(paused)
          .append("}");
    }

    sb.append("]");
    return sb.toString();
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
