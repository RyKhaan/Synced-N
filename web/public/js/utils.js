import { CLOUDINARY_CONFIG, TEAM_CODE_CHARS } from "./config.js";

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
  if (DISPOSABLE_DOMAINS.has(domainLower) || domainLower.endsWith(".invalid") || domainLower.endsWith(".test")) {
    return "Disposable or temporary email addresses are not allowed.";
  }
  return null;
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

// YouTube helpers matching SongData.kt
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

// Musical Chord Transposition Engine
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

export function getSemitoneDiff(fromKey = "C", toKey = "C") {
  const cleanRoot = (k) => {
    const m = String(k || "C").trim().match(/^([A-G][#b]?)/i);
    if (!m) return "C";
    return m[1][0].toUpperCase() + (m[1][1] || "");
  };
  const i1 = NOTE_INDEX[cleanRoot(fromKey)] ?? 0;
  const i2 = NOTE_INDEX[cleanRoot(toKey)] ?? 0;
  return (i2 - i1 + 12) % 12;
}

export function transposeSingleNote(note, semitones, useFlats = false) {
  if (!note) return note;
  const formatted = note[0].toUpperCase() + (note.slice(1) || "");
  const idx = NOTE_INDEX[formatted];
  if (idx === undefined) return note;
  const nextIdx = ((idx + semitones) % 12 + 12) % 12;
  return (useFlats ? CHROMATIC_FLAT : CHROMATIC_SHARP)[nextIdx];
}

export function transposeChordToken(chord, semitones, useFlats = false) {
  if (!semitones) return chord;
  return String(chord).replace(/^([A-G][#b]?)(.*?)(\/([A-G][#b]?))?$/, (_, root, quality, slashFull, bass) => {
    const newRoot = transposeSingleNote(root, semitones, useFlats);
    const newBass = bass ? "/" + transposeSingleNote(bass, semitones, useFlats) : "";
    return `${newRoot}${quality || ""}${newBass}`;
  });
}

function isChordToken(tok) {
  return /^[A-G][#b]?(?:m|maj|min|dim|aug|sus|add|\d|[+\-()])*(?:\/[A-G][#b]?)?$/.test(tok);
}

function isLikelyChordLine(line) {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith("[") || trimmed.startsWith("{")) return false;
  const tokens = trimmed.split(/\s+/).filter((t) => t !== "|" && t !== "-" && t !== "/");
  if (!tokens.length) return false;
  const chordCount = tokens.filter(isChordToken).length;
  return chordCount > 0 && chordCount / tokens.length >= 0.65;
}

export function renderFormattedChordSheet(rawSheet = "", rawLyrics = "", semitones = 0, lyricsOnly = false) {
  const source = (rawSheet && rawSheet.trim()) ? rawSheet : (rawLyrics || "");
  if (!source.trim()) {
    return `<div class="text-slate-400 italic py-6 text-center">No chord sheet or lyrics added for this song yet.</div>`;
  }

  const lines = source.replace(/\r\n/g, "\n").split("\n");
  const out = [];

  for (const line of lines) {
    const trimmed = line.trim();

    // Check section header e.g. [Verse 1], [Chorus], {soc}, Verse 1:
    if (/^\[(?:Verse|Chorus|Pre-Chorus|Bridge|Intro|Outro|Instrumental|Interlude|Tag|Ending|Refrain|Hook|Solo)[^\]]*\]$/i.test(trimmed) ||
        /^(?:Verse|Chorus|Pre-Chorus|Bridge|Intro|Outro|Instrumental|Interlude|Tag|Refrain)(?:\s+\d+)?\s*:?$/i.test(trimmed)) {
      const label = trimmed.replace(/^\[|\]$/g, "");
      out.push(`<div><span class="section-header-line">${escapeHtml(label)}</span></div>`);
      continue;
    }

    // Inline [Chord] notation (ChordPro style)
    if (/\[([A-G][#b]?[^\]]*)\]/.test(line)) {
      if (lyricsOnly) {
        const stripped = line.replace(/\[([^\]]+)\]/g, "").replace(/\s{2,}/g, " ").trim();
        if (stripped) out.push(`<div>${escapeHtml(stripped)}</div>`);
      } else {
        const htmlLine = escapeHtml(line).replace(/\[([A-G][#b]?[^\]]*)\]/g, (_, ch) => {
          const transposed = transposeChordToken(ch, semitones);
          return `<span class="chord-token">${escapeHtml(transposed)}</span>`;
        });
        out.push(`<div>${htmlLine}</div>`);
      }
      continue;
    }

    // Chord-over-lyric line
    if (isLikelyChordLine(line)) {
      if (lyricsOnly) continue;
      const transposedLine = line.replace(/([A-G][#b]?(?:m|maj|min|dim|aug|sus|add|\d|[+\-()])*(?:\/[A-G][#b]?)?)/g, (tok) => {
        return isChordToken(tok) ? transposeChordToken(tok, semitones) : tok;
      });
      out.push(`<div class="chord-line">${escapeHtml(transposedLine)}</div>`);
      continue;
    }

    out.push(`<div>${escapeHtml(line) || "&nbsp;"}</div>`);
  }

  return out.join("");
}

// WebAudio Metronome Engine
export class MetronomeEngine {
  constructor(onBeatCallback) {
    this.audioCtx = null;
    this.isPlaying = false;
    this.bpm = 74;
    this.beatIndex = 0;
    this.timerId = null;
    this.onBeatCallback = onBeatCallback;
  }

  setBpm(bpm) {
    const parsed = parseInt(bpm, 10);
    this.bpm = Math.min(240, Math.max(30, isNaN(parsed) ? 74 : parsed));
    if (this.isPlaying) {
      this.stop();
      this.start();
    }
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
      gain.gain.setValueAtTime(0.35, this.audioCtx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, this.audioCtx.currentTime + 0.065);
      osc.connect(gain);
      gain.connect(this.audioCtx.destination);
      osc.start();
      osc.stop(this.audioCtx.currentTime + 0.07);
    } catch {
      // Ignore audio context errors if blocked
    }
  }

  start() {
    if (this.isPlaying) return;
    this.isPlaying = true;
    this.beatIndex = 0;
    const tick = () => {
      if (!this.isPlaying) return;
      const isAccent = this.beatIndex % 4 === 0;
      this.playClick(isAccent);
      if (this.onBeatCallback) this.onBeatCallback((this.beatIndex % 4) + 1, isAccent);
      this.beatIndex++;
    };
    tick();
    const intervalMs = Math.round(60000 / this.bpm);
    this.timerId = setInterval(tick, intervalMs);
  }

  stop() {
    this.isPlaying = false;
    if (this.timerId) {
      clearInterval(this.timerId);
      this.timerId = null;
    }
    if (this.onBeatCallback) this.onBeatCallback(0, false);
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
