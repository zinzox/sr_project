(async function redirectLoggedUsers() {
  try {
    const response = await fetch("/sarbi_rohek/api/auth/status");
    if (!response.ok) {
      return;
    }

    const data = await response.json();
    if (!data || !data.loggedIn) {
      return;
    }

    const role = (data.role || "").trim();
    if (role === "PROVIDER") {
      window.location.href = "/sarbi_rohek/provider-space.html";
      return;
    }

    window.location.href = "/sarbi_rohek/client-space.html";
  } catch (error) {
    // Keep homepage visible if status check fails.
  }
})();
