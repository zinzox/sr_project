const statusEl = document.getElementById("adminStatus");
const bodyEl = document.getElementById("accountsBody");
const roleFilterEl = document.getElementById("filterRole");
const serviceTypeFilterEl = document.getElementById("filterServiceType");
const ageMinFilterEl = document.getElementById("filterAgeMin");
const ageMaxFilterEl = document.getElementById("filterAgeMax");
const params = new URLSearchParams(window.location.search);
const key = params.get("key") || "";
let allAccounts = [];

function setStatus(message) {
  if (statusEl) {
    statusEl.textContent = message;
  }
}

function esc(value) {
  return (value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function rowHtml(account) {
  const name = `${account.firstName || ""} ${account.lastName || ""}`.trim();
  return `
    <tr>
      <td>${esc(account.role)}</td>
      <td>${esc(name)}</td>
      <td>${esc(account.age || "-")}</td>
      <td>${esc(account.email)}</td>
      <td>${esc(account.phone)}</td>
      <td>${esc(account.serviceType || "-")}</td>
      <td>${esc(account.createdAt || "-")}</td>
      <td><button class="btn-delete" data-email="${esc(account.email)}">Supprimer</button></td>
    </tr>
  `;
}

function toInt(value) {
  const n = Number.parseInt(value, 10);
  return Number.isNaN(n) ? null : n;
}

function applyFilters() {
  const role = roleFilterEl ? roleFilterEl.value : "ALL";
  const serviceType = serviceTypeFilterEl ? serviceTypeFilterEl.value : "ALL";
  const ageMin = ageMinFilterEl ? toInt(ageMinFilterEl.value) : null;
  const ageMax = ageMaxFilterEl ? toInt(ageMaxFilterEl.value) : null;

  const filtered = allAccounts.filter((account) => {
    if (role !== "ALL" && account.role !== role) {
      return false;
    }

    if (serviceType !== "ALL") {
      if (account.role !== "PROVIDER") {
        return false;
      }

      if ((account.serviceType || "").toUpperCase() !== serviceType) {
        return false;
      }
    }

    const age = toInt(account.age);
    if (ageMin !== null && (age === null || age < ageMin)) {
      return false;
    }

    if (ageMax !== null && (age === null || age > ageMax)) {
      return false;
    }

    return true;
  });

  bodyEl.innerHTML = filtered.map(rowHtml).join("");
  setStatus(`${filtered.length} compte(s) affiche(s) / ${allAccounts.length} total.`);
}

async function loadAccounts() {
  if (!key) {
    setStatus("Acces refuse: cle admin manquante dans l'URL.");
    return;
  }

  setStatus("Chargement des comptes...");
  const response = await fetch(`/sarbi_rohek/api/admin/accounts?key=${encodeURIComponent(key)}`);
  if (!response.ok) {
    setStatus("Acces refuse ou erreur serveur.");
    return;
  }

  const data = await response.json();
  allAccounts = data;
  applyFilters();
}

async function deleteAccount(email) {
  const ok = window.confirm(`Supprimer definitivement le compte ${email} ?`);
  if (!ok) {
    return;
  }

  const response = await fetch(
    `/sarbi_rohek/api/admin/accounts?key=${encodeURIComponent(key)}&email=${encodeURIComponent(email)}`,
    { method: "DELETE" }
  );

  if (!response.ok) {
    setStatus("Suppression impossible.");
    return;
  }

  setStatus(`Compte ${email} supprime.`);
  await loadAccounts();
}

document.addEventListener("click", (event) => {
  const button = event.target.closest(".btn-delete");
  if (!button) {
    return;
  }

  const email = button.getAttribute("data-email");
  if (email) {
    deleteAccount(email);
  }
});

[roleFilterEl, serviceTypeFilterEl, ageMinFilterEl, ageMaxFilterEl]
  .filter(Boolean)
  .forEach((el) => {
    el.addEventListener("input", applyFilters);
    el.addEventListener("change", applyFilters);
  });

loadAccounts();
