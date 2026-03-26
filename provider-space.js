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

let currentProviderEmail = "";
let selectedConversationEmail = "";
let selectedConversationName = "";
let currentProviderProfile = null;

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

async function ensureProviderSession() {
  try {
    const response = await fetch("/sarbi_rohek/api/auth/status");
    if (!response.ok) {
      window.location.href = "/sarbi_rohek/login.html";
      return;
    }

    const data = await response.json();
    const loggedIn = Boolean(data.loggedIn);
    const role = (data.role || "").trim();

    if (!loggedIn) {
      window.location.href = "/sarbi_rohek/login.html";
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
  } catch (error) {
    window.location.href = "/sarbi_rohek/login.html";
  }
}

async function logoutProvider() {
  try {
    await fetch("/sarbi_rohek/api/auth/logout", { method: "POST" });
  } finally {
    window.location.href = "/sarbi_rohek/login.html";
  }
}

ensureProviderSession();

document.addEventListener("click", (event) => {
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

if (providerLogoutBtnEl) {
  providerLogoutBtnEl.addEventListener("click", logoutProvider);
}
