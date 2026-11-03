import { CLOUDINARY_CONFIG, TEAM_CODE_CHARS, CHROMATIC_ROOTS, DEFAULT_SEED_SONGS } from "./config.js";

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
