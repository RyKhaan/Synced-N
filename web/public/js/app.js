import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  signInAnonymously,
  updateProfile,
  signOut,
  GoogleAuthProvider,
  signInWithPopup,
  sendPasswordResetEmail,
  updatePassword
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

import {
  FIREBASE_CONFIG,
  DEFAULT_MINISTRY_ROLES,
  MUSICAL_KEYS,
  CHROMATIC_ROOTS,
  DEFAULT_SEED_SONGS
} from "./config.js";
import {
  escapeHtml,
  generateTeamCode,
  evaluatePassword,
  validateEmailForAuth,
  isValidName,
  isValidUsername,
  isValidPhilippinePhone,
  isLoginLocked,
  getLoginLockRemainingSeconds,
  recordLoginFailure,
  resetLoginAttempts,
  computeSessionHmac,
  verifySessionHmac,
  hashPasswordPbkdf2,
  verifyPasswordPbkdf2,
  isWorshipLeader,
  hasLeaderPrivileges,
  uploadImageToCloudinary,
  extractYoutubeVideoId,
  getNotePitch,
  isMinorKey,
  shouldUseFlatsForKey,
  getTargetKeyLabel,
  renderFormattedChordSheet,
  formatBpmDisplay,
  validateBpmValue,
  MetronomeEngine,
  sendDirectBrevoOtp,
  verifyDirectBrevoOtp,
  renderGuitarChordsBarHtml,
  extractUniqueChordsFromSheet
} from "./utils.js";
import {
  getAvatarHtml,
  getSongCoverHtml,
  renderPasswordInputHtml,
  renderPasswordStrengthBoxHtml,
  renderHomeView,
  renderCalendarView,
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

const nowInit = new Date();
const todayIsoInit = `${nowInit.getFullYear()}-${String(nowInit.getMonth() + 1).padStart(2, "0")}-${String(nowInit.getDate()).padStart(2, "0")}`;

// Central Reactive State
const state = {
  authLoading: true,
  authUser: null,
  currentUser: null,
  currentTeam: null,
  overrideProfileUid: null,
  theme: (typeof localStorage !== "undefined" && localStorage.getItem("synced_n_theme") === "dark") ? "dark" : "light",
  activeTab: "home", // home | calendar | schedule | songs | roster | chat | profile
  authMode: "login", // login | register | reset | otp | success
  authSuccessMessage: "",
  pendingAuth: {
    flow: "", // "register" | "reset"
    username: "",
    fullName: "",
    email: "",
    password: "",
    ministryRole: "Member",
    sentAt: 0,
    expiresAt: 0,
    resendCooldownSec: 0,
    cooldownTimer: null
  },
  songSearchQuery: "",
  songKeyFilter: "",
  calendar: {
    year: nowInit.getFullYear(),
    month: nowInit.getMonth(),
    selectedDate: todayIsoInit
  },
  teamMembers: [],
  services: [],
  songs: [],
  announcements: [],
  substitutions: [],
  messages: [],
  rejoinRequests: [],
  chatReplyTarget: null,
  chatEditingMessage: null,
  chatScroll: {
    isPinnedToBottom: true,
    savedScrollTop: 0,
    lastMessageCount: 0,
    lastMessageId: "",
    pendingSmoothScroll: false
  },
  stageModal: {
    songId: null,
    semitones: 0,
    useFlatsOverride: null,
    lyricsOnly: false,
    fontSize: 15,
    autoScrollSpeed: 0,
    scrollTimer: null
  }
};

// Apply & Persist Theme ("light" | "dark")
function applyThemeToDom(themeMode) {
  const mode = themeMode === "dark" ? "dark" : "light";
  state.theme = mode;
  if (typeof document !== "undefined") {
    document.documentElement.setAttribute("data-theme", mode);
    document.documentElement.classList.toggle("dark", mode === "dark");
    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme) {
      metaTheme.setAttribute("content", mode === "dark" ? "#0B0D1D" : "#10113F");
    }
  }
  try {
    if (typeof localStorage !== "undefined") {
      localStorage.setItem("synced_n_theme", mode);
    }
  } catch {
    // Ignore storage errors in restricted environments
  }
}

applyThemeToDom(state.theme);

let lastRenderedViewKey = "";
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

// ============================================================================
// Trusted Backend OTP API Client & Mobile-Parity Auth Helpers
// ============================================================================

/**
 * Calls the trusted backend authentication / OTP service (`/api/auth/*`).
 * If served from a static host that returns HTML for `/api/auth/*`, automatically
 * falls back to `http://localhost:3000${path}`.
 */
async function callAuthApi(path, payload = {}) {
  const endpoints = [path];
  if (typeof window !== "undefined" && window.location?.origin && !window.location.origin.includes("localhost:3000")) {
    endpoints.push(`http://localhost:3000${path}`);
  }

  let lastError = null;
  for (const url of endpoints) {
    try {
      const response = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json"
        },
        body: JSON.stringify(payload)
      });
      const contentType = response.headers.get("content-type") || "";
      if (!contentType.includes("application/json")) {
        throw new Error("Non-JSON response from endpoint");
      }
      const data = await response.json();
      return { status: response.status, ok: response.ok, ...data };
    } catch (err) {
      lastError = err;
    }
  }

  // Graceful Fallback if backend server is not running or on static hosting (e.g. Firebase Hosting)
  if (path === "/api/auth/send-otp") {
    try {
      const direct = await sendDirectBrevoOtp({
        recipientEmail: payload.email,
        pendingUsername: payload.username || payload.recipientName || "",
        purpose: payload.purpose || "reset"
      });
      return { status: 200, ok: true, success: true, ...direct };
    } catch (directErr) {
      throw directErr;
    }
  }

  if (path === "/api/auth/verify-otp") {
    const directVerify = verifyDirectBrevoOtp({
      email: payload.email,
      enteredCode: payload.code,
      purpose: payload.purpose
    });
    if (directVerify.ok) {
      return {
        status: 200,
        ok: true,
        verified: true,
        verificationToken: "direct_token_" + Date.now()
      };
    }
    return {
      status: 400,
      ok: false,
      verified: false,
      errorMessage: directVerify.error || "Invalid verification code."
    };
  }

  if (path === "/api/auth/consume-verification") {
    return { status: 200, ok: true, valid: true };
  }

  if (path === "/api/auth/can-resend-otp") {
    return { status: 200, ok: true, canResend: true, allowed: true, remainingSeconds: 0 };
  }

  if (path === "/api/auth/login-rate-limit") {
    return { status: 200, ok: true, locked: false, remainingSeconds: 0, remainingAttempts: 5 };
  }

  throw lastError || new Error("Unable to reach authentication verification server.");
}

/**
 * Starts the 60-second resend cooldown timer matching `EmailOtpService.RESEND_COOLDOWN_MS`.
 */
function startOtpCooldownTimer(seconds = 60) {
  if (state.pendingAuth.cooldownTimer) {
    clearInterval(state.pendingAuth.cooldownTimer);
    state.pendingAuth.cooldownTimer = null;
  }
  state.pendingAuth.resendCooldownSec = Math.max(0, Math.ceil(seconds));

  const updateResendBtnDom = () => {
    const btn = document.getElementById("otp-resend-btn");
    if (!btn) return;
    if (state.pendingAuth.resendCooldownSec > 0) {
      btn.disabled = true;
      btn.textContent = `Resend in ${state.pendingAuth.resendCooldownSec}s`;
      btn.className = "text-xs font-bold text-slate-400 cursor-not-allowed";
    } else {
      btn.disabled = false;
      btn.textContent = "Resend Code";
      btn.className = "text-xs font-bold text-[#500568] hover:underline cursor-pointer";
    }
  };

  updateResendBtnDom();
  if (state.pendingAuth.resendCooldownSec > 0) {
    state.pendingAuth.cooldownTimer = setInterval(() => {
      state.pendingAuth.resendCooldownSec = Math.max(0, state.pendingAuth.resendCooldownSec - 1);
      updateResendBtnDom();
      if (state.pendingAuth.resendCooldownSec <= 0 && state.pendingAuth.cooldownTimer) {
        clearInterval(state.pendingAuth.cooldownTimer);
        state.pendingAuth.cooldownTimer = null;
      }
    }, 1000);
  }
}

/**
 * Wipes any pending registration / reset credentials and stops OTP timers.
 */
function clearPendingAuthState() {
  if (state.pendingAuth.cooldownTimer) {
    clearInterval(state.pendingAuth.cooldownTimer);
  }
  state.pendingAuth = {
    flow: "",
    username: "",
    fullName: "",
    email: "",
    password: "",
    ministryRole: "Member",
    sentAt: 0,
    expiresAt: 0,
    resendCooldownSec: 0,
    cooldownTimer: null
  };
}

/**
 * Signs and persists session metadata using HMAC-SHA256 (matching `SessionManager.kt`).
 */
async function persistVerifiedSession(userProfile, isOverride = false) {
  if (!userProfile || typeof sessionStorage === "undefined") return;
  try {
    const email = String(userProfile.email || "").trim().toLowerCase();
    const role = String(userProfile.role || "Member").trim();
    const teamCode = String(userProfile.teamCode || "").trim().toUpperCase();
    const hmac = await computeSessionHmac(email, role, teamCode);
    sessionStorage.setItem("synced_n_session_email", email);
    sessionStorage.setItem("synced_n_session_role", role);
    sessionStorage.setItem("synced_n_session_team", teamCode);
    sessionStorage.setItem("synced_n_session_hmac", hmac);
    if (isOverride && userProfile.uid) {
      sessionStorage.setItem("synced_n_override_uid", userProfile.uid);
    } else if (!isOverride) {
      sessionStorage.removeItem("synced_n_override_uid");
    }
  } catch {
    // Ignore storage errors
  }
}

/**
 * Restores and cryptographically verifies any override profile UID stored in sessionStorage.
 * If session data was tampered with (`verifySessionHmac === false`), clears the session.
 */
async function restoreOverrideUidFromSession() {
  if (typeof sessionStorage === "undefined") return null;
  try {
    const overrideUid = sessionStorage.getItem("synced_n_override_uid");
    if (!overrideUid) return null;
    const email = sessionStorage.getItem("synced_n_session_email") || "";
    const role = sessionStorage.getItem("synced_n_session_role") || "";
    const team = sessionStorage.getItem("synced_n_session_team") || "";
    const storedHmac = sessionStorage.getItem("synced_n_session_hmac") || "";
    const isValid = await verifySessionHmac(email, role, team, storedHmac);
    if (!isValid) {
      clearVerifiedSession();
      return null;
    }
    return overrideUid;
  } catch {
    return null;
  }
}

function clearVerifiedSession() {
  state.overrideProfileUid = null;
  if (typeof sessionStorage === "undefined") return;
  try {
    sessionStorage.removeItem("synced_n_override_uid");
    sessionStorage.removeItem("synced_n_session_email");
    sessionStorage.removeItem("synced_n_session_role");
    sessionStorage.removeItem("synced_n_session_team");
    sessionStorage.removeItem("synced_n_session_hmac");
  } catch {
    // Ignore storage errors
  }
}

/**
 * Checks if an email is already registered in Firestore (`/users` or `/password_resets`),
 * matching `FirestoreManager.checkEmailExistsInFirestore(email)` on Android.
 */
async function checkEmailExistsInFirestore(email) {
  const cleanEmail = String(email || "").trim();
  const lowerEmail = cleanEmail.toLowerCase();
  if (!cleanEmail) return false;

  try {
    const qExact = query(collection(db, "users"), where("email", "==", cleanEmail));
    const snapExact = await getDocs(qExact);
    if (!snapExact.empty) return true;

    if (lowerEmail !== cleanEmail) {
      const qLower = query(collection(db, "users"), where("email", "==", lowerEmail));
      const snapLower = await getDocs(qLower);
      if (!snapLower.empty) return true;
    }
  } catch (err) {
    console.warn("checkEmailExistsInFirestore warning:", err);
  }
  return false;
}

/**
 * Looks up a user profile in `/users` by email (case-tolerant), matching `FirestoreManager.getUserByEmail`.
 */
async function getUserProfileByEmail(email) {
  const cleanEmail = String(email || "").trim();
  const lowerEmail = cleanEmail.toLowerCase();
  if (!cleanEmail) return null;

  try {
    const qExact = query(collection(db, "users"), where("email", "==", cleanEmail));
    const snapExact = await getDocs(qExact);
    if (!snapExact.empty) {
      const d = snapExact.docs[0];
      return normalizeUserDoc(d.id, d.data());
    }

    if (lowerEmail !== cleanEmail) {
      const qLower = query(collection(db, "users"), where("email", "==", lowerEmail));
      const snapLower = await getDocs(qLower);
      if (!snapLower.empty) {
        const d = snapLower.docs[0];
        return normalizeUserDoc(d.id, d.data());
      }
    }
  } catch (err) {
    console.warn("getUserProfileByEmail warning:", err);
  }
  return null;
}

/**
 * Retrieves the PBKDF2-HMAC-SHA256 password hash from `/password_resets/{emailLower}`,
 * matching `FirestoreManager.getPasswordReset(email)` on Android.
 */
async function getPasswordResetHash(email) {
  const lowerEmail = String(email || "").trim().toLowerCase();
  if (!lowerEmail) return null;
  try {
    const snap = await getDoc(doc(db, "password_resets", lowerEmail));
    if (snap.exists()) {
      const pwd = String(snap.data()?.password || "").trim();
      return pwd || null;
    }
  } catch (err) {
    console.warn("getPasswordResetHash warning:", err);
  }
  return null;
}

/**
 * Hashes the new password with PBKDF2-HMAC-SHA256 and saves it to `/password_resets/{emailLower}`
 * (and updates `resetPassword` on any matching `/users/{uid}` doc if authenticated),
 * matching `FirestoreManager.savePasswordReset(email, newPassword)` on Android.
 */
async function savePasswordResetInFirestore(email, newPlainPassword) {
  const cleanEmail = String(email || "").trim();
  const lowerEmail = cleanEmail.toLowerCase();
  const cleanPassword = String(newPlainPassword || "").trim();
  const hashedPassword = await hashPasswordPbkdf2(cleanPassword);

  // 1. Write to /password_resets/{lowerEmail} (100% compliant with isValidPasswordReset in firestore.rules)
  await setDoc(doc(db, "password_resets", lowerEmail), {
    email: lowerEmail,
    password: hashedPassword,
    updatedAt: Date.now()
  });

  // 2. Also update `resetPassword` on matching `/users/{uid}` documents when accessible
  try {
    const qExact = query(collection(db, "users"), where("email", "==", cleanEmail));
    const snapExact = await getDocs(qExact);
    for (const userDoc of snapExact.docs) {
      await saveCleanUserProfile(userDoc.id, { resetPassword: hashedPassword });
    }
    if (lowerEmail !== cleanEmail) {
      const qLower = query(collection(db, "users"), where("email", "==", lowerEmail));
      const snapLower = await getDocs(qLower);
      for (const userDoc of snapLower.docs) {
        await saveCleanUserProfile(userDoc.id, { resetPassword: hashedPassword });
      }
    }
  } catch {
    // Unauthenticated users may not have write access to /users/{uid}; /password_resets/{email} is authoritative
  }

  return hashedPassword;
}

// Metronome Instance with Visual Beat Indicator Support
const metronome = new MetronomeEngine((beat, isAccent, _currentBpm, totalBeats) => {
  const indicator = document.getElementById("metronome-beat-dot");
  const statusEl = document.getElementById("stage-metronome-status");
  const playBtn = document.getElementById("stage-metronome-play-btn");

  if (indicator) {
    if (beat === 0) {
      indicator.className = "w-3 h-3 rounded-full bg-slate-300 transition-transform duration-75";
    } else if (isAccent) {
      indicator.className = "w-3 h-3 rounded-full bg-[#D946EF] scale-125 shadow-xs transition-transform duration-75";
    } else {
      indicator.className = "w-3 h-3 rounded-full bg-[#500568] scale-110 transition-transform duration-75";
    }
  }

  if (statusEl) {
    if (beat === 0) {
      const intervalMs = (60000 / metronome.bpm).toFixed(1);
      statusEl.textContent = `Ready · ${formatBpmDisplay(metronome.bpm)} BPM (${intervalMs} ms/beat)`;
    } else {
      statusEl.textContent = `Beat ${beat} of ${totalBeats} · ${formatBpmDisplay(metronome.bpm)} BPM`;
    }
  }

  if (playBtn) {
    if (metronome.isPlaying) {
      playBtn.className = "btn btn-danger btn-sm font-semibold min-w-[84px]";
      playBtn.innerHTML = `<i class="fa-solid fa-stop text-[10px]"></i> Stop`;
    } else {
      playBtn.className = "btn btn-primary btn-sm font-semibold min-w-[84px]";
      playBtn.innerHTML = `<i class="fa-solid fa-play text-[10px]"></i> Start`;
    }
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
    info: "border-l-4 border-l-[#500568]"
  };
  const iconMap = {
    success: "fa-circle-check text-emerald-600",
    error: "fa-circle-exclamation text-rose-600",
    warning: "fa-triangle-exclamation text-amber-500",
    info: "fa-circle-info text-[#500568]"
  };

  const toast = document.createElement("div");
  toast.className = `pointer-events-auto flex items-center gap-3 px-4 py-3 rounded-xl bg-white border border-slate-200 ${borderMap[type] || borderMap.info} text-slate-800 shadow-lg text-xs font-semibold animate-fade-in`;
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

// Modal Helper (supports options.closeOnBackdrop = false for Song Creation / Addition modals)
function openModal(htmlContent, maxWidthClass = "max-w-lg", options = {}) {
  const modalRoot = document.getElementById("modal-root");
  if (!modalRoot) return;
  const closeOnBackdrop = options?.closeOnBackdrop !== false;
  modalRoot.setAttribute("data-close-on-backdrop", closeOnBackdrop ? "true" : "false");
  modalRoot.classList.remove("hidden");
  modalRoot.classList.add("flex");
  modalRoot.innerHTML = `
    <div class="modal-card w-full ${maxWidthClass} rounded-2xl p-3.5 sm:p-6 text-slate-900 animate-scale-in my-auto max-h-[94dvh] sm:max-h-[92vh] overflow-y-auto" onclick="event.stopPropagation()">
      ${htmlContent}
    </div>
  `;
  modalRoot.onclick = closeOnBackdrop
    ? (e) => {
        if (e.target === modalRoot) closeModal();
      }
    : null;
}

function closeModal() {
  if (state.stageModal.scrollTimer) {
    clearInterval(state.stageModal.scrollTimer);
    state.stageModal.scrollTimer = null;
  }
  state.stageModal.songId = null;
  metronome.stop();
  const modalRoot = document.getElementById("modal-root");
  if (!modalRoot) return;
  modalRoot.removeAttribute("data-close-on-backdrop");
  modalRoot.onclick = null;
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

  unsubTeamDoc = onSnapshot(
    doc(db, "teams", code),
    (snap) => {
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
    },
    (err) => {
      console.warn("Team doc snapshot error:", err);
      renderApp();
    }
  );

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
    },
    (err) => {
      console.warn("Members snapshot error:", err);
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
    },
    (err) => {
      console.warn("Services snapshot error:", err);
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
    },
    (err) => {
      console.warn("Songs snapshot error:", err);
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
    },
    (err) => {
      console.warn("Announcements snapshot error:", err);
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
    },
    (err) => {
      console.warn("Substitutions snapshot error:", err);
      renderApp();
    }
  );

  unsubMessages = onSnapshot(
    collection(db, "teams", code, "messages"),
    (snap) => {
      const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      list.sort((a, b) => Number(a.timestamp || 0) - Number(b.timestamp || 0));

      const prevCount = state.chatScroll.lastMessageCount;
      const prevLastId = state.chatScroll.lastMessageId;
      const newCount = list.length;
      const newestMsg = newCount > 0 ? list[newCount - 1] : null;
      const newLastId = newestMsg?.id || "";
      const hasNewMessages = newCount > prevCount || (newLastId && newLastId !== prevLastId);

      // If this is the initial snapshot load or the current user sent the latest message, pin to bottom
      if (prevCount === 0 || (hasNewMessages && newestMsg?.senderUid === state.currentUser?.uid)) {
        state.chatScroll.isPinnedToBottom = true;
      }

      if (hasNewMessages && prevCount > 0 && state.chatScroll.isPinnedToBottom) {
        state.chatScroll.pendingSmoothScroll = true;
      }

      state.chatScroll.lastMessageCount = newCount;
      state.chatScroll.lastMessageId = newLastId;
      state.messages = list;
      renderApp();
    },
    (err) => {
      console.warn("Messages snapshot error:", err);
      renderApp();
    }
  );

  unsubRejoinRequests = onSnapshot(
    collection(db, "teams", code, "rejoin_requests"),
    (snap) => {
      state.rejoinRequests = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      renderApp();
    },
    (err) => {
      console.warn("Rejoin requests snapshot error:", err);
      renderApp();
    }
  );
}

let loginVerificationInProgress = false;

/**
 * Subscribes to the authenticated user's `/users/{effectiveUid}` document and
 * automatically syncs their team workspace and HMAC-signed session metadata.
 */
async function activateAuthenticatedSession(fbUser, overrideUid = null) {
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

  // For real authenticated users (email/password or Google), the canonical Firestore UID is fbUser.uid
  const effectiveUid = (!fbUser.isAnonymous ? fbUser.uid : (overrideUid || state.overrideProfileUid || fbUser.uid));
  if (!fbUser.isAnonymous) {
    state.overrideProfileUid = null;
  }
  state.authUser = fbUser;
  const userRef = doc(db, "users", effectiveUid);

  try {
    const initialSnap = await getDoc(userRef);
    if (!initialSnap.exists()) {
      // If this is an anonymous fallback session without an existing user doc, sign out
      if (fbUser.isAnonymous && !overrideUid) {
        clearVerifiedSession();
        await signOut(auth);
        return;
      }
      // Check if an existing profile exists by email (e.g. from an old deleted account)
      const existingByEmail = fbUser.email ? await getUserProfileByEmail(fbUser.email) : null;
      const defaultName = existingByEmail?.fullName || fbUser.displayName || (fbUser.email ? fbUser.email.split("@")[0] : "Member");
      const defaultUsername = existingByEmail?.username || defaultName.split(/\s+/)[0] || "Member";

      const freshProfileData = {
        uid: effectiveUid,
        fullName: defaultName,
        username: defaultUsername,
        email: fbUser.email || existingByEmail?.email || "",
        role: existingByEmail?.role || "Member",
        ministryRole: existingByEmail?.ministryRole || "Member",
        teamCode: existingByEmail?.teamCode || "",
        teamName: existingByEmail?.teamName || "",
        photoUri: fbUser.photoURL || existingByEmail?.photoUri || "",
        unavailableRanges: existingByEmail?.unavailableRanges || []
      };

      await saveCleanUserProfile(effectiveUid, freshProfileData);

      // Clean up orphaned document if it belonged to a previous old UID
      if (existingByEmail && existingByEmail.uid && existingByEmail.uid !== effectiveUid) {
        try {
          await deleteDoc(doc(db, "users", existingByEmail.uid));
        } catch {
          // Ignore if firestore rules prevent deleting another doc
        }
      }
    } else {
      // Automatically heal any legacy web fields so future updates never fail firestore.rules
      const raw = initialSnap.data();
      if ("name" in raw || "teamId" in raw || "profilePictureUrl" in raw || "unavailableDates" in raw || "password" in raw) {
        await saveCleanUserProfile(effectiveUid, raw);
      }
    }
  } catch (err) {
    console.warn("Initial profile sync check:", err);
  }

  let activeSubscribedTeamCode = null;
  unsubUserDoc = onSnapshot(
    doc(db, "users", effectiveUid),
    (snap) => {
      state.authLoading = false;
      if (!snap.exists()) {
        // Fallback: immediately provide currentUser so the workspace loads and user is never locked out
        const defaultName = fbUser.displayName || (fbUser.email ? fbUser.email.split("@")[0] : "Member");
        const fallback = normalizeUserDoc(effectiveUid, {
          uid: effectiveUid,
          fullName: defaultName,
          username: defaultName.split(/\s+/)[0] || "Member",
          email: fbUser.email || "",
          role: "Member",
          ministryRole: "Member",
          teamCode: "",
          teamName: "",
          photoUri: fbUser.photoURL || ""
        });
        state.currentUser = fallback;
        renderApp();
        saveCleanUserProfile(effectiveUid, fallback).catch(() => {});
        return;
      }
      const normalized = normalizeUserDoc(snap.id, snap.data());
      state.currentUser = normalized;
      persistVerifiedSession(normalized, Boolean(state.overrideProfileUid));

      const nextTeamCode = (!normalized.removedByLeader && normalized.teamCode) ? normalized.teamCode : "";
      if (nextTeamCode !== activeSubscribedTeamCode) {
        activeSubscribedTeamCode = nextTeamCode;
        if (nextTeamCode) {
          state.currentTeam = {
            teamCode: nextTeamCode,
            teamName: normalized.teamName || "Worship Ministry"
          };
          renderApp();
          subscribeToTeamWorkspace(nextTeamCode);
        } else {
          clearTeamListeners();
          renderApp();
        }
      } else {
        renderApp();
      }
    },
    (err) => {
      console.warn("User profile snapshot observer warning:", err);
      state.authLoading = false;
      if (!state.currentUser) {
        const defaultName = fbUser.displayName || (fbUser.email ? fbUser.email.split("@")[0] : "Member");
        state.currentUser = normalizeUserDoc(effectiveUid, {
          uid: effectiveUid,
          fullName: defaultName,
          username: defaultName.split(/\s+/)[0] || "Member",
          email: fbUser.email || "",
          role: "Member",
          ministryRole: "Member",
          teamCode: "",
          teamName: "",
          photoUri: fbUser.photoURL || ""
        });
      }
      renderApp();
    }
  );
}

// Auth State Observer
onAuthStateChanged(auth, async (fbUser) => {
  if (loginVerificationInProgress) {
    return;
  }

  if (!fbUser) {
    if (unsubUserDoc) {
      unsubUserDoc();
      unsubUserDoc = null;
    }
    clearTeamListeners();
    clearVerifiedSession();
    state.authLoading = false;
    state.authUser = null;
    state.currentUser = null;
    renderApp();
    return;
  }

  if (!fbUser.isAnonymous) {
    state.overrideProfileUid = null;
  } else {
    const restoredOverrideUid = await restoreOverrideUidFromSession();
    if (!restoredOverrideUid && !state.overrideProfileUid) {
      clearVerifiedSession();
      try {
        await signOut(auth);
      } catch (_) {}
      state.authLoading = false;
      state.authUser = null;
      state.currentUser = null;
      renderApp();
      return;
    }
    if (restoredOverrideUid) {
      state.overrideProfileUid = restoredOverrideUid;
    }
  }

  try {
    await activateAuthenticatedSession(fbUser, state.overrideProfileUid);
  } catch (err) {
    console.error("Session activation failed:", err);
    state.authLoading = false;
    renderApp();
  }
});

// Watchdog: Ensure the web app never stays locked in the initial loading state (max 2s)
setTimeout(() => {
  if (state.authLoading) {
    console.warn("Auth initialization timed out; unlocking workspace for user.");
    state.authLoading = false;
    renderApp();
  }
}, 2000);

/**
 * Synchronizes the Team Chat scroll position after DOM render:
 * - When opening the chat or when `isPinnedToBottom` is true, automatically positions
 *   the conversation at the latest message (`scrollHeight`).
 * - When the user has manually scrolled upward (`isPinnedToBottom === false`), preserves
 *   their `savedScrollTop` without forcibly pulling them down, and shows the floating
 *   "Latest messages" button.
 */
function syncChatScrollPosition({ smooth = false } = {}) {
  const scroller = document.getElementById("chat-messages-scroll");
  if (!scroller) return;
  const jumpBtn = document.getElementById("chat-scroll-bottom-btn");

  if (state.chatScroll.isPinnedToBottom) {
    if (jumpBtn) {
      jumpBtn.classList.add("hidden");
      jumpBtn.classList.remove("inline-flex");
    }
    const scrollToBottom = (useSmooth) => {
      const targetTop = scroller.scrollHeight;
      if (useSmooth && typeof scroller.scrollTo === "function") {
        try {
          scroller.scrollTo({ top: targetTop, behavior: "smooth" });
        } catch {
          scroller.scrollTop = targetTop;
        }
      } else {
        scroller.scrollTop = targetTop;
      }
      state.chatScroll.savedScrollTop = scroller.scrollTop;
    };

    scrollToBottom(smooth);
    if (typeof requestAnimationFrame === "function") {
      requestAnimationFrame(() => {
        if (state.chatScroll.isPinnedToBottom) {
          scrollToBottom(smooth);
        }
      });
    }
  } else {
    scroller.scrollTop = state.chatScroll.savedScrollTop;
    if (jumpBtn) {
      jumpBtn.classList.remove("hidden");
      jumpBtn.classList.add("inline-flex");
    }
  }
}

/**
 * Messenger-Style Touch Swipe-to-Reply Gesture Engine
 * Smoothly translates message bubbles horizontally when swiped to the left,
 * presents a responsive reply indicator, protects vertical scrolling,
 * and activates reply mode when the swipe threshold is reached.
 */
function initChatSwipeToReply() {
  const container = document.getElementById("chat-messages-scroll");
  if (!container) return;
  if (container.dataset.swipeBound === "true") return;
  container.dataset.swipeBound = "true";

  let startX = 0;
  let startY = 0;
  let currentTargetBubble = null;
  let currentMsgId = null;
  let currentIndicator = null;
  let isHorizontalSwipe = false;
  let isVerticalScroll = false;
  let isDecided = false;

  container.addEventListener(
    "touchstart",
    (e) => {
      if (e.touches.length !== 1) return;
      const touch = e.touches[0];
      const row = touch.target.closest(".chat-msg-row");
      if (!row) return;

      // Ignore touches on interactive child elements like buttons, links, images
      if (touch.target.closest("button, a, input, select, textarea")) return;

      const bubble = row.querySelector(".chat-msg-bubble");
      const indicator = row.querySelector(".chat-swipe-indicator");
      if (!bubble) return;

      startX = touch.clientX;
      startY = touch.clientY;
      currentTargetBubble = bubble;
      currentIndicator = indicator;
      currentMsgId = row.getAttribute("data-msg-id");
      isHorizontalSwipe = false;
      isVerticalScroll = false;
      isDecided = false;
      bubble.style.transition = "none";
    },
    { passive: true }
  );

  container.addEventListener(
    "touchmove",
    (e) => {
      if (!currentTargetBubble || e.touches.length !== 1) return;
      const touch = e.touches[0];
      const deltaX = touch.clientX - startX;
      const deltaY = touch.clientY - startY;
      const absX = Math.abs(deltaX);
      const absY = Math.abs(deltaY);

      if (!isDecided) {
        if (absY > 7 && absY >= absX) {
          // User is scrolling vertically: lock out swipe
          isVerticalScroll = true;
          isDecided = true;
          currentTargetBubble.style.transform = "";
          if (currentIndicator) currentIndicator.style.opacity = "0";
          return;
        }
        if (deltaX < -8 && absX > absY) {
          // User is swiping horizontally to the left
          isHorizontalSwipe = true;
          isDecided = true;
        }
      }

      if (isVerticalScroll) return;

      if (isHorizontalSwipe && deltaX < 0) {
        if (e.cancelable) {
          e.preventDefault();
        }
        // Swiping toward the left: clamp between 0 and -75px
        const clamped = Math.max(-75, deltaX * 0.7);
        currentTargetBubble.style.transform = `translateX(${clamped}px)`;

        if (currentIndicator) {
          const progress = Math.min(1, Math.abs(clamped) / 40);
          currentIndicator.style.opacity = String(progress);
          currentIndicator.style.transform = `translateY(-50%) scale(${0.7 + progress * 0.35})`;

          if (Math.abs(clamped) >= 40) {
            currentIndicator.classList.add("bg-[#500568]", "text-white");
            currentIndicator.classList.remove("bg-slate-200", "text-slate-600");
          } else {
            currentIndicator.classList.remove("bg-[#500568]", "text-white");
            currentIndicator.classList.add("bg-slate-200", "text-slate-600");
          }
        }
      }
    },
    { passive: false }
  );

  const resetSwipe = () => {
    if (!currentTargetBubble) return;
    const bubble = currentTargetBubble;
    const indicator = currentIndicator;
    const msgId = currentMsgId;
    const wasHorizontal = isHorizontalSwipe;

    // Check current displacement
    const match = bubble.style.transform.match(/translateX\((-?[\d.]+)px\)/);
    const currentX = match ? parseFloat(match[1]) : 0;

    // Smooth spring back animation
    bubble.style.transition = "transform 0.22s cubic-bezier(0.18, 0.89, 0.32, 1.15)";
    bubble.style.transform = "translateX(0px)";

    if (indicator) {
      indicator.style.transition = "opacity 0.18s ease, transform 0.18s ease";
      indicator.style.opacity = "0";
      indicator.style.transform = "translateY(-50%) scale(0.7)";
      indicator.classList.remove("bg-[#500568]", "text-white");
      indicator.classList.add("bg-slate-200", "text-slate-600");
    }

    if (wasHorizontal && currentX <= -38 && msgId) {
      if (window.navigator?.vibrate) {
        try {
          window.navigator.vibrate(15);
        } catch (_) {}
      }
      window.appActions.setChatReply(msgId);
    }

    currentTargetBubble = null;
    currentIndicator = null;
    currentMsgId = null;
    isHorizontalSwipe = false;
    isVerticalScroll = false;
    isDecided = false;
  };

  container.addEventListener("touchend", resetSwipe, { passive: true });
  container.addEventListener("touchcancel", resetSwipe, { passive: true });
}

// Render Root Application (Preserving Focus & Scroll Position Without Flicker)
function renderApp() {
  const appEl = document.getElementById("app");
  if (!appEl) return;

  // Preserve active input focus, value, and cursor selection across reactive updates
  const activeEl = document.activeElement;
  const activeId = activeEl?.id || "";
  const selectionStart = typeof activeEl?.selectionStart === "number" ? activeEl.selectionStart : null;
  const selectionEnd = typeof activeEl?.selectionEnd === "number" ? activeEl.selectionEnd : null;
  const prevScrollY = window.scrollY;

  // Capture existing chat scroll state & draft input before replacing innerHTML
  const existingChatScroller = document.getElementById("chat-messages-scroll");
  if (existingChatScroller && state.activeTab === "chat") {
    const distFromBottom =
      existingChatScroller.scrollHeight - existingChatScroller.scrollTop - existingChatScroller.clientHeight;
    if (distFromBottom > 80 && !state.chatScroll.isPinnedToBottom) {
      state.chatScroll.savedScrollTop = existingChatScroller.scrollTop;
    }
  }
  const existingChatInput = document.getElementById("chat-message-input");
  const hadDraftInput = existingChatInput && !state.chatEditingMessage ? existingChatInput.value : null;

  if (state.authLoading) {
    appEl.innerHTML = renderLoadingState();
    return;
  }

  if (!state.authUser || !state.currentUser) {
    const viewKey = `auth:${state.authMode}`;
    const animate = lastRenderedViewKey !== viewKey;
    lastRenderedViewKey = viewKey;
    appEl.innerHTML = renderAuthView(animate);
    restoreFocus(activeId, selectionStart, selectionEnd);
    return;
  }

  if (!state.currentUser.teamCode || state.currentUser.removedByLeader) {
    const viewKey = "setup";
    const animate = lastRenderedViewKey !== viewKey;
    lastRenderedViewKey = viewKey;
    appEl.innerHTML = renderTeamSetupView(animate);
    restoreFocus(activeId, selectionStart, selectionEnd);
    return;
  }

  const viewKey = `tab:${state.activeTab}`;
  const animate = lastRenderedViewKey !== viewKey;
  lastRenderedViewKey = viewKey;
  appEl.innerHTML = renderDashboardShell(animate);

  // Prevent unnecessary fade animation re-triggering when already on the same tab
  if (!animate) {
    const mainInner = appEl.querySelector("main > div > .animate-fade-in");
    if (mainInner) {
      mainInner.classList.remove("animate-fade-in");
    }
    window.scrollTo(0, prevScrollY);
  }

  // Restore any in-progress chat message draft
  if (hadDraftInput !== null && !state.chatEditingMessage) {
    const restoredChatInput = document.getElementById("chat-message-input");
    if (restoredChatInput && restoredChatInput.value === "") {
      restoredChatInput.value = hadDraftInput;
    }
  }

  // Synchronize Team Chat scroll position (auto-scroll when opening or at bottom; preserve scroll when reading history)
  if (state.activeTab === "chat") {
    const useSmooth = Boolean(state.chatScroll.pendingSmoothScroll && !animate);
    state.chatScroll.pendingSmoothScroll = false;
    syncChatScrollPosition({ smooth: useSmooth });
    initChatSwipeToReply();
  }

  restoreFocus(activeId, selectionStart, selectionEnd);
}

function restoreFocus(activeId, selectionStart, selectionEnd) {
  if (!activeId) return;
  const restored = document.getElementById(activeId);
  if (restored) {
    restored.focus();
    if (selectionStart !== null && restored.setSelectionRange) {
      try {
        restored.setSelectionRange(selectionStart, selectionEnd ?? selectionStart);
      } catch {
        // Ignore on input types that don't support setSelectionRange
      }
    }
  }
}

function renderLoadingState() {
  return `
    <div class="min-h-screen flex flex-col items-center justify-center p-6 text-center bg-[#F7F7FA]">
      <div class="w-14 h-14 rounded-2xl bg-[#10113F] border border-white/10 shadow-md flex items-center justify-center mb-4">
        <img src="/assets/synced_n_logo_sharp.png" onerror="this.src='/assets/logo.png'" alt="Synced N" class="w-9 h-9 object-contain" />
      </div>
      <div class="flex items-center gap-2.5 text-[#10113F] font-semibold text-sm">
        <i class="fa-solid fa-circle-notch fa-spin text-[#500568] text-xs"></i>
        <span>Synchronizing Synced N workspace...</span>
      </div>
    </div>
  `;
}

// Authentication Screen (Login / Register / OTP Verification / Password Reset) preserving existing Web UI
function renderAuthView(animate = true) {
  const mode = state.authMode;
  const pending = state.pendingAuth;
  const cooldownSec = Number(pending?.resendCooldownSec || 0);
  const isResetOtp = pending?.flow === "reset";

  return `
    <div class="min-h-screen flex flex-col justify-center items-center p-4 sm:p-6 bg-[#F7F7FA]">
      <div class="w-full max-w-md space-y-5 ${animate ? "animate-fade-in" : ""}">
        <!-- Mobile-Matched Brand Banner -->
        <div class="hero-banner-card p-6 text-center text-white">
          <div class="relative z-10 space-y-2">
            <div class="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-white/12 border border-white/20 backdrop-blur-xs shadow-sm">
              <img src="/assets/synced_n_logo_sharp.png" onerror="this.src='/assets/logo.png'" alt="Synced N" class="w-10 h-10 object-contain" />
            </div>
            <h1 class="text-2xl font-extrabold tracking-tight text-white">Synced N</h1>
            <p class="text-xs text-white/80">Church Music Ministry Schedule, Calendar &amp; Chord Studio</p>
          </div>
        </div>

        <!-- Auth Card -->
        <div class="surface-card p-6 sm:p-7 space-y-5">
          ${mode === "login" || mode === "register" ? `
            <!-- Mode Switcher -->
            <div class="grid grid-cols-2 p-1 bg-[#F5F5F8] rounded-xl border border-slate-200/90 text-xs font-bold">
              <button type="button" onclick="window.appActions.setAuthMode('login')"
                class="py-2 rounded-lg transition ${mode === "login" ? "bg-[#500568] text-white shadow-xs" : "text-slate-600 hover:text-[#10113F]"}">
                Sign In
              </button>
              <button type="button" onclick="window.appActions.setAuthMode('register')"
                class="py-2 rounded-lg transition ${mode === "register" ? "bg-[#500568] text-white shadow-xs" : "text-slate-600 hover:text-[#10113F]"}">
                Create Account
              </button>
            </div>
          ` : ""}

          ${mode === "login" ? `
            <form onsubmit="window.appActions.handleLogin(event)" class="space-y-4">
              <div>
                <label class="form-label" for="login-email">Email Address</label>
                <input id="login-email" type="email" required maxlength="60" autocomplete="email" placeholder="you@church.org" class="form-input" />
              </div>
              <div>
                <div class="flex items-center justify-between mb-1">
                  <label class="form-label mb-0" for="login-password">Password</label>
                  <button type="button" onclick="window.appActions.setAuthMode('reset')" class="text-xs font-semibold text-[#500568] hover:underline">
                    Forgot password?
                  </button>
                </div>
                ${renderPasswordInputHtml({
                  id: "login-password",
                  placeholder: "Enter your password",
                  autocomplete: "current-password",
                  required: true
                })}
              </div>
              <button id="auth-submit-btn" type="submit" class="w-full btn btn-primary py-2.5">
                Sign In to Workspace
              </button>
            </form>
          ` : mode === "register" ? `
            <form onsubmit="window.appActions.handleRegister(event)" class="space-y-4">
              <div>
                <label class="form-label" for="reg-name">Full Name</label>
                <input id="reg-name" type="text" required maxlength="60" autocomplete="name" value="${escapeHtml(pending.flow === "register" ? pending.fullName : "")}" placeholder="e.g. Joshua Lagare" class="form-input" />
              </div>
              <div>
                <label class="form-label" for="reg-email">Email Address</label>
                <input id="reg-email" type="email" required maxlength="60" autocomplete="email" value="${escapeHtml(pending.flow === "register" ? pending.email : "")}" placeholder="you@church.org" class="form-input" />
              </div>
              <div>
                <label class="form-label" for="reg-role">Primary Ministry Role</label>
                <select id="reg-role" class="form-select">
                  ${DEFAULT_MINISTRY_ROLES.map((r) => `<option value="${escapeHtml(r)}" ${(pending.ministryRole || "Member") === r ? "selected" : ""}>${escapeHtml(r)}</option>`).join("")}
                </select>
              </div>
              <div>
                <label class="form-label" for="reg-password">Password</label>
                ${renderPasswordInputHtml({
                  id: "reg-password",
                  placeholder: "8+ chars, upper, lower, number, symbol",
                  autocomplete: "new-password",
                  required: true,
                  onInput: "window.appActions.updatePasswordStrengthUi('reg-password', this.value)"
                })}
                ${renderPasswordStrengthBoxHtml("reg-password")}
              </div>
              <div>
                <label class="form-label" for="reg-confirm-password">Confirm Password</label>
                ${renderPasswordInputHtml({
                  id: "reg-confirm-password",
                  placeholder: "Re-enter your password",
                  autocomplete: "new-password",
                  required: true,
                  ariaLabel: "Toggle confirm password visibility"
                })}
              </div>
              <button id="auth-submit-btn" type="submit" class="w-full btn btn-primary py-2.5">
                Create Ministry Account
              </button>
            </form>
          ` : mode === "reset" ? `
            <form onsubmit="window.appActions.handlePasswordReset(event)" class="space-y-4">
              <div class="space-y-1">
                <h2 class="text-base font-bold text-[#10113F]">Reset Account Password</h2>
                <p class="text-xs text-slate-500 leading-relaxed">
                  Enter your registered email address and your new password. We will send a 6-digit verification code to your email to confirm the reset.
                </p>
              </div>
              <div>
                <label class="form-label" for="reset-email">Registered Email Address</label>
                <input id="reset-email" type="email" required maxlength="60" autocomplete="email" value="${escapeHtml(pending.flow === "reset" ? pending.email : "")}" placeholder="you@church.org" class="form-input" />
              </div>
              <div>
                <label class="form-label" for="reset-new-password">New Password</label>
                ${renderPasswordInputHtml({
                  id: "reset-new-password",
                  placeholder: "8+ chars, upper, lower, number, symbol",
                  autocomplete: "new-password",
                  required: true,
                  onInput: "window.appActions.updatePasswordStrengthUi('reset-new-password', this.value)"
                })}
                ${renderPasswordStrengthBoxHtml("reset-new-password")}
              </div>
              <div>
                <label class="form-label" for="reset-confirm-password">Confirm New Password</label>
                ${renderPasswordInputHtml({
                  id: "reset-confirm-password",
                  placeholder: "Re-enter your new password",
                  autocomplete: "new-password",
                  required: true,
                  ariaLabel: "Toggle confirm new password visibility"
                })}
              </div>
              <button id="auth-submit-btn" type="submit" class="w-full btn btn-primary py-2.5">
                Send Verification Code
              </button>
              <button type="button" onclick="window.appActions.setAuthMode('login')" class="w-full btn btn-ghost text-xs">
                Back to Sign In
              </button>
            </form>
          ` : mode === "otp" ? `
            <form onsubmit="window.appActions.handleVerifyOtp(event)" class="space-y-4">
              <div class="text-center space-y-1.5">
                <div class="w-11 h-11 rounded-xl bg-[#F5D0FE]/70 border border-[#E879F9] text-[#500568] flex items-center justify-center mx-auto">
                  <i class="fa-solid fa-shield-halved text-base"></i>
                </div>
                <h2 class="text-base font-bold text-[#10113F]">
                  ${isResetOtp ? "Password Reset Verification" : "Verify Your Email Address"}
                </h2>
                <p class="text-xs text-slate-500 leading-relaxed">
                  Enter the 6-digit verification code sent to <strong class="text-[#10113F]">${escapeHtml(pending.email || "your email")}</strong>.
                  This code expires in 10 minutes.
                </p>
              </div>

              <div>
                <label class="form-label text-center" for="otp-code-input">6-Digit Verification Code</label>
                <input
                  id="otp-code-input"
                  type="text"
                  inputmode="numeric"
                  autocomplete="one-time-code"
                  required
                  maxlength="6"
                  pattern="[0-9]{6}"
                  placeholder="000000"
                  oninput="this.value = this.value.replace(/[^0-9]/g, '').slice(0, 6)"
                  class="form-input text-center font-mono text-lg font-extrabold tracking-[0.35em] py-3"
                />
              </div>

              <div class="flex items-center justify-between px-1 text-xs">
                <span class="text-slate-500">Didn't receive the code?</span>
                <button
                  id="otp-resend-btn"
                  type="button"
                  onclick="window.appActions.handleResendOtp()"
                  ${cooldownSec > 0 ? "disabled" : ""}
                  class="${cooldownSec > 0 ? "text-xs font-bold text-slate-400 cursor-not-allowed" : "text-xs font-bold text-[#500568] hover:underline cursor-pointer"}"
                >
                  ${cooldownSec > 0 ? `Resend in ${cooldownSec}s` : "Resend Code"}
                </button>
              </div>

              <button id="auth-submit-btn" type="submit" class="w-full btn btn-primary py-2.5">
                ${isResetOtp ? "Verify & Reset Password" : "Verify & Create Account"}
              </button>
              <button type="button" onclick="window.appActions.cancelOtpVerification()" class="w-full btn btn-ghost text-xs">
                Back to ${isResetOtp ? "Password Reset" : "Registration"}
              </button>
            </form>
          ` : `
            <div class="text-center space-y-4 py-2">
              <div class="w-12 h-12 rounded-2xl bg-emerald-100 text-emerald-700 flex items-center justify-center mx-auto">
                <i class="fa-solid fa-circle-check text-xl"></i>
              </div>
              <div class="space-y-1">
                <h2 class="text-base font-bold text-[#10113F]">Verification Complete</h2>
                <p class="text-xs text-slate-600 leading-relaxed">
                  ${escapeHtml(state.authSuccessMessage || "Password has been successfully reset! You can now log in.")}
                </p>
              </div>
              <button type="button" onclick="window.appActions.setAuthMode('login')" class="w-full btn btn-primary py-2.5">
                Continue to Sign In
              </button>
            </div>
          `}

          ${mode === "login" || mode === "register" ? `
            <div class="relative flex py-1 items-center">
              <div class="flex-grow border-t border-slate-200"></div>
              <span class="flex-shrink mx-3 text-[11px] uppercase tracking-wider text-slate-400 font-bold">or continue with</span>
              <div class="flex-grow border-t border-slate-200"></div>
            </div>

            <button type="button" onclick="window.appActions.handleGoogleSignIn()" class="w-full btn btn-secondary py-2.5">
              <i class="fa-brands fa-google text-[#500568]"></i>
              <span>Continue with Google</span>
            </button>
          ` : ""}
        </div>
      </div>
    </div>
  `;
}

// Team Setup View (Join with 6-character code or Create a new Worship Team)
function renderTeamSetupView(animate = true) {
  const user = state.currentUser;
  const displayName = user?.fullName || user?.name || "Member";

  return `
    <div class="min-h-screen flex flex-col justify-center items-center p-4 sm:p-6 bg-[#F7F7FA]">
      <div class="w-full max-w-3xl space-y-6 ${animate ? "animate-fade-in" : ""}">
        <!-- Top Bar -->
        <div class="hero-banner-card p-5 sm:p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4 text-white">
          <div class="relative z-10 flex items-center gap-3.5">
            <div class="w-12 h-12 rounded-xl bg-white/15 border border-white/20 flex items-center justify-center shrink-0">
              <img src="/assets/synced_n_logo_sharp.png" onerror="this.src='/assets/logo.png'" alt="Synced N" class="w-8 h-8 object-contain" />
            </div>
            <div>
              <h1 class="text-lg font-extrabold text-white">Welcome, ${escapeHtml(displayName)}</h1>
              <p class="text-xs text-white/80">Connect to your church worship ministry workspace to access schedules, calendar, and song lineups.</p>
            </div>
          </div>
          <button onclick="window.appActions.signOutUser()" class="relative z-10 btn btn-sm bg-white/15 hover:bg-white/25 text-white border border-white/25 self-start sm:self-auto">
            <i class="fa-solid fa-right-from-bracket text-xs"></i> Sign Out
          </button>
        </div>

        ${user?.removedByLeader && user?.removedTeamCode ? `
          <div class="surface-card border-amber-200 bg-amber-50/70 p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div class="text-xs text-amber-900">
              <strong>Notice:</strong> You were previously removed from team <span class="font-mono font-bold">${escapeHtml(user.removedTeamCode)}</span>. You can send a rejoin request to the Worship Leader or join/create another team below.
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
              <h2 class="text-base font-bold text-[#10113F]">Enter Team Invite Code</h2>
              <p class="text-xs text-slate-500 leading-relaxed">
                Ask your Worship Leader for your church's 6-character invite code to sync with the team schedule, calendar, and song library.
              </p>
            </div>
            <form onsubmit="window.appActions.handleJoinTeam(event)" class="space-y-3">
              <div>
                <label class="form-label" for="join-team-code">6-Character Invite Code</label>
                <input
                  id="join-team-code"
                  type="text"
                  required
                  maxlength="6"
                  placeholder="ABC123"
                  class="form-input uppercase tracking-widest font-mono text-center text-base font-bold"
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
              <h2 class="text-base font-bold text-[#10113F]">Create a New Team Workspace</h2>
              <p class="text-xs text-slate-500 leading-relaxed">
                Start a new workspace for your church music ministry and generate an invite code for your musicians and vocalists.
              </p>
            </div>
            <form onsubmit="window.appActions.handleCreateTeam(event)" class="space-y-3">
              <div>
                <label class="form-label" for="create-team-name">Ministry / Team Name</label>
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

// Main Dashboard Shell (Sidebar + Top Header + Page Content + Reorganized Mobile Nav)
function renderDashboardShell() {
  const { currentUser, currentTeam, activeTab, substitutions, rejoinRequests, theme } = state;
  const isLeader = hasLeaderPrivileges(currentUser);
  const isDark = theme === "dark";
  const pendingActionCount = isLeader
    ? substitutions.filter((s) => s.status === "Pending" || s.status === "Substitute Accepted").length +
      rejoinRequests.filter((r) => r.status === "Pending").length
    : 0;
  const teamDisplayName = currentTeam?.teamName || currentUser?.teamName || "Workspace";
  const teamCode = currentTeam?.teamCode || currentUser?.teamCode || "------";
  const userDisplayName = currentUser?.fullName || currentUser?.name || "Member";

  // Full 7-section navigation for the Desktop Sidebar
  const desktopNavItems = [
    { id: "home", label: "Home", fullLabel: "Dashboard", icon: "fa-solid fa-house" },
    { id: "calendar", label: "Calendar", fullLabel: "Ministry Calendar", icon: "fa-solid fa-calendar-days" },
    { id: "schedule", label: "Schedule", fullLabel: "Schedule & Lineups", icon: "fa-regular fa-calendar-check", badge: pendingActionCount || 0 },
    { id: "songs", label: "Songs", fullLabel: "Song Library", icon: "fa-solid fa-music" },
    { id: "roster", label: "Roster", fullLabel: "Team Roster", icon: "fa-solid fa-users" },
    { id: "chat", label: "Chat", fullLabel: "Team Chat", icon: "fa-regular fa-comments" },
    { id: "profile", label: "Profile", fullLabel: "Account Profile", icon: "fa-regular fa-user" }
  ];

  // Reorganized 5-item Mobile Bottom Navigation (Calendar is integrated in Dashboard; Roster is in the Team Hub Switcher & Dashboard)
  const mobileNavItems = [
    {
      id: "home",
      label: "Home",
      icon: "fa-solid fa-house",
      isActive: activeTab === "home" || activeTab === "calendar"
    },
    {
      id: "schedule",
      label: "Schedule",
      icon: "fa-regular fa-calendar-check",
      badge: pendingActionCount || 0,
      isActive: activeTab === "schedule"
    },
    {
      id: "songs",
      label: "Songs",
      icon: "fa-solid fa-music",
      isActive: activeTab === "songs"
    },
    {
      id: "chat",
      label: "Chat",
      icon: "fa-regular fa-comments",
      isActive: activeTab === "chat" || activeTab === "roster"
    },
    {
      id: "profile",
      label: "Profile",
      icon: "fa-regular fa-user",
      isActive: activeTab === "profile"
    }
  ];

  let mainViewHtml = "";
  if (activeTab === "home") mainViewHtml = renderHomeView(state);
  else if (activeTab === "calendar") mainViewHtml = renderCalendarView(state);
  else if (activeTab === "schedule") mainViewHtml = renderScheduleView(state);
  else if (activeTab === "songs") mainViewHtml = renderSongsView(state);
  else if (activeTab === "roster") mainViewHtml = renderRosterView(state);
  else if (activeTab === "chat") mainViewHtml = renderChatView(state);
  else if (activeTab === "profile") mainViewHtml = renderProfileView(state);

  return `
    <div class="min-h-screen flex flex-col md:flex-row bg-[#F7F7FA]">
      <!-- Desktop Fixed Sidebar -->
      <aside class="hidden md:flex md:w-64 md:flex-col md:fixed md:inset-y-0 bg-white border-r border-slate-200/90 z-30">
        <!-- Brand Header (Synced N Navy / Purple Identity) -->
        <div class="h-16 px-4 flex items-center justify-between border-b border-slate-200/80 bg-[#10113F] text-white">
          <div class="flex items-center gap-3 min-w-0">
            <div class="w-9 h-9 rounded-lg bg-white/12 border border-white/15 flex items-center justify-center shrink-0">
              <img src="/assets/synced_n_logo_sharp.png" onerror="this.src='/assets/logo.png'" alt="Synced N" class="w-6 h-6 object-contain" />
            </div>
            <div class="truncate">
              <div class="text-sm font-extrabold text-white leading-none tracking-tight">Synced N</div>
              <div class="text-[11px] text-white/75 truncate mt-1">${escapeHtml(teamDisplayName)}</div>
            </div>
          </div>
          <button
            type="button"
            onclick="window.appActions.toggleTheme()"
            class="w-8 h-8 rounded-lg bg-white/10 hover:bg-white/20 text-white/90 flex items-center justify-center transition shrink-0"
            title="${isDark ? "Switch to Light Mode" : "Switch to Dark Mode"}"
            aria-label="${isDark ? "Switch to Light Mode" : "Switch to Dark Mode"}"
          >
            <i class="fa-solid ${isDark ? "fa-sun text-amber-300" : "fa-moon text-[#F5D0FE]"} text-xs"></i>
          </button>
        </div>

        <!-- Navigation Links -->
        <nav class="flex-1 px-3 py-4 space-y-1 overflow-y-auto" aria-label="Main Navigation">
          <div class="px-2.5 pb-1.5 text-[10px] font-bold uppercase tracking-wider text-slate-400">
            Ministry Workspace
          </div>
          ${desktopNavItems.map((item) => {
            const active = activeTab === item.id;
            return `
              <button
                onclick="window.appActions.switchTab('${item.id}')"
                class="w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition ${
                  active
                    ? "bg-[#500568] text-white shadow-xs"
                    : "text-slate-600 hover:bg-[#F5F5F8] hover:text-[#10113F]"
                }"
              >
                <div class="flex items-center gap-3">
                  <i class="${item.icon} w-4 text-center text-sm ${active ? "text-white" : "text-[#500568]/75"}"></i>
                  <span>${escapeHtml(item.fullLabel)}</span>
                </div>
                ${item.badge ? `
                  <span class="px-1.5 py-0.5 rounded-full text-[10px] font-bold ${active ? "bg-amber-400 text-slate-950" : "bg-amber-100 text-amber-800"}">
                    ${item.badge}
                  </span>
                ` : ""}
              </button>
            `;
          }).join("")}
        </nav>

        <!-- Sidebar Footer: Invite Code + User Summary -->
        <div class="p-3.5 border-t border-slate-200/90 space-y-3 bg-[#F5F5F8]/70">
          <div class="flex items-center justify-between px-3 py-2 bg-white border border-slate-200/90 rounded-xl shadow-2xs">
            <div>
              <div class="text-[10px] uppercase tracking-wider text-slate-400 font-bold">Team Code</div>
              <div class="font-mono text-xs font-extrabold text-[#500568] tracking-widest">${escapeHtml(teamCode)}</div>
            </div>
            <button onclick="window.appActions.copyTeamCode()" class="btn btn-ghost btn-sm text-xs text-[#500568]" title="Copy Invite Code">
              <i class="fa-regular fa-copy"></i>
            </button>
          </div>

          <div class="flex items-center justify-between gap-2 px-1">
            <button onclick="window.appActions.switchTab('profile')" class="flex items-center gap-2.5 min-w-0 text-left flex-1 hover:opacity-85 transition">
              ${getAvatarHtml(currentUser, "w-9 h-9", "text-xs")}
              <div class="min-w-0 flex-1">
                <div class="text-xs font-bold text-[#10113F] truncate">${escapeHtml(userDisplayName)}</div>
                <div class="text-[11px] text-[#500568] font-medium truncate">${escapeHtml(currentUser.ministryRole || "Member")}</div>
              </div>
            </button>
            <button onclick="window.appActions.signOutUser()" class="btn btn-ghost btn-sm text-slate-500 hover:text-rose-600" title="Sign Out">
              <i class="fa-solid fa-right-from-bracket text-xs"></i>
            </button>
          </div>
        </div>
      </aside>

      <!-- Mobile Top Bar -->
      <header class="md:hidden sticky top-0 z-30 h-14 bg-[#10113F] text-white border-b border-white/10 px-4 flex items-center justify-between shadow-xs">
        <div class="flex items-center gap-2.5 min-w-0">
          <div class="w-8 h-8 rounded-lg bg-white/12 border border-white/15 flex items-center justify-center shrink-0">
            <img src="/assets/synced_n_logo_sharp.png" onerror="this.src='/assets/logo.png'" alt="Synced N" class="w-5 h-5 object-contain" />
          </div>
          <div class="truncate">
            <div class="text-sm font-extrabold text-white leading-none truncate">${escapeHtml(teamDisplayName)}</div>
            <div class="text-[10px] text-[#F5D0FE] font-mono mt-0.5">Code: ${escapeHtml(teamCode)}</div>
          </div>
        </div>
        <div class="flex items-center gap-1.5">
          <button
            type="button"
            onclick="window.appActions.toggleTheme()"
            class="w-7 h-7 rounded-md bg-white/10 hover:bg-white/20 text-white/90 flex items-center justify-center transition"
            title="${isDark ? "Switch to Light Mode" : "Switch to Dark Mode"}"
            aria-label="${isDark ? "Switch to Light Mode" : "Switch to Dark Mode"}"
          >
            <i class="fa-solid ${isDark ? "fa-sun text-amber-300" : "fa-moon text-[#F5D0FE]"} text-[11px]"></i>
          </button>
          <button onclick="window.appActions.copyTeamCode()" class="px-2 py-1 rounded-md bg-white/10 text-[10px] font-mono text-white/90 hover:bg-white/20" title="Copy Team Code">
            <i class="fa-regular fa-copy"></i>
          </button>
          <button onclick="window.appActions.switchTab('profile')" class="p-0.5 rounded-full ring-2 ring-[#F5D0FE]/40">
            ${getAvatarHtml(currentUser, "w-7 h-7", "text-[10px]")}
          </button>
        </div>
      </header>

      <!-- Main Workspace Container -->
      <main class="flex-1 md:pl-64 pb-20 md:pb-10">
        <div class="max-w-6xl mx-auto p-3.5 sm:p-6 lg:p-8">
          ${mainViewHtml}
        </div>
      </main>

      <!-- Reorganized 5-Item Mobile Bottom Navigation -->
      <nav id="mobile-bottom-nav" class="md:hidden fixed bottom-0 inset-x-0 z-30 bg-white/95 backdrop-blur-md border-t border-slate-200/90 grid grid-cols-5 py-1.5 px-1.5 gap-1 shadow-lg" aria-label="Mobile Navigation">
        ${mobileNavItems.map((item) => {
          const active = item.isActive;
          return `
            <button
              type="button"
              data-mobile-nav="${item.id}"
              onclick="window.appActions.switchTab('${item.id}')"
              class="relative flex flex-col items-center justify-center py-1.5 px-1 rounded-xl text-[11px] font-medium transition ${
                active ? "text-[#500568] font-bold bg-[#F5D0FE]/40" : "text-slate-500 hover:text-[#10113F]"
              }"
            >
              <i class="${item.icon} text-sm mb-0.5"></i>
              <span class="truncate max-w-[64px] leading-tight">${escapeHtml(item.label)}</span>
              ${item.badge ? `
                <span class="absolute top-1 right-2.5 w-2 h-2 rounded-full bg-amber-500"></span>
              ` : ""}
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

  // --- Theme Controls (Light Mode / Dark Mode with Persistence) ---
  setTheme(mode) {
    const target = mode === "dark" ? "dark" : "light";
    applyThemeToDom(target);
    renderApp();
    showToast(`Switched to ${target === "dark" ? "Dark Mode" : "Light Mode"}.`, "info");
  },

  toggleTheme() {
    const next = state.theme === "dark" ? "light" : "dark";
    this.setTheme(next);
  },

  setAuthMode(mode) {
    if (mode !== "otp") {
      clearPendingAuthState();
    }
    state.authMode = mode;
    renderApp();
  },

  cancelOtpVerification() {
    const returnMode = state.pendingAuth.flow === "reset" ? "reset" : "register";
    if (state.pendingAuth.cooldownTimer) {
      clearInterval(state.pendingAuth.cooldownTimer);
      state.pendingAuth.cooldownTimer = null;
    }
    state.pendingAuth.password = "";
    state.authMode = returnMode;
    renderApp();
  },

  /**
   * Real-time Password Strength Meter & Checklist updater matching `MemberActivity.kt` `passwordStrengthBox`.
   */
  updatePasswordStrengthUi(inputId, rawValue) {
    const pwd = String(rawValue ?? document.getElementById(inputId)?.value ?? "");
    const ev = evaluatePassword(pwd);

    const badge = document.getElementById(`${inputId}-strength-badge`);
    if (badge) {
      if (!pwd) {
        badge.textContent = "Min. 8 chars";
        badge.className = "px-2 py-0.5 rounded-md text-[10px] font-bold bg-slate-200/80 text-slate-600";
      } else {
        const badgeStyles = {
          1: "bg-rose-100 text-rose-700",
          2: "bg-amber-100 text-amber-800",
          3: "bg-blue-100 text-blue-700",
          4: "bg-emerald-100 text-emerald-700"
        };
        badge.textContent = ev.strengthLabel || "Weak";
        badge.className = `px-2 py-0.5 rounded-md text-[10px] font-bold ${badgeStyles[ev.score] || badgeStyles[1]}`;
      }
    }

    const barColors = {
      1: "bg-rose-500",
      2: "bg-amber-500",
      3: "bg-blue-500",
      4: "bg-emerald-500"
    };
    const activeBarColor = barColors[ev.score] || "bg-slate-200";
    for (let i = 1; i <= 4; i++) {
      const bar = document.getElementById(`${inputId}-bar-${i}`);
      if (bar) {
        bar.className = `h-1.5 rounded-full transition-colors ${pwd && i <= ev.score ? activeBarColor : "bg-slate-200"}`;
      }
    }

    const updateReqItem = (reqSuffix, met, labelText) => {
      const el = document.getElementById(`${inputId}-${reqSuffix}`);
      if (!el) return;
      el.className = `flex items-center gap-1.5 ${met ? "text-emerald-600 font-semibold" : "text-slate-500"}`;
      el.innerHTML = `
        <i class="fa-${met ? "solid fa-circle-check text-emerald-600" : "regular fa-circle"} text-[9px]"></i>
        <span>${labelText}</span>
      `;
    };

    updateReqItem("req-len", ev.hasMinLength, "8+ characters");
    updateReqItem("req-case", ev.hasUpper && ev.hasLower, "Upper &amp; lower (Aa)");
    updateReqItem("req-num", ev.hasNumber, "Number (0-9)");
    updateReqItem("req-spec", ev.hasSpecial, "Special (!@#$)");
  },

  switchTab(tabId) {
    state.activeTab = tabId;
    if (tabId === "chat") {
      // Ensure opening chat always displays the latest messages immediately
      state.chatScroll.isPinnedToBottom = true;
      state.chatScroll.pendingSmoothScroll = false;
    }
    renderApp();
    window.scrollTo({ top: 0, behavior: "smooth" });
  },

  // --- Password Visibility Toggle (Show/Hide Password Without Resetting Input) ---
  togglePasswordVisibility(inputId, btnEl) {
    const input = document.getElementById(inputId);
    if (!input) return;

    const currentVal = input.value;
    const selStart = input.selectionStart;
    const selEnd = input.selectionEnd;
    const willReveal = input.type === "password";

    input.type = willReveal ? "text" : "password";
    // Ensure value is never modified or cleared
    if (input.value !== currentVal) {
      input.value = currentVal;
    }

    const button = btnEl || input.parentElement?.querySelector(".password-toggle-btn");
    if (button) {
      button.setAttribute("aria-pressed", willReveal ? "true" : "false");
      button.setAttribute("aria-label", willReveal ? "Hide password" : "Show password");
      button.setAttribute("title", willReveal ? "Hide password" : "Show password");
      button.innerHTML = `<i class="fa-regular ${willReveal ? "fa-eye-slash" : "fa-eye"} text-xs pointer-events-none"></i>`;
    }

    try {
      input.focus({ preventScroll: true });
      if (typeof selStart === "number" && input.setSelectionRange) {
        input.setSelectionRange(selStart, selEnd ?? selStart);
      }
    } catch {
      // Ignore selection restoration errors
    }
  },

  // --- Authentication Handlers (100% Mobile Parity with AuthManager.kt & MemberActivity.kt) ---
  async handleLogin(e) {
    e.preventDefault();
    const cleanEmail = (document.getElementById("login-email")?.value || "").trim();
    const cleanPassword = (document.getElementById("login-password")?.value || "").trim();

    if (!cleanEmail || !cleanPassword) {
      return showToast("Please fill in all fields", "error");
    }

    // 1. Brute-force lockout check (Client + Trusted Backend parity with ValidationUtils.isLoginLocked)
    if (isLoginLocked(cleanEmail)) {
      const remSec = getLoginLockRemainingSeconds(cleanEmail);
      return showToast(`Too many login attempts. Please wait ${remSec} seconds.`, "error");
    }
    try {
      const rateCheck = await callAuthApi("/api/auth/login-rate-limit", {
        action: "check",
        email: cleanEmail
      });
      if (rateCheck.locked) {
        return showToast(`Too many login attempts. Please wait ${rateCheck.remainingSec || 60} seconds.`, "error");
      }
    } catch {
      // Fall back to client-side rate limiter if backend is unreachable
    }

    // 2. Email validation (matching ValidationUtils.validateEmailForAuth)
    const emailErr = validateEmailForAuth(cleanEmail);
    if (emailErr) return showToast(emailErr, "error");

    const btn = document.getElementById("auth-submit-btn");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Signing in...";
    }

    const recordFailedAttemptAndNotify = async (baseErrorMsg) => {
      const clientRem = recordLoginFailure(cleanEmail);
      let serverRes = null;
      try {
        serverRes = await callAuthApi("/api/auth/login-rate-limit", {
          action: "record_failure",
          email: cleanEmail
        });
      } catch {
        // Ignore backend error
      }
      const isLocked = clientRem === 0 || Boolean(serverRes?.locked);
      const remaining = serverRes?.remainingAttempts !== undefined ? serverRes.remainingAttempts : clientRem;

      if (isLocked) {
        showToast("Too many failed attempts. Account locked for 60 seconds.", "error");
      } else if (remaining <= 2) {
        showToast(`${baseErrorMsg} (${remaining} attempt(s) left)`, "error");
      } else {
        showToast(baseErrorMsg, "error");
      }
    };

    loginVerificationInProgress = true;
    clearVerifiedSession();

    try {
      // Step 1: Attempt standard Firebase Authentication login
      const cred = await signInWithEmailAndPassword(auth, cleanEmail, cleanPassword);

      // Step 1A: Mobile Security Parity Check (AuthManager.kt lines 135-146)
      // Check if the password was reset via OTP (/password_resets/{emailLower}).
      // If a reset hash exists and does NOT match the entered password, the user is trying
      // to sign in with their old pre-reset password -> reject and signOut immediately!
      const storedResetHash = await getPasswordResetHash(cleanEmail);
      if (storedResetHash) {
        const matchesReset = await verifyPasswordPbkdf2(cleanPassword, storedResetHash);
        if (!matchesReset) {
          await signOut(auth);
          loginVerificationInProgress = false;
          await recordFailedAttemptAndNotify(
            "Incorrect password. This account's password was changed. Please use your new password."
          );
          return;
        }
        // The user entered their new OTP-reset password and Firebase Auth also accepted it
        try {
          await updatePassword(cred.user, cleanPassword);
        } catch {
          // Ignore updatePassword notice
        }
      }

      // Reset brute-force counters on successful login
      resetLoginAttempts(cleanEmail);
      try {
        await callAuthApi("/api/auth/login-rate-limit", { action: "reset", email: cleanEmail });
      } catch {
        // Ignore
      }

      loginVerificationInProgress = false;
      await activateAuthenticatedSession(cred.user, null);
      showToast("Signed in to Synced N.", "success");
    } catch (firebaseAuthError) {
      // Step 2: Cross-Platform OTP Password Reset / Re-creation Fallback
      // If the user's password matches a stored reset password, or if they were deleted in Firebase Auth,
      // attempt to re-create their Firebase Auth account with these credentials.
      try {
        const storedResetHash = await getPasswordResetHash(cleanEmail);
        let existingProfile = await getUserProfileByEmail(cleanEmail);
        const candidateHash = storedResetHash || existingProfile?.resetPassword || "";

        if (candidateHash) {
          const matchesResetPassword = await verifyPasswordPbkdf2(cleanPassword, candidateHash);
          if (matchesResetPassword) {
            try {
              const reCreatedCred = await createUserWithEmailAndPassword(auth, cleanEmail, cleanPassword);
              if (reCreatedCred?.user) {
                resetLoginAttempts(cleanEmail);
                loginVerificationInProgress = false;
                await activateAuthenticatedSession(reCreatedCred.user, null);
                showToast("Signed in to Synced N.", "success");
                return;
              }
            } catch (createErr) {
              console.warn("Re-create account check:", createErr);
            }
          }
        }
      } catch (fallbackErr) {
        console.warn("OTP password reset fallback check:", fallbackErr);
      }

      loginVerificationInProgress = false;
      const errCode = String(firebaseAuthError?.code || "");
      let friendlyMsg = "Incorrect email or password";
      if (errCode.includes("user-not-found")) {
        friendlyMsg = "No account found with this email. Please click 'Create Account' to sign up.";
      } else if (errCode.includes("invalid-credential")) {
        friendlyMsg = "Incorrect email or password. If you recently deleted your account in Firebase, click 'Create Account' to sign up.";
      } else if (errCode.includes("too-many-requests")) {
        friendlyMsg = "Too many failed attempts. Please try again later.";
      }
      await recordFailedAttemptAndNotify(friendlyMsg);
    } finally {
      loginVerificationInProgress = false;
      if (btn) {
        btn.disabled = false;
        btn.textContent = "Sign In to Workspace";
      }
    }
  },

  /**
   * Step 1 of Registration (matches `MemberActivity.kt` `setupRegister` lines 1510-1572):
   * Validates inputs, sends a 6-digit OTP via Brevo to verify email ownership,
   * then creates the Firebase Auth / Firestore user account upon verification.
   */
  async handleRegister(e) {
    e.preventDefault();
    const fullName = (document.getElementById("reg-name")?.value || "").trim();
    const email = (document.getElementById("reg-email")?.value || "").trim();
    const ministryRole = document.getElementById("reg-role")?.value || "Member";
    const password = (document.getElementById("reg-password")?.value || "").trim();
    const confirmPassword = (document.getElementById("reg-confirm-password")?.value || "").trim();

    if (!fullName || !email || !password || !confirmPassword) {
      return showToast("Please fill in all fields", "error");
    }

    if (!isValidName(fullName)) {
      return showToast("Name must be 2-50 letters (spaces, hyphens, and apostrophes allowed)", "error");
    }
    const derivedUsername = fullName.split(/\s+/)[0] || fullName;
    if (!isValidUsername(derivedUsername) && !isValidName(fullName)) {
      return showToast("Username must be 2-30 characters (letters, numbers, spaces, underscore)", "error");
    }

    const emailErr = validateEmailForAuth(email);
    if (emailErr) return showToast(emailErr, "error");

    const pwdEval = evaluatePassword(password);
    if (!pwdEval.isStrong) return showToast(pwdEval.errorMessage, "error");

    if (password !== confirmPassword) {
      return showToast("Passwords do not match", "error");
    }

    const btn = document.getElementById("auth-submit-btn");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Sending verification code...";
    }

    try {
      const otpRes = await callAuthApi("/api/auth/send-otp", {
        email,
        recipientName: fullName,
        purpose: "register"
      });

      if (!otpRes.ok || !otpRes.success) {
        if (otpRes.cooldownRemainingMs > 0) {
          const waitSec = Math.ceil(otpRes.cooldownRemainingMs / 1000);
          showToast(`Please wait ${waitSec}s before requesting a new code.`, "warning");
        } else {
          showToast(otpRes.errorMessage || "Failed to send verification code.", "error");
        }
        return;
      }

      state.pendingAuth = {
        flow: "register",
        username: derivedUsername.slice(0, 30),
        fullName,
        email,
        password,
        ministryRole,
        sentAt: Date.now(),
        expiresAt: otpRes.expiresAt || Date.now() + 10 * 60 * 1000,
        resendCooldownSec: 60,
        cooldownTimer: null
      };

      state.authMode = "otp";
      renderApp();
      startOtpCooldownTimer(60);
      showToast(`Verification code sent to ${email}`, "success");
    } catch (err) {
      showToast(err.message || "Failed to initiate email verification.", "error");
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "Create Ministry Account";
      }
    }
  },

  /**
   * Step 1 of Forgot Password / Password Reset (matches `MemberActivity.kt` `setupReset` lines 1578-1625):
   * Validates registered email + strong new password + confirm password, verifies account existence,
   * and sends a 6-digit OTP (`purpose = "reset"`) via Brevo.
   */
  async handlePasswordReset(e) {
    e.preventDefault();
    const email = (document.getElementById("reset-email")?.value || "").trim();
    const newPassword = (document.getElementById("reset-new-password")?.value || "").trim();
    const confirmPassword = (document.getElementById("reset-confirm-password")?.value || "").trim();

    if (!email || !newPassword || !confirmPassword) {
      return showToast("Please fill in all fields", "error");
    }

    const emailErr = validateEmailForAuth(email);
    if (emailErr) return showToast(emailErr, "error");

    const pwdEval = evaluatePassword(newPassword);
    if (!pwdEval.isStrong) return showToast(pwdEval.errorMessage, "error");

    if (newPassword !== confirmPassword) {
      return showToast("Passwords do not match", "error");
    }

    const btn = document.getElementById("auth-submit-btn");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Verifying account...";
    }

    try {
      // Verify the email belongs to a known account in Firestore or password resets
      const emailExists = await checkEmailExistsInFirestore(email);
      const resetHash = await getPasswordResetHash(email);
      if (!emailExists && !resetHash) {
        showToast("No registered account found with this email. Please click 'Create Account' to sign up.", "error");
        return;
      }

      if (btn) {
        btn.textContent = "Sending reset code...";
      }

      const otpRes = await callAuthApi("/api/auth/send-otp", {
        email,
        recipientName: "Member",
        purpose: "reset"
      });

      if (!otpRes.ok || !otpRes.success) {
        if (otpRes.cooldownRemainingMs > 0) {
          const waitSec = Math.ceil(otpRes.cooldownRemainingMs / 1000);
          showToast(`Please wait ${waitSec}s before requesting a new code.`, "warning");
        } else {
          showToast(otpRes.errorMessage || "Failed to send password reset code.", "error");
        }
        return;
      }

      state.pendingAuth = {
        flow: "reset",
        username: "",
        fullName: "",
        email,
        password: newPassword,
        ministryRole: "Member",
        sentAt: Date.now(),
        expiresAt: otpRes.expiresAt || Date.now() + 10 * 60 * 1000,
        resendCooldownSec: 60,
        cooldownTimer: null
      };

      state.authMode = "otp";
      renderApp();
      startOtpCooldownTimer(60);
      showToast(`Password reset code sent to ${email}`, "success");
    } catch (err) {
      showToast(err.message || "Could not send password reset code.", "error");
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "Send Verification Code";
      }
    }
  },

  /**
   * Resends the 6-digit OTP code while enforcing the 60-second cooldown (`EmailOtpService.canResendOtp`).
   */
  async handleResendOtp() {
    const pending = state.pendingAuth;
    if (!pending.email || !pending.flow) {
      return showToast("Session expired. Please start again.", "error");
    }
    if (pending.resendCooldownSec > 0) {
      return showToast(`Please wait ${pending.resendCooldownSec}s before requesting a new code.`, "warning");
    }

    const resendBtn = document.getElementById("otp-resend-btn");
    if (resendBtn) {
      resendBtn.disabled = true;
      resendBtn.textContent = "Sending...";
    }

    try {
      const otpRes = await callAuthApi("/api/auth/send-otp", {
        email: pending.email,
        recipientName: pending.fullName || "Member",
        purpose: pending.flow
      });

      if (!otpRes.ok || !otpRes.success) {
        if (otpRes.cooldownRemainingMs > 0) {
          const waitSec = Math.ceil(otpRes.cooldownRemainingMs / 1000);
          startOtpCooldownTimer(waitSec);
        }
        showToast(otpRes.errorMessage || "Please wait before requesting a new code.", "warning");
        return;
      }

      const otpInput = document.getElementById("otp-code-input");
      if (otpInput) otpInput.value = "";
      startOtpCooldownTimer(60);
      showToast("New verification code sent!", "success");
    } catch (err) {
      showToast(err.message || "Failed to resend verification code.", "error");
    }
  },

  /**
   * Step 2 of Registration & Password Reset (`MemberActivity.kt` `setupOtp` lines 1658-1706):
   * Verifies the 6-digit OTP through the trusted backend (or direct Brevo fallback),
   * and completes either account creation (`createUserWithEmailAndPassword`) or password reset (`savePasswordResetInFirestore`).
   */
  async handleVerifyOtp(e) {
    e.preventDefault();
    const pending = state.pendingAuth;
    if (!pending.email || !pending.flow || !pending.password) {
      showToast("Verification session expired. Please try again.", "error");
      state.authMode = "login";
      renderApp();
      return;
    }

    const code = (document.getElementById("otp-code-input")?.value || "").trim();
    if (code.length !== 6 || !/^\d{6}$/.test(code)) {
      return showToast("Please enter the 6-digit verification code", "error");
    }

    const btn = document.getElementById("auth-submit-btn");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Verifying...";
    }

    try {
      // 1. Verify OTP against trusted backend or direct Brevo client fallback
      const verifyRes = await callAuthApi("/api/auth/verify-otp", {
        email: pending.email,
        code,
        purpose: pending.flow
      });

      if (!verifyRes.ok || !verifyRes.verified) {
        showToast(verifyRes.errorMessage || "Invalid verification code.", "error");
        return;
      }

      // 2. Consume the single-use verification token to prevent OTP bypass or replay
      if (verifyRes.verificationToken) {
        await callAuthApi("/api/auth/consume-verification", {
          email: pending.email,
          purpose: pending.flow,
          verificationToken: verifyRes.verificationToken
        }).catch(() => {});
      }

      // 3A. Registration Flow
      if (pending.flow === "register") {
        if (btn) btn.textContent = "Creating account...";

        let cred;
        try {
          cred = await createUserWithEmailAndPassword(auth, pending.email, pending.password);
        } catch (createAuthErr) {
          const code = String(createAuthErr?.code || "");
          if (code.includes("email-already-in-use")) {
            showToast("This email is already in use by an active account. Please sign in or reset your password.", "error");
            clearPendingAuthState();
            state.authMode = "login";
            renderApp();
            return;
          }
          throw createAuthErr;
        }

        try {
          await updateProfile(cred.user, { displayName: pending.fullName });
        } catch {
          // Ignore updateProfile error
        }

        // Check if there was an old profile doc in Firestore for this email (e.g. from an account deleted in Firebase)
        let oldTeamCode = "";
        let oldTeamName = "";
        let oldRole = pending.ministryRole === "Worship Leader" ? "Leader" : "Member";
        try {
          const oldProfile = await getUserProfileByEmail(pending.email);
          if (oldProfile) {
            oldTeamCode = oldProfile.teamCode || "";
            oldTeamName = oldProfile.teamName || "";
            if (oldProfile.role) oldRole = oldProfile.role;
            if (oldProfile.uid && oldProfile.uid !== cred.user.uid) {
              await deleteDoc(doc(db, "users", oldProfile.uid)).catch(() => {});
            }
          }
        } catch {
          // Ignore
        }

        const hashedPassword = await hashPasswordPbkdf2(pending.password);
        const createdProfile = await saveCleanUserProfile(cred.user.uid, {
          uid: cred.user.uid,
          fullName: pending.fullName,
          username: pending.username || pending.fullName.split(/\s+/)[0] || "Member",
          email: pending.email,
          resetPassword: hashedPassword,
          role: oldRole,
          ministryRole: pending.ministryRole || "Member",
          teamCode: oldTeamCode,
          teamName: oldTeamName,
          photoUri: "",
          unavailableRanges: []
        });

        // Also update password_resets record with the newly set password
        await setDoc(doc(db, "password_resets", pending.email.toLowerCase()), {
          email: pending.email.toLowerCase(),
          password: hashedPassword,
          updatedAt: Date.now()
        }).catch(() => {});

        clearPendingAuthState();
        await persistVerifiedSession(createdProfile, false);
        showToast("Your account has been created!", "success");
        return;
      }

      // 3B. Password Reset Flow
      if (pending.flow === "reset") {
        if (btn) btn.textContent = "Saving new password...";

        await savePasswordResetInFirestore(pending.email, pending.password);
        try {
          await sendPasswordResetEmail(auth, pending.email);
        } catch {
          // Ignore
        }
        clearPendingAuthState();
        state.authSuccessMessage = "Password has been successfully reset! You can now log in.";
        state.authMode = "success";
        renderApp();
        showToast("Password has been successfully reset!", "success");
      }
    } catch (err) {
      const errCode = String(err?.code || "");
      if (errCode.includes("email-already-in-use")) {
        showToast("This email is already registered. Please log in instead.", "error");
      } else {
        showToast(err.message || "Verification failed. Please try again.", "error");
      }
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = pending.flow === "reset" ? "Verify & Reset Password" : "Verify & Create Account";
      }
    }
  },

  async handleGoogleSignIn() {
    try {
      clearVerifiedSession();
      await signInWithPopup(auth, googleProvider);
      showToast("Signed in with Google.", "success");
    } catch (err) {
      showToast(err.message || "Google sign-in cancelled.", "error");
    }
  },

  async handleChangePassword(e) {
    e.preventDefault();
    const newPassword = (document.getElementById("profile-new-password")?.value || "").trim();
    const confirmPassword = (document.getElementById("profile-confirm-password")?.value || "").trim();

    if (newPassword !== confirmPassword) {
      return showToast("Passwords do not match.", "error");
    }

    const pwdEval = evaluatePassword(newPassword);
    if (!pwdEval.isStrong) {
      return showToast(pwdEval.errorMessage, "error");
    }

    const btn = document.getElementById("change-password-btn");
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Updating...";
    }

    try {
      const hashedPassword = await hashPasswordPbkdf2(newPassword);
      if (auth.currentUser && !auth.currentUser.isAnonymous) {
        try {
          await updatePassword(auth.currentUser, newPassword);
        } catch (authErr) {
          console.warn("Firebase updatePassword notice:", authErr);
        }
      }
      await saveCleanUserProfile(state.currentUser.uid, { resetPassword: hashedPassword });
      if (state.currentUser?.email) {
        await savePasswordResetInFirestore(state.currentUser.email, newPassword);
      }

      const newInput = document.getElementById("profile-new-password");
      const confirmInput = document.getElementById("profile-confirm-password");
      if (newInput) newInput.value = "";
      if (confirmInput) confirmInput.value = "";
      this.updatePasswordStrengthUi("profile-new-password", "");
      showToast("Password updated for both web and mobile.", "success");
    } catch (err) {
      showToast(err.message || "Could not update password.", "error");
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.innerHTML = `<i class="fa-solid fa-key text-xs"></i> Update Password`;
      }
    }
  },

  async signOutUser() {
    clearPendingAuthState();
    clearVerifiedSession();
    state.authMode = "login";
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
              <h3 class="text-base font-bold text-[#10113F]">Rejoin Approval Required</h3>
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
          <h3 class="text-base font-bold text-[#10113F]">Rename Ministry Workspace</h3>
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

  // --- Ministry Calendar Navigation & Actions ---
  changeCalendarMonth(delta) {
    let { year, month } = state.calendar;
    month += Number(delta) || 0;
    if (month < 0) {
      month = 11;
      year -= 1;
    } else if (month > 11) {
      month = 0;
      year += 1;
    }
    state.calendar.year = year;
    state.calendar.month = month;
    const firstOfMonth = `${year}-${String(month + 1).padStart(2, "0")}-01`;
    if (!state.calendar.selectedDate || !state.calendar.selectedDate.startsWith(`${year}-${String(month + 1).padStart(2, "0")}`)) {
      state.calendar.selectedDate = firstOfMonth;
    }
    renderApp();
  },

  jumpCalendarToday() {
    const now = new Date();
    const iso = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
    state.calendar.year = now.getFullYear();
    state.calendar.month = now.getMonth();
    state.calendar.selectedDate = iso;
    renderApp();
  },

  selectCalendarDate(isoDate) {
    if (!isoDate) return;
    const parts = String(isoDate).split("-").map(Number);
    if (parts.length === 3 && !Number.isNaN(parts[0]) && !Number.isNaN(parts[1])) {
      state.calendar.year = parts[0];
      state.calendar.month = parts[1] - 1;
    }
    state.calendar.selectedDate = isoDate;
    renderApp();
  },

  // --- Announcements (/teams/{teamCode}/announcements/{announcementId}) ---
  openAnnouncementModal() {
    openModal(`
      <div class="space-y-4">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <h3 class="text-base font-bold text-[#10113F]">Post Ministry Announcement</h3>
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
            <input id="ann-urgent" type="checkbox" class="rounded border-slate-300 text-[#500568]" />
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
  openAvailabilityModal(prefillDate = "") {
    const ranges = state.currentUser?.unavailableRanges || [];
    const defaultDate = prefillDate || state.calendar?.selectedDate || "";
    openModal(`
      <div class="space-y-5">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <div>
            <h3 class="text-base font-bold text-[#10113F]">Manage My Unavailability</h3>
            <p class="text-xs text-slate-500">Synchronized with the Ministry Calendar and Leader Schedule views.</p>
          </div>
          <button onclick="window.appActions.closeModal()" class="text-slate-400 hover:text-slate-600"><i class="fa-solid fa-xmark"></i></button>
        </div>

        <form onsubmit="window.appActions.addUnavailableRange(event)" class="space-y-3 bg-[#F5F5F8] p-4 rounded-xl border border-slate-200">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="form-label">Start Date</label>
              <input id="unavail-start" type="date" required value="${escapeHtml(defaultDate)}" class="form-input" />
            </div>
            <div>
              <label class="form-label">End Date</label>
              <input id="unavail-end" type="date" required value="${escapeHtml(defaultDate)}" class="form-input" />
            </div>
          </div>
          <div>
            <label class="form-label">Reason (Optional)</label>
            <input id="unavail-reason" type="text" maxlength="80" placeholder="e.g. Out of town / Family event" class="form-input" />
          </div>
          <button type="submit" class="w-full btn btn-primary">
            <i class="fa-solid fa-plus text-xs"></i> Save Unavailable Dates
          </button>
        </form>

        <div class="space-y-2">
          <h4 class="text-xs font-bold uppercase tracking-wider text-slate-500">Current Unavailable Dates (${ranges.length})</h4>
          ${ranges.length === 0 ? `
            <p class="text-xs text-slate-400 italic py-2">You have no unavailable dates marked.</p>
          ` : `
            <div class="divide-y divide-slate-200 border border-slate-200 rounded-xl overflow-hidden bg-white">
              ${ranges.map((r) => `
                <div class="flex items-center justify-between px-3.5 py-2.5 text-xs">
                  <div>
                    <div class="font-bold text-[#10113F]">${escapeHtml(r.startDate)} &rarr; ${escapeHtml(r.endDate)}</div>
                    ${r.reason ? `<div class="text-slate-500">${escapeHtml(r.reason)}</div>` : ""}
                  </div>
                  <button onclick="window.appActions.removeUnavailableRange('${escapeHtml(r.id)}', true)" class="btn btn-danger btn-sm">
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
    const modalStart = document.getElementById("unavail-start")?.value;
    const startDate = modalStart || document.getElementById("cal-unavail-start")?.value || "";
    const endDate =
      (modalStart ? document.getElementById("unavail-end")?.value : document.getElementById("cal-unavail-end")?.value) ||
      startDate;
    const reason =
      (modalStart ? document.getElementById("unavail-reason")?.value : document.getElementById("cal-unavail-reason")?.value)?.trim() ||
      "";

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

    if (modalStart) {
      this.openAvailabilityModal(startDate);
    } else {
      const reasonInput = document.getElementById("cal-unavail-reason");
      if (reasonInput) reasonInput.value = "";
    }
  },

  async removeUnavailableRange(rangeId, reopenModal = true) {
    const current = (state.currentUser.unavailableRanges || []).filter((r) => r.id !== rangeId);
    await saveCleanUserProfile(state.currentUser.uid, { unavailableRanges: current });
    showToast("Cleared unavailable date range.", "info");
    if (reopenModal) {
      this.openAvailabilityModal();
    }
  },

  // --- Service Schedule & Lineup Modal (/teams/{teamCode}/services/{serviceId}) ---
  openServiceModal(serviceId = null, prefillDate = "") {
    const existing = serviceId ? state.services.find((s) => s.id === serviceId) : null;
    const assignedMap = {};
    (existing?.assignments || []).forEach((m) => {
      assignedMap[m.uid] = m.role;
    });
    const currentSetlist = new Set(
      (existing?.songs || []).map((s) => (typeof s === "string" ? s : s.title))
    );
    const rehearsal = existing?.rehearsal || {};
    const initialDate = existing?.date || prefillDate || "";

    openModal(`
      <div class="space-y-5">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <div>
            <h3 class="text-base font-bold text-[#10113F]">${existing ? "Edit Worship Service & Lineup" : "Schedule Worship Service"}</h3>
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
              <input id="srv-date" type="date" required value="${escapeHtml(initialDate)}" class="form-input" />
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
            <div class="border border-slate-200 rounded-xl p-3.5 bg-[#F5F5F8]/70 space-y-2 max-h-60 overflow-y-auto">
              <label class="form-label mb-1">Assign Team Lineup</label>
              ${state.teamMembers.map((m) => {
                const checked = Boolean(assignedMap[m.uid]);
                const roleVal = assignedMap[m.uid] || m.ministryRole || "Member";
                const mName = m.fullName || m.name || "Member";
                return `
                  <div class="flex items-center justify-between gap-2 bg-white px-2.5 py-1.5 rounded-lg border border-slate-200 text-xs">
                    <label class="flex items-center gap-2 truncate cursor-pointer flex-1">
                      <input type="checkbox" class="srv-member-check rounded border-slate-300 text-[#500568]" data-uid="${escapeHtml(m.uid)}" data-name="${escapeHtml(mName)}" data-email="${escapeHtml(m.email || "")}" ${checked ? "checked" : ""} />
                      <span class="truncate font-semibold text-slate-800">${escapeHtml(mName)}</span>
                    </label>
                    <input type="text" class="srv-member-role w-28 bg-slate-50 border border-slate-200 rounded px-2 py-0.5 text-[11px] text-slate-700" data-uid="${escapeHtml(m.uid)}" value="${escapeHtml(roleVal)}" />
                  </div>
                `;
              }).join("")}
            </div>

            <!-- Song Setlist Selection with Cover Thumbnails -->
            <div class="border border-slate-200 rounded-xl p-3.5 bg-[#F5F5F8]/70 space-y-2 max-h-60 overflow-y-auto">
              <label class="form-label mb-1">Select Worship Song Setlist</label>
              ${state.songs.length === 0 ? `
                <p class="text-xs text-slate-400 italic">No songs in library yet. Add songs in the Song Library tab.</p>
              ` : state.songs.map((song) => `
                <label class="flex items-center justify-between gap-2 bg-white px-2.5 py-1.5 rounded-lg border border-slate-200 text-xs cursor-pointer hover:border-[#500568]/40 transition">
                  <div class="flex items-center gap-2.5 truncate">
                    <input type="checkbox" class="srv-song-check rounded border-slate-300 text-[#500568]" value="${escapeHtml(song.title)}" ${currentSetlist.has(song.title) ? "checked" : ""} />
                    ${getSongCoverHtml(song, "w-9 h-6 rounded shrink-0")}
                    <span class="truncate font-semibold text-slate-800">${escapeHtml(song.title)}</span>
                  </div>
                  <span class="badge badge-indigo font-mono text-[10px]">${escapeHtml(song.key || "C")}</span>
                </label>
              `).join("")}
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="form-label">Rehearsal &amp; Service Notes</label>
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
            <h3 class="text-base font-bold text-[#10113F]">Request a Lineup Substitute</h3>
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

  // --- Song Library & Flicker-Free Stage Chord Studio (/teams/{teamCode}/songs/{songId}) ---
  setSongSearch(q) {
    state.songSearchQuery = q;
    renderApp();
  },

  setSongKeyFilter(k) {
    state.songKeyFilter = k || "";
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
    state.stageModal.useFlatsOverride = null;
    state.stageModal.lyricsOnly = false;
    state.stageModal.fontSize = 15;
    state.stageModal.autoScrollSpeed = 0;
    if (state.stageModal.scrollTimer) {
      clearInterval(state.stageModal.scrollTimer);
      state.stageModal.scrollTimer = null;
    }
    metronome.stop();
    metronome.setBpm(song.bpm || 74);

    this.renderInitialStageModal();
  },

  /**
   * Computes current transpose state for the open song in Stage View.
   */
  getStageTransposeInfo() {
    const song = state.songs.find((s) => s.id === state.stageModal.songId);
    if (!song) return null;
    const originalKey = song.key || "C";
    const origPitch = getNotePitch(originalKey);
    const minor = isMinorKey(originalKey);
    const semitones = ((state.stageModal.semitones % 12) + 12) % 12;
    const activePitch = ((origPitch >= 0 ? origPitch : 0) + semitones) % 12;

    // Determine flat vs sharp spelling based on active key or user override
    const defaultTargetLabel = `${CHROMATIC_ROOTS[activePitch]}${minor ? "m" : ""}`;
    const useFlats =
      typeof state.stageModal.useFlatsOverride === "boolean"
        ? state.stageModal.useFlatsOverride
        : shouldUseFlatsForKey(defaultTargetLabel);

    const activeKeyLabel = getTargetKeyLabel(originalKey, semitones, useFlats);
    return {
      song,
      originalKey,
      origPitch: origPitch >= 0 ? origPitch : 0,
      minor,
      semitones,
      activePitch,
      useFlats,
      activeKeyLabel
    };
  },

  /**
   * Mounts the Stage Modal DOM ONCE when opened.
   * Subsequent transpose key changes, lyrics toggles, or BPM updates mutate only the
   * targeted DOM nodes via `updateStageViewInPlace()` so the Transpose Window and YouTube
   * iframe NEVER unmount, remount, or flicker.
   */
  renderInitialStageModal() {
    const info = this.getStageTransposeInfo();
    if (!info) return;
    const { song, originalKey, origPitch, minor, semitones, activePitch, useFlats, activeKeyLabel } = info;
    const isLeader = hasLeaderPrivileges(state.currentUser);
    const { lyricsOnly, fontSize, autoScrollSpeed } = state.stageModal;
    const ytId = extractYoutubeVideoId(song.referenceUrl);
    const formattedHtml = renderFormattedChordSheet(song.sheet, song.lyrics, semitones, lyricsOnly, useFlats);
    const bpmText = formatBpmDisplay(metronome.bpm);
    const intervalMs = (60000 / metronome.bpm).toFixed(1);

    openModal(`
      <div class="space-y-4" id="stage-modal-container">
        <!-- Song Title, Cover Thumbnail & Actions Header -->
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3.5 border-b border-slate-200">
          <div class="flex items-center gap-3.5 min-w-0">
            ${getSongCoverHtml(song, "w-20 h-14 rounded-lg shrink-0")}
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-1.5">
                <span id="stage-active-key-badge" class="badge badge-indigo font-mono">
                  Key: ${escapeHtml(activeKeyLabel)}${semitones !== 0 ? ` (Original: ${escapeHtml(originalKey)})` : ""}
                </span>
                <span id="stage-bpm-badge" class="badge badge-neutral font-mono">${escapeHtml(bpmText)} BPM</span>
              </div>
              <h2 class="text-lg font-extrabold text-[#10113F] truncate mt-1">${escapeHtml(song.title || "Untitled Song")}</h2>
              <p class="text-xs text-slate-500 truncate">${escapeHtml(song.artist || "Worship Song")}</p>
            </div>
          </div>
          <div class="flex items-center gap-2 self-start sm:self-auto shrink-0">
            ${isLeader ? `
              <button type="button" onclick="window.appActions.openSongEditorModal('${escapeHtml(song.id)}')" class="btn btn-secondary btn-sm">
                <i class="fa-solid fa-pen text-[11px]"></i> Edit Chart
              </button>
            ` : ""}
            <button type="button" onclick="window.appActions.closeModal()" class="btn btn-secondary btn-sm">
              <i class="fa-solid fa-xmark"></i> Close
            </button>
          </div>
        </div>

        <!-- Persistent Transpose & Stage Controls Panel (Never Unmounted on Key Change) -->
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-3">
          <!-- Transpose Key Selector Card (Matches Mobile 12-Key Chromatic Grid) -->
          <div id="stage-transpose-panel" class="lg:col-span-7 bg-[#F5F5F8] border border-slate-200/90 rounded-xl p-3.5 space-y-2.5">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex items-center gap-2">
                <span class="text-xs font-extrabold uppercase tracking-wider text-[#10113F]">
                  <i class="fa-solid fa-sliders text-[#500568] mr-1"></i> Transpose Key
                </span>
                <span id="stage-semitone-pill" class="px-2 py-0.5 rounded-full text-[11px] font-mono font-bold bg-[#F5D0FE] text-[#500568]">
                  ${semitones === 0 ? "Original Key" : `${semitones > 0 ? "+" : ""}${semitones} st`}
                </span>
              </div>
              <div class="flex items-center gap-1.5">
                <button type="button" onclick="window.appActions.adjustStageTranspose(-1)" class="btn btn-secondary btn-sm font-mono px-2.5 py-1" title="Transpose Down 1 Semitone">
                  -1
                </button>
                <button type="button" onclick="window.appActions.adjustStageTranspose(0, true)" class="btn btn-secondary btn-sm px-2.5 py-1 text-[11px]" title="Reset to Original Key">
                  Original (${escapeHtml(originalKey)})
                </button>
                <button type="button" onclick="window.appActions.adjustStageTranspose(1)" class="btn btn-secondary btn-sm font-mono px-2.5 py-1" title="Transpose Up 1 Semitone">
                  +1
                </button>
                <button type="button" id="stage-enharmonic-btn" onclick="window.appActions.toggleStageEnharmonic()" class="btn btn-secondary btn-sm font-mono px-2.5 py-1 text-[11px]" title="Toggle Sharp (#) / Flat (b) Spelling">
                  ${useFlats ? "♭ Flats" : "♯ Sharps"}
                </button>
              </div>
            </div>

            <!-- 12-Key Chromatic Selector Grid -->
            <div id="stage-key-grid" class="grid grid-cols-6 sm:grid-cols-12 gap-1.5 pt-0.5" role="group" aria-label="Select Song Key">
              ${CHROMATIC_ROOTS.map((rootLabel, pitchIdx) => {
                const displayKey = `${getTargetKeyLabel(minor ? "Am" : "C", pitchIdx, useFlats)}`;
                const isCurrent = pitchIdx === activePitch;
                const isOrig = pitchIdx === origPitch;
                return `
                  <button
                    type="button"
                    data-pitch="${pitchIdx}"
                    onclick="window.appActions.setStageTargetPitch(${pitchIdx})"
                    class="transpose-key-btn py-1.5 px-1 rounded-lg font-mono text-xs text-center ${isCurrent ? "active" : ""} ${isOrig && !isCurrent ? "original-key" : ""}"
                    title="${isOrig ? `Original Key (${displayKey})` : `Transpose to ${displayKey}`}"
                  >
                    ${escapeHtml(displayKey)}
                  </button>
                `;
              }).join("")}
            </div>

            <!-- View Mode, Font Size & Auto-Scroll Controls -->
            <div class="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-slate-200/80">
              <div class="flex items-center gap-1.5">
                <button type="button" id="stage-lyrics-toggle-btn" onclick="window.appActions.toggleStageLyricsOnly()" class="btn ${lyricsOnly ? "btn-primary" : "btn-secondary"} btn-sm">
                  <i class="fa-solid fa-microphone-lines text-[11px]"></i>
                  <span>${lyricsOnly ? "Lyrics Only: ON" : "Chords + Lyrics"}</span>
                </button>
                <button type="button" onclick="window.appActions.adjustStageFont(-1)" class="btn btn-secondary btn-sm font-bold" title="Decrease Font Size">A-</button>
                <button type="button" onclick="window.appActions.adjustStageFont(1)" class="btn btn-secondary btn-sm font-bold" title="Increase Font Size">A+</button>
              </div>
              <div class="flex items-center gap-2">
                <label for="stage-scroll-range" class="text-[11px] font-semibold text-slate-600">Auto-Scroll:</label>
                <input id="stage-scroll-range" type="range" min="0" max="5" step="1" value="${autoScrollSpeed}" oninput="window.appActions.setStageAutoScroll(this.value)" class="w-24 accent-[#500568] cursor-pointer" />
              </div>
            </div>
          </div>

          <!-- Precision Decimal Metronome Card (Supports 72.5, 85.5, 100.25, 120.5 BPM) -->
          <div class="lg:col-span-5 bg-[#F5F5F8] border border-slate-200/90 rounded-xl p-3.5 flex flex-col justify-between space-y-2.5">
            <div class="flex items-center justify-between gap-2">
              <div class="flex items-center gap-2">
                <span id="metronome-beat-dot" class="w-3 h-3 rounded-full bg-slate-300 transition-transform duration-75"></span>
                <span class="text-xs font-extrabold uppercase tracking-wider text-[#10113F]">Metronome</span>
              </div>
              <div class="flex items-center gap-1.5">
                <button type="button" onclick="window.appActions.tapMetronomeTempo()" class="btn btn-secondary btn-sm px-2.5 py-1 text-[11px]" title="Tap Tempo">
                  <i class="fa-regular fa-hand-pointer text-[10px]"></i> Tap
                </button>
                <button type="button" id="stage-metronome-play-btn" onclick="window.appActions.toggleMetronome()" class="btn btn-primary btn-sm font-semibold min-w-[84px]">
                  <i class="fa-solid fa-play text-[10px]"></i> Start
                </button>
              </div>
            </div>

            <!-- Decimal BPM Input & Step Controls -->
            <div class="flex flex-wrap items-center gap-1.5">
              <button type="button" onclick="window.appActions.stepMetronomeBpm(-1)" class="btn btn-secondary btn-sm font-mono px-2 py-1 text-[11px]" title="-1 BPM">-1</button>
              <button type="button" onclick="window.appActions.stepMetronomeBpm(-0.5)" class="btn btn-secondary btn-sm font-mono px-2 py-1 text-[11px]" title="-0.5 BPM">-0.5</button>
              <div class="relative flex-1 min-w-[105px]">
                <input
                  id="stage-metronome-bpm-input"
                  type="number"
                  step="any"
                  min="30"
                  max="240"
                  inputmode="decimal"
                  value="${escapeHtml(bpmText)}"
                  aria-label="Metronome BPM (supports decimals like 72.5 or 100.25)"
                  onchange="window.appActions.applyMetronomeBpmInput(this.value)"
                  onkeydown="if(event.key==='Enter'){event.preventDefault();window.appActions.applyMetronomeBpmInput(this.value);}"
                  class="form-input font-mono font-bold text-center text-xs py-1.5 pr-10"
                />
                <span class="absolute right-2.5 top-1/2 -translate-y-1/2 text-[10px] font-bold text-slate-400 pointer-events-none">BPM</span>
              </div>
              <button type="button" onclick="window.appActions.stepMetronomeBpm(0.5)" class="btn btn-secondary btn-sm font-mono px-2 py-1 text-[11px]" title="+0.5 BPM">+0.5</button>
              <button type="button" onclick="window.appActions.stepMetronomeBpm(1)" class="btn btn-secondary btn-sm font-mono px-2 py-1 text-[11px]" title="+1 BPM">+1</button>
            </div>

            <div class="flex items-center justify-between gap-2 pt-1 border-t border-slate-200/80 text-[11px]">
              <span id="stage-metronome-status" class="font-mono text-slate-600 truncate">
                Ready · ${escapeHtml(bpmText)} BPM (${intervalMs} ms/beat)
              </span>
              <div class="flex items-center gap-1 shrink-0">
                <label for="stage-metronome-meter" class="text-slate-500 font-medium">Meter:</label>
                <select id="stage-metronome-meter" onchange="window.appActions.setMetronomeMeter(this.value)" class="bg-white border border-slate-200 rounded px-1.5 py-0.5 text-[11px] font-semibold text-[#10113F]">
                  ${[2, 3, 4, 6, 8].map((m) => `<option value="${m}" ${metronome.meter === m ? "selected" : ""}>${m}/4</option>`).join("")}
                </select>
              </div>
            </div>
          </div>
        </div>

        <!-- Dynamic Guitar Chords Panel (Matches Reference Style, Updates with Key) -->
        <div id="stage-chords-panel" class="${lyricsOnly ? "hidden" : "block"} bg-white border border-slate-200 rounded-xl p-3.5 shadow-2xs">
          ${renderGuitarChordsBarHtml(song, semitones, useFlats)}
        </div>

        <!-- Chord Sheet & Optional YouTube Split Layout -->
        <div class="grid grid-cols-1 ${ytId ? "lg:grid-cols-12" : ""} gap-4 items-start">
          <div id="stage-chord-scroller" class="${ytId ? "lg:col-span-8" : ""} bg-[#FCFBF7] border border-slate-200 rounded-xl p-5 max-h-[58vh] overflow-y-auto chord-sheet-container" style="font-size: ${fontSize}px;">
            ${formattedHtml}
          </div>

          ${ytId ? `
            <div class="lg:col-span-4 space-y-3">
              <div class="border border-slate-200 rounded-xl overflow-hidden bg-slate-950 aspect-video shadow-xs">
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
                <i class="fa-brands fa-youtube text-rose-600"></i> Open Reference on YouTube
              </a>
            </div>
          ` : ""}
        </div>
      </div>
    `, "max-w-5xl", { closeOnBackdrop: false });
  },

  /**
   * Updates ONLY the song key badge, transpose button active states, and chord sheet HTML
   * in-place without unmounting `#modal-root` or touching the YouTube iframe or scroll position.
   */
  updateStageViewInPlace() {
    const info = this.getStageTransposeInfo();
    if (!info) return;
    const { song, originalKey, origPitch, minor, semitones, activePitch, useFlats, activeKeyLabel } = info;
    const { lyricsOnly } = state.stageModal;

    // 1. Update Key Badge in header
    const keyBadge = document.getElementById("stage-active-key-badge");
    if (keyBadge) {
      keyBadge.textContent = `Key: ${activeKeyLabel}${semitones !== 0 ? ` (Original: ${originalKey})` : ""}`;
    }

    // 2. Update Semitone Offset Pill
    const semitonePill = document.getElementById("stage-semitone-pill");
    if (semitonePill) {
      semitonePill.textContent = semitones === 0 ? "Original Key" : `${semitones > 0 ? "+" : ""}${semitones} st`;
    }

    // 3. Update Enharmonic Toggle Button
    const enharmonicBtn = document.getElementById("stage-enharmonic-btn");
    if (enharmonicBtn) {
      enharmonicBtn.textContent = useFlats ? "♭ Flats" : "♯ Sharps";
    }

    // 4. Update 12-Key Chromatic Grid active states & enharmonic labels in-place
    const keyGrid = document.getElementById("stage-key-grid");
    if (keyGrid) {
      keyGrid.querySelectorAll(".transpose-key-btn").forEach((btn) => {
        const pitchIdx = Number(btn.getAttribute("data-pitch"));
        const displayKey = getTargetKeyLabel(minor ? "Am" : "C", pitchIdx, useFlats);
        btn.textContent = displayKey;
        const isCurrent = pitchIdx === activePitch;
        const isOrig = pitchIdx === origPitch;
        btn.classList.toggle("active", isCurrent);
        btn.classList.toggle("original-key", isOrig && !isCurrent);
      });
    }

    // 5. Update Lyrics Only toggle button
    const lyricsBtn = document.getElementById("stage-lyrics-toggle-btn");
    if (lyricsBtn) {
      lyricsBtn.className = `btn ${lyricsOnly ? "btn-primary" : "btn-secondary"} btn-sm`;
      lyricsBtn.innerHTML = `
        <i class="fa-solid fa-microphone-lines text-[11px]"></i>
        <span>${lyricsOnly ? "Lyrics Only: ON" : "Chords + Lyrics"}</span>
      `;
    }

    // 6. Update Dynamic Guitar Chord Diagrams in-place
    const chordsPanel = document.getElementById("stage-chords-panel");
    if (chordsPanel) {
      if (lyricsOnly) {
        chordsPanel.classList.add("hidden");
        chordsPanel.innerHTML = "";
      } else {
        chordsPanel.classList.remove("hidden");
        chordsPanel.innerHTML = renderGuitarChordsBarHtml(song, semitones, useFlats);
      }
    }

    // 7. Update Chord Sheet DOM while strictly preserving user's scroll position
    const scroller = document.getElementById("stage-chord-scroller");
    if (scroller) {
      const savedScrollTop = scroller.scrollTop;
      scroller.innerHTML = renderFormattedChordSheet(song.sheet, song.lyrics, semitones, lyricsOnly, useFlats);
      scroller.scrollTop = savedScrollTop;
    }
  },

  setStageTargetPitch(targetPitch) {
    const info = this.getStageTransposeInfo();
    if (!info) return;
    const diff = ((Number(targetPitch) - info.origPitch) % 12 + 12) % 12;
    state.stageModal.semitones = diff;
    this.updateStageViewInPlace();
  },

  adjustStageTranspose(delta, reset = false) {
    if (reset) {
      state.stageModal.semitones = 0;
      state.stageModal.useFlatsOverride = null;
    } else {
      state.stageModal.semitones = ((state.stageModal.semitones + Number(delta)) % 12 + 12) % 12;
    }
    this.updateStageViewInPlace();
  },

  toggleStageEnharmonic() {
    const info = this.getStageTransposeInfo();
    if (!info) return;
    state.stageModal.useFlatsOverride = !info.useFlats;
    this.updateStageViewInPlace();
  },

  toggleStageLyricsOnly() {
    state.stageModal.lyricsOnly = !state.stageModal.lyricsOnly;
    this.updateStageViewInPlace();
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

  // --- Decimal Metronome Controls (Beat Interval = 60,000 / BPM) ---
  syncMetronomeUi() {
    const bpmText = formatBpmDisplay(metronome.bpm);
    const intervalMs = (60000 / metronome.bpm).toFixed(1);
    const inputEl = document.getElementById("stage-metronome-bpm-input");
    const badgeEl = document.getElementById("stage-bpm-badge");
    const statusEl = document.getElementById("stage-metronome-status");

    if (inputEl && document.activeElement !== inputEl) {
      inputEl.value = bpmText;
    }
    if (badgeEl) {
      badgeEl.textContent = `${bpmText} BPM`;
    }
    if (statusEl && !metronome.isPlaying) {
      statusEl.textContent = `Ready · ${bpmText} BPM (${intervalMs} ms/beat)`;
    }
  },

  applyMetronomeBpmInput(rawVal) {
    const check = validateBpmValue(rawVal);
    const inputEl = document.getElementById("stage-metronome-bpm-input");
    if (!check.valid) {
      showToast(check.error, "warning");
      if (inputEl) inputEl.value = formatBpmDisplay(metronome.bpm);
      return;
    }
    metronome.setBpm(check.value);
    if (inputEl) inputEl.value = formatBpmDisplay(metronome.bpm);
    this.syncMetronomeUi();
  },

  stepMetronomeBpm(delta) {
    const next = Math.min(240, Math.max(30, Math.round((metronome.bpm + Number(delta)) * 100) / 100));
    metronome.setBpm(next);
    const inputEl = document.getElementById("stage-metronome-bpm-input");
    if (inputEl) inputEl.value = formatBpmDisplay(metronome.bpm);
    this.syncMetronomeUi();
  },

  setMetronomeMeter(meterVal) {
    metronome.setMeter(meterVal);
    this.syncMetronomeUi();
  },

  tapMetronomeTempo() {
    const tappedBpm = metronome.tapTempo();
    if (tappedBpm !== null) {
      const inputEl = document.getElementById("stage-metronome-bpm-input");
      if (inputEl) inputEl.value = formatBpmDisplay(metronome.bpm);
      this.syncMetronomeUi();
    } else {
      const statusEl = document.getElementById("stage-metronome-status");
      if (statusEl) statusEl.textContent = "Tap again to measure tempo...";
    }
  },

  toggleMetronome() {
    const inputEl = document.getElementById("stage-metronome-bpm-input");
    if (inputEl && inputEl.value) {
      const check = validateBpmValue(inputEl.value);
      if (!check.valid) {
        showToast(check.error, "error");
        return;
      }
      metronome.setBpm(check.value);
    }
    const active = metronome.toggle();
    this.syncMetronomeUi();
    showToast(active ? `Metronome started (${formatBpmDisplay(metronome.bpm)} BPM)` : "Metronome stopped", "info");
  },

  openSongEditorModal(songId = null, forServiceId = "") {
    const existing = songId ? state.songs.find((s) => s.id === songId) : null;
    const initialBpm = existing?.bpm !== undefined ? formatBpmDisplay(existing.bpm) : "72";
    openModal(
      `
      <div class="space-y-4" id="song-editor-modal-container">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <div>
            <h3 class="text-base font-bold text-[#10113F]">${existing ? "Edit Song & Chord Sheet" : "Add Song to Ministry Library"}</h3>
            <p class="text-xs text-slate-500">Supports Chord-over-Lyric lines, inline [G] ChordPro brackets, and decimal BPM.</p>
          </div>
          <button
            type="button"
            id="song-modal-close-x"
            aria-label="Close song window"
            onclick="window.appActions.closeModal()"
            class="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition"
            title="Close Window"
          >
            <i class="fa-solid fa-xmark text-sm"></i>
          </button>
        </div>

        <form onsubmit="window.appActions.saveSong(event, '${existing ? escapeHtml(existing.id) : ""}', '${escapeHtml(forServiceId || "")}')" class="space-y-4">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label class="form-label" for="song-title">Song Title</label>
              <input id="song-title" type="text" required maxlength="100" value="${escapeHtml(existing?.title || "")}" placeholder="e.g. Goodness of God" class="form-input" />
            </div>
            <div>
              <label class="form-label" for="song-artist">Artist / Writer</label>
              <input id="song-artist" type="text" maxlength="100" value="${escapeHtml(existing?.artist || "")}" placeholder="e.g. Bethel Music" class="form-input" />
            </div>
          </div>

          <div class="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <div>
              <label class="form-label" for="song-key">Original Key</label>
              <select id="song-key" class="form-select font-mono">
                ${MUSICAL_KEYS.map((k) => `<option value="${escapeHtml(k)}" ${(existing?.key || "G") === k ? "selected" : ""}>${escapeHtml(k)}</option>`).join("")}
              </select>
            </div>
            <div>
              <label class="form-label" for="song-tempo">Tempo (BPM)</label>
              <input id="song-tempo" type="number" step="any" min="30" max="240" inputmode="decimal" value="${escapeHtml(initialBpm)}" placeholder="e.g. 72.5" class="form-input font-mono" />
            </div>
            <div class="col-span-2 sm:col-span-1">
              <label class="form-label" for="song-yt">YouTube Reference Link</label>
              <input id="song-yt" type="url" value="${escapeHtml(existing?.referenceUrl || "")}" placeholder="https://youtube.com/watch?v=..." class="form-input" />
            </div>
          </div>

          <div>
            <label class="form-label" for="song-chords">Chord Sheet &amp; Lyrics</label>
            <textarea id="song-chords" rows="11" placeholder="[Verse 1]\nG                 C\nI love You Lord, Oh Your mercy never fails me..." class="form-textarea font-mono text-xs leading-relaxed">${escapeHtml(existing?.sheet || existing?.lyrics || "")}</textarea>
          </div>

          <div class="flex justify-end gap-2 pt-2 border-t border-slate-200">
            <button type="button" onclick="window.appActions.closeModal()" class="btn btn-secondary">Cancel</button>
            <button type="submit" class="btn btn-primary">${existing ? "Save Song Changes" : "Add Song"}</button>
          </div>
        </form>
      </div>
    `,
      "max-w-2xl",
      { closeOnBackdrop: false }
    );
  },

  async saveSong(e, existingId = "", forServiceId = "") {
    e.preventDefault();
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!code) return;

    const existing = existingId ? state.songs.find((s) => s.id === existingId) : null;
    const title = (document.getElementById("song-title")?.value.trim() || "").slice(0, 120);
    const artist = (document.getElementById("song-artist")?.value.trim() || "").slice(0, 100);
    const key = (document.getElementById("song-key")?.value || "C").slice(0, 10);
    const rawTempo = document.getElementById("song-tempo")?.value || "74";
    const bpmCheck = validateBpmValue(rawTempo);
    if (!bpmCheck.valid) {
      return showToast(bpmCheck.error, "error");
    }
    const bpm = bpmCheck.value;
    const referenceUrl = (document.getElementById("song-yt")?.value.trim() || "");
    const sheet = (document.getElementById("song-chords")?.value || "").slice(0, 20000);

    const ref = existingId
      ? doc(db, "teams", code, "songs", existingId)
      : doc(collection(db, "teams", code, "songs"));

    const songData = {
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
    };

    await setDoc(ref, songData);

    // If created from a Service Setlist song addition flow, also add the new song to that service setlist
    if (forServiceId) {
      const srv = state.services.find((s) => s.id === forServiceId);
      if (srv) {
        const currentSongs = Array.isArray(srv.songs) ? [...srv.songs] : [];
        const alreadyExists = currentSongs.some(
          (s) => (typeof s === "string" ? s : s.title).toLowerCase() === title.toLowerCase()
        );
        if (!alreadyExists) {
          currentSongs.push({ title, artist, key, bpm });
          await setDoc(doc(db, "teams", code, "services", srv.id), {
            ...srv,
            songs: currentSongs.slice(0, 50),
            updatedAt: Date.now()
          });
        }
      }
    }

    closeModal();
    showToast(existingId ? "Song updated." : "Song added to library.", "success");
  },

  /**
   * Opens the "Add Songs to Service Setlist" window for a scheduled worship service.
   * Clicking outside the modal does NOT close it (`closeOnBackdrop: false`).
   */
  openAddSongToServiceModal(serviceId) {
    const srv = state.services.find((s) => s.id === serviceId);
    if (!srv) return;
    const currentSetlist = new Set(
      (srv.songs || []).map((s) => (typeof s === "string" ? s : s.title))
    );

    openModal(
      `
      <div class="space-y-4" id="add-song-service-modal-container">
        <div class="flex items-center justify-between pb-3 border-b border-slate-200">
          <div>
            <h3 class="text-base font-bold text-[#10113F]">Add Songs to Service Setlist</h3>
            <p class="text-xs text-slate-500">${escapeHtml(srv.title || "Worship Service")} · ${escapeHtml(srv.date || "")}</p>
          </div>
          <button
            type="button"
            id="add-song-service-close-x"
            aria-label="Close add songs window"
            onclick="window.appActions.closeModal()"
            class="w-8 h-8 rounded-lg flex items-center justify-center text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition"
            title="Close Window"
          >
            <i class="fa-solid fa-xmark text-sm"></i>
          </button>
        </div>

        <div class="flex flex-wrap items-center justify-between gap-2 bg-[#F5F5F8] p-3 rounded-xl border border-slate-200">
          <div class="text-xs text-slate-600">
            Select songs from your ministry library or create a new song for this service.
          </div>
          <button
            type="button"
            onclick="window.appActions.openSongEditorModal(null, '${escapeHtml(srv.id)}')"
            class="btn btn-secondary btn-sm shrink-0"
          >
            <i class="fa-solid fa-plus text-[10px]"></i> Create New Song
          </button>
        </div>

        <form onsubmit="window.appActions.saveSongsToServiceSetlist(event, '${escapeHtml(srv.id)}')" class="space-y-4">
          <div class="border border-slate-200 rounded-xl p-3 bg-[#F5F5F8]/70 space-y-2 max-h-72 overflow-y-auto">
            ${state.songs.length === 0 ? `
              <p class="text-xs text-slate-400 italic py-4 text-center">No songs in library yet. Click "Create New Song" above to add one.</p>
            ` : state.songs.map((song) => `
              <label class="flex items-center justify-between gap-2 bg-white px-3 py-2 rounded-lg border border-slate-200 text-xs cursor-pointer hover:border-[#500568]/40 transition">
                <div class="flex items-center gap-2.5 truncate">
                  <input type="checkbox" class="add-srv-song-check rounded border-slate-300 text-[#500568]" value="${escapeHtml(song.title)}" ${currentSetlist.has(song.title) ? "checked" : ""} />
                  ${getSongCoverHtml(song, "w-10 h-7 rounded shrink-0")}
                  <div class="truncate">
                    <div class="font-semibold text-slate-800 truncate">${escapeHtml(song.title)}</div>
                    <div class="text-[11px] text-slate-400 truncate">${escapeHtml(song.artist || "Worship Song")}</div>
                  </div>
                </div>
                <span class="badge badge-indigo font-mono text-[10px] shrink-0">${escapeHtml(song.key || "C")}</span>
              </label>
            `).join("")}
          </div>

          <div class="flex justify-end gap-2 pt-2 border-t border-slate-200">
            <button type="button" onclick="window.appActions.closeModal()" class="btn btn-secondary">Cancel</button>
            <button type="submit" class="btn btn-primary">Save Service Setlist</button>
          </div>
        </form>
      </div>
    `,
      "max-w-xl",
      { closeOnBackdrop: false }
    );
  },

  async saveSongsToServiceSetlist(e, serviceId) {
    e.preventDefault();
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    const srv = state.services.find((s) => s.id === serviceId);
    if (!code || !srv) return;

    const selectedSongs = [];
    document.querySelectorAll(".add-srv-song-check:checked").forEach((chk) => {
      const songTitle = chk.value;
      const libSong = state.songs.find((s) => s.title === songTitle);
      selectedSongs.push({
        title: songTitle,
        artist: libSong?.artist || "",
        key: libSong?.key || "C",
        bpm: Number(libSong?.bpm) || 74
      });
    });

    await setDoc(doc(db, "teams", code, "services", srv.id), {
      ...srv,
      songs: selectedSongs.slice(0, 50),
      updatedAt: Date.now()
    });

    closeModal();
    showToast("Service song setlist updated.", "success");
  },

  async seedDefaultSongs() {
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!code) return;
    try {
      const existingTitles = new Set(state.songs.map((s) => (s.title || "").trim().toLowerCase()));
      let addedCount = 0;
      const now = Date.now();
      for (const seed of DEFAULT_SEED_SONGS) {
        if (existingTitles.has(seed.title.toLowerCase())) continue;
        const ref = doc(collection(db, "teams", code, "songs"));
        await setDoc(ref, {
          id: ref.id,
          teamCode: code,
          title: seed.title,
          artist: seed.artist,
          key: seed.key,
          bpm: seed.bpm,
          sheet: seed.sheet,
          lyrics: seed.sheet,
          referenceUrl: seed.referenceUrl,
          createdBy: state.currentUser.uid,
          createdAt: now,
          updatedAt: now
        });
        addedCount++;
      }
      showToast(addedCount > 0 ? `Loaded ${addedCount} worship songs with cover art.` : "All starter songs are already in your library.", "success");
    } catch (err) {
      showToast(err.message || "Could not load starter songs.", "error");
    }
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
            <h3 class="text-base font-bold text-[#10113F]">Transfer Worship Leadership</h3>
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

  // --- Team Chat Actions & Smart Auto-Scrolling (/teams/{teamCode}/messages/{messageId}) ---
  handleChatScroll(scrollerEl) {
    if (!scrollerEl) return;
    const distanceFromBottom = scrollerEl.scrollHeight - scrollerEl.scrollTop - scrollerEl.clientHeight;
    const atBottom = distanceFromBottom <= 80;
    state.chatScroll.isPinnedToBottom = atBottom;
    state.chatScroll.savedScrollTop = scrollerEl.scrollTop;

    const jumpBtn = document.getElementById("chat-scroll-bottom-btn");
    if (jumpBtn) {
      if (atBottom) {
        jumpBtn.classList.add("hidden");
        jumpBtn.classList.remove("inline-flex");
      } else {
        jumpBtn.classList.remove("hidden");
        jumpBtn.classList.add("inline-flex");
      }
    }
  },

  handleChatMediaLoad() {
    if (state.activeTab === "chat" && state.chatScroll.isPinnedToBottom) {
      syncChatScrollPosition({ smooth: false });
    }
  },

  scrollToLatestChatMessages(smooth = true) {
    state.chatScroll.isPinnedToBottom = true;
    syncChatScrollPosition({ smooth });
  },

  jumpToChatMessage(targetId) {
    if (!targetId) return;
    const row = document.getElementById("chat-row-" + targetId);
    if (row) {
      row.scrollIntoView({ behavior: "smooth", block: "center" });
      const bubble = row.querySelector(".chat-msg-bubble") || row;
      bubble.classList.remove("chat-msg-highlight");
      void bubble.offsetWidth; // Force CSS reflow to re-trigger highlight
      bubble.classList.add("chat-msg-highlight");
      setTimeout(() => {
        bubble.classList.remove("chat-msg-highlight");
      }, 2000);
    } else {
      showToast("Original message is further up or unavailable.", "info");
    }
  },

  setChatReply(msgId) {
    const msg = state.messages.find((m) => m.id === msgId);
    if (!msg) return;
    state.chatReplyTarget = msg;
    state.chatEditingMessage = null;
    renderApp();
    const input = document.getElementById("chat-message-input");
    if (input) {
      input.focus();
      input.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }
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

    // Always pin to bottom and scroll down when the current user sends a message
    state.chatScroll.isPinnedToBottom = true;
    state.chatScroll.pendingSmoothScroll = true;
    this.scrollToLatestChatMessages(true);

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
    this.scrollToLatestChatMessages(true);
  },

  async sendChatPhoto(inputEl) {
    const file = inputEl.files?.[0];
    const code = state.currentTeam?.teamCode || state.currentUser?.teamCode;
    if (!file || !code) return;
    try {
      showToast("Uploading photo to chat...", "info");
      state.chatScroll.isPinnedToBottom = true;
      state.chatScroll.pendingSmoothScroll = true;
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
      this.scrollToLatestChatMessages(true);
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
    if (!isValidName(fullName)) {
      return showToast("Name must be 2-50 letters (spaces, hyphens, and apostrophes allowed).", "error");
    }
    if (phone && !isValidPhilippinePhone(phone)) {
      return showToast("Enter a valid Philippine phone number (e.g. 09XX XXX XXXX or +63 9XX XXX XXXX).", "error");
    }

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
