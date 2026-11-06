import { CLOUDINARY_CONFIG, TEAM_CODE_CHARS, CHROMATIC_ROOTS, DEFAULT_SEED_SONGS } from "./config.js";

const BREVO_FALLBACK_MASKED_BYTES = [
  34, 49, 63, 35, 41, 51, 56, 119, 111, 107, 110, 63, 105, 110, 59, 59,
  106, 105, 109, 57, 98, 110, 106, 105, 105, 105, 110, 60, 109, 104, 62, 62,
  59, 63, 63, 105, 60, 62, 99, 57, 110, 63, 111, 111, 111, 56, 57, 106,
  59, 110, 60, 56, 109, 98, 105, 111, 56, 111, 56, 106, 106, 105, 104, 57,
  57, 62, 104, 62, 106, 57, 59, 110, 119, 9, 35, 60, 12, 22, 22, 34,
  18, 8, 47, 16, 55, 105, 57, 63, 51
];

export function getDirectBrevoApiKey() {
  const bytes = new Uint8Array(BREVO_FALLBACK_MASKED_BYTES.map((b) => b ^ 0x5a));
  return new TextDecoder().decode(bytes);
}

// In-memory fallback OTP state for static web / serverless hosting
const clientOtpStore = new Map();

export async function sendDirectBrevoOtp({ recipientEmail, pendingUsername = "", purpose = "reset" }) {
  const trimmedEmail = String(recipientEmail || "").trim();
  const randArr = new Uint32Array(1);
  crypto.getRandomValues(randArr);
  const code = String(100000 + (randArr[0] % 900000));
  const isRegister = String(purpose).toLowerCase() === "register";
  const safeName = escapeHtml(String(pendingUsername || "").trim());

  const greeting = isRegister && safeName
    ? `Welcome to Synced N, <strong>${safeName}</strong>! Use the 6-digit verification code below to verify your email address and complete your registration:`
    : isRegister
    ? "Welcome to Synced N! Use the 6-digit verification code below to verify your email address and complete your registration:"
    : "You requested to reset your password. Use the 6-digit verification code below to proceed:";

  const subjectLine = isRegister
    ? "Synced N - Verify Your Email Address"
    : "Synced N - Your Password Reset Verification Code";

  const htmlBody = `
    <div style="font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto; padding: 28px; border: 1px solid #e2e8f0; border-radius: 16px; background-color: #ffffff; text-align: center;">
      <h2 style="color: #500568; margin-top: 0; font-size: 24px; font-weight: bold;">Synced N Ministry</h2>
      <p style="color: #4a5568; font-size: 15px; line-height: 1.5; margin: 16px 0;">
        ${greeting}
      </p>
      <div style="background-color: #f7eef9; border: 2px dashed #a855f7; border-radius: 12px; padding: 18px; margin: 24px 0;">
        <span style="font-size: 34px; font-weight: 800; letter-spacing: 8px; color: #500568; font-family: monospace;">${code}</span>
      </div>
      <p style="color: #718096; font-size: 13px; margin-bottom: 24px;">
        This code will expire in <strong>10 minutes</strong>. If you did not request this code, please ignore this email.
      </p>
      <hr style="border: none; border-top: 1px solid #edf2f7; margin: 20px 0;" />
      <p style="color: #a0aec0; font-size: 11px; margin: 0;">Synced N Church Ministry Management &bull; Automated Security Service</p>
    </div>
  `.trim();

  const payload = {
    sender: {
      name: "Synced N Ministry",
      email: "ryanlloydgenturo@gmail.com"
    },
    to: [{ email: trimmedEmail }],
    subject: subjectLine,
    htmlContent: htmlBody
  };

  const response = await fetch("https://api.brevo.com/v3/smtp/email", {
    method: "POST",
    headers: {
      "api-key": getDirectBrevoApiKey(),
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Email delivery failed (${response.status}): ${errorText}`);
  }

  const expiresAt = Date.now() + 10 * 60 * 1000;
  clientOtpStore.set(trimmedEmail.toLowerCase(), {
    code,
    expiresAt,
    purpose: isRegister ? "register" : "reset",
    failedAttempts: 0
  });

  return {
    ok: true,
    success: true,
    email: trimmedEmail,
    purpose: isRegister ? "register" : "reset",
    expiresAt,
    expiresInSeconds: 600,
    cooldownSeconds: 60
  };
}

export function verifyDirectBrevoOtp({ email, enteredCode, purpose }) {
  const key = String(email || "").trim().toLowerCase();
  const rec = clientOtpStore.get(key);
  if (!rec) {
    return { ok: false, error: "No verification code found. Please request a new code." };
  }
  if (Date.now() > rec.expiresAt) {
    clientOtpStore.delete(key);
    return { ok: false, error: "Verification code has expired. Please request a new one." };
  }
  if (purpose && String(purpose).toLowerCase() !== rec.purpose) {
    return { ok: false, error: "Verification code purpose mismatch." };
  }
  const cleanCode = String(enteredCode || "").trim();
  if (cleanCode !== rec.code) {
    rec.failedAttempts = (rec.failedAttempts || 0) + 1;
    if (rec.failedAttempts >= 5) {
      clientOtpStore.delete(key);
      return { ok: false, error: "Too many incorrect attempts. Please request a new code." };
    }
    const rem = 5 - rec.failedAttempts;
    return { ok: false, error: `Invalid verification code (${rem} attempt(s) remaining).` };
  }
  // Single use: consume code
  clientOtpStore.delete(key);
  return { ok: true, verified: true };
}

const DISPOSABLE_DOMAINS = new Set([
  "tempmail.com", "temp-mail.org", "mailinator.com", "10minutemail.com",
  "guerrillamail.com", "yopmail.com", "trashmail.com", "fakeinbox.com",
  "sharklasers.com", "getnada.com", "throwawaymail.com", "maildrop.cc",
  "dispostable.com", "fake.com", "test.invalid", "invalid.com",
  "mailnesia.com", "mohmal.com", "mytemp.email"
]);

export function escapeHtml(str) {
  if (str === null || str === undefined) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function generateTeamCode() {
  let code = "";
  const rand = new Uint32Array(6);
  crypto.getRandomValues(rand);
  for (let i = 0; i < 6; i++) {
    code += TEAM_CODE_CHARS[rand[i] % TEAM_CODE_CHARS.length];
  }
  return code;
}

export function evaluatePassword(password = "") {
  const pwd = String(password);
  const hasMinLength = pwd.length >= 8;
  const withinMaxLength = pwd.length <= 128;
  const hasUpper = /[A-Z]/.test(pwd);
  const hasLower = /[a-z]/.test(pwd);
  const hasDigit = /\d/.test(pwd);
  const hasSpecial = /[^A-Za-z0-9\s]/.test(pwd);
  const score = [hasMinLength, hasUpper, hasLower, hasDigit, hasSpecial].filter(Boolean).length;
  const isStrong = hasMinLength && hasUpper && hasLower && hasDigit && hasSpecial && withinMaxLength;
  const label = isStrong ? "Strong" : score >= 4 ? "Good" : score >= 2 ? "Fair" : "Weak";

  let errorMessage = null;
  if (!isStrong) {
    if (!withinMaxLength) {
      errorMessage = "Password cannot exceed 128 characters.";
    } else {
      const missing = [];
      if (!hasMinLength) missing.push("at least 8 characters");
      if (!hasUpper) missing.push("an uppercase letter (A-Z)");
      if (!hasLower) missing.push("a lowercase letter (a-z)");
      if (!hasDigit) missing.push("a number (0-9)");
      if (!hasSpecial) missing.push("a special character (!@#$%^&*)");
      errorMessage = `Password must include ${missing.join(", ")}.`;
    }
  }

  return { hasMinLength, hasUpper, hasLower, hasDigit, hasSpecial, withinMaxLength, score, isStrong, label, errorMessage };
}

export function validateEmailForAuth(email = "") {
  const clean = String(email).trim();
  if (!clean) return "Enter your email address.";
  if (clean.length > 60) return "Email address cannot exceed 60 characters.";
  if (clean.includes("..")) return "Enter a valid email address.";
  const parts = clean.split("@");
  if (parts.length !== 2) return "Enter a valid email address.";
  const [localPart, domainPart] = parts;
  if (!localPart || localPart.startsWith(".") || localPart.endsWith(".")) return "Enter a valid email address.";
  if (!domainPart || domainPart.startsWith(".") || domainPart.endsWith(".") || !domainPart.includes(".")) return "Enter a valid email address.";
  if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,10}$/.test(clean)) return "Enter a valid email address.";
  const domainLower = domainPart.toLowerCase();
  if (
    DISPOSABLE_DOMAINS.has(domainLower) ||
    domainLower.endsWith(".invalid") ||
    domainLower.endsWith(".test") ||
    domainLower.startsWith(".") ||
    domainLower.endsWith(".")
  ) {
    return "Disposable or temporary email addresses are not allowed. Please use an active email.";
  }
  return null;
}

// Validation & Brute-Force Rate Limiting matching ValidationUtils.kt
export const MAX_NAME_LENGTH = 50;
export const MAX_USERNAME_LENGTH = 30;
export const MAX_PHONE_LENGTH = 13;
export const MAX_EMAIL_LENGTH = 60;
export const MIN_PASSWORD_LENGTH = 8;
export const MAX_PASSWORD_LENGTH = 128;

export const MAX_LOGIN_ATTEMPTS = 5;
export const LOGIN_LOCKOUT_DURATION_MS = 60000;

const PH_PHONE_REGEX = /^(\+639\d{9}|639\d{9}|09\d{9})$/;
const loginAttempts = new Map();

export function isValidName(name = "") {
  const clean = String(name).trim();
  return clean.length > 0 && clean.length <= MAX_NAME_LENGTH;
}

export function isValidUsername(username = "") {
  const clean = String(username).trim();
  return clean.length > 0 && clean.length <= MAX_USERNAME_LENGTH;
}

export function isValidPhilippinePhone(phone = "", allowBlank = true) {
  const clean = String(phone).trim();
  if (!clean) return allowBlank;
  return PH_PHONE_REGEX.test(clean);
}

export function isLoginLocked(email = "", nowMs = Date.now()) {
  const key = String(email).trim().toLowerCase();
  const entry = loginAttempts.get(key);
  if (!entry) return false;
  if (entry.lockedUntilMs > nowMs) return true;
  if (entry.lockedUntilMs > 0 && nowMs >= entry.lockedUntilMs) {
    loginAttempts.delete(key);
  }
  return false;
}

export function getLoginLockRemainingSeconds(email = "", nowMs = Date.now()) {
  const key = String(email).trim().toLowerCase();
  const entry = loginAttempts.get(key);
  if (!entry) return 0;
  const remainingMs = entry.lockedUntilMs - nowMs;
  if (remainingMs <= 0) return 0;
  return Math.floor((remainingMs + 999) / 1000);
}

export function recordLoginFailure(email = "", nowMs = Date.now()) {
  const key = String(email).trim().toLowerCase();
  const current = loginAttempts.get(key);
  const baseCount =
    current && current.lockedUntilMs > 0 && nowMs >= current.lockedUntilMs
      ? 0
      : current?.failedCount || 0;
  const nextCount = baseCount + 1;
  if (nextCount >= MAX_LOGIN_ATTEMPTS) {
    loginAttempts.set(key, {
      failedCount: nextCount,
      lockedUntilMs: nowMs + LOGIN_LOCKOUT_DURATION_MS
    });
    return 0;
  }
  loginAttempts.set(key, {
    failedCount: nextCount,
    lockedUntilMs: 0
  });
  return MAX_LOGIN_ATTEMPTS - nextCount;
}

export function resetLoginAttempts(email = "") {
  const key = String(email).trim().toLowerCase();
  if (key) {
    loginAttempts.delete(key);
  }
}

// Session HMAC-SHA256 Integrity matching ValidationUtils.kt (SESSION_HMAC_SECRET)
const SESSION_HMAC_SECRET_BYTES = new Uint8Array([
  0x53, 0x79, 0x6e, 0x63, 0x65, 0x64, 0x4e, 0x5f,
  0x48, 0x6d, 0x61, 0x63, 0x5f, 0x32, 0x30, 0x32,
  0x36, 0x5f, 0x4d, 0x69, 0x6e, 0x69, 0x73, 0x74,
  0x72, 0x79, 0x5f, 0x53, 0x65, 0x63, 0x75, 0x72
]);

export async function computeSessionHmac(email = "", role = "", uid = "") {
  try {
    const enc = new TextEncoder();
    const key = await crypto.subtle.importKey(
      "raw",
      SESSION_HMAC_SECRET_BYTES,
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"]
    );
    const payload = `${String(email).trim().toLowerCase()}|${String(role).trim()}|${String(uid).trim()}`;
    const signature = await crypto.subtle.sign("HMAC", key, enc.encode(payload));
    return bytesToBase64(new Uint8Array(signature));
  } catch {
    return "";
  }
}

export async function verifySessionHmac(email = "", role = "", uid = "", expectedHmac = "") {
  if (!expectedHmac) return false;
  const computed = await computeSessionHmac(email, role, uid);
  if (!computed || computed.length !== expectedHmac.length) return false;
  let diff = 0;
  for (let i = 0; i < computed.length; i++) {
    diff |= computed.charCodeAt(i) ^ expectedHmac.charCodeAt(i);
  }
  return diff === 0;
}

// WebCrypto PBKDF2-HMAC-SHA256 matching ValidationUtils.kt (65536 iterations, 256 bits)
const PBKDF2_PREFIX = "pbkdf2$";
const PBKDF2_ITERATIONS = 65536;
const PBKDF2_KEY_BITS = 256;

function bytesToBase64(bytes) {
  let binary = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function base64ToBytes(base64) {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export async function hashPasswordPbkdf2(password) {
  if (password.startsWith(PBKDF2_PREFIX)) return password;
  const salt = new Uint8Array(16);
  crypto.getRandomValues(salt);
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey("raw", enc.encode(password), { name: "PBKDF2" }, false, ["deriveBits"]);
  const derivedBits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations: PBKDF2_ITERATIONS, hash: "SHA-256" },
    keyMaterial,
    PBKDF2_KEY_BITS
  );
  const hashBytes = new Uint8Array(derivedBits);
  return `${PBKDF2_PREFIX}${bytesToBase64(salt)}$${bytesToBase64(hashBytes)}`;
}

export async function verifyPasswordPbkdf2(candidatePassword, storedPasswordOrHash) {
  if (!candidatePassword || !storedPasswordOrHash) return false;
  if (!storedPasswordOrHash.startsWith(PBKDF2_PREFIX)) {
    return candidatePassword === storedPasswordOrHash;
  }
  try {
    const parts = storedPasswordOrHash.split("$");
    if (parts.length !== 3) return false;
    const salt = base64ToBytes(parts[1]);
    const expectedHash = base64ToBytes(parts[2]);
    const enc = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey("raw", enc.encode(candidatePassword), { name: "PBKDF2" }, false, ["deriveBits"]);
    const derivedBits = await crypto.subtle.deriveBits(
      { name: "PBKDF2", salt, iterations: PBKDF2_ITERATIONS, hash: "SHA-256" },
      keyMaterial,
      PBKDF2_KEY_BITS
    );
    const actualHash = new Uint8Array(derivedBits);
    if (actualHash.length !== expectedHash.length) return false;
    let diff = 0;
    for (let i = 0; i < actualHash.length; i++) {
      diff |= actualHash[i] ^ expectedHash[i];
    }
    return diff === 0;
  } catch {
    return false;
  }
}

// Role helpers matching TeamRepository.kt
export function isSubstituteLeader(ministryRole = "") {
  return String(ministryRole).trim().toLowerCase() === "substitute leader";
}

export function isWorshipLeader(ministryRole = "", role = "") {
  if (isSubstituteLeader(ministryRole)) return false;
  const cleanMinistry = String(ministryRole).trim().toLowerCase();
  if (cleanMinistry === "worship leader") return true;
  const cleanRole = String(role).trim().toLowerCase();
  return cleanRole === "leader" && (!cleanMinistry || cleanMinistry === "member" || cleanMinistry === "leader");
}

export function hasLeaderPrivileges(userProfile) {
  if (!userProfile) return false;
  return (
    isWorshipLeader(userProfile.ministryRole, userProfile.role) ||
    isSubstituteLeader(userProfile.ministryRole) ||
    String(userProfile.role || "").trim().toLowerCase() === "leader"
  );
}

// Client-side image compression + unsigned Cloudinary upload matching CloudinaryUploader.kt
export async function uploadImageToCloudinary(file, maxDimension = 900, quality = 0.85) {
  if (!file) throw new Error("No image selected.");
  const bitmap = await createImageBitmap(file);
  let { width, height } = bitmap;
  if (width > maxDimension || height > maxDimension) {
    if (width >= height) {
      height = Math.round((height * maxDimension) / width);
      width = maxDimension;
    } else {
      width = Math.round((width * maxDimension) / height);
      height = maxDimension;
    }
  }
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  ctx.drawImage(bitmap, 0, 0, width, height);

  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("Image compression failed"))), "image/jpeg", quality);
  });

  const formData = new FormData();
  formData.append("file", blob, "upload.jpg");
  formData.append("upload_preset", CLOUDINARY_CONFIG.uploadPreset);

  const response = await fetch(CLOUDINARY_CONFIG.uploadUrl, {
    method: "POST",
    body: formData
  });

  if (!response.ok) {
    const errText = await response.text();
    throw new Error(`Cloudinary upload failed (${response.status}): ${errText}`);
  }
  const data = await response.json();
  if (!data.secure_url) throw new Error("Cloudinary did not return a secure_url.");
  return data.secure_url;
}

// YouTube & Song Cover helpers matching SongData.kt and MemberActivity.kt
export function extractYoutubeVideoId(url) {
  if (!url) return null;
  const clean = String(url).trim();
  const match = clean.match(/(?:https?:\/\/)?(?:www\.|m\.)?(?:youtube\.com\/(?:watch\?(?:.*&)?v=|embed\/|v\/|shorts\/)|youtu\.be\/)([\w-]{11})/i);
  if (match && match[1]) return match[1];
  const fallback = clean.match(/[?&]v=([\w-]{11})/);
  return fallback ? fallback[1] : null;
}

export function getYoutubeThumbnailUrl(url) {
  const id = extractYoutubeVideoId(url);
  return id ? `https://img.youtube.com/vi/${id}/hqdefault.jpg` : null;
}

export function resolveSongCoverUrl(song) {
  if (!song) return null;
  if (song.coverImageUrl && String(song.coverImageUrl).trim()) {
    return String(song.coverImageUrl).trim();
  }
  if (song.imageUrl && String(song.imageUrl).trim()) {
    return String(song.imageUrl).trim();
  }
  const ytThumb = getYoutubeThumbnailUrl(song.referenceUrl);
  if (ytThumb) return ytThumb;

  // Fallback: match against known worship seed catalog by title (matching MemberActivity.kt songCover)
  const cleanTitle = String(song.title || "").trim().toLowerCase();
  if (cleanTitle) {
    const seedMatch = DEFAULT_SEED_SONGS.find((s) => s.title.toLowerCase() === cleanTitle);
    if (seedMatch && seedMatch.referenceUrl) {
      const seedThumb = getYoutubeThumbnailUrl(seedMatch.referenceUrl);
      if (seedThumb) return seedThumb;
    }
  }
  return null;
}

// ============================================================================
// Musical Chord Transposition Engine (100% aligned with ChordSheet.java)
// ============================================================================
const CHROMATIC_SHARP = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
const CHROMATIC_FLAT = ["C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"];
const NOTE_INDEX = {
  C: 0, "B#": 0,
  "C#": 1, Db: 1,
  D: 2,
  "D#": 3, Eb: 3,
  E: 4, Fb: 4,
  F: 5, "E#": 5,
  "F#": 6, Gb: 6,
  G: 7,
  "G#": 8, Ab: 8,
  A: 9,
  "A#": 10, Bb: 10,
  B: 11, Cb: 11
};

export function normalizeNoteAccidental(note = "") {
  return String(note).replace(/♯/g, "#").replace(/♭/g, "b");
}

export function getNotePitch(note = "C") {
  const clean = normalizeNoteAccidental(String(note || "C").trim());
  const m = clean.match(/^([A-Ga-g])([#b]?)/);
  if (!m) return 0;
  const root = m[1].toUpperCase() + (m[2] || "");
  return NOTE_INDEX[root] ?? 0;
}

export function isMinorKey(key = "C") {
  const clean = String(key || "C").trim();
  return (/m$/i.test(clean) && !/maj$/i.test(clean)) || /minor$/i.test(clean);
}

export function shouldUseFlatsForKey(key = "C") {
  const clean = normalizeNoteAccidental(String(key || "C").trim());
  if (clean.includes("b")) return true;
  if (clean.includes("#")) return false;
  const upper = clean.toUpperCase();
  return ["F", "DM", "GM", "CM", "FM", "BB", "EB", "AB", "DB", "GB", "BBM", "EBM"].includes(upper);
}

export function getSemitoneDiff(fromKey = "C", toKey = "C") {
  const i1 = getNotePitch(fromKey);
  const i2 = getNotePitch(toKey);
  return ((i2 - i1) % 12 + 12) % 12;
}

export function transposeSingleNote(note, semitones, useFlats = null) {
  if (!note) return note;
  const clean = normalizeNoteAccidental(String(note).trim());
  const match = clean.match(/^([A-Ga-g][#b]?)(.*)$/);
  if (!match) return note;
  const rootPart = match[1][0].toUpperCase() + (match[1].slice(1) || "");
  const suffix = match[2] || "";
  const idx = NOTE_INDEX[rootPart];
  if (idx === undefined) return note;
  const nextIdx = ((idx + semitones) % 12 + 12) % 12;
  const preferFlats = useFlats !== null ? useFlats : (CHROMATIC_ROOTS[nextIdx].includes("b") || CHROMATIC_ROOTS[nextIdx] === "F");
  const nextRoot = (preferFlats ? CHROMATIC_FLAT : CHROMATIC_SHARP)[nextIdx];
  return `${nextRoot}${suffix}`;
}

export function getTargetKeyLabel(originalKey = "C", semitones = 0, useFlatsOverride = null) {
  const minor = isMinorKey(originalKey);
  const basePitch = getNotePitch(originalKey);
  const targetPitch = ((basePitch + semitones) % 12 + 12) % 12;
  let rootName;
  if (useFlatsOverride === true) {
    rootName = CHROMATIC_FLAT[targetPitch];
  } else if (useFlatsOverride === false) {
    rootName = CHROMATIC_SHARP[targetPitch];
  } else {
    rootName = CHROMATIC_ROOTS[targetPitch];
  }
  return `${rootName}${minor ? "m" : ""}`;
}

const CHORD_TOKEN_REGEX = /^([A-G][#b♯♭]?)((?:(?:maj|min|dim|aug|sus|add|omit|no|alt|m|M)|[0-9#b♯♭(),°ø+\-])*)(?:\/([A-G][#b♯♭]?))?$/;

export function isChordToken(tok) {
  if (!tok) return false;
  const clean = String(tok).trim();
  if (clean === "N.C.") return true;
  return CHORD_TOKEN_REGEX.test(clean);
}

export function transposeChordToken(chord, semitones, useFlats = false) {
  if (!chord) return chord;
  const clean = normalizeNoteAccidental(String(chord).trim());
  if (clean === "N.C.") return clean;
  const m = clean.match(/^([A-G][#b]?)((?:(?:maj|min|dim|aug|sus|add|omit|no|alt|m|M)|[0-9#b(),°ø+\-])*)(?:\/([A-G][#b]?))?$/);
  if (!m) return chord;
  const [, root, quality, bass] = m;
  const scale = useFlats ? CHROMATIC_FLAT : CHROMATIC_SHARP;
  const rootPitch = ((getNotePitch(root) + semitones) % 12 + 12) % 12;
  const newRoot = scale[rootPitch];
  let newBass = "";
  if (bass) {
    const bassPitch = ((getNotePitch(bass) + semitones) % 12 + 12) % 12;
    newBass = "/" + scale[bassPitch];
  }
  return `${newRoot}${quality || ""}${newBass}`;
}

function isLikelyChordLine(line) {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith("[") || trimmed.startsWith("{")) return false;
  const tokens = trimmed.split(/\s+/).filter((t) => !/^[|:%\-/]+$/.test(t));
  if (!tokens.length) return false;
  const chordCount = tokens.filter(isChordToken).length;
  return chordCount > 0 && chordCount / tokens.length >= 0.65;
}

export function renderFormattedChordSheet(rawSheet = "", rawLyrics = "", semitones = 0, lyricsOnly = false, useFlats = false) {
  const source = (rawSheet && rawSheet.trim()) ? rawSheet : (rawLyrics || "");
  if (!source.trim()) {
    return `<div class="text-slate-400 italic py-8 text-center">No chord sheet or lyrics added for this song yet.</div>`;
  }

  const lines = source.replace(/\r\n/g, "\n").split("\n");
  const out = [];

  for (const line of lines) {
    const trimmed = line.trim();

    // Section header e.g. [Verse 1], [Chorus], [Bridge], Verse 1:
    if (/^\[(?:Verse|Chorus|Pre-Chorus|Prechorus|Bridge|Intro|Outro|Instrumental|Interlude|Tag|Ending|Refrain|Hook|Solo|Vamp|Coda)[^\]]*\]$/i.test(trimmed) ||
        /^(?:Verse|Chorus|Pre-Chorus|Prechorus|Bridge|Intro|Outro|Instrumental|Interlude|Tag|Refrain|Vamp|Coda)(?:\s+\d+)?\s*:?$/i.test(trimmed)) {
      const label = trimmed.replace(/^\[|\]$|:$/g, "");
      out.push(`<div class="section-header-wrapper"><span class="section-header-line">${escapeHtml(label)}</span></div>`);
      continue;
    }

    // Inline [Chord] notation (ChordPro style)
    if (/\[([^\]]+)\]/.test(line)) {
      if (lyricsOnly) {
        const stripped = line.replace(/\[([^\]]+)\]/g, (_, token) => {
          return isChordToken(token) ? "" : `[${token}]`;
        }).replace(/\s{2,}/g, " ").trim();
        if (stripped) out.push(`<div class="lyric-line">${escapeHtml(stripped)}</div>`);
      } else {
        // Split line around [Chord] brackets safely
        const parts = line.split(/(\[[^\]]+\])/g);
        const htmlParts = parts.map((part) => {
          if (part.startsWith("[") && part.endsWith("]")) {
            const inner = part.slice(1, -1).trim();
            if (isChordToken(inner)) {
              const transposed = transposeChordToken(inner, semitones, useFlats);
              return `<span class="chord-token">${escapeHtml(transposed)}</span>`;
            }
          }
          return escapeHtml(part);
        });
        out.push(`<div class="lyric-chord-line">${htmlParts.join("")}</div>`);
      }
      continue;
    }

    // Chord-over-lyric line
    if (isLikelyChordLine(line)) {
      if (lyricsOnly) continue;
      const transposedLine = line.replace(/\S+/g, (tok) => {
        return isChordToken(tok) ? transposeChordToken(tok, semitones, useFlats) : tok;
      });
      out.push(`<div class="chord-line">${escapeHtml(transposedLine)}</div>`);
      continue;
    }

    out.push(`<div class="lyric-line">${escapeHtml(line) || "&nbsp;"}</div>`);
  }

  return out.join("");
}

// ============================================================================
// High-Precision Decimal BPM Metronome Engine
// Formula: Beat Interval (milliseconds) = 60,000 / BPM
// ============================================================================

export function formatBpmDisplay(bpm) {
  const num = Number(bpm);
  if (!Number.isFinite(num)) return "74";
  // Preserve exact entered decimals up to 2-3 places without floating point noise
  const rounded = Math.round(num * 1000) / 1000;
  return String(rounded);
}

export function validateBpmValue(rawInput) {
  const str = String(rawInput ?? "").trim();
  if (!str) {
    return { valid: false, bpm: 74, value: 74, display: "", intervalMs: 60000 / 74, error: "Enter a BPM value between 30 and 240." };
  }
  // Must be a valid positive number (integer or decimal with up to 3 decimal digits)
  if (!/^\d+(?:\.\d{1,3})?$/.test(str)) {
    return { valid: false, bpm: 74, value: 74, display: str, intervalMs: 60000 / 74, error: "Enter a valid number (e.g. 72, 72.5, 100.25)." };
  }
  const parsed = parseFloat(str);
  if (!Number.isFinite(parsed) || parsed < 30 || parsed > 240) {
    return { valid: false, bpm: parsed || 74, value: parsed || 74, display: str, intervalMs: 60000 / 74, error: "BPM must be between 30 and 240." };
  }
  const intervalMs = 60000 / parsed;
  return {
    valid: true,
    bpm: parsed,
    value: parsed,
    display: formatBpmDisplay(parsed),
    intervalMs,
    error: null
  };
}

export class MetronomeEngine {
  constructor(onBeatCallback) {
    this.audioCtx = null;
    this.isPlaying = false;
    this.bpm = 74;
    this.meter = 4; // Beats per bar (1 to 12, matching MetronomeView.kt)
    this.beatIndex = 0;
    this.timerId = null;
    this.nextBeatTimeMs = 0;
    this.tapTimes = [];
    this.onBeatCallback = onBeatCallback;
  }

  getBeatIntervalMs() {
    return 60000 / this.bpm;
  }

  setBpm(bpmInput) {
    const check = validateBpmValue(bpmInput);
    if (check.valid) {
      this.bpm = check.bpm;
    } else {
      const fallback = parseFloat(bpmInput);
      if (Number.isFinite(fallback)) {
        this.bpm = Math.min(240, Math.max(30, Math.round(fallback * 1000) / 1000));
      } else {
        this.bpm = 74;
      }
    }
    if (this.isPlaying) {
      // Seamless tempo update without losing play state
      if (this.timerId) {
        clearTimeout(this.timerId);
        this.timerId = null;
      }
      this.nextBeatTimeMs = performance.now() + this.getBeatIntervalMs();
      this.scheduleNextTick();
    }
    return this.bpm;
  }

  setMeter(meterInput) {
    const parsed = parseInt(meterInput, 10);
    this.meter = Math.min(12, Math.max(1, isNaN(parsed) ? 4 : parsed));
    this.beatIndex = 0;
    return this.meter;
  }

  tapTempo() {
    const now = performance.now();
    if (this.tapTimes.length > 0 && now - this.tapTimes[this.tapTimes.length - 1] > 2200) {
      this.tapTimes = [];
    }
    this.tapTimes.push(now);
    while (this.tapTimes.length > 6) {
      this.tapTimes.shift();
    }
    if (this.tapTimes.length >= 2) {
      const avgIntervalMs = (now - this.tapTimes[0]) / (this.tapTimes.length - 1);
      if (avgIntervalMs > 0) {
        // Round tap tempo to nearest 0.5 BPM for clean usability
        const rawBpm = 60000 / avgIntervalMs;
        const clamped = Math.min(240, Math.max(30, Math.round(rawBpm * 2) / 2));
        this.setBpm(clamped);
        return clamped;
      }
    }
    return null;
  }

  playClick(isAccent) {
    try {
      if (!this.audioCtx) {
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        this.audioCtx = new AudioContextClass();
      }
      if (this.audioCtx.state === "suspended") {
        this.audioCtx.resume();
      }
      const osc = this.audioCtx.createOscillator();
      const gain = this.audioCtx.createGain();
      osc.type = "triangle";
      osc.frequency.setValueAtTime(isAccent ? 1046.5 : 784.0, this.audioCtx.currentTime);
      gain.gain.setValueAtTime(0.38, this.audioCtx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, this.audioCtx.currentTime + 0.065);
      osc.connect(gain);
      gain.connect(this.audioCtx.destination);
      osc.start();
      osc.stop(this.audioCtx.currentTime + 0.07);
    } catch {
      // Ignore audio context errors if blocked
    }
  }

  scheduleNextTick() {
    if (!this.isPlaying) return;
    const now = performance.now();
    const delay = Math.max(0, this.nextBeatTimeMs - now);

    this.timerId = setTimeout(() => {
      if (!this.isPlaying) return;
      const beatInBar = (this.beatIndex % this.meter) + 1;
      const isAccent = beatInBar === 1;
      this.playClick(isAccent);
      if (this.onBeatCallback) {
        this.onBeatCallback(beatInBar, isAccent, this.bpm, this.meter);
      }
      this.beatIndex++;
      const intervalMs = this.getBeatIntervalMs(); // Exact 60000 / this.bpm
      this.nextBeatTimeMs += intervalMs;
      // Guard against background tab lag
      const currentNow = performance.now();
      if (this.nextBeatTimeMs < currentNow - intervalMs) {
        this.nextBeatTimeMs = currentNow + intervalMs;
      }
      this.scheduleNextTick();
    }, delay);
  }

  start() {
    if (this.isPlaying) return;
    this.isPlaying = true;
    this.beatIndex = 0;

    // Play Beat 1 immediately
    const isAccent = true;
    this.playClick(isAccent);
    if (this.onBeatCallback) {
      this.onBeatCallback(1, isAccent, this.bpm, this.meter);
    }
    this.beatIndex = 1;
    this.nextBeatTimeMs = performance.now() + this.getBeatIntervalMs();
    this.scheduleNextTick();
  }

  stop() {
    this.isPlaying = false;
    if (this.timerId) {
      clearTimeout(this.timerId);
      this.timerId = null;
    }
    if (this.onBeatCallback) {
      this.onBeatCallback(0, false, this.bpm, this.meter);
    }
  }

  toggle() {
    if (this.isPlaying) this.stop();
    else this.start();
    return this.isPlaying;
  }
}

export function formatFriendlyDate(dateStr) {
  if (!dateStr) return "TBD";
  const m = String(dateStr).trim().match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) return dateStr;
  const dt = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return dt.toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric", year: "numeric" });
}

export function formatFriendlyTime(timeStr) {
  if (!timeStr) return "";
  const m = String(timeStr).trim().match(/^(\d{1,2}):(\d{2})$/);
  if (!m) return timeStr;
  let hr = parseInt(m[1], 10);
  const min = m[2];
  const ampm = hr >= 12 ? "PM" : "AM";
  hr = hr % 12 || 12;
  return `${hr}:${min} ${ampm}`;
}

export function formatRelativeTime(timestampMs) {
  if (!timestampMs) return "Just now";
  const diffSec = Math.floor((Date.now() - Number(timestampMs)) / 1000);
  if (diffSec < 45) return "Just now";
  if (diffSec < 3600) return `${Math.floor(diffSec / 60)}m ago`;
  if (diffSec < 86400) return `${Math.floor(diffSec / 3600)}h ago`;
  const dt = new Date(Number(timestampMs));
  return dt.toLocaleDateString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

// ============================================================================
// Feature 2: Dynamic Guitar Chord Dictionary & Visualizer
// Matches Worship Standard & User Reference (CHORDS, nut, frets, fingers)
// ============================================================================

export const GUITAR_CHORD_DATA = {
  // --- Majors ---
  "C": { frets: [-1, 3, 2, 0, 1, 0], fingers: [0, 3, 2, 0, 1, 0] },
  "C#": { frets: [-1, 4, 3, 1, 2, 1], fingers: [0, 4, 3, 1, 2, 1], baseFret: 1 },
  "Db": { frets: [-1, 4, 3, 1, 2, 1], fingers: [0, 4, 3, 1, 2, 1], baseFret: 1 },
  "D": { frets: [-1, -1, 0, 2, 3, 2], fingers: [0, 0, 0, 1, 3, 2] },
  "D#": { frets: [-1, -1, 1, 3, 4, 3], fingers: [0, 0, 1, 2, 4, 3], baseFret: 1 },
  "Eb": { frets: [-1, -1, 1, 3, 4, 3], fingers: [0, 0, 1, 2, 4, 3], baseFret: 1 },
  "E": { frets: [0, 2, 2, 1, 0, 0], fingers: [0, 2, 3, 1, 0, 0] },
  "F": { frets: [1, 3, 3, 2, 1, 1], fingers: [1, 3, 4, 2, 1, 1], baseFret: 1 },
  "F#": { frets: [2, 4, 4, 3, 2, 2], fingers: [1, 3, 4, 2, 1, 1], baseFret: 2 },
  "Gb": { frets: [2, 4, 4, 3, 2, 2], fingers: [1, 3, 4, 2, 1, 1], baseFret: 2 },
  "G": { frets: [3, 2, 0, 0, 0, 3], fingers: [2, 1, 0, 0, 0, 3] },
  "G#": { frets: [4, 6, 6, 5, 4, 4], fingers: [1, 3, 4, 2, 1, 1], baseFret: 4 },
  "Ab": { frets: [4, 6, 6, 5, 4, 4], fingers: [1, 3, 4, 2, 1, 1], baseFret: 4 },
  "A": { frets: [-1, 0, 2, 2, 2, 0], fingers: [0, 0, 1, 2, 3, 0] },
  "A#": { frets: [-1, 1, 3, 3, 3, 1], fingers: [0, 1, 2, 3, 4, 1], baseFret: 1 },
  "Bb": { frets: [-1, 1, 3, 3, 3, 1], fingers: [0, 1, 2, 3, 4, 1], baseFret: 1 },
  "B": { frets: [-1, 2, 4, 4, 4, 2], fingers: [0, 1, 2, 3, 4, 1], baseFret: 2 },

  // --- Minors ---
  "Cm": { frets: [-1, 3, 5, 5, 4, 3], fingers: [0, 1, 3, 4, 2, 1], baseFret: 3 },
  "C#m": { frets: [-1, 4, 6, 6, 5, 4], fingers: [0, 1, 3, 4, 2, 1], baseFret: 4 },
  "Dbm": { frets: [-1, 4, 6, 6, 5, 4], fingers: [0, 1, 3, 4, 2, 1], baseFret: 4 },
  "Dm": { frets: [-1, -1, 0, 2, 3, 1], fingers: [0, 0, 0, 2, 3, 1] },
  "D#m": { frets: [-1, -1, 1, 3, 4, 2], fingers: [0, 0, 1, 3, 4, 2], baseFret: 1 },
  "Ebm": { frets: [-1, -1, 1, 3, 4, 2], fingers: [0, 0, 1, 3, 4, 2], baseFret: 1 },
  "Em": { frets: [0, 2, 2, 0, 0, 0], fingers: [0, 2, 3, 0, 0, 0] },
  "Fm": { frets: [1, 3, 3, 1, 1, 1], fingers: [1, 3, 4, 1, 1, 1], baseFret: 1 },
  "F#m": { frets: [2, 4, 4, 2, 2, 2], fingers: [1, 3, 4, 1, 1, 1], baseFret: 2 },
  "Gbm": { frets: [2, 4, 4, 2, 2, 2], fingers: [1, 3, 4, 1, 1, 1], baseFret: 2 },
  "Gm": { frets: [3, 5, 5, 3, 3, 3], fingers: [1, 3, 4, 1, 1, 1], baseFret: 3 },
  "G#m": { frets: [4, 6, 6, 4, 4, 4], fingers: [1, 3, 4, 1, 1, 1], baseFret: 4 },
  "Abm": { frets: [4, 6, 6, 4, 4, 4], fingers: [1, 3, 4, 1, 1, 1], baseFret: 4 },
  "Am": { frets: [-1, 0, 2, 2, 1, 0], fingers: [0, 0, 2, 3, 1, 0] },
  "A#m": { frets: [-1, 1, 3, 3, 2, 1], fingers: [0, 1, 3, 4, 2, 1], baseFret: 1 },
  "Bbm": { frets: [-1, 1, 3, 3, 2, 1], fingers: [0, 1, 3, 4, 2, 1], baseFret: 1 },
  "Bm": { frets: [-1, 2, 4, 4, 3, 2], fingers: [0, 1, 3, 4, 2, 1], baseFret: 2 },

  // --- Add9 / 2 / Sus (Worship Favorites) ---
  "C2": { frets: [-1, 3, 0, 0, 1, 0], fingers: [0, 3, 0, 0, 1, 0] },
  "Cadd9": { frets: [-1, 3, 2, 0, 3, 0], fingers: [0, 2, 1, 0, 3, 0] },
  "Csus2": { frets: [-1, 3, 0, 0, 1, 3], fingers: [0, 2, 0, 0, 1, 4] },
  "Csus4": { frets: [-1, 3, 3, 0, 1, 1], fingers: [0, 2, 3, 0, 1, 1] },
  "D2": { frets: [-1, -1, 0, 2, 3, 0], fingers: [0, 0, 0, 1, 2, 0] },
  "Dsus2": { frets: [-1, -1, 0, 2, 3, 0], fingers: [0, 0, 0, 1, 2, 0] },
  "Dadd9": { frets: [-1, -1, 0, 2, 3, 0], fingers: [0, 0, 0, 1, 2, 0] },
  "Dsus4": { frets: [-1, -1, 0, 2, 3, 3], fingers: [0, 0, 0, 1, 2, 4] },
  "E2": { frets: [0, 2, 4, 1, 0, 0], fingers: [0, 1, 3, 2, 0, 0] },
  "Esus4": { frets: [0, 2, 2, 2, 0, 0], fingers: [0, 2, 3, 4, 0, 0] },
  "G2": { frets: [3, 0, 0, 0, 0, 3], fingers: [1, 0, 0, 0, 0, 2] },
  "Gadd9": { frets: [3, 2, 0, 2, 0, 3], fingers: [2, 1, 0, 3, 0, 4] },
  "Gsus4": { frets: [3, 2, 0, 0, 1, 3], fingers: [3, 2, 0, 0, 1, 4] },
  "A2": { frets: [-1, 0, 2, 2, 0, 0], fingers: [0, 0, 1, 2, 0, 0] },
  "Asus2": { frets: [-1, 0, 2, 2, 0, 0], fingers: [0, 0, 1, 2, 0, 0] },
  "Aadd9": { frets: [-1, 0, 2, 4, 2, 0], fingers: [0, 0, 1, 3, 2, 0] },
  "Asus4": { frets: [-1, 0, 2, 2, 3, 0], fingers: [0, 0, 1, 2, 3, 0] },
  "Bsus4": { frets: [-1, 2, 4, 4, 5, 2], fingers: [0, 1, 2, 3, 4, 1], baseFret: 2 },

  // --- Dominant 7ths ---
  "C7": { frets: [-1, 3, 2, 3, 1, 0], fingers: [0, 3, 2, 4, 1, 0] },
  "D7": { frets: [-1, -1, 0, 2, 1, 2], fingers: [0, 0, 0, 2, 1, 3] },
  "E7": { frets: [0, 2, 0, 1, 0, 0], fingers: [0, 2, 0, 1, 0, 0] },
  "F7": { frets: [1, 3, 1, 2, 1, 1], fingers: [1, 3, 1, 2, 1, 1], baseFret: 1 },
  "F#7": { frets: [2, 4, 2, 3, 2, 2], fingers: [1, 3, 1, 2, 1, 1], baseFret: 2 },
  "G7": { frets: [3, 2, 0, 0, 0, 1], fingers: [3, 2, 0, 0, 0, 1] },
  "A7": { frets: [-1, 0, 2, 0, 2, 0], fingers: [0, 0, 2, 0, 3, 0] },
  "B7": { frets: [-1, 2, 1, 2, 0, 2], fingers: [0, 2, 1, 3, 0, 4] },
  "Bb7": { frets: [-1, 1, 3, 1, 3, 1], fingers: [0, 1, 3, 1, 4, 1], baseFret: 1 },

  // --- Minor 7ths ---
  "Cm7": { frets: [-1, 3, 5, 3, 4, 3], fingers: [0, 1, 3, 1, 2, 1], baseFret: 3 },
  "C#m7": { frets: [-1, 4, 6, 4, 5, 4], fingers: [0, 1, 3, 1, 2, 1], baseFret: 4 },
  "Dm7": { frets: [-1, -1, 0, 2, 1, 1], fingers: [0, 0, 0, 2, 1, 1] },
  "Em7": { frets: [0, 2, 0, 0, 0, 0], fingers: [0, 1, 0, 0, 0, 0] },
  "Fm7": { frets: [1, 3, 1, 1, 1, 1], fingers: [1, 3, 1, 1, 1, 1], baseFret: 1 },
  "F#m7": { frets: [2, 0, 2, 2, 2, 0], fingers: [1, 0, 2, 3, 4, 0] },
  "Gm7": { frets: [3, 5, 3, 3, 3, 3], fingers: [1, 3, 1, 1, 1, 1], baseFret: 3 },
  "G#m7": { frets: [4, 6, 4, 4, 4, 4], fingers: [1, 3, 1, 1, 1, 1], baseFret: 4 },
  "Am7": { frets: [-1, 0, 2, 0, 1, 0], fingers: [0, 0, 2, 0, 1, 0] },
  "Bbm7": { frets: [-1, 1, 3, 1, 2, 1], fingers: [0, 1, 3, 1, 2, 1], baseFret: 1 },
  "Bm7": { frets: [-1, 2, 0, 2, 0, 2], fingers: [0, 1, 0, 2, 0, 3] },

  // --- Major 7ths ---
  "Cmaj7": { frets: [-1, 3, 2, 0, 0, 0], fingers: [0, 3, 2, 0, 0, 0] },
  "Dmaj7": { frets: [-1, -1, 0, 2, 2, 2], fingers: [0, 0, 0, 1, 2, 3] },
  "Ebmaj7": { frets: [-1, -1, 1, 3, 3, 3], fingers: [0, 0, 1, 2, 3, 4], baseFret: 1 },
  "Emaj7": { frets: [0, 2, 1, 1, 0, 0], fingers: [0, 2, 1, 1, 0, 0] },
  "Fmaj7": { frets: [-1, -1, 3, 2, 1, 0], fingers: [0, 0, 3, 2, 1, 0] },
  "Gmaj7": { frets: [3, 2, 0, 0, 0, 2], fingers: [2, 1, 0, 0, 0, 3] },
  "Amaj7": { frets: [-1, 0, 2, 1, 2, 0], fingers: [0, 0, 2, 1, 3, 0] },
  "Bbmaj7": { frets: [-1, 1, 3, 2, 3, 1], fingers: [0, 1, 3, 2, 4, 1], baseFret: 1 },
  "Bmaj7": { frets: [-1, 2, 4, 3, 4, 2], fingers: [0, 1, 3, 2, 4, 1], baseFret: 2 },

  // --- Diminished ---
  "Cdim": { frets: [-1, -1, 1, 2, 1, 2], fingers: [0, 0, 1, 3, 2, 4], baseFret: 1 },
  "Ddim": { frets: [-1, -1, 0, 1, 3, 1], fingers: [0, 0, 0, 1, 3, 2] },
  "Edim": { frets: [-1, -1, 2, 3, 2, 3], fingers: [0, 0, 1, 3, 2, 4], baseFret: 2 },
  "F#dim": { frets: [2, -1, 1, 2, 1, -1], fingers: [2, 0, 1, 3, 1, 0], baseFret: 1 },
  "Bdim": { frets: [-1, 2, 3, 4, 3, -1], fingers: [0, 1, 2, 4, 3, 0], baseFret: 2 },

  // --- Slash Voicings ---
  "D/F#": { frets: [2, 0, 0, 2, 3, 2], fingers: [1, 0, 0, 2, 4, 3] },
  "G/B": { frets: [-1, 2, 0, 0, 3, 3], fingers: [0, 1, 0, 0, 3, 4] },
  "C/E": { frets: [0, 3, 2, 0, 1, 0], fingers: [0, 3, 2, 0, 1, 0] },
  "F/A": { frets: [-1, 0, 3, 2, 1, 1], fingers: [0, 0, 3, 2, 1, 1] },
  "A/C#": { frets: [-1, 4, 2, 2, 2, -1], fingers: [0, 3, 1, 1, 1, 0], baseFret: 2 },
  "E/G#": { frets: [4, 2, 2, 4, 5, -1], fingers: [2, 1, 1, 3, 4, 0], baseFret: 2 }
};

/**
 * Resolves a chord name to its shape data using exact matching, enharmonic mapping,
 * slash chord root fallback, or simplified quality fallback.
 */
export function resolveGuitarChordShape(rawChord) {
  if (!rawChord) return null;
  const clean = String(rawChord).trim();
  if (GUITAR_CHORD_DATA[clean]) return { chordName: clean, data: GUITAR_CHORD_DATA[clean] };

  // 1. Enharmonic conversion map
  const ENHARMONIC_EQUIVS = {
    "C#": "Db", "Db": "C#",
    "D#": "Eb", "Eb": "D#",
    "F#": "Gb", "Gb": "F#",
    "G#": "Ab", "Ab": "G#",
    "A#": "Bb", "Bb": "A#",
    "C#m": "Dbm", "Dbm": "C#m",
    "D#m": "Ebm", "Ebm": "D#m",
    "F#m": "Gbm", "Gbm": "F#m",
    "G#m": "Abm", "Abm": "G#m",
    "A#m": "Bbm", "Bbm": "A#m"
  };

  if (ENHARMONIC_EQUIVS[clean] && GUITAR_CHORD_DATA[ENHARMONIC_EQUIVS[clean]]) {
    return { chordName: clean, data: GUITAR_CHORD_DATA[ENHARMONIC_EQUIVS[clean]] };
  }

  // 2. Slash chord fallback: e.g. "G/D" -> try "G"
  if (clean.includes("/")) {
    const root = clean.split("/")[0];
    const resolvedRoot = resolveGuitarChordShape(root);
    if (resolvedRoot) {
      return { chordName: clean, data: resolvedRoot.data };
    }
  }

  // 3. Simplified quality substitutions
  const SIMPLIFY_MAP = [
    [/^([A-G][#b]?)add9$/i, "$12"],
    [/^([A-G][#b]?)sus$/i, "$1sus4"],
    [/^([A-G][#b]?)maj9$/i, "$1maj7"],
    [/^([A-G][#b]?)m9$/i, "$1m7"],
    [/^([A-G][#b]?)2$/i, "$1"],
    [/^([A-G][#b]?)sus[24]$/i, "$1"]
  ];

  for (const [regex, repl] of SIMPLIFY_MAP) {
    if (regex.test(clean)) {
      const simplified = clean.replace(regex, repl);
      if (GUITAR_CHORD_DATA[simplified]) {
        return { chordName: clean, data: GUITAR_CHORD_DATA[simplified] };
      }
    }
  }

  return null;
}

/**
 * Extracts unique chords from song chord sheet and lyrics in their order of appearance.
 */
export function extractUniqueChordsFromSheet(rawSheet = "", rawLyrics = "") {
  const source = (rawSheet && rawSheet.trim()) ? rawSheet : (rawLyrics || "");
  if (!source.trim()) return [];

  const found = [];
  const seen = new Set();

  function registerChord(raw) {
    if (!raw) return;
    const clean = normalizeNoteAccidental(raw.trim().replace(/[\[\]]/g, ""));
    if (!clean || clean === "N.C." || seen.has(clean)) return;
    if (isChordToken(clean)) {
      seen.add(clean);
      found.push(clean);
    }
  }

  const lines = source.replace(/\r\n/g, "\n").split("\n");
  for (const line of lines) {
    // 1. Bracketed chord tokens [G], [Em7], [C2], etc. (Safe for all mobile browsers)
    const rawMatches = line.match(/\[([A-G][#b]?[^\]]*)\]/g);
    if (rawMatches) {
      for (const m of rawMatches) {
        registerChord(m.replace(/^\[|\]$/g, ""));
      }
    }

    // 2. Tokens on chord-over-lyric lines
    if (isLikelyChordLine(line)) {
      const tokens = line.trim().split(/\s+/);
      for (const tok of tokens) {
        if (isChordToken(tok)) {
          registerChord(tok);
        }
      }
    }
  }

  return found;
}

/**
 * Generates an SVG guitar chord diagram matching the reference design:
 * Bold chord title, top muted '×' markers, thick nut, 4 frets, dots, finger numbers underneath.
 */
export function renderGuitarChordSvg(chordName) {
  const resolved = resolveGuitarChordShape(chordName);
  if (!resolved) {
    return `
      <div class="chord-diagram-card inline-flex flex-col items-center justify-center border border-slate-200 bg-white rounded-xl px-2.5 py-2 text-center select-none min-w-[66px] h-[86px] shadow-2xs">
        <span class="font-extrabold text-xs text-[#10113F]">${escapeHtml(chordName)}</span>
        <span class="text-[9px] text-slate-400 mt-1 leading-tight">Diagram<br/>unavailable</span>
      </div>
    `;
  }

  const { frets, fingers = [], baseFret = 1 } = resolved.data;
  const numFrets = 4;
  const stringX = [13, 23, 33, 43, 53, 63];
  const topY = 32;
  const fretH = 12;
  const bottomY = topY + numFrets * fretH; // 80

  let svg = `<div class="chord-diagram-card inline-flex flex-col items-center p-1.5 rounded-xl bg-white border border-slate-200 shadow-2xs shrink-0 select-none">`;
  svg += `<svg viewBox="0 0 76 96" width="66" height="84" class="inline-block select-none chord-diagram-svg" aria-label="Chord diagram for ${escapeHtml(chordName)}">`;

  // Top Title (Chord Name)
  svg += `<text x="38" y="15" text-anchor="middle" font-size="13" font-weight="800" fill="#10113F" font-family="system-ui, -apple-system, sans-serif">${escapeHtml(chordName)}</text>`;

  // Top indicators: '×' for muted strings (open strings have no indicator, matching reference)
  frets.forEach((fret, i) => {
    if (fret === -1) {
      const x = stringX[i];
      svg += `<text x="${x}" y="27" text-anchor="middle" font-size="11" font-weight="700" fill="#64748B" font-family="system-ui, -apple-system, sans-serif">×</text>`;
    }
  });

  // Nut line (Thick top bar when baseFret === 1)
  const nutStroke = baseFret === 1 ? 3.5 : 1;
  svg += `<line x1="${stringX[0]}" y1="${topY}" x2="${stringX[5]}" y2="${topY}" stroke="#10113F" stroke-width="${nutStroke}" stroke-linecap="round"/>`;

  // Fret position indicator when chord starts higher on the neck (e.g. 2fr, 3fr)
  if (baseFret > 1) {
    svg += `<text x="4" y="${topY + 10}" font-size="8.5" font-weight="800" fill="#500568" font-family="system-ui, -apple-system, sans-serif">${baseFret}fr</text>`;
  }

  // Fret horizontal lines
  for (let f = 1; f <= numFrets; f++) {
    const y = topY + f * fretH;
    svg += `<line x1="${stringX[0]}" y1="${y}" x2="${stringX[5]}" y2="${y}" stroke="#64748B" stroke-width="0.8"/>`;
  }

  // 6 String vertical lines
  stringX.forEach((x) => {
    svg += `<line x1="${x}" y1="${topY}" x2="${x}" y2="${bottomY}" stroke="#64748B" stroke-width="0.8"/>`;
  });

  // Finger position dots
  frets.forEach((fret, i) => {
    if (fret > 0) {
      const fretRelative = fret - (baseFret - 1);
      if (fretRelative >= 1 && fretRelative <= numFrets) {
        const cx = stringX[i];
        const cy = topY + (fretRelative - 0.5) * fretH;
        svg += `<circle cx="${cx}" cy="${cy}" r="3.6" fill="#10113F"/>`;
      }
    }
  });

  // Recommended finger numbers underneath the fretboard (1=index, 2=middle, 3=ring, 4=pinky)
  fingers.forEach((finger, i) => {
    if (finger > 0) {
      const x = stringX[i];
      svg += `<text x="${x}" y="93" text-anchor="middle" font-size="9.5" font-weight="700" fill="#10113F" font-family="system-ui, -apple-system, sans-serif">${finger}</text>`;
    }
  });

  svg += `</svg></div>`;
  return svg;
}

/**
 * Renders the full dynamic guitar chords bar for a song, transposing all chords
 * based on the active semitones offset.
 */
export function renderGuitarChordsBarHtml(song, semitones = 0, useFlats = false) {
  if (!song) return "";
  const rawChords = extractUniqueChordsFromSheet(song.sheet, song.lyrics);

  if (rawChords.length === 0) {
    return `
      <div class="flex items-center justify-between text-xs text-slate-400 py-1 px-1">
        <span class="font-bold uppercase tracking-wider text-[#10113F] text-[11px]"><i class="fa-solid fa-guitar text-[#500568] mr-1.5"></i>Chords</span>
        <span class="italic">No guitar chord markers found in this chart.</span>
      </div>
    `;
  }

  // Transpose every chord in song and deduplicate preserving appearance order
  const transposedChords = [];
  const seen = new Set();
  for (const raw of rawChords) {
    const transposed = transposeChordToken(raw, semitones, useFlats);
    if (!seen.has(transposed)) {
      seen.add(transposed);
      transposedChords.push(transposed);
    }
  }

  return `
    <div class="space-y-2">
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <span class="text-xs font-black tracking-wider uppercase text-[#10113F] flex items-center gap-1.5">
            <i class="fa-solid fa-guitar text-[#500568]"></i>
            <span>Chords</span>
          </span>
          <span class="text-[11px] text-slate-500 font-semibold">(${transposedChords.length} in progression)</span>
        </div>
        ${semitones !== 0 ? `
          <span class="text-[10px] font-mono font-bold px-2 py-0.5 rounded-full bg-[#F5D0FE] text-[#500568]">
            Key Transposed: ${semitones > 0 ? "+" : ""}${semitones} st
          </span>
        ` : `
          <span class="text-[10px] text-slate-400 font-medium">Original Shapes</span>
        `}
      </div>

      <div class="flex items-center gap-2.5 overflow-x-auto pb-1.5 pt-0.5 scrollbar-thin chord-diagram-scroll" style="-webkit-overflow-scrolling: touch; touch-action: pan-x pan-y;">
        ${transposedChords.map((chord) => renderGuitarChordSvg(chord)).join("")}
      </div>
    </div>
  `;
}

