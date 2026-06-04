function loadOrders() {
  fetch("/sarbi_rohek/api/orders/list", {
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
      renderOrdersList(data.orders || []);
    })
    .catch((err) => {
      console.error("Erreur lors du chargement des commandes:", err);
      if (ordersStatusEl) {
        ordersStatusEl.textContent = "Erreur lors du chargement.";
      }
    });
}

function renderOrdersList(orders) {
  if (!ordersList El) return;

  if (orders.length === 0) {
    ordersListEl.innerHTML =
      '<div class="empty-state"><p>Aucune commande pour le moment.</p><button class="btn btn-primary" onclick="openCreateOrderModal()">Créer une demande</button></div>';
    return;
  }

  ordersListEl.innerHTML = orders
    .map(
      (order) => `
    <div class="order-card">
      <div class="order-header">
        <div class="order-title">${esc(order.title || "Sans titre")}</div>
        <div class="order-status-badge status-${order.status}">${getOrderStatusLabel(order.status)}</div>
      </div>
      <div class="order-info">
        <div class="info-row">
          <span class="label">Prestataire:</span>
          <span class="value">${esc(order.providerEmail)}</span>
        </div>
        <div class="info-row">
          <span class="label">Prix:</span>
          <span class="value price">${order.price ? order.price.toFixed(2) + " TND" : "À confirmer"}</span>
        </div>
        <div class="info-row">
          <span class="label">Livraison estimée:</span>
          <span class="value">${order.estimatedDeliveryDate ? formatDate(order.estimatedDeliveryDate) : "À confirmer"}</span>
        </div>
        <div class="info-row">
          <span class="label">Révisions:</span>
          <span class="value">${order.revisions || 0}</span>
        </div>
        <div class="info-row">
          <span class="label">Statut paiement:</span>
          <span class="value payment-${order.paymentStatus}">${getPaymentStatusLabel(order.paymentStatus)}</span>
        </div>
      </div>
      <div class="order-actions">
        <button class="btn btn-small btn-secondary" onclick="viewOrderDetail(${order.id})">Voir détails</button>
        ${order.paymentStatus === "pending" && order.status === "pending_payment" ? '<button class="btn btn-small btn-success" onclick="payOrder(' + order.id + ')">Payer</button>' : ""}
      </div>
    </div>
  `
    )
    .join("");
}

function getOrderStatusLabel(status) {
  const labels = {
    draft: "Brouillon",
    pending_details: "En attente de détails",
    pending_payment: "En attente de paiement",
    paid: "Payée",
    in_progress: "En cours",
    delivered: "Livrée",
    in_revision: "En révision",
    completed: "Terminée",
    cancelled: "Annulée",
  };
  return labels[status] || status;
}

function getPaymentStatusLabel(status) {
  const labels = {
    pending: "En attente",
    paid: "Payée",
    failed: "Échouée",
  };
  return labels[status] || status;
}

function formatDate(dateString) {
  try {
    const date = new Date(dateString);
    return date.toLocaleDateString("fr-FR");
  } catch (e) {
    return dateString;
  }
}

function viewOrderDetail(orderId) {
  window.location.href = `/sarbi_rohek/order-detail.html?id=${orderId}`;
}

function payOrder(orderId) {
  if (!confirm("Confirmer le paiement de cette commande?")) {
    return;
  }

  fetch("/sarbi_rohek/api/orders/pay", {
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
        if (ordersStatusEl) {
          ordersStatusEl.textContent = "Paiement effectué avec succès!";
        }
        loadOrders();
      } else {
        if (ordersStatusEl) {
          ordersStatusEl.textContent = "Erreur: " + (data.error || "Paiement échoué.");
        }
      }
    })
    .catch((err) => {
      console.error("Erreur paiement:", err);
      if (ordersStatusEl) {
        ordersStatusEl.textContent = "Erreur lors du paiement.";
      }
    });
}

function openCreateOrderModal() {
  if (createOrderModalEl) {
    createOrderModalEl.style.display = "block";
    loadProvidersForOrderSelection();
  }
}

function closeCreateOrderModal() {
  if (createOrderModalEl) {
    createOrderModalEl.style.display = "none";
  }
}

function loadProvidersForOrderSelection() {
  fetch("/sarbi_rohek/api/providers/all", {
    method: "GET",
    credentials: "include",
  })
    .then((r) => r.json())
    .then((data) => {
      const select = document.getElementById("orderProviderSelect");
      if (!select) return;

      select.innerHTML = '<option value="">-- Sélectionner un prestataire --</option>';
      (data.providers || []).forEach((p) => {
        const option = document.createElement("option");
        option.value = p.email;
        option.textContent = `${p.first_name || ""} ${p.last_name || ""} (${p.email})`;
        select.appendChild(option);
      });
    })
    .catch((err) => console.error("Erreur chargement prestataires:", err));
}

const createOrderFormEl = document.getElementById("createOrderForm");
if (createOrderFormEl) {
  createOrderFormEl.addEventListener("submit", (e) => {
    e.preventDefault();
    const providerEmail = document.getElementById("orderProviderSelect").value;
    if (!providerEmail) {
      alert("Sélectionner un prestataire.");
      return;
    }

    fetch("/sarbi_rohek/api/orders/create", {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: `providerEmail=${encodeURIComponent(providerEmail)}`,
    })
      .then((r) => r.json())
      .then((data) => {
        if (data.success) {
          closeCreateOrderModal();
          loadOrders();
          if (ordersStatusEl) {
            ordersStatusEl.textContent = "Demande créée! Le prestataire recevra une notification.";
          }
        } else {
          alert("Erreur: " + (data.error || "Impossible de créer la demande."));
        }
      })
      .catch((err) => {
        console.error("Erreur création commande:", err);
        alert("Erreur lors de la création.");
      });
  });
}

const ordersListEl = document.getElementById("ordersList");
const ordersStatusEl = document.getElementById("ordersStatus");
const createOrderModalEl = document.getElementById("createOrderModal");

window.addEventListener("click", (event) => {
  if (event.target === createOrderModalEl) {
    closeCreateOrderModal();
  }
});
