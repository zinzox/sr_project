const roleSelect = document.getElementById("role");
const providerSection = document.getElementById("providerFields");
const flash = document.getElementById("flashMessage");
const registerForm = document.getElementById("registerForm");

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
    flash.hidden = false;
    flash.textContent = error;
    flash.classList.add("is-error");
    return;
  }

  if (success) {
    flash.hidden = false;
    flash.textContent = success;
    flash.classList.add("is-success");
  }
}

function fixRegisterAction() {
  if (!registerForm) {
    return;
  }

  registerForm.action = `${window.location.origin}/sarbi_rohek/api/register`;
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

loadAuthStatus();
