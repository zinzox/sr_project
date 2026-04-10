function byId(id) {
  return document.getElementById(id);
}

const photoEl = byId("providerPhoto");
const photoUrlInputEl = byId("photoUrlInput");
const savePhotoBtnEl = byId("savePhotoBtn");
const photoStatusEl = byId("photoStatus");
const providerHelloNameEl = byId("providerHelloName");
const providerLogoutBtnEl = byId("providerLogoutBtn");
const providerConversationsEl = byId("providerConversations");
const providerThreadHeaderEl = byId("providerThreadHeader");
const providerThreadMessagesEl = byId("providerThreadMessages");
const providerThreadFormEl = byId("providerThreadForm");
const providerThreadInputEl = byId("providerThreadInput");
const providerThreadStatusEl = byId("providerThreadStatus");
const editProfileToggleBtnEl = byId("editProfileToggleBtn");
const providerEditFormEl = byId("providerEditForm");
const cancelEditProfileBtnEl = byId("cancelEditProfileBtn");
const editServiceTypeEl = byId("editServiceType");
const editMainActivityEl = byId("editMainActivity");
const editWorkTitleEl = byId("editWorkTitle");
const editPhoneEl = byId("editPhone");
const editWorkDescriptionEl = byId("editWorkDescription");
const editProfileStatusEl = byId("editProfileStatus");
const providerKpiPendingEl = byId("providerKpiPending");
const providerKpiInProgressEl = byId("providerKpiInProgress");
const providerKpiEarnedEl = byId("providerKpiEarned");
const slotFormEl = byId("slotForm");
const slotStartAtEl = byId("slotStartAt");
const slotEndAtEl = byId("slotEndAt");
const providerSlotsListEl = byId("providerSlotsList");
const slotStatusEl = byId("slotStatus");
const providerNotificationsListEl = byId("providerNotificationsList");
const providerViewTabs = Array.from(document.querySelectorAll("[data-provider-view-tab]"));
const providerViews = Array.from(document.querySelectorAll("[data-provider-view]"));

let currentProviderEmail = "";
let selectedConversationEmail = "";
let selectedConversationName = "";
let currentProviderProfile = null;

function showProviderView(viewName) {
  const normalized = String(viewName || "dashboard").trim();
  providerViewTabs.forEach((tab) => {
    const active = tab.getAttribute("data-provider-view-tab") === normalized;
    tab.classList.toggle("active", active);
  });

  providerViews.forEach((view) => {
    const active = view.getAttribute("data-provider-view") === normalized;
    view.classList.toggle("active", active);
  });

  if (normalized === "dashboard") {
    loadProviderDashboard().catch(() => {});
  } else if (normalized === "order-requests") {
    loadOrderRequests().catch(() => {});
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

function setText(id, value) {
  const el = byId(id);
  if (el) {
    el.textContent = value;
  }
}

function fillProfile(profile) {
  currentProviderProfile = profile;
  setText("providerEmail", profile.email || "-");
  setText("providerName", `${profile.firstName || ""} ${profile.lastName || ""}`.trim() || "-");
  setText("providerPhone", profile.phone || "-");
  setText("providerServiceType", profile.serviceType || "-");
  setText("providerMainActivity", profile.mainActivity || "-");
  setText("providerWorkTitle", profile.workTitle || "-");
  setText("providerWorkDescription", profile.workDescription || "-");

  if (photoEl) {
    photoEl.src = profile.photoUrl || "logo.png";
  }

  if (photoUrlInputEl) {
    photoUrlInputEl.value = profile.photoUrl || "";
  }

  if (editServiceTypeEl) {
    editServiceTypeEl.value = profile.serviceType || "SERVICE_PRO";
  }
  if (editMainActivityEl) {
    editMainActivityEl.value = profile.mainActivity || "";
  }
  if (editWorkTitleEl) {
    editWorkTitleEl.value = profile.workTitle || "";
  }
  if (editPhoneEl) {
    editPhoneEl.value = profile.phone || "";
  }
  if (editWorkDescriptionEl) {
    editWorkDescriptionEl.value = profile.workDescription || "";
  }
}

function setPhotoStatus(message) {
  if (photoStatusEl) {
    photoStatusEl.textContent = message;
  }
}

function setThreadStatus(message) {
  if (providerThreadStatusEl) {
    providerThreadStatusEl.textContent = message || "";
  }
}

function setEditProfileStatus(message) {
  if (editProfileStatusEl) {
    editProfileStatusEl.textContent = message || "";
  }
}

function setSlotStatus(message) {
  if (slotStatusEl) {
    slotStatusEl.textContent = message || "";
  }
}

function closeEditProfileForm() {
  if (providerEditFormEl) {
    providerEditFormEl.hidden = true;
  }
}

function openEditProfileForm() {
  if (providerEditFormEl) {
    providerEditFormEl.hidden = false;
  }
}

function esc(value) {
  return (value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function providerStatusPill(status) {
  const raw = String(status || "PENDING").trim().toUpperCase();
  const css = raw.toLowerCase();
  return `<span class="provider-status-pill ${esc(css)}">${esc(raw)}</span>`;
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
    return `La tache #${payload.paymentId || "-"} est ${statusLabelFr(payload.workStatus)}.`;
  }
  if (t === "PAYMENT_CREATED") {
    return `Le client ${payload.clientEmail || "-"} a lance le paiement #${payload.paymentId || "-"}.`;
  }
  if (t === "PAYMENT_CONFIRMED") {
    return `Le client ${payload.clientEmail || "-"} a paye la commande #${payload.paymentId || "-"}.`;
  }
  if (t === "REVIEW_POSTED") {
    return `Le client ${payload.clientEmail || "-"} a laisse un commentaire (${payload.rating || "-"}/5).`;
  }
  if (t === "NEW_REVIEW") {
    return `Le client ${payload.clientEmail || "-"} a laisse un commentaire (${payload.rating || "-"}/5).`;
  }
  if (t === "NEW_PAYMENT") {
    return `Le client ${payload.clientEmail || "-"} a paye la commande #${payload.paymentId || "-"}.`;
  }

  return notificationDetails(payloadRaw);
}

function notificationTypeLabel(type) {
  const raw = String(type || "INFO").trim().toUpperCase();
  if (raw === "TASK_STATUS_UPDATED") {
    return "Tache mise a jour";
  }
  if (raw === "PAYMENT_CREATED") {
    return "Paiement client";
  }
  if (raw === "PAYMENT_CONFIRMED") {
    return "Paiement client";
  }
  if (raw === "REVIEW_POSTED") {
    return "Commentaire client";
  }
  if (raw === "NEW_PAYMENT") {
    return "Paiement client";
  }
  if (raw === "NEW_REVIEW") {
    return "Commentaire client";
  }
  return raw;
}

function isProviderKeyNotification(type) {
  const raw = String(type || "").trim().toUpperCase();
  return raw === "NEW_PAYMENT"
    || raw === "PAYMENT_CREATED"
    || raw === "PAYMENT_CONFIRMED"
    || raw === "NEW_REVIEW"
    || raw === "REVIEW_POSTED";
}

function notificationTypeClass(type) {
  return String(type || "info")
    .trim()
    .toLowerCase()
    .replaceAll(/[^a-z0-9]+/g, "-")
    .replaceAll(/^-+|-+$/g, "");
}

function renderConversations(conversations) {
  if (!providerConversationsEl) {
    return;
  }

  if (!Array.isArray(conversations) || conversations.length === 0) {
    providerConversationsEl.innerHTML = "<p>Aucune conversation.</p>";
    return;
  }

  providerConversationsEl.innerHTML = conversations.map((item) => {
    const active = item.counterpartEmail === selectedConversationEmail ? "active" : "";
    const name = item.counterpartName || item.counterpartEmail;
    return `
      <div class="conversation-item ${active}" data-conversation-email="${esc(item.counterpartEmail)}" data-conversation-name="${esc(name)}">
        <div class="conversation-name">${esc(name)}</div>
        <div class="conversation-preview">${esc(item.lastMessage || "")}</div>
      </div>
    `;
  }).join("");
}

function renderThread(messages) {
  if (!providerThreadMessagesEl) {
    return;
  }

  if (!Array.isArray(messages) || messages.length === 0) {
    providerThreadMessagesEl.innerHTML = "<p>Aucun message dans cette conversation.</p>";
    return;
  }

  providerThreadMessagesEl.innerHTML = messages.map((msg) => {
    const self = (msg.senderEmail || "").toLowerCase() === currentProviderEmail.toLowerCase();
    const cls = self ? "self" : "other";
    return `
      <div class="msg-row ${cls}">
        <div>${esc(msg.message || "")}</div>
        <span class="msg-time">${esc(msg.createdAt || "")}</span>
      </div>
    `;
  }).join("");

  providerThreadMessagesEl.scrollTop = providerThreadMessagesEl.scrollHeight;
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
  setThreadStatus("");
  if (providerThreadHeaderEl) {
    providerThreadHeaderEl.textContent = `Conversation avec ${selectedConversationName}`;
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

async function savePhoto() {
  const email = currentProviderEmail;
  const photoUrl = photoUrlInputEl ? photoUrlInputEl.value.trim() : "";

  if (!email) {
    setPhotoStatus("Email manquant. Reconnectez-vous.");
    return;
  }

  setPhotoStatus("Enregistrement...");

  try {
    const response = await fetch(
      `/sarbi_rohek/api/provider-profile?email=${encodeURIComponent(email)}&photoUrl=${encodeURIComponent(photoUrl)}`,
      { method: "PUT" }
    );

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      setPhotoStatus(errorData.message || "Mise a jour impossible.");
      return;
    }

    const updated = await response.json();
    fillProfile(updated);
    setPhotoStatus("Photo enregistree.");
  } catch (error) {
    setPhotoStatus("Erreur reseau pendant la mise a jour.");
  }
}

async function loadProviderProfile(email) {
  if (!email) {
    setText("providerWorkDescription", "Session manquante. Reconnectez-vous.");
    return;
  }

  setText("providerEmail", email);

  try {
    const response = await fetch(`/sarbi_rohek/api/provider-profile?email=${encodeURIComponent(email)}`);
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      setText("providerWorkDescription", errorData.message || "Impossible de charger les donnees du prestataire.");
      return;
    }

    const data = await response.json();
    fillProfile(data);
  } catch (error) {
    setText("providerWorkDescription", "Erreur reseau lors du chargement du profil.");
  }
}

async function saveProviderProfileEdits() {
  if (!currentProviderEmail) {
    setEditProfileStatus("Session manquante. Reconnectez-vous.");
    return;
  }

  const serviceType = editServiceTypeEl ? editServiceTypeEl.value.trim() : "";
  const mainActivity = editMainActivityEl ? editMainActivityEl.value.trim() : "";
  const workTitle = editWorkTitleEl ? editWorkTitleEl.value.trim() : "";
  const phone = editPhoneEl ? editPhoneEl.value.trim() : "";
  const workDescription = editWorkDescriptionEl ? editWorkDescriptionEl.value.trim() : "";

  if (!serviceType) {
    setEditProfileStatus("Choisissez un type de service.");
    return;
  }

  setEditProfileStatus("Enregistrement...");

  try {
    const query = new URLSearchParams();
    query.set("email", currentProviderEmail);
    query.set("serviceType", serviceType);
    query.set("mainActivity", mainActivity);
    query.set("workTitle", workTitle);
    query.set("phone", phone);
    query.set("workDescription", workDescription);

    const response = await fetch(`/sarbi_rohek/api/provider-profile?${query.toString()}`, {
      method: "PUT"
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      setEditProfileStatus(errorData.message || "Mise a jour impossible.");
      return;
    }

    const updated = await response.json();
    fillProfile(updated);
    setEditProfileStatus("Profil mis a jour.");
    closeEditProfileForm();
  } catch (error) {
    setEditProfileStatus("Erreur reseau pendant la mise a jour.");
  }
}

async function loadProviderDashboard() {
  const response = await fetch("/sarbi_rohek/api/dashboard/provider");
  if (!response.ok) {
    return;
  }

  const data = await response.json().catch(() => ({}));
  if (providerKpiPendingEl) {
    providerKpiPendingEl.textContent = String(data.pendingQuotes ?? 0);
  }
  if (providerKpiInProgressEl) {
    providerKpiInProgressEl.textContent = String(data.inProgress ?? 0);
  }
  if (providerKpiEarnedEl) {
    providerKpiEarnedEl.textContent = String(data.earned ?? 0);
  }
}

async function loadProviderSlots() {
  if (!providerSlotsListEl || !currentProviderEmail) {
    return;
  }

  const response = await fetch(`/sarbi_rohek/api/slots?providerEmail=${encodeURIComponent(currentProviderEmail)}`);
  if (!response.ok) {
    providerSlotsListEl.innerHTML = "<p>Aucun creneau.</p>";
    return;
  }

  const data = await response.json().catch(() => ({}));
  const slots = Array.isArray(data.slots) ? data.slots : [];
  if (slots.length === 0) {
    providerSlotsListEl.innerHTML = "<p>Aucun creneau.</p>";
    return;
  }

  providerSlotsListEl.innerHTML = slots.map((slot) => `
    <div class="provider-list-item">
      ${esc(slot.startAt || "")} -> ${esc(slot.endAt || "")} ${providerStatusPill(slot.status || "AVAILABLE")}
      <button class="mini-btn" type="button" data-slot-delete-id="${esc(String(slot.id || ""))}">Supprimer</button>
    </div>
  `).join("");
}

async function createSlot(event) {
  event.preventDefault();
  if (!slotStartAtEl || !slotEndAtEl) {
    return;
  }

  const body = new URLSearchParams();
  body.set("startAt", slotStartAtEl.value.trim());
  body.set("endAt", slotEndAtEl.value.trim());

  const response = await fetch("/sarbi_rohek/api/slots/create", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString()
  });

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    setSlotStatus(payload.message || "Pause impossible.");
    return;
  }

  setSlotStatus(payload.message || "Intervalle de pause cree.");
  slotStartAtEl.value = "";
  slotEndAtEl.value = "";
  await loadProviderSlots();
}

async function deleteSlot(slotId) {
  const body = new URLSearchParams();
  body.set("slotId", slotId);

  const response = await fetch("/sarbi_rohek/api/slots/delete", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString()
  });

  if (!response.ok) {
    setSlotStatus("Suppression impossible.");
    return;
  }

  await loadProviderSlots();
}

async function loadProviderNotifications() {
  if (!providerNotificationsListEl) {
    return;
  }

  const response = await fetch("/sarbi_rohek/api/notifications");
  if (!response.ok) {
    providerNotificationsListEl.innerHTML = "<p>Aucune notification.</p>";
    return;
  }

  const data = await response.json().catch(() => ({}));
  const notifications = Array.isArray(data.notifications) ? data.notifications : [];
  const filtered = notifications.filter((n) => isProviderKeyNotification(n.type));
  if (filtered.length === 0) {
    providerNotificationsListEl.innerHTML = "<p>Aucune notification de paiement ou commentaire.</p>";
    return;
  }

  providerNotificationsListEl.innerHTML = filtered.map((n) => `
    <div class="provider-list-item notification-item notification-item-${esc(notificationTypeClass(n.type))}">
      <div class="notification-head">
        <strong class="notification-type">${esc(notificationTypeLabel(n.type))}</strong>
        <span class="notification-time">${esc(n.createdAt || "")}</span>
      </div>
      <div class="notification-body">${esc(notificationText(n.type, n.payload || ""))}</div>
    </div>
  `).join("");
}

async function loadProviderPayments() {
  if (!providerPaymentsListEl) {
    return;
  }

  const response = await fetch("/sarbi_rohek/api/commerce/payments");
  if (!response.ok) {
    providerPaymentsListEl.innerHTML = "<p>Aucun paiement.</p>";
    return;
  }

  const data = await response.json().catch(() => ({}));
  const payments = Array.isArray(data.payments) ? data.payments : [];
  if (payments.length === 0) {
    providerPaymentsListEl.innerHTML = "<p>Aucun paiement.</p>";
    return;
  }

  providerPaymentsListEl.innerHTML = payments.map((p) => `
    <div class="provider-list-item">
      ${esc(p.clientEmail || "-")} - ${esc(String(p.amount || 0))} ${esc(p.currency || "TND")} ${providerStatusPill(p.status || "PENDING")}<br/>
      <strong>Mode commande:</strong> ${providerStatusPill(p.workStatus || "NOT_STARTED")} (${esc(workStatusLabel(p.workStatus))})<br/>
      <button class="mini-btn" type="button" data-payment-work-status="NOT_STARTED" data-payment-id="${esc(String(p.id || ""))}">Pas commencee</button>
      <button class="mini-btn" type="button" data-payment-work-status="IN_PROGRESS" data-payment-id="${esc(String(p.id || ""))}">En cours</button>
      <button class="mini-btn" type="button" data-payment-work-status="COMPLETED" data-payment-id="${esc(String(p.id || ""))}">Terminee</button>
    </div>
  `).join("");
}

async function updatePaymentWorkStatus(paymentId, workStatus) {
  const body = new URLSearchParams();
  body.set("paymentId", String(paymentId || ""));
  body.set("workStatus", String(workStatus || ""));

  const response = await fetch("/sarbi_rohek/api/commerce/payments/work-status/update", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString()
  });

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    setEditProfileStatus(payload.message || "Mise a jour du mode commande impossible.");
    return;
  }

  setEditProfileStatus(payload.message || "Mode commande mis a jour.");
  await loadProviderPayments();
}

async function ensureProviderSession() {
  try {
    const response = await fetch("/sarbi_rohek/api/auth/status");
    if (!response.ok) {
      redirectToLoginWithReason("");
      return;
    }

    const data = await response.json();
    const loggedIn = Boolean(data.loggedIn);
    const role = (data.role || "").trim();

    if (!loggedIn) {
      redirectToLoginWithReason(data.message || "");
      return;
    }

    if (role !== "PROVIDER") {
      window.location.href = "/sarbi_rohek/index.html";
      return;
    }

    currentProviderEmail = (data.email || "").trim();
    const fullName = `${(data.firstName || "").trim()} ${(data.lastName || "").trim()}`.trim();
    if (providerHelloNameEl) {
      providerHelloNameEl.textContent = fullName ? `Bonjour, ${fullName}` : "Bonjour";
    }

    await loadProviderProfile(currentProviderEmail);
    await loadConversations();
    await loadProviderDashboard();
    await loadProviderSlots();
    await loadPortfolio();
    await loadSlots();
    await loadProviderNotifications();
    await loadProviderPayments();
    await loadOrderRequests();
    showProviderView("profile");
  } catch (error) {
    redirectToLoginWithReason("");
  }
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

async function logoutProvider() {
  try {
    await fetch("/sarbi_rohek/api/auth/logout", { method: "POST" });
  } finally {
    window.location.href = "/sarbi_rohek/login.html";
  }
}

ensureProviderSession();
startSessionGuard();

document.addEventListener("click", (event) => {
  const slotDeleteBtn = event.target.closest("[data-slot-delete-id]");
  if (slotDeleteBtn) {
    const slotId = slotDeleteBtn.getAttribute("data-slot-delete-id") || "";
    deleteSlot(slotId);
    return;
  }

  const paymentWorkBtn = event.target.closest("[data-payment-work-status]");
  if (paymentWorkBtn) {
    const paymentId = paymentWorkBtn.getAttribute("data-payment-id") || "";
    const workStatus = paymentWorkBtn.getAttribute("data-payment-work-status") || "";
    updatePaymentWorkStatus(paymentId, workStatus);
    return;
  }

  const item = event.target.closest("[data-conversation-email]");
  if (!item) {
    return;
  }

  const email = item.getAttribute("data-conversation-email") || "";
  const name = item.getAttribute("data-conversation-name") || email;
  openConversation(email, name);
});

if (savePhotoBtnEl) {
  savePhotoBtnEl.addEventListener("click", savePhoto);
}

if (editProfileToggleBtnEl) {
  editProfileToggleBtnEl.addEventListener("click", () => {
    if (!providerEditFormEl) {
      return;
    }

    if (providerEditFormEl.hidden) {
      if (currentProviderProfile) {
        fillProfile(currentProviderProfile);
      }
      openEditProfileForm();
      setEditProfileStatus("");
    } else {
      closeEditProfileForm();
    }
  });
}

if (cancelEditProfileBtnEl) {
  cancelEditProfileBtnEl.addEventListener("click", () => {
    closeEditProfileForm();
    setEditProfileStatus("");
    if (currentProviderProfile) {
      fillProfile(currentProviderProfile);
    }
  });
}

if (providerEditFormEl) {
  providerEditFormEl.addEventListener("submit", async (event) => {
    event.preventDefault();
    await saveProviderProfileEdits();
  });
}

if (providerThreadFormEl) {
  providerThreadFormEl.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!selectedConversationEmail || !providerThreadInputEl) {
      setThreadStatus("Selectionnez d'abord une conversation.");
      return;
    }

    const message = providerThreadInputEl.value.trim();
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

    providerThreadInputEl.value = "";
    await openConversation(selectedConversationEmail, selectedConversationName);
    setThreadStatus("Message envoye.");
  });
}

if (slotFormEl) {
  slotFormEl.addEventListener("submit", createSlot);
}

// ========== ORDER REQUESTS FUNCTIONS ==========

async function loadOrderRequests() {
  const listEl = document.getElementById("orderRequestsList");
  const statusEl = document.getElementById("orderRequestsStatus");
  if (!listEl) return;

  try {
    const response = await fetch("/sarbi_rohek/api/orders/requests", {
      method: "GET",
      credentials: "include"
    });

    if (!response.ok) {
      if (statusEl) statusEl.textContent = "Erreur lors du chargement";
      return;
    }

    const data = await response.json().catch(() => ({ requests: [] }));
    const requests = data.requests || [];

    if (requests.length === 0) {
      listEl.innerHTML = '<div style="text-align: center; padding: 2rem; color: #999;">Aucune demande pour le moment.</div>';
      if (statusEl) statusEl.textContent = "";
      return;
    }

    if (statusEl) statusEl.textContent = "";

    // Group by status
    const pending = requests.filter(r => r.status === "draft" || r.status === "pending_details");
    const paymentPending = requests.filter(r => r.status === "pending_payment");
    const active = requests.filter(r => ["paid", "in_progress", "delivered", "in_revision"].includes(r.status));
    const completed = requests.filter(r => ["completed", "cancelled"].includes(r.status));

    let html = "";
    if (pending.length > 0) {
      html += '<h3 style="color: #667eea; border-bottom: 2px solid #e0e0e0; padding-bottom: 0.75rem; margin-top: 1.5rem;">À remplir</h3>';
      html += pending.map(r => renderOrderRequestCard(r, true)).join("");
    }
    if (paymentPending.length > 0) {
      html += '<h3 style="color: #667eea; border-bottom: 2px solid #e0e0e0; padding-bottom: 0.75rem; margin-top: 1.5rem;">En attente de paiement</h3>';
      html += paymentPending.map(r => renderOrderRequestCard(r, false)).join("");
    }
    if (active.length > 0) {
      html += '<h3 style="color: #667eea; border-bottom: 2px solid #e0e0e0; padding-bottom: 0.75rem; margin-top: 1.5rem;">En cours</h3>';
      html += active.map(r => renderOrderRequestCard(r, false)).join("");
    }
    if (completed.length > 0) {
      html += '<h3 style="color: #667eea; border-bottom: 2px solid #e0e0e0; padding-bottom: 0.75rem; margin-top: 1.5rem;">Terminées</h3>';
      html += completed.map(r => renderOrderRequestCard(r, false)).join("");
    }

    listEl.innerHTML = html;
  } catch (error) {
    console.error("Erreur:", error);
    if (statusEl) statusEl.textContent = "❌ Erreur lors du chargement";
  }
}

function renderOrderRequestCard(order, showFillButton) {
  const statusColors = {
    "draft": "#999",
    "pending_details": "#ffc107",
    "pending_payment": "#ffc107",
    "paid": "#28a745",
    "in_progress": "#17a2b8",
    "delivered": "#28a745",
    "in_revision": "#fd7e14",
    "completed": "#28a745"
  };

  const statusLabels = {
    "draft": "Brouillon",
    "pending_details": "À remplir",
    "pending_payment": "En attente de paiement",
    "paid": "Payée - En cours",
    "in_progress": "En cours",
    "delivered": "Livrée",
    "in_revision": "Révision demandée",
    "completed": "Terminée"
  };

  return `
    <div style="background: white; border: 1px solid #e0e0e0; border-radius: 8px; padding: 1.5rem; margin-bottom: 1rem; transition: box-shadow 0.2s;">
      <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
        <div>
          <div style="font-weight: 600; color: #333; font-size: 1.1rem;">Commande #${order.id}</div>
          <div style="font-size: 0.9rem; color: #666; margin-top: 0.25rem;">Client: ${escapeHtml(order.clientEmail || "N/A")}</div>
        </div>
        <div style="background: ${statusColors[order.status] || "#999"}; color: white; padding: 0.5rem 1rem; border-radius: 20px; font-size: 0.85rem; font-weight: 600;">${statusLabels[order.status] || order.status}</div>
      </div>
      <div style="margin-bottom: 1rem;">
        <div style="font-weight: 600; color: #333; margin-bottom: 0.5rem;">${escapeHtml(order.title || "(sans titre)")}</div>
        <div style="color: #666; font-size: 0.95rem; line-height: 1.4;">${escapeHtml((order.description || "").substring(0, 150))}${(order.description || "").length > 150 ? "..." : ""}</div>
      </div>
      ${order.price ? `<div style="color: #667eea; font-weight: 600; font-size: 1.1rem; margin-bottom: 1rem;">Prix: ${order.price.toFixed(2)} TND</div>` : ""}
      <div style="display: flex; gap: 0.75rem;">
        ${showFillButton ? `<button class="btn ghost" onclick="openFillDetailsModal(${order.id})" style="padding: 0.5rem 1rem; background: #667eea; color: white; border: none; border-radius: 4px; cursor: pointer;">Remplir les détails</button>` : ""}
        <button class="btn ghost" onclick="viewOrderDetail(${order.id})" style="padding: 0.5rem 1rem;">Détails</button>
        ${["paid", "in_progress"].includes(order.status) ? `<button class="btn ghost" onclick="deliverOrder(${order.id})" style="padding: 0.5rem 1rem; background: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer;">Livrer</button>` : ""}
      </div>
    </div>
  `;
}

function openFillDetailsModal(orderId) {
  const modal = document.getElementById("fillDetailsModal");
  if (!modal) return;

  document.getElementById("modalOrderId").value = orderId;
  document.getElementById("detailTitle").value = "";
  document.getElementById("detailDescription").value = "";
  document.getElementById("detailPrice").value = "";
  document.getElementById("detailDeliveryDate").value = "";
  document.getElementById("detailRevisions").value = "3";

  modal.style.display = "flex";
}

function closeFillDetailsModal() {
  const modal = document.getElementById("fillDetailsModal");
  if (modal) {
    modal.style.display = "none";
  }
}

function viewOrderDetail(orderId) {
  window.location.href = `/sarbi_rohek/order-detail.html?id=${orderId}`;
}

function deliverOrder(orderId) {
  if (!confirm("Confirmer la livraison de cette commande?")) return;

  fetch("/sarbi_rohek/api/orders/deliver", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `orderId=${orderId}`
  })
    .then(r => r.json())
    .then(data => {
      if (data.success) {
        const statusEl = document.getElementById("orderRequestsStatus");
        if (statusEl) statusEl.textContent = "Commande livrée!";
        loadOrderRequests();
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
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: `orderId=${orderId}&title=${encodeURIComponent(title)}&description=${encodeURIComponent(description)}&price=${price}&estimatedDeliveryDate=${encodeURIComponent(deliveryDate)}&revisions=${revisions}`
    })
      .then(r => r.json())
      .then(data => {
        if (data.success) {
          closeFillDetailsModal();
          loadOrderRequests();
          const statusEl = document.getElementById("orderRequestsStatus");
          if (statusEl) statusEl.textContent = "Détails envoyés au client!";
        } else {
          alert("Erreur: " + (data.error || "Impossible de mettre à jour."));
        }
      });
  });
}

window.addEventListener("click", (event) => {
  const modal = document.getElementById("fillDetailsModal");
  if (event.target === modal) {
    closeFillDetailsModal();
  }
});

function escapeHtml(text) {
  if (!text) return "";
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

if (providerLogoutBtnEl) {
  providerLogoutBtnEl.addEventListener("click", logoutProvider);
}

providerViewTabs.forEach((tab) => {
  tab.addEventListener("click", () => {
    showProviderView(tab.getAttribute("data-provider-view-tab") || "dashboard");
  });
});
