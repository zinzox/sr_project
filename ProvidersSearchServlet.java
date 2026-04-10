import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet({"/api/providers/search", "/api/providers/recommended"})
public class ProvidersSearchServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String servletPath = request.getServletPath();
    List<ProviderRepository.Account> providers = ProviderRepository.getInstance().listAllProviders();

    if ("/api/providers/search".equals(servletPath)) {
      String keyword = clean(request.getParameter("keyword")).toLowerCase(Locale.ROOT);
      String serviceType = clean(request.getParameter("serviceType")).toUpperCase(Locale.ROOT);

      List<ProviderRepository.Account> filtered = providers.stream().filter(p -> {
        if (!serviceType.isEmpty() && !"ALL".equals(serviceType)) {
          if (!serviceType.equals(clean(p.serviceType).toUpperCase(Locale.ROOT))) {
            return false;
          }
        }

        if (keyword.isEmpty()) {
          return true;
        }

        String haystack = (clean(p.firstName) + " " + clean(p.lastName) + " " + clean(p.mainActivity) + " "
            + clean(p.workDescription) + " " + clean(p.email)).toLowerCase(Locale.ROOT);
        return haystack.contains(keyword);
      }).collect(Collectors.toList());

      writeProviders(response, filtered);
      return;
    }

    if ("/api/providers/recommended".equals(servletPath)) {
      String clientEmail = clean(request.getParameter("clientEmail"));
      final List<String> favorites = clientEmail.isEmpty()
          ? new ArrayList<>()
          : ProductFeaturesRepository.getInstance().listFavorites(clientEmail);

      List<ProviderRepository.Account> recommended;
      if (favorites.isEmpty()) {
        recommended = providers.stream().limit(6).collect(Collectors.toList());
      } else {
        recommended = providers.stream().filter(p -> !favorites.contains(clean(p.email).toLowerCase(Locale.ROOT))).limit(6)
            .collect(Collectors.toList());
      }

      writeProviders(response, recommended);
      return;
    }

    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  private void writeProviders(HttpServletResponse response, List<ProviderRepository.Account> rows) throws IOException {
    String payload = "[" + rows.stream().map(p -> "{" +
        "\"email\":\"" + escape(p.email) + "\"," +
        "\"firstName\":\"" + escape(p.firstName) + "\"," +
        "\"lastName\":\"" + escape(p.lastName) + "\"," +
        "\"serviceType\":\"" + escape(p.serviceType) + "\"," +
        "\"mainActivity\":\"" + escape(p.mainActivity) + "\"," +
        "\"workDescription\":\"" + escape(p.workDescription) + "\"" +
        "}").collect(Collectors.joining(",")) + "]";

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(payload);
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
