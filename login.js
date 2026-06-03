const flash = document.getElementById("flashMessage");
const loginForm = document.getElementById("loginForm");
const ADMIN_CONTACT_EMAIL = "roheksarbi@gmail.com";

function containsBlacklistMessage(message) {
  return (message || "").toLowerCase().includes("blacklist");
}

function apiBase() {
  return (window.NEXT_PUBLIC_API_URL || 'https://ton-backend.onrender.com').replace(/\/$/, '');
}

function renderFlash(message, kind) {
  if (!flash) {
    return;
  }

  flash.hidden = false;
  flash.classList.remove("is-error", "is-success");
  flash.classList.add(kind === "error" ? "is-error" : "is-success");
  flash.textContent = message || "";

  if (kind === "error" && containsBlacklistMessage(message)) {
    const lineBreak = document.createElement("br");
    const link = document.createElement("a");
    link.href = `mailto:${ADMIN_CONTACT_EMAIL}`;
    link.textContent = `Contacter l'administrateur: ${ADMIN_CONTACT_EMAIL}`;
    link.className = "flash-contact-link";
    flash.appendChild(lineBreak);
    flash.appendChild(link);
  }
}

async function loadAuthStatus() {
  try {
    const response = await fetch(apiBase() + "/api/auth/status");
    if (!response.ok) {
      return;
    }

    const data = await response.json();
    const isClientLoggedIn = Boolean(data.loggedIn);

    if (!isClientLoggedIn) {
      return;
    }

    const role = (data.role || "").trim();
    if (role === "PROVIDER") {
      window.location.href = "/sarbi_rohek/provider-space.html";
      return;
    }

    window.location.href = "/sarbi_rohek/client-space.html";
  } catch (error) {
    // No active session, stay on login page.
  }
}

function showMessageFromQuery() {
  const params = new URLSearchParams(window.location.search);
  const error = params.get("error");
  const success = params.get("success");

  if (!flash) {
    return;
  }

  if (error) {
    renderFlash(error, "error");
    return;
  }

  if (success) {
    renderFlash(success, "success");
  }
}

function fixLoginAction() {
  if (!loginForm) {
    return;
  }

  loginForm.action = apiBase() + "/api/login";
}

showMessageFromQuery();
fixLoginAction();

loadAuthStatus();
