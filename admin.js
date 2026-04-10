const statusEl = document.getElementById("adminStatus");
const bodyEl = document.getElementById("accountsBody");
const alertsBodyEl = document.getElementById("alertsBody");
const blacklistBodyEl = document.getElementById("blacklistBody");
const auditBodyEl = document.getElementById("auditBody");
const roleFilterEl = document.getElementById("filterRole");
const serviceTypeFilterEl = document.getElementById("filterServiceType");
const ageMinFilterEl = document.getElementById("filterAgeMin");
const ageMaxFilterEl = document.getElementById("filterAgeMax");
const alertStatusFilterEl = document.getElementById("filterAlertStatus");
const alertSeverityFilterEl = document.getElementById("filterAlertSeverity");
const alertSenderFilterEl = document.getElementById("filterAlertSender");
const applyModerationFiltersBtn = document.getElementById("applyModerationFilters");
const kpiOpenAlertsEl = document.getElementById("kpiOpenAlerts");
const kpiHighTodayEl = document.getElementById("kpiHighToday");
const kpiTempBansEl = document.getElementById("kpiTempBans");
const params = new URLSearchParams(window.location.search);
const key = params.get("key") || "";
let allAccounts = [];
let allAlerts = [];
let allBlacklist = [];
let allAuditLogs = [];

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

function alertRowHtml(alert) {
  const senderEmail = alert.senderEmail || "";
  const senderPhone = alert.senderPhone || "";
  return `
    <tr>
      <td>${esc(senderEmail)}</td>
      <td>${esc(alert.recipientEmail || "-")}</td>
      <td>${esc(alert.severity || "LOW")}</td>
      <td>${esc(String(alert.riskScore ?? 0))}</td>
      <td>${esc(alert.categories || "-")}</td>
      <td>${esc(alert.messageText || "-")}</td>
      <td>${esc(alert.reason || "-")}</td>
      <td>${esc(alert.createdAt || "-")}</td>
      <td>
        <button class="btn-mod btn-blacklist" data-action="blacklist" data-alert-id="${esc(String(alert.id || ""))}" data-email="${esc(senderEmail)}" data-phone="${esc(senderPhone)}">Blacklist</button>
        <button class="btn-mod btn-ban" data-action="tempban" data-alert-id="${esc(String(alert.id || ""))}" data-email="${esc(senderEmail)}" data-phone="${esc(senderPhone)}">Ban 5 min</button>
        <button class="btn-mod btn-warn" data-action="warn" data-alert-id="${esc(String(alert.id || ""))}" data-email="${esc(senderEmail)}" data-phone="${esc(senderPhone)}" data-message="${esc(alert.messageText || "")}" data-reason="${esc(alert.reason || "")}">Avertir</button>
        <button class="btn-mod btn-resolve" data-action="resolve" data-alert-id="${esc(String(alert.id || ""))}">Resolu</button>
      </td>
    </tr>
  `;
}

function blacklistRowHtml(item) {
  return `
    <tr>
      <td>${esc(item.email || "-")}</td>
      <td>${esc(item.phone || "-")}</td>
      <td>${esc(item.reason || "-")}</td>
      <td>${esc(item.startsAt || "-")}</td>
      <td><button class="btn-mod btn-unblacklist" data-action="unblacklist" data-email="${esc(item.email || "")}">Retirer</button></td>
    </tr>
  `;
}

function renderModeration() {
  if (alertsBodyEl) {
    alertsBodyEl.innerHTML = allAlerts.map(alertRowHtml).join("");
  }

  if (blacklistBodyEl) {
    blacklistBodyEl.innerHTML = allBlacklist.map(blacklistRowHtml).join("");
  }

  if (auditBodyEl) {
    auditBodyEl.innerHTML = allAuditLogs.map((log) => `
      <tr>
        <td>${esc(log.createdAt || "-")}</td>
        <td>${esc(log.action || "-")}</td>
        <td>${esc(log.targetEmail || "-")}</td>
        <td>${esc(log.targetPhone || "-")}</td>
        <td>${esc(log.reason || "-")}</td>
        <td>${esc(log.adminKey || "-")}</td>
      </tr>
    `).join("");
  }
}

function renderMetrics(metrics) {
  if (kpiOpenAlertsEl) {
    kpiOpenAlertsEl.textContent = String(metrics?.openAlerts ?? 0);
  }
  if (kpiHighTodayEl) {
    kpiHighTodayEl.textContent = String(metrics?.highAlertsToday ?? 0);
  }
  if (kpiTempBansEl) {
    kpiTempBansEl.textContent = String(metrics?.activeTempBans ?? 0);
  }
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
  setStatus(`${filtered.length} compte(s) affiche(s) / ${allAccounts.length} total. ${allAlerts.length} alerte(s) IA.`);
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

  if (Array.isArray(data)) {
    allAccounts = data;
  } else {
    allAccounts = Array.isArray(data.accounts) ? data.accounts : [];
  }

  applyFilters();
  await loadModeration();
}

async function loadModeration() {
  if (!key) {
    return;
  }

  const query = new URLSearchParams();
  query.set("key", key);
  query.set("status", alertStatusFilterEl ? alertStatusFilterEl.value : "OPEN");
  query.set("severity", alertSeverityFilterEl ? alertSeverityFilterEl.value : "ALL");
  if (alertSenderFilterEl && alertSenderFilterEl.value.trim()) {
    query.set("sender", alertSenderFilterEl.value.trim());
  }

  const response = await fetch(`/sarbi_rohek/api/admin/moderation?${query.toString()}`);
  if (!response.ok) {
    return;
  }

  const data = await response.json().catch(() => ({}));
  allAlerts = Array.isArray(data.alerts) ? data.alerts : [];
  allBlacklist = Array.isArray(data.blacklist) ? data.blacklist : [];
  allAuditLogs = Array.isArray(data.logs) ? data.logs : [];
  renderMetrics(data.metrics || {});
  renderModeration();
}

async function doModerationAction(action, payload) {
  const body = new URLSearchParams();
  body.set("key", key);
  body.set("action", action);

  Object.entries(payload || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null) {
      body.set(k, String(v));
    }
  });

  const response = await fetch("/sarbi_rohek/api/admin/moderation", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: body.toString()
  });

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    setStatus(data.message || "Action moderation impossible.");
    return;
  }

  setStatus(data.message || "Action moderation executee.");
  await loadModeration();
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
  const modButton = event.target.closest("[data-action]");
  if (modButton && modButton.classList.contains("btn-mod")) {
    const action = modButton.getAttribute("data-action") || "";
    const alertId = modButton.getAttribute("data-alert-id") || "";
    const email = modButton.getAttribute("data-email") || "";
    const phone = modButton.getAttribute("data-phone") || "";
    const messageText = modButton.getAttribute("data-message") || "";
    const reason = modButton.getAttribute("data-reason") || "";

    doModerationAction(action, {
      alertId,
      email,
      phone,
      messageText,
      reason
    });
    return;
  }

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

[alertStatusFilterEl, alertSeverityFilterEl]
  .filter(Boolean)
  .forEach((el) => {
    el.addEventListener("change", () => {
      loadModeration();
    });
  });

if (alertSenderFilterEl) {
  alertSenderFilterEl.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      loadModeration();
    }
  });
}

if (applyModerationFiltersBtn) {
  applyModerationFiltersBtn.addEventListener("click", () => {
    loadModeration();
  });
}

loadAccounts();
