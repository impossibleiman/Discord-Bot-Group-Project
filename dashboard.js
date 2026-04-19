const API_BASE = "https://api.mmuminecraftsociety.co.uk";
let currentGuildId = null;

document.addEventListener("DOMContentLoaded", () => {
    const urlParams = new URLSearchParams(window.location.search);
    const sessionToken = urlParams.get('session');

    if (sessionToken) {
        localStorage.setItem('admin_session', sessionToken);
        window.history.replaceState({}, document.title, "/dashboard.html");
    }
    verifySession();
});

// Toast System (Replaces Alerts)
function showToast(message, type = 'success') {
    const container = document.getElementById('toast-container');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    // Simple icon check based on type
    const icon = type === 'success' ? '✓' : '✕';
    toast.innerHTML = `<span style="font-size: 16px;">${icon}</span> ${message}`;
    
    container.appendChild(toast);
    setTimeout(() => {
        toast.classList.add('fade-out');
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

// Tab Switching Logic
function switchTab(tabId, buttonElement) {
    document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.nav-tab').forEach(btn => btn.classList.remove('active'));
    
    document.getElementById(tabId).classList.add('active');
    buttonElement.classList.add('active');
}

async function verifySession() {
    const token = localStorage.getItem('admin_session');
    if (!token) {
        document.getElementById('session-info').innerHTML = "No active session.";
        return;
    }
    try {
        const response = await fetch(`${API_BASE}/check-session?session=${token}`);
        if (response.ok) {
            document.getElementById('session-info').innerHTML = "Session Valid";
            loadGuilds(); 
            checkBotStatus();
        } else {
            document.getElementById('session-info').innerHTML = "Session Expired";
            localStorage.removeItem('admin_session');
            window.location.href = '/login.html';
        }
    } catch (err) {
        showToast("Could not connect to API.", "error");
    }
}

async function loadGuilds() {
    const token = localStorage.getItem('admin_session');
    try {
        const response = await fetch(`${API_BASE}/my-guilds`, { headers: { 'Authorization': token } });
        if (!response.ok) return showToast("Failed to load servers.", "error");

        const guilds = await response.json();
        const serverBar = document.getElementById('server-bar');
        serverBar.innerHTML = '';

        if (guilds.length === 0) {
            serverBar.innerHTML = '<span style="color:var(--muted); font-size:14px;">No managed servers found.</span>';
            return;
        }

        guilds.forEach((g, index) => {
            const btn = document.createElement('button');
            btn.className = 'server-pill';
            btn.innerText = g.name;
            btn.onclick = () => selectServer(g.id, btn);
            serverBar.appendChild(btn);

            // Auto-select the first server
            if (index === 0) {
                selectServer(g.id, btn);
            }
        });
    } catch (err) {
        showToast("Error fetching server list.", "error");
    }
}

function selectServer(guildId, buttonElement) {
    currentGuildId = guildId;
    
    // Update active pill styling
    document.querySelectorAll('.server-pill').forEach(btn => btn.classList.remove('active'));
    buttonElement.classList.add('active');

    // Show settings container
    document.getElementById('settings-container').style.display = "block";
    loadServerConfig();
}

async function loadServerConfig() {
    if (!currentGuildId) return;
    const response = await fetch(`${API_BASE}/config/${currentGuildId}`, {
        headers: { 'Authorization': localStorage.getItem('admin_session') }
    });
    const config = await response.json();
    
    // RESTORED: Load the Bot Nickname
    const nickEl = document.getElementById('config-nickname');
    if (nickEl) nickEl.value = config.nickname || "";

    let embedData = {};
    try { embedData = JSON.parse(config.welcomeMessage || "{}"); } 
    catch (e) { embedData = { desc: config.welcomeMessage || "" }; }

    document.getElementById('emb-title').value = embedData.title || "";
    document.getElementById('emb-desc').value = embedData.desc || "";
    document.getElementById('emb-color').value = embedData.color || "";
    document.getElementById('emb-thumb').value = embedData.thumb || "";
    document.getElementById('emb-image').value = embedData.image || "";
    document.getElementById('emb-footer').value = embedData.footer || "";

    renderAliasTable(config.inviteAliases || {});
    updatePreview(); 
}

async function saveServerConfig() {
    if (!currentGuildId) return;
    const token = localStorage.getItem('admin_session');
    const btn = document.getElementById('save-general-btn');
    if (btn) { btn.innerText = "Saving..."; btn.disabled = true; }
    
    try {
        const embedData = {
            title: document.getElementById('emb-title').value,
            desc: document.getElementById('emb-desc').value,
            color: document.getElementById('emb-color').value,
            thumb: document.getElementById('emb-thumb').value,
            image: document.getElementById('emb-image').value,
            footer: document.getElementById('emb-footer').value
        };

        // RESTORED: Send the nickname to the Java Backend!
        const payload = { 
            nickname: document.getElementById('config-nickname').value,
            welcomeMessage: JSON.stringify(embedData) 
        };

        const postRes = await fetch(`${API_BASE}/config/${currentGuildId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': token },
            body: JSON.stringify(payload)
        });

        if (postRes.ok) showToast("Embed settings saved successfully!");
        else showToast("Failed to save settings.", "error");
    } catch (err) {
        showToast("Network error.", "error");
    } finally {
        if (btn) { btn.innerText = "Save Identity Settings"; btn.disabled = false; }
    }
}

async function checkBotStatus() {
    const statusEl = document.getElementById('bot-status');
    try {
        const response = await fetch(`${API_BASE}/status`);
        if (response.ok) {
            statusEl.innerText = "● Bot Online";
            statusEl.style.color = "var(--green)";
            statusEl.style.background = "rgba(74,222,128,.1)";
            statusEl.style.borderColor = "rgba(74,222,128,.25)";
        } else {
            throw new Error("Bad status");
        }
    } catch (err) {
        statusEl.innerText = "● Bot Offline";
        statusEl.style.color = "#e74c3c";
        statusEl.style.background = "rgba(231,76,60,.1)";
        statusEl.style.borderColor = "rgba(231,76,60,.25)";
    }
}

async function createMagicInvite() {
    const aliasInput = document.getElementById('new-magic-alias');
    const alias = aliasInput.value.trim();
    const btn = document.getElementById('generate-magic-btn');

    if (!currentGuildId) return showToast("Please select a server first.", "error");
    if (!alias) return showToast("Please type an alias name.", "error");

    const token = localStorage.getItem('admin_session');
    
    // Visual Loading State (Prevents empty console logs)
    btn.innerText = "Generating...";
    btn.disabled = true;
    aliasInput.disabled = true;

    try {
        const response = await fetch(`${API_BASE}/create-magic-invite/${currentGuildId}?alias=${encodeURIComponent(alias)}`, {
            headers: { 'Authorization': token }
        });

        if (response.ok) {
            const newCode = await response.text();
            
            // Safety check against empty string returns
            if (!newCode || newCode.trim() === "") {
                throw new Error("Bot returned an empty invite code.");
            }

            showToast(`Success! Mapped ${alias} to discord.gg/${newCode}`);
            aliasInput.value = "";
            loadServerConfig(); 
        } else {
            const error = await response.text();
            showToast(error, "error");
        }
    } catch (err) {
        showToast("Failed to generate magic invite.", "error");
        console.error(err);
    } finally {
        btn.innerText = "Generate Invite";
        btn.disabled = false;
        aliasInput.disabled = false;
    }
}

function renderAliasTable(aliases) {
    const tbody = document.getElementById('alias-list');
    tbody.innerHTML = "";
    
    const sortedEntries = Object.entries(aliases).sort((a, b) => String(a).localeCompare(String(b)));

    for (const [code, alias] of sortedEntries) {
        tbody.innerHTML += `
            <tr>
                <td><strong>${alias}</strong></td>
                <td><code>discord.gg/${code}</code></td>
                <td style="text-align: right;">
                    <button class="btn-danger" onclick="deleteAlias('${code}')">Delete</button>
                </td>
            </tr>`;
    }
    
    if (Object.keys(aliases).length === 0) {
        tbody.innerHTML = '<tr><td colspan="3" style="text-align:center; padding: 30px; color: var(--muted);">No magic invites found.</td></tr>';
    }
}

async function deleteAlias(code) {
    if (!currentGuildId) return;
    if (!confirm("Delete this invite? It will be revoked on Discord as well.")) return;

    const token = localStorage.getItem('admin_session');
    
    try {
        const response = await fetch(`${API_BASE}/delete-invite/${currentGuildId}/${code}`, {
            method: 'DELETE',
            headers: { 'Authorization': token }
        });

        if (response.ok) {
            showToast("Invite deleted from Discord.");
            loadServerConfig();
        } else {
            showToast("Error deleting invite.", "error");
        }
    } catch (err) {
        showToast("Failed to connect to server.", "error");
    }
}

function updatePreview() {
    // 0. Bot Nickname Update
    const nickInput = document.getElementById('config-nickname');
    const prevBotName = document.getElementById('prev-bot-name');
    if (prevBotName) {
        // If the box has text, use it. Otherwise, default to "Society Bot"
        prevBotName.innerText = (nickInput && nickInput.value.trim() !== '') ? nickInput.value.trim() : 'Society Bot';
    }
    // 1. Color
    const colorEl = document.getElementById('emb-color');
    const hex = (colorEl ? colorEl.value.trim() : '') || '#1e1f22';
    document.getElementById('prev-container').style.borderLeftColor = hex;

    // 2. Title
    const titleElInput = document.getElementById('emb-title');
    const title = titleElInput ? titleElInput.value.trim() : '';
    const titleEl = document.getElementById('prev-title');
    if (title) { 
        titleEl.style.display = 'block'; 
        titleEl.innerText = title; 
    } else { 
        titleEl.style.display = 'none'; 
    }

// 3. Description (With Live Variable Mockups!)
    const descEl = document.getElementById('emb-desc');
    let desc = descEl ? descEl.value.trim() : '';
    
    if (!desc) {
        desc = 'Hello <span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@User</span>, welcome to the server!\nJoined via: discord.gg/abcd123\nInvited by: <span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@Friend</span>\nTime: Today at 12:00 PM';
    } else {
        // The \b ensures $INVITE doesn't accidentally overwrite $INVITER
        desc = desc.replace(/\$USER\b/gi, '<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@User</span>')
                   .replace(/\$GUILD\b/gi, 'MMU Minecraft Society')
                   .replace(/\$MEMBER_COUNT\b/gi, '42')
                   .replace(/\$INVITER\b/gi, '<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@Friend</span>')
                   .replace(/\$INVITE\b/gi, 'discord.gg/abcd123')
                   .replace(/\$TIME\b/gi, 'Today at 12:00 PM');
    }
    // We use innerHTML here instead of innerText so the blue @User tags render properly
    document.getElementById('prev-desc').innerHTML = desc.replace(/\n/g, '<br>');

    // 4. Thumbnail
    const thumbElInput = document.getElementById('emb-thumb');
    const thumb = thumbElInput ? thumbElInput.value.trim() : '';
    const prevThumb = document.getElementById('prev-thumb');
    if (thumb) { 
        prevThumb.src = thumb; 
        prevThumb.style.display = 'block'; 
    } else { 
        prevThumb.style.display = 'none'; 
    }

    // 5. Main Image
    const imgElInput = document.getElementById('emb-image');
    const img = imgElInput ? imgElInput.value.trim() : '';
    const prevImg = document.getElementById('prev-image');
    if (img) { 
        prevImg.src = img; 
        prevImg.style.display = 'block'; 
    } else { 
        prevImg.style.display = 'none'; 
    }

    // 6. Footer
    const footerElInput = document.getElementById('emb-footer');
    let footerTxt = footerElInput ? footerElInput.value.trim() : '';
    const footerWrap = document.getElementById('prev-footer-wrap');
    if (footerTxt) {
        footerTxt = footerTxt.replace(/\$MEMBER_COUNT/g, '42');
        footerWrap.style.display = 'flex';
        document.getElementById('prev-footer-text').innerText = footerTxt;
    } else {
        footerWrap.style.display = 'none';
    }
}

// Function to update the dashboard with live Minecraft data
async function refreshMinecraftData() {
    try {
        const res = await fetch(`${API_BASE}/mc-data`);
        if (!res.ok) throw new Error("Offline");
        
        const data = await res.json();

        // 1. Update Stats
        document.getElementById('mc-online-status').innerText = "● Online";
        document.getElementById('mc-online-status').style.color = "var(--green)";
        document.getElementById('mc-players').innerText = `${data.status.online || 0} / ${data.status.max || 0}`;

        // 2. Format World Time (Ticks to HH:MM)
        const ticks = data.status.time || 0;
        const hours = Math.floor((ticks / 1000 + 6) % 24);
        const minutes = Math.floor((ticks % 1000) * 0.06);
        const timeStr = `${hours}:${minutes < 10 ? '0' : ''}${minutes}`;
        document.getElementById('mc-time').innerText = `Day ${data.status.day || 0} (${timeStr})`;

        // 3. Update Chat Feed
        const chatBox = document.getElementById('mc-chat-box');
        if (data.chat && data.chat.length > 0) {
        chatBox.innerHTML = data.chat.map(m => `
            <div style="margin-bottom: 10px; border-bottom: 1px solid rgba(255,255,255,0.03); padding-bottom: 5px;">
                <span class="pill" style="background: rgba(74, 222, 128, 0.1); color: var(--green); border: 1px solid rgba(74, 222, 128, 0.2);">
                    ${escapeHTML(m.user)}
                </span> 
                <span style="color: var(--white); margin-left: 8px;">${escapeHTML(m.text)}</span>
            </div>
        `).join('');
        } else {
            chatBox.innerHTML = '<div style="color: var(--muted); text-align: center; margin-top: 100px;">Waiting for in-game activity...</div>';
        }
    } catch (e) {
        document.getElementById('mc-online-status').innerText = "○ Offline";
        document.getElementById('mc-online-status').style.color = "var(--muted)";
    }

    if (data.status.leaderboards) {
        const mobs = data.status.leaderboards.mob_kills || [];
        document.getElementById('leaderboard-mobs').innerHTML = mobs.length > 0 
            ? mobs.map((p, i) => `<div>${i+1}. ${p}</div>`).join('')
            : "No data yet";

        const deaths = data.status.leaderboards.deaths || [];
        document.getElementById('leaderboard-deaths').innerHTML = deaths.length > 0 
            ? deaths.map((p, i) => `<div>${i+1}. ${p}</div>`).join('')
            : "No data yet";
    }

}

// Function to send a message from the Website to Minecraft
async function sendToGame() {
    const input = document.getElementById('mc-chat-input');
    const message = input.value.trim();
    if (!message) return;

    try {
        const res = await fetch(`${API_BASE}/mc-send-chat`, {
            method: 'POST',
            headers: { 
                'Content-Type': 'application/json',
                'Authorization': localStorage.getItem('admin_session')
            },
            body: JSON.stringify({ message: message })
        });

        if (res.ok) {
            input.value = "";
            // Optionally refresh immediately to show your message
            refreshMinecraftData();
        } else {
            alert("Failed to send: Ensure you are logged in as an admin.");
        }
    } catch (err) {
        console.error("Chat Error:", err);
    }
}

// Function to safely escape HTML
function escapeHTML(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// Start the loop: Check for new data every 3 seconds
setInterval(refreshMinecraftData, 3000);