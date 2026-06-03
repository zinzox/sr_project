const flash = document.getElementById("flashMessage");
const loginForm = document.getElementById("loginForm");
const ADMIN_CONTACT_EMAIL = "roheksarbi@gmail.com";

function containsBlacklistMessage(message) {
  return (message || "").toLowerCase().includes("blacklist");
}

function apiBase() {
  return (window.NEXT_PUBLIC_API_URL || 'https://tonbackend.onrender.com').replace(/\/$/, '');
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

// Intercept form submit and use JSON POST to backend auth endpoint
if (loginForm) {
  loginForm.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const formData = new FormData(loginForm);
    const email = String(formData.get('email') || '').trim();
    const password = String(formData.get('password') || '');

    if (!email || !password) {
      renderFlash('Email et mot de passe requis.', 'error');
      return;
    }

    try {
      const res = await fetch(apiBase() + '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });

      if (!res.ok) {
        const txt = await res.text().catch(() => 'Erreur de connexion');
        renderFlash(txt || `Erreur: ${res.status}`, 'error');
        return;
      }

      const data = await res.json().catch(() => ({}));
      // Redirect based on role or to client-space by default
      const role = (data.role || '').trim().toUpperCase();
      if (role === 'PROVIDER') {
        window.location.href = '/sarbi_rohek/provider-space.html';
        return;
      }

      window.location.href = '/sarbi_rohek/client-space.html';
    } catch (err) {
      renderFlash(String(err.message || err), 'error');
    }
  });
}

showMessageFromQuery();
fixLoginAction();

loadAuthStatus();
