function byId(id) {
  return document.getElementById(id);
}

// Login helper using configured backend URL (falls back to provided backend)
async function login(email, password) {
  const base = (typeof NEXT_PUBLIC_API_URL !== 'undefined' && NEXT_PUBLIC_API_URL) || window.NEXT_PUBLIC_API_URL || 'https://ton-backend.onrender.com';
  const url = `${base.replace(/\/$/, '')}/api/auth/login`;

  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`Login failed: ${res.status} ${text}`);
  }

  return res.json();
}

const helloNameEl = byId("clientHelloName");
const logoutBtnEl = byId("clientLogoutBtn");
const openMessagesBtnEl = byId("openMessagesBtn");
const messengerPanelEl = byId("messengerPanel");
const conversationsListEl = byId("conversationsList");
const threadHeaderEl = byId("threadHeader");
const threadMessagesEl = byId("threadMessages");
const threadFormEl = byId("threadForm");
const threadInputEl = byId("threadInput");
const threadStatusEl = byId("threadStatus");
const providersGridEl = byId("providersGrid");
const providersStatusEl = byId("providersStatus");
const filterServiceTypeEl = byId("filterServiceType");
const filterKeywordEl = byId("filterKeyword");
const favoritesListEl = byId("favoritesList");
const notificationsListEl = byId("notificationsList");
const clientKpiActiveQuotesEl = byId("clientKpiActiveQuotes");
const clientKpiFavoritesEl = byId("clientKpiFavorites");
const clientKpiSpentEl = byId("clientKpiSpent");
const reviewFormEl = byId("reviewForm");
const reviewProviderEmailEl = byId("reviewProviderEmail");
const reviewRatingEl = byId("reviewRating");
const reviewCommentEl = byId("reviewComment");
const reviewStatusEl = byId("reviewStatus");
const quotesListEl = byId("quotesList");
const quotesStatusEl = byId("quotesStatus");
const ordersListEl = byId("ordersList");
const ordersStatusEl = byId("ordersStatus");
const clientViewTabs = Array.from(document.querySelectorAll("[data-client-view-tab]"));
const clientViews = Array.from(document.querySelectorAll("[data-client-view]"));

let allProviders = [];
let currentUserEmail = "";
let selectedConversationEmail = "";
let selectedConversationName = "";
let selectedConversationProviderEmail = ""; // Pour stocker l'email du prestataire
let favoritesSet = new Set();

function showClientView(viewName) {
  const normalized = String(viewName || "dashboard").trim();
  clientViewTabs.forEach((tab) => {
    const active = tab.getAttribute("data-client-view-tab") === normalized;
    tab.classList.toggle("active", active);
  });

  clientViews.forEach((view) => {
    const active = view.getAttribute("data-client-view") === normalized;
    view.classList.toggle("active", active);
  });

  // Masquer le bouton Commander si on quitte la vue messages
  const commanderBtn = document.getElementById("commanderBtn");
  if (normalized !== "messages" && commanderBtn) {
    commanderBtn.style.display = "none";
  }

  if (normalized === "dashboard") {
    loadClientDashboard().catch(() => {});
  } else if (normalized === "quotes") {
    loadQuotes().catch(() => {});
  } else if (normalized === "orders") {
    loadOrders().catch(() => {});
  }
}

function redirectToLoginWithReason(message) {
  const reason = (message || "").trim();
  if (!reason) {
    window.location.href = "/sarbi_rohek/login.html";
    return;
  }

  window.location.href = `/sarbi_rohek/login.html?error=${encodeURIComponent(reason)}`;
}

function esc(value) {
  return (value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function setStatus(message) {
  if (providersStatusEl) {
    providersStatusEl.textContent = message;
  }
}

function setThreadStatus(message) {
  if (threadStatusEl) {
    threadStatusEl.textContent = message || "";
  }
}

function setReviewStatus(message) {
  if (reviewStatusEl) {
    reviewStatusEl.textContent = message || "";
  }
}

function toStatusPill(status) {
  const raw = String(status || "PENDING").trim().toUpperCase();
  const css = raw.toLowerCase();
  return `<span class="status-pill ${esc(css)}">${esc(raw)}</span>`;
}

function workStatusLabel(status) {
  const raw = String(status || "NOT_STARTED").trim().toUpperCase();
  if (raw === "IN_PROGRESS") {
    return "En cours";
  }
  if (raw === "COMPLETED") {
    return "Terminee";
  }
  return "Pas commencee";
}

function notificationDetails(payloadRaw) {
  if (!payloadRaw) {
    return "";
  }

  try {
    const obj = JSON.parse(payloadRaw);
    const keys = Object.keys(obj || {});
    if (keys.length === 0) {
      return "";
    }
    return keys.map((k) => `${k}: ${String(obj[k])}`).join(" | ");
  } catch (error) {
    return String(payloadRaw);
  }
}

function statusLabelFr(status) {
  const raw = String(status || "").trim().toUpperCase();
  if (raw === "NOT_STARTED") {
    return "pas commencee";
  }
  if (raw === "IN_PROGRESS") {
    return "en cours";
  }
  if (raw === "COMPLETED") {
    return "terminee";
  }
  return raw.toLowerCase();
}

function notificationText(type, payloadRaw) {
  let payload = {};
  try {
    payload = JSON.parse(payloadRaw || "{}");
  } catch (error) {
    payload = {};
  }

  const t = String(type || "INFO").trim().toUpperCase();
  if (t === "TASK_STATUS_UPDATED") {
    return `Votre tache #${payload.paymentId || "-"} est ${statusLabelFr(payload.workStatus)}.`;
  }
  if (t === "PAYMENT_CREATED") {
    return `Paiement #${payload.paymentId || "-"} cree vers ${payload.providerEmail || "prestataire"}.`;
  }
  if (t === "PAYMENT_CONFIRMED") {
    return `Paiement #${payload.paymentId || "-"} confirme par ${payload.providerEmail || "prestataire"}.`;
  }
  if (t === "REVIEW_POSTED") {
    return `Votre avis (${payload.rating || "-"}/5) a ete envoye a ${payload.providerEmail || "prestataire"}.`;
  }
  if (t === "NEW_REVIEW") {
    return `Nouvel avis recu (${payload.rating || "-"}/5) de ${payload.clientEmail || "client"}.`;
  }
  if (t === "NEW_PAYMENT") {
    return `Nouveau paiement #${payload.paymentId || "-"} recu de ${payload.clientEmail || "client"}.`;
  }

  return notificationDetails(payloadRaw);
}

function notificationTypeLabel(type) {
  const raw = String(type || "INFO").trim().toUpperCase();
  if (raw === "TASK_STATUS_UPDATED") {
    return "Tache mise a jour";
  }
  if (raw === "PAYMENT_CREATED") {
    return "Paiement cree";
  }
  if (raw === "PAYMENT_CONFIRMED") {
    return "Paiement confirme";
  }
  if (raw === "REVIEW_POSTED") {
    return "Avis publie";
  }
  if (raw === "NEW_PAYMENT") {
    return "Nouveau paiement";
  }
  if (raw === "NEW_REVIEW") {
    return "Nouvel avis";
  }
  return raw;
}

function notificationTypeClass(type) {
  return String(type || "info")
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, "-")
    .replaceAll(/^-+|-+$/g, "");
}

function toServiceCategory(provider) {
  const raw = (provider.serviceType || "").trim().toUpperCase();
  if (raw === "ARTISANT") {
    return "ARTISANT";
  }
  if (raw === "VIDEO_MAKER") {
    return "SERVICE_PERSONNALISE";
  }
  return "SERVICE_PRO";
}

function toProviderPhotoUrl(provider) {
  const raw = (provider.photoUrl || "").trim();
  if (!raw) {
    return "logo.png";
  }

  const lower = raw.toLowerCase();
  if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("/")) {
    return raw;
  }

  return "logo.png";
}

function cardHtml(provider, index) {
  const fullName = `${provider.firstName || ""} ${provider.lastName || ""}`.trim() || "Prestataire";
  const role = provider.role || "PROVIDER";
  const serviceType = toServiceCategory(provider);
  const mainActivity = provider.mainActivity || "Activite non precisee";
  const description = provider.workDescription || "Aucune description.";
  const email = provider.email || "-";
  const photoUrl = toProviderPhotoUrl(provider);
  const isFavorite = favoritesSet.has((email || "").toLowerCase());
  const favLabel = isFavorite ? "Retirer favori" : "Ajouter favori";
  const isPaused = Boolean(provider.paused);
  const disabledAttr = isPaused ? "disabled" : "";
  const disabledTitle = isPaused ? "Prestataire en pause sur cet intervalle" : "";

  return `
    <article class="provider-card ${isPaused ? "provider-paused" : ""}" data-card-index="${index}" data-provider-email="${esc((email || "").toLowerCase())}">
      <div class="provider-head">
        <img class="provider-avatar" src="${esc(photoUrl)}" alt="Photo de ${esc(fullName)}" onerror="this.onerror=null;this.src='logo.png';" />
        <div>
          <div class="provider-name">${esc(fullName)}</div>
          <div class="provider-meta">
            <div>Role: ${esc(role)}</div>
            <div>Email: ${esc(email)}</div>
            <div>Activite: ${esc(mainActivity)}</div>
          </div>
        </div>
        <span class="provider-type">${esc(serviceType)}</span>
      </div>

      ${isPaused ? '<p class="provider-paused-note">Prestataire en pause: actions desactivees temporairement.</p>' : ""}

      <p class="provider-main">${esc(description)}</p>
      <button class="card-expand-btn" type="button" data-expand-index="${index}">Voir plus</button>
      <button class="card-message-btn" type="button" data-message-email="${esc(email)}" data-message-name="${esc(fullName)}" ${disabledAttr} title="${esc(disabledTitle)}">
        Envoyer un message
      </button>
      <button class="card-action-btn" type="button" data-favorite-email="${esc(email)}" ${disabledAttr} title="${esc(disabledTitle)}">${favLabel}</button>
      <button class="card-action-btn" type="button" data-pay-email="${esc(email)}" ${disabledAttr} title="${esc(disabledTitle)}">Payer</button>

      <div class="provider-extra">
        <p><strong>Description complete:</strong> ${esc(description)}</p>
        <div class="reviews-zone" data-reviews-email="${esc(email)}">
          <p class="reviews-loading">Cliquer sur la carte pour voir les avis...</p>
        </div>
      </div>
    </article>
  `;
}

function findProviderCardByEmail(providerEmail) {
  if (!providersGridEl) {
    return null;
  }

  const normalized = String(providerEmail || "").trim().toLowerCase();
  if (!normalized) {
    return null;
  }

  const cards = Array.from(providersGridEl.querySelectorAll(".provider-card"));
  return cards.find((card) => String(card.getAttribute("data-provider-email") || "").toLowerCase() === normalized) || null;
}

function openProviderFromFavorite(providerEmail) {
  const normalized = String(providerEmail || "").trim().toLowerCase();
  if (!normalized) {
    return;
  }

  if (filterServiceTypeEl) {
    filterServiceTypeEl.value = "ALL";
  }
  if (filterKeywordEl) {
    filterKeywordEl.value = "";
  }

  renderProviders();
  showClientView("providers");

  const card = findProviderCardByEmail(normalized);
  if (!card) {
    setStatus("Prestataire introuvable dans la liste.");
    return;
  }

  card.scrollIntoView({ behavior: "smooth", block: "center" });
  card.classList.add("favorite-target");
  window.setTimeout(() => {
    card.classList.remove("favorite-target");
  }, 2200);

  const expandBtn = card.querySelector("[data-expand-index]");
  if (expandBtn && !card.classList.contains("expanded")) {
    card.classList.add("expanded");
    expandBtn.textContent = "Voir moins";
    loadProviderReviews(normalized, card);
  }
}

function isProviderPausedByEmail(email) {
  const normalized = String(email || "").trim().toLowerCase();
  if (!normalized) {
    return false;
  }

  const provider = allProviders.find((p) => String(p.email || "").toLowerCase() === normalized);
  return Boolean(provider && provider.paused);
}

async function loadProviderReviews(providerEmail, cardEl) {
  if (!providerEmail || !cardEl) {
    return;
  }

  const zone = cardEl.querySelector(".reviews-zone");
  if (!zone) {
    return;
  }

  if (zone.getAttribute("data-loaded") === "true") {
    return;
  }

  zone.innerHTML = '<p class="reviews-loading">Chargement des avis...</p>';

  const response = await fetch(`/sarbi_rohek/api/reviews?providerEmail=${encodeURIComponent(providerEmail)}`);
  if (!response.ok) {
    zone.innerHTML = '<p class="reviews-loading">Impossible de charger les avis.</p>';
    return;
  }

  const data = await response.json().catch(() => ({}));
  const summary = data.summary || {};
  const reviews = Array.isArray(data.reviews) ? data.reviews : [];

  const header = `
    <div class="reviews-summary">
      <strong>Note moyenne: ${esc(String(summary.averageRating ?? "0.00"))}/5</strong>
      <span>(${esc(String(summary.totalReviews ?? 0))} avis)</span>
    </div>
  `;

  if (reviews.length === 0) {
    zone.innerHTML = header + '<p class="reviews-loading">Aucun avis pour le moment.</p>';
    zone.setAttribute("data-loaded", "true");
    return;
  }

  const list = reviews.map((review) => `
    <div class="review-item">
      <div><strong>${esc(review.clientEmail || "client")}</strong> - ${esc(String(review.rating || 0))}/5</div>
      <div>${esc(review.comment || "")}</div>
    </div>
  `).join("");

  zone.innerHTML = header + `<div class="reviews-list">${list}</div>`;
  zone.setAttribute("data-loaded", "true");
}

function filteredProviders() {
  const selectedType = filterServiceTypeEl ? filterServiceTypeEl.value : "ALL";
  const keyword = (filterKeywordEl ? filterKeywordEl.value : "").trim().toLowerCase();

  return allProviders.filter((provider) => {
    const byType = selectedType === "ALL" || toServiceCategory(provider) === selectedType;
    if (!byType) {
      return false;
    }

    if (!keyword) {
      return true;
    }

    const haystack = [
      provider.firstName,
      provider.lastName,
      provider.mainActivity,
      provider.workDescription,
      provider.email,
      provider.role,
      toServiceCategory(provider)
    ]
      .filter(Boolean)
      .join(" ")
      .toLowerCase();

    return haystack.includes(keyword);
  });
}

function renderProviders() {
  if (!providersGridEl) {
    return;
  }

  const providers = filteredProviders();
  if (providers.length === 0) {
    providersGridEl.innerHTML = "<p>Aucun prestataire ne correspond au filtre.</p>";
    setStatus("0 prestataire trouve.");
    return;
  }

  providersGridEl.innerHTML = providers.map((provider, index) => cardHtml(provider, index)).join("");
  setStatus(`${providers.length} prestataire(s) affiche(s).`);
}

function fillServiceTypeFilter() {
  // Static service categories are defined in HTML as requested.
}

async function loadClientSession() {
  const response = await fetch("/sarbi_rohek/api/auth/status");
  if (!response.ok) {
    redirectToLoginWithReason("");
    return null;
  }

  const data = await response.json();
  if (!data.loggedIn) {
    redirectToLoginWithReason(data.message || "");
    return null;
  }

  if ((data.role || "").trim() === "PROVIDER") {
    window.location.href = "/sarbi_rohek/provider-space.html";
    return null;
  }

  return data;
}

function startSessionGuard() {
  window.setInterval(async () => {
    try {
      const response = await fetch("/sarbi_rohek/api/auth/status", { cache: "no-store" });
      if (!response.ok) {
        redirectToLoginWithReason("");
        return;
      }

      const data = await response.json();
      if (!data.loggedIn) {
        redirectToLoginWithReason(data.message || "");
      }
    } catch (error) {
      // Ignore transient network errors for periodic guard.
    }
  }, 15000);
}

async function loadClientName(sessionData) {
  let firstName = (sessionData.firstName || "").trim();
  let lastName = (sessionData.lastName || "").trim();

  if (!firstName && !lastName) {
    const profileResponse = await fetch("/sarbi_rohek/api/auth/profile");
    if (profileResponse.ok) {
      const profile = await profileResponse.json();
      firstName = (profile.firstName || "").trim();
      lastName = (profile.lastName || "").trim();
    }
  }

  const fullName = `${firstName} ${lastName}`.trim();
  if (helloNameEl) {
    helloNameEl.textContent = fullName ? `Bonjour, ${fullName}` : "Bonjour";
  }
}

function renderConversations(conversations) {
  if (!conversationsListEl) {
    return;
  }

  if (!Array.isArray(conversations) || conversations.length === 0) {
    conversationsListEl.innerHTML = "<p>Aucune conversation.</p>";
    return;
  }

  conversationsListEl.innerHTML = conversations.map((item) => {
    const active = item.counterpartEmail === selectedConversationEmail ? "active" : "";
    const name = item.counterpartName || item.counterpartEmail;
    const preview = item.lastMessage || "";
    return `
      <div class="conversation-item ${active}" data-conversation-email="${esc(item.counterpartEmail)}" data-conversation-name="${esc(name)}">
        <div class="conversation-name">${esc(name)}</div>
        <div class="conversation-preview">${esc(preview)}</div>
      </div>
    `;
  }).join("");
}

function renderThread(messages) {
  if (!threadMessagesEl) {
    return;
  }

  if (!Array.isArray(messages) || messages.length === 0) {
    threadMessagesEl.innerHTML = "<p>Aucun message pour le moment.</p>";
    return;
  }

  threadMessagesEl.innerHTML = messages.map((msg) => {
    const self = (msg.senderEmail || "").toLowerCase() === currentUserEmail.toLowerCase();
    const cls = self ? "self" : "other";
    return `
      <div class="msg-row ${cls}">
        <div>${esc(msg.message || "")}</div>
        <span class="msg-time">${esc(msg.createdAt || "")}</span>
      </div>
    `;
  }).join("");

  threadMessagesEl.scrollTop = threadMessagesEl.scrollHeight;
}

async function loadConversations() {
  const response = await fetch("/sarbi_rohek/api/messages/conversations");
  if (!response.ok) {
    renderConversations([]);
    return;
  }

  const data = await response.json();
  renderConversations(data);
}

async function openConversation(email, name) {
  if (!email) {
    return;
  }

  selectedConversationEmail = email;
  selectedConversationName = name || email;
  selectedConversationProviderEmail = email; // Stocker l'email du prestataire
  setThreadStatus("");
  if (threadHeaderEl) {
    const headerText = threadHeaderEl.querySelector("#threadHeaderText");
    if (headerText) {
      headerText.textContent = `Conversation avec ${selectedConversationName}`;
    } else {
      threadHeaderEl.textContent = `Conversation avec ${selectedConversationName}`;
    }
  }
  
  // Afficher le bouton Commander
  const commanderBtn = document.getElementById("commanderBtn");
  if (commanderBtn) {
    commanderBtn.style.display = "block";
  }

  const response = await fetch(`/sarbi_rohek/api/messages/thread?with=${encodeURIComponent(email)}`);
  if (!response.ok) {
    renderThread([]);
    return;
  }

  const data = await response.json();
  renderThread(data);
  await loadConversations();
}

async function openMessengerPanel() {
  if (!messengerPanelEl) {
    return;
  }
  showClientView("messages");
  await loadConversations();
}

function closeMessengerPanel() {
  showClientView("providers");
  // Masquer le bouton Commander
  const commanderBtn = document.getElementById("commanderBtn");
  if (commanderBtn) {
    commanderBtn.style.display = "none";
  }
}

function commanderFromConversation() {
  if (!selectedConversationProviderEmail) {
    alert("Impossible de créer une commande. Prestataire non identifié.");
    return;
  }

  if (!confirm("Créer une demande de commande avec " + selectedConversationName + "?")) {
    return;
  }

  fetch("/sarbi_rohek/api/orders/create", {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: `providerEmail=${encodeURIComponent(selectedConversationProviderEmail)}`
  })
    .then(r => r.json())
    .then(data => {
      if (data.success) {
        alert("✓ Demande créée! " + selectedConversationName + " recevra une notification.");
        // Optionnel: rediriger vers la page de détail de la commande
        // window.location.href = `/sarbi_rohek/order-detail.html?id=${data.orderId}`;
      } else {
        alert("Erreur: " + (data.error || "Impossible de créer la demande."));
      }
    })
    .catch(err => {
      console.error("Erreur:", err);
      alert("Erreur lors de la création de la demande.");
    });
}

async function loadProviders() {
  setStatus("Chargement des prestataires...");
  let response = await fetch("/sarbi_rohek/api/providers/all");
  let data = [];

  if (response.ok) {
    data = await response.json();
  } else {
    // Temporary fallback while backend is redeployed.
    response = await fetch("/sarbi_rohek/api/providers/featured");
    if (response.ok) {
      data = await response.json();
    } else {
      providersGridEl.innerHTML = "<p>Impossible de charger les prestataires.</p>";
      setStatus("Chargement echoue.");
      return;
    }
  }

  allProviders = Array.isArray(data) ? data : [];
  fillServiceTypeFilter();
  renderProviders();
}

async function loadFavorites() {
  if (!favoritesListEl) {
    return;
  }

  const response = await fetch("/sarbi_rohek/api/favorites");
  if (!response.ok) {
    favoritesListEl.innerHTML = "<p>Aucun favori.</p>";
    favoritesSet = new Set();
    return;
  }

  const data = await response.json().catch(() => ({}));
  const favorites = Array.isArray(data.favorites) ? data.favorites : [];
  favoritesSet = new Set(favorites.map((f) => String(f || "").toLowerCase()));
  if (favorites.length === 0) {
    favoritesListEl.innerHTML = "<p>Aucun favori.</p>";
    return;
  }

  favoritesListEl.innerHTML = favorites
    .map((email) => `
      <button class="simple-list-item favorite-jump-btn" type="button" data-open-provider-email="${esc(email)}">
        ${esc(email)}
      </button>
    `)
    .join("");
}

async function toggleFavorite(providerEmail) {
  if (!providerEmail) {
    return;
  }

  const normalized = providerEmail.toLowerCase();
  const path = favoritesSet.has(normalized) ? "/remove" : "/add";
  const body = new URLSearchParams();
  body.set("providerEmail", providerEmail);

  const response = await fetch(`/sarbi_rohek/api/favorites${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString()
  });

  if (!response.ok) {
    setStatus("Impossible de mettre a jour les favoris.");
    return;
  }

  await loadFavorites();
  renderProviders();
}

async function createPayment(providerEmail) {
  const amountRaw = window.prompt("Montant a payer (TND):", "50");
  if (!amountRaw) {
    return;
  }

  const amount = Number.parseFloat(amountRaw);
  if (Number.isNaN(amount) || amount <= 0) {
    setStatus("Montant invalide.");
    return;
  }

  const body = new URLSearchParams();
  body.set("providerEmail", providerEmail);
  body.set("amount", String(amount));
  body.set("currency", "TND");

  const response = await fetch("/sarbi_rohek/api/commerce/payments/create", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString()
  });

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    setStatus(payload.message || "Paiement impossible.");
    return;
  }

  setStatus(payload.message || "Paiement cree.");
  await loadPayments();
  await loadClientDashboard();
}

async function loadPayments() {
  if (!paymentsListEl) {
    return;
  }

  const response = await fetch("/sarbi_rohek/api/commerce/payments");
  if (!response.ok) {
    paymentsListEl.innerHTML = "<p>Aucun paiement.</p>";
    return;
  }

  const data = await response.json().catch(() => ({}));
  const payments = Array.isArray(data.payments) ? data.payments : [];
  if (payments.length === 0) {
    paymentsListEl.innerHTML = "<p>Aucun paiement.</p>";
    return;
  }

  paymentsListEl.innerHTML = payments.map((p) => `
    <div class="simple-list-item">
      <strong>${esc(p.providerEmail || "-")}</strong>${toStatusPill(p.status || "PENDING")}<br/>
      ${esc(String(p.amount || 0))} ${esc(p.currency || "TND")}<br/>
      <strong>Mode commande:</strong> ${toStatusPill(p.workStatus || "NOT_STARTED")} (${esc(workStatusLabel(p.workStatus))})
    </div>
  `).join("");
}

async function loadNotifications() {
  if (!notificationsListEl) {
    return;
  }

  const response = await fetch("/sarbi_rohek/api/notifications");
  if (!response.ok) {
    notificationsListEl.innerHTML = "<p>Aucune notification.</p>";
    return;
  }

  const data = await response.json().catch(() => ({}));
  const notifications = Array.isArray(data.notifications) ? data.notifications : [];
  if (notifications.length === 0) {
    notificationsListEl.innerHTML = "<p>Aucune notification.</p>";
    return;
  }

  notificationsListEl.innerHTML = notifications.map((n) => `
    <div class="simple-list-item notification-item notification-item-${esc(notificationTypeClass(n.type))}">
      <div class="notification-head">
        <strong class="notification-type"><span class="notif-dot"></span>${esc(notificationTypeLabel(n.type))}</strong>
        <span class="notification-time">${esc(n.createdAt || "")}</span>
      </div>
      <div class="notification-body">${esc(notificationText(n.type, n.payload || ""))}</div>
    </div>
  `).join("");
}

async function loadClientDashboard() {
  const response = await fetch("/sarbi_rohek/api/dashboard/client");
  if (!response.ok) {
    return;
  }

  const data = await response.json().catch(() => ({}));
  if (clientKpiActiveQuotesEl) {
    clientKpiActiveQuotesEl.textContent = String(data.activeQuotes ?? 0);
  }
  if (clientKpiFavoritesEl) {
    clientKpiFavoritesEl.textContent = String(data.favorites ?? 0);
  }
  if (clientKpiSpentEl) {
    clientKpiSpentEl.textContent = String(data.spent ?? 0);
  }
}

async function submitReview(event) {
  event.preventDefault();
  if (!reviewProviderEmailEl || !reviewRatingEl) {
    return;
  }

  const body = new URLSearchParams();
  body.set("providerEmail", reviewProviderEmailEl.value.trim());
  body.set("rating", reviewRatingEl.value.trim());
  body.set("comment", reviewCommentEl ? reviewCommentEl.value.trim() : "");

  const response = await fetch("/sarbi_rohek/api/reviews", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString()
  });

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    setReviewStatus(payload.message || "Avis impossible.");
    return;
  }

  setReviewStatus(payload.message || "Avis enregistre.");
}

async function logoutClient() {
  try {
    await fetch("/sarbi_rohek/api/auth/logout", { method: "POST" });
  } finally {
    window.location.href = "/sarbi_rohek/login.html";
  }
}

async function loadQuotes() {
  if (!quotesListEl) return;
  
  try {
    const response = await fetch(`/sarbi_rohek/api/quotes/list?clientEmail=${encodeURIComponent(currentUserEmail)}`);
    if (!response.ok) {
      if (quotesStatusEl) quotesStatusEl.textContent = "Erreur lors du chargement des devis";
      return;
    }
    
    const quotes = await response.json().catch(() => []);
    quotesListEl.innerHTML = "";
    
    if (!Array.isArray(quotes) || quotes.length === 0) {
      if (quotesStatusEl) quotesStatusEl.textContent = "📋 Aucune demande de devis pour le moment.";
      return;
    }
    
    if (quotesStatusEl) quotesStatusEl.textContent = "";
    quotesListEl.innerHTML = quotes.map(quote => `
      <div class="simple-list-item">
        <div><strong>${esc(quote.serviceName || "Service non specifie")}</strong></div>
        <div style="font-size: 0.85em; color: #666;">Prestataire: ${esc(quote.providerEmail || "Non assigne")}</div>
        <div style="font-size: 0.85em; color: #666;">Prix: ${esc(quote.price || "0")} TND</div>
        <div>${toStatusPill(quote.status)}</div>
      </div>
    `).join("");
  } catch (error) {
    console.error("Erreur lors du chargement des devis", error);
    if (quotesStatusEl) quotesStatusEl.textContent = "❌ Erreur lors du chargement des devis";
  }
}

async function loadOrders() {
  if (!ordersListEl) return;
  
  try {
    const response = await fetch(`/sarbi_rohek/api/orders/list`, {
      method: "GET",
      credentials: "include"
    });
    if (!response.ok) {
      if (ordersStatusEl) ordersStatusEl.textContent = "Erreur lors du chargement des commandes";
      return;
    }
    
    const data = await response.json().catch(() => ({ orders: [] }));
    const orders = data.orders || [];
    ordersListEl.innerHTML = "";
    
    if (!Array.isArray(orders) || orders.length === 0) {
      ordersListEl.innerHTML = '<div class="empty-state" style="padding: 2rem; text-align: center;"><p>Aucune commande pour le moment.</p><button class="btn btn-small btn-primary" onclick="openCreateOrderModal()" style="margin-top: 1rem;">+ Créer une demande</button></div>';
      if (ordersStatusEl) ordersStatusEl.textContent = "";
      return;
    }
    
    if (ordersStatusEl) ordersStatusEl.textContent = "";
    ordersListEl.innerHTML = orders.map(order => `
      <div class="order-card" style="background: white; border: 1px solid #e0e0e0; border-radius: 8px; padding: 1.5rem; margin-bottom: 1rem; transition: box-shadow 0.2s;">
        <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
          <div>
            <div style="font-weight: 600; color: #333; font-size: 1.1rem;">${esc(order.title || "Commande #" + order.id)}</div>
            <div style="font-size: 0.9rem; color: #666; margin-top: 0.25rem;">Prestataire: ${esc(order.providerEmail || "N/A")}</div>
          </div>
          <div style="background: ${getOrderStatusColor(order.status)}; color: white; padding: 0.5rem 1rem; border-radius: 20px; font-size: 0.85rem; font-weight: 600;">${getOrderStatusLabel(order.status)}</div>
        </div>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 1rem; font-size: 0.95rem;">
          <div><span style="color: #999;">Prix:</span> ${order.price ? '<strong style="color: #667eea;">' + order.price.toFixed(2) + ' TND</strong>' : '<span style="color: #999;">À confirmer</span>'}</div>
          <div><span style="color: #999;">Paiement:</span> <strong>${getPaymentStatusLabel(order.paymentStatus)}</strong></div>
        </div>
        <div style="display: flex; gap: 0.75rem;">
          <button class="btn btn-small btn-secondary" onclick="viewOrderDetail(${order.id})">Détails</button>
          ${order.paymentStatus === "pending" && order.status === "pending_payment" ? '<button class="btn btn-small btn-success" onclick="payOrder(' + order.id + ')">Payer</button>' : ''}
        </div>
      </div>
    `).join("");
  } catch (error) {
    console.error("Erreur lors du chargement des commandes", error);
    if (ordersStatusEl) ordersStatusEl.textContent = "❌ Erreur lors du chargement des commandes";
  }
}

function getOrderStatusColor(status) {
  const colors = {
    "draft": "#999",
    "pending_details": "#ffc107",
    "pending_payment": "#ffc107",
    "paid": "#28a745",
    "in_progress": "#17a2b8",
    "delivered": "#28a745",
    "in_revision": "#fd7e14",
    "completed": "#28a745",
    "cancelled": "#dc3545"
  };
  return colors[status] || "#999";
}

function getOrderStatusLabel(status) {
  const labels = {
    "draft": "Brouillon",
    "pending_details": "En attente de détails",
    "pending_payment": "En attente de paiement",
    "paid": "Payée",
    "in_progress": "En cours",
    "delivered": "Livrée",
    "in_revision": "En révision",
    "completed": "Terminée",
    "cancelled": "Annulée"
  };
  return labels[status] || status;
}

function getPaymentStatusLabel(status) {
  const labels = {
    "pending": "En attente",
    "paid": "Payée",
    "failed": "Échouée"
  };
  return labels[status] || status;
}

function openCreateOrderModal() {
  const modal = document.getElementById("createOrderModal");
  if (modal) {
    modal.style.display = "block";
    loadProvidersForOrderSelection();
  }
}

function closeCreateOrderModal() {
  const modal = document.getElementById("createOrderModal");
  if (modal) {
    modal.style.display = "none";
  }
}

function loadProvidersForOrderSelection() {
  const select = document.getElementById("orderProviderSelect");
  if (!select) return;
  
  fetch("/sarbi_rohek/api/providers/all", {
    method: "GET",
    credentials: "include"
  })
    .then(r => r.json())
    .then(data => {
      select.innerHTML = '<option value="">-- Sélectionner un prestataire --</option>';
      (data.providers || []).forEach(p => {
        const option = document.createElement("option");
        option.value = p.email;
        option.textContent = `${p.first_name || ""} ${p.last_name || ""} (${p.email})`;
        select.appendChild(option);
      });
    })
    .catch(err => console.error("Erreur chargement prestataires:", err));
}

function viewOrderDetail(orderId) {
  window.location.href = `/sarbi_rohek/order-detail.html?id=${orderId}`;
}

function payOrder(orderId) {
  if (!confirm("Confirmer le paiement de cette commande?")) return;
  
  fetch("/sarbi_rohek/api/orders/pay", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `orderId=${orderId}`
  })
    .then(r => r.json())
    .then(data => {
      if (data.success) {
        if (ordersStatusEl) ordersStatusEl.textContent = "Paiement effectué avec succès!";
        loadOrders();
      } else {
        if (ordersStatusEl) ordersStatusEl.textContent = "Erreur: " + (data.error || "Paiement échoué.");
      }
    })
    .catch(err => {
      console.error("Erreur paiement:", err);
      if (ordersStatusEl) ordersStatusEl.textContent = "Erreur lors du paiement.";
    });
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
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: `providerEmail=${encodeURIComponent(providerEmail)}`
    })
      .then(r => r.json())
      .then(data => {
        if (data.success) {
          closeCreateOrderModal();
          loadOrders();
          if (ordersStatusEl) ordersStatusEl.textContent = "Demande créée! Le prestataire recevra une notification.";
        } else {
          alert("Erreur: " + (data.error || "Impossible de créer la demande."));
        }
      })
      .catch(err => {
        console.error("Erreur création commande:", err);
        alert("Erreur lors de la création.");
      });
  });
}

window.addEventListener("click", (event) => {
  const modal = document.getElementById("createOrderModal");
  if (event.target === modal) {
    closeCreateOrderModal();
  }
});

document.addEventListener("click", (event) => {
  const favoriteJumpBtn = event.target.closest("[data-open-provider-email]");
  if (favoriteJumpBtn) {
    const providerEmail = favoriteJumpBtn.getAttribute("data-open-provider-email") || "";
    openProviderFromFavorite(providerEmail);
    return;
  }

  const expandBtn = event.target.closest("[data-expand-index]");
  if (expandBtn) {
    const card = expandBtn.closest(".provider-card");
    if (!card) {
      return;
    }

    const expanded = card.classList.toggle("expanded");
    expandBtn.textContent = expanded ? "Voir moins" : "Voir plus";
    if (expanded) {
      const metaEmailEl = card.querySelector("[data-favorite-email]");
      const providerEmail = metaEmailEl ? (metaEmailEl.getAttribute("data-favorite-email") || "") : "";
      loadProviderReviews(providerEmail, card);
    }
    return;
  }

  const card = event.target.closest(".provider-card");
  if (card && !event.target.closest("button") && !event.target.closest("input") && !event.target.closest("a")) {
    const expandBtn = card.querySelector("[data-expand-index]");
    const expanded = card.classList.toggle("expanded");
    if (expandBtn) {
      expandBtn.textContent = expanded ? "Voir moins" : "Voir plus";
    }
    if (expanded) {
      const metaEmailEl = card.querySelector("[data-favorite-email]");
      const providerEmail = metaEmailEl ? (metaEmailEl.getAttribute("data-favorite-email") || "") : "";
      loadProviderReviews(providerEmail, card);
    }
    return;
  }

  const conversationBtn = event.target.closest("[data-message-email]");
  if (conversationBtn) {
    const email = conversationBtn.getAttribute("data-message-email") || "";
    if (isProviderPausedByEmail(email)) {
      setStatus("Ce prestataire est en pause. Messagerie temporairement desactivee.");
      return;
    }
    const name = conversationBtn.getAttribute("data-message-name") || email;
    openMessengerPanel();
    openConversation(email, name);
    return;
  }

  const favoriteBtn = event.target.closest("[data-favorite-email]");
  if (favoriteBtn) {
    const email = favoriteBtn.getAttribute("data-favorite-email") || "";
    if (isProviderPausedByEmail(email)) {
      setStatus("Ce prestataire est en pause. Action indisponible.");
      return;
    }
    toggleFavorite(email);
    return;
  }

  const payBtn = event.target.closest("[data-pay-email]");
  if (payBtn) {
    const email = payBtn.getAttribute("data-pay-email") || "";
    if (isProviderPausedByEmail(email)) {
      setStatus("Ce prestataire est en pause. Paiement temporairement indisponible.");
      return;
    }
    createPayment(email);
    return;
  }

  const conversationItem = event.target.closest("[data-conversation-email]");
  if (conversationItem) {
    const email = conversationItem.getAttribute("data-conversation-email") || "";
    const name = conversationItem.getAttribute("data-conversation-name") || email;
    openConversation(email, name);
    return;
  }
});

if (filterServiceTypeEl) {
  filterServiceTypeEl.addEventListener("change", renderProviders);
}

if (filterKeywordEl) {
  filterKeywordEl.addEventListener("input", renderProviders);
}

if (logoutBtnEl) {
  logoutBtnEl.addEventListener("click", logoutClient);
}

if (openMessagesBtnEl) {
  openMessagesBtnEl.addEventListener("click", async () => {
    await openMessengerPanel();
  });
}

if (threadFormEl) {
  threadFormEl.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!selectedConversationEmail || !threadInputEl) {
      setThreadStatus("Selectionnez d'abord une conversation.");
      return;
    }

    const message = threadInputEl.value.trim();
    if (!message) {
      setThreadStatus("Le message est vide.");
      return;
    }

    setThreadStatus("Envoi en cours...");

    const body = new URLSearchParams();
    body.set("recipientEmail", selectedConversationEmail);
    body.set("message", message);

    const response = await fetch("/sarbi_rohek/api/messages/send", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: body.toString()
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      setThreadStatus(errorData.message || `Envoi impossible (HTTP ${response.status}).`);
      return;
    }

    threadInputEl.value = "";
    await openConversation(selectedConversationEmail, selectedConversationName);
    setThreadStatus("Message envoye.");
  });
}

if (reviewFormEl) {
  reviewFormEl.addEventListener("submit", submitReview);
}

clientViewTabs.forEach((tab) => {
  tab.addEventListener("click", () => {
    showClientView(tab.getAttribute("data-client-view-tab") || "dashboard");
  });
});

(async function initClientSpace() {
  try {
    const sessionData = await loadClientSession();
    if (!sessionData) {
      return;
    }

    currentUserEmail = (sessionData.email || "").trim();
    startSessionGuard();
    await loadClientName(sessionData);
    await loadClientDashboard();
    await loadFavorites();
    await loadPayments();
    await loadNotifications();
    await loadQuotes();
    await loadOrders();
    await loadProviders();
    showClientView("providers");
  } catch (error) {
    redirectToLoginWithReason("");
  }
})();
