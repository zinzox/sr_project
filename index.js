const featuredGridEl = document.getElementById("featuredProvidersGrid");
const featuredHintEl = document.getElementById("featuredHint");
const navLoginBtnEl = document.getElementById("navLoginBtn");
const navRegisterBtnEl = document.getElementById("navRegisterBtn");
const navClientNameEl = document.getElementById("navClientName");
const navLogoutBtnEl = document.getElementById("navLogoutBtn");

let isLoggedIn = false;
let loggedRole = "";
let loggedEmail = "";
let loggedFirstName = "";
let loggedLastName = "";

function esc(value) {
  return (value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function apiBase() {
  return (window.NEXT_PUBLIC_API_URL || 'https://ton-backend.onrender.com').replace(/\/$/, '');
}

function apiPath(path) {
  if (!path) return apiBase();
  if (path.startsWith('/sarbi_rohek/api')) {
    return apiBase() + path.replace(/^\/sarbi_rohek/, '');
  }
  if (path.startsWith('/api')) {
    return apiBase() + path;
  }
  return path;
}

function buildReviews(provider) {
  const tag = (provider.serviceType || "PROVIDER").toUpperCase();
  if (tag === "VIDEO_MAKER") {
    return ["Avis: Montage rapide et creatif.", "Avis: Communication tres pro."];
  }

  if (tag === "FREELANCER") {
    return ["Avis: Travail propre et ponctuel.", "Avis: Bon rapport qualite/prix."];
  }

  return ["Avis: Service recommande par les clients.", "Avis: Prestataire serieux et disponible."];
}

function buildAvatarUrl(fullName) {
  const name = (fullName || "Prestataire").trim();
  return `https://ui-avatars.com/api/?name=${encodeURIComponent(name)}&background=2f80ed&color=ffffff&bold=true&size=160`;
}

function normalizePhoto(provider, fullName) {
  const raw = (provider.photoUrl || "").trim();
  if (!raw || raw.toLowerCase() === "logo" || raw.toLowerCase() === "logo.png") {
    return buildAvatarUrl(fullName);
  }

  return raw;
}

function providerCardHtml(provider) {
  const fullName = `${provider.firstName || ""} ${provider.lastName || ""}`.trim();
  const activity = provider.mainActivity || provider.serviceType || "Activite non precisee";
  const photo = normalizePhoto(provider, fullName);
  const fallback = buildAvatarUrl(fullName);
  const reviews = buildReviews(provider)
    .map((line) => `<div>${esc(line)}</div>`)
    .join("");

  const loginButtonHtml = isLoggedIn ? "" : '<a class="btn primary provider-login" href="login.html">Se connecter</a>';

  return `
    <article class="provider-card" data-provider-card>
      <div class="provider-top">
        <img class="provider-photo" src="${esc(photo)}" alt="${esc(fullName)}" loading="lazy" onerror="this.onerror=null;this.src='${esc(fallback)}';" />
        <div>
          <div class="provider-name">${esc(fullName || "Prestataire")}</div>
          <div class="provider-activity">${esc(activity)}</div>
        </div>
      </div>
      <div class="provider-extra">
        <p class="provider-desc">${esc(provider.workDescription || "Description a venir.")}</p>
        <div class="provider-reviews">${reviews}</div>
        ${loginButtonHtml}
      </div>
    </article>
  `;
}

function bindCardAnimation() {
  document.querySelectorAll("[data-provider-card]").forEach((card) => {
    card.addEventListener("click", () => {
      if (!isLoggedIn) {
        if (featuredHintEl) {
          featuredHintEl.textContent = "Connectez-vous d'abord pour ouvrir les fiches prestataires.";
        }
        return;
      }

      const alreadyExpanded = card.classList.contains("expanded");
      document.querySelectorAll("[data-provider-card]").forEach((node) => node.classList.remove("expanded"));
      if (!alreadyExpanded) {
        card.classList.add("expanded");
      }
    });
  });
}

function updateNavAuth() {
  if (!navLoginBtnEl || !navRegisterBtnEl || !navClientNameEl || !navLogoutBtnEl) {
    return;
  }

  const hideAllAuthButtons = isLoggedIn;
  const fullName = `${loggedFirstName} ${loggedLastName}`.trim();

  navLoginBtnEl.hidden = hideAllAuthButtons;
  navRegisterBtnEl.hidden = hideAllAuthButtons;
  navClientNameEl.hidden = !hideAllAuthButtons;
  navLogoutBtnEl.hidden = !hideAllAuthButtons;
  navClientNameEl.textContent = hideAllAuthButtons ? (fullName ? `Bonjour, ${fullName}` : "Bonjour") : "";

  // Force-hide top-right auth buttons to avoid CSS/cache inconsistencies.
  if (hideAllAuthButtons) {
    navLoginBtnEl.style.setProperty("display", "none", "important");
    navRegisterBtnEl.style.setProperty("display", "none", "important");
    navClientNameEl.style.removeProperty("display");
    navLogoutBtnEl.style.removeProperty("display");
  } else {
    navLoginBtnEl.style.removeProperty("display");
    navRegisterBtnEl.style.removeProperty("display");
    navClientNameEl.style.setProperty("display", "none", "important");
    navLogoutBtnEl.style.setProperty("display", "none", "important");
  }
}

async function fetchNameFromDatabase() {
  try {
    const response = await fetch(apiPath("/sarbi_rohek/api/auth/profile"));
    if (!response.ok) {
      return;
    }

    const data = await response.json();
    loggedFirstName = (data.firstName || "").trim();
    loggedLastName = (data.lastName || "").trim();
    loggedEmail = (data.email || loggedEmail || "").trim();
    loggedRole = (data.role || loggedRole || "").trim();
  } catch (error) {
    // Keep session-derived values if profile fetch fails.
  }
}

async function logoutClient() {
  try {
    await fetch(apiPath("/sarbi_rohek/api/auth/logout"), { method: "POST" });
  } finally {
    window.location.href = "/sarbi_rohek/login.html";
  }
}

async function loadAuthStatus() {
  try {
    const response = await fetch(apiPath("/sarbi_rohek/api/auth/status"));
    if (!response.ok) {
      window.location.href = "/sarbi_rohek/login.html";
      return;
    }

    const data = await response.json();
    isLoggedIn = Boolean(data.loggedIn);
    loggedRole = (data.role || "").trim();
    loggedEmail = (data.email || "").trim();
    loggedFirstName = (data.firstName || "").trim();
    loggedLastName = (data.lastName || "").trim();
    if (!isLoggedIn) {
      window.location.href = "/sarbi_rohek/login.html";
      return;
    }

    if (loggedRole === "PROVIDER") {
      window.location.href = "/sarbi_rohek/provider-space.html";
      return;
    }

    if (!loggedFirstName && !loggedLastName) {
      await fetchNameFromDatabase();
    }

    updateNavAuth();

    if (featuredHintEl) {
      featuredHintEl.textContent = isLoggedIn
        ? "Vous etes connecte: cliquez sur un prestataire pour voir les details."
        : "Connectez-vous pour ouvrir les fiches en detail.";
    }
  } catch (error) {
    isLoggedIn = false;
    loggedRole = "";
    loggedEmail = "";
    loggedFirstName = "";
    loggedLastName = "";
    updateNavAuth();
    window.location.href = "/sarbi_rohek/login.html";
  }
}

async function loadFeaturedProviders() {
  if (!featuredGridEl) {
    return;
  }

  try {
    const response = await fetch("/sarbi_rohek/api/providers/featured");
    if (!response.ok) {
      featuredGridEl.innerHTML = "<p>Impossible de charger les prestataires.</p>";
      return;
    }

    const providers = await response.json();
    if (!Array.isArray(providers) || providers.length === 0) {
      featuredGridEl.innerHTML = "<p>Aucun prestataire inscrit pour le moment.</p>";
      return;
    }

    if (providers.length === 1) {
      featuredGridEl.classList.add("one-item");
    } else {
      featuredGridEl.classList.remove("one-item");
    }

    featuredGridEl.innerHTML = providers.map(providerCardHtml).join("");
    bindCardAnimation();
  } catch (error) {
    featuredGridEl.innerHTML = "<p>Erreur reseau pendant le chargement.</p>";
  }
}

async function initFeaturedProviders() {
  if (navLogoutBtnEl) {
    navLogoutBtnEl.addEventListener("click", logoutClient);
  }

  await loadAuthStatus();
  await loadFeaturedProviders();
}

initFeaturedProviders();
