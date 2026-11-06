const crypto = require("crypto");
const https = require("https");

/**
 * Trusted Backend Email OTP & Brute-Force Protection Service for Synced N Web.
 * Mirrors `EmailOtpService.kt` and `ValidationUtils.kt` from the Android mobile application:
 * - Cryptographic 6-digit OTP generation (`crypto.randomInt(100000, 1000000)`)
 * - 10-minute OTP expiration (`OTP_VALIDITY_MS = 10 * 60 * 1000`)
 * - 60-second resend cooldown (`RESEND_COOLDOWN_MS = 60 * 1000`)
 * - Maximum 5 verification attempts before automatic OTP invalidation (`MAX_OTP_ATTEMPTS = 5`)
 * - Single-use OTP consumption + single-use backend verification tokens to prevent bypass/reuse
 * - Server-side Brevo REST API delivery (API key never exposed to the browser)
 * - Server-side Login Brute-Force Rate Limiting (5 failed attempts -> 60s lockout)
 */

const OTP_VALIDITY_MS = 10 * 60 * 1000; // 10 minutes
const MAX_OTP_ATTEMPTS = 5;
const RESEND_COOLDOWN_MS = 60 * 1000; // 60 seconds
const VERIFICATION_TOKEN_TTL_MS = 5 * 60 * 1000; // 5 minutes to finalize registration/reset after OTP verify

const MAX_LOGIN_ATTEMPTS = 5;
const LOGIN_LOCKOUT_DURATION_MS = 60 * 1000; // 60 seconds

const SENDER_NAME = "Synced N Ministry";
const SENDER_EMAIL = process.env.BREVO_SENDER_EMAIL || "ryanlloydgenturo@gmail.com";
const BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

// Same masked fallback bytes used by Android `EmailOtpService.kt` (kept strictly server-side)
const FALLBACK_MASKED_BYTES = [
  34, 49, 63, 35, 41, 51, 56, 119, 111, 107, 110, 63, 105, 110, 59, 59,
  106, 105, 109, 57, 98, 110, 106, 105, 105, 105, 110, 60, 109, 104, 62, 62,
  59, 63, 63, 105, 60, 62, 99, 57, 110, 63, 111, 111, 111, 56, 57, 106,
  59, 110, 60, 56, 109, 98, 105, 111, 56, 111, 56, 106, 106, 105, 104, 57,
  57, 62, 104, 62, 106, 57, 59, 110, 119, 9, 35, 60, 12, 22, 22, 34,
  18, 8, 47, 16, 55, 105, 57, 63, 51
];

function getBrevoApiKey() {
  if (process.env.BREVO_API_KEY && process.env.BREVO_API_KEY.trim()) {
    return process.env.BREVO_API_KEY.trim();
  }
  return Buffer.from(FALLBACK_MASKED_BYTES.map((b) => b ^ 0x5a)).toString("utf8");
}

// Ephemeral per-process HMAC secret for hashing OTP codes in memory
const OTP_HMAC_SECRET = crypto.randomBytes(32);

// Active OTP records keyed by normalized email
const otpRecords = new Map();

// Active single-use verification tokens issued after successful OTP verification
const verifiedTokens = new Map();

// Login brute-force attempt states keyed by normalized email
const loginAttemptMap = new Map();

function normalizeEmailKey(email) {
  return String(email || "").trim().toLowerCase();
}

function hashOtpCode(emailKey, code) {
  return crypto
    .createHmac("sha256", OTP_HMAC_SECRET)
    .update(`${emailKey}:${String(code).trim()}`)
    .digest();
}

function escapeHtml(input = "") {
  return String(input)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function generateSecureOtpCode() {
  return String(crypto.randomInt(100000, 1000000));
}

const DISPOSABLE_DOMAINS = new Set([
  "tempmail.com", "temp-mail.org", "mailinator.com", "10minutemail.com",
  "guerrillamail.com", "yopmail.com", "trashmail.com", "fakeinbox.com",
  "sharklasers.com", "getnada.com", "throwawaymail.com", "maildrop.cc",
  "dispostable.com", "fake.com", "test.invalid", "invalid.com",
  "mailnesia.com", "mohmal.com", "mytemp.email"
]);

function validateEmailForAuth(email = "") {
  const clean = String(email || "").trim();
  if (!clean) return "Enter your email address.";
  if (clean.length > 60) return "Email address cannot exceed 60 characters.";
  if (clean.includes("..")) return "Enter a valid email address.";
  const parts = clean.split("@");
  if (parts.length !== 2) return "Enter a valid email address.";
  const [localPart, domainPart] = parts;
  if (!localPart || localPart.startsWith(".") || localPart.endsWith(".")) {
    return "Enter a valid email address.";
  }
  if (!domainPart || domainPart.startsWith(".") || domainPart.endsWith(".") || !domainPart.includes(".")) {
    return "Enter a valid email address.";
  }
  if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,10}$/.test(clean)) {
    return "Enter a valid email address.";
  }
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

/**
 * Checks if the user can request/resend an OTP or if the 60-second cooldown is active.
 */
function canResendOtp(email, nowMs = Date.now()) {
  const key = normalizeEmailKey(email);
  const rec = otpRecords.get(key);
  if (!rec) return { canResend: true, remainingSeconds: 0 };
  const elapsed = nowMs - rec.sentAt;
  if (elapsed >= RESEND_COOLDOWN_MS) {
    return { canResend: true, remainingSeconds: 0 };
  }
  const remainingSeconds = Math.max(1, Math.floor((RESEND_COOLDOWN_MS - elapsed + 999) / 1000));
  return { canResend: false, remainingSeconds };
}

/**
 * Sends a 6-digit OTP email via Brevo REST API (or custom transport in unit tests).
 */
let customEmailTransport = null;
function setCustomEmailTransportForTesting(fn) {
  customEmailTransport = fn;
}

function sendBrevoEmailHttp(payloadObj) {
  if (typeof customEmailTransport === "function") {
    return customEmailTransport(payloadObj);
  }

  return new Promise((resolve, reject) => {
    const bodyStr = JSON.stringify(payloadObj);
    const parsedUrl = new URL(BREVO_API_URL);
    const req = https.request(
      {
        hostname: parsedUrl.hostname,
        path: parsedUrl.pathname,
        method: "POST",
        timeout: 15000,
        headers: {
          "api-key": getBrevoApiKey(),
          "Content-Type": "application/json; charset=UTF-8",
          Accept: "application/json",
          "Content-Length": Buffer.byteLength(bodyStr, "utf8")
        }
      },
      (res) => {
        let responseBody = "";
        res.setEncoding("utf8");
        res.on("data", (chunk) => {
          responseBody += chunk;
        });
        res.on("end", () => {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve({ statusCode: res.statusCode, body: responseBody });
          } else {
            const lower = responseBody.toLowerCase();
            if (lower.includes("unrecognised ip") || lower.includes("authorised_ips")) {
              reject(
                new Error(
                  "Brevo security check: Please click the authorization link sent to your email or visit https://app.brevo.com/security/authorised_ips to authorize your IP address."
                )
              );
            } else {
              reject(new Error(`Email service error (${res.statusCode}): ${responseBody}`));
            }
          }
        });
      }
    );

    req.on("error", (err) => reject(err));
    req.on("timeout", () => {
      req.destroy(new Error("Email service request timed out."));
    });
    req.write(bodyStr);
    req.end();
  });
}

/**
 * Generates, stores, and sends a 6-digit OTP code for "register" or "reset".
 */
async function sendOtp({
  recipientEmail,
  pendingUsername = "",
  purpose = "reset",
  enforceCooldown = true,
  nowMs = Date.now()
}) {
  const trimmedEmail = String(recipientEmail || "").trim();
  const emailErr = validateEmailForAuth(trimmedEmail);
  if (emailErr) {
    const err = new Error(emailErr);
    err.statusCode = 400;
    throw err;
  }

  const normalizedPurpose = String(purpose || "reset").toLowerCase() === "register" ? "register" : "reset";
  const emailKey = normalizeEmailKey(trimmedEmail);

  if (enforceCooldown) {
    const cooldown = canResendOtp(trimmedEmail, nowMs);
    if (!cooldown.canResend) {
      const err = new Error(`Please wait ${cooldown.remainingSeconds}s before requesting another code.`);
      err.statusCode = 429;
      err.remainingSeconds = cooldown.remainingSeconds;
      throw err;
    }
  }

  const generatedCode = generateSecureOtpCode();
  const expiresAt = nowMs + OTP_VALIDITY_MS;

  otpRecords.set(emailKey, {
    email: trimmedEmail,
    codeHash: hashOtpCode(emailKey, generatedCode),
    expiresAt,
    pendingUsername: String(pendingUsername || "").trim().slice(0, 50),
    purpose: normalizedPurpose,
    failedAttempts: 0,
    sentAt: nowMs
  });

  // Invalidate any older unconsumed verification token for this email
  verifiedTokens.delete(emailKey);

  const isRegister = normalizedPurpose === "register";
  const safeUsername = escapeHtml(String(pendingUsername || "").trim());
  const greeting =
    isRegister && safeUsername
      ? `Welcome to Synced N, <strong>${safeUsername}</strong>! Use the 6-digit verification code below to verify your email address and complete your registration:`
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
        <span style="font-size: 34px; font-weight: 800; letter-spacing: 8px; color: #500568; font-family: monospace;">${generatedCode}</span>
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
      name: SENDER_NAME,
      email: SENDER_EMAIL
    },
    to: [{ email: trimmedEmail }],
    subject: subjectLine,
    htmlContent: htmlBody
  };

  try {
    await sendBrevoEmailHttp(payload);
  } catch (sendErr) {
    // Roll back record if email delivery failed so user isn't locked out by cooldown
    otpRecords.delete(emailKey);
    throw sendErr;
  }

  return {
    email: trimmedEmail,
    purpose: normalizedPurpose,
    expiresAt,
    expiresInSeconds: Math.floor(OTP_VALIDITY_MS / 1000),
    cooldownSeconds: Math.floor(RESEND_COOLDOWN_MS / 1000)
  };
}

/**
 * Verifies a submitted 6-digit OTP code against the active server-side record.
 * Enforces 10-minute expiration, 5-attempt brute-force invalidation, and single-use deletion.
 */
function verifyOtp({ email, enteredCode, purpose = null, nowMs = Date.now() }) {
  const emailKey = normalizeEmailKey(email);
  const record = otpRecords.get(emailKey);
  if (!record) {
    return {
      ok: false,
      statusCode: 400,
      error: "No verification code found. Please request a new code."
    };
  }

  const trimmedCode = String(enteredCode || "").trim();
  if (!trimmedCode || !/^\d{6}$/.test(trimmedCode)) {
    return {
      ok: false,
      statusCode: 400,
      error: "Please enter the 6-digit verification code."
    };
  }

  if (nowMs > record.expiresAt) {
    otpRecords.delete(emailKey);
    return {
      ok: false,
      statusCode: 400,
      expired: true,
      error: "Verification code has expired. Please request a new one."
    };
  }

  if (purpose && String(purpose).toLowerCase() !== record.purpose) {
    return {
      ok: false,
      statusCode: 400,
      error: "Verification code purpose mismatch. Please request a new code."
    };
  }

  const candidateHash = hashOtpCode(emailKey, trimmedCode);
  const isMatch =
    candidateHash.length === record.codeHash.length &&
    crypto.timingSafeEqual(candidateHash, record.codeHash);

  if (!isMatch) {
    const nextFailures = record.failedAttempts + 1;
    if (nextFailures >= MAX_OTP_ATTEMPTS) {
      otpRecords.delete(emailKey);
      return {
        ok: false,
        statusCode: 429,
        invalidated: true,
        remainingAttempts: 0,
        error: "Too many incorrect attempts. Verification code has been invalidated. Please request a new code."
      };
    }
    record.failedAttempts = nextFailures;
    otpRecords.set(emailKey, record);
    const remaining = MAX_OTP_ATTEMPTS - nextFailures;
    return {
      ok: false,
      statusCode: 400,
      remainingAttempts: remaining,
      error: `Invalid verification code (${remaining} attempts remaining). Please check your email and try again.`
    };
  }

  // Single-use: immediately delete OTP record so it can never be reused
  otpRecords.delete(emailKey);

  // Issue a short-lived single-use verification token bound to this email and purpose
  const verificationToken = crypto.randomBytes(32).toString("hex");
  verifiedTokens.set(emailKey, {
    token: verificationToken,
    email: record.email,
    purpose: record.purpose,
    pendingUsername: record.pendingUsername,
    expiresAt: nowMs + VERIFICATION_TOKEN_TTL_MS
  });

  return {
    ok: true,
    statusCode: 200,
    email: record.email,
    purpose: record.purpose,
    pendingUsername: record.pendingUsername,
    verificationToken
  };
}

/**
 * Verifies and consumes the single-use `verificationToken` issued by `verifyOtp`.
 * Prevents frontend callers from bypassing OTP verification before account creation or password reset.
 */
function consumeVerificationToken({ email, purpose, verificationToken, nowMs = Date.now() }) {
  const emailKey = normalizeEmailKey(email);
  const entry = verifiedTokens.get(emailKey);
  if (!entry || !verificationToken) {
    return {
      ok: false,
      error: "Email verification required. Please verify the 6-digit OTP code sent to your email."
    };
  }
  if (nowMs > entry.expiresAt) {
    verifiedTokens.delete(emailKey);
    return {
      ok: false,
      error: "Verification session expired. Please request a new verification code."
    };
  }
  if (purpose && String(purpose).toLowerCase() !== entry.purpose) {
    return {
      ok: false,
      error: "Verification token purpose mismatch."
    };
  }
  const a = Buffer.from(String(verificationToken), "utf8");
  const b = Buffer.from(String(entry.token), "utf8");
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return {
      ok: false,
      error: "Invalid verification token."
    };
  }

  // Single-use consumption
  verifiedTokens.delete(emailKey);
  return {
    ok: true,
    email: entry.email,
    purpose: entry.purpose,
    pendingUsername: entry.pendingUsername
  };
}

/**
 * Server-side Login Brute-Force Rate Limiting (mirrors `ValidationUtils.kt`)
 */
function isLoginLocked(email, nowMs = Date.now()) {
  const key = normalizeEmailKey(email);
  const state = loginAttemptMap.get(key);
  if (!state) return false;
  if (state.lockedUntilMs > nowMs) return true;
  if (state.lockedUntilMs > 0 && nowMs >= state.lockedUntilMs) {
    loginAttemptMap.delete(key);
  }
  return false;
}

function getLoginLockRemainingSeconds(email, nowMs = Date.now()) {
  const key = normalizeEmailKey(email);
  const state = loginAttemptMap.get(key);
  if (!state) return 0;
  const remainingMs = state.lockedUntilMs - nowMs;
  if (remainingMs <= 0) return 0;
  return Math.floor((remainingMs + 999) / 1000);
}

function recordLoginFailure(email, nowMs = Date.now()) {
  const key = normalizeEmailKey(email);
  const current = loginAttemptMap.get(key);
  const baseCount =
    current && current.lockedUntilMs > 0 && nowMs >= current.lockedUntilMs
      ? 0
      : current?.failedCount || 0;
  const nextCount = baseCount + 1;
  if (nextCount >= MAX_LOGIN_ATTEMPTS) {
    loginAttemptMap.set(key, {
      failedCount: nextCount,
      lockedUntilMs: nowMs + LOGIN_LOCKOUT_DURATION_MS
    });
    return {
      locked: true,
      remainingAttempts: 0,
      lockRemainingSeconds: Math.floor(LOGIN_LOCKOUT_DURATION_MS / 1000)
    };
  }
  loginAttemptMap.set(key, {
    failedCount: nextCount,
    lockedUntilMs: 0
  });
  return {
    locked: false,
    remainingAttempts: MAX_LOGIN_ATTEMPTS - nextCount,
    lockRemainingSeconds: 0
  };
}

function resetLoginAttempts(email) {
  const key = normalizeEmailKey(email);
  if (key) loginAttemptMap.delete(key);
}

/**
 * Helper for automated unit testing (mirrors `EmailOtpService.setRecordForTesting` in Kotlin)
 */
function setRecordForTesting({
  email,
  code,
  expiresAt = Date.now() + OTP_VALIDITY_MS,
  pendingUsername = "",
  purpose = "reset",
  failedAttempts = 0,
  sentAt = Date.now()
}) {
  const emailKey = normalizeEmailKey(email);
  otpRecords.set(emailKey, {
    email: String(email).trim(),
    codeHash: hashOtpCode(emailKey, code),
    expiresAt,
    pendingUsername,
    purpose,
    failedAttempts,
    sentAt
  });
}

function clearAllForTesting() {
  otpRecords.clear();
  verifiedTokens.clear();
  loginAttemptMap.clear();
}

module.exports = {
  OTP_VALIDITY_MS,
  MAX_OTP_ATTEMPTS,
  RESEND_COOLDOWN_MS,
  MAX_LOGIN_ATTEMPTS,
  LOGIN_LOCKOUT_DURATION_MS,
  escapeHtml,
  generateSecureOtpCode,
  validateEmailForAuth,
  canResendOtp,
  sendOtp,
  verifyOtp,
  consumeVerificationToken,
  isLoginLocked,
  getLoginLockRemainingSeconds,
  recordLoginFailure,
  resetLoginAttempts,
  setRecordForTesting,
  setCustomEmailTransportForTesting,
  clearAllForTesting
};
