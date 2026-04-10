function loadOrderRequests() {
  fetch("/sarbi_rohek/api/orders/requests", {
    method: "GET",
    credentials: "include",
  })
    .then((r) => {
      if (r.status === 401) {
        redirectToLoginWithReason("Session expirée.");
        return null;
      }
      return r.json();
    })
    .then((data) => {
      if (!data) return;
      renderOrderRequestsList(data.requests || []);
    })
    .catch((err) => {
      console.error("Erreur lors du chargement:", err);
      if (orderRequestsStatusEl) {
        orderRequestsStatusEl.textContent = "Erreur lors du chargement.";
      }
    });
}

function renderOrderRequestsList(requests) {
  if (!orderRequestsListEl) return;

  // Séparer les demandes par statut
  const pending = requests.filter((r) => r.status === "draft" || r.status === "pending_details");
  const filled = requests.filter((r) => r.status === "pending_payment" || r.status === "paid");
  const active = requests.filter((r) => ["in_progress", "delivered", "in_revision"].includes(r.status));
  const completed = requests.filter((r) => ["completed", "cancelled"].includes(r.status));

  let html = "";

  if (pending.length > 0) {
    html += '<div class="section-group"><h3>À remplir</h3>';
    html += pending.map((r) => renderRequestCard(r, true)).join("");
    html += "</div>";
  }

  if (filled.length > 0) {
    html += '<div class="section-group"><h3>En attente de paiement</h3>';
    html += filled.map((r) => renderRequestCard(r, false)).join("");
    html += "</div>";
  }

  if (active.length > 0) {
    html += '<div class="section-group"><h3>En cours</h3>';
    html += active.map((r) => renderRequestCard(r, false)).join("");
    html += "</div>";
  }

  if (completed.length > 0) {
    html += '<div class="section-group"><h3>Terminées</h3>';
    html += completed.map((r) => renderRequestCard(r, false)).join("");
    html += "</div>";
  }

  if (requests.length === 0) {
    html =
      '<div class="empty-state"><p>Aucune demande de commande pour le moment.</p></div>';
  }

  orderRequestsListEl.innerHTML = html;
}

function renderRequestCard(order, showFillButton) {
  const statusLabel = getOrderStatusLabel(order.status);
  const statusClass = order.status;

  return `
    <div class="order-request-card">
      <div class="request-header">
        <div>
          <div class="request-id">Commande #${order.id}</div>
          <div class="request-client">Client: ${esc(order.clientEmail)}</div>
        </div>
        <div class="status-badge status-${statusClass}">${statusLabel}</div>
      </div>
      <div class="request-content">
        <div class="request-title">${esc(order.title || "Sans titre")}</div>
        <div class="request-description">${esc((order.description || "").substring(0, 150))}${(order.description || "").length > 150 ? "..." : ""}</div>
        ${order.price ? `<div class="request-price">Prix: ${order.price.toFixed(2)} TND</div>` : ""}
      </div>
      <div class="request-actions">
        ${showFillButton ? `<button class="btn btn-small btn-primary" onclick="openFillDetailsModal(${order.id})">Remplir les détails</button>` : ""}
        <button class="btn btn-small btn-secondary" onclick="viewRequestDetail(${order.id})">Détails</button>
        ${["paid", "in_progress"].includes(order.status) ? `<button class="btn btn-small btn-success" onclick="deliverOrder(${order.id})">Livrer</button>` : ""}
      </div>
    </div>
  `;
}

function getOrderStatusLabel(status) {
  const labels = {
    draft: "Brouillon",
    pending_details: "À remplir",
    pending_payment: "En attente de paiement",
    paid: "Payée - En cours",
    in_progress: "En cours",
    delivered: "Livrée",
    in_revision: "Révision demandée",
    completed: "Terminée",
    cancelled: "Annulée",
  };
  return labels[status] || status;
}

function openFillDetailsModal(orderId) {
  if (!fillDetailsModalEl) return;

  document.getElementById("modalOrderId").value = orderId;
  document.getElementById("detailTitle").value = "";
  document.getElementById("detailDescription").value = "";
  document.getElementById("detailPrice").value = "";
  document.getElementById("detailDeliveryDate").value = "";
  document.getElementById("detailRevisions").value = "3";

  fillDetailsModalEl.style.display = "block";
}

function closeFillDetailsModal() {
  if (fillDetailsModalEl) {
    fillDetailsModalEl.style.display = "none";
  }
}

function viewRequestDetail(orderId) {
  window.location.href = `/sarbi_rohek/order-detail.html?id=${orderId}`;
}

function deliverOrder(orderId) {
  if (!confirm("Confirmer la livraison de cette commande?")) {
    return;
  }

  fetch("/sarbi_rohek/api/orders/deliver", {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: `orderId=${orderId}`,
  })
    .then((r) => r.json())
    .then((data) => {
      if (data.success) {
        if (orderRequestsStatusEl) {
          orderRequestsStatusEl.textContent = "Commande livrée!";
        }
        loadOrderRequests();
      } else {
        if (orderRequestsStatusEl) {
          orderRequestsStatusEl.textContent = "Erreur: " + (data.error || "Impossible de livrer.");
        }
      }
    })
    .catch((err) => {
      console.error("Erreur livraison:", err);
      if (orderRequestsStatusEl) {
        orderRequestsStatusEl.textContent = "Erreur lors de la livraison.";
      }
    });
}

const fillDetailsFormEl = document.getElementById("fillDetailsForm");
if (fillDetailsFormEl) {
  fillDetailsFormEl.addEventListener("submit", (e) => {
    e.preventDefault();

    const orderId = document.getElementById("modalOrderId").value;
    const title = document.getElementById("detailTitle").value.trim();
    const description = document.getElementById("detailDescription").value.trim();
    const price = parseFloat(document.getElementById("detailPrice").value);
    const deliveryDate = document.getElementById("detailDeliveryDate").value;
    const revisions = parseInt(document.getElementById("detailRevisions").value) || 3;

    if (!orderId || !title || !description || price <= 0 || !deliveryDate) {
      alert("Remplir tous les champs obligatoires.");
      return;
    }

    fetch("/sarbi_rohek/api/orders/fill-details", {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: `orderId=${orderId}&title=${encodeURIComponent(title)}&description=${encodeURIComponent(description)}&price=${price}&estimatedDeliveryDate=${encodeURIComponent(deliveryDate)}&revisions=${revisions}`,
    })
      .then((r) => r.json())
      .then((data) => {
        if (data.success) {
          closeFillDetailsModal();
          loadOrderRequests();
          if (orderRequestsStatusEl) {
            orderRequestsStatusEl.textContent = "Détails envoyés au client!";
          }
        } else {
          alert("Erreur: " + (data.error || "Impossible de mettre à jour."));
        }
      })
      .catch((err) => {
        console.error("Erreur:", err);
        alert("Erreur lors de la mise à jour.");
      });
  });
}

const orderRequestsListEl = document.getElementById("orderRequestsList");
const orderRequestsStatusEl = document.getElementById("orderRequestsStatus");
const fillDetailsModalEl = document.getElementById("fillDetailsModal");

window.addEventListener("click", (event) => {
  if (event.target === fillDetailsModalEl) {
    closeFillDetailsModal();
  }
});

// CSS styles pour les cartes de demandes
const styleEl = document.createElement("style");
styleEl.textContent = `
  .order-requests-container {
    margin-top: 1.5rem;
  }

  .section-group {
    margin-bottom: 2rem;
  }

  .section-group h3 {
    color: #667eea;
    border-bottom: 2px solid #e0e0e0;
    padding-bottom: 0.75rem;
    margin-bottom: 1rem;
  }

  .order-request-card {
    background: white;
    border: 1px solid #e0e0e0;
    border-radius: 8px;
    padding: 1.5rem;
    margin-bottom: 1rem;
    transition: box-shadow 0.2s;
  }

  .order-request-card:hover {
    box-shadow: 0 2px 8px rgba(102, 126, 234, 0.1);
  }

  .request-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 1rem;
  }

  .request-id {
    font-weight: 600;
    color: #333;
    font-size: 1.1rem;
  }

  .request-client {
    font-size: 0.9rem;
    color: #666;
    margin-top: 0.25rem;
  }

  .status-badge {
    padding: 0.5rem 1rem;
    border-radius: 20px;
    font-size: 0.85rem;
    font-weight: 600;
    white-space: nowrap;
  }

  .status-draft,
  .status-pending-details,
  .status-pending_details {
    background: #fff3cd;
    color: #856404;
  }

  .status-pending-payment,
  .status-pending_payment {
    background: #fff3cd;
    color: #856404;
  }

  .status-paid {
    background: #d4edda;
    color: #155724;
  }

  .status-in-progress,
  .status-in_progress {
    background: #d1ecf1;
    color: #0c5460;
  }

  .status-delivered {
    background: #d4edda;
    color: #155724;
  }

  .status-in-revision,
  .status-in_revision {
    background: #f8d7da;
    color: #721c24;
  }

  .status-completed {
    background: #d4edda;
    color: #155724;
  }

  .request-content {
    margin-bottom: 1rem;
  }

  .request-title {
    font-weight: 600;
    color: #333;
    font-size: 1rem;
    margin-bottom: 0.5rem;
  }

  .request-description {
    color: #666;
    font-size: 0.95rem;
    line-height: 1.4;
    margin-bottom: 0.5rem;
  }

  .request-price {
    color: #667eea;
    font-weight: 600;
    font-size: 1.1rem;
  }

  .request-actions {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
  }

  .form-actions {
    display: flex;
    gap: 1rem;
    justify-content: flex-end;
    margin-top: 1.5rem;
  }

  .empty-state {
    text-align: center;
    padding: 3rem 1rem;
    color: #999;
  }

  .empty-state p {
    font-size: 1.1rem;
  }
`;
document.head.appendChild(styleEl);
