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
        
        if (response.ok) {
            const text = await response.text();
            document.getElementById('session-info').innerHTML = "✅ " + text;
            log("Session verified with server.");
            
            // Trigger data loading now that we are authenticated
            loadGuilds(); 
            checkBotStatus();
        } else {
            document.getElementById('session-info').innerHTML = "⚠️ Session expired. Please login again.";
            localStorage.removeItem('admin_session');
            log("Session expired or invalid.");
        }
    } catch (err) {
        log("Error: Could not reach API server.");
    }
}

// 3. Fetch servers where the bot is present
async function loadGuilds() {
    const token = localStorage.getItem('admin_session');
    log("Fetching manageable servers...");

    try {
        const response = await fetch(`${API_BASE}/my-guilds`, {
            headers: { 'Authorization': token }
        });

        if (!response.ok) {
            log("Failed to load servers (Status: " + response.status + ")");
            return;
        }

        const guilds = await response.json();
        const selector = document.getElementById('guild-selector');
        
        // Clear existing options except the placeholder
        selector.innerHTML = '<option value="">-- Choose a Server --</option>';

        if (guilds.length === 0) {
            log("No servers found where the bot is present.");
            return;
        }

        guilds.forEach(g => {
            let opt = document.createElement('option');
            opt.value = g.id;
            opt.innerHTML = g.name;
            selector.appendChild(opt);
        });
        
        log("Loaded " + guilds.length + " servers.");
    } catch (err) {
        log("Error fetching server list.");
        console.error(err);
    }
}

// 4. Load specific server settings
async function loadServerConfig() {
    const guildId = document.getElementById('guild-selector').value;
    if (!guildId) {
        document.getElementById('settings-form').style.display = "none";
        return;
    }

    log("Loading settings for guild: " + guildId);
    const token = localStorage.getItem('admin_session');

    try {
        const response = await fetch(`${API_BASE}/config/${guildId}`, {
            headers: { 'Authorization': token }
        });
        const config = await response.json();
        
        document.getElementById('config-nickname').value = config.nickname || "";
        document.getElementById('config-welcome').value = config.welcomeMessage || "";
        document.getElementById('settings-form').style.display = "block";
    } catch (err) {
        log("Error loading server config.");
    }
}

// 5. Save settings back to the bot
async function saveServerConfig() {
    const guildId = document.getElementById('guild-selector').value;
    const token = localStorage.getItem('admin_session');
    
    const data = {
        nickname: document.getElementById('config-nickname').value,
        welcomeMessage: document.getElementById('config-welcome').value
    };

    try {
        const response = await fetch(`${API_BASE}/config/${guildId}`, {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'Authorization': token 
            },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            alert("Settings saved successfully!");
            log("Config updated for " + guildId);
        } else {
            alert("Failed to save settings.");
        }
    } catch (err) {
        log("Error saving config.");
    }
}

// 6. Check Bot Health
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
    if (out) {
        out.innerHTML += `<br>> ${msg}`;
        out.scrollTop = out.scrollHeight;
    }
    console.log(msg);
}