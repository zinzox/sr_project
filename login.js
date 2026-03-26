const flash = document.getElementById("flashMessage");
const loginForm = document.getElementById("loginForm");
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

function fixLoginAction() {
  if (!loginForm) {
    return;
  }

  loginForm.action = `${window.location.origin}/sarbi_rohek/api/login`;
}

showMessageFromQuery();
fixLoginAction();

loadAuthStatus();
