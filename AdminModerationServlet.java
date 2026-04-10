import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/admin/moderation")
public class AdminModerationServlet extends HttpServlet {

  private static final String ADMIN_KEY = "AZIZ-ONLY-ADMIN-2026";

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (!isAuthorized(request)) {
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Acces refuse.");
      return;
    }

    ModerationRepository repository = ModerationRepository.getInstance();
    String statusFilter = clean(request.getParameter("status"));
    String severityFilter = clean(request.getParameter("severity"));
    String senderFilter = clean(request.getParameter("sender"));

    List<ModerationRepository.ModerationAlert> alerts = repository.listAlerts(statusFilter, severityFilter, senderFilter);
    List<ModerationRepository.SanctionRecord> blacklist = repository.listActiveBlacklist();
    ModerationRepository.DashboardMetrics metrics = repository.readDashboardMetrics();
    List<ModerationRepository.AdminActionRecord> logs = repository.listRecentAdminActions(40);

    String payload = "{"
        + "\"alerts\":" + alertsToJson(alerts) + ","
      + "\"blacklist\":" + sanctionsToJson(blacklist) + ","
      + "\"metrics\":" + metricsToJson(metrics) + ","
      + "\"logs\":" + logsToJson(logs)
        + "}";

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(payload);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (!isAuthorized(request)) {
      writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Acces refuse.");
      return;
    }

    String action = clean(request.getParameter("action")).toLowerCase();
    String email = clean(request.getParameter("email")).toLowerCase();
    String phone = clean(request.getParameter("phone"));
    String reason = clean(request.getParameter("reason"));
    long alertId = parseLong(request.getParameter("alertId"));
    String adminKey = clean(request.getParameter("key"));

    ModerationRepository moderation = ModerationRepository.getInstance();

    switch (action) {
      case "blacklist":
        if (email.isEmpty()) {
          writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Email requis.");
          return;
        }
        if (phone.isEmpty()) {
          phone = moderation.findPhoneByEmail(email);
        }
        moderation.applyBlacklist(email, phone, reason.isEmpty() ? "Decision admin" : reason);
        if (alertId > 0) {
          moderation.markAlertAction(alertId, "BLACKLIST");
        }
        moderation.logAdminAction("BLACKLIST", email, phone, reason, adminKey, alertId);
        writeJsonOk(response, "Compte ajoute a la blacklist.");
        return;

      case "tempban":
        if (email.isEmpty()) {
          writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Email requis.");
          return;
        }
        if (phone.isEmpty()) {
          phone = moderation.findPhoneByEmail(email);
        }
        moderation.applyTemporaryBan(email, phone, 5, reason.isEmpty() ? "Ban 5 min admin" : reason);
        if (alertId > 0) {
          moderation.markAlertAction(alertId, "TEMP_BAN_5_MIN");
        }
        moderation.logAdminAction("TEMP_BAN_5_MIN", email, phone, reason, adminKey, alertId);
        writeJsonOk(response, "Ban temporaire applique pour 5 minutes.");
        return;

      case "warn":
        if (email.isEmpty()) {
          writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Email requis.");
          return;
        }
        String messageText = clean(request.getParameter("messageText"));
        String warning = OllamaModerationService.getInstance()
            .generateWarningEmailText(messageText, reason.isEmpty() ? "contenu inapproprie" : reason);
        SimpleSmtpMailer.sendPlainEmail(email, "Avertissement moderation - Sarbi Rohek", warning);
        if (alertId > 0) {
          moderation.markAlertAction(alertId, "WARN_EMAIL");
        }
        moderation.logAdminAction("WARN_EMAIL", email, phone, reason, adminKey, alertId);
        writeJsonOk(response, "Avertissement IA envoye par email.");
        return;

      case "unblacklist":
        if (email.isEmpty()) {
          writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Email requis.");
          return;
        }
        moderation.removeBlacklist(email);
        moderation.logAdminAction("UNBLACKLIST", email, phone, reason, adminKey, alertId);
        writeJsonOk(response, "Compte retire de la blacklist.");
        return;

      case "resolve":
        if (alertId <= 0) {
          writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "alertId requis.");
          return;
        }
        moderation.resolveAlert(alertId);
        moderation.logAdminAction("RESOLVE", email, phone, reason, adminKey, alertId);
        writeJsonOk(response, "Alerte resolue.");
        return;

      default:
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Action inconnue.");
    }
  }

  private boolean isAuthorized(HttpServletRequest request) {
    String key = clean(request.getParameter("key"));
    return ADMIN_KEY.equals(key);
  }

  private String alertsToJson(List<ModerationRepository.ModerationAlert> alerts) {
    return "[" + alerts.stream().map(a -> "{"
        + "\"id\":" + a.id + ","
        + "\"senderEmail\":\"" + escape(a.senderEmail) + "\","
        + "\"senderPhone\":\"" + escape(a.senderPhone) + "\","
        + "\"recipientEmail\":\"" + escape(a.recipientEmail) + "\","
        + "\"messageText\":\"" + escape(a.messageText) + "\","
        + "\"categories\":\"" + escape(a.categories) + "\","
        + "\"severity\":\"" + escape(a.severity) + "\","
        + "\"riskScore\":" + a.riskScore + ","
        + "\"reason\":\"" + escape(a.reason) + "\","
        + "\"model\":\"" + escape(a.model) + "\","
        + "\"status\":\"" + escape(a.status) + "\","
        + "\"createdAt\":\"" + escape(a.createdAt) + "\","
        + "\"actionTaken\":\"" + escape(a.actionTaken) + "\""
        + "}").collect(Collectors.joining(",")) + "]";
  }

  private String sanctionsToJson(List<ModerationRepository.SanctionRecord> rows) {
    return "[" + rows.stream().map(s -> "{"
        + "\"id\":" + s.id + ","
        + "\"email\":\"" + escape(s.email) + "\","
        + "\"phone\":\"" + escape(s.phone) + "\","
        + "\"sanctionType\":\"" + escape(s.sanctionType) + "\","
        + "\"reason\":\"" + escape(s.reason) + "\","
        + "\"startsAt\":\"" + escape(s.startsAt) + "\","
        + "\"endsAt\":\"" + escape(s.endsAt) + "\""
        + "}").collect(Collectors.joining(",")) + "]";
  }

        private String metricsToJson(ModerationRepository.DashboardMetrics metrics) {
          return "{"
          + "\"openAlerts\":" + metrics.openAlerts + ","
          + "\"highAlertsToday\":" + metrics.highAlertsToday + ","
          + "\"activeTempBans\":" + metrics.activeTempBans
          + "}";
        }

        private String logsToJson(List<ModerationRepository.AdminActionRecord> logs) {
          return "[" + logs.stream().map(log -> "{"
          + "\"id\":" + log.id + ","
          + "\"action\":\"" + escape(log.action) + "\","
          + "\"targetEmail\":\"" + escape(log.targetEmail) + "\","
          + "\"targetPhone\":\"" + escape(log.targetPhone) + "\","
          + "\"reason\":\"" + escape(log.reason) + "\","
          + "\"adminKey\":\"" + escape(log.adminKey) + "\","
          + "\"alertId\":" + log.alertId + ","
          + "\"createdAt\":\"" + escape(log.createdAt) + "\""
          + "}").collect(Collectors.joining(",")) + "]";
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

  private long parseLong(String value) {
    try {
      return Long.parseLong(clean(value));
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
}
