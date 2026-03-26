function byId(id) {
  return document.getElementById(id);
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

let allProviders = [];
let currentUserEmail = "";
let selectedConversationEmail = "";
let selectedConversationName = "";

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

  return `
    <article class="provider-card" data-card-index="${index}">
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

      <p class="provider-main">${esc(description)}</p>
      <button class="card-expand-btn" type="button" data-expand-index="${index}">Voir plus</button>
      <button class="card-message-btn" type="button" data-message-email="${esc(email)}" data-message-name="${esc(fullName)}">
        Envoyer un message
      </button>

      <div class="provider-extra">
        <p><strong>Description complete:</strong> ${esc(description)}</p>
      </div>
    </article>
  `;
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
    window.location.href = "/sarbi_rohek/login.html";
    return null;
  }

  const data = await response.json();
  if (!data.loggedIn) {
    window.location.href = "/sarbi_rohek/login.html";
    return null;
  }

  if ((data.role || "").trim() === "PROVIDER") {
    window.location.href = "/sarbi_rohek/provider-space.html";
    return null;
  }

  return data;
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
  setThreadStatus("");
  if (threadHeaderEl) {
    threadHeaderEl.textContent = `Conversation avec ${selectedConversationName}`;
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

  if (messengerPanelEl.hidden) {
    messengerPanelEl.hidden = false;
  }
  messengerPanelEl.style.display = "grid";

  await loadConversations();
}

function closeMessengerPanel() {
  if (!messengerPanelEl) {
    return;
  }

  messengerPanelEl.hidden = true;
  messengerPanelEl.style.display = "none";
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

async function logoutClient() {
  try {
    await fetch("/sarbi_rohek/api/auth/logout", { method: "POST" });
  } finally {
    window.location.href = "/sarbi_rohek/login.html";
  }
}

document.addEventListener("click", (event) => {
  const expandBtn = event.target.closest("[data-expand-index]");
  if (expandBtn) {
    const card = expandBtn.closest(".provider-card");
    if (!card) {
      return;
    }

    const expanded = card.classList.toggle("expanded");
    expandBtn.textContent = expanded ? "Voir moins" : "Voir plus";
    return;
  }

  const conversationBtn = event.target.closest("[data-message-email]");
  if (conversationBtn) {
    const email = conversationBtn.getAttribute("data-message-email") || "";
    const name = conversationBtn.getAttribute("data-message-name") || email;
    openMessengerPanel();
    openConversation(email, name);
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
    if (!messengerPanelEl) {
      return;
    }

    if (messengerPanelEl.hidden) {
      await openMessengerPanel();
    } else {
      closeMessengerPanel();
    }
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

(async function initClientSpace() {
  try {
    if (messengerPanelEl) {
      messengerPanelEl.hidden = true;
      messengerPanelEl.style.display = "none";
    }

    const sessionData = await loadClientSession();
    if (!sessionData) {
      return;
    }

    currentUserEmail = (sessionData.email || "").trim();
    await loadClientName(sessionData);
    await loadProviders();
  } catch (error) {
    window.location.href = "/sarbi_rohek/login.html";
  }
})();
