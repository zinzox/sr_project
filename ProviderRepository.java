import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;

public class ProviderRepository {

  private static final ProviderRepository INSTANCE = new ProviderRepository();
  private static final String DEFAULT_DB_URL =
      "jdbc:mysql://localhost:3306/sarbi_rohek?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
  private static final String DB_USER = "root";
  private static final String DB_PASSWORD = "AZIZ1234";

  private ProviderRepository() {
    try {
      Class.forName("com.mysql.cj.jdbc.Driver");
      initializeSchema();
    } catch (ClassNotFoundException ex) {
      throw new IllegalStateException("Driver MySQL introuvable (mysql-connector-j).", ex);
    } catch (SQLException ex) {
      throw new IllegalStateException("Impossible d'initialiser la base MySQL.", ex);
    }
  }

  public static ProviderRepository getInstance() {
    return INSTANCE;
  }

  public synchronized boolean emailExists(String email) throws IOException {
    String providerSql = "SELECT 1 FROM providers WHERE LOWER(email) = LOWER(?) LIMIT 1";
    String clientSql = "SELECT 1 FROM clients WHERE LOWER(email) = LOWER(?) LIMIT 1";
    try (Connection connection = getConnection();
        PreparedStatement providerStatement = connection.prepareStatement(providerSql);
        PreparedStatement clientStatement = connection.prepareStatement(clientSql)) {
      providerStatement.setString(1, normalizeEmail(email));
      try (ResultSet providerRs = providerStatement.executeQuery()) {
        if (providerRs.next()) {
          return true;
        }
      }

      clientStatement.setString(1, normalizeEmail(email));
      try (ResultSet clientRs = clientStatement.executeQuery()) {
        return clientRs.next();
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la verification d'email.", ex);
    }
  }

  public synchronized Account findByEmail(String email) throws IOException {
    String providerSql = """
      SELECT first_name, last_name, age, cin, service_type,
             main_activity, work_title, work_description, phone, email, password_hash, created_at, photo_url
      FROM providers
      WHERE LOWER(email) = LOWER(?)
      LIMIT 1
      """;

    String clientSql = """
      SELECT first_name, last_name, age, phone, email, password_hash, created_at
      FROM clients
      WHERE LOWER(email) = LOWER(?)
      LIMIT 1
      """;

    try (Connection connection = getConnection();
        PreparedStatement providerStatement = connection.prepareStatement(providerSql);
        PreparedStatement clientStatement = connection.prepareStatement(clientSql)) {
      String normalizedEmail = normalizeEmail(email);

      providerStatement.setString(1, normalizedEmail);
      try (ResultSet rs = providerStatement.executeQuery()) {
        if (rs.next()) {
          return new Account(
              "PROVIDER",
              rs.getString("first_name"),
              rs.getString("last_name"),
              String.valueOf(rs.getInt("age")),
              rs.getString("cin"),
              rs.getString("service_type"),
              rs.getString("main_activity"),
              rs.getString("work_title"),
              rs.getString("work_description"),
              rs.getString("phone"),
              rs.getString("email"),
              rs.getString("password_hash"),
              rs.getString("created_at"),
              rs.getString("photo_url"));
        }
      }

      clientStatement.setString(1, normalizedEmail);
      try (ResultSet rs = clientStatement.executeQuery()) {
        if (!rs.next()) {
          return null;
        }

        return new Account(
            "CLIENT",
            rs.getString("first_name"),
            rs.getString("last_name"),
            String.valueOf(rs.getInt("age")),
            "",
            "",
            "",
            "",
            "",
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("created_at"),
            "");
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture du compte.", ex);
    }
  }

  public synchronized List<Account> listAllAccounts() throws IOException {
    String sql = """
        SELECT role, first_name, last_name, age, cin, service_type,
               main_activity, work_title, work_description, phone, email, password_hash, created_at, photo_url
        FROM (
          SELECT 'PROVIDER' AS role,
                 first_name, last_name, age, cin, service_type,
                 main_activity, work_title, work_description, phone, email, password_hash, created_at, photo_url
          FROM providers

          UNION ALL

          SELECT 'CLIENT' AS role,
                 first_name, last_name, age,
                 '' AS cin,
                 '' AS service_type,
                 '' AS main_activity,
                 '' AS work_title,
                 '' AS work_description,
                 phone, email, password_hash, created_at,
                 '' AS photo_url
          FROM clients
        ) all_accounts
        ORDER BY created_at DESC
        """;

    List<Account> accounts = new ArrayList<>();

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        accounts.add(new Account(
            rs.getString("role"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            String.valueOf(rs.getInt("age")),
            rs.getString("cin"),
            rs.getString("service_type"),
            rs.getString("main_activity"),
            rs.getString("work_title"),
            rs.getString("work_description"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("created_at"),
            rs.getString("photo_url")));
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des comptes.", ex);
    }

    return accounts;
  }

  public synchronized boolean deleteByEmail(String email) throws IOException {
    String providerSql = "DELETE FROM providers WHERE LOWER(email) = LOWER(?)";
    String clientSql = "DELETE FROM clients WHERE LOWER(email) = LOWER(?)";

    try (Connection connection = getConnection();
        PreparedStatement providerStatement = connection.prepareStatement(providerSql);
        PreparedStatement clientStatement = connection.prepareStatement(clientSql)) {
      String normalizedEmail = normalizeEmail(email);
      providerStatement.setString(1, normalizedEmail);
      clientStatement.setString(1, normalizedEmail);

      int deletedProviders = providerStatement.executeUpdate();
      int deletedClients = clientStatement.executeUpdate();
      return deletedProviders > 0 || deletedClients > 0;
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la suppression du compte.", ex);
    }
  }

  public synchronized List<Account> listFirstProviders(int limit) throws IOException {
    String sql = """
     SELECT first_name, last_name, age, cin, service_type,
               main_activity, work_title, work_description, phone, email, password_hash, created_at, photo_url
        FROM providers
        ORDER BY created_at ASC
        LIMIT ?
        """;

    List<Account> accounts = new ArrayList<>();

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, Math.max(1, limit));

      try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
          accounts.add(new Account(
              "PROVIDER",
              rs.getString("first_name"),
              rs.getString("last_name"),
              String.valueOf(rs.getInt("age")),
              rs.getString("cin"),
              rs.getString("service_type"),
              rs.getString("main_activity"),
              rs.getString("work_title"),
              rs.getString("work_description"),
              rs.getString("phone"),
              rs.getString("email"),
              rs.getString("password_hash"),
              rs.getString("created_at"),
              rs.getString("photo_url")));
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des prestataires vedettes.", ex);
    }

    return accounts;
  }

  public synchronized List<Account> listAllProviders() throws IOException {
    String sql = """
     SELECT first_name, last_name, age, cin, service_type,
               main_activity, work_title, work_description, phone, email, password_hash, created_at, photo_url
        FROM providers
        ORDER BY created_at DESC
        """;

    List<Account> accounts = new ArrayList<>();

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        accounts.add(new Account(
            "PROVIDER",
            rs.getString("first_name"),
            rs.getString("last_name"),
            String.valueOf(rs.getInt("age")),
            rs.getString("cin"),
            rs.getString("service_type"),
            rs.getString("main_activity"),
            rs.getString("work_title"),
            rs.getString("work_description"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("created_at"),
            rs.getString("photo_url")));
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture de tous les prestataires.", ex);
    }

    return accounts;
  }

  public synchronized boolean updateProviderPhoto(String email, String photoUrl) throws IOException {
    String sql = "UPDATE providers SET photo_url = ? WHERE LOWER(email) = LOWER(?)";
    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sanitize(photoUrl));
      statement.setString(2, normalizeEmail(email));
      return statement.executeUpdate() > 0;
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la mise a jour de la photo.", ex);
    }
  }

  public synchronized boolean updateProviderProfile(
      String email,
      String serviceType,
      String mainActivity,
      String workTitle,
      String workDescription,
      String phone) throws IOException {
    String sql = """
        UPDATE providers
        SET service_type = ?,
            main_activity = ?,
            work_title = ?,
            work_description = ?,
            phone = ?
        WHERE LOWER(email) = LOWER(?)
        """;

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sanitize(serviceType));
      statement.setString(2, sanitize(mainActivity));
      statement.setString(3, sanitize(workTitle));
      statement.setString(4, sanitize(workDescription));
      statement.setString(5, sanitize(phone));
      statement.setString(6, normalizeEmail(email));
      return statement.executeUpdate() > 0;
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la mise a jour du profil prestataire.", ex);
    }
  }

  public synchronized void save(Account account) throws IOException {
    String providerSql = """
        INSERT INTO providers (
          first_name, last_name, age, cin, service_type,
          main_activity, work_title, work_description, phone, email, password_hash, photo_url
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    String clientSql = """
        INSERT INTO clients (
          first_name, last_name, age, phone, email, password_hash
        ) VALUES (?, ?, ?, ?, ?, ?)
        """;

    try (Connection connection = getConnection()) {
      if ("PROVIDER".equalsIgnoreCase(sanitize(account.role))) {
        try (PreparedStatement statement = connection.prepareStatement(providerSql)) {
          statement.setString(1, sanitize(account.firstName));
          statement.setString(2, sanitize(account.lastName));
          statement.setInt(3, parseAge(account.age));
          statement.setString(4, sanitize(account.cin));
          statement.setString(5, sanitize(account.serviceType));
          statement.setString(6, sanitize(account.mainActivity));
          statement.setString(7, sanitize(account.workTitle));
          statement.setString(8, sanitize(account.workDescription));
          statement.setString(9, sanitize(account.phone));
          statement.setString(10, normalizeEmail(account.email));
          statement.setString(11, sanitize(account.passwordHash));
          statement.setString(12, sanitize(account.photoUrl));
          statement.executeUpdate();
        }
      } else {
        try (PreparedStatement statement = connection.prepareStatement(clientSql)) {
          statement.setString(1, sanitize(account.firstName));
          statement.setString(2, sanitize(account.lastName));
          statement.setInt(3, parseAge(account.age));
          statement.setString(4, sanitize(account.phone));
          statement.setString(5, normalizeEmail(account.email));
          statement.setString(6, sanitize(account.passwordHash));
          statement.executeUpdate();
        }
      }
    } catch (SQLIntegrityConstraintViolationException ex) {
      throw new IllegalStateException("Email deja utilise.", ex);
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la sauvegarde du compte.", ex);
    }
  }

  public static String hashPassword(String password) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("Algorithme SHA-256 indisponible", ex);
    }
  }

  private Connection getConnection() throws SQLException {
    return DriverManager.getConnection(DEFAULT_DB_URL, DB_USER, DB_PASSWORD);
  }

  private void initializeSchema() throws SQLException {
    String sql = """
        CREATE TABLE IF NOT EXISTS providers (
          id BIGINT AUTO_INCREMENT UNIQUE,
          first_name VARCHAR(120) NOT NULL,
          last_name VARCHAR(120) NOT NULL,
          age INT NOT NULL,
          cin VARCHAR(20) NULL,
          service_type VARCHAR(120) NULL,
          main_activity VARCHAR(120) NULL,
          work_title VARCHAR(160) NULL,
          work_description TEXT NULL,
          phone VARCHAR(40) NOT NULL,
          email VARCHAR(190) NOT NULL,
          password_hash VARCHAR(128) NOT NULL,
          photo_url VARCHAR(500) NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (email)
        )
        """;

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
      ensureEmailPrimaryKey(connection);
      ensurePhotoColumn(connection);
      ensureClientsTable(connection);
      ensureClientsSchemaCompatibility(connection);
      ensureLegacyProviderRoleCompatibility(connection);
      migrateLegacyClientsFromProviders(connection);
      ensureClientProviderLinksTable(connection);
      ensureMessagesTable(connection);
    }
  }

  private void ensureEmailPrimaryKey(Connection connection) {
    // Migration helper for existing installations where id was the primary key.
    String alterSql = """
        ALTER TABLE providers
          DROP PRIMARY KEY,
          ADD PRIMARY KEY (email),
          ADD UNIQUE KEY uk_providers_id (id)
        """;

    try (PreparedStatement statement = connection.prepareStatement(alterSql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // If schema is already migrated or partially compatible, ignore.
    }
  }

  private void ensurePhotoColumn(Connection connection) {
    String alterSql = "ALTER TABLE providers ADD COLUMN photo_url VARCHAR(500) NULL";
    try (PreparedStatement statement = connection.prepareStatement(alterSql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // Column may already exist.
    }
  }

  private void ensureClientsTable(Connection connection) {
    String sql = """
        CREATE TABLE IF NOT EXISTS clients (
          email VARCHAR(190) NOT NULL,
          first_name VARCHAR(120) NOT NULL,
          last_name VARCHAR(120) NOT NULL,
          age INT NOT NULL DEFAULT 0,
          phone VARCHAR(40) NULL,
          password_hash VARCHAR(128) NOT NULL DEFAULT '',
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (email)
        )
        """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // Table may already exist or be partially compatible.
    }
  }

  private void ensureClientsSchemaCompatibility(Connection connection) {
    String addAgeSql = "ALTER TABLE clients ADD COLUMN age INT NOT NULL DEFAULT 0";
    String addPasswordSql = "ALTER TABLE clients ADD COLUMN password_hash VARCHAR(128) NOT NULL DEFAULT ''";

    try (PreparedStatement statement = connection.prepareStatement(addAgeSql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // Column may already exist.
    }

    try (PreparedStatement statement = connection.prepareStatement(addPasswordSql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // Column may already exist.
    }
  }

  private void ensureLegacyProviderRoleCompatibility(Connection connection) {
    String alterRoleSql = "ALTER TABLE providers MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'PROVIDER'";
    try (PreparedStatement statement = connection.prepareStatement(alterRoleSql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // The role column may not exist anymore, which is fine.
    }
  }

  private void migrateLegacyClientsFromProviders(Connection connection) {
    String copyLegacyClientsSql = """
        INSERT INTO clients (email, first_name, last_name, age, phone, password_hash, created_at)
        SELECT p.email, p.first_name, p.last_name, p.age, p.phone, p.password_hash, p.created_at
        FROM providers p
        WHERE p.role = 'CLIENT'
          AND NOT EXISTS (
            SELECT 1 FROM clients c WHERE LOWER(c.email) = LOWER(p.email)
          )
        """;

    String deleteLegacyClientsSql = "DELETE FROM providers WHERE role = 'CLIENT'";

    try (PreparedStatement copyStatement = connection.prepareStatement(copyLegacyClientsSql);
        PreparedStatement deleteStatement = connection.prepareStatement(deleteLegacyClientsSql)) {
      copyStatement.executeUpdate();
      deleteStatement.executeUpdate();
    } catch (SQLException ignored) {
      // If providers has no legacy role column/data, ignore.
    }
  }

  private void ensureClientProviderLinksTable(Connection connection) {
    String sql = """
        CREATE TABLE IF NOT EXISTS client_provider_links (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          client_email VARCHAR(190) NOT NULL,
          provider_email VARCHAR(190) NOT NULL,
          relation_type VARCHAR(40) NOT NULL DEFAULT 'REQUEST',
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          UNIQUE KEY uk_client_provider_relation (client_email, provider_email, relation_type),
          INDEX idx_cpl_client (client_email),
          INDEX idx_cpl_provider (provider_email),
          CONSTRAINT fk_cpl_client_email
            FOREIGN KEY (client_email) REFERENCES clients(email)
            ON UPDATE CASCADE
            ON DELETE CASCADE,
          CONSTRAINT fk_cpl_provider_email
            FOREIGN KEY (provider_email) REFERENCES providers(email)
            ON UPDATE CASCADE
            ON DELETE CASCADE
        )
        """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // Table may already exist or be partially compatible.
    }
  }

  private void ensureMessagesTable(Connection connection) {
    String sql = """
        CREATE TABLE IF NOT EXISTS messages (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          sender_email VARCHAR(190) NOT NULL,
          recipient_email VARCHAR(190) NOT NULL,
          message_text TEXT NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          INDEX idx_messages_sender (sender_email),
          INDEX idx_messages_recipient (recipient_email),
          INDEX idx_messages_created_at (created_at)
        )
        """;

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
    } catch (SQLException ignored) {
      // Table may already exist or be partially compatible.
    }
  }

  public synchronized void saveMessage(String senderEmail, String recipientEmail, String messageText)
      throws IOException {
    String sql = """
        INSERT INTO messages (sender_email, recipient_email, message_text)
        VALUES (?, ?, ?)
        """;

    try (Connection connection = getConnection()) {
      ensureMessagesTable(connection);

      try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, normalizeEmail(senderEmail));
      statement.setString(2, normalizeEmail(recipientEmail));
      statement.setString(3, sanitizeMessage(messageText));
      statement.executeUpdate();
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant l'envoi du message.", ex);
    }
  }

  public synchronized List<MessageRecord> listMessagesForUser(String userEmail) throws IOException {
    String sql = """
        SELECT sender_email, recipient_email, message_text, created_at
        FROM messages
        WHERE LOWER(sender_email) = LOWER(?) OR LOWER(recipient_email) = LOWER(?)
        ORDER BY created_at DESC
        """;

    List<MessageRecord> messages = new ArrayList<>();

    try (Connection connection = getConnection()) {
      ensureMessagesTable(connection);

      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, normalizeEmail(userEmail));
        statement.setString(2, normalizeEmail(userEmail));

        try (ResultSet rs = statement.executeQuery()) {
          while (rs.next()) {
            messages.add(new MessageRecord(
                rs.getString("sender_email"),
                rs.getString("recipient_email"),
                rs.getString("message_text"),
                rs.getString("created_at")));
          }
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des conversations.", ex);
    }

    return messages;
  }

  public synchronized List<MessageRecord> listConversation(String userAEmail, String userBEmail) throws IOException {
    String sql = """
        SELECT sender_email, recipient_email, message_text, created_at
        FROM messages
        WHERE (LOWER(sender_email) = LOWER(?) AND LOWER(recipient_email) = LOWER(?))
           OR (LOWER(sender_email) = LOWER(?) AND LOWER(recipient_email) = LOWER(?))
        ORDER BY created_at ASC
        """;

    List<MessageRecord> messages = new ArrayList<>();

    try (Connection connection = getConnection()) {
      ensureMessagesTable(connection);

      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, normalizeEmail(userAEmail));
        statement.setString(2, normalizeEmail(userBEmail));
        statement.setString(3, normalizeEmail(userBEmail));
        statement.setString(4, normalizeEmail(userAEmail));

        try (ResultSet rs = statement.executeQuery()) {
          while (rs.next()) {
            messages.add(new MessageRecord(
                rs.getString("sender_email"),
                rs.getString("recipient_email"),
                rs.getString("message_text"),
                rs.getString("created_at")));
          }
        }
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des messages.", ex);
    }

    return messages;
  }

  public synchronized List<ClientProviderLinkRecord> listClientProviderLinks() throws IOException {
    String sql = """
        SELECT client_email, provider_email, relation_type, created_at
        FROM client_provider_links
        ORDER BY created_at DESC
        """;

    List<ClientProviderLinkRecord> links = new ArrayList<>();

    try (Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        links.add(new ClientProviderLinkRecord(
            rs.getString("client_email"),
            rs.getString("provider_email"),
            rs.getString("relation_type"),
            rs.getString("created_at")));
      }
    } catch (SQLException ex) {
      throw new IOException("Erreur SQL pendant la lecture des liaisons client-prestataire.", ex);
    }

    return links;
  }

  private String sanitizeMessage(String value) {
    if (value == null) {
      return "";
    }

    return value
        .replace("\r", "")
        .trim();
  }

  private String sanitize(String value) {
    if (value == null) {
      return "";
    }

    return value.replace("\t", " ")
        .replace("\r", " ")
        .replace("\n", " ")
        .trim();
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }

  private int parseAge(String age) {
    try {
      return Integer.parseInt(age);
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  public static class Account {
    public final String role;
    public final String firstName;
    public final String lastName;
    public final String age;
    public final String cin;
    public final String serviceType;
    public final String mainActivity;
    public final String workTitle;
    public final String workDescription;
    public final String phone;
    public final String email;
    public final String passwordHash;
    public final String createdAt;
    public final String photoUrl;

    public Account(
        String role,
        String firstName,
        String lastName,
        String age,
        String cin,
        String serviceType,
        String mainActivity,
        String workTitle,
        String workDescription,
        String phone,
        String email,
        String passwordHash) {
      this(
          role,
          firstName,
          lastName,
          age,
          cin,
          serviceType,
          mainActivity,
          workTitle,
          workDescription,
          phone,
          email,
          passwordHash,
            "",
            "");
    }

    public Account(
        String role,
        String firstName,
        String lastName,
        String age,
        String cin,
        String serviceType,
        String mainActivity,
        String workTitle,
        String workDescription,
        String phone,
        String email,
        String passwordHash,
        String createdAt) {
      this(
          role,
          firstName,
          lastName,
          age,
          cin,
          serviceType,
          mainActivity,
          workTitle,
          workDescription,
          phone,
          email,
          passwordHash,
          createdAt,
          "");
    }

    public Account(
        String role,
        String firstName,
        String lastName,
        String age,
        String cin,
        String serviceType,
        String mainActivity,
        String workTitle,
        String workDescription,
        String phone,
        String email,
        String passwordHash,
        String createdAt,
        String photoUrl) {
      this.role = role;
      this.firstName = firstName;
      this.lastName = lastName;
      this.age = age;
      this.cin = cin;
      this.serviceType = serviceType;
      this.mainActivity = mainActivity;
      this.workTitle = workTitle;
      this.workDescription = workDescription;
      this.phone = phone;
      this.email = email;
      this.passwordHash = passwordHash;
      this.createdAt = createdAt;
      this.photoUrl = photoUrl;
    }
  }

  public static class MessageRecord {
    public final String senderEmail;
    public final String recipientEmail;
    public final String messageText;
    public final String createdAt;

    public MessageRecord(String senderEmail, String recipientEmail, String messageText, String createdAt) {
      this.senderEmail = senderEmail;
      this.recipientEmail = recipientEmail;
      this.messageText = messageText;
      this.createdAt = createdAt;
    }
  }

  public static class ClientProviderLinkRecord {
    public final String clientEmail;
    public final String providerEmail;
    public final String relationType;
    public final String createdAt;

    public ClientProviderLinkRecord(String clientEmail, String providerEmail, String relationType, String createdAt) {
      this.clientEmail = clientEmail;
      this.providerEmail = providerEmail;
      this.relationType = relationType;
      this.createdAt = createdAt;
    }
  }
}
