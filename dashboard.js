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

// Load Settings for Selected Server
async function loadServerConfig() {
    if (!currentGuildId) return;
    const response = await fetch(`${API_BASE}/config/${currentGuildId}`, {
        headers: { 'Authorization': localStorage.getItem('admin_session') }
    });
    const config = await response.json();
    
    // Set Nickname
    const nickEl = document.getElementById('config-nickname');
    if (nickEl) nickEl.value = config.nickname || "";

    // Safely get the embed object
    const emb = config.welcomeEmbed || {};

    // Populate Embed Fields exactly mapping to ServerConfig properties
    document.getElementById('emb-author').value = emb.authorName || "";
    document.getElementById('emb-author-icon').value = emb.authorIcon || "";
    document.getElementById('emb-title').value = emb.title || "";
    document.getElementById('emb-desc').value = emb.description || "";
    document.getElementById('emb-color').value = emb.color || "";
    document.getElementById('emb-thumb').value = emb.thumbnail || "";
    document.getElementById('emb-image').value = emb.image || "";
    document.getElementById('emb-footer').value = emb.footerText || "";

    // Render tables and preview
    renderAliasTable(config.inviteAliases || {});
    updatePreview(); 
}

// Save Settings
async function saveServerConfig() {
    if (!currentGuildId) return;
    const token = localStorage.getItem('admin_session');
    const btn = document.getElementById('save-general-btn');
    
    if (btn) {
        btn.innerText = "Saving...";
        btn.disabled = true;
    }
    
    try {
        // Create a strict, clean payload matching ServerConfig.java exactly
        const payload = {
            nickname: document.getElementById('config-nickname').value,
            welcomeEmbed: {
                authorName: document.getElementById('emb-author').value,
                authorIcon: document.getElementById('emb-author-icon').value,
                title: document.getElementById('emb-title').value,
                description: document.getElementById('emb-desc').value,
                color: document.getElementById('emb-color').value,
                thumbnail: document.getElementById('emb-thumb').value,
                image: document.getElementById('emb-image').value,
                footerText: document.getElementById('emb-footer').value
            }
        };

        const postRes = await fetch(`${API_BASE}/config/${currentGuildId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': token },
            body: JSON.stringify(payload)
        });

        if (postRes.ok) {
            showToast("Identity settings saved successfully!");
        } else {
            showToast("Failed to save settings. Check bot terminal.", "error");
        }
    } catch (err) {
        showToast("Network error.", "error");
    } finally {
        if (btn) {
            btn.innerText = "Save Identity Settings";
            btn.disabled = false;
        }
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
    // 1. Color
    const hex = document.getElementById('emb-color').value.trim() || '#1e1f22';
    document.getElementById('prev-container').style.borderLeftColor = hex;

    // 2. Author
    const authorTxt = document.getElementById('emb-author').value.trim();
    const authorIco = document.getElementById('emb-author-icon').value.trim();
    const authorWrap = document.getElementById('prev-author-wrap');
    if (authorTxt) {
        authorWrap.style.display = 'flex';
        document.getElementById('prev-author-text').innerText = authorTxt;
        const imgEl = document.getElementById('prev-author-icon');
        if (authorIco) { imgEl.src = authorIco; imgEl.style.display = 'block'; }
        else { imgEl.style.display = 'none'; }
    } else {
        authorWrap.style.display = 'none';
    }

    // 3. Title
    const title = document.getElementById('emb-title').value.trim();
    const titleEl = document.getElementById('prev-title');
    if (title) { titleEl.style.display = 'block'; titleEl.innerText = title; }
    else { titleEl.style.display = 'none'; }

    // 4. Description
    const desc = document.getElementById('emb-desc').value.trim();
    document.getElementById('prev-desc').innerText = desc || 'Hello $USER, please read the rules...';

    // 5. Thumbnail
    const thumb = document.getElementById('emb-thumb').value.trim();
    const thumbEl = document.getElementById('prev-thumb');
    if (thumb) { thumbEl.src = thumb; thumbEl.style.display = 'block'; }
    else { thumbEl.style.display = 'none'; }

    // 6. Main Image
    const img = document.getElementById('emb-image').value.trim();
    const imgEl = document.getElementById('prev-image');
    if (img) { imgEl.src = img; imgEl.style.display = 'block'; }
    else { imgEl.style.display = 'none'; }

    // 7. Footer
    const footerTxt = document.getElementById('emb-footer').value.trim();
    const footerWrap = document.getElementById('prev-footer-wrap');
    if (footerTxt) {
        footerWrap.style.display = 'flex';
        document.getElementById('prev-footer-text').innerText = footerTxt;
    } else {
        footerWrap.style.display = 'none';
    }
}