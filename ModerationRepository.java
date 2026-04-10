import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public class ModerationRepository {

  private static final ModerationRepository INSTANCE = new ModerationRepository();
  private static final String DEFAULT_DB_URL =
      "jdbc:mysql://localhost:3306/sarbi_rohek?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
  private static final String DB_USER = "root";
  private static final String DB_PASSWORD = "AZIZ1234";

  private ModerationRepository() {
    try {
      Class.forName("com.mysql.cj.jdbc.Driver");
      initializeSchema();
    } catch (Exception ex) {
      throw new IllegalStateException("Impossible d'initialiser le module de moderation.", ex);
    }
  }

  public static ModerationRepository getInstance() {
    return INSTANCE;
  }

  public synchronized boolean isBlacklisted(String email, String phone) throws IOException {
    String sql = """
        SELECT 1
        FROM account_sanctions
        WHERE active = 1
          AND sanction_type = 'BLACKLIST'
          AND (
            LOWER(email) = LOWER(?)
            OR (phone IS NOT NULL AND phone <> '' AND phone = ?)
          )
        LIMIT 1
        """;

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, safeEmail(email));
      statement.setString(2, safe(phone));
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la verification blacklist.", ex);
    }
  }

  public synchronized boolean isTemporarilyBanned(String email) throws IOException {
    String sql = """
        SELECT 1
        FROM account_sanctions
        WHERE active = 1
          AND sanction_type = 'TEMP_BAN'
          AND LOWER(email) = LOWER(?)
          AND (ends_at IS NULL OR ends_at > CURRENT_TIMESTAMP)
        LIMIT 1
        """;

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, safeEmail(email));
      try (ResultSet rs = statement.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la verification de ban temporaire.", ex);
    }
  }

  public synchronized void saveAlert(
      String senderEmail,
      String senderPhone,
      String recipientEmail,
      String messageText,
      String categories,
      String severity,
      String reason,
      String model) throws IOException {
    saveAlert(senderEmail, senderPhone, recipientEmail, messageText, categories, severity, 0, reason, model);
  }

  public synchronized void saveAlert(
      String senderEmail,
      String senderPhone,
      String recipientEmail,
      String messageText,
      String categories,
      String severity,
      int riskScore,
      String reason,
      String model) throws IOException {
    String sql = """
        INSERT INTO message_alerts (
          sender_email, sender_phone, recipient_email, message_text,
          categories, severity, risk_score, reason, model
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, safeEmail(senderEmail));
      statement.setString(2, safe(senderPhone));
      statement.setString(3, safeEmail(recipientEmail));
      statement.setString(4, safe(messageText));
      statement.setString(5, safe(categories));
      statement.setString(6, normalizeSeverity(severity));
      statement.setInt(7, normalizeRiskScore(riskScore));
      statement.setString(8, safe(reason));
      statement.setString(9, safe(model));
      statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant l'enregistrement de l'alerte.", ex);
    }
  }

  public synchronized List<ModerationAlert> listOpenAlerts() throws IOException {
    return listAlerts("OPEN", "", "");
  }

  public synchronized List<ModerationAlert> listAlerts(String status, String severity, String senderEmail)
      throws IOException {
    StringBuilder sql = new StringBuilder("""
        SELECT id, sender_email, sender_phone, recipient_email, message_text,
               categories, severity, risk_score, reason, model, status, created_at, action_taken
        FROM message_alerts
        """);
    List<String> where = new ArrayList<>();
    List<String> params = new ArrayList<>();

    String cleanStatus = safe(status).toUpperCase(Locale.ROOT);
    if (!cleanStatus.isEmpty() && !"ALL".equals(cleanStatus)) {
      where.add("status = ?");
      params.add(cleanStatus);
    }

    String cleanSeverity = normalizeSeverity(safe(severity));
    if (!safe(severity).isEmpty() && !"ALL".equals(safe(severity).toUpperCase(Locale.ROOT))) {
      where.add("severity = ?");
      params.add(cleanSeverity);
    }

    String cleanSender = safeEmail(senderEmail);
    if (!cleanSender.isEmpty()) {
      where.add("LOWER(sender_email) LIKE ?");
      params.add("%" + cleanSender + "%");
    }

    if (!where.isEmpty()) {
      StringJoiner joiner = new StringJoiner(" AND ");
      for (String clause : where) {
        joiner.add(clause);
      }
      sql.append(" WHERE ").append(joiner);
    }

    sql.append(" ORDER BY created_at DESC");

    List<ModerationAlert> alerts = new ArrayList<>();
    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql.toString())) {
      for (int i = 0; i < params.size(); i++) {
        statement.setString(i + 1, params.get(i));
      }

      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          alerts.add(new ModerationAlert(
              rs.getLong("id"),
              rs.getString("sender_email"),
              rs.getString("sender_phone"),
              rs.getString("recipient_email"),
              rs.getString("message_text"),
              rs.getString("categories"),
              rs.getString("severity"),
              rs.getInt("risk_score"),
              rs.getString("reason"),
              rs.getString("model"),
              rs.getString("status"),
              rs.getString("created_at"),
              rs.getString("action_taken")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des alertes.", ex);
    }

    return alerts;
  }

  public synchronized int countRecentAlertsForSender(String senderEmail, int lookbackHours) throws IOException {
    String sql = """
        SELECT COUNT(1) AS c
        FROM message_alerts
        WHERE LOWER(sender_email) = LOWER(?)
          AND created_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? HOUR)
        """;

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, safeEmail(senderEmail));
      statement.setInt(2, Math.max(1, lookbackHours));
      try (ResultSet rs = statement.executeQuery()) {
        if (rs.next()) {
          return rs.getInt("c");
        }
        return 0;
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant le calcul de l'escalade.", ex);
    }
  }

  public synchronized DashboardMetrics readDashboardMetrics() throws IOException {
    String openSql = "SELECT COUNT(1) AS c FROM message_alerts WHERE status = 'OPEN'";
    String highTodaySql = """
        SELECT COUNT(1) AS c
        FROM message_alerts
        WHERE severity = 'HIGH'
          AND DATE(created_at) = CURRENT_DATE
        """;
    String banSql = """
        SELECT COUNT(1) AS c
        FROM account_sanctions
        WHERE active = 1
          AND sanction_type = 'TEMP_BAN'
          AND (ends_at IS NULL OR ends_at > CURRENT_TIMESTAMP)
        """;

    int open = scalarCount(openSql);
    int highToday = scalarCount(highTodaySql);
    int activeTempBans = scalarCount(banSql);
    return new DashboardMetrics(open, highToday, activeTempBans);
  }

  public synchronized void logAdminAction(
      String action,
      String targetEmail,
      String targetPhone,
      String reason,
      String adminKey,
      long alertId) throws IOException {
    String sql = """
        INSERT INTO moderation_audit_logs (
          action, target_email, target_phone, reason, admin_key, alert_id
        ) VALUES (?, ?, ?, ?, ?, ?)
        """;

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, safe(action));
      statement.setString(2, safeEmail(targetEmail));
      statement.setString(3, safe(targetPhone));
      statement.setString(4, safe(reason));
      statement.setString(5, maskAdminKey(adminKey));
      if (alertId > 0) {
        statement.setLong(6, alertId);
      } else {
        statement.setNull(6, java.sql.Types.BIGINT);
      }
      statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant l'audit admin.", ex);
    }
  }

  public synchronized List<AdminActionRecord> listRecentAdminActions(int maxRows) throws IOException {
    String sql = """
        SELECT id, action, target_email, target_phone, reason, admin_key, alert_id, created_at
        FROM moderation_audit_logs
        ORDER BY created_at DESC
        LIMIT ?
        """;

    List<AdminActionRecord> rows = new ArrayList<>();
    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, Math.max(1, Math.min(100, maxRows)));
      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          rows.add(new AdminActionRecord(
              rs.getLong("id"),
              rs.getString("action"),
              rs.getString("target_email"),
              rs.getString("target_phone"),
              rs.getString("reason"),
              rs.getString("admin_key"),
              rs.getLong("alert_id"),
              rs.getString("created_at")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des audits.", ex);
    }

    return rows;
  }

  public synchronized List<SanctionRecord> listActiveBlacklist() throws IOException {
    String sql = """
        SELECT id, email, phone, sanction_type, reason, starts_at, ends_at
        FROM account_sanctions
        WHERE active = 1
          AND sanction_type = 'BLACKLIST'
        ORDER BY created_at DESC
        """;

    List<SanctionRecord> rows = new ArrayList<>();
    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        rows.add(new SanctionRecord(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("sanction_type"),
            rs.getString("reason"),
            rs.getString("starts_at"),
            rs.getString("ends_at")));
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture de la blacklist.", ex);
    }

    return rows;
  }

  public synchronized void applyBlacklist(String email, String phone, String reason) throws IOException {
    String disableSql = """
        UPDATE account_sanctions
        SET active = 0
        WHERE sanction_type = 'BLACKLIST'
          AND active = 1
          AND LOWER(email) = LOWER(?)
        """;

    String insertSql = """
        INSERT INTO account_sanctions (email, phone, sanction_type, reason, active, starts_at)
        VALUES (?, ?, 'BLACKLIST', ?, 1, CURRENT_TIMESTAMP)
        """;

    try (Connection connection = getConnection();
        PreparedStatement disableStmt = connection.prepareStatement(disableSql);
        PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
      disableStmt.setString(1, safeEmail(email));
      disableStmt.executeUpdate();

      insertStmt.setString(1, safeEmail(email));
      insertStmt.setString(2, safe(phone));
      insertStmt.setString(3, safe(reason));
      insertStmt.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant l'ajout en blacklist.", ex);
    }
  }

  public synchronized void removeBlacklist(String email) throws IOException {
    String sql = """
        UPDATE account_sanctions
        SET active = 0
        WHERE sanction_type = 'BLACKLIST'
          AND active = 1
          AND LOWER(email) = LOWER(?)
        """;

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, safeEmail(email));
      statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant le retrait de blacklist.", ex);
    }
  }

  public synchronized void applyTemporaryBan(String email, String phone, int minutes, String reason) throws IOException {
    String disableSql = """
        UPDATE account_sanctions
        SET active = 0
        WHERE sanction_type = 'TEMP_BAN'
          AND active = 1
          AND LOWER(email) = LOWER(?)
        """;

    String insertSql = """
        INSERT INTO account_sanctions (email, phone, sanction_type, reason, active, starts_at, ends_at)
      VALUES (?, ?, 'TEMP_BAN', ?, 1, CURRENT_TIMESTAMP, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? MINUTE))
        """;

    try (Connection connection = getConnection();
        PreparedStatement disableStmt = connection.prepareStatement(disableSql);
        PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
      disableStmt.setString(1, safeEmail(email));
      disableStmt.executeUpdate();

      insertStmt.setString(1, safeEmail(email));
      insertStmt.setString(2, safe(phone));
      insertStmt.setString(3, safe(reason));
      insertStmt.setInt(4, Math.max(1, minutes));
      insertStmt.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant l'application du ban temporaire.", ex);
    }
  }

  public synchronized void markAlertAction(long alertId, String action) throws IOException {
    String sql = """
        UPDATE message_alerts
        SET status = 'ACTIONED',
            action_taken = ?,
            resolved_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """;

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, safe(action));
      statement.setLong(2, alertId);
      statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la mise a jour de l'alerte.", ex);
    }
  }

  public synchronized void resolveAlert(long alertId) throws IOException {
    String sql = """
        UPDATE message_alerts
        SET status = 'RESOLVED',
            resolved_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """;

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, alertId);
      statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la resolution de l'alerte.", ex);
    }
  }

  public synchronized String findPhoneByEmail(String email) throws IOException {
    String providerSql = "SELECT phone FROM providers WHERE LOWER(email) = LOWER(?) LIMIT 1";
    String clientSql = "SELECT phone FROM clients WHERE LOWER(email) = LOWER(?) LIMIT 1";

    try (Connection connection = getConnection();
        PreparedStatement providerStmt = connection.prepareStatement(providerSql);
        PreparedStatement clientStmt = connection.prepareStatement(clientSql)) {
      providerStmt.setString(1, safeEmail(email));
      try (ResultSet rs = providerStmt.executeQuery()) {
        if (rs.next()) {
          return safe(rs.getString("phone"));
        }
      }

      clientStmt.setString(1, safeEmail(email));
      try (ResultSet rs = clientStmt.executeQuery()) {
        if (rs.next()) {
          return safe(rs.getString("phone"));
        }
      }

      return "";
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture du telephone.", ex);
    }
  }

  private Connection getConnection() throws SQLException {
    return DriverManager.getConnection(DEFAULT_DB_URL, DB_USER, DB_PASSWORD);
  }

  private void initializeSchema() throws SQLException {
    try (Connection connection = getConnection()) {
      ensureAlertsTable(connection);
      ensureAlertsRiskColumn(connection);
      ensureSanctionsTable(connection);
      ensureAuditTable(connection);
    }
  }

  private void ensureAlertsTable(Connection connection) {
    String sql = """
        CREATE TABLE IF NOT EXISTS message_alerts (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          sender_email VARCHAR(190) NOT NULL,
          sender_phone VARCHAR(40) NULL,
          recipient_email VARCHAR(190) NOT NULL,
          message_text TEXT NOT NULL,
          categories VARCHAR(500) NOT NULL,
          severity VARCHAR(20) NOT NULL,
          risk_score INT NOT NULL DEFAULT 0,
          reason TEXT NULL,
          model VARCHAR(120) NULL,
          status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
          action_taken VARCHAR(60) NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          resolved_at TIMESTAMP NULL,
          INDEX idx_alerts_status (status),
          INDEX idx_alerts_sender (sender_email),
          INDEX idx_alerts_created_at (created_at)
        )
        """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // May already exist.
    }
  }

  private void ensureAlertsRiskColumn(Connection connection) {
    String sql = "ALTER TABLE message_alerts ADD COLUMN risk_score INT NOT NULL DEFAULT 0 AFTER severity";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // Column may already exist.
    }
  }

  private void ensureSanctionsTable(Connection connection) {
    String sql = """
        CREATE TABLE IF NOT EXISTS account_sanctions (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          email VARCHAR(190) NOT NULL,
          phone VARCHAR(40) NULL,
          sanction_type VARCHAR(20) NOT NULL,
          reason VARCHAR(255) NULL,
          active TINYINT(1) NOT NULL DEFAULT 1,
          starts_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          ends_at TIMESTAMP NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_sanctions_email (email),
          INDEX idx_sanctions_type_active (sanction_type, active),
          INDEX idx_sanctions_ends_at (ends_at)
        )
        """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // May already exist.
    }
  }

  private void ensureAuditTable(Connection connection) {
    String sql = """
        CREATE TABLE IF NOT EXISTS moderation_audit_logs (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          action VARCHAR(60) NOT NULL,
          target_email VARCHAR(190) NULL,
          target_phone VARCHAR(40) NULL,
          reason VARCHAR(255) NULL,
          admin_key VARCHAR(40) NULL,
          alert_id BIGINT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_audit_created (created_at),
          INDEX idx_audit_target (target_email)
        )
        """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // May already exist.
    }
  }

  private String normalizeSeverity(String severity) {
    String value = safe(severity).toUpperCase(Locale.ROOT);
    if ("HIGH".equals(value) || "MEDIUM".equals(value) || "LOW".equals(value)) {
      return value;
    }

    return "LOW";
  }

  private int normalizeRiskScore(int riskScore) {
    if (riskScore < 0) {
      return 0;
    }
    if (riskScore > 100) {
      return 100;
    }
    return riskScore;
  }

  private int scalarCount(String sql) throws IOException {
    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery()) {
      if (rs.next()) {
        return rs.getInt("c");
      }
      return 0;
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant le calcul des indicateurs.", ex);
    }
  }

  private String maskAdminKey(String adminKey) {
    String value = safe(adminKey);
    if (value.length() <= 6) {
      return "***";
    }
    return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
  }

  private String safe(String value) {
    if (value == null) {
      return "";
    }

    return value.trim();
  }

  private String safeEmail(String email) {
    return safe(email).toLowerCase(Locale.ROOT);
  }

  public static class ModerationAlert {
    public final long id;
    public final String senderEmail;
    public final String senderPhone;
    public final String recipientEmail;
    public final String messageText;
    public final String categories;
    public final String severity;
    public final int riskScore;
    public final String reason;
    public final String model;
    public final String status;
    public final String createdAt;
    public final String actionTaken;

    public ModerationAlert(
        long id,
        String senderEmail,
        String senderPhone,
        String recipientEmail,
        String messageText,
        String categories,
        String severity,
        int riskScore,
        String reason,
        String model,
        String status,
        String createdAt,
        String actionTaken) {
      this.id = id;
      this.senderEmail = senderEmail;
      this.senderPhone = senderPhone;
      this.recipientEmail = recipientEmail;
      this.messageText = messageText;
      this.categories = categories;
      this.severity = severity;
      this.riskScore = riskScore;
      this.reason = reason;
      this.model = model;
      this.status = status;
      this.createdAt = createdAt;
      this.actionTaken = actionTaken;
    }
  }

  public static class SanctionRecord {
    public final long id;
    public final String email;
    public final String phone;
    public final String sanctionType;
    public final String reason;
    public final String startsAt;
    public final String endsAt;

    public SanctionRecord(long id, String email, String phone, String sanctionType, String reason,
        String startsAt, String endsAt) {
      this.id = id;
      this.email = email;
      this.phone = phone;
      this.sanctionType = sanctionType;
      this.reason = reason;
      this.startsAt = startsAt;
      this.endsAt = endsAt;
    }
  }

  public static class DashboardMetrics {
    public final int openAlerts;
    public final int highAlertsToday;
    public final int activeTempBans;

    public DashboardMetrics(int openAlerts, int highAlertsToday, int activeTempBans) {
      this.openAlerts = openAlerts;
      this.highAlertsToday = highAlertsToday;
      this.activeTempBans = activeTempBans;
    }
  }

  public static class AdminActionRecord {
    public final long id;
    public final String action;
    public final String targetEmail;
    public final String targetPhone;
    public final String reason;
    public final String adminKey;
    public final long alertId;
    public final String createdAt;

    public AdminActionRecord(long id, String action, String targetEmail, String targetPhone,
        String reason, String adminKey, long alertId, String createdAt) {
      this.id = id;
      this.action = action;
      this.targetEmail = targetEmail;
      this.targetPhone = targetPhone;
      this.reason = reason;
      this.adminKey = adminKey;
      this.alertId = alertId;
      this.createdAt = createdAt;
    }
  }
}
