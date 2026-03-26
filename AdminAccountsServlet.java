import java.io.IOException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/admin/accounts")
public class AdminAccountsServlet extends HttpServlet {

  private static final String ADMIN_KEY = "AZIZ-ONLY-ADMIN-2026";

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (!isAuthorized(request)) {
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Acces refuse.");
      return;
    }

    ProviderRepository repository = ProviderRepository.getInstance();
    List<ProviderRepository.Account> accounts = repository.listAllAccounts();

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(accountsToJson(accounts));
  }

  @Override
  protected void doDelete(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (!isAuthorized(request)) {
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Acces refuse.");
      return;
    }

    String email = clean(request.getParameter("email"));
    if (email.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Email requis.");
      return;
    }

    ProviderRepository repository = ProviderRepository.getInstance();
    boolean deleted = repository.deleteByEmail(email);

    if (!deleted) {
      writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Compte introuvable.");
      return;
    }

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"message\":\"Compte supprime.\"}");
  }

  private boolean isAuthorized(HttpServletRequest request) {
    String key = clean(request.getParameter("key"));
    return ADMIN_KEY.equals(key);
  }

  private String accountsToJson(List<ProviderRepository.Account> accounts) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");

    for (int i = 0; i < accounts.size(); i++) {
      ProviderRepository.Account account = accounts.get(i);
      if (i > 0) {
        sb.append(",");
      }

      sb.append("{")
          .append("\"role\":\"").append(escape(account.role)).append("\",")
          .append("\"firstName\":\"").append(escape(account.firstName)).append("\",")
          .append("\"lastName\":\"").append(escape(account.lastName)).append("\",")
          .append("\"age\":\"").append(escape(account.age)).append("\",")
          .append("\"phone\":\"").append(escape(account.phone)).append("\",")
          .append("\"email\":\"").append(escape(account.email)).append("\",")
          .append("\"serviceType\":\"").append(escape(account.serviceType)).append("\",")
          .append("\"createdAt\":\"").append(escape(account.createdAt)).append("\"")
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
}
