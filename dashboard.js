const API_BASE = "https://api.mmuminecraftsociety.co.uk";
let currentGuildId = null;
let welcomeEmbedBuilder = null;
let leaveEmbedBuilder = null;
let reactionRoleConfigsById = {};
let currentGuildChannels = [];
let currentGuildRoles = [];
let currentAiProfiles = {};
let currentAiProfileDescriptions = {};
let currentAiActiveProfileName = 'Bob';
let selectedAiProfileName = 'Bob';
let activeReactionRoleId = null;

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
    initReactionRoleEditor();
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
        titleMocks: {
            '$TIME': '<t:1776689340:R>'
        },
        footerMocks: {
            '$TIME': '<t:1776689340:R>'
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
        titleMocks: {
            '$TIME': '<t:1776689340:R>'
        },
        footerMocks: {
            '$TIME': '<t:1776689340:R>'
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

// Toast System
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
    const reactionNote = document.getElementById('reaction-server-note');
    const invitesNote = document.getElementById('invites-server-note');
    const leaveNote = document.getElementById('leave-server-note');
    const aiNote = document.getElementById('ai-server-note');

    if (identityNote) {
        identityNote.classList.toggle('visible', activeTabId === 'tab-identity' && needsServer);
    }

    if (generalNote) {
        generalNote.classList.toggle('visible', activeTabId === 'tab-general' && needsServer);
    }

    if (reactionNote) {
        reactionNote.classList.toggle('visible', activeTabId === 'tab-reaction' && needsServer);
    }

    if (invitesNote) {
        invitesNote.classList.toggle('visible', activeTabId === 'tab-invites' && needsServer);
    }

    if (leaveNote) {
        leaveNote.classList.toggle('visible', activeTabId === 'tab-leave' && needsServer);
    }

    if (aiNote) {
        aiNote.classList.toggle('visible', activeTabId === 'tab-ai' && needsServer);
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
            window.location.href = '/';
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

    reactionRoleConfigsById = config.reactionRoleConfigs || {};
    await loadGuildMetadata();
    renderSetupChannelOptions(config);
    renderAIProfiles(config);
    renderReactionRoleLiveList();
    hideReactionRoleEditor();
    activeReactionRoleId = null;

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

function renderSetupChannelOptions(config = {}) {
    const fields = [
        { id: 'route-welcome-channel', value: config.welcomeChannelId || '' },
        { id: 'route-leave-channel', value: config.leaveChannelId || '' },
        { id: 'route-audit-edit-channel', value: config.auditEditChannelId || '' },
        { id: 'route-audit-delete-channel', value: config.auditDeleteChannelId || '' },
        { id: 'route-ai-channel', value: config.aiChannelId || '' },
        { id: 'route-ticket-log-channel', value: config.ticketLogChannelId || '' }
    ];

    fields.forEach(field => {
        const select = document.getElementById(field.id);
        if (!select) return;
        populateChannelSelect(select, field.value, { includeNotSet: true });
    });
}

async function saveChannelRoutingConfig() {
    await postConfigUpdate(
        {
            welcomeChannelId: document.getElementById('route-welcome-channel')?.value || null,
            leaveChannelId: document.getElementById('route-leave-channel')?.value || null,
            auditEditChannelId: document.getElementById('route-audit-edit-channel')?.value || null,
            auditDeleteChannelId: document.getElementById('route-audit-delete-channel')?.value || null,
            aiChannelId: document.getElementById('route-ai-channel')?.value || null,
            ticketLogChannelId: document.getElementById('route-ticket-log-channel')?.value || null
        },
        {
            btnId: 'save-routing-btn',
            savingText: 'Saving Channel Routing...',
            idleText: 'Save Channel Routing',
            successMessage: 'Channel routing saved successfully!'
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

function normalizeAiProfiles(rawProfiles = {}) {
    const normalized = {};

    if (rawProfiles && typeof rawProfiles === 'object') {
        Object.entries(rawProfiles).forEach(([profileName, prompt]) => {
            const trimmedName = (profileName || '').trim();
            if (!trimmedName) return;
            normalized[trimmedName] = typeof prompt === 'string' ? prompt : '';
        });
    }

    return normalized;
}

function normalizeAiProfileDescriptions(rawDescriptions = {}) {
    const normalized = {};

    if (rawDescriptions && typeof rawDescriptions === 'object') {
        Object.entries(rawDescriptions).forEach(([profileName, description]) => {
            const trimmedName = (profileName || '').trim();
            if (!trimmedName) return;
            normalized[trimmedName] = typeof description === 'string' ? description : '';
        });
    }

    return normalized;
}

function resolveAiProfileName(preferredName, profiles = {}) {
    if (preferredName && profiles[preferredName]) {
        return preferredName;
    }

    const profileNames = Object.keys(profiles);
    if (profileNames.length === 0) {
        return 'Bob';
    }

    if (preferredName) {
        const match = profileNames.find(name => name.toLowerCase() === preferredName.toLowerCase());
        if (match) {
            return match;
        }
    }

    return profileNames[0];
}

function renderAIProfiles(config = {}) {
    currentAiProfiles = normalizeAiProfiles(config.aiProfiles);
    currentAiProfileDescriptions = normalizeAiProfileDescriptions(config.aiProfileDescriptions);

    if (Object.keys(currentAiProfiles).length === 0) {
        currentAiProfiles = { Bob: '' };
    }

    Object.keys(currentAiProfiles).forEach(profileName => {
        if (currentAiProfileDescriptions[profileName] === undefined) {
            currentAiProfileDescriptions[profileName] = '';
        }
    });
    Object.keys(currentAiProfileDescriptions).forEach(profileName => {
        if (currentAiProfiles[profileName] === undefined) {
            delete currentAiProfileDescriptions[profileName];
        }
    });

    currentAiActiveProfileName = resolveAiProfileName(config.activeAiProfileName || 'Bob', currentAiProfiles);
    selectedAiProfileName = resolveAiProfileName(currentAiActiveProfileName, currentAiProfiles);

    renderAIProfileList();
    renderAIProfileSelect();
    loadAIProfileIntoEditor(selectedAiProfileName);
}

function renderAIProfileList() {
    const list = document.getElementById('ai-profile-list');
    if (!list) return;

    const profileNames = Object.keys(currentAiProfiles || {});
    if (profileNames.length === 0) {
        list.innerHTML = '<div style="color: var(--muted); font-size: 13px;">No profiles found.</div>';
        return;
    }

    list.innerHTML = profileNames.map(profileName => {
        const isActive = profileName === currentAiActiveProfileName;
        const activeStyles = isActive
            ? 'border-color: var(--green); color: var(--green); background: rgba(74,222,128,.1);'
            : '';

        return `
            <div style="display:flex; gap:8px; align-items:center;">
                <button type="button" class="server-pill" data-ai-profile="${sanitize(profileName)}" style="${activeStyles}; flex:1;">${sanitize(profileName)}${isActive ? ' (active)' : ''}</button>
            </div>
        `;
    }).join('');

    list.querySelectorAll('[data-ai-profile]').forEach(btn => {
        btn.addEventListener('click', () => {
            const profileName = btn.getAttribute('data-ai-profile');
            if (profileName) {
                selectAIProfile(profileName);
            }
        });
    });
}

function renderAIProfileSelect() {
    const select = document.getElementById('ai-active-profile');
    if (!select) return;

    const profileNames = Object.keys(currentAiProfiles || {});
    select.innerHTML = profileNames.map(profileName => {
        const selected = profileName === currentAiActiveProfileName ? ' selected' : '';
        return `<option value="${sanitize(profileName)}"${selected}>${sanitize(profileName)}</option>`;
    }).join('');

    select.value = currentAiActiveProfileName || profileNames[0] || 'Bob';
    select.onchange = () => {
        const nextProfile = select.value;
        if (nextProfile) {
            setActiveAIProfile(nextProfile);
        }
    };
}

function loadAIProfileIntoEditor(profileName) {
    const nameInput = document.getElementById('ai-profile-name');
    const descriptionInput = document.getElementById('ai-profile-description');
    const promptInput = document.getElementById('ai-profile-prompt');
    if (!nameInput || !descriptionInput || !promptInput) return;

    const resolvedName = resolveAiProfileName(profileName, currentAiProfiles);
    selectedAiProfileName = resolvedName;
    nameInput.value = resolvedName;
    descriptionInput.value = currentAiProfileDescriptions[resolvedName] || '';
    promptInput.value = currentAiProfiles[resolvedName] || '';
    renderAIProfileSelect();
    renderAIProfileList();
}

function selectAIProfile(profileName) {
    if (!profileName) return;
    loadAIProfileIntoEditor(profileName);
}

function createNewAIProfile() {
    const nameInput = document.getElementById('ai-profile-name');
    const descriptionInput = document.getElementById('ai-profile-description');
    const promptInput = document.getElementById('ai-profile-prompt');
    if (!nameInput || !descriptionInput || !promptInput) return;

    selectedAiProfileName = '';
    nameInput.value = '';
    descriptionInput.value = '';
    promptInput.value = '';
    nameInput.focus();
}

async function saveAIProfile() {
    const nameInput = document.getElementById('ai-profile-name');
    const descriptionInput = document.getElementById('ai-profile-description');
    const promptInput = document.getElementById('ai-profile-prompt');
    const profileName = nameInput ? nameInput.value.trim() : '';
    const profileDescription = descriptionInput ? descriptionInput.value.trim() : '';
    const profilePrompt = promptInput ? promptInput.value.trim() : '';

    if (!profileName) {
        showToast('Profile name is required.', 'error');
        return;
    }

    if (!profilePrompt) {
        showToast('Profile prompt is required.', 'error');
        return;
    }

    const previousName = selectedAiProfileName;
    if (previousName && previousName !== profileName && currentAiProfiles[previousName] !== undefined) {
        delete currentAiProfiles[previousName];
        delete currentAiProfileDescriptions[previousName];
    }

    currentAiProfiles[profileName] = profilePrompt;
    currentAiProfileDescriptions[profileName] = profileDescription;
    selectedAiProfileName = profileName;

    if (!currentAiProfiles[currentAiActiveProfileName]) {
        currentAiActiveProfileName = profileName;
    }

    await postConfigUpdate(
        {
            aiProfiles: currentAiProfiles,
            aiProfileDescriptions: currentAiProfileDescriptions,
            activeAiProfileName: currentAiActiveProfileName
        },
        {
            btnId: 'ai-save-profile-btn',
            savingText: 'Saving Profile...',
            idleText: 'Save Profile',
            successMessage: 'AI profile saved successfully!'
        }
    );

    renderAIProfileList();
    renderAIProfileSelect();
}

async function setActiveAIProfile(profileName = null) {
    const nameInput = document.getElementById('ai-profile-name');
    const targetProfileName = (profileName || (nameInput ? nameInput.value.trim() : '') || selectedAiProfileName || '').trim();

    if (!targetProfileName || !currentAiProfiles[targetProfileName]) {
        showToast('Choose a saved profile first.', 'error');
        return;
    }

    currentAiActiveProfileName = targetProfileName;
    selectedAiProfileName = targetProfileName;

    await postConfigUpdate(
        {
            aiProfiles: currentAiProfiles,
            aiProfileDescriptions: currentAiProfileDescriptions,
            activeAiProfileName: currentAiActiveProfileName
        },
        {
            btnId: 'ai-set-active-btn',
            savingText: 'Setting Active...',
            idleText: 'Set Active',
            successMessage: `AI profile switched to ${targetProfileName}!`
        }
    );

    loadAIProfileIntoEditor(targetProfileName);
}

async function deleteAIProfile() {
    const nameInput = document.getElementById('ai-profile-name');
    const targetProfileName = (nameInput ? nameInput.value.trim() : '') || selectedAiProfileName;

    if (!targetProfileName || !currentAiProfiles[targetProfileName]) {
        showToast('Choose a saved profile to delete.', 'error');
        return;
    }

    const profileNames = Object.keys(currentAiProfiles);
    if (profileNames.length <= 1) {
        showToast('At least one AI profile must remain.', 'error');
        return;
    }

    delete currentAiProfiles[targetProfileName];
    delete currentAiProfileDescriptions[targetProfileName];

    if (!currentAiProfiles[currentAiActiveProfileName]) {
        currentAiActiveProfileName = Object.keys(currentAiProfiles)[0];
    }

    selectedAiProfileName = currentAiActiveProfileName;

    await postConfigUpdate(
        {
            aiProfiles: currentAiProfiles,
            aiProfileDescriptions: currentAiProfileDescriptions,
            activeAiProfileName: currentAiActiveProfileName
        },
        {
            btnId: 'ai-delete-profile-btn',
            savingText: 'Deleting Profile...',
            idleText: 'Delete Profile',
            successMessage: 'AI profile deleted successfully!'
        }
    );

    loadAIProfileIntoEditor(selectedAiProfileName);
}

function initReactionRoleEditor() {
    hideReactionRoleEditor();
}

function generateReactionRoleConfigId() {
    return `rr-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
}

function normalizeDiscordId(value) {
    const raw = (value || '').trim();
    if (!raw) return '';

    const mentionMatch = raw.match(/^<#(\d+)>$/);
    if (mentionMatch) {
        return mentionMatch[1];
    }

    return raw.replace(/\D/g, '');
}

function addReactionRoleButtonRow(buttonData = {}) {
    const container = document.getElementById('rr-buttons-container');
    if (!container) return;

    const row = document.createElement('div');
    row.className = 'rr-button-row';
    row.style.display = 'grid';
    row.style.gridTemplateColumns = 'minmax(120px, 1fr) minmax(180px, 1fr) 120px auto';
    row.style.gap = '8px';
    row.style.alignItems = 'end';

    const roleOptions = ['<option value="">Select role...</option>']
        .concat(currentGuildRoles.map(role => {
            const isSelected = role.id === (buttonData.roleId || '') ? ' selected' : '';
            return `<option value="${sanitize(role.id)}"${isSelected}>${sanitize(role.name)}</option>`;
        }))
        .join('');

    row.innerHTML = `
        <div class="input-group" style="margin-bottom:0;">
            <label>Label</label>
            <input type="text" class="rr-btn-label" placeholder="Member" value="${sanitize(buttonData.label || '')}">
        </div>
        <div class="input-group" style="margin-bottom:0;">
            <label>Role</label>
            <select class="rr-btn-role-select" style="background: #08101a; border: 1px solid var(--border); color: var(--white); padding: 12px 16px; border-radius: 8px; font-family: 'JetBrains Mono', monospace; font-size: 13px; width: 100%;">
                ${roleOptions}
            </select>
        </div>
        <div class="input-group" style="margin-bottom:0;">
            <label>Emoji (Optional)</label>
            <input type="text" class="rr-btn-emoji" placeholder=":thumbs_up:" value="${sanitize(buttonData.emoji || '')}">
        </div>
        <button class="btn-danger" type="button">Remove</button>
    `;

    const removeBtn = row.querySelector('.btn-danger');
    if (removeBtn) {
        removeBtn.addEventListener('click', () => row.remove());
    }

    container.appendChild(row);
}

function clearReactionRoleButtonRows() {
    const container = document.getElementById('rr-buttons-container');
    if (!container) return;
    container.innerHTML = '';
}

function collectReactionRoleButtons() {
    const container = document.getElementById('rr-buttons-container');
    if (!container) return [];

    const rows = Array.from(container.querySelectorAll('.rr-button-row'));
    const buttons = rows.map(row => {
        const label = row.querySelector('.rr-btn-label')?.value.trim() || '';
        const roleId = normalizeDiscordId(row.querySelector('.rr-btn-role-select')?.value || '');
        const emoji = row.querySelector('.rr-btn-emoji')?.value.trim() || '';
        return { label, roleId, emoji };
    }).filter(btn => btn.label && btn.roleId);

    return buttons;
}

function loadReactionRoleTemplate(templateId) {
    activeReactionRoleId = templateId;
    const channelInput = document.getElementById('rr-channel-id');
    const messageInput = document.getElementById('rr-message-id');
    const contentInput = document.getElementById('rr-content');

    renderReactionRoleLiveList();
    showReactionRoleEditor();

    const config = reactionRoleConfigsById?.[templateId] || null;
    if (!config) {
        if (channelInput) channelInput.value = '';
        if (messageInput) messageInput.value = '';
        if (contentInput) contentInput.value = 'Choose your roles below.';
        clearReactionRoleButtonRows();
        addReactionRoleButtonRow();
        return;
    }

    renderReactionRoleChannelOptions(config.channelId || '');
    if (channelInput) channelInput.value = config.channelId || '';
    if (messageInput) messageInput.value = config.messageId || '';
    if (contentInput) contentInput.value = config.content || '';

    clearReactionRoleButtonRows();
    const buttons = Array.isArray(config.buttons) ? config.buttons : [];
    if (buttons.length === 0) {
        addReactionRoleButtonRow();
    } else {
        buttons.forEach(btn => addReactionRoleButtonRow(btn));
    }
}

function startNewReactionRoleConfig() {
    activeReactionRoleId = generateReactionRoleConfigId();
    const channelInput = document.getElementById('rr-channel-id');
    const messageInput = document.getElementById('rr-message-id');
    const contentInput = document.getElementById('rr-content');

    renderReactionRoleChannelOptions('');
    if (channelInput) channelInput.value = '';
    if (messageInput) messageInput.value = '';
    if (contentInput) contentInput.value = 'Choose your roles below.';

    clearReactionRoleButtonRows();
    addReactionRoleButtonRow();
    showReactionRoleEditor();
    renderReactionRoleLiveList();
}

function showReactionRoleEditor() {
    const panel = document.getElementById('rr-editor-panel');
    const newBtn = document.getElementById('rr-new-config-btn');
    if (panel) {
        panel.style.display = '';
    }
    if (newBtn) {
        newBtn.style.display = 'none';
    }
}

function hideReactionRoleEditor() {
    const panel = document.getElementById('rr-editor-panel');
    const newBtn = document.getElementById('rr-new-config-btn');
    if (panel) {
        panel.style.display = 'none';
    }
    if (newBtn) {
        newBtn.style.display = '';
    }
}

function cancelReactionRoleEdit() {
    activeReactionRoleId = null;
    hideReactionRoleEditor();
    renderReactionRoleLiveList();
}

function renderReactionRoleLiveList() {
    const container = document.getElementById('rr-live-list');
    if (!container) return;

    const entries = Object.entries(reactionRoleConfigsById || {});
    if (entries.length === 0) {
        container.innerHTML = '<div style="color: var(--muted); font-size: 13px;">No live reaction roles yet. Click Send Message to create one.</div>';
        return;
    }

    const channelsById = Object.fromEntries((currentGuildChannels || []).map(c => [c.id, c.name]));

    container.innerHTML = entries.map(([configId, cfg]) => {
        const messageId = cfg?.messageId ? String(cfg.messageId) : 'Draft';
        const channelName = cfg?.channelId && channelsById[cfg.channelId] ? `#${channelsById[cfg.channelId]}` : (cfg?.channelId ? `#${cfg.channelId}` : 'No channel');
        const isActive = activeReactionRoleId === configId;
        const activeStyles = isActive
            ? 'border-color: var(--green); color: var(--green); background: rgba(74,222,128,.1);'
            : '';

        return `
            <div style="display:flex; gap:8px; align-items:center;">
                <button type="button" class="server-pill" data-rr-id="${sanitize(configId)}" style="${activeStyles}; flex:1;">Message ${sanitize(messageId)} in ${sanitize(channelName)}</button>
                <button type="button" class="btn-danger" data-rr-delete-id="${sanitize(configId)}">Delete</button>
            </div>
        `;
    }).join('');

    container.querySelectorAll('[data-rr-id]').forEach(btn => {
        btn.addEventListener('click', () => {
            const configId = btn.getAttribute('data-rr-id');
            if (configId) {
                loadReactionRoleTemplate(configId);
            }
        });
    });

    container.querySelectorAll('[data-rr-delete-id]').forEach(btn => {
        btn.addEventListener('click', async (event) => {
            event.stopPropagation();
            const configId = btn.getAttribute('data-rr-delete-id');
            if (configId) {
                await deleteReactionRoleConfig(configId);
            }
        });
    });
}

async function deleteReactionRoleConfig(configId) {
    if (!currentGuildId) {
        showToast('Select a server from the sidebar first.', 'error');
        return;
    }

    const config = reactionRoleConfigsById?.[configId];
    if (!config) {
        showToast('Reaction role entry not found.', 'error');
        return;
    }

    const messageLabel = config.messageId ? `message ${config.messageId}` : 'this reaction role';
    const confirmed = confirm(`Delete ${messageLabel} and remove it from live reaction roles?`);
    if (!confirmed) {
        return;
    }

    const token = localStorage.getItem('admin_session');
    try {
        const response = await fetch(`${API_BASE}/reaction-roles/${currentGuildId}/${encodeURIComponent(configId)}`, {
            method: 'DELETE',
            headers: { 'Authorization': token }
        });

        if (!response.ok) {
            const errText = await response.text();
            showToast(errText || 'Failed to delete reaction role.', 'error');
            return;
        }

        delete reactionRoleConfigsById[configId];
        if (activeReactionRoleId === configId) {
            cancelReactionRoleEdit();
        } else {
            renderReactionRoleLiveList();
        }

        showToast('Reaction role deleted successfully.');
    } catch (err) {
        showToast('Network error while deleting reaction role.', 'error');
    }
}

async function loadGuildMetadata() {
    if (!currentGuildId) return;

    const token = localStorage.getItem('admin_session');
    try {
        const response = await fetch(`${API_BASE}/guild-meta/${currentGuildId}`, {
            headers: { 'Authorization': token }
        });
        if (!response.ok) {
            return;
        }

        const data = await response.json();
        currentGuildChannels = Array.isArray(data.channels) ? data.channels : [];
        currentGuildRoles = Array.isArray(data.roles) ? data.roles : [];
    } catch (err) {
        currentGuildChannels = [];
        currentGuildRoles = [];
    }

    renderReactionRoleChannelOptions();
}

function renderReactionRoleChannelOptions(selectedChannelId = '') {
    const channelSelect = document.getElementById('rr-channel-id');
    if (!channelSelect) return;

    populateChannelSelect(channelSelect, selectedChannelId, { includeNotSet: false });
}

function getChannelsGroupedByCategory() {
    const grouped = new Map();
    (currentGuildChannels || []).forEach(channel => {
        const categoryName = channel.category || 'Uncategorized';
        if (!grouped.has(categoryName)) {
            grouped.set(categoryName, []);
        }
        grouped.get(categoryName).push(channel);
    });

    return Array.from(grouped.entries()).map(([category, channels]) => ({
        category,
        channels
    }));
}

function populateChannelSelect(select, selectedChannelId = '', options = {}) {
    const includeNotSet = !!options.includeNotSet;
    select.innerHTML = '';

    const topOption = document.createElement('option');
    topOption.value = '';
    topOption.textContent = includeNotSet ? 'Not set' : 'Select a channel...';
    select.appendChild(topOption);

    const groups = getChannelsGroupedByCategory();
    groups.forEach(group => {
        const optgroup = document.createElement('optgroup');
        optgroup.label = group.category;

        group.channels.forEach(channel => {
            const option = document.createElement('option');
            option.value = channel.id;
            option.textContent = `#${channel.name}`;
            optgroup.appendChild(option);
        });

        select.appendChild(optgroup);
    });

    if (selectedChannelId) {
        const exists = (currentGuildChannels || []).some(channel => channel.id === selectedChannelId);
        if (!exists) {
            const unknown = document.createElement('option');
            unknown.value = selectedChannelId;
            unknown.textContent = `Unknown channel (${selectedChannelId})`;
            select.appendChild(unknown);
        }
        select.value = selectedChannelId;
    }
}

async function saveReactionRoleConfig() {
    const configId = activeReactionRoleId;
    if (!configId) {
        showToast('Click New Reaction Role first.', 'error');
        return false;
    }
    const channelId = normalizeDiscordId(document.getElementById('rr-channel-id')?.value || '');
    const messageId = normalizeDiscordId(document.getElementById('rr-message-id')?.value || '');
    const content = document.getElementById('rr-content')?.value.trim() || '';
    const buttons = collectReactionRoleButtons();

    if (!channelId) {
        showToast('Please provide a valid channel ID.', 'error');
        return false;
    }
    if (!content) {
        showToast('Reaction role message content is required.', 'error');
        return false;
    }
    if (buttons.length === 0) {
        showToast('Add at least one button with label and role ID.', 'error');
        return false;
    }

    reactionRoleConfigsById[configId] = {
        templateId: configId,
        channelId,
        messageId,
        content,
        buttons
    };

    const ok = await postConfigUpdate(
        {
            reactionRoleConfigs: reactionRoleConfigsById
        },
        {
            successMessage: 'Reaction role draft saved!'
        }
    );

    if (ok) {
        renderReactionRoleLiveList();
    }

    return ok;
}

async function publishReactionRoleConfig() {
    const configId = activeReactionRoleId;
    if (!configId) {
        showToast('Click New Reaction Role first.', 'error');
        return;
    }

    const channelId = normalizeDiscordId(document.getElementById('rr-channel-id')?.value || '');
    const messageId = normalizeDiscordId(document.getElementById('rr-message-id')?.value || '');
    const content = document.getElementById('rr-content')?.value.trim() || '';
    const buttons = collectReactionRoleButtons();

    if (!channelId) {
        showToast('Please select a channel.', 'error');
        return;
    }
    if (!content) {
        showToast('Reaction role message content is required.', 'error');
        return;
    }
    if (buttons.length === 0) {
        showToast('Add at least one button with label and role.', 'error');
        return;
    }

    reactionRoleConfigsById[configId] = {
        templateId: configId,
        channelId,
        messageId,
        content,
        buttons
    };

    if (!currentGuildId) {
        showToast('Select a server from the sidebar first.', 'error');
        return;
    }

    const token = localStorage.getItem('admin_session');
    const publishBtn = document.getElementById('publish-reaction-btn');
    if (publishBtn) {
        publishBtn.innerText = 'Publishing...';
        publishBtn.disabled = true;
    }

    try {
        const saveRes = await fetch(`${API_BASE}/config/${currentGuildId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': token },
            body: JSON.stringify({ reactionRoleConfigs: reactionRoleConfigsById })
        });
        if (!saveRes.ok) {
            showToast('Failed to save reaction role config.', 'error');
            return;
        }

        const response = await fetch(`${API_BASE}/reaction-roles/${currentGuildId}/publish?templateId=${encodeURIComponent(configId)}`, {
            method: 'POST',
            headers: { 'Authorization': token }
        });

        if (!response.ok) {
            const errText = await response.text();
            showToast(errText || 'Failed to publish reaction role message.', 'error');
            return;
        }

        const result = await response.json();
        const updatedMessageId = (result && result.messageId) ? String(result.messageId) : '';
        if (updatedMessageId) {
            const rrMessageIdInput = document.getElementById('rr-message-id');
            if (rrMessageIdInput) rrMessageIdInput.value = updatedMessageId;
            if (reactionRoleConfigsById[configId]) {
                reactionRoleConfigsById[configId].messageId = updatedMessageId;
            }

            await fetch(`${API_BASE}/config/${currentGuildId}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Authorization': token },
                body: JSON.stringify({ reactionRoleConfigs: reactionRoleConfigsById })
            });
        }

        showToast(`Reaction role message ${result.action === 'updated' ? 'updated' : 'sent'} successfully!`);
        cancelReactionRoleEdit();
    } catch (err) {
        showToast('Network error while publishing reaction role message.', 'error');
    } finally {
        if (publishBtn) {
            publishBtn.innerText = 'Send Message';
            publishBtn.disabled = false;
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
            const titleText = replaceTemplateTokens(
                title,
                {
                    ...(config.variableMocks || {}),
                    ...(config.titleMocks || {})
                }
            );
            prevTitle.style.display = 'block';
            prevTitle.innerText = titleText;
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
            const footerText = replaceTemplateTokens(
                footerRaw,
                {
                    ...(config.variableMocks || {}),
                    ...(config.footerMocks || {})
                }
            );
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