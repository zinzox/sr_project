import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class SimpleSmtpMailer {

  private static final String SMTP_HOST = envOrDefault("SMTP_HOST", "");
  private static final int SMTP_PORT = parsePort(envOrDefault("SMTP_PORT", "465"));
  private static final String SMTP_USER = envOrDefault("SMTP_USER", "");
  private static final String SMTP_PASSWORD = normalizeAppPassword(envOrDefault("SMTP_PASSWORD", ""));
  private static final String SMTP_FROM = envOrDefault("SMTP_FROM", SMTP_USER);

  private SimpleSmtpMailer() {
    // Utility class.
  }

  public static void sendVerificationCode(String recipientEmail, String code) throws IOException {
    String subject = "Code de verification Sarbi Rohek";
    String body = "Bonjour,\r\n\r\n"
        + "Votre code de verification est : " + code + "\r\n"
        + "Ce code expire dans 10 minutes.\r\n\r\n"
        + "Equipe Sarbi Rohek";
    sendPlainEmail(recipientEmail, subject, body);
  }

  public static void sendPlainEmail(String recipientEmail, String subject, String body) throws IOException {
    if (SMTP_HOST.isEmpty() || SMTP_USER.isEmpty() || SMTP_PASSWORD.isEmpty() || SMTP_FROM.isEmpty()) {
      throw new IOException("Configuration SMTP manquante. Definir SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, SMTP_FROM.");
    }

    SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    try (SSLSocket socket = (SSLSocket) factory.createSocket(SMTP_HOST, SMTP_PORT);
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {

      expectCode(reader, 220);
      sendCommand(writer, "EHLO sarbi-rohek.local");
      expectCode(reader, 250);

      sendCommand(writer, "AUTH LOGIN");
      expectCode(reader, 334);

      sendCommand(writer, base64(SMTP_USER));
      expectCode(reader, 334);

      sendCommand(writer, base64(SMTP_PASSWORD));
      expectCode(reader, 235);

      sendCommand(writer, "MAIL FROM:<" + SMTP_FROM + ">");
      expectCode(reader, 250);

      sendCommand(writer, "RCPT TO:<" + recipientEmail + ">");
      expectCode(reader, 250);

      sendCommand(writer, "DATA");
      expectCode(reader, 354);

      writer.write("From: " + SMTP_FROM + "\r\n");
      writer.write("To: " + recipientEmail + "\r\n");
      writer.write("Subject: " + subject + "\r\n");
      writer.write("MIME-Version: 1.0\r\n");
      writer.write("Content-Type: text/plain; charset=UTF-8\r\n");
      writer.write("\r\n");
      writer.write(body);
      writer.write("\r\n.\r\n");
      writer.flush();

      expectCode(reader, 250);
      sendCommand(writer, "QUIT");
      expectCode(reader, 221);
    }
  }

  private static void sendCommand(OutputStreamWriter writer, String command) throws IOException {
    writer.write(command + "\r\n");
    writer.flush();
  }

  private static void expectCode(BufferedReader reader, int expectedCode) throws IOException {
    String line;
    int receivedCode = -1;
    String lastLine = "";

    while ((line = reader.readLine()) != null) {
      lastLine = line;
      if (line.length() >= 3) {
        try {
          receivedCode = Integer.parseInt(line.substring(0, 3));
        } catch (NumberFormatException ignored) {
          // Keep reading.
        }
      }

      if (line.length() >= 4 && line.charAt(3) == '-') {
        continue;
      }

      break;
    }

    if (receivedCode != expectedCode) {
      throw new IOException("Erreur SMTP: attendu " + expectedCode + ", recu " + receivedCode + " (" + lastLine + ").");
    }
  }

  private static String base64(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String envOrDefault(String key, String defaultValue) {
    String value = System.getenv(key);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }

    return value.trim();
  }

  private static String normalizeAppPassword(String value) {
    if (value == null) {
      return "";
    }

    // Google app passwords are often shown in groups with spaces.
    return value.replace(" ", "").trim();
  }

  private static int parsePort(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      return 465;
    }
  }
}
