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

// Add this to your verifySession() success block
async function loadGuilds() {
    // This is a simple way to get guilds where the bot and user are both present
    // For now, we'll fetch from a new endpoint we'll create below
    const response = await fetch(`${API_BASE}/my-guilds`, {
        headers: { 'Authorization': localStorage.getItem('admin_session') }
    });
    const guilds = await response.json();
    const selector = document.getElementById('guild-selector');
    guilds.forEach(g => {
        let opt = document.createElement('option');
        opt.value = g.id;
        opt.innerHTML = g.name;
        selector.appendChild(opt);
    });
}

async function loadServerConfig() {
    const guildId = document.getElementById('guild-selector').value;
    if (!guildId) return;

    const response = await fetch(`${API_BASE}/config/${guildId}`, {
        headers: { 'Authorization': localStorage.getItem('admin_session') }
    });
    const config = await response.json();
    
    document.getElementById('config-nickname').value = config.nickname || "";
    document.getElementById('config-welcome').value = config.welcomeMessage || "";
    document.getElementById('settings-form').style.display = "block";
}

async function saveServerConfig() {
    const guildId = document.getElementById('guild-selector').value;
    const data = {
        nickname: document.getElementById('config-nickname').value,
        welcomeMessage: document.getElementById('config-welcome').value
    };

    const response = await fetch(`${API_BASE}/config/${guildId}`, {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('admin_session') 
        },
        body: JSON.stringify(data)
    });

    if (response.ok) alert("Settings saved successfully!");
}