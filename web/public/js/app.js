import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signOut,
  GoogleAuthProvider,
  signInWithPopup,
  sendPasswordResetEmail
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  deleteDoc,
  query,
  where,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

import { FIREBASE_CONFIG, DEFAULT_MINISTRY_ROLES, MUSICAL_KEYS } from "./config.js";
import {
  escapeHtml,
  generateTeamCode,
  evaluatePassword,
  validateEmailForAuth,
  hashPasswordPbkdf2,
  verifyPasswordPbkdf2,
  isWorshipLeader,
  hasLeaderPrivileges,
  uploadImageToCloudinary,
  extractYoutubeVideoId,
  transposeSingleNote,
  renderFormattedChordSheet,
  MetronomeEngine
} from "./utils.js";
import {
  getAvatarHtml,
  renderHomeView,
  renderScheduleView,
  renderSongsView,
  renderRosterView,
  renderChatView,
  renderProfileView
} from "./views.js";

// Initialize Firebase
const firebaseApp = initializeApp(FIREBASE_CONFIG);
const auth = getAuth(firebaseApp);
const db = getFirestore(firebaseApp);
const googleProvider = new GoogleAuthProvider();

// Central Reactive State
const state = {
  authLoading: true,
  authUser: null,
  currentUser: null,
  currentTeam: null,
  activeTab: "home", // home | schedule | songs | roster | chat | profile
  authMode: "login", // login | register | reset
  songSearchQuery: "",
  teamMembers: [],
  services: [],
  songs: [],
  announcements: [],
  substitutions: [],
  messages: [],
  rejoinRequests: [],
  chatReplyTarget: null,
  chatEditingMessage: null,
  stageModal: {
    songId: null,
    semitones: 0,
    lyricsOnly: false,
    fontSize: 15,
    autoScrollSpeed: 0,
    scrollTimer: null
  }
};

let unsubUserDoc = null;
let unsubTeamDoc = null;
let unsubMembers = null;
let unsubServices = null;
let unsubSongs = null;
let unsubAnnouncements = null;
let unsubSubstitutions = null;
let unsubMessages = null;
let unsubRejoinRequests = null;

// ============================================================================
// Firestore Schema Helpers (100% compliant with firestore.rules)
// ============================================================================

function normalizeUserDoc(uid, raw = {}) {
  const fullName = String(raw.fullName || raw.name || raw.username || "Member").trim().slice(0, 100);
  const username = String(raw.username || fullName.split(/\s+/)[0] || "Member").trim().slice(0, 50);
  const email = String(raw.email || "").trim().slice(0, 100);
  const role = ["Leader", "Member", ""].includes(raw.role) ? raw.role : "Member";
  const teamCode = String(raw.teamCode || raw.teamId || "").trim().toUpperCase().slice(0, 20);
  const teamName = String(raw.teamName || "").trim().slice(0, 100);
  const ministryRole = String(raw.ministryRole || "Member").trim().slice(0, 50);
  const photoUri = String(raw.photoUri || raw.profilePictureUrl || "").trim().slice(0, 500);
  const unavailableRanges = Array.isArray(raw.unavailableRanges)
    ? raw.unavailableRanges
    : Array.isArray(raw.unavailableDates)
    ? raw.unavailableDates
    : [];

  return {
    uid,
    username,
    email,
    role,
    teamName,
    teamCode,
    resetPassword: String(raw.resetPassword || raw.password || "").slice(0, 128),
    fullName,
    phone: String(raw.phone || "").slice(0, 25),
    country: String(raw.country || "Philippines").slice(0, 50),
    language: String(raw.language || "English").slice(0, 50),
    ministryRole,
    unavailableRanges,
    notificationsEnabled: typeof raw.notificationsEnabled === "boolean" ? raw.notificationsEnabled : true,
    teamUnmuted: typeof raw.teamUnmuted === "boolean" ? raw.teamUnmuted : true,
    fcmToken: String(raw.fcmToken || "").slice(0, 500),
    photoUri,
    removedByLeader: Boolean(raw.removedByLeader),
    removedTeamCode: String(raw.removedTeamCode || "").slice(0, 128),
    createdAt: raw.createdAt !== undefined ? raw.createdAt : Date.now(),
    joinedAt: typeof raw.joinedAt === "number" ? raw.joinedAt : Date.now(),
    // Convenience aliases for views
    name: fullName
  };
}

/**
 * Writes a strictly validated `/users/{uid}` document that satisfies `isValidUser`
 * and `areImmutableFieldsUnchanged(['uid', 'createdAt'])` in `firestore.rules`.
 */
async function saveCleanUserProfile(uid, updates = {}) {
  const userRef = doc(db, "users", uid);
  const snap = await getDoc(userRef);
  const existing = snap.exists() ? snap.data() : {};
  const merged = { ...existing, ...updates };
  const normalized = normalizeUserDoc(uid, merged);

  const cleanPayload = {
    uid,
    username: normalized.username,
    email: normalized.email,
    role: normalized.role,
    teamName: normalized.teamName,
    teamCode: normalized.teamCode,
    resetPassword: normalized.resetPassword,
    fullName: normalized.fullName,
    phone: normalized.phone,
    country: normalized.country,
    language: normalized.language,
    ministryRole: normalized.ministryRole,
    unavailableRanges: normalized.unavailableRanges.slice(0, 100),
    notificationsEnabled: normalized.notificationsEnabled,
    teamUnmuted: normalized.teamUnmuted,
    fcmToken: normalized.fcmToken,
    photoUri: normalized.photoUri,
    removedByLeader: normalized.removedByLeader,
    removedTeamCode: normalized.removedTeamCode,
    joinedAt: normalized.joinedAt
  };

  if (existing.createdAt !== undefined) {
    cleanPayload.createdAt = existing.createdAt;
  } else {
    cleanPayload.createdAt = Date.now();
  }

  await setDoc(userRef, cleanPayload);
  return cleanPayload;
}

/**
 * Writes a strictly validated `/teams/{teamCode}` document that satisfies `isValidTeam`
 * and `areImmutableFieldsUnchanged(['teamCode', 'createdAt'])` in `firestore.rules`.
 */
async function saveCleanTeamDoc(teamCode, updates = {}) {
  const cleanCode = String(teamCode).trim().toUpperCase();
  const teamRef = doc(db, "teams", cleanCode);
  const snap = await getDoc(teamRef);
  const existing = snap.exists() ? snap.data() : {};
  const merged = { ...existing, ...updates };

  const cleanPayload = {
    teamCode: cleanCode,
    teamName: String(merged.teamName || merged.name || "Worship Ministry").trim().slice(0, 100),
    leaderUid: String(merged.leaderUid || merged.leaderId || state.currentUser?.uid || "").trim().slice(0, 128),
    leaderName: String(merged.leaderName || state.currentUser?.fullName || "Worship Leader").trim().slice(0, 100),
    leaderEmail: String(merged.leaderEmail || state.currentUser?.email || "").trim().slice(0, 120),
    roles: Array.isArray(merged.roles) ? merged.roles.slice(0, 50) : DEFAULT_MINISTRY_ROLES,
    removedMembers: Array.isArray(merged.removedMembers) ? merged.removedMembers.slice(0, 200) : [],
    isDeleted: Boolean(merged.isDeleted),
    status: String(merged.status || "active").slice(0, 50)
  };

  if (existing.createdAt !== undefined) {
    cleanPayload.createdAt = existing.createdAt;
  } else {
    cleanPayload.createdAt = Date.now();
  }

  await setDoc(teamRef, cleanPayload);
  return cleanPayload;
}

const metronome = new MetronomeEngine((beat, isAccent) => {
  const indicator = document.getElementById("metronome-beat-dot");
  if (!indicator) return;
  if (beat === 0) {
    indicator.className = "w-2.5 h-2.5 rounded-full bg-slate-300 transition-all";
  } else if (isAccent) {
    indicator.className = "w-2.5 h-2.5 rounded-full bg-amber-500 scale-125 transition-all";
  } else {
    indicator.className = "w-2.5 h-2.5 rounded-full bg-indigo-600 scale-110 transition-all";
  }
});

// Toast Notification Helper
export function showToast(message, type = "info") {
  const container = document.getElementById("toast-container");
  if (!container) return;

  const borderMap = {
    success: "border-l-4 border-l-emerald-600",
    error: "border-l-4 border-l-rose-600",
    warning: "border-l-4 border-l-amber-500",
    info: "border-l-4 border-l-indigo-600"
  };
  const iconMap = {
    success: "fa-circle-check text-emerald-600",
    error: "fa-circle-exclamation text-rose-600",
    warning: "fa-triangle-exclamation text-amber-500",
    info: "fa-circle-info text-indigo-600"
  };

  const toast = document.createElement("div");
  toast.className = `pointer-events-auto flex items-center gap-3 px-4 py-3 rounded-md bg-white border border-slate-200 ${borderMap[type] || borderMap.info} text-slate-800 shadow-md text-xs font-medium animate-fade-in`;
  toast.innerHTML = `
    <i class="fa-solid ${iconMap[type] || iconMap.info} text-sm shrink-0"></i>
    <span class="flex-1 leading-snug">${escapeHtml(message)}</span>
  `;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = "0";
    toast.style.transition = "opacity 0.2s ease";
    setTimeout(() => toast.remove(), 220);
  }, 3600);
}

// Modal Helper
function openModal(htmlContent, maxWidthClass = "max-w-lg") {
  const modalRoot = document.getElementById("modal-root");
  if (!modalRoot) return;
  modalRoot.classList.remove("hidden");
  modalRoot.classList.add("flex");
  modalRoot.innerHTML = `
    <div class="modal-card w-full ${maxWidthClass} rounded-lg p-6 text-slate-900 animate-fade-in my-auto max-h-[92vh] overflow-y-auto" onclick="event.stopPropagation()">
      ${htmlContent}
    </div>
  `;
  modalRoot.onclick = () => closeModal();
}

function closeModal() {
  if (state.stageModal.scrollTimer) {
    clearInterval(state.stageModal.scrollTimer);
    state.stageModal.scrollTimer = null;
  }
  metronome.stop();
  const modalRoot = document.getElementById("modal-root");
  if (!modalRoot) return;
  modalRoot.classList.add("hidden");
  modalRoot.classList.remove("flex");
  modalRoot.innerHTML = "";
}

// Cleanup Firestore Listeners
function clearTeamListeners() {
  [unsubTeamDoc, unsubMembers, unsubServices, unsubSongs, unsubAnnouncements, unsubSubstitutions, unsubMessages, unsubRejoinRequests].forEach((fn) => {
    if (typeof fn === "function") fn();
  });
  unsubTeamDoc = unsubMembers = unsubServices = unsubSongs = unsubAnnouncements = unsubSubstitutions = unsubMessages = unsubRejoinRequests = null;
  state.currentTeam = null;
  state.teamMembers = [];
  state.services = [];
  state.songs = [];
  state.announcements = [];
  state.substitutions = [];
  state.messages = [];
  state.rejoinRequests = [];
}

// Subscribe to Current Team & All Subcollections under /teams/{teamCode}/...
function subscribeToTeamWorkspace(teamCode) {
  clearTeamListeners();
  const code = String(teamCode || "").trim().toUpperCase();
  if (!code || code.length !== 6) {
    renderApp();
    return;
  }

  unsubTeamDoc = onSnapshot(doc(db, "teams", code), (snap) => {
    if (!snap.exists() || snap.data()?.isDeleted === true) {
      state.currentTeam = {
        teamCode: code,
        teamName: state.currentUser?.teamName || "Worship Ministry"
      };
      renderApp();
      return;
    }
    state.currentTeam = { teamCode: snap.id, ...snap.data() };
    renderApp();
  });

  unsubMembers = onSnapshot(
    query(collection(db, "users"), where("teamCode", "==", code)),
    (snap) => {
      const list = snap.docs
        .map((d) => normalizeUserDoc(d.id, d.data()))
        .filter((m) => !m.removedByLeader);
      list.sort((a, b) => {
        const aLeader = isWorshipLeader(a.ministryRole, a.role) ? 1 : 0;
        const bLeader = isWorshipLeader(b.ministryRole, b.role) ? 1 : 0;
        if (aLeader !== bLeader) return bLeader - aLeader;
        return String(a.fullName).localeCompare(String(b.fullName));
      });
      state.teamMembers = list;
      renderApp();
    }
  );

  unsubServices = onSnapshot(
    collection(db, "teams", code, "services"),
    (snap) => {
      const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      list.sort((a, b) => String(a.date || "").localeCompare(String(b.date || "")));
      state.services = list;
      renderApp();
    }
  );

  unsubSongs = onSnapshot(
    collection(db, "teams", code, "songs"),
    (snap) => {
      const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      list.sort((a, b) => String(a.title || "").localeCompare(String(b.title || "")));
      state.songs = list;
      renderApp();
    }
  );

  unsubAnnouncements = onSnapshot(
    collection(db, "teams", code, "announcements"),
    (snap) => {
      const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      list.sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
      state.announcements = list;
      renderApp();
    }
  );

  unsubSubstitutions = onSnapshot(
    collection(db, "teams", code, "substitutions"),
    (snap) => {
      const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      list.sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
      state.substitutions = list;
      renderApp();
    }
  );

  unsubMessages = onSnapshot(
    collection(db, "teams", code, "messages"),
    (snap) => {
      const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      list.sort((a, b) => Number(a.timestamp || 0) - Number(b.timestamp || 0));
      state.messages = list;
      renderApp();
      if (state.activeTab === "chat") {
        setTimeout(() => {
          const scroller = document.getElementById("chat-messages-scroll");
          if (scroller) scroller.scrollTop = scroller.scrollHeight;
        }, 40);
      }
    }
  );

  unsubRejoinRequests = onSnapshot(
    collection(db, "teams", code, "rejoin_requests"),
    (snap) => {
      state.rejoinRequests = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      renderApp();
    }
  );
}

// Auth State Observer
onAuthStateChanged(auth, async (fbUser) => {
  if (unsubUserDoc) {
    unsubUserDoc();
    unsubUserDoc = null;
  }
  clearTeamListeners();

  if (!fbUser) {
    state.authLoading = false;
    state.authUser = null;
    state.currentUser = null;
    renderApp();
    return;
  }

  state.authUser = fbUser;
  const userRef = doc(db, "users", fbUser.uid);

  // Ensure user document exists & matches firestore.rules schema
  try {
    const initialSnap = await getDoc(userRef);
    if (!initialSnap.exists()) {
      const defaultName = fbUser.displayName || (fbUser.email ? fbUser.email.split("@")[0] : "Member");
      await saveCleanUserProfile(fbUser.uid, {
        uid: fbUser.uid,
        fullName: defaultName,
        username: defaultName.split(/\s+/)[0] || "Member",
        email: fbUser.email || "",
        role: "Member",
        ministryRole: "Member",
        teamCode: "",
        teamName: "",
        photoUri: fbUser.photoURL || "",
        unavailableRanges: []
      });
    } else {
      // Automatically heal any legacy web fields (e.g., name / teamId) so future updates never fail firestore.rules
      const raw = initialSnap.data();
      if ("name" in raw || "teamId" in raw || "profilePictureUrl" in raw || "unavailableDates" in raw || "password" in raw) {
        await saveCleanUserProfile(fbUser.uid, raw);
      }
    }
  } catch (err) {
    console.warn("Initial profile sync check:", err);
  }

  let activeSubscribedTeamCode = null;
  unsubUserDoc = onSnapshot(userRef, (snap) => {
    state.authLoading = false;
    if (!snap.exists()) return;
    const normalized = normalizeUserDoc(snap.id, snap.data());
    state.currentUser = normalized;

    const nextTeamCode = (!normalized.removedByLeader && normalized.teamCode) ? normalized.teamCode : "";
    if (nextTeamCode !== activeSubscribedTeamCode) {
      activeSubscribedTeamCode = nextTeamCode;
      if (nextTeamCode) {
        subscribeToTeamWorkspace(nextTeamCode);
      } else {
        clearTeamListeners();
        renderApp();
      }
    } else {
      renderApp();
    }
  });
});

// Render Root Application
function renderApp() {
  const appEl = document.getElementById("app");
  if (!appEl) return;

  // Preserve active input focus for song search or chat input
  const activeId = document.activeElement?.id;
  const selectionStart = document.activeElement?.selectionStart;

  if (state.authLoading) {
    appEl.innerHTML = renderLoadingState();
    return;
  }

  if (!state.authUser || !state.currentUser) {
    appEl.innerHTML = renderAuthView();
    return;
  }

  if (!state.currentUser.teamCode || state.currentUser.removedByLeader) {
    appEl.innerHTML = renderTeamSetupView();
    return;
  }

  appEl.innerHTML = renderDashboardShell();

  if (activeId) {
    const restored = document.getElementById(activeId);
    if (restored) {
      restored.focus();
      if (typeof selectionStart === "number" && restored.setSelectionRange) {
        try {
          restored.setSelectionRange(selectionStart, selectionStart);
        } catch {
          // ignore
        }
      }
    }
  }
}

function renderLoadingState() {
  return `
    <div class="min-h-screen flex flex-col items-center justify-center p-6 text-center bg-[#F8FAFC]">
      <div class="w-12 h-12 rounded-lg bg-white border border-slate-200 shadow-xs flex items-center justify-center mb-4">
        <img src="/assets/logo.png" alt="Synced N" class="w-8 h-8 object-contain" />
      </div>
      <div class="flex items-center gap-2.5 text-slate-700 font-medium text-sm">
        <i class="fa-solid fa-circle-notch fa-spin text-indigo-600 text-xs"></i>
        <span>Synchronizing ministry workspace...</span>
      </div>
    </div>
  `;
}

// Authentication Screen (Login / Register / Password Reset)
function renderAuthView() {
  const mode = state.authMode;
  return `
    <div class="min-h-screen flex flex-col justify-center items-center p-4 sm:p-6 bg-[#F8FAFC]">
      <div class="w-full max-w-md space-y-6 animate-fade-in">
        <!-- Brand Header -->
        <div class="text-center space-y-2">
          <div class="inline-flex items-center justify-center w-12 h-12 rounded-lg bg-white border border-slate-200 shadow-xs">
            <img src="/assets/logo.png" alt="Synced N" class="w-8 h-8 object-contain" />
          </div>
          <h1 class="text-2xl font-semibold text-slate-900 tracking-tight">Synced N</h1>
          <p class="text-xs text-slate-500">Church Music Ministry Schedule & Lineup Management</p>
        </div>

        <!-- Auth Card -->
        <div class="surface-card p-6 sm:p-7 space-y-5">
          <!-- Mode Switcher -->
          <div class="grid grid-cols-2 p-1 bg-slate-100 rounded-md border border-slate-200 text-xs font-semibold">
            <button onclick="window.appActions.setAuthMode('login')"
              class="py-1.5 rounded transition ${mode === "login" ? "bg-white text-slate-900 shadow-2xs" : "text-slate-600 hover:text-slate-900"}">
              Sign In
            </button>
            <button onclick="window.appActions.setAuthMode('register')"
              class="py-1.5 rounded transition ${mode === "register" ? "bg-white text-slate-900 shadow-2xs" : "text-slate-600 hover:text-slate-900"}">
              Create Account
            </button>
          </div>

          ${mode === "login" ? `
            <form onsubmit="window.appActions.handleLogin(event)" class="space-y-4">
              <div>
                <label class="form-label">Email Address</label>
                <input id="login-email" type="email" required placeholder="you@church.org" class="form-input" />
              </div>
              <div>
                <div class="flex items-center justify-between mb-1">
                  <label class="form-label mb-0">Password</label>
                  <button type="button" onclick="window.appActions.setAuthMode('reset')" class="text-xs font-medium text-indigo-700 hover:underline">
                    Forgot password?
                  </button>
                </div>
                <input id="login-password" type="password" required placeholder="••••••••" class="form-input" />
              </div>
              <button id="auth-submit-btn" type="submit" class="w-full btn btn-primary py-2.5">
                Sign In to Workspace
              </button>
            </form>
          ` : mode === "register" ? `
            <form onsubmit="window.appActions.handleRegister(event)" class="space-y-4">
              <div>
                <label class="form-label">Full Name</label>
                <input id="reg-name" type="text" required maxlength="60" placeholder="e.g. Joshua Lagare" class="form-input" />
              </div>
              <div>
                <label class="form-label">Email Address</label>
                <input id="reg-email" type="email" required maxlength="60" placeholder="you@church.org" class="form-input" />
              </div>
              <div>
                <label class="form-label">Primary Ministry Role</label>
                <select id="reg-role" class="form-select">
                  ${DEFAULT_MINISTRY_ROLES.map((r) => `<option value="${escapeHtml(r)}">${escapeHtml(r)}</option>`).join("")}
                </select>
              </div>
              <div>
                <label class="form-label">Password</label>
                <input id="reg-password" type="password" required placeholder="8+ chars, upper, lower, number, symbol" class="form-input" />
                <p class="text-[11px] text-slate-500 mt-1">Must include at least 8 characters, uppercase, lowercase, number, and symbol.</p>
              </div>
              <button id="auth-submit-btn" type="submit" class="w-full btn btn-primary py-2.5">
                Create Ministry Account
              </button>
            </form>
          ` : `
            <form onsubmit="window.appActions.handlePasswordReset(event)" class="space-y-4">
              <p class="text-xs text-slate-600 leading-relaxed">Enter your registered email address and we will send a password reset link.</p>
              <div>
                <label class="form-label">Email Address</label>
                <input id="reset-email" type="email" required placeholder="you@church.org" class="form-input" />
              </div>
              <button id="auth-submit-btn" type="submit" class="w-full btn btn-primary py-2.5">
                Send Password Reset Link
              </button>
              <button type="button" onclick="window.appActions.setAuthMode('login')" class="w-full btn btn-ghost text-xs">
                Back to Sign In
              </button>
            </form>
          `}

          <div class="relative flex py-1 items-center">
            <div class="flex-grow border-t border-slate-200"></div>
            <span class="flex-shrink mx-3 text-[11px] uppercase tracking-wider text-slate-400 font-medium">or continue with</span>
            <div class="flex-grow border-t border-slate-200"></div>
          </div>

          <button onclick="window.appActions.handleGoogleSignIn()" class="w-full btn btn-secondary py-2.5">
            <i class="fa-brands fa-google text-slate-700"></i>
            <span>Continue with Google</span>
          </button>
        </div>
      </div>
    </div>
  `;
}

// Team Setup View (Join with 6-character code or Create a new Worship Team)
function renderTeamSetupView() {
  const user = state.currentUser;
  const displayName = user?.fullName || user?.name || "Member";

  return `
    <div class="min-h-screen flex flex-col justify-center items-center p-4 sm:p-6 bg-[#F8FAFC]">
      <div class="w-full max-w-3xl space-y-6 animate-fade-in">
        <!-- Top Bar -->
        <div class="surface-card p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div class="flex items-center gap-3.5">
            <div class="w-10 h-10 rounded-md bg-slate-100 border border-slate-200 flex items-center justify-center shrink-0">
              <img src="/assets/logo.png" alt="Synced N" class="w-6 h-6 object-contain" />
            </div>
            <div>
              <h1 class="text-lg font-semibold text-slate-900">Welcome, ${escapeHtml(displayName)}</h1>
              <p class="text-xs text-slate-500">Connect to your church worship ministry workspace to access schedules and song lineups.</p>
            </div>
          </div>
          <button onclick="window.appActions.signOutUser()" class="btn btn-secondary btn-sm self-start sm:self-auto">
            <i class="fa-solid fa-right-from-bracket text-xs"></i> Sign Out
          </button>
        </div>

        ${user?.removedByLeader && user?.removedTeamCode ? `
          <div class="surface-card border-amber-200 bg-amber-50/70 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div class="text-xs text-amber-900">
              <strong>Notice:</strong> You were previously removed from team <span class="font-mono font-semibold">${escapeHtml(user.removedTeamCode)}</span>. You can send a rejoin request to the Worship Leader or join/create another team below.
            </div>
            <button onclick="window.appActions.submitRejoinRequest('${escapeHtml(user.removedTeamCode)}')" class="btn btn-primary btn-sm shrink-0">
              Request to Rejoin ${escapeHtml(user.removedTeamCode)}
            </button>
          </div>
        ` : ""}

        <!-- Two-Column Onboarding Options -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <!-- Join Existing Team -->
          <div class="surface-card p-6 flex flex-col justify-between space-y-5">
            <div class="space-y-2">
              <span class="badge badge-indigo">Join Existing Ministry</span>
              <h2 class="text-base font-semibold text-slate-900">Enter Team Invite Code</h2>
              <p class="text-xs text-slate-500 leading-relaxed">
                Ask your Worship Leader for your church's 6-character invite code to sync with the team schedule and song library.
              </p>
            </div>
            <form onsubmit="window.appActions.handleJoinTeam(event)" class="space-y-3">
              <div>
                <label class="form-label">6-Character Invite Code</label>
                <input
                  id="join-team-code"
                  type="text"
                  required
                  maxlength="6"
                  placeholder="ABC123"
                  class="form-input uppercase tracking-widest font-mono text-center text-base font-semibold"
                />
              </div>
              <button id="join-team-btn" type="submit" class="w-full btn btn-indigo py-2.5">
                Join Worship Team
              </button>
            </form>
          </div>

          <!-- Create New Team -->
          <div class="surface-card p-6 flex flex-col justify-between space-y-5">
            <div class="space-y-2">
              <span class="badge badge-neutral">For Worship Leaders</span>
              <h2 class="text-base font-semibold text-slate-900">Create a New Team Workspace</h2>
              <p class="text-xs text-slate-500 leading-relaxed">
                Start a new workspace for your church music ministry and generate an invite code for your musicians and vocalists.
              </p>
            </div>
            <form onsubmit="window.appActions.handleCreateTeam(event)" class="space-y-3">
              <div>
                <label class="form-label">Ministry / Team Name</label>
                <input
                  id="create-team-name"
                  type="text"
                  required
                  maxlength="60"
                  placeholder="e.g. Grace Fellowship Worship"
                  class="form-input"
                />
              </div>
              <button id="create-team-btn" type="submit" class="w-full btn btn-primary py-2.5">
                Create Workspace
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  `;
}

// Main Dashboard Shell (Sidebar + Top Header + Page Content + Mobile Nav)
function renderDashboardShell() {
  const { currentUser, currentTeam, activeTab, substitutions, rejoinRequests } = state;
  const isLeader = hasLeaderPrivileges(currentUser);
  const pendingActionCount = isLeader
    ? substitutions.filter((s) => s.status === "Pending" || s.status === "Substitute Accepted").length +
      rejoinRequests.filter((r) => r.status === "Pending").length
    : 0;
  const teamDisplayName = currentTeam?.teamName || currentUser?.teamName || "Workspace";
  const teamCode = currentTeam?.teamCode || currentUser?.teamCode || "------";
  const userDisplayName = currentUser?.fullName || currentUser?.name || "Member";

  const navItems = [
    { id: "home", label: "Dashboard", icon: "fa-solid fa-table-columns" },
    { id: "schedule", label: "Schedule & Lineups", icon: "fa-regular fa-calendar-check", badge: pendingActionCount || 0 },
    { id: "songs", label: "Song Library", icon: "fa-solid fa-music" },
    { id: "roster", label: "Team Roster", icon: "fa-solid fa-users" },
    { id: "chat", label: "Team Chat", icon: "fa-regular fa-comments" },
    { id: "profile", label: "Account Profile", icon: "fa-regular fa-user" }
  ];

  let mainViewHtml = "";
  if (activeTab === "home") mainViewHtml = renderHomeView(state);
  else if (activeTab === "schedule") mainViewHtml = renderScheduleView(state);
  else if (activeTab === "songs") mainViewHtml = renderSongsView(state);
  else if (activeTab === "roster") mainViewHtml = renderRosterView(state);
  else if (activeTab === "chat") mainViewHtml = renderChatView(state);
  else if (activeTab === "profile") mainViewHtml = renderProfileView(state);

  return `
    <div class="min-h-screen flex flex-col md:flex-row bg-[#F8FAFC]">
      <!-- Desktop Fixed Sidebar -->
      <aside class="hidden md:flex md:w-60 md:flex-col md:fixed md:inset-y-0 bg-white border-r border-slate-200 z-30">
        <!-- Brand Header -->
        <div class="h-14 px-4 flex items-center justify-between border-b border-slate-200">
          <div class="flex items-center gap-2.5 min-w-0">
            <img src="/assets/logo.png" alt="Synced N" class="w-7 h-7 rounded object-contain shrink-0" />
            <div class="truncate">
              <div class="text-sm font-semibold text-slate-900 leading-none">Synced N</div>
              <div class="text-[11px] text-slate-500 truncate mt-0.5">${escapeHtml(teamDisplayName)}</div>
            </div>
          </div>
        </div>

        <!-- Navigation Links -->
        <nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          <div class="px-2.5 pb-1.5 text-[10px] font-semibold uppercase tracking-wider text-slate-400">
            Navigation
          </div>
          ${navItems.map((item) => {
            const active = activeTab === item.id;
            return `
              <button
                onclick="window.appActions.switchTab('${item.id}')"
                class="w-full flex items-center justify-between px-2.5 py-2 rounded-md text-xs font-medium transition ${
                  active
                    ? "bg-slate-900 text-white"
                    : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                }"
              >
                <div class="flex items-center gap-2.5">
                  <i class="${item.icon} w-4 text-center ${active ? "text-white" : "text-slate-400"}"></i>
                  <span>${escapeHtml(item.label)}</span>
                </div>
                ${item.badge ? `
                  <span class="px-1.5 py-0.2 rounded text-[10px] font-semibold ${active ? "bg-amber-400 text-slate-950" : "bg-amber-100 text-amber-800"}">
                    ${item.badge}
                  </span>
                ` : ""}
              </button>
            `;
          }).join("")}
        </nav>

        <!-- Sidebar Footer: Invite Code + User Summary -->
        <div class="p-3 border-t border-slate-200 space-y-2.5 bg-slate-50/60">
          <div class="flex items-center justify-between px-2 py-1.5 bg-white border border-slate-200 rounded-md">
            <div>
              <div class="text-[10px] uppercase tracking-wider text-slate-400 font-semibold">Team Code</div>
              <div class="font-mono text-xs font-semibold text-slate-800 tracking-widest">${escapeHtml(teamCode)}</div>
            </div>
            <button onclick="window.appActions.copyTeamCode()" class="btn btn-ghost btn-sm text-xs" title="Copy Invite Code">
              <i class="fa-regular fa-copy"></i>
            </button>
          </div>

          <div class="flex items-center justify-between gap-2 px-1">
            <button onclick="window.appActions.switchTab('profile')" class="flex items-center gap-2.5 min-w-0 text-left flex-1 hover:opacity-80 transition">
              ${getAvatarHtml(currentUser, "w-8 h-8", "text-xs")}
              <div class="min-w-0 flex-1">
                <div class="text-xs font-semibold text-slate-900 truncate">${escapeHtml(userDisplayName)}</div>
                <div class="text-[11px] text-slate-500 truncate">${escapeHtml(currentUser.ministryRole || "Member")}</div>
              </div>
            </button>
            <button onclick="window.appActions.signOutUser()" class="btn btn-ghost btn-sm text-slate-500 hover:text-rose-600" title="Sign Out">
              <i class="fa-solid fa-right-from-bracket text-xs"></i>
            </button>
          </div>
        </div>
      </aside>

      <!-- Mobile Top Bar -->
      <header class="md:hidden sticky top-0 z-30 h-14 bg-white border-b border-slate-200 px-4 flex items-center justify-between">
        <div class="flex items-center gap-2.5 min-w-0">
          <img src="/assets/logo.png" alt="Synced N" class="w-7 h-7 rounded object-contain shrink-0" />
          <div class="truncate">
            <div class="text-sm font-semibold text-slate-900 leading-none truncate">${escapeHtml(teamDisplayName)}</div>
            <div class="text-[10px] text-slate-500 font-mono">Code: ${escapeHtml(teamCode)}</div>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <button onclick="window.appActions.switchTab('profile')" class="p-0.5 rounded-full">
            ${getAvatarHtml(currentUser, "w-7 h-7", "text-[10px]")}
          </button>
        </div>
      </header>

      <!-- Main Workspace Container -->
      <main class="flex-1 md:pl-60 pb-20 md:pb-10">
        <div class="max-w-6xl mx-auto p-4 sm:p-6 lg:p-8">
          ${mainViewHtml}
        </div>
      </main>

      <!-- Mobile Bottom Navigation -->
      <nav class="md:hidden fixed bottom-0 inset-x-0 z-30 bg-white border-t border-slate-200 grid grid-cols-6 py-1 px-1">
        ${navItems.map((item) => {
          const active = activeTab === item.id;
          return `
            <button
              onclick="window.appActions.switchTab('${item.id}')"
              class="flex flex-col items-center justify-center py-1.5 rounded text-[10px] font-medium transition ${
                active ? "text-indigo-700 font-semibold" : "text-slate-500"
              }"
            >
              <i class="${item.icon} text-xs mb-0.5"></i>
              <span class="truncate max-w-[54px]">${escapeHtml(item.label.split(" ")[0])}</span>
            </button>
          `;
        }).join("")}
      </nav>
    </div>
  `;
}

// ============================================================================
// Global User Actions (`window.appActions`)
// ============================================================================
window.appActions = {
  closeModal,

  setAuthMode(mode) {
    state.authMode = mode;
    renderApp();
  },

  switchTab(tabId) {
    state.activeTab = tabId;
    renderApp();
    window.scrollTo({ top: 0, behavior: "smooth" });
  },

  // --- Authentication Handlers ---
  async handleLogin(e) {
    e.preventDefault();
    const email = document.getElementById("login-email")?.value.trim() || "";
    const password = document.getElementById("login-password")?.value || "";
    const emailErr = validateEmailForAuth(email);
    if (emailErr) return showToast(emailErr, "error");

    const btn = document.getElementById("auth-submit-btn");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Signing in...";
    }

    try {
      await signInWithEmailAndPassword(auth, email, password);
      showToast("Signed in to Synced N.", "success");
    } catch (err) {
      // Fallback check for custom PBKDF2 hash stored in Firestore
      try {
        const q = query(collection(db, "users"), where("email", "==", email));
        const snap = await getDocs(q);
        if (!snap.empty) {
          const userDoc = snap.docs[0].data();
          const storedHash = userDoc.resetPassword || userDoc.password;
          if (storedHash) {
            const matches = await verifyPasswordPbkdf2(password, storedHash);
            if (matches) {
              await sendPasswordResetEmail(auth, email);
              showToast("Password verified. Please check your email link to synchronize Firebase Auth.", "info");
              return;
            }
          }
        }
      } catch {
        // Ignore fallback error
      }
      showToast(err.message || "Invalid email or password.", "error");
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "Sign In to Workspace";
      }
    }
  },

  async handleRegister(e) {
    e.preventDefault();
    const fullName = document.getElementById("reg-name")?.value.trim() || "";
    const email = document.getElementById("reg-email")?.value.trim() || "";
    const ministryRole = document.getElementById("reg-role")?.value || "Member";
    const password = document.getElementById("reg-password")?.value || "";

    if (fullName.length < 2) return showToast("Enter your full name.", "error");
    const emailErr = validateEmailForAuth(email);
    if (emailErr) return showToast(emailErr, "error");

    const pwdEval = evaluatePassword(password);
    if (!pwdEval.isStrong) return showToast(pwdEval.errorMessage, "error");

    const btn = document.getElementById("auth-submit-btn");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Creating account...";
    }

    try {
      const cred = await createUserWithEmailAndPassword(auth, email, password);
      const hashedPassword = await hashPasswordPbkdf2(password);
      const isLeaderRole = ministryRole === "Worship Leader";
      await saveCleanUserProfile(cred.user.uid, {
        uid: cred.user.uid,
        fullName,
        username: fullName.split(/\s+/)[0] || "Member",
        email,
        resetPassword: hashedPassword,
        role: isLeaderRole ? "Leader" : "Member",
        ministryRole,
        teamCode: "",
        teamName: "",
        photoUri: "",
        unavailableRanges: []
      });
      showToast("Account created.", "success");
    } catch (err) {
      showToast(err.message || "Failed to create account.", "error");
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "Create Ministry Account";
      }
    }
  },

  async handleGoogleSignIn() {
    try {
      await signInWithPopup(auth, googleProvider);
      showToast("Signed in with Google.", "success");
    } catch (err) {
      showToast(err.message || "Google sign-in cancelled.", "error");
    }
  },

  async handlePasswordReset(e) {
    e.preventDefault();
    const email = document.getElementById("reset-email")?.value.trim() || "";
    const emailErr = validateEmailForAuth(email);
    if (emailErr) return showToast(emailErr, "error");

    try {
      await sendPasswordResetEmail(auth, email);
      showToast("Password reset link sent to your email.", "success");
      state.authMode = "login";
      renderApp();
    } catch (err) {
      showToast(err.message || "Could not send password reset email.", "error");
    }
  },

  async signOutUser() {
    await signOut(auth);
    showToast("Signed out.", "info");
  },

  // --- Team Onboarding (100% compliant with /teams/{teamCode} & /users/{uid} rules) ---
  async handleCreateTeam(e) {
    e.preventDefault();
    const teamName = document.getElementById("create-team-name")?.value.trim() || "";
    if (teamName.length < 2) return showToast("Enter a valid ministry team name.", "error");

    const btn = document.getElementById("create-team-btn");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Creating Workspace...";
    }

    try {
      let teamCode = generateTeamCode();
      const existingSnap = await getDoc(doc(db, "teams", teamCode));
      if (existingSnap.exists()) {
        teamCode = generateTeamCode();
      }

      const uid = state.currentUser.uid;
      const leaderName = state.currentUser.fullName || state.currentUser.name || "Worship Leader";

      await saveCleanTeamDoc(teamCode, {
        teamCode,
        teamName,
        leaderUid: uid,
        leaderName,
        leaderEmail: state.currentUser.email || "",
        roles: DEFAULT_MINISTRY_ROLES,
        removedMembers: [],
        isDeleted: false,
        status: "active"
      });

      await saveCleanUserProfile(uid, {
        teamCode,
        teamName,
        role: "Leader",
        ministryRole: "Worship Leader",
        removedByLeader: false,
        removedTeamCode: "",
        joinedAt: Date.now()
      });

      showToast(`Created team "${teamName}" (Invite Code: ${teamCode}).`, "success");
    } catch (err) {
      console.error("Create team error:", err);
      showToast(err.message || "Failed to create team.", "error");
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "Create Workspace";
      }
    }
  },

  async handleJoinTeam(e) {
    e.preventDefault();
    const code = (document.getElementById("join-team-code")?.value || "").trim().toUpperCase();
    if (!/^[A-Z0-9]{6}$/.test(code)) {
      return showToast("Enter a valid 6-character alphanumeric invite code.", "error");
    }

    const btn = document.getElementById("join-team-btn");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Joining Team...";
    }

    try {
      const teamSnap = await getDoc(doc(db, "teams", code));
      let teamData = teamSnap.exists() ? teamSnap.data() : null;

      // Fallback: check if any users already belong to this teamCode (for teams created offline on mobile)
      if (!teamData) {
        const memberCheck = await getDocs(query(collection(db, "users"), where("teamCode", "==", code)));
        if (!memberCheck.empty) {
          const firstMember = memberCheck.docs[0].data();
          teamData = {
            teamCode: code,
            teamName: firstMember.teamName || `Team ${code}`,
            leaderUid: "",
            removedMembers: []
          };
        }
      }

      if (!teamData || teamData.isDeleted === true || teamData.status === "deleted") {
        return showToast("No active ministry team found with that invite code.", "error");
      }

      const uid = state.currentUser.uid;
      const wasRemoved =
        (state.currentUser.removedByLeader && state.currentUser.removedTeamCode === code) ||
        (Array.isArray(teamData.removedMembers) && teamData.removedMembers.includes(uid));

      if (wasRemoved) {
        openModal(`
          <div class="space-y-4">
            <div class="flex items-center gap-2.5 text-amber-700">
              <i class="fa-solid fa-circle-exclamation"></i>
              <h3 class="text-base font-semibold text-slate-900">Rejoin Approval Required</h3>
            </div>
            <p class="text-xs text-slate-600 leading-relaxed">
              You were previously removed from <strong>${escapeHtml(teamData.teamName || code)}</strong>. Would you like to send a rejoin request to the Worship Leader?
            </p>
            <div class="flex justify-end gap-2 pt-2">
              <button onclick="window.appActions.closeModal()" class="btn btn-secondary">Cancel</button>
              <button onclick="window.appActions.submitRejoinRequest('${escapeHtml(code)}')" class="btn btn-primary">
                Send Rejoin Request
              </button>
            </div>
          </div>
        `);
        return;
      }

      const isDesignatedLeader = teamData.leaderUid === uid;
      const currentMinistry = state.currentUser.ministryRole || "Member";
      const nextMinistry = (!isDesignatedLeader && isWorshipLeader(currentMinistry, state.currentUser.role))
        ? "Member"
        : currentMinistry;

      await saveCleanUserProfile(uid, {
        teamCode: code,
        teamName: teamData.teamName || `Team ${code}`,
        role: isDesignatedLeader ? "Leader" : "Member",
        ministryRole: isDesignatedLeader ? "Worship Leader" : nextMinistry,
        removedByLeader: false,
        removedTeamCode: "",
        joinedAt: Date.now()
      });

      showToast(`Joined ${teamData.teamName || code}.`, "success");
    } catch (err) {
      console.error("Join team error:", err);
      showToast(err.message || "Could not join team.", "error");
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "Join Worship Team";
      }
    }
  },

  async submitRejoinRequest(teamCode) {
    const code = String(teamCode || "").trim().toUpperCase();
    if (!code) return;
    try {
      const reqRef = doc(collection(db, "teams", code, "rejoin_requests"));
      const now = Date.now();
      await setDoc(reqRef, {
        id: reqRef.id,
        teamCode: code,
        userUid: state.currentUser.uid,
        userName: state.currentUser.fullName || state.currentUser.name || "Member",
        userEmail: state.currentUser.email || "",
        reason: "Requested to rejoin via Synced N Web",
        status: "Pending",
        createdAt: now,
        updatedAt: now
      });
      closeModal();
      showToast("Rejoin request sent to the Worship Leader.", "success");
    } catch (err) {
      showToast(err.message || "Could not send rejoin request.", "error");
    }
  },

  copyTeamCode() {
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!code) return;
    navigator.clipboard.writeText(code);
    showToast(`Copied invite code: ${code}`, "success");
  },

  openEditTeamNameModal() {
    const currentName = state.currentTeam?.teamName || state.currentUser?.teamName || "";
    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <h3 class="text-base font-semibold text-slate-900">Rename Ministry Workspace</h3>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-slate-600"><i class="fa-solid fa-xmark"></i></button>
        </div>
        <form onsubmit="window.appActions.saveTeamName(event)" class="space-y-4">
          <div>
            <label class="form-label">Team Name</label>
            <input id="edit-team-name-input" type="text" required maxlength="60" value="${escapeHtml(currentName)}" class="form-input" />
          </div>
          <div class="flex justify-end gap-2 pt-2">
            <button type="button" onclick="window.appActions.closeModal()" class="btn btn-secondary">Cancel</button>
            <button type="submit" class="btn btn-primary">Save Name</button>
          </div>
        </form>
      </div>
    `);
  },

  async saveTeamName(e) {
    e.preventDefault();
    const newName = document.getElementById("edit-team-name-input")?.value.trim() || "";
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (newName.length < 2 || !code) return;
    await saveCleanTeamDoc(code, { teamName: newName });
    await saveCleanUserProfile(state.currentUser.uid, { teamName: newName });
    closeModal();
    showToast("Team name updated.", "success");
  },

  // --- Announcements (/teams/{teamCode}/announcements/{announcementId}) ---
  openAnnouncementModal() {
    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <h3 class="text-base font-semibold text-slate-900">Post Ministry Announcement</h3>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-slate-600"><i class="fa-solid fa-xmark"></i></button>
        </div>
        <form onsubmit="window.appActions.saveAnnouncement(event)" class="space-y-4">
          <div>
            <label class="form-label">Headline</label>
            <input id="ann-title" type="text" required maxlength="100" placeholder="e.g. Friday Night Full Band Rehearsal" class="form-input" />
          </div>
          <div>
            <label class="form-label">Message Details</label>
            <textarea id="ann-content" rows="4" required maxlength="1200" placeholder="Share schedule reminders, dress code, or song updates..." class="form-textarea"></textarea>
          </div>
          <label class="flex items-center gap-2 text-xs font-medium text-slate-700 cursor-pointer">
            <input id="ann-urgent" type="checkbox" class="rounded border-slate-300 text-indigo-600" />
            <span>Mark as Urgent Notice</span>
          </label>
          <div class="flex justify-end gap-2 pt-2">
            <button type="button" onclick="window.appActions.closeModal()" class="btn btn-secondary">Cancel</button>
            <button type="submit" class="btn btn-primary">Publish Update</button>
          </div>
        </form>
      </div>
    `);
  },

  async saveAnnouncement(e) {
    e.preventDefault();
    const title = document.getElementById("ann-title")?.value.trim() || "";
    const message = document.getElementById("ann-content")?.value.trim() || "";
    const urgent = Boolean(document.getElementById("ann-urgent")?.checked);
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!title || !message || !code) return;

    const now = Date.now();
    const ref = doc(collection(db, "teams", code, "announcements"));
    await setDoc(ref, {
      id: ref.id,
      teamCode: code,
      title: title.slice(0, 120),
      message: message.slice(0, 2000),
      urgent,
      author: (state.currentUser.fullName || state.currentUser.name || "Leader").slice(0, 100),
      createdBy: state.currentUser.uid,
      createdAt: now,
      updatedAt: now
    });
    closeModal();
    showToast("Announcement posted.", "success");
  },

  async deleteAnnouncement(id) {
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!code || !confirm("Delete this announcement?")) return;
    await deleteDoc(doc(db, "teams", code, "announcements", id));
    showToast("Announcement removed.", "info");
  },

  // --- Member Unavailability (saved on /users/{uid}.unavailableRanges) ---
  openAvailabilityModal() {
    const ranges = state.currentUser?.unavailableRanges || [];
    openModal(`
      <div class="space-y-5">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <div>
            <h3 class="text-base font-semibold text-slate-900">Manage My Unavailability</h3>
            <p class="text-xs text-slate-500">Leaders will see conflict alerts if you are scheduled during these dates.</p>
          </div>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-slate-600"><i class="fa-solid fa-xmark"></i></button>
        </div>

        <form onsubmit="window.appActions.addUnavailableRange(event)" class="space-y-3 bg-slate-50 p-4 rounded-md border border-slate-200">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="form-label">Start Date</label>
              <input id="unavail-start" type="date" required class="form-input" />
            </div>
            <div>
              <label class="form-label">End Date</label>
              <input id="unavail-end" type="date" required class="form-input" />
            </div>
          </div>
          <div>
            <label class="form-label">Reason (Optional)</label>
            <input id="unavail-reason" type="text" maxlength="80" placeholder="e.g. Out of town / Family event" class="form-input" />
          </div>
          <button type="submit" class="w-full btn btn-primary">
            <i class="fa-solid fa-plus text-xs"></i> Add Unavailable Dates
          </button>
        </form>

        <div class="space-y-2">
          <h4 class="text-xs font-semibold uppercase tracking-wider text-slate-500">Current Unavailable Dates (${ranges.length})</h4>
          ${ranges.length === 0 ? `
            <p class="text-xs text-slate-400 italic py-2">You have no unavailable dates marked.</p>
          ` : `
            <div class="divide-y divide-slate-200 border border-slate-200 rounded-md">
              ${ranges.map((r) => `
                <div class="flex items-center justify-between px-3.5 py-2.5 text-xs">
                  <div>
                    <div class="font-semibold text-slate-800">${escapeHtml(r.startDate)} &rarr; ${escapeHtml(r.endDate)}</div>
                    ${r.reason ? `<div class="text-slate-500">${escapeHtml(r.reason)}</div>` : ""}
                  </div>
                  <button onclick="window.appActions.removeUnavailableRange('${escapeHtml(r.id)}')" class="btn btn-danger btn-sm">
                    <i class="fa-regular fa-trash-can"></i>
                  </button>
                </div>
              `).join("")}
            </div>
          `}
        </div>
      </div>
    `);
  },

  async addUnavailableRange(e) {
    e.preventDefault();
    const startDate = document.getElementById("unavail-start")?.value || "";
    const endDate = document.getElementById("unavail-end")?.value || startDate;
    const reason = document.getElementById("unavail-reason")?.value.trim() || "";
    if (!startDate) return;
    if (endDate < startDate) return showToast("End date cannot be earlier than start date.", "error");

    const current = [...(state.currentUser.unavailableRanges || [])];
    current.push({
      id: "unavail_" + Date.now(),
      startDate,
      endDate,
      reason,
      createdAt: Date.now()
    });
    await saveCleanUserProfile(state.currentUser.uid, { unavailableRanges: current });
    showToast("Availability updated.", "success");
    this.openAvailabilityModal();
  },

  async removeUnavailableRange(rangeId) {
    const current = (state.currentUser.unavailableRanges || []).filter((r) => r.id !== rangeId);
    await saveCleanUserProfile(state.currentUser.uid, { unavailableRanges: current });
    showToast("Removed unavailable date range.", "info");
    this.openAvailabilityModal();
  },

  // --- Service Schedule & Lineup Modal (/teams/{teamCode}/services/{serviceId}) ---
  openServiceModal(serviceId = null) {
    const existing = serviceId ? state.services.find((s) => s.id === serviceId) : null;
    const assignedMap = {};
    (existing?.assignments || []).forEach((m) => {
      assignedMap[m.uid] = m.role;
    });
    const currentSetlist = new Set(
      (existing?.songs || []).map((s) => (typeof s === "string" ? s : s.title))
    );
    const rehearsal = existing?.rehearsal || {};

    openModal(`
      <div class="space-y-5">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <div>
            <h3 class="text-base font-semibold text-slate-900">${existing ? "Edit Worship Service & Lineup" : "Schedule Worship Service"}</h3>
            <p class="text-xs text-slate-500">Set service times, venue, rehearsal dates, assigned members, and song lineup.</p>
          </div>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-slate-600"><i class="fa-solid fa-xmark"></i></button>
        </div>

        <form onsubmit="window.appActions.saveService(event, '${existing ? escapeHtml(existing.id) : ""}')" class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label class="form-label">Service Title</label>
              <input id="srv-name" type="text" required maxlength="80" value="${escapeHtml(existing?.title || "")}" placeholder="e.g. Sunday Morning Worship" class="form-input" />
            </div>
            <div>
              <label class="form-label">Venue / Location</label>
              <input id="srv-venue" type="text" required maxlength="80" value="${escapeHtml(existing?.venue || "Main Sanctuary")}" placeholder="Main Sanctuary" class="form-input" />
            </div>
            <div>
              <label class="form-label">Status</label>
              <select id="srv-status" class="form-select">
                <option value="Published" ${existing?.status !== "Draft" ? "selected" : ""}>Published</option>
                <option value="Draft" ${existing?.status === "Draft" ? "selected" : ""}>Draft</option>
              </select>
            </div>
          </div>

          <div class="grid grid-cols-2 sm:grid-cols-5 gap-3">
            <div>
              <label class="form-label">Service Date</label>
              <input id="srv-date" type="date" required value="${escapeHtml(existing?.date || "")}" class="form-input" />
            </div>
            <div>
              <label class="form-label">Start Time</label>
              <input id="srv-time" type="time" value="${escapeHtml(existing?.start || "09:00")}" class="form-input" />
            </div>
            <div>
              <label class="form-label">End Time</label>
              <input id="srv-end-time" type="time" value="${escapeHtml(existing?.end || "11:00")}" class="form-input" />
            </div>
            <div>
              <label class="form-label">Rehearsal Date</label>
              <input id="srv-reh-date" type="date" value="${escapeHtml(rehearsal.date || "")}" class="form-input" />
            </div>
            <div>
              <label class="form-label">Rehearsal Time</label>
              <input id="srv-reh-time" type="time" value="${escapeHtml(rehearsal.start || "")}" class="form-input" />
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <!-- Member Assignments -->
            <div class="border border-slate-200 rounded-md p-3.5 bg-slate-50/50 space-y-2 max-h-60 overflow-y-auto">
              <label class="form-label mb-1">Assign Team Lineup</label>
              ${state.teamMembers.map((m) => {
                const checked = Boolean(assignedMap[m.uid]);
                const roleVal = assignedMap[m.uid] || m.ministryRole || "Member";
                const mName = m.fullName || m.name || "Member";
                return `
                  <div class="flex items-center justify-between gap-2 bg-white px-2.5 py-1.5 rounded border border-slate-200 text-xs">
                    <label class="flex items-center gap-2 truncate cursor-pointer flex-1">
                      <input type="checkbox" class="srv-member-check rounded border-slate-300 text-indigo-600" data-uid="${escapeHtml(m.uid)}" data-name="${escapeHtml(mName)}" data-email="${escapeHtml(m.email || "")}" ${checked ? "checked" : ""} />
                      <span class="truncate font-medium text-slate-800">${escapeHtml(mName)}</span>
                    </label>
                    <input type="text" class="srv-member-role w-28 bg-slate-50 border border-slate-200 rounded px-2 py-0.5 text-[11px] text-slate-700" data-uid="${escapeHtml(m.uid)}" value="${escapeHtml(roleVal)}" />
                  </div>
                `;
              }).join("")}
            </div>

            <!-- Song Setlist Selection -->
            <div class="border border-slate-200 rounded-md p-3.5 bg-slate-50/50 space-y-2 max-h-60 overflow-y-auto">
              <label class="form-label mb-1">Select Worship Song Setlist</label>
              ${state.songs.length === 0 ? `
                <p class="text-xs text-slate-400 italic">No songs in library yet. Add songs in the Song Library tab.</p>
              ` : state.songs.map((song) => `
                <label class="flex items-center justify-between gap-2 bg-white px-2.5 py-1.5 rounded border border-slate-200 text-xs cursor-pointer">
                  <div class="flex items-center gap-2 truncate">
                    <input type="checkbox" class="srv-song-check rounded border-slate-300 text-indigo-600" value="${escapeHtml(song.title)}" ${currentSetlist.has(song.title) ? "checked" : ""} />
                    <span class="truncate font-medium text-slate-800">${escapeHtml(song.title)}</span>
                  </div>
                  <span class="badge badge-indigo font-mono text-[10px]">${escapeHtml(song.key || "C")}</span>
                </label>
              `).join("")}
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="form-label">Rehearsal & Service Notes</label>
              <input id="srv-notes" type="text" maxlength="240" value="${escapeHtml(rehearsal.notes || "")}" placeholder="e.g. Call time 7:30 AM, neutral attire" class="form-input" />
            </div>
            <div>
              <label class="form-label">Event Banner Image (Optional)</label>
              <div class="flex items-center gap-2">
                <input id="srv-image-url" type="text" value="${escapeHtml(existing?.coverImageUrl || "")}" placeholder="https://..." class="form-input flex-1 text-xs" />
                <label class="btn btn-secondary btn-sm cursor-pointer shrink-0">
                  <i class="fa-solid fa-cloud-arrow-up"></i> Upload
                  <input type="file" accept="image/*" class="hidden" onchange="window.appActions.uploadServiceCover(this)" />
                </label>
              </div>
            </div>
          </div>

          <div class="flex justify-end gap-2 pt-3 border-t border-slate-200">
            <button type="button" onclick="window.appActions.closeModal()" class="btn btn-secondary">Cancel</button>
            <button type="submit" class="btn btn-primary">
              ${existing ? "Save Changes" : "Publish Service Schedule"}
            </button>
          </div>
        </form>
      </div>
    `, "max-w-3xl");
  },

  async uploadServiceCover(inputEl) {
    const file = inputEl.files?.[0];
    if (!file) return;
    try {
      showToast("Uploading service cover image...", "info");
      const url = await uploadImageToCloudinary(file);
      const urlInput = document.getElementById("srv-image-url");
      if (urlInput) urlInput.value = url;
      showToast("Banner uploaded.", "success");
    } catch (err) {
      showToast(err.message || "Image upload failed.", "error");
    }
  },

  async saveService(e, existingId = "") {
    e.preventDefault();
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!code) return;

    const existing = existingId ? state.services.find((s) => s.id === existingId) : null;
    const title = (document.getElementById("srv-name")?.value.trim() || "Worship Service").slice(0, 100);
    const venue = (document.getElementById("srv-venue")?.value.trim() || "Main Sanctuary").slice(0, 100);
    const status = document.getElementById("srv-status")?.value === "Draft" ? "Draft" : "Published";
    const date = (document.getElementById("srv-date")?.value || "").slice(0, 20);
    const start = (document.getElementById("srv-time")?.value || "09:00").slice(0, 10);
    const end = (document.getElementById("srv-end-time")?.value || "11:00").slice(0, 10);
    const rehearsalDate = document.getElementById("srv-reh-date")?.value || "";
    const rehearsalTime = document.getElementById("srv-reh-time")?.value || "";
    const notes = document.getElementById("srv-notes")?.value.trim() || "";
    const coverImageUrl = (document.getElementById("srv-image-url")?.value.trim() || "").slice(0, 1000);

    const assignments = [];
    document.querySelectorAll(".srv-member-check:checked").forEach((chk) => {
      const uid = chk.getAttribute("data-uid") || "";
      const name = chk.getAttribute("data-name") || "";
      const email = chk.getAttribute("data-email") || "";
      const roleInput = document.querySelector(`.srv-member-role[data-uid="${uid}"]`);
      const role = roleInput?.value.trim() || "Member";
      assignments.push({ uid, email, name, role });
    });

    const songs = [];
    document.querySelectorAll(".srv-song-check:checked").forEach((chk) => {
      const songTitle = chk.value;
      const libSong = state.songs.find((s) => s.title === songTitle);
      songs.push({
        title: songTitle,
        artist: libSong?.artist || "",
        key: libSong?.key || "C",
        bpm: Number(libSong?.bpm) || 74
      });
    });

    const ref = existingId
      ? doc(db, "teams", code, "services", existingId)
      : doc(collection(db, "teams", code, "services"));

    const payload = {
      id: ref.id,
      teamCode: code,
      title,
      date,
      start,
      end,
      venue,
      status,
      coverImageUrl,
      assignments: assignments.slice(0, 50),
      rehearsal: {
        date: rehearsalDate,
        start: rehearsalTime,
        end: "",
        venue,
        notes
      },
      songs: songs.slice(0, 50),
      createdBy: existing?.createdBy || state.currentUser.uid,
      createdAt: existing?.createdAt !== undefined ? existing.createdAt : Date.now(),
      updatedAt: Date.now()
    };

    await setDoc(ref, payload);
    closeModal();
    showToast(existingId ? "Service schedule updated." : "New service scheduled.", "success");
  },

  async deleteService(serviceId) {
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!code || !confirm("Delete this worship service schedule?")) return;
    await deleteDoc(doc(db, "teams", code, "services", serviceId));
    showToast("Service deleted.", "info");
  },

  // --- Substitution Requests (/teams/{teamCode}/substitutions/{substitutionId}) ---
  openSubstitutionModal(serviceId) {
    const srv = state.services.find((s) => s.id === serviceId);
    if (!srv) return;
    const otherMembers = state.teamMembers.filter((m) => m.uid !== state.currentUser.uid);

    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <div>
            <h3 class="text-base font-semibold text-slate-900">Request a Lineup Substitute</h3>
            <p class="text-xs text-slate-500">${escapeHtml(srv.title)} (${escapeHtml(srv.date)})</p>
          </div>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-slate-600"><i class="fa-solid fa-xmark"></i></button>
        </div>

        ${otherMembers.length === 0 ? `
          <p class="text-xs text-slate-600">Invite at least one other member to your team before requesting a substitute.</p>
        ` : `
          <form onsubmit="window.appActions.submitSubstitutionRequest(event, '${escapeHtml(srv.id)}')" class="space-y-4">
            <div>
              <label class="form-label">Proposed Replacement Teammate</label>
              <select id="sub-suggested-user" required class="form-select">
                ${otherMembers.map((m) => {
                  const mName = m.fullName || m.name || "Member";
                  return `
                    <option value="${escapeHtml(m.uid)}" data-name="${escapeHtml(mName)}">
                      ${escapeHtml(mName)} (${escapeHtml(m.ministryRole || "Member")})
                    </option>
                  `;
                }).join("")}
              </select>
            </div>
            <div>
              <label class="form-label">Reason for Request</label>
              <textarea id="sub-reason" rows="3" required maxlength="300" placeholder="Explain your schedule conflict..." class="form-textarea"></textarea>
            </div>
            <div class="flex justify-end gap-2 pt-2">
              <button type="button" onclick="window.appActions.closeModal()" class="btn btn-secondary">Cancel</button>
              <button type="submit" class="btn btn-primary">Submit Request</button>
            </div>
          </form>
        `}
      </div>
    `);
  },

  async submitSubstitutionRequest(e, serviceId) {
    e.preventDefault();
    const srv = state.services.find((s) => s.id === serviceId);
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!srv || !code) return;

    const sel = document.getElementById("sub-suggested-user");
    const substituteUid = sel?.value || "";
    const substituteName = sel?.selectedOptions?.[0]?.getAttribute("data-name") || "Teammate";
    const reason = (document.getElementById("sub-reason")?.value.trim() || "Schedule conflict").slice(0, 500);
    if (!substituteUid) return showToast("Select a proposed substitute teammate.", "error");

    const mySlot = (srv.assignments || []).find((a) => a.uid === state.currentUser.uid);
    const role = mySlot?.role || state.currentUser.ministryRole || "Member";

    const now = Date.now();
    const ref = doc(collection(db, "teams", code, "substitutions"));
    await setDoc(ref, {
      id: ref.id,
      teamCode: code,
      serviceId: srv.id,
      serviceTitle: (srv.title || "Worship Service").slice(0, 120),
      serviceDate: (srv.date || "").slice(0, 50),
      requesterUid: state.currentUser.uid,
      requesterName: (state.currentUser.fullName || state.currentUser.name || "Member").slice(0, 100),
      role: role.slice(0, 50),
      substituteUid,
      substituteName: substituteName.slice(0, 100),
      reason,
      type: "Exclusion",
      status: "Pending",
      createdBy: state.currentUser.uid,
      createdAt: now,
      updatedAt: now
    });

    closeModal();
    showToast("Substitution request sent to leadership.", "success");
  },

  async respondSubstitution(subId, newStatus) {
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    const sub = state.substitutions.find((s) => s.id === subId);
    if (!sub || !code) return;

    const subRef = doc(db, "teams", code, "substitutions", subId);
    await setDoc(subRef, {
      ...sub,
      status: newStatus,
      updatedAt: Date.now()
    });

    // If approved, automatically swap in the service lineup
    if (newStatus === "Approved" && sub.serviceId) {
      const srv = state.services.find((s) => s.id === sub.serviceId);
      if (srv && Array.isArray(srv.assignments)) {
        const subMember = state.teamMembers.find((m) => m.uid === sub.substituteUid);
        const updatedAssignments = srv.assignments.map((slot) => {
          if (slot.uid === sub.requesterUid) {
            return {
              uid: sub.substituteUid,
              email: subMember?.email || "",
              name: sub.substituteName || subMember?.fullName || "Substitute",
              role: slot.role
            };
          }
          return slot;
        });
        await setDoc(doc(db, "teams", code, "services", srv.id), {
          ...srv,
          assignments: updatedAssignments,
          updatedAt: Date.now()
        });
      }
    }

    showToast(`Substitution request ${newStatus.toLowerCase()}.`, "success");
  },

  // --- Song Library & Interactive Stage Chord Studio (/teams/{teamCode}/songs/{songId}) ---
  setSongSearch(q) {
    state.songSearchQuery = q;
    renderApp();
  },

  openSongByTitle(title) {
    const match = state.songs.find((s) => (s.title || "").trim().toLowerCase() === String(title).trim().toLowerCase());
    if (match) {
      this.openSongStageModal(match.id);
    } else {
      state.activeTab = "songs";
      state.songSearchQuery = title;
      renderApp();
      showToast(`Opened Song Library filtered for "${title}".`, "info");
    }
  },

  openSongStageModal(songId) {
    const song = state.songs.find((s) => s.id === songId);
    if (!song) return;

    state.stageModal.songId = songId;
    state.stageModal.semitones = 0;
    state.stageModal.lyricsOnly = false;
    state.stageModal.fontSize = 15;
    state.stageModal.autoScrollSpeed = 0;
    if (state.stageModal.scrollTimer) {
      clearInterval(state.stageModal.scrollTimer);
      state.stageModal.scrollTimer = null;
    }
    metronome.stop();
    metronome.setBpm(song.bpm || 74);

    this.renderStageModalContent();
  },

  renderStageModalContent() {
    const song = state.songs.find((s) => s.id === state.stageModal.songId);
    if (!song) return;
    const isLeader = hasLeaderPrivileges(state.currentUser);
    const { semitones, lyricsOnly, fontSize, autoScrollSpeed } = state.stageModal;
    const originalKey = song.key || "C";
    const activeKey = transposeSingleNote(originalKey, semitones);
    const ytId = extractYoutubeVideoId(song.referenceUrl);
    const formattedHtml = renderFormattedChordSheet(song.sheet, song.lyrics, semitones, lyricsOnly);

    openModal(`
      <div class="space-y-4">
        <!-- Song Title & Actions Header -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-200">
          <div>
            <div class="flex flex-wrap items-center gap-2">
              <span class="badge badge-indigo font-mono">Key: ${escapeHtml(activeKey)}${semitones !== 0 ? ` (${semitones > 0 ? "+" : ""}${semitones} from ${escapeHtml(originalKey)})` : ""}</span>
              ${song.bpm ? `<span class="badge badge-neutral font-mono">${escapeHtml(song.bpm)} BPM</span>` : ""}
            </div>
            <h2 class="text-lg font-semibold text-slate-900 mt-1">${escapeHtml(song.title || "Untitled Song")}</h2>
            <p class="text-xs text-slate-500">${escapeHtml(song.artist || "Unknown Artist")}</p>
          </div>
          <div class="flex items-center gap-2 self-start sm:self-auto">
            ${isLeader ? `
              <button onclick="window.appActions.openSongEditorModal('${escapeHtml(song.id)}')" class="btn btn-secondary btn-sm">
                <i class="fa-solid fa-pen text-[11px]"></i> Edit Chart
              </button>
            ` : ""}
            <button onclick="window.appActions.closeModal()" class="btn btn-secondary btn-sm">
              <i class="fa-solid fa-xmark"></i> Close
            </button>
          </div>
        </div>

        <!-- Stage Controls Toolbar -->
        <div class="bg-slate-50 border border-slate-200 rounded-md p-3 flex flex-wrap items-center justify-between gap-3">
          <!-- Transpose Controls -->
          <div class="flex items-center gap-1.5">
            <span class="text-xs font-medium text-slate-600 mr-1">Transpose:</span>
            <button onclick="window.appActions.adjustStageTranspose(-1)" class="btn btn-secondary btn-sm font-mono">-1</button>
            <button onclick="window.appActions.adjustStageTranspose(0, true)" class="btn btn-secondary btn-sm">Reset (${escapeHtml(originalKey)})</button>
            <button onclick="window.appActions.adjustStageTranspose(1)" class="btn btn-secondary btn-sm font-mono">+1</button>
          </div>

          <!-- Lyrics Mode & Font Size -->
          <div class="flex items-center gap-1.5">
            <button onclick="window.appActions.toggleStageLyricsOnly()" class="btn ${lyricsOnly ? "btn-indigo" : "btn-secondary"} btn-sm">
              <i class="fa-solid fa-microphone-lines text-[11px]"></i> ${lyricsOnly ? "Lyrics Only: ON" : "Chords + Lyrics"}
            </button>
            <button onclick="window.appActions.adjustStageFont(-1)" class="btn btn-secondary btn-sm" title="Smaller Font">A-</button>
            <button onclick="window.appActions.adjustStageFont(1)" class="btn btn-secondary btn-sm" title="Larger Font">A+</button>
          </div>

          <!-- Auto-Scroll & Metronome -->
          <div class="flex items-center gap-3">
            <div class="flex items-center gap-1.5">
              <span class="text-xs font-medium text-slate-600">Scroll:</span>
              <input type="range" min="0" max="5" step="1" value="${autoScrollSpeed}" onchange="window.appActions.setStageAutoScroll(this.value)" class="w-20 accent-indigo-600 cursor-pointer" />
            </div>
            <button onclick="window.appActions.toggleMetronome()" class="btn btn-secondary btn-sm">
              <span id="metronome-beat-dot" class="w-2.5 h-2.5 rounded-full bg-slate-300"></span>
              <span class="font-mono">${escapeHtml(song.bpm || "74")} BPM</span>
            </button>
          </div>
        </div>

        <!-- Chord Sheet & Optional YouTube Split Layout -->
        <div class="grid grid-cols-1 ${ytId ? "lg:grid-cols-12" : ""} gap-4 items-start">
          <div id="stage-chord-scroller" class="${ytId ? "lg:col-span-8" : ""} bg-[#FCFBF7] border border-slate-200 rounded-md p-5 max-h-[60vh] overflow-y-auto chord-sheet-container" style="font-size: ${fontSize}px;">
            ${formattedHtml}
          </div>

          ${ytId ? `
            <div class="lg:col-span-4 space-y-3">
              <div class="border border-slate-200 rounded-md overflow-hidden bg-slate-950 aspect-video">
                <iframe
                  src="https://www.youtube.com/embed/${escapeHtml(ytId)}"
                  title="YouTube Reference Player"
                  class="w-full h-full"
                  frameborder="0"
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                  allowfullscreen
                ></iframe>
              </div>
              <a href="${escapeHtml(song.referenceUrl)}" target="_blank" rel="noopener" class="w-full btn btn-secondary btn-sm">
                <i class="fa-brands fa-youtube text-rose-600"></i> Open on YouTube
              </a>
            </div>
          ` : ""}
        </div>
      </div>
    `, "max-w-5xl");
  },

  adjustStageTranspose(delta, reset = false) {
    if (reset) state.stageModal.semitones = 0;
    else state.stageModal.semitones = ((state.stageModal.semitones + delta) % 12);
    this.renderStageModalContent();
  },

  toggleStageLyricsOnly() {
    state.stageModal.lyricsOnly = !state.stageModal.lyricsOnly;
    this.renderStageModalContent();
  },

  adjustStageFont(delta) {
    state.stageModal.fontSize = Math.min(26, Math.max(12, state.stageModal.fontSize + delta));
    const scroller = document.getElementById("stage-chord-scroller");
    if (scroller) scroller.style.fontSize = `${state.stageModal.fontSize}px`;
  },

  setStageAutoScroll(speedVal) {
    const speed = parseInt(speedVal, 10) || 0;
    state.stageModal.autoScrollSpeed = speed;
    if (state.stageModal.scrollTimer) {
      clearInterval(state.stageModal.scrollTimer);
      state.stageModal.scrollTimer = null;
    }
    if (speed > 0) {
      state.stageModal.scrollTimer = setInterval(() => {
        const scroller = document.getElementById("stage-chord-scroller");
        if (scroller) scroller.scrollTop += speed;
      }, 65);
    }
  },

  toggleMetronome() {
    const active = metronome.toggle();
    showToast(active ? `Metronome started (${metronome.bpm} BPM)` : "Metronome stopped", "info");
  },

  openSongEditorModal(songId = null) {
    const existing = songId ? state.songs.find((s) => s.id === songId) : null;
    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <div>
            <h3 class="text-base font-semibold text-slate-900">${existing ? "Edit Song & Chord Sheet" : "Add Song to Ministry Library"}</h3>
            <p class="text-xs text-slate-500">Supports both Chord-over-Lyric lines and inline [G] ChordPro brackets.</p>
          </div>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-slate-600"><i class="fa-solid fa-xmark"></i></button>
        </div>

        <form onsubmit="window.appActions.saveSong(event, '${existing ? escapeHtml(existing.id) : ""}')" class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="form-label">Song Title</label>
              <input id="song-title" type="text" required maxlength="100" value="${escapeHtml(existing?.title || "")}" placeholder="e.g. Goodness of God" class="form-input" />
            </div>
            <div>
              <label class="form-label">Artist / Writer</label>
              <input id="song-artist" type="text" maxlength="100" value="${escapeHtml(existing?.artist || "")}" placeholder="e.g. Bethel Music" class="form-input" />
            </div>
          </div>

          <div class="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <div>
              <label class="form-label">Original Key</label>
              <select id="song-key" class="form-select font-mono">
                ${MUSICAL_KEYS.map((k) => `<option value="${escapeHtml(k)}" ${(existing?.key || "G") === k ? "selected" : ""}>${escapeHtml(k)}</option>`).join("")}
              </select>
            </div>
            <div>
              <label class="form-label">Tempo (BPM)</label>
              <input id="song-tempo" type="number" min="30" max="240" value="${escapeHtml(existing?.bpm || "72")}" class="form-input font-mono" />
            </div>
            <div class="col-span-2 sm:col-span-1">
              <label class="form-label">YouTube Reference Link</label>
              <input id="song-yt" type="url" value="${escapeHtml(existing?.referenceUrl || "")}" placeholder="https://youtube.com/watch?v=..." class="form-input" />
            </div>
          </div>

          <div>
            <label class="form-label">Chord Sheet & Lyrics</label>
            <textarea id="song-chords" rows="11" placeholder="[Verse 1]\nG                 C\nI love You Lord, Oh Your mercy never fails me..." class="form-textarea font-mono text-xs leading-relaxed">${escapeHtml(existing?.sheet || existing?.lyrics || "")}</textarea>
          </div>

          <div class="flex justify-end gap-2 pt-2 border-t border-slate-200">
            <button type="button" onclick="window.appActions.closeModal()" class="btn btn-secondary">Cancel</button>
            <button type="submit" class="btn btn-primary">${existing ? "Save Song Changes" : "Add Song"}</button>
          </div>
        </form>
      </div>
    `, "max-w-2xl");
  },

  async saveSong(e, existingId = "") {
    e.preventDefault();
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!code) return;

    const existing = existingId ? state.songs.find((s) => s.id === existingId) : null;
    const title = (document.getElementById("song-title")?.value.trim() || "").slice(0, 120);
    const artist = (document.getElementById("song-artist")?.value.trim() || "").slice(0, 100);
    const key = (document.getElementById("song-key")?.value || "C").slice(0, 10);
    const bpm = parseInt(document.getElementById("song-tempo")?.value || "74", 10) || 74;
    const referenceUrl = (document.getElementById("song-yt")?.value.trim() || "");
    const sheet = (document.getElementById("song-chords")?.value || "").slice(0, 20000);

    const ref = existingId
      ? doc(db, "teams", code, "songs", existingId)
      : doc(collection(db, "teams", code, "songs"));

    await setDoc(ref, {
      id: ref.id,
      teamCode: code,
      title,
      artist,
      key,
      bpm,
      sheet,
      lyrics: sheet,
      referenceUrl,
      createdBy: existing?.createdBy || state.currentUser.uid,
      createdAt: existing?.createdAt !== undefined ? existing.createdAt : Date.now(),
      updatedAt: Date.now()
    });

    closeModal();
    showToast(existingId ? "Song updated." : "Song added to library.", "success");
  },

  async deleteSong(songId) {
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!code || !confirm("Delete this song from the library?")) return;
    await deleteDoc(doc(db, "teams", code, "songs", songId));
    showToast("Song deleted.", "info");
  },

  // --- Roster & Leadership Actions ---
  async assignMemberRole(memberUid, newMinistryRole) {
    const isSubLeader = newMinistryRole === "Substitute Leader";
    await saveCleanUserProfile(memberUid, {
      ministryRole: newMinistryRole,
      role: isSubLeader ? "Leader" : "Member"
    });
    showToast(`Updated role to ${newMinistryRole}.`, "success");
  },

  async removeMemberFromTeam(memberUid, memberName) {
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!code || !confirm(`Remove ${memberName} from the team workspace?`)) return;

    const currentRemoved = Array.isArray(state.currentTeam?.removedMembers)
      ? [...state.currentTeam.removedMembers]
      : [];
    if (!currentRemoved.includes(memberUid)) currentRemoved.push(memberUid);

    await saveCleanTeamDoc(code, { removedMembers: currentRemoved });
    await saveCleanUserProfile(memberUid, {
      teamCode: "",
      teamName: "",
      role: "Member",
      ministryRole: "Member",
      removedByLeader: true,
      removedTeamCode: code
    });
    showToast(`${memberName} was removed from the team.`, "info");
  },

  async handleRejoinRequest(requestId, approve) {
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    const req = state.rejoinRequests.find((r) => r.id === requestId);
    if (!req || !code) return;

    if (approve) {
      const currentRemoved = (state.currentTeam?.removedMembers || []).filter((id) => id !== req.userUid);
      await saveCleanTeamDoc(code, { removedMembers: currentRemoved });
      await saveCleanUserProfile(req.userUid, {
        teamCode: code,
        teamName: state.currentTeam?.teamName || "",
        role: "Member",
        ministryRole: "Member",
        removedByLeader: false,
        removedTeamCode: "",
        joinedAt: Date.now()
      });
    }
    await deleteDoc(doc(db, "teams", code, "rejoin_requests", requestId));
    showToast(approve ? `${req.userName} rejoined the team.` : "Rejoin request declined.", "success");
  },

  openTransferLeadershipModal() {
    const candidates = state.teamMembers.filter((m) => m.uid !== state.currentUser.uid);
    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <div>
            <h3 class="text-base font-semibold text-slate-900">Transfer Worship Leadership</h3>
            <p class="text-xs text-slate-500">Select a team member to become the new primary Worship Leader.</p>
          </div>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-slate-600"><i class="fa-solid fa-xmark"></i></button>
        </div>
        ${candidates.length === 0 ? `
          <p class="text-xs text-slate-500">Invite another team member before transferring leadership.</p>
        ` : `
          <form onsubmit="window.appActions.confirmTransferLeadership(event)" class="space-y-4">
            <div>
              <label class="form-label">New Primary Worship Leader</label>
              <select id="transfer-leader-uid" class="form-select">
                ${candidates.map((c) => {
                  const cName = c.fullName || c.name || "Member";
                  return `<option value="${escapeHtml(c.uid)}" data-name="${escapeHtml(cName)}" data-email="${escapeHtml(c.email || "")}">${escapeHtml(cName)} (${escapeHtml(c.ministryRole || "Member")})</option>`;
                }).join("")}
              </select>
            </div>
            <div class="flex justify-end gap-2 pt-2">
              <button type="button" onclick="window.appActions.closeModal()" class="btn btn-secondary">Cancel</button>
              <button type="submit" class="btn btn-primary">Confirm Transfer</button>
            </div>
          </form>
        `}
      </div>
    `);
  },

  async confirmTransferLeadership(e) {
    e.preventDefault();
    const sel = document.getElementById("transfer-leader-uid");
    const targetUid = sel?.value;
    const targetName = sel?.selectedOptions?.[0]?.getAttribute("data-name") || "Worship Leader";
    const targetEmail = sel?.selectedOptions?.[0]?.getAttribute("data-email") || "";
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!targetUid || !code) return;

    await saveCleanTeamDoc(code, {
      leaderUid: targetUid,
      leaderName: targetName,
      leaderEmail: targetEmail
    });
    await saveCleanUserProfile(targetUid, { role: "Leader", ministryRole: "Worship Leader" });
    await saveCleanUserProfile(state.currentUser.uid, { role: "Member", ministryRole: "Member" });
    closeModal();
    showToast("Leadership transferred.", "success");
  },

  // --- Team Chat Actions (/teams/{teamCode}/messages/{messageId}) ---
  setChatReply(msgId) {
    const msg = state.messages.find((m) => m.id === msgId);
    if (!msg) return;
    state.chatReplyTarget = msg;
    state.chatEditingMessage = null;
    renderApp();
    document.getElementById("chat-message-input")?.focus();
  },

  clearChatReply() {
    state.chatReplyTarget = null;
    renderApp();
  },

  startEditChatMessage(msgId) {
    const msg = state.messages.find((m) => m.id === msgId);
    if (!msg) return;
    state.chatEditingMessage = msg;
    state.chatReplyTarget = null;
    renderApp();
    document.getElementById("chat-message-input")?.focus();
  },

  clearChatEdit() {
    state.chatEditingMessage = null;
    renderApp();
  },

  async sendChatMessage(e) {
    e.preventDefault();
    const input = document.getElementById("chat-message-input");
    const text = (input?.value || "").trim().slice(0, 2000);
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!text || !code) return;

    if (state.chatEditingMessage) {
      const existingMsg = state.chatEditingMessage;
      state.chatEditingMessage = null;
      await setDoc(doc(db, "teams", code, "messages", existingMsg.id), {
        ...existingMsg,
        text,
        isEdited: true,
        editedAt: Date.now()
      });
      return;
    }

    const reply = state.chatReplyTarget;
    state.chatReplyTarget = null;
    if (input) input.value = "";

    const ref = doc(collection(db, "teams", code, "messages"));
    await setDoc(ref, {
      id: ref.id,
      teamCode: code,
      senderUid: state.currentUser.uid,
      senderName: (state.currentUser.fullName || state.currentUser.name || "Member").slice(0, 100),
      senderPhotoUri: (state.currentUser.photoUri || "").slice(0, 500),
      senderRole: (state.currentUser.ministryRole || "Member").slice(0, 50),
      text,
      mediaUrl: "",
      replyToId: reply ? reply.id : "",
      replyToSender: reply ? (reply.senderName || "Member") : "",
      replyToText: reply ? (reply.text || "Photo") : "",
      timestamp: Date.now(),
      isEdited: false,
      editedAt: 0,
      isDeleted: false,
      deletedAt: 0
    });
  },

  async sendChatPhoto(inputEl) {
    const file = inputEl.files?.[0];
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!file || !code) return;
    try {
      showToast("Uploading photo to chat...", "info");
      const mediaUrl = await uploadImageToCloudinary(file);
      const ref = doc(collection(db, "teams", code, "messages"));
      await setDoc(ref, {
        id: ref.id,
        teamCode: code,
        senderUid: state.currentUser.uid,
        senderName: (state.currentUser.fullName || state.currentUser.name || "Member").slice(0, 100),
        senderPhotoUri: (state.currentUser.photoUri || "").slice(0, 500),
        senderRole: (state.currentUser.ministryRole || "Member").slice(0, 50),
        text: "",
        mediaUrl: mediaUrl.slice(0, 500),
        replyToId: "",
        replyToSender: "",
        replyToText: "",
        timestamp: Date.now(),
        isEdited: false,
        editedAt: 0,
        isDeleted: false,
        deletedAt: 0
      });
      showToast("Photo sent.", "success");
    } catch (err) {
      showToast(err.message || "Failed to upload chat photo.", "error");
    }
  },

  async retractChatMessage(msgId) {
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    const existing = state.messages.find((m) => m.id === msgId);
    if (!code || !existing) return;
    await setDoc(doc(db, "teams", code, "messages", msgId), {
      ...existing,
      text: "",
      mediaUrl: "",
      isDeleted: true,
      deletedAt: Date.now()
    });
  },

  // --- Profile Settings ---
  async uploadProfileAvatar(inputEl) {
    const file = inputEl.files?.[0];
    if (!file) return;
    try {
      showToast("Uploading profile picture...", "info");
      const url = await uploadImageToCloudinary(file);
      await saveCleanUserProfile(state.currentUser.uid, { photoUri: url });
      showToast("Profile photo updated.", "success");
    } catch (err) {
      showToast(err.message || "Avatar upload failed.", "error");
    }
  },

  async saveProfileChanges(e) {
    e.preventDefault();
    const fullName = document.getElementById("profile-name-input")?.value.trim() || "";
    const phone = document.getElementById("profile-phone-input")?.value.trim() || "";
    const roleSelect = document.getElementById("profile-role-select");
    if (fullName.length < 2) return showToast("Enter a valid name.", "error");

    const updates = {
      fullName,
      username: fullName.split(/\s+/)[0] || "Member",
      phone
    };
    if (roleSelect && !roleSelect.disabled) {
      updates.ministryRole = roleSelect.value;
    }
    await saveCleanUserProfile(state.currentUser.uid, updates);
    showToast("Profile updated.", "success");
  },

  async leaveCurrentTeam() {
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!code) return;
    const isPrimary = state.currentTeam?.leaderUid === state.currentUser.uid || isWorshipLeader(state.currentUser.ministryRole, state.currentUser.role);
    if (isPrimary && state.teamMembers.length > 1) {
      return showToast("Transfer Worship Leadership to another member before leaving the team.", "warning");
    }
    if (!confirm(`Leave ${state.currentTeam?.teamName || code}?`)) return;

    await saveCleanUserProfile(state.currentUser.uid, {
      teamCode: "",
      teamName: "",
      role: "Member",
      ministryRole: "Member"
    });
    showToast("You have left the team workspace.", "info");
  }
};
