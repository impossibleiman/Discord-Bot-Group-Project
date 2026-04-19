const API_BASE = "https://api.mmuminecraftsociety.co.uk";

// 1. Capture Session on Load
document.addEventListener("DOMContentLoaded", () => {
    const urlParams = new URLSearchParams(window.location.search);
    const sessionToken = urlParams.get('session');

    if (sessionToken) {
        // Store it securely in the browser
        localStorage.setItem('admin_session', sessionToken);
        log("New session saved!");
        
        // Clean the URL (remove the ?session=xxx part)
        window.history.replaceState({}, document.title, "/dashboard.html");
    }

    verifySession();
});

// 2. Verify Session with the Backend
async function verifySession() {
    const token = localStorage.getItem('admin_session');
    if (!token) {
        document.getElementById('session-info').innerHTML = "❌ No active session. <a href='/login.html' style='color:white;'>Login here</a>";
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/check-session?session=${token}`);
        const text = await response.text();
        
        if (response.ok) {
            document.getElementById('session-info').innerHTML = "✅ Authenticated: " + text;
            log("Session verified with server.");
            checkBotStatus();
        } else {
            document.getElementById('session-info').innerHTML = "⚠️ Session expired. Please login again.";
            localStorage.removeItem('admin_session');
        }
    } catch (err) {
        log("Error: Could not reach API server.");
    }
}

// 3. Example Action: Check Bot Health
async function checkBotStatus() {
    try {
        const response = await fetch(`${API_BASE}/status`);
        const statusEl = document.getElementById('bot-status');
        
        if (response.ok) {
            statusEl.innerText = "Online";
            statusEl.className = "status-badge online";
            log("Bot heartbeat detected.");
        } else {
            statusEl.innerText = "Offline";
            statusEl.className = "status-badge offline";
        }
    } catch (err) {
        log("Failed to fetch bot status.");
    }
}

function log(msg) {
    const out = document.getElementById('log-output');
    out.innerHTML += `<br>> ${msg}`;
    out.scrollTop = out.scrollHeight;
}