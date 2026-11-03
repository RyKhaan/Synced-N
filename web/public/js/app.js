import { initializeApp } from "https://www.gstatic.com/firebasejs/10.13.1/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signInWithPopup,
  GoogleAuthProvider,
  sendPasswordResetEmail,
  updateProfile,
  signOut,
  signInAnonymously
} from "https://www.gstatic.com/firebasejs/10.13.1/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  query,
  where,
  getDocs,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/10.13.1/firebase-firestore.js";

import { FIREBASE_CONFIG, DEFAULT_MINISTRY_ROLES, MUSICAL_KEYS } from "./config.js";
import {
  escapeHtml,
  generateTeamCode,
  evaluatePassword,
  validateEmailForAuth,
  verifyPasswordPbkdf2,
  isWorshipLeader,
  isSubstituteLeader,
  hasLeaderPrivileges,
  uploadImageToCloudinary,
  extractYoutubeVideoId,
  transposeSingleNote,
  renderFormattedChordSheet,
  MetronomeEngine,
  formatFriendlyDate
} from "./utils.js";
import {
  getAvatarHtml,
  getRoleBadgeHtml,
  renderHomeView,
  renderScheduleView,
  renderSongsView,
  renderRosterView,
  renderChatView,
  renderProfileView
} from "./views.js";

// Initialize Firebase
const app = initializeApp(FIREBASE_CONFIG);
const auth = getAuth(app);
const db = getFirestore(app);

// Global Application State
const state = {
  authLoading: true,
  authMode: "login", // "login" | "register" | "reset"
  user: null,
  profile: null,
  team: null,
  activeTab: "home", // "home" | "schedule" | "songs" | "roster" | "chat" | "profile"
  roster: [],
  services: [],
  songs: [],
  announcements: [],
  substitutions: [],
  messages: [],
  rejoinRequests: [],
  songSearchQuery: "",
  chatReplyTo: null,
  chatEditingMessage: null,
  // Stage Chord Viewer state
  stageSong: null,
  stageSemitones: 0,
  stageLyricsOnly: false,
  stageFontSize: 16,
  stageAutoScrollSpeed: 0,
  stageAutoScrollTimer: null
};

let unsubUserDoc = null;
let unsubTeamDoc = null;
let unsubRoster = null;
let unsubServices = null;
let unsubSongs = null;
let unsubAnnouncements = null;
let unsubSubstitutions = null;
let unsubMessages = null;
let unsubRejoins = null;

const metronome = new MetronomeEngine((beatNum, isAccent) => {
  const dot = document.getElementById("metronomeBeatDot");
  const label = document.getElementById("metronomeBeatLabel");
  if (dot) {
    if (beatNum === 0) {
      dot.className = "w-3.5 h-3.5 rounded-full bg-slate-600 transition";
    } else {
      dot.className = `w-3.5 h-3.5 rounded-full ${isAccent ? "bg-amber-400 scale-125" : "bg-purple-400 scale-110"} transition`;
    }
  }
  if (label) {
    label.textContent = beatNum > 0 ? `Beat ${beatNum}/4` : "Stopped";
  }
});

export function showToast(message, type = "info") {
  const container = document.getElementById("toastContainer");
  if (!container) return;
  const el = document.createElement("div");
  const colors =
    type === "error"
      ? "bg-rose-950/95 border-rose-500/50 text-rose-100"
      : type === "success"
      ? "bg-emerald-950/95 border-emerald-500/50 text-emerald-100"
      : "bg-purple-950/95 border-purple-400/50 text-purple-100";
  const icon =
    type === "error" ? "fa-circle-exclamation text-rose-400" : type === "success" ? "fa-circle-check text-emerald-400" : "fa-circle-info text-purple-300";

  el.className = `px-4 py-3 rounded-xl border shadow-2xl backdrop-blur-md flex items-center gap-3 text-xs font-medium transition-all duration-300 ${colors}`;
  el.innerHTML = `<i class="fas ${icon} text-sm"></i><span>${escapeHtml(message)}</span>`;
  container.appendChild(el);
  setTimeout(() => {
    el.style.opacity = "0";
    setTimeout(() => el.remove(), 300);
  }, 3600);
}

function clearTeamSubscriptions() {
  [unsubTeamDoc, unsubRoster, unsubServices, unsubSongs, unsubAnnouncements, unsubSubstitutions, unsubMessages, unsubRejoins].forEach((fn) => {
    if (typeof fn === "function") {
      try { fn(); } catch {}
    }
  });
  unsubTeamDoc = unsubRoster = unsubServices = unsubSongs = unsubAnnouncements = unsubSubstitutions = unsubMessages = unsubRejoins = null;
}

function deduplicateRosterMembers(rawMembers, teamDoc) {
  const merged = new Map();
  const leaderUid = (teamDoc?.leaderUid || "").trim();
  const leaderEmail = (teamDoc?.leaderEmail || "").trim().toLowerCase();

  for (const m of rawMembers) {
    if (m.removedByLeader) continue;
    const key = (m.email || "").trim().toLowerCase() || m.uid || m.fullName;
    if (!key) continue;
    const existing = merged.get(key);
    if (!existing) {
      merged.set(key, { ...m });
    } else {
      const realUid = (!existing.uid?.startsWith("user_") && existing.uid) ? existing.uid : m.uid;
      merged.set(key, {
        ...existing,
        ...m,
        uid: realUid,
        fullName: existing.fullName || m.fullName,
        photoUri: existing.photoUri || m.photoUri,
        ministryRole: m.ministryRole || existing.ministryRole,
        unavailableRanges: (m.unavailableRanges && m.unavailableRanges.length) ? m.unavailableRanges : (existing.unavailableRanges || [])
      });
    }
  }

  const list = Array.from(merged.values()).map((m) => {
    const emailLower = (m.email || "").trim().toLowerCase();
    const isDesignatedLeader =
      (leaderUid && m.uid === leaderUid) ||
      (leaderEmail && emailLower === leaderEmail) ||
      (!leaderUid && !leaderEmail && emailLower === "ryanlloydgenturo@gmail.com");

    if (isDesignatedLeader || isWorshipLeader(m.ministryRole, m.role)) {
      return { ...m, role: "Leader", ministryRole: "Worship Leader" };
    }
    if (isSubstituteLeader(m.ministryRole) && String(m.role || "").toLowerCase() !== "member") {
      return { ...m, role: "Leader", ministryRole: "Substitute Leader" };
    }
    return m;
  });

  list.sort((a, b) => {
    const aLeader = isWorshipLeader(a.ministryRole, a.role) ? 2 : isSubstituteLeader(a.ministryRole) ? 1 : 0;
    const bLeader = isWorshipLeader(b.ministryRole, b.role) ? 2 : isSubstituteLeader(b.ministryRole) ? 1 : 0;
    if (aLeader !== bLeader) return bLeader - aLeader;
    return String(a.fullName || a.username || "").localeCompare(String(b.fullName || b.username || ""));
  });

  return list;
}

function subscribeToTeamData(teamCode) {
  clearTeamSubscriptions();
  const code = String(teamCode || "").trim().toUpperCase();
  if (!code) return;

  // 1. Team Document
  unsubTeamDoc = onSnapshot(doc(db, "teams", code), (snap) => {
    if (snap.exists()) {
      const data = snap.data();
      if (data.isDeleted || data.status === "deleted") {
        showToast("This team was deleted by the Worship Leader.", "error");
        state.team = null;
        if (state.profile) {
          state.profile.teamCode = "";
          state.profile.teamName = "";
        }
        clearTeamSubscriptions();
        renderApp();
        return;
      }
      state.team = { teamCode: code, ...data };
      if (state.profile && data.teamName && state.profile.teamName !== data.teamName) {
        state.profile.teamName = data.teamName;
      }
      state.roster = deduplicateRosterMembers(state.roster, state.team);
      renderApp();
    }
  });

  // 2. Team Roster (/users where teamCode == code)
  const rosterQuery = query(collection(db, "users"), where("teamCode", "==", code));
  unsubRoster = onSnapshot(rosterQuery, (snap) => {
    const raw = [];
    snap.forEach((d) => {
      const data = d.data();
      raw.push({ uid: data.uid || d.id, ...data });
    });
    state.roster = deduplicateRosterMembers(raw, state.team);
    renderApp();
  });

  // 3. Worship Services (/teams/{code}/services)
  unsubServices = onSnapshot(collection(db, "teams", code, "services"), (snap) => {
    const list = [];
    snap.forEach((d) => list.push({ id: d.id, ...d.data() }));
    list.sort((a, b) => String(a.date || "").localeCompare(String(b.date || "")));
    state.services = list;
    renderApp();
  });

  // 4. Songs Catalog (/teams/{code}/songs)
  unsubSongs = onSnapshot(collection(db, "teams", code, "songs"), (snap) => {
    const list = [];
    snap.forEach((d) => list.push({ id: d.id, ...d.data() }));
    list.sort((a, b) => String(a.title || "").localeCompare(String(b.title || "")));
    state.songs = list;
    renderApp();
  });

  // 5. Announcements (/teams/{code}/announcements)
  unsubAnnouncements = onSnapshot(collection(db, "teams", code, "announcements"), (snap) => {
    const list = [];
    snap.forEach((d) => list.push({ id: d.id, ...d.data() }));
    list.sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
    state.announcements = list;
    renderApp();
  });

  // 6. Substitutions (/teams/{code}/substitutions)
  unsubSubstitutions = onSnapshot(collection(db, "teams", code, "substitutions"), (snap) => {
    const list = [];
    snap.forEach((d) => list.push({ id: d.id, ...d.data() }));
    list.sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
    state.substitutions = list;
    renderApp();
  });

  // 7. Team Chat Messages (/teams/{code}/messages)
  unsubMessages = onSnapshot(collection(db, "teams", code, "messages"), (snap) => {
    const list = [];
    snap.forEach((d) => list.push({ id: d.id, ...d.data() }));
    list.sort((a, b) => Number(a.timestamp || 0) - Number(b.timestamp || 0));
    state.messages = list;
    renderApp();
    if (state.activeTab === "chat") {
      setTimeout(() => {
        const c = document.getElementById("chatMessagesContainer");
        if (c) c.scrollTop = c.scrollHeight;
      }, 50);
    }
  });

  // 8. Rejoin Requests (/teams/{code}/rejoin_requests)
  unsubRejoins = onSnapshot(collection(db, "teams", code, "rejoin_requests"), (snap) => {
    const list = [];
    snap.forEach((d) => list.push({ id: d.id, ...d.data() }));
    list.sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
    state.rejoinRequests = list;
    renderApp();
  });
}

function subscribeToCurrentUserProfile(uid, email) {
  if (unsubUserDoc) {
    try { unsubUserDoc(); } catch {}
    unsubUserDoc = null;
  }
  let subscribedTeamCode = "";

  unsubUserDoc = onSnapshot(doc(db, "users", uid), async (snap) => {
    if (snap.exists()) {
      const data = snap.data();
      state.profile = { uid, ...data };
      if (data.removedByLeader) {
        clearTeamSubscriptions();
        subscribedTeamCode = "";
        renderApp();
        return;
      }
      const nextCode = String(data.teamCode || "").trim().toUpperCase();
      if (nextCode && nextCode !== subscribedTeamCode) {
        subscribedTeamCode = nextCode;
        subscribeToTeamData(nextCode);
      } else if (!nextCode && subscribedTeamCode) {
        subscribedTeamCode = "";
        clearTeamSubscriptions();
      }
      renderApp();
    } else if (email) {
      // Check if an existing profile exists by email
      const q = query(collection(db, "users"), where("email", "==", email.trim().toLowerCase()));
      const qSnap = await getDocs(q);
      if (!qSnap.empty) {
        const existingData = qSnap.docs[0].data();
        const profile = { ...existingData, uid };
        await setDoc(doc(db, "users", uid), profile, { merge: true });
        state.profile = profile;
        if (profile.teamCode && !profile.removedByLeader) {
          subscribedTeamCode = profile.teamCode.toUpperCase();
          subscribeToTeamData(subscribedTeamCode);
        }
      } else {
        const newProfile = {
          uid,
          username: (auth.currentUser?.displayName || email.split("@")[0] || "Member").slice(0, 30),
          fullName: (auth.currentUser?.displayName || email.split("@")[0] || "Member").slice(0, 50),
          email: email.trim().toLowerCase(),
          role: "",
          ministryRole: "",
          teamName: "",
          teamCode: "",
          country: "Philippines",
          language: "English",
          unavailableRanges: [],
          removedByLeader: false,
          createdAt: Date.now()
        };
        await setDoc(doc(db, "users", uid), newProfile, { merge: true });
        state.profile = newProfile;
      }
      renderApp();
    }
  });
}

onAuthStateChanged(auth, (user) => {
  state.authLoading = false;
  state.user = user;
  if (user) {
    const email = user.email || localStorage.getItem("syncedn_fallback_email") || "";
    const uid = localStorage.getItem("syncedn_override_uid") || user.uid;
    subscribeToCurrentUserProfile(uid, email);
  } else {
    if (unsubUserDoc) {
      try { unsubUserDoc(); } catch {}
      unsubUserDoc = null;
    }
    clearTeamSubscriptions();
    state.profile = null;
    state.team = null;
    renderApp();
  }
});

// Render Authentication Screen
function renderAuthScreen() {
  const mode = state.authMode;
  return `
    <div class="min-h-screen flex items-center justify-center p-4 relative overflow-hidden">
      <img src="/assets/landing_bg.png" alt="" class="fixed inset-0 w-full h-full object-cover opacity-25 pointer-events-none" />
      <div class="fixed inset-0 bg-gradient-to-b from-[#0d0714]/80 via-[#181024]/90 to-[#0d0714] pointer-events-none"></div>

      <div class="glass-modal rounded-3xl w-full max-w-md p-7 sm:p-8 shadow-2xl relative z-10">
        <div class="flex flex-col items-center text-center mb-6">
          <img src="/assets/logo.png" alt="Synced-N Logo" class="w-20 h-20 rounded-2xl object-contain shadow-lg mb-3" />
          <h1 class="text-2xl font-extrabold text-white tracking-tight">Synced-N Ministry</h1>
          <p class="text-xs text-slate-300 mt-1">Church Music Ministry Schedule, Lineup & Chord Studio</p>
          <span class="mt-2 px-3 py-0.5 rounded-full text-[11px] font-semibold bg-purple-500/20 text-purple-200 border border-purple-400/30">
            Web &amp; Mobile Real-Time Sync
          </span>
        </div>

        <!-- Mode Tabs -->
        <div class="grid grid-cols-2 gap-1 p-1 rounded-xl bg-black/40 border border-white/10 mb-6">
          <button onclick="window.appActions.setAuthMode('login')" class="py-2 rounded-lg text-xs font-bold transition ${mode === "login" ? "brand-gradient text-white shadow" : "text-slate-400 hover:text-white"}">
            Sign In
          </button>
          <button onclick="window.appActions.setAuthMode('register')" class="py-2 rounded-lg text-xs font-bold transition ${mode === "register" ? "brand-gradient text-white shadow" : "text-slate-400 hover:text-white"}">
            Create Account
          </button>
        </div>

        ${mode === "login" ? `
          <form onsubmit="window.appActions.handleLogin(event)" class="space-y-4">
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Email Address</label>
              <input type="email" name="email" required placeholder="you@church.org" class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/15 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-purple-500" />
            </div>
            <div>
              <div class="flex items-center justify-between mb-1">
                <label class="text-xs font-semibold text-slate-300">Password</label>
                <button type="button" onclick="window.appActions.setAuthMode('reset')" class="text-[11px] text-purple-300 hover:text-white">Forgot password?</button>
              </div>
              <input type="password" name="password" required placeholder="••••••••" class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/15 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-purple-500" />
            </div>
            <button type="submit" id="authSubmitBtn" class="w-full brand-gradient hover:opacity-95 py-3 rounded-xl text-sm font-bold text-white shadow-lg transition">
              Sign In to Synced-N
            </button>
          </form>
        ` : mode === "register" ? `
          <form onsubmit="window.appActions.handleRegister(event)" class="space-y-3.5">
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Full Name</label>
              <input type="text" name="fullName" required maxlength="50" placeholder="e.g. Daniel Ceralvo" class="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-purple-500" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Username</label>
              <input type="text" name="username" required maxlength="30" placeholder="e.g. daniel_bass" class="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-purple-500" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Email Address</label>
              <input type="email" name="email" required maxlength="60" placeholder="you@church.org" class="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-purple-500" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Password</label>
              <input type="password" name="password" required placeholder="8+ chars, upper, lower, digit, symbol" class="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-purple-500" />
              <p class="text-[11px] text-slate-400 mt-1">Must include uppercase, lowercase, number, and special character.</p>
            </div>
            <button type="submit" id="authSubmitBtn" class="w-full brand-gradient hover:opacity-95 py-3 rounded-xl text-sm font-bold text-white shadow-lg transition">
              Create Ministry Account
            </button>
          </form>
        ` : `
          <form onsubmit="window.appActions.handlePasswordReset(event)" class="space-y-4">
            <p class="text-xs text-slate-300">Enter your registered email address and we will send you a password reset link.</p>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Email Address</label>
              <input type="email" name="email" required placeholder="you@church.org" class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/15 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-purple-500" />
            </div>
            <button type="submit" class="w-full brand-gradient hover:opacity-95 py-3 rounded-xl text-sm font-bold text-white shadow-lg transition">
              Send Reset Email
            </button>
            <button type="button" onclick="window.appActions.setAuthMode('login')" class="w-full py-2 text-xs text-slate-400 hover:text-white">
              &larr; Back to Sign In
            </button>
          </form>
        `}

        <div class="relative my-5">
          <div class="absolute inset-0 flex items-center"><div class="w-full border-t border-white/10"></div></div>
          <div class="relative flex justify-center text-[11px] uppercase"><span class="bg-[#150d21] px-3 text-slate-400">Or continue with</span></div>
        </div>

        <button onclick="window.appActions.handleGoogleSignIn()" class="w-full py-2.5 px-4 rounded-xl bg-white/5 hover:bg-white/10 border border-white/15 text-xs font-semibold text-white flex items-center justify-center gap-2.5 transition">
          <i class="fab fa-google text-rose-400"></i> Sign In with Google
        </button>
      </div>
    </div>
  `;
}

// Render Team Onboarding Screen (Create or Join Team by 6-char code)
function renderTeamOnboardingScreen() {
  const { profile } = state;
  const wasRemoved = profile?.removedByLeader === true;
  const removedCode = profile?.removedTeamCode || "";

  return `
    <div class="min-h-screen flex items-center justify-center p-4 relative">
      <div class="glass-modal rounded-3xl w-full max-w-2xl p-7 sm:p-9 shadow-2xl space-y-6">
        <div class="flex items-center justify-between border-b border-white/10 pb-4">
          <div class="flex items-center gap-3">
            <img src="/assets/team_icon.png" alt="Logo" class="w-12 h-12 rounded-xl object-cover border border-purple-400/40" />
            <div>
              <h1 class="text-xl font-extrabold text-white">Welcome, ${escapeHtml(profile?.fullName || profile?.username || "Member")}!</h1>
              <p class="text-xs text-slate-400">${escapeHtml(profile?.email || "")}</p>
            </div>
          </div>
          <button onclick="window.appActions.signOutUser()" class="text-xs text-slate-400 hover:text-white px-3 py-1.5 rounded-lg bg-white/5">
            <i class="fas fa-right-from-bracket mr-1"></i> Sign Out
          </button>
        </div>

        ${wasRemoved ? `
          <div class="p-4 rounded-2xl bg-amber-500/15 border border-amber-500/40 space-y-3">
            <div class="flex items-center gap-2 text-amber-300 font-bold text-sm">
              <i class="fas fa-triangle-exclamation"></i> You were removed from team ${escapeHtml(removedCode || "")}
            </div>
            <p class="text-xs text-slate-300">You can send a Rejoin Request to the Worship Leader, or join/create another team below.</p>
            ${removedCode ? `
              <form onsubmit="window.appActions.submitRejoinRequest(event, '${escapeHtml(removedCode)}')" class="flex flex-col sm:flex-row gap-2">
                <input type="text" name="reason" required placeholder="Brief message or reason to rejoin..." class="flex-1 px-3.5 py-2 rounded-xl bg-black/40 border border-white/15 text-xs text-white" />
                <button type="submit" class="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-black font-bold text-xs shrink-0">
                  Request to Rejoin (${escapeHtml(removedCode)})
                </button>
              </form>
            ` : ""}
          </div>
        ` : ""}

        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <!-- Join Existing Team Card -->
          <div class="glass-panel rounded-2xl p-5 border border-purple-500/30 flex flex-col justify-between space-y-4">
            <div>
              <div class="w-10 h-10 rounded-xl bg-purple-500/20 text-purple-300 flex items-center justify-center mb-3">
                <i class="fas fa-key text-lg"></i>
              </div>
              <h2 class="text-base font-bold text-white">Join a Ministry Team</h2>
              <p class="text-xs text-slate-400 mt-1">Enter the 6-character invite code shared by your Worship Leader.</p>
            </div>
            <form onsubmit="window.appActions.handleJoinTeam(event)" class="space-y-3">
              <input
                type="text"
                name="teamCode"
                maxlength="6"
                required
                placeholder="6-CHAR CODE (e.g. NLCC26)"
                class="w-full px-4 py-3 rounded-xl bg-black/40 border border-purple-400/40 text-center font-mono text-base font-bold uppercase tracking-widest text-white focus:outline-none focus:border-purple-400"
              />
              <button type="submit" class="w-full brand-gradient hover:opacity-95 py-2.5 rounded-xl text-xs font-bold text-white shadow-lg transition">
                Join Team Now
              </button>
            </form>
          </div>

          <!-- Create New Team Card -->
          <div class="glass-panel rounded-2xl p-5 border border-white/10 flex flex-col justify-between space-y-4">
            <div>
              <div class="w-10 h-10 rounded-xl bg-amber-500/20 text-amber-300 flex items-center justify-center mb-3">
                <i class="fas fa-crown text-lg"></i>
              </div>
              <h2 class="text-base font-bold text-white">Create a New Team</h2>
              <p class="text-xs text-slate-400 mt-1">Start a new Church Music Ministry team as Worship Leader and get a 6-character invite code.</p>
            </div>
            <form onsubmit="window.appActions.handleCreateTeam(event)" class="space-y-3">
              <input
                type="text"
                name="teamName"
                maxlength="60"
                required
                placeholder="e.g. NLCC Music Ministry"
                class="w-full px-4 py-3 rounded-xl bg-black/40 border border-white/15 text-sm text-white focus:outline-none focus:border-purple-400"
              />
              <button type="submit" class="w-full bg-white/10 hover:bg-white/15 border border-white/20 py-2.5 rounded-xl text-xs font-bold text-white transition">
                Create Ministry Team
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  `;
}

// Main App Render
export function renderApp() {
  const root = document.getElementById("appRoot");
  if (!root) return;

  if (state.authLoading) {
    root.innerHTML = `
      <div class="min-h-screen flex flex-col items-center justify-center gap-3">
        <img src="/assets/logo.png" alt="Synced-N" class="w-16 h-16 animate-pulse" />
        <p class="text-xs text-purple-300 font-semibold tracking-wider uppercase">Connecting to Synced-N Cloud...</p>
      </div>
    `;
    return;
  }

  if (!state.user) {
    root.innerHTML = renderAuthScreen();
    return;
  }

  if (!state.profile || !state.profile.teamCode || state.profile.removedByLeader) {
    root.innerHTML = renderTeamOnboardingScreen();
    return;
  }

  const { profile, activeTab, substitutions, rejoinRequests } = state;
  const isLeader = hasLeaderPrivileges(profile);
  const pendingAlerts =
    substitutions.filter((s) => s.status === "Pending" || s.status === "Substitute Accepted").length +
    (isLeader ? rejoinRequests.filter((r) => r.status === "Pending").length : 0);

  const navItems = [
    { id: "home", label: "Home", icon: "fa-house" },
    { id: "schedule", label: "Schedule", icon: "fa-calendar-days", badge: pendingAlerts || 0 },
    { id: "songs", label: "Songs", icon: "fa-music" },
    { id: "roster", label: "Team", icon: "fa-users" },
    { id: "chat", label: "Chat", icon: "fa-comments" },
    { id: "profile", label: "Profile", icon: "fa-user-gear" }
  ];

  let activeContent = "";
  switch (activeTab) {
    case "schedule":
      activeContent = renderScheduleView(state);
      break;
    case "songs":
      activeContent = renderSongsView(state);
      break;
    case "roster":
      activeContent = renderRosterView(state);
      break;
    case "chat":
      activeContent = renderChatView(state);
      break;
    case "profile":
      activeContent = renderProfileView(state);
      break;
    default:
      activeContent = renderHomeView(state);
  }

  root.innerHTML = `
    <div class="min-h-screen flex flex-col md:flex-row">
      <!-- Desktop Left Sidebar -->
      <aside class="hidden md:flex md:w-64 lg:w-72 flex-col justify-between glass-panel border-r border-white/10 p-5 shrink-0 sticky top-0 h-screen">
        <div class="space-y-6">
          <div class="flex items-center gap-3 px-2">
            <img src="/assets/logo.png" alt="Synced-N" class="w-10 h-10 rounded-xl object-contain" />
            <div class="min-w-0">
              <div class="font-extrabold text-base text-white tracking-tight truncate">Synced-N</div>
              <div class="text-[11px] text-purple-300 truncate">${escapeHtml(state.team?.teamName || profile.teamName || "Music Ministry")}</div>
            </div>
          </div>

          <nav class="space-y-1.5">
            ${navItems.map((item) => `
              <button
                onclick="window.appActions.switchTab('${item.id}')"
                class="w-full flex items-center justify-between px-4 py-3 rounded-xl text-sm font-semibold transition ${
                  activeTab === item.id
                    ? "brand-gradient text-white shadow-lg"
                    : "text-slate-300 hover:bg-white/5 hover:text-white"
                }"
              >
                <span class="flex items-center gap-3">
                  <i class="fas ${item.icon} w-5 text-center"></i>
                  <span>${item.label}</span>
                </span>
                ${item.badge ? `<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-400 text-black">${item.badge}</span>` : ""}
              </button>
            `).join("")}
          </nav>
        </div>

        <div class="pt-4 border-t border-white/10 flex items-center justify-between gap-2">
          <div onclick="window.appActions.switchTab('profile')" class="flex items-center gap-2.5 min-w-0 cursor-pointer">
            ${getAvatarHtml(profile.fullName || profile.username, profile.photoUri, "w-9 h-9 text-xs")}
            <div class="min-w-0">
              <div class="text-xs font-bold text-white truncate">${escapeHtml(profile.fullName || profile.username)}</div>
              <div class="text-[10px] text-purple-300 truncate">${escapeHtml(profile.ministryRole || profile.role || "Member")}</div>
            </div>
          </div>
          <button onclick="window.appActions.signOutUser()" class="p-2 rounded-lg hover:bg-white/10 text-slate-400 hover:text-rose-300 text-xs" title="Sign Out">
            <i class="fas fa-right-from-bracket"></i>
          </button>
        </div>
      </aside>

      <!-- Mobile Top Header -->
      <header class="md:hidden glass-panel border-b border-white/10 px-4 py-3 flex items-center justify-between sticky top-0 z-30">
        <div class="flex items-center gap-2.5">
          <img src="/assets/logo.png" alt="Synced-N" class="w-8 h-8 rounded-lg object-contain" />
          <div>
            <div class="font-extrabold text-sm text-white leading-tight">${escapeHtml(state.team?.teamName || profile.teamName || "Synced-N")}</div>
            <div class="text-[10px] text-purple-300 font-mono">Code: ${escapeHtml(profile.teamCode)}</div>
          </div>
        </div>
        <div class="flex items-center gap-2">
          ${getRoleBadgeHtml(profile.ministryRole, profile.role)}
        </div>
      </header>

      <!-- Main Workspace Content -->
      <main class="flex-1 p-4 sm:p-6 lg:p-8 pb-safe md:pb-8 max-w-7xl w-full mx-auto">
        ${activeContent}
      </main>

      <!-- Mobile Bottom Navigation -->
      <nav class="md:hidden fixed bottom-0 inset-x-0 glass-panel border-t border-white/10 px-2 py-1.5 flex items-center justify-around z-30">
        ${navItems.map((item) => `
          <button
            onclick="window.appActions.switchTab('${item.id}')"
            class="relative flex flex-col items-center justify-center py-1.5 px-2.5 rounded-xl text-[11px] font-medium transition ${
              activeTab === item.id ? "text-purple-300 font-bold" : "text-slate-400"
            }"
          >
            <i class="fas ${item.icon} text-base mb-0.5 ${activeTab === item.id ? "text-purple-400" : ""}"></i>
            <span>${item.label}</span>
            ${item.badge ? `<span class="absolute top-1 right-2 w-4 h-4 rounded-full text-[9px] font-bold bg-amber-400 text-black flex items-center justify-center">${item.badge}</span>` : ""}
          </button>
        `).join("")}
      </nav>
    </div>
  `;
}

// Modal Helper
function openModal(htmlContent) {
  const modalRoot = document.getElementById("modalRoot");
  if (!modalRoot) return;
  modalRoot.innerHTML = `
    <div class="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/75 backdrop-blur-sm animate-fadeIn">
      <div class="glass-modal rounded-3xl w-full max-w-2xl max-h-[92vh] overflow-y-auto shadow-2xl p-6 relative">
        ${htmlContent}
      </div>
    </div>
  `;
}

function closeModal() {
  metronome.stop();
  if (state.stageAutoScrollTimer) {
    clearInterval(state.stageAutoScrollTimer);
    state.stageAutoScrollTimer = null;
  }
  state.stageAutoScrollSpeed = 0;
  const modalRoot = document.getElementById("modalRoot");
  if (modalRoot) modalRoot.innerHTML = "";
}

// Stage Chord Sheet Modal Renderer
function renderStageModalContent() {
  const song = state.stageSong;
  if (!song) return "";
  const baseKey = song.key || "C";
  const currentKey = transposeSingleNote(baseKey.replace(/m$/, ""), state.stageSemitones) + (baseKey.endsWith("m") ? "m" : "");
  const ytId = extractYoutubeVideoId(song.referenceUrl);
  const sheetHtml = renderFormattedChordSheet(song.sheet, song.lyrics, state.stageSemitones, state.stageLyricsOnly);

  return `
    <div class="space-y-4">
      <!-- Top Header -->
      <div class="flex items-start justify-between gap-3 border-b border-white/10 pb-3">
        <div>
          <div class="flex flex-wrap items-center gap-2">
            <h2 class="text-xl font-extrabold text-white">${escapeHtml(song.title)}</h2>
            <span id="stageKeyBadge" class="px-2.5 py-0.5 rounded-lg bg-purple-500/25 border border-purple-400/40 text-purple-200 font-mono text-xs font-bold">
              Key: ${escapeHtml(currentKey)} ${state.stageSemitones !== 0 ? `(Orig: ${escapeHtml(baseKey)})` : ""}
            </span>
            <span class="px-2.5 py-0.5 rounded-lg bg-slate-800 text-slate-300 text-xs font-mono">${escapeHtml(song.bpm || 74)} BPM</span>
          </div>
          <p class="text-xs text-slate-400 mt-0.5">${escapeHtml(song.artist || "Worship Song")}</p>
        </div>
        <button onclick="window.appActions.closeModal()" class="p-2 rounded-xl bg-white/5 hover:bg-white/10 text-slate-300">
          <i class="fas fa-xmark text-lg"></i>
        </button>
      </div>

      <!-- Stage Controls Toolbar (Transpose, Vocalist Mode, Font Size, Auto-Scroll, Metronome) -->
      <div class="p-3 rounded-2xl bg-black/40 border border-white/10 flex flex-wrap items-center justify-between gap-3 text-xs">
        <!-- Transpose Controls -->
        <div class="flex items-center gap-1.5">
          <span class="text-slate-400 font-semibold mr-1">Transpose:</span>
          <button onclick="window.appActions.adjustStageTranspose(-1)" class="px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/15 text-white font-bold">-1</button>
          <button onclick="window.appActions.adjustStageTranspose(0, true)" class="px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/15 text-purple-300 font-semibold">Reset</button>
          <button onclick="window.appActions.adjustStageTranspose(1)" class="px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/15 text-white font-bold">+1</button>
        </div>

        <!-- Vocalist / Chords Toggle & Font Size -->
        <div class="flex items-center gap-1.5">
          <button onclick="window.appActions.toggleStageLyricsOnly()" id="stageLyricsToggleBtn" class="px-3 py-1.5 rounded-lg font-semibold border transition ${state.stageLyricsOnly ? "bg-pink-500/25 border-pink-400/40 text-pink-200" : "bg-white/5 border-white/10 text-slate-300"}">
            <i class="fas fa-microphone-lines mr-1"></i>${state.stageLyricsOnly ? "Vocalist View (Lyrics)" : "Chords + Lyrics"}
          </button>
          <button onclick="window.appActions.adjustStageFont(-2)" class="px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/15 text-white font-bold" title="Decrease Font">A-</button>
          <button onclick="window.appActions.adjustStageFont(2)" class="px-2.5 py-1.5 rounded-lg bg-white/5 hover:bg-white/15 text-white font-bold" title="Increase Font">A+</button>
        </div>

        <!-- Auto-Scroll & Metronome -->
        <div class="flex flex-wrap items-center gap-2">
          <div class="flex items-center gap-1.5 bg-white/5 px-2.5 py-1 rounded-lg border border-white/10">
            <i class="fas fa-arrows-up-down text-purple-300"></i>
            <span class="text-[11px] text-slate-300">Auto-Scroll</span>
            <input type="range" min="0" max="5" step="1" value="${state.stageAutoScrollSpeed}" oninput="window.appActions.setStageAutoScroll(this.value)" class="w-16 accent-purple-500" />
          </div>

          <button onclick="window.appActions.toggleMetronome()" class="px-3 py-1.5 rounded-lg bg-purple-600/30 hover:bg-purple-600/50 border border-purple-400/40 text-white font-semibold flex items-center gap-2">
            <span id="metronomeBeatDot" class="w-3.5 h-3.5 rounded-full bg-slate-600 transition"></span>
            <span id="metronomeBeatLabel">Metronome (${escapeHtml(song.bpm || 74)})</span>
          </button>
        </div>
      </div>

      <!--Chord Sheet Viewport -->
      <div id="stageChordScrollBox" style="font-size: ${state.stageFontSize}px;" class="chord-sheet-container p-5 rounded-2xl bg-[#0b0612] border border-white/10 max-h-[54vh] overflow-y-auto text-slate-100">
        ${sheetHtml}
      </div>

      <!-- YouTube In-App Reference Player & Link Card -->
      ${song.referenceUrl ? `
        <div class="p-4 rounded-2xl bg-white/5 border border-white/10 space-y-3">
          <div class="flex items-center justify-between gap-2">
            <div class="flex items-center gap-2 text-xs font-bold text-white">
              <i class="fab fa-youtube text-rose-500 text-base"></i> Song Reference Track
            </div>
            <a href="${escapeHtml(song.referenceUrl)}" target="_blank" rel="noopener" class="text-xs text-purple-300 hover:text-white underline">
              Open Reference Link <i class="fas fa-arrow-up-right-from-square ml-1 text-[10px]"></i>
            </a>
          </div>
          ${ytId ? `
            <div class="aspect-video w-full rounded-xl overflow-hidden bg-black border border-white/10">
              <iframe
                src="https://www.youtube.com/embed/${escapeHtml(ytId)}?rel=0"
                class="w-full h-full"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowfullscreen
              ></iframe>
            </div>
          ` : ""}
        </div>
      ` : ""}
    </div>
  `;
}

// Expose Actions to Global Window for inline event handlers
window.appActions = {
  closeModal,

  setAuthMode(mode) {
    state.authMode = mode;
    renderApp();
  },

  switchTab(tabId) {
    state.activeTab = tabId;
    renderApp();
    if (tabId === "chat") {
      setTimeout(() => {
        const c = document.getElementById("chatMessagesContainer");
        if (c) c.scrollTop = c.scrollHeight;
      }, 60);
    }
  },

  async handleLogin(e) {
    e.preventDefault();
    const form = e.target;
    const email = form.email.value.trim();
    const password = form.password.value.trim();
    const emailErr = validateEmailForAuth(email);
    if (emailErr) return showToast(emailErr, "error");

    const btn = document.getElementById("authSubmitBtn");
    if (btn) { btn.disabled = true; btn.textContent = "Signing in..."; }

    try {
      const cred = await signInWithEmailAndPassword(auth, email, password);
      // Verify if password was reset in /password_resets/{email}
      const resetSnap = await getDoc(doc(db, "password_resets", email.toLowerCase()));
      if (resetSnap.exists()) {
        const storedHash = resetSnap.data()?.password || "";
        if (storedHash && !(await verifyPasswordPbkdf2(password, storedHash))) {
          await signOut(auth);
          showToast("Incorrect password. This account's password was recently reset.", "error");
          if (btn) { btn.disabled = false; btn.textContent = "Sign In to Synced-N"; }
          return;
        }
      }
      localStorage.removeItem("syncedn_override_uid");
      localStorage.setItem("syncedn_fallback_email", email.toLowerCase());
      showToast("Signed in successfully!", "success");
    } catch (err) {
      // Fallback check: did user reset password via OTP in /password_resets/{email}?
      try {
        const resetSnap = await getDoc(doc(db, "password_resets", email.toLowerCase()));
        if (resetSnap.exists()) {
          const storedHash = resetSnap.data()?.password || "";
          if (storedHash && (await verifyPasswordPbkdf2(password, storedHash))) {
            await signInAnonymously(auth);
            const q = query(collection(db, "users"), where("email", "==", email.toLowerCase()));
            const uSnap = await getDocs(q);
            if (!uSnap.empty) {
              const realUid = uSnap.docs[0].data().uid || uSnap.docs[0].id;
              localStorage.setItem("syncedn_override_uid", realUid);
              localStorage.setItem("syncedn_fallback_email", email.toLowerCase());
              subscribeToCurrentUserProfile(realUid, email.toLowerCase());
              showToast("Signed in with reset password!", "success");
              return;
            }
          }
        }
      } catch {}
      showToast(err.message || "Invalid email or password.", "error");
      if (btn) { btn.disabled = false; btn.textContent = "Sign In to Synced-N"; }
    }
  },

  async handleRegister(e) {
    e.preventDefault();
    const form = e.target;
    const fullName = form.fullName.value.trim();
    const username = form.username.value.trim();
    const email = form.email.value.trim().toLowerCase();
    const password = form.password.value;

    const emailErr = validateEmailForAuth(email);
    if (emailErr) return showToast(emailErr, "error");
    const pwdEval = evaluatePassword(password);
    if (!pwdEval.isStrong) return showToast(pwdEval.errorMessage, "error");

    const btn = document.getElementById("authSubmitBtn");
    if (btn) { btn.disabled = true; btn.textContent = "Creating account..."; }

    try {
      const cred = await createUserWithEmailAndPassword(auth, email, password);
      await updateProfile(cred.user, { displayName: username });
      const newProfile = {
        uid: cred.user.uid,
        fullName,
        username,
        email,
        role: "",
        ministryRole: "",
        teamName: "",
        teamCode: "",
        phone: "",
        country: "Philippines",
        language: "English",
        photoUri: "",
        unavailableRanges: [],
        removedByLeader: false,
        createdAt: Date.now()
      };
      await setDoc(doc(db, "users", cred.user.uid), newProfile);
      state.profile = newProfile;
      showToast("Account created! Join or create your ministry team.", "success");
      renderApp();
    } catch (err) {
      showToast(err.message || "Registration failed.", "error");
      if (btn) { btn.disabled = false; btn.textContent = "Create Ministry Account"; }
    }
  },

  async handleGoogleSignIn() {
    try {
      const provider = new GoogleAuthProvider();
      await signInWithPopup(auth, provider);
      showToast("Signed in with Google!", "success");
    } catch (err) {
      showToast(err.message || "Google Sign-In failed.", "error");
    }
  },

  async handlePasswordReset(e) {
    e.preventDefault();
    const email = e.target.email.value.trim();
    if (!email) return;
    try {
      await sendPasswordResetEmail(auth, email);
      showToast("Password reset link sent to your email!", "success");
      state.authMode = "login";
      renderApp();
    } catch (err) {
      showToast(err.message || "Could not send password reset email.", "error");
    }
  },

  async signOutUser() {
    localStorage.removeItem("syncedn_override_uid");
    localStorage.removeItem("syncedn_fallback_email");
    await signOut(auth);
    showToast("Signed out.", "info");
  },

  async handleCreateTeam(e) {
    e.preventDefault();
    const teamName = e.target.teamName.value.trim();
    if (!teamName || !state.profile) return;
    const code = generateTeamCode();
    const uid = state.profile.uid;
    const leaderName = state.profile.fullName || state.profile.username || "Worship Leader";

    try {
      const teamData = {
        teamCode: code,
        teamName,
        leaderUid: uid,
        leaderName,
        leaderEmail: (state.profile.email || "").toLowerCase(),
        roles: DEFAULT_MINISTRY_ROLES,
        removedMembers: [],
        isDeleted: false,
        status: "active",
        createdAt: Date.now()
      };
      await setDoc(doc(db, "teams", code), teamData);
      await updateDoc(doc(db, "users", uid), {
        teamCode: code,
        teamName,
        role: "Leader",
        ministryRole: "Worship Leader",
        removedByLeader: false,
        removedTeamCode: "",
        joinedAt: Date.now()
      });
      showToast(`Team "${teamName}" created! Invite Code: ${code}`, "success");
    } catch (err) {
      showToast(err.message || "Failed to create team.", "error");
    }
  },

  async handleJoinTeam(e) {
    e.preventDefault();
    const code = e.target.teamCode.value.trim().toUpperCase();
    if (code.length !== 6 || !state.profile) {
      return showToast("Enter a valid 6-character team invite code.", "error");
    }
    try {
      const teamSnap = await getDoc(doc(db, "teams", code));
      if (!teamSnap.exists() || teamSnap.data().isDeleted) {
        return showToast("No active team found with that 6-character code.", "error");
      }
      const teamData = teamSnap.data();
      const removedList = (teamData.removedMembers || []).map((x) => String(x).toLowerCase());
      const myUidLower = state.profile.uid.toLowerCase();
      const myEmailLower = (state.profile.email || "").toLowerCase();

      if (removedList.includes(myUidLower) || (myEmailLower && removedList.includes(myEmailLower))) {
        await updateDoc(doc(db, "users", state.profile.uid), {
          removedByLeader: true,
          removedTeamCode: code
        });
        return showToast("You were previously removed from this team. Please send a Rejoin Request.", "error");
      }

      const isLeaderOwner = teamData.leaderUid === state.profile.uid || (myEmailLower && teamData.leaderEmail?.toLowerCase() === myEmailLower);
      await updateDoc(doc(db, "users", state.profile.uid), {
        teamCode: code,
        teamName: teamData.teamName,
        role: isLeaderOwner ? "Leader" : "Member",
        ministryRole: isLeaderOwner ? "Worship Leader" : (state.profile.ministryRole || "Member"),
        removedByLeader: false,
        removedTeamCode: "",
        joinedAt: Date.now()
      });
      showToast(`Joined ${teamData.teamName}!`, "success");
    } catch (err) {
      showToast(err.message || "Could not join team.", "error");
    }
  },

  async submitRejoinRequest(e, teamCode) {
    e.preventDefault();
    const reason = e.target.reason.value.trim();
    if (!reason || !state.profile) return;
    const reqId = `rejoin_${state.profile.uid}_${Date.now()}`;
    try {
      await setDoc(doc(db, "teams", teamCode, "rejoin_requests", reqId), {
        id: reqId,
        teamCode,
        userUid: state.profile.uid,
        userName: state.profile.fullName || state.profile.username || "Member",
        userEmail: state.profile.email || "",
        reason,
        status: "Pending",
        createdAt: Date.now(),
        updatedAt: Date.now()
      });
      showToast("Rejoin request sent to the Worship Leader!", "success");
      e.target.reset();
    } catch (err) {
      showToast(err.message || "Failed to send rejoin request.", "error");
    }
  },

  copyTeamCode() {
    const code = state.profile?.teamCode || "";
    if (!code) return;
    navigator.clipboard.writeText(code);
    showToast(`Copied Team Invite Code: ${code}`, "success");
  },

  openEditTeamNameModal() {
    const currentName = state.team?.teamName || state.profile?.teamName || "";
    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-bold text-white">Edit Ministry Team Name</h2>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-white"><i class="fas fa-xmark"></i></button>
        </div>
        <form onsubmit="window.appActions.saveTeamName(event)" class="space-y-4">
          <input type="text" name="teamName" value="${escapeHtml(currentName)}" required maxlength="60" class="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/15 text-sm text-white" />
          <div class="flex justify-end gap-2">
            <button type="button" onclick="window.appActions.closeModal()" class="px-4 py-2 rounded-xl bg-white/5 text-xs text-slate-300">Cancel</button>
            <button type="submit" class="brand-gradient px-5 py-2 rounded-xl text-xs font-bold text-white">Save Team Name</button>
          </div>
        </form>
      </div>
    `);
  },

  async saveTeamName(e) {
    e.preventDefault();
    const newName = e.target.teamName.value.trim();
    const code = state.profile?.teamCode;
    if (!newName || !code) return;
    try {
      await updateDoc(doc(db, "teams", code), { teamName: newName });
      await updateDoc(doc(db, "users", state.profile.uid), { teamName: newName });
      closeModal();
      showToast("Team name updated across all devices!", "success");
    } catch (err) {
      showToast(err.message || "Failed to update team name.", "error");
    }
  },

  // Announcements
  openAnnouncementModal() {
    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-bold text-white"><i class="fas fa-bullhorn text-amber-400 mr-2"></i>Post Ministry Announcement</h2>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-white"><i class="fas fa-xmark"></i></button>
        </div>
        <form onsubmit="window.appActions.saveAnnouncement(event)" class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-slate-300 mb-1">Headline / Title</label>
            <input type="text" name="title" required maxlength="100" placeholder="e.g. Saturday General Rehearsal Call Time" class="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white" />
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-300 mb-1">Message</label>
            <textarea name="message" rows="4" required maxlength="1500" placeholder="Write announcement details for the music team..." class="w-full px-4 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white"></textarea>
          </div>
          <label class="flex items-center gap-2 text-xs text-rose-300 font-semibold cursor-pointer">
            <input type="checkbox" name="urgent" class="rounded accent-rose-500" />
            Mark as Urgent Ministry Alert
          </label>
          <div class="flex justify-end gap-2">
            <button type="button" onclick="window.appActions.closeModal()" class="px-4 py-2 rounded-xl bg-white/5 text-xs text-slate-300">Cancel</button>
            <button type="submit" class="brand-gradient px-5 py-2 rounded-xl text-xs font-bold text-white">Post Announcement</button>
          </div>
        </form>
      </div>
    `);
  },

  async saveAnnouncement(e) {
    e.preventDefault();
    const form = e.target;
    const code = state.profile?.teamCode;
    if (!code) return;
    const id = `ann_${Date.now()}`;
    const data = {
      id,
      teamCode: code,
      title: form.title.value.trim(),
      message: form.message.value.trim(),
      urgent: form.urgent.checked,
      author: state.profile.fullName || state.profile.username || "Worship Leader",
      createdBy: state.profile.uid,
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
    try {
      await setDoc(doc(db, "teams", code, "announcements", id), data);
      closeModal();
      showToast("Announcement posted!", "success");
    } catch (err) {
      showToast(err.message || "Failed to post announcement.", "error");
    }
  },

  async deleteAnnouncement(id) {
    const code = state.profile?.teamCode;
    if (!code || !id) return;
    try {
      await deleteDoc(doc(db, "teams", code, "announcements", id));
      showToast("Announcement deleted.", "info");
    } catch (err) {
      showToast(err.message || "Could not delete announcement.", "error");
    }
  },

  // Availability Modal
  openAvailabilityModal() {
    const myRanges = state.profile?.unavailableRanges || [];
    const todayIso = new Date().toISOString().slice(0, 10);
    openModal(`
      <div class="space-y-5">
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-bold text-white"><i class="fas fa-calendar-xmark text-rose-400 mr-2"></i>My Unavailable Dates</h2>
            <p class="text-xs text-slate-400">Leaders will be alerted if they try to assign you during these dates.</p>
          </div>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-white"><i class="fas fa-xmark"></i></button>
        </div>

        <form onsubmit="window.appActions.addUnavailableRange(event)" class="p-4 rounded-2xl bg-white/5 border border-white/10 space-y-3">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Start Date</label>
              <input type="date" name="startDate" min="${todayIso}" required class="w-full px-3 py-2 rounded-xl bg-slate-900 border border-white/15 text-xs text-white" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">End Date</label>
              <input type="date" name="endDate" min="${todayIso}" required class="w-full px-3 py-2 rounded-xl bg-slate-900 border border-white/15 text-xs text-white" />
            </div>
          </div>
          <div>
            <label class="block text-xs font-semibold text-slate-300 mb-1">Reason</label>
            <input type="text" name="reason" required maxlength="100" placeholder="e.g. Out of town / Family event / School exam" class="w-full px-3.5 py-2 rounded-xl bg-slate-900 border border-white/15 text-xs text-white" />
          </div>
          <button type="submit" class="brand-gradient px-4 py-2 rounded-xl text-xs font-bold text-white">
            + Add Unavailable Date Range
          </button>
        </form>

        <div class="space-y-2">
          <h3 class="text-xs font-bold uppercase tracking-wider text-slate-400">Saved Unavailable Ranges (${myRanges.length})</h3>
          ${myRanges.length === 0 ? `<p class="text-xs text-slate-500 italic">You have no unavailable dates marked.</p>` : myRanges.map((r) => `
            <div class="p-3 rounded-xl bg-white/5 border border-white/10 flex items-center justify-between gap-2 text-xs">
              <div>
                <div class="font-bold text-rose-300">${escapeHtml(r.startDate)} &rarr; ${escapeHtml(r.endDate)}</div>
                <div class="text-slate-300 mt-0.5">${escapeHtml(r.reason)}</div>
              </div>
              <button onclick="window.appActions.removeUnavailableRange('${escapeHtml(r.id)}')" class="p-2 rounded-lg hover:bg-rose-500/20 text-slate-400 hover:text-rose-300">
                <i class="fas fa-trash"></i>
              </button>
            </div>
          `).join("")}
        </div>
      </div>
    `);
  },

  async addUnavailableRange(e) {
    e.preventDefault();
    const form = e.target;
    const startDate = form.startDate.value;
    const endDate = form.endDate.value;
    const reason = form.reason.value.trim();
    if (endDate < startDate) {
      return showToast("End date cannot be earlier than start date.", "error");
    }
    const existing = Array.isArray(state.profile?.unavailableRanges) ? [...state.profile.unavailableRanges] : [];
    existing.push({
      id: `rng_${Date.now()}`,
      startDate,
      endDate,
      reason,
      createdAt: Date.now()
    });
    try {
      await updateDoc(doc(db, "users", state.profile.uid), { unavailableRanges: existing });
      state.profile.unavailableRanges = existing;
      window.appActions.openAvailabilityModal();
      showToast("Unavailable date range saved!", "success");
    } catch (err) {
      showToast(err.message || "Failed to update availability.", "error");
    }
  },

  async removeUnavailableRange(rangeId) {
    const existing = (state.profile?.unavailableRanges || []).filter((r) => r.id !== rangeId);
    try {
      await updateDoc(doc(db, "users", state.profile.uid), { unavailableRanges: existing });
      state.profile.unavailableRanges = existing;
      window.appActions.openAvailabilityModal();
      showToast("Removed date range.", "info");
    } catch (err) {
      showToast(err.message || "Failed to remove range.", "error");
    }
  },

  // Worship Service Creation & Editing Wizard
  openServiceModal(serviceId = null) {
    const existing = serviceId ? state.services.find((s) => s.id === serviceId) : null;
    const todayIso = new Date().toISOString().slice(0, 10);
    const roles = (state.team?.roles && state.team.roles.length ? state.team.roles : DEFAULT_MINISTRY_ROLES);
    const assignments = existing?.assignments || [];
    const selectedSongs = existing?.songs || [];
    const rehearsal = existing?.rehearsal || {};

    openModal(`
      <div class="space-y-5">
        <div class="flex items-center justify-between border-b border-white/10 pb-3">
          <h2 class="text-lg font-extrabold text-white">
            <i class="fas fa-calendar-check text-purple-400 mr-2"></i>${existing ? "Edit Worship Service" : "Create Worship Service"}
          </h2>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-white"><i class="fas fa-xmark"></i></button>
        </div>

        <form onsubmit="window.appActions.saveService(event, '${escapeHtml(existing?.id || "")}')" class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div class="sm:col-span-2">
              <label class="block text-xs font-semibold text-slate-300 mb-1">Service Title</label>
              <input type="text" name="title" value="${escapeHtml(existing?.title || "")}" required maxlength="100" placeholder="e.g. Sunday Morning Worship Service" class="w-full px-3.5 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Service Date</label>
              <input type="date" id="svcDateInput" name="date" value="${escapeHtml(existing?.date || todayIso)}" min="${existing ? "" : todayIso}" required class="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-xs text-white" />
            </div>
            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="block text-xs font-semibold text-slate-300 mb-1">Start Time</label>
                <input type="time" name="start" value="${escapeHtml(existing?.start || "09:00")}" required class="w-full px-2.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-xs text-white" />
              </div>
              <div>
                <label class="block text-xs font-semibold text-slate-300 mb-1">End Time</label>
                <input type="time" name="end" value="${escapeHtml(existing?.end || "11:00")}" required class="w-full px-2.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-xs text-white" />
              </div>
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Venue / Sanctuary</label>
              <input type="text" name="venue" value="${escapeHtml(existing?.venue || "Main Sanctuary")}" required maxlength="100" class="w-full px-3.5 py-2.5 rounded-xl bg-white/5 border border-white/15 text-xs text-white" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Publication Status</label>
              <select name="status" class="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-xs text-white">
                <option value="Published" ${existing?.status === "Published" || !existing ? "selected" : ""}>Published (Visible to All Members)</option>
                <option value="Draft" ${existing?.status === "Draft" ? "selected" : ""}>Draft (Leaders Only)</option>
              </select>
            </div>
          </div>

          <!-- Cover Image Upload -->
          <div class="p-3.5 rounded-xl bg-white/5 border border-white/10 flex items-center justify-between gap-3">
            <div class="text-xs">
              <div class="font-bold text-white">Service Cover Image (Optional)</div>
              <input type="hidden" id="svcCoverUrlInput" name="coverImageUrl" value="${escapeHtml(existing?.coverImageUrl || "")}" />
              <div id="svcCoverStatus" class="text-[11px] text-slate-400 truncate max-w-xs">${existing?.coverImageUrl ? "Cover image attached" : "Upload flyer or banner via Cloudinary"}</div>
            </div>
            <label class="px-3 py-1.5 rounded-lg bg-purple-600/30 hover:bg-purple-600/50 border border-purple-400/30 text-xs font-semibold text-purple-200 cursor-pointer shrink-0">
              <i class="fas fa-upload mr-1"></i> Upload Cover
              <input type="file" accept="image/*" class="hidden" onchange="window.appActions.uploadServiceCover(this.files[0])" />
            </label>
          </div>

          <!-- Ministry Lineup Assignments -->
          <div class="p-4 rounded-2xl bg-white/5 border border-white/10 space-y-3">
            <div class="text-xs font-bold uppercase tracking-wider text-purple-300">
              <i class="fas fa-users-gear mr-1"></i> Assign Ministry Lineup
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2.5 max-h-48 overflow-y-auto pr-1">
              ${roles.filter((r) => r !== "Substitute Leader").map((roleName) => {
                const assigned = assignments.find((a) => a.role === roleName);
                return `
                  <div class="flex flex-col">
                    <label class="text-[11px] text-slate-300 font-medium mb-1">${escapeHtml(roleName)}</label>
                    <select data-assign-role="${escapeHtml(roleName)}" class="svc-role-select px-2.5 py-1.5 rounded-lg bg-slate-900 border border-white/15 text-xs text-white">
                      <option value="">-- Unassigned --</option>
                      ${state.roster.map((m) => {
                        const label = m.fullName || m.username;
                        const isSelected = assigned && (assigned.uid === m.uid || assigned.name === label);
                        return `<option value="${escapeHtml(m.uid)}" data-name="${escapeHtml(label)}" data-email="${escapeHtml(m.email || "")}" ${isSelected ? "selected" : ""}>${escapeHtml(label)} (${escapeHtml(m.ministryRole || "Member")})</option>`;
                      }).join("")}
                    </select>
                  </div>
                `;
              }).join("")}
            </div>
          </div>

          <!-- Worship Setlist Selection -->
          <div class="p-4 rounded-2xl bg-white/5 border border-white/10 space-y-2.5">
            <div class="flex items-center justify-between">
              <span class="text-xs font-bold uppercase tracking-wider text-pink-300"><i class="fas fa-music mr-1"></i> Select Worship Songs for Setlist</span>
              <span class="text-[11px] text-slate-400">${state.songs.length} in catalog</span>
            </div>
            ${state.songs.length === 0 ? `<p class="text-xs text-slate-400 italic">No songs in catalog yet. Add songs in the Songs tab.</p>` : `
              <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-40 overflow-y-auto pr-1">
                ${state.songs.map((sg) => {
                  const checked = selectedSongs.some((s) => s.title === sg.title);
                  return `
                    <label class="flex items-center gap-2 p-2 rounded-lg bg-black/30 border border-white/10 text-xs cursor-pointer hover:border-purple-400/40">
                      <input type="checkbox" class="svc-song-check accent-purple-500" value="${escapeHtml(sg.id)}" ${checked ? "checked" : ""} />
                      <span class="truncate text-white font-medium">${escapeHtml(sg.title)}</span>
                      <span class="ml-auto text-[10px] font-mono text-purple-300 shrink-0">${escapeHtml(sg.key || "C")}</span>
                    </label>
                  `;
                }).join("")}
              </div>
            `}
          </div>

          <!-- Rehearsal Details -->
          <div class="p-4 rounded-2xl bg-amber-500/5 border border-amber-500/20 space-y-3">
            <div class="text-xs font-bold uppercase tracking-wider text-amber-300"><i class="fas fa-guitar mr-1"></i> Rehearsal Schedule (Optional)</div>
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
              <div>
                <label class="block text-[11px] text-slate-300 mb-1">Rehearsal Date</label>
                <input type="date" name="rehDate" value="${escapeHtml(rehearsal.date || "")}" class="w-full px-2.5 py-1.5 rounded-lg bg-slate-900 border border-white/15 text-xs text-white" />
              </div>
              <div>
                <label class="block text-[11px] text-slate-300 mb-1">Start Time</label>
                <input type="time" name="rehStart" value="${escapeHtml(rehearsal.start || "")}" class="w-full px-2.5 py-1.5 rounded-lg bg-slate-900 border border-white/15 text-xs text-white" />
              </div>
              <div>
                <label class="block text-[11px] text-slate-300 mb-1">Rehearsal Venue</label>
                <input type="text" name="rehVenue" value="${escapeHtml(rehearsal.venue || "")}" placeholder="Music Room" class="w-full px-2.5 py-1.5 rounded-lg bg-slate-900 border border-white/15 text-xs text-white" />
              </div>
            </div>
            <input type="text" name="rehNotes" value="${escapeHtml(rehearsal.notes || "")}" placeholder="Rehearsal notes or reminders..." class="w-full px-3 py-1.5 rounded-lg bg-slate-900 border border-white/15 text-xs text-white" />
          </div>

          <div class="flex justify-end gap-2 pt-2">
            <button type="button" onclick="window.appActions.closeModal()" class="px-4 py-2.5 rounded-xl bg-white/5 text-xs text-slate-300">Cancel</button>
            <button type="submit" class="brand-gradient px-6 py-2.5 rounded-xl text-xs font-bold text-white shadow-lg">
              ${existing ? "Update Worship Service" : "Save Worship Service"}
            </button>
          </div>
        </form>
      </div>
    `);
  },

  async uploadServiceCover(file) {
    if (!file) return;
    const statusEl = document.getElementById("svcCoverStatus");
    const inputEl = document.getElementById("svcCoverUrlInput");
    if (statusEl) statusEl.textContent = "Uploading cover image...";
    try {
      const url = await uploadImageToCloudinary(file, 1000, 0.85);
      if (inputEl) inputEl.value = url;
      if (statusEl) statusEl.textContent = "Cover uploaded successfully!";
      showToast("Service cover image uploaded!", "success");
    } catch (err) {
      if (statusEl) statusEl.textContent = "Upload failed.";
      showToast(err.message || "Cover upload failed.", "error");
    }
  },

  async saveService(e, existingId = "") {
    e.preventDefault();
    const form = e.target;
    const code = state.profile?.teamCode;
    if (!code) return;
    const serviceDate = form.date.value;

    // Collect lineup assignments and check unavailability conflicts
    const assignments = [];
    const conflictNames = [];
    document.querySelectorAll(".svc-role-select").forEach((sel) => {
      const uid = sel.value;
      if (!uid) return;
      const opt = sel.options[sel.selectedIndex];
      const name = opt.getAttribute("data-name") || "Member";
      const email = opt.getAttribute("data-email") || "";
      const role = sel.getAttribute("data-assign-role") || "Member";

      const memberObj = state.roster.find((m) => m.uid === uid);
      const isUnavail = (memberObj?.unavailableRanges || []).some(
        (r) => serviceDate >= r.startDate && serviceDate <= r.endDate
      );
      if (isUnavail) conflictNames.push(`${name} (${role})`);
      assignments.push({ uid, email, name, role });
    });

    if (conflictNames.length > 0) {
      const proceed = confirm(
        `Availability Conflict Warning:\n${conflictNames.join(", ")} marked themselves unavailable on ${serviceDate}.\n\nDo you still want to save this lineup?`
      );
      if (!proceed) return;
    }

    // Collect selected setlist songs
    const songs = [];
    document.querySelectorAll(".svc-song-check:checked").forEach((chk) => {
      const sg = state.songs.find((s) => s.id === chk.value);
      if (sg) {
        songs.push({
          title: sg.title,
          artist: sg.artist || "",
          key: sg.key || "C",
          bpm: Number(sg.bpm || 74)
        });
      }
    });

    const id = existingId || `svc_${Date.now()}`;
    const existingDoc = existingId ? state.services.find((s) => s.id === existingId) : null;

    const serviceData = {
      id,
      teamCode: code,
      title: form.title.value.trim(),
      date: serviceDate,
      start: form.start.value,
      end: form.end.value,
      venue: form.venue.value.trim(),
      status: form.status.value,
      coverImageUrl: form.coverImageUrl.value.trim(),
      assignments,
      songs,
      rehearsal: {
        date: form.rehDate.value || "",
        start: form.rehStart.value || "",
        end: "",
        venue: form.rehVenue.value.trim(),
        notes: form.rehNotes.value.trim()
      },
      createdBy: existingDoc?.createdBy || state.profile.uid,
      createdAt: existingDoc?.createdAt || Date.now(),
      updatedAt: Date.now()
    };

    try {
      await setDoc(doc(db, "teams", code, "services", id), serviceData);
      closeModal();
      showToast(existingId ? "Worship service updated!" : "Worship service created!", "success");
    } catch (err) {
      showToast(err.message || "Failed to save service.", "error");
    }
  },

  async deleteService(serviceId) {
    if (!confirm("Delete this worship service?")) return;
    const code = state.profile?.teamCode;
    if (!code || !serviceId) return;
    try {
      await deleteDoc(doc(db, "teams", code, "services", serviceId));
      showToast("Service deleted.", "info");
    } catch (err) {
      showToast(err.message || "Failed to delete service.", "error");
    }
  },

  // Substitution Request Modal (with 1-request-per-service & unavailable member guard)
  openSubstitutionModal(serviceId, type = "Exclusion") {
    const svc = state.services.find((s) => s.id === serviceId);
    if (!svc || !state.profile) return;

    // Enforce 1 active sub request per service for this member
    const existingActive = state.substitutions.find(
      (s) =>
        s.serviceId === svc.id &&
        s.requesterUid === state.profile.uid &&
        (s.status === "Pending" || s.status === "Substitute Accepted")
    );
    if (existingActive) {
      return showToast("You already have an active substitution request for this service.", "error");
    }

    const myAssignment = (svc.assignments || []).find(
      (a) => a.uid === state.profile.uid || (a.email && a.email.toLowerCase() === (state.profile.email || "").toLowerCase())
    );
    const defaultRole = myAssignment?.role || state.profile.ministryRole || "Member";

    const availableTeammates = state.roster.filter((m) => m.uid !== state.profile.uid);

    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-lg font-bold text-white">
              <i class="fas fa-people-arrows text-amber-400 mr-2"></i>${type === "Exclusion" ? "Request a Substitute (Sub Out)" : "Volunteer for Service (Step In)"}
            </h2>
            <p class="text-xs text-slate-400">${escapeHtml(svc.title)} &bull; ${formatFriendlyDate(svc.date)}</p>
          </div>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-white"><i class="fas fa-xmark"></i></button>
        </div>

        <form onsubmit="window.appActions.submitSubstitutionRequest(event, '${escapeHtml(svc.id)}', '${escapeHtml(type)}')" class="space-y-4">
          <div>
            <label class="block text-xs font-semibold text-slate-300 mb-1">Ministry Role</label>
            <input type="text" name="role" value="${escapeHtml(defaultRole)}" required maxlength="50" class="w-full px-3.5 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white" />
          </div>

          ${type === "Exclusion" ? `
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Select Proposed Substitute Teammate</label>
              <select name="substituteUid" required class="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-xs text-white">
                <option value="">-- Choose Teammate --</option>
                ${availableTeammates.map((m) => {
                  const isUnavail = (m.unavailableRanges || []).some(
                    (r) => svc.date >= r.startDate && svc.date <= r.endDate
                  );
                  const label = m.fullName || m.username;
                  return `<option value="${escapeHtml(m.uid)}" data-name="${escapeHtml(label)}" data-unavail="${isUnavail ? "1" : "0"}">${escapeHtml(label)} (${escapeHtml(m.ministryRole || "Member")})${isUnavail ? " [UNAVAILABLE ON THIS DATE]" : ""}</option>`;
                }).join("")}
              </select>
            </div>
          ` : ""}

          <div>
            <label class="block text-xs font-semibold text-slate-300 mb-1">Reason</label>
            <textarea name="reason" rows="3" required maxlength="300" placeholder="Provide a brief explanation..." class="w-full px-3.5 py-2.5 rounded-xl bg-white/5 border border-white/15 text-sm text-white"></textarea>
          </div>

          <div class="flex justify-end gap-2">
            <button type="button" onclick="window.appActions.closeModal()" class="px-4 py-2 rounded-xl bg-white/5 text-xs text-slate-300">Cancel</button>
            <button type="submit" class="brand-gradient px-5 py-2 rounded-xl text-xs font-bold text-white">Submit Request</button>
          </div>
        </form>
      </div>
    `);
  },

  async submitSubstitutionRequest(e, serviceId, type) {
    e.preventDefault();
    const form = e.target;
    const svc = state.services.find((s) => s.id === serviceId);
    const code = state.profile?.teamCode;
    if (!svc || !code) return;

    let substituteUid = state.profile.uid;
    let substituteName = state.profile.fullName || state.profile.username || "Member";

    if (type === "Exclusion") {
      const sel = form.substituteUid;
      const opt = sel.options[sel.selectedIndex];
      if (opt.getAttribute("data-unavail") === "1") {
        return showToast("Cannot select this member: they marked themselves unavailable on the service date.", "error");
      }
      substituteUid = sel.value;
      substituteName = opt.getAttribute("data-name") || "Teammate";
    }

    const id = `sub_${Date.now()}`;
    const subData = {
      id,
      teamCode: code,
      serviceId: svc.id,
      serviceTitle: svc.title,
      serviceDate: svc.date,
      requesterUid: state.profile.uid,
      requesterName: state.profile.fullName || state.profile.username || "Member",
      role: form.role.value.trim(),
      substituteUid,
      substituteName,
      reason: form.reason.value.trim(),
      type,
      status: "Pending",
      createdBy: state.profile.uid,
      createdAt: Date.now(),
      updatedAt: Date.now()
    };

    try {
      await setDoc(doc(db, "teams", code, "substitutions", id), subData);
      closeModal();
      showToast("Substitution request submitted!", "success");
    } catch (err) {
      showToast(err.message || "Failed to submit substitution request.", "error");
    }
  },

  async respondSubstitution(subId, newStatus) {
    const code = state.profile?.teamCode;
    const sub = state.substitutions.find((s) => s.id === subId);
    if (!code || !sub) return;

    try {
      await updateDoc(doc(db, "teams", code, "substitutions", subId), {
        status: newStatus,
        updatedAt: Date.now()
      });

      // If approved by Leader, automatically update the linked service lineup!
      if (newStatus === "Approved" && sub.serviceId) {
        const svc = state.services.find((s) => s.id === sub.serviceId);
        if (svc) {
          const updatedAssignments = [...(svc.assignments || [])];
          const subMember = state.roster.find((m) => m.uid === sub.substituteUid);
          const idx = updatedAssignments.findIndex(
            (a) => a.uid === sub.requesterUid || a.role.toLowerCase() === sub.role.toLowerCase()
          );
          const newEntry = {
            uid: sub.substituteUid,
            email: subMember?.email || "",
            name: sub.substituteName,
            role: sub.role
          };
          if (idx >= 0) updatedAssignments[idx] = newEntry;
          else updatedAssignments.push(newEntry);

          await updateDoc(doc(db, "teams", code, "services", svc.id), {
            assignments: updatedAssignments,
            updatedAt: Date.now()
          });
        }
      }
      showToast(`Substitution marked as ${newStatus}.`, "success");
    } catch (err) {
      showToast(err.message || "Could not update substitution.", "error");
    }
  },

  // Song Search & Stage Viewer
  setSongSearch(val) {
    state.songSearchQuery = val;
    renderApp();
    const input = document.querySelector('input[placeholder*="Search songs"]');
    if (input) {
      input.focus();
      input.setSelectionRange(val.length, val.length);
    }
  },

  openSongByTitle(title, overrideKey = null) {
    const song = state.songs.find((s) => String(s.title || "").toLowerCase() === String(title || "").toLowerCase());
    if (!song) {
      return showToast(`Song "${title}" not found in catalog.`, "error");
    }
    this.openSongStageModal(song.id, overrideKey);
  },

  openSongStageModal(songId, overrideKey = null) {
    const song = state.songs.find((s) => s.id === songId);
    if (!song) return;
    state.stageSong = song;
    state.stageSemitones = 0;
    state.stageLyricsOnly = false;
    state.stageAutoScrollSpeed = 0;
    metronome.stop();
    metronome.setBpm(song.bpm || 74);
    openModal(`<div id="stageModalBody">${renderStageModalContent()}</div>`);
  },

  adjustStageTranspose(delta, reset = false) {
    if (reset) state.stageSemitones = 0;
    else state.stageSemitones = ((state.stageSemitones + delta) % 12 + 12) % 12;
    const body = document.getElementById("stageModalBody");
    if (body) body.innerHTML = renderStageModalContent();
  },

  toggleStageLyricsOnly() {
    state.stageLyricsOnly = !state.stageLyricsOnly;
    const body = document.getElementById("stageModalBody");
    if (body) body.innerHTML = renderStageModalContent();
  },

  adjustStageFont(delta) {
    state.stageFontSize = Math.min(28, Math.max(12, state.stageFontSize + delta));
    const box = document.getElementById("stageChordScrollBox");
    if (box) box.style.fontSize = `${state.stageFontSize}px`;
  },

  setStageAutoScroll(speedVal) {
    const speed = parseInt(speedVal, 10) || 0;
    state.stageAutoScrollSpeed = speed;
    if (state.stageAutoScrollTimer) {
      clearInterval(state.stageAutoScrollTimer);
      state.stageAutoScrollTimer = null;
    }
    if (speed > 0) {
      const intervalMs = Math.max(20, 110 - speed * 18);
      state.stageAutoScrollTimer = setInterval(() => {
        const box = document.getElementById("stageChordScrollBox");
        if (box) box.scrollTop += 1;
      }, intervalMs);
    }
  },

  toggleMetronome() {
    metronome.toggle();
  },

  openSongEditorModal(songId = null) {
    const existing = songId ? state.songs.find((s) => s.id === songId) : null;
    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between border-b border-white/10 pb-3">
          <h2 class="text-lg font-extrabold text-white">
            <i class="fas fa-music text-pink-400 mr-2"></i>${existing ? "Edit Song & Chord Sheet" : "Add Song to Ministry Catalog"}
          </h2>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-white"><i class="fas fa-xmark"></i></button>
        </div>

        <form onsubmit="window.appActions.saveSong(event, '${escapeHtml(existing?.id || "")}')" class="space-y-3.5">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Song Title</label>
              <input type="text" name="title" value="${escapeHtml(existing?.title || "")}" required maxlength="120" placeholder="e.g. Goodness of God" class="w-full px-3.5 py-2 rounded-xl bg-white/5 border border-white/15 text-sm text-white" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Artist / Worship Band</label>
              <input type="text" name="artist" value="${escapeHtml(existing?.artist || "")}" maxlength="100" placeholder="e.g. Bethel Music" class="w-full px-3.5 py-2 rounded-xl bg-white/5 border border-white/15 text-sm text-white" />
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Musical Key</label>
              <select name="key" class="w-full px-3.5 py-2 rounded-xl bg-slate-900 border border-white/15 text-sm text-white">
                ${MUSICAL_KEYS.map((k) => `<option value="${k}" ${(existing?.key || "C") === k ? "selected" : ""}>${k}</option>`).join("")}
              </select>
            </div>
            <div>
              <label class="block text-xs font-semibold text-slate-300 mb-1">Tempo (BPM)</label>
              <input type="number" name="bpm" min="30" max="240" value="${escapeHtml(existing?.bpm || 74)}" required class="w-full px-3.5 py-2 rounded-xl bg-white/5 border border-white/15 text-sm text-white" />
            </div>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 mb-1">YouTube / Audio Reference URL (Optional)</label>
            <input type="url" name="referenceUrl" value="${escapeHtml(existing?.referenceUrl || "")}" placeholder="https://www.youtube.com/watch?v=..." class="w-full px-3.5 py-2 rounded-xl bg-white/5 border border-white/15 text-xs text-white" />
          </div>

          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="text-xs font-semibold text-slate-300">Chord Sheet &amp; Arrangement</label>
              <span class="text-[10px] text-purple-300">Supports [Verse 1], [Chorus], [G]inline chords or chord lines</span>
            </div>
            <textarea name="sheet" rows="8" placeholder="[Verse 1]\n[G]I love You, Lord\n[C]Oh Your mercy [G]never fails me..." class="w-full px-3.5 py-2.5 rounded-xl bg-[#0b0612] border border-white/15 text-xs font-mono text-white leading-relaxed">${escapeHtml(existing?.sheet || "")}</textarea>
          </div>

          <div>
            <label class="block text-xs font-semibold text-slate-300 mb-1">Vocalist Lyrics (Optional Separate Lyrics)</label>
            <textarea name="lyrics" rows="4" placeholder="Clean lyrics for vocalists (or leave blank to auto-extract from chord sheet)..." class="w-full px-3.5 py-2 rounded-xl bg-white/5 border border-white/15 text-xs text-white">${escapeHtml(existing?.lyrics || "")}</textarea>
          </div>

          <div class="flex justify-end gap-2 pt-2">
            <button type="button" onclick="window.appActions.closeModal()" class="px-4 py-2 rounded-xl bg-white/5 text-xs text-slate-300">Cancel</button>
            <button type="submit" class="brand-gradient px-6 py-2 rounded-xl text-xs font-bold text-white shadow-lg">
              ${existing ? "Save Changes" : "Add Song"}
            </button>
          </div>
        </form>
      </div>
    `);
  },

  async saveSong(e, existingId = "") {
    e.preventDefault();
    const form = e.target;
    const code = state.profile?.teamCode;
    if (!code) return;
    const id = existingId || `song_${Date.now()}`;
    const existing = existingId ? state.songs.find((s) => s.id === existingId) : null;
    const sheetText = form.sheet.value;
    const lyricsText = form.lyrics.value || sheetText.replace(/\[([A-G][#b]?[^\]]*)\]/g, "");

    const songData = {
      id,
      teamCode: code,
      title: form.title.value.trim(),
      artist: form.artist.value.trim(),
      key: form.key.value,
      bpm: parseInt(form.bpm.value, 10) || 74,
      sheet: sheetText,
      lyrics: lyricsText,
      referenceUrl: form.referenceUrl.value.trim(),
      createdBy: existing?.createdBy || state.profile.uid,
      createdAt: existing?.createdAt || Date.now(),
      updatedAt: Date.now()
    };

    try {
      await setDoc(doc(db, "teams", code, "songs", id), songData);
      closeModal();
      showToast(existingId ? "Song updated!" : "Song added to catalog!", "success");
    } catch (err) {
      showToast(err.message || "Failed to save song.", "error");
    }
  },

  async deleteSong(songId) {
    if (!confirm("Delete this song from the team catalog?")) return;
    const code = state.profile?.teamCode;
    if (!code || !songId) return;
    try {
      await deleteDoc(doc(db, "teams", code, "songs", songId));
      showToast("Song deleted.", "info");
    } catch (err) {
      showToast(err.message || "Failed to delete song.", "error");
    }
  },

  // Team Roster & Role Management
  async assignMemberRole(memberUid, newMinistryRole, memberEmail = "", memberName = "") {
    if (!memberUid || !state.profile) return;
    const isSub = isSubstituteLeader(newMinistryRole);
    const newAuthRole = isSub ? "Leader" : "Member";

    try {
      await updateDoc(doc(db, "users", memberUid), {
        ministryRole: newMinistryRole,
        role: newAuthRole
      });
      showToast(`Assigned ${memberName || "member"} as ${newMinistryRole}!`, "success");
    } catch (err) {
      showToast(err.message || "Failed to update member role.", "error");
    }
  },

  async removeMemberFromTeam(memberUid, memberName) {
    if (!confirm(`Remove ${memberName} from the team?`)) return;
    const code = state.profile?.teamCode;
    if (!code || !memberUid) return;
    try {
      await updateDoc(doc(db, "users", memberUid), {
        removedByLeader: true,
        removedTeamCode: code,
        teamCode: "",
        teamName: "",
        role: "Member",
        ministryRole: "Member"
      });
      const removedList = Array.from(new Set([...(state.team?.removedMembers || []), memberUid]));
      await updateDoc(doc(db, "teams", code), { removedMembers: removedList });
      showToast(`${memberName} removed from team.`, "info");
    } catch (err) {
      showToast(err.message || "Could not remove member.", "error");
    }
  },

  async handleRejoinRequest(reqId, status) {
    const code = state.profile?.teamCode;
    const req = state.rejoinRequests.find((r) => r.id === reqId);
    if (!code || !req) return;

    try {
      await updateDoc(doc(db, "teams", code, "rejoin_requests", reqId), {
        status,
        updatedAt: Date.now()
      });
      if (status === "Approved") {
        const updatedRemoved = (state.team?.removedMembers || []).filter(
          (item) => item !== req.userUid && item.toLowerCase() !== (req.userEmail || "").toLowerCase()
        );
        await updateDoc(doc(db, "teams", code), { removedMembers: updatedRemoved });
        await updateDoc(doc(db, "users", req.userUid), {
          removedByLeader: false,
          removedTeamCode: "",
          teamCode: code,
          teamName: state.team?.teamName || state.profile?.teamName || "",
          role: "Member",
          ministryRole: "Member"
        });
      }
      showToast(`Rejoin request ${status.toLowerCase()}.`, "success");
    } catch (err) {
      showToast(err.message || "Failed to process rejoin request.", "error");
    }
  },

  openTransferLeadershipModal() {
    const candidates = state.roster.filter((m) => m.uid !== state.profile?.uid);
    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <h2 class="text-lg font-bold text-white"><i class="fas fa-crown text-amber-400 mr-2"></i>Transfer Worship Leadership</h2>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-white"><i class="fas fa-xmark"></i></button>
        </div>
        <p class="text-xs text-slate-300">Select a team member (preferably a Substitute Leader) to promote to primary Worship Leader.</p>
        <form onsubmit="window.appActions.confirmTransferLeadership(event)" class="space-y-4">
          <select name="targetUid" required class="w-full px-3.5 py-2.5 rounded-xl bg-slate-900 border border-white/15 text-xs text-white">
            <option value="">-- Select New Worship Leader --</option>
            ${candidates.map((m) => `<option value="${escapeHtml(m.uid)}">${escapeHtml(m.fullName || m.username)} (${escapeHtml(m.ministryRole || "Member")})</option>`).join("")}
          </select>
          <label class="flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
            <input type="checkbox" name="leaveAfterTransfer" class="accent-rose-500" />
            Also leave the team after transferring leadership
          </label>
          <div class="flex justify-end gap-2">
            <button type="button" onclick="window.appActions.closeModal()" class="px-4 py-2 rounded-xl bg-white/5 text-xs text-slate-300">Cancel</button>
            <button type="submit" class="px-5 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-black font-bold text-xs">Transfer Leadership</button>
          </div>
        </form>
      </div>
    `);
  },

  async confirmTransferLeadership(e) {
    e.preventDefault();
    const targetUid = e.target.targetUid.value;
    const leaveAfter = e.target.leaveAfterTransfer.checked;
    const code = state.profile?.teamCode;
    const targetMember = state.roster.find((m) => m.uid === targetUid);
    if (!targetUid || !code || !targetMember) return;

    try {
      await updateDoc(doc(db, "teams", code), {
        leaderUid: targetMember.uid,
        leaderName: targetMember.fullName || targetMember.username || "Worship Leader",
        leaderEmail: (targetMember.email || "").toLowerCase()
      });
      await updateDoc(doc(db, "users", targetMember.uid), {
        role: "Leader",
        ministryRole: "Worship Leader"
      });
      if (leaveAfter) {
        await updateDoc(doc(db, "users", state.profile.uid), {
          role: "Member",
          ministryRole: "Member",
          teamCode: "",
          teamName: ""
        });
      } else {
        await updateDoc(doc(db, "users", state.profile.uid), {
          role: "Member",
          ministryRole: "Member"
        });
      }
      closeModal();
      showToast(`Transferred Worship Leadership to ${targetMember.fullName || targetMember.username}!`, "success");
    } catch (err) {
      showToast(err.message || "Leadership transfer failed.", "error");
    }
  },

  // Team Chat Actions
  setChatReply(msgId) {
    const msg = state.messages.find((m) => m.id === msgId);
    if (!msg) return;
    state.chatReplyTo = msg;
    state.chatEditingMessage = null;
    renderApp();
    document.getElementById("chatInputBox")?.focus();
  },

  clearChatReply() {
    state.chatReplyTo = null;
    renderApp();
  },

  startEditChatMessage(msgId) {
    const msg = state.messages.find((m) => m.id === msgId);
    if (!msg) return;
    state.chatEditingMessage = msg;
    state.chatReplyTo = null;
    renderApp();
    document.getElementById("chatInputBox")?.focus();
  },

  clearChatEdit() {
    state.chatEditingMessage = null;
    renderApp();
  },

  async sendChatMessage(e) {
    e.preventDefault();
    const input = document.getElementById("chatInputBox");
    const text = input ? input.value.trim() : "";
    const code = state.profile?.teamCode;
    if (!text || !code || !state.profile) return;
    if (input) input.value = "";

    try {
      if (state.chatEditingMessage) {
        const msgId = state.chatEditingMessage.id;
        state.chatEditingMessage = null;
        await updateDoc(doc(db, "teams", code, "messages", msgId), {
          text,
          isEdited: true,
          editedAt: Date.now()
        });
        return;
      }

      const id = `msg_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
      const reply = state.chatReplyTo;
      state.chatReplyTo = null;

      await setDoc(doc(db, "teams", code, "messages", id), {
        id,
        teamCode: code,
        senderUid: state.profile.uid,
        senderName: state.profile.fullName || state.profile.username || "Member",
        senderRole: state.profile.ministryRole || state.profile.role || "Member",
        senderPhotoUri: state.profile.photoUri || "",
        text,
        mediaUrl: "",
        timestamp: Date.now(),
        isEdited: false,
        editedAt: 0,
        isDeleted: false,
        deletedAt: 0,
        replyToId: reply ? reply.id : "",
        replyToSender: reply ? reply.senderName : "",
        replyToText: reply ? (reply.text || "[Photo]") : ""
      });
    } catch (err) {
      showToast(err.message || "Failed to send message.", "error");
    }
  },

  async sendChatPhoto(file) {
    if (!file || !state.profile?.teamCode) return;
    const code = state.profile.teamCode;
    showToast("Uploading photo to chat...", "info");
    try {
      const mediaUrl = await uploadImageToCloudinary(file, 900, 0.85);
      const id = `msg_${Date.now()}`;
      await setDoc(doc(db, "teams", code, "messages", id), {
        id,
        teamCode: code,
        senderUid: state.profile.uid,
        senderName: state.profile.fullName || state.profile.username || "Member",
        senderRole: state.profile.ministryRole || state.profile.role || "Member",
        senderPhotoUri: state.profile.photoUri || "",
        text: "",
        mediaUrl,
        timestamp: Date.now(),
        isEdited: false,
        editedAt: 0,
        isDeleted: false,
        deletedAt: 0,
        replyToId: "",
        replyToSender: "",
        replyToText: ""
      });
      showToast("Photo sent!", "success");
    } catch (err) {
      showToast(err.message || "Photo upload failed.", "error");
    }
  },

  async retractChatMessage(msgId) {
    const code = state.profile?.teamCode;
    if (!code || !msgId) return;
    try {
      await updateDoc(doc(db, "teams", code, "messages", msgId), {
        text: "",
        mediaUrl: "",
        isDeleted: true,
        deletedAt: Date.now()
      });
    } catch (err) {
      showToast(err.message || "Could not unsend message.", "error");
    }
  },

  // Profile & Team Leave Actions
  async uploadProfileAvatar(file) {
    if (!file || !state.profile) return;
    showToast("Uploading profile photo...", "info");
    try {
      const url = await uploadImageToCloudinary(file, 512, 0.85);
      await updateDoc(doc(db, "users", state.profile.uid), { photoUri: url });
      state.profile.photoUri = url;
      renderApp();
      showToast("Profile photo updated!", "success");
    } catch (err) {
      showToast(err.message || "Avatar upload failed.", "error");
    }
  },

  async saveProfileChanges(e) {
    e.preventDefault();
    const form = e.target;
    if (!state.profile) return;
    const fullName = form.fullName.value.trim();
    const username = form.username.value.trim();
    const phone = form.phone.value.trim();

    if (phone && !/^(\+639\d{9}|639\d{9}|09\d{9})$/.test(phone)) {
      return showToast("Enter a valid Philippine mobile number (09XXXXXXXXX or +639XXXXXXXXX).", "error");
    }

    try {
      await updateDoc(doc(db, "users", state.profile.uid), {
        fullName,
        username,
        phone
      });
      showToast("Profile saved and synced!", "success");
    } catch (err) {
      showToast(err.message || "Could not update profile.", "error");
    }
  },

  async leaveCurrentTeam() {
    if (!state.profile) return;
    if (isWorshipLeader(state.profile.ministryRole, state.profile.role) && state.roster.length > 1) {
      this.openTransferLeadershipModal();
      return showToast("As Worship Leader, please transfer leadership before leaving the team.", "info");
    }
    if (!confirm("Are you sure you want to leave your current ministry team?")) return;
    try {
      await updateDoc(doc(db, "users", state.profile.uid), {
        teamCode: "",
        teamName: "",
        role: "Member",
        ministryRole: "Member"
      });
      showToast("You have left the team.", "info");
    } catch (err) {
      showToast(err.message || "Could not leave team.", "error");
    }
  }
};
