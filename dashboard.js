const API_BASE = "https://api.mmuminecraftsociety.co.uk";
let currentGuildId = null;
let welcomeEmbedBuilder = null;
let leaveEmbedBuilder = null;

const DASHBOARD_ACTIVE_TAB_KEY = 'dashboard_active_tab';
const DASHBOARD_SELECTED_GUILD_KEY = 'dashboard_selected_guild';

document.addEventListener("DOMContentLoaded", () => {
    const urlParams = new URLSearchParams(window.location.search);
    const sessionToken = urlParams.get('session');

    if (sessionToken) {
        localStorage.setItem('admin_session', sessionToken);
        window.history.replaceState({}, document.title, "/dashboard.html");
    }
    initEmbedBuilders();
    restoreActiveTabFromStorage();
    verifySession();
});

function getNavButtonForTab(tabId) {
    return document.querySelector(`.nav-tab[onclick*="'${tabId}'"]`);
}

function restoreActiveTabFromStorage() {
    const savedTabId = localStorage.getItem(DASHBOARD_ACTIVE_TAB_KEY);
    if (!savedTabId) return;

    const tabEl = document.getElementById(savedTabId);
    const navBtn = getNavButtonForTab(savedTabId);
    if (!tabEl || !navBtn) return;

    switchTab(savedTabId, navBtn, false);
}

function initEmbedBuilders() {
    welcomeEmbedBuilder = createEmbedBuilder({
        nicknameInputId: 'config-nickname',
        inputIds: {
            title: 'emb-title',
            desc: 'emb-desc',
            color: 'emb-color',
            thumb: 'emb-thumb',
            image: 'emb-image',
            footer: 'emb-footer'
        },
        previewIds: {
            botName: 'prev-bot-name',
            container: 'prev-container',
            title: 'prev-title',
            desc: 'prev-desc',
            thumb: 'prev-thumb',
            image: 'prev-image',
            footerWrap: 'prev-footer-wrap',
            footerText: 'prev-footer-text'
        },
        defaultDescriptionHtml: 'Hello <span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@User</span>, welcome to the server!<br>Joined via: discord.gg/abcd123<br>Invited by: <span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@Friend</span><br>Time: Today at 12:00 PM',
        variableMocks: {
            '$USER': '<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@User</span>',
            '$GUILD': 'MMU Minecraft Society',
            '$MEMBER_COUNT': '42',
            '$INVITER': '<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@Friend</span>',
            '$INVITE': 'discord.gg/abcd123',
            '$AGE': '<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">2 years, 3 months</span>',
            '$PFP': '<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">User profile picture URL</span>',
            '$TIME': 'Today at 12:00 PM'
        },
        mediaMocks: {
            '$PFP': 'https://cdn.discordapp.com/embed/avatars/0.png'
        },
        defaultBotName: 'Society Bot',
        defaultBorderColor: '#1e1f22'
    });

    leaveEmbedBuilder = createEmbedBuilder({
        nicknameInputId: 'config-nickname',
        inputIds: {
            title: 'leave-emb-title',
            desc: 'leave-emb-desc',
            color: 'leave-emb-color',
            thumb: 'leave-emb-thumb',
            image: 'leave-emb-image',
            footer: 'leave-emb-footer'
        },
        previewIds: {
            botName: 'leave-prev-bot-name',
            container: 'leave-prev-container',
            title: 'leave-prev-title',
            desc: 'leave-prev-desc',
            thumb: 'leave-prev-thumb',
            image: 'leave-prev-image',
            footerWrap: 'leave-prev-footer-wrap',
            footerText: 'leave-prev-footer-text'
        },
        defaultDescriptionHtml: 'User (<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@User</span>) left the server.<br>Time in server: <span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">2y 3mo 5d</span><br>Roles: <span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@Member</span>, <span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@Builder</span>',
        variableMocks: {
            '$USER': '@User (<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@User</span>)',
            '$TIME_IN_SERVER': '<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">2y 3mo 5d</span>',
            '$ROLES': '<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">@Member, @Builder</span>',
            '$MEMBER_COUNT': '41',
            '$PFP': '<span style="color:#c9cdfb; background:rgba(88,101,242,.3); padding:0 2px; border-radius:3px;">User profile picture URL</span>',
            '$TIME': 'Today at 12:00 PM'
        },
        mediaMocks: {
            '$PFP': 'https://cdn.discordapp.com/embed/avatars/1.png'
        },
        defaultBotName: 'Society Bot',
        defaultBorderColor: '#ef4444'
    });

    if (welcomeEmbedBuilder) {
        welcomeEmbedBuilder.update();
    }

    if (leaveEmbedBuilder) {
        leaveEmbedBuilder.update();
    }
}

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
function switchTab(tabId, buttonElement, persist = true) {
    document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.nav-tab').forEach(btn => btn.classList.remove('active'));
    
    document.getElementById(tabId).classList.add('active');
    buttonElement.classList.add('active');

    if (persist) {
        localStorage.setItem(DASHBOARD_ACTIVE_TAB_KEY, tabId);
    }

    updateServerRequiredState(tabId);
}

function updateServerRequiredState(activeTabId) {
    const needsServer = activeTabId !== 'tab-minecraft' && !currentGuildId;
    const identityNote = document.getElementById('identity-server-note');
    const generalNote = document.getElementById('general-server-note');
    const invitesNote = document.getElementById('invites-server-note');
    const leaveNote = document.getElementById('leave-server-note');

    if (identityNote) {
        identityNote.classList.toggle('visible', activeTabId === 'tab-identity' && needsServer);
    }

    if (generalNote) {
        generalNote.classList.toggle('visible', activeTabId === 'tab-general' && needsServer);
    }

    if (invitesNote) {
        invitesNote.classList.toggle('visible', activeTabId === 'tab-invites' && needsServer);
    }

    if (leaveNote) {
        leaveNote.classList.toggle('visible', activeTabId === 'tab-leave' && needsServer);
    }
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
        const serverBar = document.getElementById('guild-list');
        serverBar.innerHTML = '';
        let firstButton = null;
        const savedGuildId = localStorage.getItem(DASHBOARD_SELECTED_GUILD_KEY);
        let restored = false;

        if (guilds.length === 0) {
            serverBar.innerHTML = '<span style="color:var(--muted); font-size:14px;">No managed servers found.</span>';
            currentGuildId = null;
            localStorage.removeItem(DASHBOARD_SELECTED_GUILD_KEY);
            updateServerRequiredState('tab-minecraft');
            return;
        }

        guilds.forEach((g, index) => {
            const btn = document.createElement('button');
            btn.className = 'server-pill';
            btn.innerText = g.name;
            btn.onclick = () => selectServer(g.id, btn);
            serverBar.appendChild(btn);

            if (index === 0) {
                firstButton = btn;
            }

            if (savedGuildId && g.id === savedGuildId) {
                selectServer(g.id, btn);
                restored = true;
            }
        });

        if (!restored && guilds.length > 0 && firstButton) {
            selectServer(guilds[0].id, firstButton);
        }

        const activeTab = document.querySelector('.tab-content.active');
        updateServerRequiredState(activeTab ? activeTab.id : 'tab-minecraft');
    } catch (err) {
        showToast("Error fetching server list.", "error");
    }
}

function selectServer(guildId, buttonElement) {
    currentGuildId = guildId;
    localStorage.setItem(DASHBOARD_SELECTED_GUILD_KEY, guildId);
    
    // Update active pill styling
    document.querySelectorAll('.server-pill').forEach(btn => btn.classList.remove('active'));
    buttonElement.classList.add('active');
    loadServerConfig();

    const activeTab = document.querySelector('.tab-content.active');
    updateServerRequiredState(activeTab ? activeTab.id : 'tab-minecraft');
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

    let WelcomeEmbedData = {};
    try { WelcomeEmbedData = JSON.parse(config.welcomeMessage || "{}"); } 
    catch (e) { WelcomeEmbedData = { desc: config.welcomeMessage || "" }; }

    document.getElementById('emb-title').value = WelcomeEmbedData.title || "";
    document.getElementById('emb-desc').value = WelcomeEmbedData.desc || "";
    document.getElementById('emb-color').value = WelcomeEmbedData.color || "";
    document.getElementById('emb-thumb').value = WelcomeEmbedData.thumb || "";
    document.getElementById('emb-image').value = WelcomeEmbedData.image || "";
    document.getElementById('emb-footer').value = WelcomeEmbedData.footer || "";

    let leaveEmbedData = {};
    try { leaveEmbedData = JSON.parse(config.leaveMessage || "{}"); }
    catch (e) { leaveEmbedData = { desc: config.leaveMessage || "" }; }

    document.getElementById('leave-emb-title').value = leaveEmbedData.title || "";
    document.getElementById('leave-emb-desc').value = leaveEmbedData.desc || "";
    document.getElementById('leave-emb-color').value = leaveEmbedData.color || "";
    document.getElementById('leave-emb-thumb').value = leaveEmbedData.thumb || "";
    document.getElementById('leave-emb-image').value = leaveEmbedData.image || "";
    document.getElementById('leave-emb-footer').value = leaveEmbedData.footer || "";

    renderAliasTable(config.inviteAliases || {});
    updateWelcomePreview();
    updateLeavePreview();
}

async function postConfigUpdate(payload, options = {}) {
    if (!currentGuildId) {
        showToast("Select a server from the sidebar first.", "error");
        return false;
    }

    const token = localStorage.getItem('admin_session');
    const btnId = options.btnId || null;
    const savingText = options.savingText || "Saving...";
    const idleText = options.idleText || "Save";
    const successMessage = options.successMessage || "Settings saved successfully!";

    const btn = btnId ? document.getElementById(btnId) : null;
    if (btn) {
        btn.innerText = savingText;
        btn.disabled = true;
    }
    
    try {
        const postRes = await fetch(`${API_BASE}/config/${currentGuildId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': token },
            body: JSON.stringify(payload)
        });

        if (postRes.ok) {
            showToast(successMessage);
            return true;
        }

        showToast("Failed to save settings.", "error");
        return false;
    } catch (err) {
        showToast("Network error.", "error");
        return false;
    } finally {
        if (btn) {
            btn.innerText = idleText;
            btn.disabled = false;
        }
    }
}

function buildWelcomeEmbedPayload() {
    return welcomeEmbedBuilder
        ? welcomeEmbedBuilder.getPayload()
        : {
            title: document.getElementById('emb-title').value,
            desc: document.getElementById('emb-desc').value,
            color: document.getElementById('emb-color').value,
            thumb: document.getElementById('emb-thumb').value,
            image: document.getElementById('emb-image').value,
            footer: document.getElementById('emb-footer').value
        };
}

function buildLeaveEmbedPayload() {
    return leaveEmbedBuilder
        ? leaveEmbedBuilder.getPayload()
        : {
            title: document.getElementById('leave-emb-title').value,
            desc: document.getElementById('leave-emb-desc').value,
            color: document.getElementById('leave-emb-color').value,
            thumb: document.getElementById('leave-emb-thumb').value,
            image: document.getElementById('leave-emb-image').value,
            footer: document.getElementById('leave-emb-footer').value
        };
}

async function saveServerIdentity() {
    const nicknameInput = document.getElementById('config-nickname');
    await postConfigUpdate(
        {
            nickname: nicknameInput ? nicknameInput.value : ''
        },
        {
            btnId: 'save-identity-btn',
            savingText: 'Saving Identity...',
            idleText: 'Save Server Identity',
            successMessage: 'Server identity saved successfully!'
        }
    );
}

async function saveWelcomeEmbedConfig() {
    await postConfigUpdate(
        {
            welcomeMessage: JSON.stringify(buildWelcomeEmbedPayload())
        },
        {
            btnId: 'save-welcome-btn',
            savingText: 'Saving Welcome Embed...',
            idleText: 'Save Welcome Embed',
            successMessage: 'Welcome embed saved successfully!'
        }
    );
}

async function saveLeaveEmbedConfig() {
    await postConfigUpdate(
        {
            leaveMessage: JSON.stringify(buildLeaveEmbedPayload())
        },
        {
            btnId: 'save-leave-btn',
            savingText: 'Saving Leave Embed...',
            idleText: 'Save Leave Settings',
            successMessage: 'Leave listener embed saved successfully!'
        }
    );
}

async function checkBotStatus() {
    const statusEl = document.getElementById('bot-status');
    try {
        const response = await fetch(`${API_BASE}/status`);
        if (response.ok) {
            statusEl.innerText = "● Bot Online";
            statusEl.style.color = "var(--green)";
        } else {
            throw new Error("Bad status");
        }
    } catch (err) {
        statusEl.innerText = "● Bot Offline";
        statusEl.style.color = "#e74c3c";
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

function updateWelcomePreview() {
    if (!welcomeEmbedBuilder) {
        initEmbedBuilders();
    }
    if (welcomeEmbedBuilder) {
        welcomeEmbedBuilder.update();
    }
}

function updateLeavePreview() {
    if (!leaveEmbedBuilder) {
        initEmbedBuilders();
    }
    if (leaveEmbedBuilder) {
        leaveEmbedBuilder.update();
    }
}

function updatePreview() {
    updateWelcomePreview();
}

function createEmbedBuilder(config) {
    if (!config || !config.inputIds || !config.previewIds) {
        return null;
    }

    const getInputValue = id => {
        const el = document.getElementById(id);
        return el ? el.value.trim() : '';
    };

    const replaceTemplateTokens = (text, replacements) => {
        let output = text;
        Object.entries(replacements || {}).forEach(([token, replacement]) => {
            const pattern = new RegExp(escapeRegExp(token) + '\\b', 'gi');
            output = output.replace(pattern, replacement);
        });
        return output;
    };

    const update = () => {
        const prevBotName = document.getElementById(config.previewIds.botName);
        const prevContainer = document.getElementById(config.previewIds.container);
        const prevTitle = document.getElementById(config.previewIds.title);
        const prevDesc = document.getElementById(config.previewIds.desc);
        const prevThumb = document.getElementById(config.previewIds.thumb);
        const prevImage = document.getElementById(config.previewIds.image);
        const prevFooterWrap = document.getElementById(config.previewIds.footerWrap);
        const prevFooterText = document.getElementById(config.previewIds.footerText);

        if (!prevContainer || !prevTitle || !prevDesc || !prevThumb || !prevImage || !prevFooterWrap || !prevFooterText) {
            return;
        }

        const nicknameEl = document.getElementById(config.nicknameInputId);
        if (prevBotName) {
            prevBotName.innerText = (nicknameEl && nicknameEl.value.trim() !== '')
                ? nicknameEl.value.trim()
                : (config.defaultBotName || 'Society Bot');
        }

        const color = getInputValue(config.inputIds.color) || (config.defaultBorderColor || '#1e1f22');
        prevContainer.style.borderLeftColor = color;

        const title = getInputValue(config.inputIds.title);
        if (title) {
            prevTitle.style.display = 'block';
            prevTitle.innerText = title;
        } else {
            prevTitle.style.display = 'none';
        }

        const rawDesc = getInputValue(config.inputIds.desc);
        if (!rawDesc) {
            prevDesc.innerHTML = config.defaultDescriptionHtml || '';
        } else {
            const safeDesc = sanitize(rawDesc);
            const withMocks = replaceTemplateTokens(safeDesc, config.variableMocks);
            prevDesc.innerHTML = withMocks.replace(/\n/g, '<br>');
        }

        const thumbRaw = getInputValue(config.inputIds.thumb);
        const thumb = thumbRaw ? replaceTemplateTokens(thumbRaw, config.mediaMocks || config.variableMocks) : '';
        if (thumb) {
            prevThumb.src = thumb;
            prevThumb.style.display = 'block';
        } else {
            prevThumb.style.display = 'none';
        }

        const imageRaw = getInputValue(config.inputIds.image);
        const image = imageRaw ? replaceTemplateTokens(imageRaw, config.mediaMocks || config.variableMocks) : '';
        if (image) {
            prevImage.src = image;
            prevImage.style.display = 'block';
        } else {
            prevImage.style.display = 'none';
        }

        const footerRaw = getInputValue(config.inputIds.footer);
        if (footerRaw) {
            const footerText = replaceTemplateTokens(footerRaw, config.footerMocks || config.variableMocks);
            prevFooterWrap.style.display = 'flex';
            prevFooterText.innerText = footerText;
        } else {
            prevFooterWrap.style.display = 'none';
        }
    };

    const getPayload = () => ({
        title: getInputValue(config.inputIds.title),
        desc: getInputValue(config.inputIds.desc),
        color: getInputValue(config.inputIds.color),
        thumb: getInputValue(config.inputIds.thumb),
        image: getInputValue(config.inputIds.image),
        footer: getInputValue(config.inputIds.footer)
    });

    return { update, getPayload };
}

function escapeRegExp(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Sanitizes a string by converting HTML characters to their entity equivalents.
 * This prevents Cross-Site Scripting (XSS) when injecting user content into the DOM.
 */
function sanitize(str) {
    if (typeof str !== 'string') return '';
    return str.replace(/[&<>"']/g, function(match) {
        const escape = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
        };
        return escape[match];
    });
}

async function refreshMinecraftData() {
    try {
        const res = await fetch(`${API_BASE}/mc-data`);
        if (!res.ok) throw new Error("Offline");
        const data = await res.json();

        // 1. Update Basic Stats
        if (data.status) {
            document.getElementById('mc-players').innerText = `${data.status.online || 0} / ${data.status.max || 0}`;

            const ticks = data.status.time || 0;
            const hours = Math.floor((ticks / 1000 + 6) % 24);
            const minutes = Math.floor((ticks % 1000) * 0.06);
            const timeStr = `${hours}:${minutes < 10 ? '0' : ''}${minutes}`;
            document.getElementById('mc-time').innerText = `Day ${data.status.day || 0} (${timeStr})`;
        }

        // 2. Update Chat
        const chatBox = document.getElementById('mc-chat-box');
        if (data.chat && data.chat.length > 0) {
            chatBox.innerHTML = data.chat.map(m => {
                const isServer = m.user === "Server";
                const pillColor = isServer ? "rgba(251, 191, 36, 0.1)" : "rgba(74, 222, 128, 0.1)";
                const textColor = isServer ? "#fbbf24" : "var(--green)";

                return `
                <div style="margin-bottom: 12px; border-bottom: 1px solid rgba(255,255,255,0.03); padding-bottom: 6px;">
                    <div style="display: flex; align-items: center; margin-bottom: 4px;">
                        <span class="pill" style="font-size: 11px; background: ${pillColor}; color: ${textColor}; border: 1px solid ${pillColor};">
                            ${sanitize(m.user)}
                        </span> 
                        <span style="font-family: 'JetBrains Mono'; font-size: 10px; color: var(--muted); margin-left: 8px;">
                            ${m.time || ''}
                        </span>
                    </div>
                    <div style="color:var(--text); margin-left: 4px; font-size: 13px;">
                        ${isServer ? `<em>${sanitize(m.text)}</em>` : sanitize(m.text)}
                    </div>
                </div>
                `;
            }).join('');
        } else {
            chatBox.innerHTML = '<div style="color:var(--muted)">No recent activity...</div>';
        }
    } catch (e) {
        document.getElementById('mc-chat-box').innerHTML = '<div style="color:#e74c3c">Bridge Offline...</div>';
        document.getElementById('mc-players').innerText = "0 / 0";
    }
}

// Start the auto-refresh loop
setInterval(refreshMinecraftData, 2000);