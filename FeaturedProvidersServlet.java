import java.io.IOException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/providers/featured")
public class FeaturedProvidersServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    ProviderRepository repository = ProviderRepository.getInstance();
    List<ProviderRepository.Account> providers = repository.listFirstProviders(3);

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(toJson(providers));
  }

  private String toJson(List<ProviderRepository.Account> providers) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");

    for (int i = 0; i < providers.size(); i++) {
      ProviderRepository.Account p = providers.get(i);
      if (i > 0) {
        sb.append(",");
      }

      sb.append("{")
          .append("\"role\":\"").append(escape(p.role)).append("\",")
          .append("\"email\":\"").append(escape(p.email)).append("\",")
          .append("\"firstName\":\"").append(escape(p.firstName)).append("\",")
          .append("\"lastName\":\"").append(escape(p.lastName)).append("\",")
          .append("\"age\":\"").append(escape(p.age)).append("\",")
          .append("\"cin\":\"").append(escape(p.cin)).append("\",")
          .append("\"serviceType\":\"").append(escape(p.serviceType)).append("\",")
          .append("\"mainActivity\":\"").append(escape(p.mainActivity)).append("\",")
          .append("\"workTitle\":\"").append(escape(p.workTitle)).append("\",")
          .append("\"workDescription\":\"").append(escape(p.workDescription)).append("\",")
          .append("\"phone\":\"").append(escape(p.phone)).append("\",")
          .append("\"createdAt\":\"").append(escape(p.createdAt)).append("\",")
          .append("\"photoUrl\":\"").append(escape(p.photoUrl)).append("\"")
          .append("}");
    }

    sb.append("]");
    return sb.toString();
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
