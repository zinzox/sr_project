import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/reviews")
public class ReviewsServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String providerEmail = clean(request.getParameter("providerEmail"));
    if (providerEmail.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "providerEmail requis.");
      return;
    }

    ProductFeaturesRepository repository = ProductFeaturesRepository.getInstance();
    List<ProductFeaturesRepository.ReviewRecord> rows = repository.listReviews(providerEmail);
    ProductFeaturesRepository.RatingSummary summary = repository.getRatingSummary(providerEmail);

    String payload = "{" +
        "\"summary\":{" +
        "\"totalReviews\":" + summary.totalReviews + "," +
        "\"averageRating\":" + String.format(java.util.Locale.US, "%.2f", summary.averageRating) +
        "}," +
        "\"reviews\":" + rowsToJson(rows) +
        "}";

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(payload);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireClient(request, response);
    if (user == null) {
      return;
    }

    String providerEmail = clean(request.getParameter("providerEmail"));
    int rating = parseInt(request.getParameter("rating"), 0);
    String comment = clean(request.getParameter("comment"));

    if (providerEmail.isEmpty() || rating < 1 || rating > 5) {
      writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "providerEmail et rating(1..5) requis.");
      return;
    }

    ProductFeaturesRepository.getInstance().addReview(providerEmail, user.email, rating, comment);
    ProductFeaturesRepository.getInstance().createNotification(
      providerEmail,
      "NEW_REVIEW",
      "{\"clientEmail\":\"" + escape(user.email) + "\",\"rating\":" + rating + "}");
    ProductFeaturesRepository.getInstance().createNotification(
      user.email,
      "REVIEW_POSTED",
      "{\"providerEmail\":\"" + escape(providerEmail) + "\",\"rating\":" + rating + "}");
    writeJsonOk(response, "Avis enregistre.");
  }

  private String rowsToJson(List<ProductFeaturesRepository.ReviewRecord> rows) {
    return "[" + rows.stream().map(r -> "{" +
        "\"id\":" + r.id + "," +
        "\"providerEmail\":\"" + escape(r.providerEmail) + "\"," +
        "\"clientEmail\":\"" + escape(r.clientEmail) + "\"," +
        "\"rating\":" + r.rating + "," +
        "\"comment\":\"" + escape(r.commentText) + "\"," +
        "\"createdAt\":\"" + escape(r.createdAt) + "\"" +
        "}").collect(Collectors.joining(",")) + "]";
  }

  private SessionUser requireClient(HttpServletRequest request, HttpServletResponse response) throws IOException {
    HttpSession session = request.getSession(false);
    boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    String email = loggedIn ? clean(String.valueOf(session.getAttribute("userEmail"))) : "";
    String role = loggedIn ? clean(String.valueOf(session.getAttribute("userRole"))) : "";

    if (!loggedIn || email.isEmpty() || !"CLIENT".equalsIgnoreCase(role)) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Connexion client requise.");
      return null;
    }

    return new SessionUser(email);
  }

  private int parseInt(String value, int defaultValue) {
    try {
      return Integer.parseInt(clean(value));
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  private void writeJsonOk(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"message\":\"" + escape(message) + "\"}");
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

  private static class SessionUser {
    final String email;

    SessionUser(String email) {
      this.email = email;
    }
  }
}
