import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/commerce/*")
public class CommerceServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    String path = request.getPathInfo() == null ? "" : request.getPathInfo();
    ProductFeaturesRepository repository = ProductFeaturesRepository.getInstance();

    if ("/payments".equals(path)) {
      List<ProductFeaturesRepository.PaymentRecord> rows = repository.listPaymentsForUser(user.email, user.role);
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{\"payments\":" + paymentsToJson(rows) + "}");
      return;
    }

    if ("/invoices".equals(path)) {
      List<ProductFeaturesRepository.InvoiceRecord> rows = repository.listInvoices();
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{\"invoices\":" + invoicesToJson(rows) + "}");
      return;
    }

    writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Endpoint commerce introuvable.");
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    String path = request.getPathInfo() == null ? "" : request.getPathInfo();
    ProductFeaturesRepository repository = ProductFeaturesRepository.getInstance();

    if ("/payments/create".equals(path)) {
      if (!"CLIENT".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Action reservee au client.");
        return;
      }

      String providerEmail = clean(request.getParameter("providerEmail"));
      double amount = parseDouble(request.getParameter("amount"), 0.0);
      String currency = clean(request.getParameter("currency"));
      if (providerEmail.isEmpty() || amount <= 0) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "providerEmail et amount requis.");
        return;
      }

      long id = repository.createPayment(user.email, providerEmail, amount, currency, "PENDING", "");
      if (id > 0) {
        // Simule un paiement reel: confirmation immediate apres creation.
        repository.confirmPayment(id, "AUTO_SIMULATED");
      }

      ProductFeaturesRepository.PaymentRecord created = repository.findPaymentById(id);
      if (created != null) {
        repository.createNotification(
            created.providerEmail,
            "NEW_PAYMENT",
            "{\"paymentId\":" + created.id + ",\"clientEmail\":\"" + escape(created.clientEmail)
                + "\",\"amount\":" + String.format(java.util.Locale.US, "%.2f", created.amount) + "}");
        repository.createNotification(
            created.clientEmail,
            "PAYMENT_CONFIRMED",
            "{\"paymentId\":" + created.id + ",\"providerEmail\":\"" + escape(created.providerEmail)
                + "\",\"amount\":" + String.format(java.util.Locale.US, "%.2f", created.amount) + "}");
      }
      writeJsonOk(response, "Paiement confirme.", "paymentId", String.valueOf(id));
      return;
    }

    if ("/payments/confirm".equals(path)) {
      if (!"PROVIDER".equalsIgnoreCase(user.role) && !"ADMIN".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Action reservee au prestataire/admin.");
        return;
      }

      long paymentId = parseLong(request.getParameter("paymentId"), -1L);
      String providerRef = clean(request.getParameter("providerRef"));
      if (paymentId <= 0) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "paymentId requis.");
        return;
      }

      repository.confirmPayment(paymentId, providerRef);
      ProductFeaturesRepository.PaymentRecord confirmed = repository.findPaymentById(paymentId);
      if (confirmed != null) {
        repository.createNotification(
            confirmed.clientEmail,
            "PAYMENT_CONFIRMED",
            "{\"paymentId\":" + confirmed.id + ",\"providerEmail\":\"" + escape(confirmed.providerEmail)
                + "\"}");
      }
      writeJsonOk(response, "Paiement confirme.", "paymentId", String.valueOf(paymentId));
      return;
    }

    if ("/payments/work-status/update".equals(path) || path.startsWith("/payments/work-status/update/")) {
      if (!"PROVIDER".equalsIgnoreCase(user.role) && !"ADMIN".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Action reservee au prestataire/admin.");
        return;
      }

      long paymentId = parseLong(request.getParameter("paymentId"), -1L);
      String workStatus = clean(request.getParameter("workStatus"));
      if (paymentId <= 0 || workStatus.isEmpty()) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "paymentId et workStatus requis.");
        return;
      }

      repository.updatePaymentWorkStatus(paymentId, user.email, user.role, workStatus);
      ProductFeaturesRepository.PaymentRecord payment = repository.findPaymentById(paymentId);
      if (payment != null) {
        repository.createNotification(
            payment.clientEmail,
            "TASK_STATUS_UPDATED",
            "{\"paymentId\":" + payment.id + ",\"workStatus\":\"" + escape(payment.workStatus)
                + "\",\"providerEmail\":\"" + escape(payment.providerEmail) + "\"}");
        repository.createNotification(
            payment.providerEmail,
            "TASK_STATUS_UPDATED",
            "{\"paymentId\":" + payment.id + ",\"workStatus\":\"" + escape(payment.workStatus)
                + "\",\"clientEmail\":\"" + escape(payment.clientEmail) + "\"}");
      }
      writeJsonOk(response, "Mode commande mis a jour.", "paymentId", String.valueOf(paymentId));
      return;
    }

    if ("/invoices/create".equals(path)) {
      long orderId = parseLong(request.getParameter("orderId"), -1L);
      String invoiceNumber = clean(request.getParameter("invoiceNumber"));
      String pdfUrl = clean(request.getParameter("pdfUrl"));
      if (invoiceNumber.isEmpty()) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "invoiceNumber requis.");
        return;
      }

      long id = repository.createInvoice(orderId, invoiceNumber, pdfUrl);
      writeJsonOk(response, "Facture creee.", "invoiceId", String.valueOf(id));
      return;
    }

    writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Action commerce introuvable.");
  }

  private String paymentsToJson(List<ProductFeaturesRepository.PaymentRecord> rows) {
    return "[" + rows.stream().map(p -> "{" +
        "\"id\":" + p.id + "," +
        "\"clientEmail\":\"" + escape(p.clientEmail) + "\"," +
        "\"providerEmail\":\"" + escape(p.providerEmail) + "\"," +
        "\"amount\":" + String.format(java.util.Locale.US, "%.2f", p.amount) + "," +
        "\"currency\":\"" + escape(p.currency) + "\"," +
        "\"status\":\"" + escape(p.status) + "\"," +
        "\"providerRef\":\"" + escape(p.providerRef) + "\"," +
      "\"workStatus\":\"" + escape(p.workStatus) + "\"," +
        "\"createdAt\":\"" + escape(p.createdAt) + "\"" +
        "}").collect(Collectors.joining(",")) + "]";
  }

  private String invoicesToJson(List<ProductFeaturesRepository.InvoiceRecord> rows) {
    return "[" + rows.stream().map(i -> "{" +
        "\"id\":" + i.id + "," +
        "\"orderId\":" + i.orderId + "," +
        "\"invoiceNumber\":\"" + escape(i.invoiceNumber) + "\"," +
        "\"pdfUrl\":\"" + escape(i.pdfUrl) + "\"," +
        "\"issuedAt\":\"" + escape(i.issuedAt) + "\"" +
        "}").collect(Collectors.joining(",")) + "]";
  }

  private SessionUser requireUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
    HttpSession session = request.getSession(false);
    boolean loggedIn = session != null && Boolean.TRUE.equals(session.getAttribute("loggedIn"));
    String email = loggedIn ? clean(String.valueOf(session.getAttribute("userEmail"))) : "";
    String role = loggedIn ? clean(String.valueOf(session.getAttribute("userRole"))) : "";

    if (!loggedIn || email.isEmpty() || role.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Connexion requise.");
      return null;
    }

    return new SessionUser(email, role);
  }

  private long parseLong(String value, long defaultValue) {
    try {
      return Long.parseLong(clean(value));
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  private double parseDouble(String value, double defaultValue) {
    try {
      return Double.parseDouble(clean(value));
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  private void writeJsonOk(HttpServletResponse response, String message, String key, String value) throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"message\":\"" + escape(message) + "\",\"" + escape(key) + "\":\"" + escape(value) + "\"}");
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
    final String role;

    SessionUser(String email, String role) {
      this.email = email;
      this.role = role;
    }
  }
}
