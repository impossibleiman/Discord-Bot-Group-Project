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
    if (!guildId) return;

    const response = await fetch(`${API_BASE}/config/${guildId}`, {
        headers: { 'Authorization': localStorage.getItem('admin_session') }
    });
    const config = await response.json();
    
    document.getElementById('config-nickname').value = config.nickname || "";
    document.getElementById('config-welcome').value = config.welcomeMessage || "";
    document.getElementById('settings-form').style.display = "block";

    // Build the alias table
    renderAliasTable(config.inviteAliases || {});
}

// Add/Replace this in dashboard.js

async function createMagicInvite() {
    const guildId = document.getElementById('guild-selector').value;
    const alias = document.getElementById('new-magic-alias').value.trim();

    if (!guildId) return alert("Please select a server first!");
    if (!alias) return alert("Please type an alias name (e.g. Instagram)!");

    log(`Requesting magic invite for alias: '${alias}'...`);
    const token = localStorage.getItem('admin_session');

    try {
        // Send the alias as a query parameter (?alias=Instagram)
        const response = await fetch(`${API_BASE}/create-magic-invite/${guildId}?alias=${encodeURIComponent(alias)}`, {
            headers: { 'Authorization': token }
        });

        if (response.ok) {
            const newCode = await response.text();
            log(`✨ Success! Created and mapped: ${alias} -> ${newCode}`);
            
            // Clear input box
            document.getElementById('new-magic-alias').value = "";
            
            // Reload settings immediately to show the new entry in the table
            loadServerConfig(); 
            
            alert(`Magic Invite Created!\n\n${alias} -> discord.gg/${newCode}`);
        } else {
            const error = await response.text();
            alert("Error: " + error);
            log("Server rejected invite creation: " + error);
        }
    } catch (err) {
        log("Failed to communicate with bot for magic invite.");
        console.error(err);
    }
}

// Update your existing renderAliasTable function to show links
function renderAliasTable(aliases) {
    const tbody = document.getElementById('alias-list');
    tbody.innerHTML = "";
    
    // Sort aliases alphabetically by name
    const sortedEntries = Object.entries(aliases).sort((a, b) => a.localeCompare(b));

    for (const [code, alias] of sortedEntries) {
        tbody.innerHTML += `
            <tr style="border-bottom: 1px solid #333;">
                <td style="padding: 10px;"><b>${alias}</b></td>
                <td style="padding: 10px;"><code>discord.gg/${code}</code></td>
                <td style="padding: 10px;">
                    <button onclick="deleteAlias('${code}')" style="background:#e74c3c; padding:5px 10px; border-radius:4px; border:none; color:white; font-weight:bold; cursor:pointer;">
                        Delete
                    </button>
                </td>
            </tr>`;
    }
    
    if (Object.keys(aliases).length === 0) {
        tbody.innerHTML = '<tr><td colspan="3" style="text-align:center; padding: 20px; color: #aaa;">No magic invites created yet.</td></tr>';
    }
}

async function addInviteAlias() {
    const guildId = document.getElementById('guild-selector').value;
    const code = document.getElementById('new-invite-code').value.trim();
    const alias = document.getElementById('new-invite-alias').value.trim();

    if (!code || !alias) return alert("Please enter both a code and an alias.");

    const token = localStorage.getItem('admin_session');
    
    // 1. Fetch current config
    const response = await fetch(`${API_BASE}/config/${guildId}`, {
        headers: { 'Authorization': token }
    });
    const config = await response.json();
    
    // 2. SAFETY CHECK: Ensure the map exists before we try to add to it
    if (!config.inviteAliases) {
        config.inviteAliases = {};
    }

    // 3. Add the new alias
    config.inviteAliases[code] = alias;

    // 4. Save the full updated object
    await saveFullConfig(guildId, config);
    
    // 5. Reset and refresh
    document.getElementById('new-invite-code').value = "";
    document.getElementById('new-invite-alias').value = "";
    loadServerConfig(); 
}

async function deleteAlias(code) {
    const guildId = document.getElementById('guild-selector').value;
    const response = await fetch(`${API_BASE}/config/${guildId}`, {
        headers: { 'Authorization': localStorage.getItem('admin_session') }
    });
    const config = await response.json();
    
    delete config.inviteAliases[code];
    await saveFullConfig(guildId, config);
    loadServerConfig();
}

// Helper to save the whole config object
async function saveFullConfig(guildId, config) {
    await fetch(`${API_BASE}/config/${guildId}`, {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            'Authorization': localStorage.getItem('admin_session') 
        },
        body: JSON.stringify(config)
    });
}

// 5. Save settings back to the bot
async function saveServerConfig() {
    const guildId = document.getElementById('guild-selector').value;
    const token = localStorage.getItem('admin_session');
    
    // 1. Fetch the CURRENT full config first (to keep the aliases safe!)
    const getRes = await fetch(`${API_BASE}/config/${guildId}`, {
        headers: { 'Authorization': token }
    });
    const config = await getRes.json();

    // 2. Only update the fields from the text boxes
    config.nickname = document.getElementById('config-nickname').value;
    config.welcomeMessage = document.getElementById('config-welcome').value;

    // 3. Send the WHOLE object (aliases included) back to the bot
    const postRes = await fetch(`${API_BASE}/config/${guildId}`, {
        method: 'POST',
        headers: { 
            'Content-Type': 'application/json',
            'Authorization': token 
        },
        body: JSON.stringify(config)
    });

    if (postRes.ok) {
        alert("Everything saved!");
        loadServerConfig(); // Refresh the UI
    }
}

async function generateBotInvite() {
    const guildId = document.getElementById('guild-selector').value;
    if (!guildId) return alert("Please select a server first!");

    log("Requesting new permanent invite from bot...");
    const token = localStorage.getItem('admin_session');

    try {
        const response = await fetch(`${API_BASE}/create-invite/${guildId}`, {
            headers: { 'Authorization': token }
        });

        if (response.ok) {
            const newCode = await response.text();
            // Automatically fill the code box for the user
            document.getElementById('new-invite-code').value = newCode;
            log("Invite created: " + newCode);
        } else {
            const error = await response.text();
            alert("Error: " + error);
        }
    } catch (err) {
        log("Failed to communicate with bot for invite creation.");
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