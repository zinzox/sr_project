(async function redirectLoggedUsers() {
  try {
    const response = await fetch((window.NEXT_PUBLIC_API_URL || 'https://ton-backend.onrender.com').replace(/\/$/, '') + "/api/auth/status");
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
