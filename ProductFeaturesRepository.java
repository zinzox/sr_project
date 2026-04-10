import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductFeaturesRepository {

  private static final ProductFeaturesRepository INSTANCE = new ProductFeaturesRepository();
  private static final String DEFAULT_DB_URL =
      "jdbc:mysql://localhost:3306/sarbi_rohek?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
  private static final String DB_USER = "root";
  private static final String DB_PASSWORD = "AZIZ1234";

  private ProductFeaturesRepository() {
    try {
      Class.forName("com.mysql.cj.jdbc.Driver");
      initializeSchema();
    } catch (Exception ex) {
      throw new IllegalStateException("Impossible d'initialiser ProductFeaturesRepository.", ex);
    }
  }

  public static ProductFeaturesRepository getInstance() {
    return INSTANCE;
  }

  public synchronized void addFavorite(String clientEmail, String providerEmail) throws IOException {
    String sql = """
        INSERT INTO client_favorites (client_email, provider_email)
        VALUES (?, ?)
        ON DUPLICATE KEY UPDATE created_at = CURRENT_TIMESTAMP
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(clientEmail));
      s.setString(2, safeEmail(providerEmail));
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant l'ajout en favoris.", ex);
    }
  }

  public synchronized void removeFavorite(String clientEmail, String providerEmail) throws IOException {
    String sql = "DELETE FROM client_favorites WHERE LOWER(client_email) = LOWER(?) AND LOWER(provider_email) = LOWER(?)";

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(clientEmail));
      s.setString(2, safeEmail(providerEmail));
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la suppression de favori.", ex);
    }
  }

  public synchronized List<String> listFavorites(String clientEmail) throws IOException {
    String sql = """
        SELECT provider_email
        FROM client_favorites
        WHERE LOWER(client_email) = LOWER(?)
        ORDER BY created_at DESC
        """;

    List<String> emails = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(clientEmail));
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          emails.add(safeEmail(rs.getString("provider_email")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des favoris.", ex);
    }

    return emails;
  }

  public synchronized void addReview(String providerEmail, String clientEmail, int rating, String commentText)
      throws IOException {
    String sql = """
        INSERT INTO provider_reviews (provider_email, client_email, rating, comment_text)
        VALUES (?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE rating = VALUES(rating), comment_text = VALUES(comment_text), created_at = CURRENT_TIMESTAMP
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(providerEmail));
      s.setString(2, safeEmail(clientEmail));
      s.setInt(3, Math.max(1, Math.min(5, rating)));
      s.setString(4, safe(commentText));
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant l'enregistrement de l'avis.", ex);
    }
  }

  public synchronized List<ReviewRecord> listReviews(String providerEmail) throws IOException {
    String sql = """
        SELECT id, provider_email, client_email, rating, comment_text, created_at
        FROM provider_reviews
        WHERE LOWER(provider_email) = LOWER(?)
        ORDER BY created_at DESC
        """;

    List<ReviewRecord> rows = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(providerEmail));
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          rows.add(new ReviewRecord(
              rs.getLong("id"),
              rs.getString("provider_email"),
              rs.getString("client_email"),
              rs.getInt("rating"),
              rs.getString("comment_text"),
              rs.getString("created_at")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des avis.", ex);
    }

    return rows;
  }

  public synchronized RatingSummary getRatingSummary(String providerEmail) throws IOException {
    String sql = "SELECT COUNT(1) AS total_reviews, COALESCE(AVG(rating),0) AS avg_rating FROM provider_reviews WHERE LOWER(provider_email)=LOWER(?)";

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(providerEmail));
      try (ResultSet rs = s.executeQuery()) {
        if (rs.next()) {
          return new RatingSummary(rs.getInt("total_reviews"), rs.getDouble("avg_rating"));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant le calcul des notes.", ex);
    }

    return new RatingSummary(0, 0.0);
  }

  public synchronized long createQuoteRequest(String clientEmail, String providerEmail, String description,
      String budget, String deadlineAt) throws IOException {
    String sql = """
        INSERT INTO quote_requests (client_email, provider_email, description, budget, deadline_at, status)
        VALUES (?, ?, ?, ?, ?, 'PENDING')
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
      s.setString(1, safeEmail(clientEmail));
      s.setString(2, safeEmail(providerEmail));
      s.setString(3, safe(description));
      s.setString(4, safe(budget));
      if (safe(deadlineAt).isEmpty()) {
        s.setNull(5, java.sql.Types.TIMESTAMP);
      } else {
        s.setString(5, safe(deadlineAt));
      }
      s.executeUpdate();

      try (ResultSet keys = s.getGeneratedKeys()) {
        if (keys.next()) {
          return keys.getLong(1);
        }
      }
      return -1L;
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la creation du devis.", ex);
    }
  }

  public synchronized void respondQuote(long quoteId, String status, String providerResponse) throws IOException {
    String normalized = normalizeQuoteStatus(status);
    String sql = """
        UPDATE quote_requests
        SET status = ?, provider_response = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, normalized);
      s.setString(2, safe(providerResponse));
      s.setLong(3, quoteId);
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la reponse au devis.", ex);
    }
  }

  public synchronized List<QuoteRecord> listQuotesForUser(String email, String role) throws IOException {
    boolean provider = "PROVIDER".equalsIgnoreCase(safe(role));
    String sql = provider
        ? "SELECT * FROM quote_requests WHERE LOWER(provider_email)=LOWER(?) ORDER BY updated_at DESC, created_at DESC"
        : "SELECT * FROM quote_requests WHERE LOWER(client_email)=LOWER(?) ORDER BY updated_at DESC, created_at DESC";

    List<QuoteRecord> rows = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(email));
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          rows.add(new QuoteRecord(
              rs.getLong("id"),
              rs.getString("client_email"),
              rs.getString("provider_email"),
              rs.getString("description"),
              rs.getString("budget"),
              rs.getString("deadline_at"),
              rs.getString("status"),
              rs.getString("provider_response"),
              rs.getString("created_at"),
              rs.getString("updated_at")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des devis.", ex);
    }

    return rows;
  }

  public synchronized void createNotification(String userEmail, String type, String payloadJson) throws IOException {
    String sql = """
        INSERT INTO notifications (user_email, type, payload_json, is_read)
        VALUES (?, ?, ?, 0)
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(userEmail));
      s.setString(2, safe(type));
      s.setString(3, safe(payloadJson));
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la creation de notification.", ex);
    }
  }

  public synchronized List<NotificationRecord> listNotifications(String userEmail) throws IOException {
    String sql = """
        SELECT id, user_email, type, payload_json, is_read, created_at
        FROM notifications
        WHERE LOWER(user_email)=LOWER(?)
        ORDER BY created_at DESC
        LIMIT 100
        """;

    List<NotificationRecord> rows = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(userEmail));
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          rows.add(new NotificationRecord(
              rs.getLong("id"),
              rs.getString("user_email"),
              rs.getString("type"),
              rs.getString("payload_json"),
              rs.getInt("is_read") == 1,
              rs.getString("created_at")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des notifications.", ex);
    }

    return rows;
  }

  public synchronized void markNotificationRead(long id, String userEmail) throws IOException {
    String sql = "UPDATE notifications SET is_read = 1 WHERE id = ? AND LOWER(user_email)=LOWER(?)";

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setLong(1, id);
      s.setString(2, safeEmail(userEmail));
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la mise a jour de notification.", ex);
    }
  }

  public synchronized DashboardSummary clientDashboard(String email) throws IOException {
    String activeQuotesSql = "SELECT COUNT(1) AS c FROM payments WHERE LOWER(client_email)=LOWER(?) AND status='CONFIRMED' AND COALESCE(work_status,'NOT_STARTED') IN ('NOT_STARTED','IN_PROGRESS')";
    String favoritesSql = "SELECT COUNT(1) AS c FROM client_favorites WHERE LOWER(client_email)=LOWER(?)";
    String spentSql = "SELECT COALESCE(SUM(amount),0) AS total FROM payments WHERE LOWER(client_email)=LOWER(?) AND status='CONFIRMED'";

    int activeQuotes = scalarInt(activeQuotesSql, safeEmail(email));
    int favorites = scalarInt(favoritesSql, safeEmail(email));
    double spent = scalarDouble(spentSql, safeEmail(email));
    return new DashboardSummary(activeQuotes, favorites, spent);
  }

  public synchronized DashboardSummary providerDashboard(String email) throws IOException {
    String pendingSql = "SELECT COUNT(1) AS c FROM payments WHERE LOWER(provider_email)=LOWER(?) AND status='CONFIRMED' AND COALESCE(work_status,'NOT_STARTED')='NOT_STARTED'";
    String inProgressSql = "SELECT COUNT(1) AS c FROM payments WHERE LOWER(provider_email)=LOWER(?) AND status='CONFIRMED' AND COALESCE(work_status,'NOT_STARTED')='IN_PROGRESS'";
    String earnedSql = "SELECT COALESCE(SUM(amount),0) AS total FROM payments WHERE LOWER(provider_email)=LOWER(?) AND status='CONFIRMED'";

    int pending = scalarInt(pendingSql, safeEmail(email));
    int inProgress = scalarInt(inProgressSql, safeEmail(email));
    double earned = scalarDouble(earnedSql, safeEmail(email));
    return new DashboardSummary(pending, inProgress, earned);
  }

  public synchronized void createSlot(String providerEmail, String startAt, String endAt) throws IOException {
    createSlotWithStatus(providerEmail, startAt, endAt, "AVAILABLE");
  }

  public synchronized void createPauseWindow(String providerEmail, String startAt, String endAt) throws IOException {
    createSlotWithStatus(providerEmail, startAt, endAt, "PAUSED");
  }

  private void createSlotWithStatus(String providerEmail, String startAt, String endAt, String status)
      throws IOException {
    String sql = """
        INSERT INTO provider_slots (provider_email, start_at, end_at, status)
        VALUES (?, ?, ?, ?)
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(providerEmail));
      s.setString(2, safe(startAt));
      s.setString(3, safe(endAt));
      s.setString(4, normalizeSlotStatus(status));
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la creation de creneau.", ex);
    }
  }

  public synchronized void deleteSlot(long slotId, String providerEmail) throws IOException {
    String sql = "DELETE FROM provider_slots WHERE id=? AND LOWER(provider_email)=LOWER(?)";

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setLong(1, slotId);
      s.setString(2, safeEmail(providerEmail));
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la suppression de creneau.", ex);
    }
  }

  public synchronized void bookSlot(long slotId, String clientEmail) throws IOException {
    String sql = """
        UPDATE provider_slots
        SET status = 'BOOKED', booked_by = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ? AND status = 'AVAILABLE'
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(clientEmail));
      s.setLong(2, slotId);
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la reservation de creneau.", ex);
    }
  }

  public synchronized List<SlotRecord> listSlots(String providerEmail) throws IOException {
    String sql = """
        SELECT id, provider_email, start_at, end_at, status, booked_by, created_at
        FROM provider_slots
        WHERE LOWER(provider_email)=LOWER(?)
        ORDER BY start_at ASC
        """;

    List<SlotRecord> rows = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(providerEmail));
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          rows.add(new SlotRecord(
              rs.getLong("id"),
              rs.getString("provider_email"),
              rs.getString("start_at"),
              rs.getString("end_at"),
              rs.getString("status"),
              rs.getString("booked_by"),
              rs.getString("created_at")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des creneaux.", ex);
    }

    return rows;
  }

  public synchronized int loyaltyBalance(String clientEmail) throws IOException {
    String sql = "SELECT COALESCE(SUM(delta_points),0) AS balance FROM loyalty_points WHERE LOWER(client_email)=LOWER(?)";

    return scalarInt(sql, safeEmail(clientEmail));
  }

  public synchronized long createPayment(String clientEmail, String providerEmail, double amount, String currency,
      String status, String providerRef) throws IOException {
    String sql = """
        INSERT INTO payments (client_email, provider_email, amount, currency, status, provider_ref, work_status)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
      s.setString(1, safeEmail(clientEmail));
      s.setString(2, safeEmail(providerEmail));
      s.setDouble(3, Math.max(0.0, amount));
      s.setString(4, safe(currency).isEmpty() ? "TND" : safe(currency).toUpperCase(Locale.ROOT));
      s.setString(5, normalizePaymentStatus(status));
      s.setString(6, safe(providerRef));
      s.setString(7, "NOT_STARTED");
      s.executeUpdate();

      try (ResultSet keys = s.getGeneratedKeys()) {
        if (keys.next()) {
          return keys.getLong(1);
        }
      }
      return -1L;
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la creation du paiement.", ex);
    }
  }

  public synchronized void confirmPayment(long paymentId, String providerRef) throws IOException {
    String sql = "UPDATE payments SET status='CONFIRMED', provider_ref=? WHERE id=?";
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safe(providerRef));
      s.setLong(2, paymentId);
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la confirmation du paiement.", ex);
    }
  }

  public synchronized void updatePaymentWorkStatus(long paymentId, String providerEmail, String role, String workStatus)
      throws IOException {
    boolean admin = "ADMIN".equalsIgnoreCase(safe(role));
    String sql = admin
        ? "UPDATE payments SET work_status=? WHERE id=?"
        : "UPDATE payments SET work_status=? WHERE id=? AND LOWER(provider_email)=LOWER(?)";

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, normalizeWorkStatus(workStatus));
      s.setLong(2, paymentId);
      if (!admin) {
        s.setString(3, safeEmail(providerEmail));
      }

      int updated = s.executeUpdate();
      if (updated <= 0) {
        throw new IOException("Aucune commande mise a jour (introuvable ou non autorisee).");
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la mise a jour du mode commande.", ex);
    }
  }

  public synchronized List<PaymentRecord> listPaymentsForUser(String email, String role) throws IOException {
    boolean provider = "PROVIDER".equalsIgnoreCase(safe(role));
    String sql = provider
        ? "SELECT id, client_email, provider_email, amount, currency, status, provider_ref, work_status, created_at FROM payments WHERE LOWER(provider_email)=LOWER(?) ORDER BY created_at DESC"
        : "SELECT id, client_email, provider_email, amount, currency, status, provider_ref, work_status, created_at FROM payments WHERE LOWER(client_email)=LOWER(?) ORDER BY created_at DESC";

    List<PaymentRecord> rows = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(email));
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          rows.add(new PaymentRecord(
              rs.getLong("id"),
              rs.getString("client_email"),
              rs.getString("provider_email"),
              rs.getDouble("amount"),
              rs.getString("currency"),
              rs.getString("status"),
              rs.getString("provider_ref"),
              rs.getString("work_status"),
              rs.getString("created_at")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des paiements.", ex);
    }

    return rows;
  }

  public synchronized PaymentRecord findPaymentById(long paymentId) throws IOException {
    String sql = """
        SELECT id, client_email, provider_email, amount, currency, status, provider_ref, work_status, created_at
        FROM payments
        WHERE id = ?
        LIMIT 1
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setLong(1, paymentId);
      try (ResultSet rs = s.executeQuery()) {
        if (rs.next()) {
          return new PaymentRecord(
              rs.getLong("id"),
              rs.getString("client_email"),
              rs.getString("provider_email"),
              rs.getDouble("amount"),
              rs.getString("currency"),
              rs.getString("status"),
              rs.getString("provider_ref"),
              rs.getString("work_status"),
              rs.getString("created_at"));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture du paiement.", ex);
    }

    return null;
  }

  public synchronized List<String> listProvidersPausedNow() throws IOException {
    String sql = """
        SELECT DISTINCT provider_email
        FROM provider_slots
        WHERE status='PAUSED'
          AND STR_TO_DATE(start_at, '%Y-%m-%d %H:%i:%s') IS NOT NULL
          AND STR_TO_DATE(end_at, '%Y-%m-%d %H:%i:%s') IS NOT NULL
          AND CURRENT_TIMESTAMP BETWEEN STR_TO_DATE(start_at, '%Y-%m-%d %H:%i:%s')
                                  AND STR_TO_DATE(end_at, '%Y-%m-%d %H:%i:%s')
        """;

    List<String> emails = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql);
        ResultSet rs = s.executeQuery()) {
      while (rs.next()) {
        emails.add(safeEmail(rs.getString("provider_email")));
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des prestataires en pause.", ex);
    }

    return emails;
  }

  public synchronized long createInvoice(long orderId, String invoiceNumber, String pdfUrl) throws IOException {
    String sql = "INSERT INTO invoices (order_id, invoice_number, pdf_url) VALUES (?, ?, ?)";
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
      if (orderId > 0) {
        s.setLong(1, orderId);
      } else {
        s.setNull(1, java.sql.Types.BIGINT);
      }
      s.setString(2, safe(invoiceNumber));
      s.setString(3, safe(pdfUrl));
      s.executeUpdate();

      try (ResultSet keys = s.getGeneratedKeys()) {
        if (keys.next()) {
          return keys.getLong(1);
        }
      }
      return -1L;
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la creation de facture.", ex);
    }
  }

  public synchronized List<InvoiceRecord> listInvoices() throws IOException {
    String sql = "SELECT id, order_id, invoice_number, pdf_url, issued_at FROM invoices ORDER BY issued_at DESC";
    List<InvoiceRecord> rows = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql);
        ResultSet rs = s.executeQuery()) {
      while (rs.next()) {
        rows.add(new InvoiceRecord(
            rs.getLong("id"),
            rs.getLong("order_id"),
            rs.getString("invoice_number"),
            rs.getString("pdf_url"),
            rs.getString("issued_at")));
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des factures.", ex);
    }

    return rows;
  }

  private int scalarInt(String sql, String emailParam) throws IOException {
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, emailParam);
      try (ResultSet rs = s.executeQuery()) {
        if (rs.next()) {
          if (hasColumn(rs, "c")) {
            return rs.getInt("c");
          }
          if (hasColumn(rs, "balance")) {
            return rs.getInt("balance");
          }
          return rs.getInt(1);
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL d'agregation.", ex);
    }

    return 0;
  }

  private double scalarDouble(String sql, String emailParam) throws IOException {
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, emailParam);
      try (ResultSet rs = s.executeQuery()) {
        if (rs.next()) {
          if (hasColumn(rs, "total")) {
            return rs.getDouble("total");
          }
          return rs.getDouble(1);
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL d'agregation.", ex);
    }

    return 0.0;
  }

  private boolean hasColumn(ResultSet rs, String name) {
    try {
      rs.findColumn(name);
      return true;
    } catch (SQLException ex) {
      return false;
    }
  }

  private String normalizeQuoteStatus(String status) {
    String s = safe(status).toUpperCase(Locale.ROOT);
    if ("PENDING".equals(s) || "ACCEPTED".equals(s) || "IN_PROGRESS".equals(s) || "DONE".equals(s)
        || "CANCELED".equals(s) || "REJECTED".equals(s)) {
      return s;
    }

    return "PENDING";
  }

  private String normalizePaymentStatus(String status) {
    String s = safe(status).toUpperCase(Locale.ROOT);
    if ("PENDING".equals(s) || "CONFIRMED".equals(s) || "FAILED".equals(s)) {
      return s;
    }

    return "PENDING";
  }

  private String normalizeSlotStatus(String status) {
    String s = safe(status).toUpperCase(Locale.ROOT);
    if ("AVAILABLE".equals(s) || "BOOKED".equals(s) || "PAUSED".equals(s)) {
      return s;
    }

    return "AVAILABLE";
  }

  private String normalizeWorkStatus(String status) {
    String s = safe(status).toUpperCase(Locale.ROOT);
    if ("NOT_STARTED".equals(s) || "IN_PROGRESS".equals(s) || "COMPLETED".equals(s)) {
      return s;
    }

    return "NOT_STARTED";
  }

  private Connection getConnection() throws SQLException {
    return DriverManager.getConnection(DEFAULT_DB_URL, DB_USER, DB_PASSWORD);
  }

  private void initializeSchema() throws SQLException {
    try (Connection c = getConnection()) {
      ensureFavoritesTable(c);
      ensureReviewsTable(c);
      ensureQuoteRequestsTable(c);
      ensureProviderSlotsTable(c);
      ensureNotificationsTable(c);
      ensurePaymentsTable(c);
      ensureInvoicesTable(c);
      ensureRecommendationsTable(c);
      ensureAttachmentsTable(c);
      ensureLoyaltyTable(c);
      ensurePaymentsWorkStatusColumn(c);
      ensureOrdersTable(c);
      ensureOrderMessagesTable(c);
    }
  }

  private void ensureFavoritesTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS client_favorites (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          client_email VARCHAR(190) NOT NULL,
          provider_email VARCHAR(190) NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE KEY uk_favorites (client_email, provider_email),
          INDEX idx_favorites_client (client_email),
          INDEX idx_favorites_provider (provider_email)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensureReviewsTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS provider_reviews (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          provider_email VARCHAR(190) NOT NULL,
          client_email VARCHAR(190) NOT NULL,
          rating INT NOT NULL,
          comment_text TEXT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE KEY uk_review_once (provider_email, client_email),
          INDEX idx_reviews_provider (provider_email)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensureQuoteRequestsTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS quote_requests (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          client_email VARCHAR(190) NOT NULL,
          provider_email VARCHAR(190) NOT NULL,
          description TEXT NOT NULL,
          budget VARCHAR(60) NULL,
          deadline_at TIMESTAMP NULL,
          status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
          provider_response TEXT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NULL,
          INDEX idx_quote_client (client_email),
          INDEX idx_quote_provider (provider_email),
          INDEX idx_quote_status (status)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensureProviderSlotsTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS provider_slots (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          provider_email VARCHAR(190) NOT NULL,
          start_at VARCHAR(40) NOT NULL,
          end_at VARCHAR(40) NOT NULL,
          status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
          booked_by VARCHAR(190) NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NULL,
          INDEX idx_slots_provider (provider_email),
          INDEX idx_slots_status (status)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensureNotificationsTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS notifications (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          user_email VARCHAR(190) NOT NULL,
          type VARCHAR(60) NOT NULL,
          payload_json TEXT NULL,
          is_read TINYINT(1) NOT NULL DEFAULT 0,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_notif_user (user_email),
          INDEX idx_notif_read (is_read)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensurePaymentsTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS payments (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          order_id BIGINT NULL,
          client_email VARCHAR(190) NOT NULL,
          provider_email VARCHAR(190) NOT NULL,
          amount DECIMAL(12,2) NOT NULL DEFAULT 0,
          currency VARCHAR(10) NOT NULL DEFAULT 'TND',
          status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
          provider_ref VARCHAR(120) NULL,
          work_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_pay_client (client_email),
          INDEX idx_pay_provider (provider_email),
          INDEX idx_pay_status (status),
          INDEX idx_pay_work_status (work_status)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensurePaymentsWorkStatusColumn(Connection c) throws SQLException {
    String sql = "ALTER TABLE payments ADD COLUMN work_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED'";
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    } catch (SQLException ex) {
      String message = ex.getMessage() == null ? "" : ex.getMessage();
      if (!message.toLowerCase(Locale.ROOT).contains("duplicate column")) {
        throw ex;
      }
    }

    String indexSql = "ALTER TABLE payments ADD INDEX idx_pay_work_status (work_status)";
    try (PreparedStatement s = c.prepareStatement(indexSql)) {
      s.execute();
    } catch (SQLException ex) {
      String message = ex.getMessage() == null ? "" : ex.getMessage();
      if (!message.toLowerCase(Locale.ROOT).contains("duplicate key")) {
        throw ex;
      }
    }
  }

  private void ensureInvoicesTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS invoices (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          order_id BIGINT NULL,
          invoice_number VARCHAR(60) NOT NULL,
          pdf_url VARCHAR(400) NULL,
          issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE KEY uk_invoice_number (invoice_number)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensureRecommendationsTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS client_recommendations (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          client_email VARCHAR(190) NOT NULL,
          provider_email VARCHAR(190) NOT NULL,
          score DOUBLE NOT NULL DEFAULT 0,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_rec_client (client_email),
          INDEX idx_rec_provider (provider_email)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensureAttachmentsTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS message_attachments (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          message_id BIGINT NOT NULL,
          file_name VARCHAR(260) NOT NULL,
          mime_type VARCHAR(120) NOT NULL,
          file_url VARCHAR(400) NOT NULL,
          size_bytes BIGINT NOT NULL DEFAULT 0,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_attach_message (message_id)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensureLoyaltyTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS loyalty_points (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          client_email VARCHAR(190) NOT NULL,
          delta_points INT NOT NULL,
          reason VARCHAR(255) NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_loyalty_client (client_email)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensureOrdersTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS orders (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          client_email VARCHAR(190) NOT NULL,
          provider_email VARCHAR(190) NOT NULL,
          title VARCHAR(255) NULL,
          description TEXT NULL,
          price DECIMAL(12,2) NULL,
          estimated_delivery_date TIMESTAMP NULL,
          revisions INT DEFAULT 3,
          status VARCHAR(30) NOT NULL DEFAULT 'draft',
          payment_status VARCHAR(20) NOT NULL DEFAULT 'pending',
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          INDEX idx_order_client (client_email),
          INDEX idx_order_provider (provider_email),
          INDEX idx_order_status (status),
          INDEX idx_order_payment (payment_status)
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    }
  }

  private void ensureOrderMessagesTable(Connection c) throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS order_messages (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          order_id BIGINT NOT NULL,
          sender_email VARCHAR(190) NOT NULL,
          message_text TEXT NOT NULL,
          attachment_url VARCHAR(400) NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_msg_order (order_id),
          INDEX idx_msg_sender (sender_email),
          FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
        )
        """;
    try (PreparedStatement s = c.prepareStatement(sql)) {
      s.execute();
    } catch (SQLException ex) {
      String msg = ex.getMessage() == null ? "" : ex.getMessage();
      if (!msg.toLowerCase(Locale.ROOT).contains("foreign key")) {
        throw ex;
      }
    }
  }

  // ========== ORDERS METHODS ==========

  public synchronized long createOrder(String clientEmail, String providerEmail) throws IOException {
    String sql = """
        INSERT INTO orders (client_email, provider_email, status, payment_status)
        VALUES (?, ?, 'draft', 'pending')
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
      s.setString(1, safeEmail(clientEmail));
      s.setString(2, safeEmail(providerEmail));
      s.executeUpdate();

      try (ResultSet keys = s.getGeneratedKeys()) {
        if (keys.next()) {
          return keys.getLong(1);
        }
      }
      return -1L;
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la création de commande.", ex);
    }
  }

  public synchronized void updateOrderDetails(long orderId, String providerEmail, String title, String description,
      Double price, String estimatedDeliveryDate, Integer revisions) throws IOException {
    String sql = """
        UPDATE orders
        SET title = ?, description = ?, price = ?, estimated_delivery_date = ?, revisions = ?, status = 'pending_details', updated_at = CURRENT_TIMESTAMP
        WHERE id = ? AND LOWER(provider_email) = LOWER(?)
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safe(title));
      s.setString(2, safe(description));
      if (price == null || price <= 0) {
        s.setNull(3, java.sql.Types.DECIMAL);
      } else {
        s.setDouble(3, price);
      }
      if (safe(estimatedDeliveryDate).isEmpty()) {
        s.setNull(4, java.sql.Types.TIMESTAMP);
      } else {
        s.setString(4, safe(estimatedDeliveryDate));
      }
      s.setInt(5, revisions == null ? 3 : Math.max(1, revisions));
      s.setLong(6, orderId);
      s.setString(7, safeEmail(providerEmail));
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la mise à jour des détails.", ex);
    }
  }

  public synchronized OrderRecord findOrderById(long orderId) throws IOException {
    String sql = """
        SELECT id, client_email, provider_email, title, description, price, estimated_delivery_date,
               revisions, status, payment_status, created_at, updated_at
        FROM orders
        WHERE id = ?
        LIMIT 1
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setLong(1, orderId);
      try (ResultSet rs = s.executeQuery()) {
        if (rs.next()) {
          return new OrderRecord(
              rs.getLong("id"),
              rs.getString("client_email"),
              rs.getString("provider_email"),
              rs.getString("title"),
              rs.getString("description"),
              rs.getDouble("price"),
              rs.getString("estimated_delivery_date"),
              rs.getInt("revisions"),
              rs.getString("status"),
              rs.getString("payment_status"),
              rs.getString("created_at"),
              rs.getString("updated_at"));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture de commande.", ex);
    }

    return null;
  }

  public synchronized List<OrderRecord> listOrdersForClient(String clientEmail) throws IOException {
    String sql = """
        SELECT id, client_email, provider_email, title, description, price, estimated_delivery_date,
               revisions, status, payment_status, created_at, updated_at
        FROM orders
        WHERE LOWER(client_email) = LOWER(?)
        ORDER BY updated_at DESC, created_at DESC
        """;

    List<OrderRecord> rows = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(clientEmail));
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          rows.add(new OrderRecord(
              rs.getLong("id"),
              rs.getString("client_email"),
              rs.getString("provider_email"),
              rs.getString("title"),
              rs.getString("description"),
              rs.getDouble("price"),
              rs.getString("estimated_delivery_date"),
              rs.getInt("revisions"),
              rs.getString("status"),
              rs.getString("payment_status"),
              rs.getString("created_at"),
              rs.getString("updated_at")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des commandes client.", ex);
    }

    return rows;
  }

  public synchronized List<OrderRecord> listOrdersForProvider(String providerEmail) throws IOException {
    String sql = """
        SELECT id, client_email, provider_email, title, description, price, estimated_delivery_date,
               revisions, status, payment_status, created_at, updated_at
        FROM orders
        WHERE LOWER(provider_email) = LOWER(?)
        ORDER BY updated_at DESC, created_at DESC
        """;

    List<OrderRecord> rows = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, safeEmail(providerEmail));
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          rows.add(new OrderRecord(
              rs.getLong("id"),
              rs.getString("client_email"),
              rs.getString("provider_email"),
              rs.getString("title"),
              rs.getString("description"),
              rs.getDouble("price"),
              rs.getString("estimated_delivery_date"),
              rs.getInt("revisions"),
              rs.getString("status"),
              rs.getString("payment_status"),
              rs.getString("created_at"),
              rs.getString("updated_at")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des commandes prestataire.", ex);
    }

    return rows;
  }

  public synchronized void updateOrderStatus(long orderId, String newStatus) throws IOException {
    String normalized = normalizeOrderStatus(newStatus);
    String sql = """
        UPDATE orders
        SET status = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, normalized);
      s.setLong(2, orderId);
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la mise à jour du statut.", ex);
    }
  }

  public synchronized void updateOrderPaymentStatus(long orderId, String paymentStatus) throws IOException {
    String normalized = normalizeOrderPaymentStatus(paymentStatus);
    String sql = """
        UPDATE orders
        SET payment_status = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, normalized);
      s.setLong(2, orderId);
      s.executeUpdate();
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la mise à jour du statut paiement.", ex);
    }
  }

  public synchronized long addOrderMessage(long orderId, String senderEmail, String messageText, String attachmentUrl)
      throws IOException {
    String sql = """
        INSERT INTO order_messages (order_id, sender_email, message_text, attachment_url)
        VALUES (?, ?, ?, ?)
        """;

    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
      s.setLong(1, orderId);
      s.setString(2, safeEmail(senderEmail));
      s.setString(3, safe(messageText));
      if (safe(attachmentUrl).isEmpty()) {
        s.setNull(4, java.sql.Types.VARCHAR);
      } else {
        s.setString(4, safe(attachmentUrl));
      }
      s.executeUpdate();

      try (ResultSet keys = s.getGeneratedKeys()) {
        if (keys.next()) {
          return keys.getLong(1);
        }
      }
      return -1L;
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant l'ajout de message.", ex);
    }
  }

  public synchronized List<OrderMessageRecord> listOrderMessages(long orderId) throws IOException {
    String sql = """
        SELECT id, order_id, sender_email, message_text, attachment_url, created_at
        FROM order_messages
        WHERE order_id = ?
        ORDER BY created_at ASC
        """;

    List<OrderMessageRecord> rows = new ArrayList<>();
    try (Connection c = getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setLong(1, orderId);
      try (ResultSet rs = s.executeQuery()) {
        while (rs.next()) {
          rows.add(new OrderMessageRecord(
              rs.getLong("id"),
              rs.getLong("order_id"),
              rs.getString("sender_email"),
              rs.getString("message_text"),
              rs.getString("attachment_url"),
              rs.getString("created_at")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des messages.", ex);
    }

    return rows;
  }

  // ========== NORMALIZATION ==========

  private String normalizeOrderStatus(String status) {
    String s = safe(status).toLowerCase(Locale.ROOT);
    if ("draft".equals(s) || "pending_details".equals(s) || "pending_payment".equals(s) || "paid".equals(s)
        || "in_progress".equals(s) || "delivered".equals(s) || "in_revision".equals(s) || "completed".equals(s)
        || "cancelled".equals(s)) {
      return s;
    }
    return "draft";
  }

  private String normalizeOrderPaymentStatus(String status) {
    String s = safe(status).toLowerCase(Locale.ROOT);
    if ("pending".equals(s) || "paid".equals(s) || "failed".equals(s)) {
      return s;
    }
    return "pending";
  }

  private String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private String safeEmail(String value) {
    return safe(value).toLowerCase(Locale.ROOT);
  }

  public static class ReviewRecord {
    public final long id;
    public final String providerEmail;
    public final String clientEmail;
    public final int rating;
    public final String commentText;
    public final String createdAt;

    public ReviewRecord(long id, String providerEmail, String clientEmail, int rating, String commentText,
        String createdAt) {
      this.id = id;
      this.providerEmail = providerEmail;
      this.clientEmail = clientEmail;
      this.rating = rating;
      this.commentText = commentText;
      this.createdAt = createdAt;
    }
  }

  public static class RatingSummary {
    public final int totalReviews;
    public final double averageRating;

    public RatingSummary(int totalReviews, double averageRating) {
      this.totalReviews = totalReviews;
      this.averageRating = averageRating;
    }
  }

  public static class OrderRecord {
    public final long id;
    public final String clientEmail;
    public final String providerEmail;
    public final String title;
    public final String description;
    public final double price;
    public final String estimatedDeliveryDate;
    public final int revisions;
    public final String status;
    public final String paymentStatus;
    public final String createdAt;
    public final String updatedAt;

    public OrderRecord(long id, String clientEmail, String providerEmail, String title, String description,
        double price, String estimatedDeliveryDate, int revisions, String status, String paymentStatus,
        String createdAt, String updatedAt) {
      this.id = id;
      this.clientEmail = clientEmail;
      this.providerEmail = providerEmail;
      this.title = title;
      this.description = description;
      this.price = price;
      this.estimatedDeliveryDate = estimatedDeliveryDate;
      this.revisions = revisions;
      this.status = status;
      this.paymentStatus = paymentStatus;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }
  }

  public static class OrderMessageRecord {
    public final long id;
    public final long orderId;
    public final String senderEmail;
    public final String messageText;
    public final String attachmentUrl;
    public final String createdAt;

    public OrderMessageRecord(long id, long orderId, String senderEmail, String messageText,
        String attachmentUrl, String createdAt) {
      this.id = id;
      this.orderId = orderId;
      this.senderEmail = senderEmail;
      this.messageText = messageText;
      this.attachmentUrl = attachmentUrl;
      this.createdAt = createdAt;
    }
  }

  public static class QuoteRecord {
    public final long id;
    public final String clientEmail;
    public final String providerEmail;
    public final String description;
    public final String budget;
    public final String deadlineAt;
    public final String status;
    public final String providerResponse;
    public final String createdAt;
    public final String updatedAt;

    public QuoteRecord(long id, String clientEmail, String providerEmail, String description, String budget,
        String deadlineAt, String status, String providerResponse, String createdAt, String updatedAt) {
      this.id = id;
      this.clientEmail = clientEmail;
      this.providerEmail = providerEmail;
      this.description = description;
      this.budget = budget;
      this.deadlineAt = deadlineAt;
      this.status = status;
      this.providerResponse = providerResponse;
      this.createdAt = createdAt;
      this.updatedAt = updatedAt;
    }
  }

  public static class NotificationRecord {
    public final long id;
    public final String userEmail;
    public final String type;
    public final String payloadJson;
    public final boolean read;
    public final String createdAt;

    public NotificationRecord(long id, String userEmail, String type, String payloadJson, boolean read,
        String createdAt) {
      this.id = id;
      this.userEmail = userEmail;
      this.type = type;
      this.payloadJson = payloadJson;
      this.read = read;
      this.createdAt = createdAt;
    }
  }

  public static class DashboardSummary {
    public final int metricA;
    public final int metricB;
    public final double amount;

    public DashboardSummary(int metricA, int metricB, double amount) {
      this.metricA = metricA;
      this.metricB = metricB;
      this.amount = amount;
    }
  }

  public static class SlotRecord {
    public final long id;
    public final String providerEmail;
    public final String startAt;
    public final String endAt;
    public final String status;
    public final String bookedBy;
    public final String createdAt;

    public SlotRecord(long id, String providerEmail, String startAt, String endAt, String status,
        String bookedBy, String createdAt) {
      this.id = id;
      this.providerEmail = providerEmail;
      this.startAt = startAt;
      this.endAt = endAt;
      this.status = status;
      this.bookedBy = bookedBy;
      this.createdAt = createdAt;
    }
  }

  public static class PaymentRecord {
    public final long id;
    public final String clientEmail;
    public final String providerEmail;
    public final double amount;
    public final String currency;
    public final String status;
    public final String providerRef;
    public final String workStatus;
    public final String createdAt;

    public PaymentRecord(long id, String clientEmail, String providerEmail, double amount, String currency,
        String status, String providerRef, String workStatus, String createdAt) {
      this.id = id;
      this.clientEmail = clientEmail;
      this.providerEmail = providerEmail;
      this.amount = amount;
      this.currency = currency;
      this.status = status;
      this.providerRef = providerRef;
      this.workStatus = workStatus;
      this.createdAt = createdAt;
    }
  }

  public static class InvoiceRecord {
    public final long id;
    public final long orderId;
    public final String invoiceNumber;
    public final String pdfUrl;
    public final String issuedAt;

    public InvoiceRecord(long id, long orderId, String invoiceNumber, String pdfUrl, String issuedAt) {
      this.id = id;
      this.orderId = orderId;
      this.invoiceNumber = invoiceNumber;
      this.pdfUrl = pdfUrl;
      this.issuedAt = issuedAt;
    }
  }
}
