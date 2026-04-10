import java.io.IOException;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/api/orders/*")
public class OrdersServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    SessionUser user = requireUser(request, response);
    if (user == null) {
      return;
    }

    String path = request.getPathInfo() == null ? "" : request.getPathInfo();
    ProductFeaturesRepository repository = ProductFeaturesRepository.getInstance();

    if ("/list".equals(path)) {
      List<ProductFeaturesRepository.OrderRecord> orders = "PROVIDER".equalsIgnoreCase(user.role)
          ? repository.listOrdersForProvider(user.email)
          : repository.listOrdersForClient(user.email);
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{\"orders\":" + ordersToJson(orders) + "}");
      return;
    }

    if (path.startsWith("/detail/")) {
      long orderId = parseLong(path.substring(8), -1);
      if (orderId <= 0) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "ID commande invalide.");
        return;
      }

      ProductFeaturesRepository.OrderRecord order = repository.findOrderById(orderId);
      if (order == null) {
        writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Commande introuvable.");
        return;
      }

      if (!isOrderOwner(order, user)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Accès non autorisé.");
        return;
      }

      List<ProductFeaturesRepository.OrderMessageRecord> messages = repository.listOrderMessages(orderId);
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      String json = "{\"order\":" + orderToJson(order) + ",\"messages\":" + messagesToJson(messages) + "}";
      response.getWriter().write(json);
      return;
    }

    if ("/requests".equals(path)) {
      if (!"PROVIDER".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Réservé aux prestataires.");
        return;
      }

      List<ProductFeaturesRepository.OrderRecord> requests = repository.listOrdersForProvider(user.email);
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().write("{\"requests\":" + ordersToJson(requests) + "}");
      return;
    }

    writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Endpoint introuvable.");
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

    if ("/create".equals(path)) {
      if (!"CLIENT".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Réservé aux clients.");
        return;
      }

      String providerEmail = clean(request.getParameter("providerEmail"));
      if (providerEmail.isEmpty()) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "providerEmail requis.");
        return;
      }

      try {
        long orderId = repository.createOrder(user.email, providerEmail);
        ProductFeaturesRepository.OrderRecord order = repository.findOrderById(orderId);
        repository.createNotification(providerEmail, "ORDER_REQUEST",
            "{\"clientEmail\":\"" + user.email + "\",\"orderId\":" + orderId + "}");

        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":true,\"orderId\":" + orderId + "}");
      } catch (IOException ex) {
        writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur lors de la création.");
      }
      return;
    }

    if ("/fill-details".equals(path)) {
      if (!"PROVIDER".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Réservé aux prestataires.");
        return;
      }

      long orderId = parseLong(request.getParameter("orderId"), -1);
      String title = clean(request.getParameter("title"));
      String description = clean(request.getParameter("description"));
      double price = parseDouble(request.getParameter("price"), 0.0);
      String estimatedDeliveryDate = clean(request.getParameter("estimatedDeliveryDate"));
      int revisions = parseInt(request.getParameter("revisions"), 3);

      if (orderId <= 0 || title.isEmpty() || description.isEmpty() || price <= 0) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Paramètres invalides.");
        return;
      }

      try {
        ProductFeaturesRepository.OrderRecord order = repository.findOrderById(orderId);
        if (order == null) {
          writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Commande introuvable.");
          return;
        }

        if (!user.email.equalsIgnoreCase(order.providerEmail)) {
          writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Accès non autorisé.");
          return;
        }

        repository.updateOrderDetails(orderId, user.email, title, description, price, estimatedDeliveryDate,
            revisions);
        repository.updateOrderStatus(orderId, "pending_payment");
        repository.createNotification(order.clientEmail, "ORDER_READY",
            "{\"orderId\":" + orderId + ",\"title\":\"" + title + "\",\"price\":" + price + "}");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":true}");
      } catch (IOException ex) {
        writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur lors de la mise à jour.");
      }
      return;
    }

    if ("/pay".equals(path)) {
      if (!"CLIENT".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Réservé aux clients.");
        return;
      }

      long orderId = parseLong(request.getParameter("orderId"), -1);
      if (orderId <= 0) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "orderId invalide.");
        return;
      }

      try {
        ProductFeaturesRepository.OrderRecord order = repository.findOrderById(orderId);
        if (order == null) {
          writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Commande introuvable.");
          return;
        }

        if (!user.email.equalsIgnoreCase(order.clientEmail)) {
          writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Accès non autorisé.");
          return;
        }

        if (!"pending_payment".equalsIgnoreCase(order.status)) {
          writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Commande non payable dans cet état.");
          return;
        }

        repository.updateOrderPaymentStatus(orderId, "paid");
        repository.updateOrderStatus(orderId, "paid");
        repository.createNotification(order.providerEmail, "ORDER_PAID",
            "{\"orderId\":" + orderId + ",\"clientEmail\":\"" + user.email + "\"}");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":true}");
      } catch (IOException ex) {
        writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur lors du paiement.");
      }
      return;
    }

    if ("/deliver".equals(path)) {
      if (!"PROVIDER".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Réservé aux prestataires.");
        return;
      }

      long orderId = parseLong(request.getParameter("orderId"), -1);
      if (orderId <= 0) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "orderId invalide.");
        return;
      }

      try {
        ProductFeaturesRepository.OrderRecord order = repository.findOrderById(orderId);
        if (order == null) {
          writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Commande introuvable.");
          return;
        }

        if (!user.email.equalsIgnoreCase(order.providerEmail)) {
          writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Accès non autorisé.");
          return;
        }

        if (!"paid".equalsIgnoreCase(order.status) && !"in_progress".equalsIgnoreCase(order.status)) {
          writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Commande non livrable dans cet état.");
          return;
        }

        repository.updateOrderStatus(orderId, "delivered");
        repository.createNotification(order.clientEmail, "ORDER_DELIVERED",
            "{\"orderId\":" + orderId + "}");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":true}");
      } catch (IOException ex) {
        writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur lors de la livraison.");
      }
      return;
    }

    if ("/request-revision".equals(path)) {
      if (!"CLIENT".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Réservé aux clients.");
        return;
      }

      long orderId = parseLong(request.getParameter("orderId"), -1);
      if (orderId <= 0) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "orderId invalide.");
        return;
      }

      try {
        ProductFeaturesRepository.OrderRecord order = repository.findOrderById(orderId);
        if (order == null) {
          writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Commande introuvable.");
          return;
        }

        if (!user.email.equalsIgnoreCase(order.clientEmail)) {
          writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Accès non autorisé.");
          return;
        }

        if (!"delivered".equalsIgnoreCase(order.status)) {
          writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Seules les livrées peuvent être révisées.");
          return;
        }

        repository.updateOrderStatus(orderId, "in_revision");
        repository.createNotification(order.providerEmail, "REVISION_REQUESTED",
            "{\"orderId\":" + orderId + "}");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":true}");
      } catch (IOException ex) {
        writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur lors de la révision.");
      }
      return;
    }

    if ("/complete".equals(path)) {
      if (!"CLIENT".equalsIgnoreCase(user.role)) {
        writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Réservé aux clients.");
        return;
      }

      long orderId = parseLong(request.getParameter("orderId"), -1);
      if (orderId <= 0) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "orderId invalide.");
        return;
      }

      try {
        ProductFeaturesRepository.OrderRecord order = repository.findOrderById(orderId);
        if (order == null) {
          writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Commande introuvable.");
          return;
        }

        if (!user.email.equalsIgnoreCase(order.clientEmail)) {
          writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Accès non autorisé.");
          return;
        }

        if (!"delivered".equalsIgnoreCase(order.status) && !"in_revision".equalsIgnoreCase(order.status)) {
          writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Commande non complétable dans cet état.");
          return;
        }

        repository.updateOrderStatus(orderId, "completed");
        repository.createNotification(order.providerEmail, "ORDER_COMPLETED",
            "{\"orderId\":" + orderId + "}");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":true}");
      } catch (IOException ex) {
        writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur lors de la finalisation.");
      }
      return;
    }

    if ("/add-message".equals(path)) {
      long orderId = parseLong(request.getParameter("orderId"), -1);
      String messageText = clean(request.getParameter("message"));
      String attachmentUrl = clean(request.getParameter("attachmentUrl"));

      if (orderId <= 0 || messageText.isEmpty()) {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "orderId et message requis.");
        return;
      }

      try {
        ProductFeaturesRepository.OrderRecord order = repository.findOrderById(orderId);
        if (order == null) {
          writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Commande introuvable.");
          return;
        }

        if (!isOrderOwner(order, user)) {
          writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Accès non autorisé.");
          return;
        }

        long messageId = repository.addOrderMessage(orderId, user.email, messageText, attachmentUrl);
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":true,\"messageId\":" + messageId + "}");
      } catch (IOException ex) {
        writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Erreur lors de l'ajout du message.");
      }
      return;
    }

    writeJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Endpoint introuvable.");
  }

  // ========== HELPERS ==========

  private static class SessionUser {
    String email;
    String role;
    String firstName;
    String lastName;
  }

  private SessionUser requireUser(HttpServletRequest request, HttpServletResponse response) throws IOException {
    HttpSession session = request.getSession(false);
    if (session == null || session.getAttribute("loggedIn") == null) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Non authentifié.");
      return null;
    }

    SessionUser user = new SessionUser();
    user.email = (String) session.getAttribute("userEmail");
    user.role = (String) session.getAttribute("userRole");
    user.firstName = (String) session.getAttribute("userFirstName");
    user.lastName = (String) session.getAttribute("userLastName");

    if (user.email == null || user.email.isEmpty()) {
      writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Session invalide.");
      return null;
    }

    return user;
  }

  private boolean isOrderOwner(ProductFeaturesRepository.OrderRecord order, SessionUser user) {
    return user.email.equalsIgnoreCase(order.clientEmail) || user.email.equalsIgnoreCase(order.providerEmail);
  }

  private void writeJsonError(HttpServletResponse response, int statusCode, String message) throws IOException {
    response.setStatus(statusCode);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
  }

  private String escapeJson(String str) {
    if (str == null) {
      return "";
    }
    return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private long parseLong(String value, long defaultVal) {
    try {
      return Long.parseLong(clean(value));
    } catch (NumberFormatException ex) {
      return defaultVal;
    }
  }

  private double parseDouble(String value, double defaultVal) {
    try {
      return Double.parseDouble(clean(value));
    } catch (NumberFormatException ex) {
      return defaultVal;
    }
  }

  private int parseInt(String value, int defaultVal) {
    try {
      return Integer.parseInt(clean(value));
    } catch (NumberFormatException ex) {
      return defaultVal;
    }
  }

  private String ordersToJson(List<ProductFeaturesRepository.OrderRecord> orders) {
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < orders.size(); i++) {
      if (i > 0) {
        json.append(",");
      }
      json.append(orderToJson(orders.get(i)));
    }
    json.append("]");
    return json.toString();
  }

  private String orderToJson(ProductFeaturesRepository.OrderRecord order) {
    return "{\"id\":" + order.id + ",\"clientEmail\":\"" + escapeJson(order.clientEmail)
        + "\",\"providerEmail\":\"" + escapeJson(order.providerEmail) + "\",\"title\":\"" + escapeJson(order.title)
        + "\",\"description\":\"" + escapeJson(order.description) + "\",\"price\":" + order.price
        + ",\"estimatedDeliveryDate\":\"" + escapeJson(order.estimatedDeliveryDate) + "\",\"revisions\":"
        + order.revisions + ",\"status\":\"" + order.status + "\",\"paymentStatus\":\"" + order.paymentStatus
        + "\",\"createdAt\":\"" + order.createdAt + "\",\"updatedAt\":\"" + order.updatedAt + "\"}";
  }

  private String messagesToJson(List<ProductFeaturesRepository.OrderMessageRecord> messages) {
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < messages.size(); i++) {
      if (i > 0) {
        json.append(",");
      }
      json.append(messageToJson(messages.get(i)));
    }
    json.append("]");
    return json.toString();
  }

  private String messageToJson(ProductFeaturesRepository.OrderMessageRecord msg) {
    return "{\"id\":" + msg.id + ",\"orderId\":" + msg.orderId + ",\"senderEmail\":\"" + escapeJson(msg.senderEmail)
        + "\",\"messageText\":\"" + escapeJson(msg.messageText) + "\",\"attachmentUrl\":\""
        + escapeJson(msg.attachmentUrl) + "\",\"createdAt\":\"" + msg.createdAt + "\"}";
  }
}
