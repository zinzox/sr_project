const roleSelect = document.getElementById("role");
const providerSection = document.getElementById("providerFields");
const flash = document.getElementById("flashMessage");
const registerForm = document.getElementById("registerForm");
const verificationSection = document.getElementById("verificationSection");
const verificationCodeInput = document.getElementById("verificationCode");
const registerSubmitBtn = document.getElementById("registerSubmitBtn");
const ADMIN_CONTACT_EMAIL = "roheksarbi@gmail.com";
let verificationSent = false;
let submitInProgress = false;

const providerFields = [
  "cin",
  "serviceType",
  "mainActivity",
  "workTitle",
  "workDescription"
];

function toggleProviderFields() {
  const isProvider = roleSelect && roleSelect.value === "PROVIDER";
  if (!providerSection) {
    return;
  }

  providerSection.hidden = !isProvider;

  providerFields.forEach((fieldName) => {
    const input = document.querySelector(`[name="${fieldName}"]`);
    if (input) {
      input.required = isProvider;
    }
  });
}

function showMessageFromQuery() {
  const params = new URLSearchParams(window.location.search);
  const error = params.get("error");
  const success = params.get("success");

  if (!flash) {
    return;
  }

  if (error) {
    showFlash(error, true);
    return;
  }

  if (success) {
    showFlash(success, false);
  }
}

function containsBlacklistMessage(message) {
  return (message || "").toLowerCase().includes("blacklist");
}

function showFlash(message, isError) {
  if (!flash) {
    return;
  }

  flash.hidden = false;
  flash.textContent = message || "";
  flash.classList.remove("is-error", "is-success");
  flash.classList.add(isError ? "is-error" : "is-success");

  if (isError && containsBlacklistMessage(message)) {
    const lineBreak = document.createElement("br");
    const link = document.createElement("a");
    link.href = `mailto:${ADMIN_CONTACT_EMAIL}`;
    link.textContent = `Contacter l'administrateur: ${ADMIN_CONTACT_EMAIL}`;
    link.className = "flash-contact-link";
    flash.appendChild(lineBreak);
    flash.appendChild(link);
  }
}

function fixRegisterAction() {
  if (!registerForm) {
    return;
  }

  registerForm.action = `${window.location.origin}/sarbi_rohek/api/register`;
}

function setVerificationMode(enabled) {
  verificationSent = enabled;

  if (verificationSection) {
    verificationSection.hidden = !enabled;
  }

  if (verificationCodeInput) {
    verificationCodeInput.required = enabled;
  }

  if (registerSubmitBtn) {
    registerSubmitBtn.textContent = enabled ? "Verifier le code" : "Envoyer le code";
  }
}

async function submitRegister(event) {
  event.preventDefault();
  if (!registerForm) {
    return;
  }

  if (submitInProgress) {
    return;
  }

  submitInProgress = true;
  if (registerSubmitBtn) {
    registerSubmitBtn.disabled = true;
  }

  const formData = new FormData(registerForm);
  if (!verificationSent) {
    formData.set("step", "send-code");
    formData.delete("verificationCode");
  } else {
    formData.set("step", "verify-code");
  }

  let response;
  try {
    response = await fetch(registerForm.action, {
      method: "POST",
      body: new URLSearchParams(formData)
    });
  } catch (error) {
    showFlash("Erreur reseau. Reessayez.", true);
    submitInProgress = false;
    if (registerSubmitBtn) {
      registerSubmitBtn.disabled = false;
    }
    return;
  }

  let payload = {};
  try {
    payload = await response.json();
  } catch (error) {
    payload = {};
  }

  if (!response.ok) {
    showFlash(payload.message || "Erreur pendant l'inscription.", true);
    submitInProgress = false;
    if (registerSubmitBtn) {
      registerSubmitBtn.disabled = false;
    }
    return;
  }

  if (!verificationSent) {
    setVerificationMode(true);
    showFlash(payload.message || "Code envoye.", false);
    if (verificationCodeInput) {
      verificationCodeInput.focus();
    }
    submitInProgress = false;
    if (registerSubmitBtn) {
      registerSubmitBtn.disabled = false;
    }
    return;
  }

  if (payload.redirect) {
    window.location.href = payload.redirect;
    return;
  }

  showFlash("Code valide. Redirection...", false);
  submitInProgress = false;
  if (registerSubmitBtn) {
    registerSubmitBtn.disabled = false;
  }
}

async function loadAuthStatus() {
  try {
    const response = await fetch("/sarbi_rohek/api/auth/status");
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
    // No active session, stay on register page.
  }
}

if (roleSelect) {
  roleSelect.addEventListener("change", toggleProviderFields);
}

toggleProviderFields();
showMessageFromQuery();
fixRegisterAction();
setVerificationMode(false);

if (registerForm) {
  registerForm.addEventListener("submit", submitRegister);
}

loadAuthStatus();
