const http = require("http");
const fs = require("fs");
const path = require("path");
const os = require("os");
const otpService = require("./otpService");

const PORT = process.env.PORT || 3000;
const PUBLIC_DIR = path.join(__dirname, "public");
const MAX_JSON_BODY_BYTES = 16 * 1024; // 16 KB body size limit

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon"
};

function sendJson(res, statusCode, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Access-Control-Allow-Private-Network": "true"
  });
  res.end(body);
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let raw = "";
    let totalBytes = 0;
    req.on("data", (chunk) => {
      totalBytes += chunk.length;
      if (totalBytes > MAX_JSON_BODY_BYTES) {
        req.destroy();
        reject(new Error("Request payload too large."));
        return;
      }
      raw += chunk.toString("utf8");
    });
    req.on("end", () => {
      if (!raw.trim()) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(new Error("Invalid JSON request body."));
      }
    });
    req.on("error", (err) => reject(err));
  });
}

async function handleAuthApi(req, res, pathname) {
  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Access-Control-Allow-Private-Network": "true"
    });
    return res.end();
  }

  if (req.method !== "POST") {
    return sendJson(res, 405, { ok: false, error: "Method not allowed." });
  }

  let body;
  try {
    body = await readJsonBody(req);
  } catch (err) {
    return sendJson(res, 400, { ok: false, error: err.message || "Bad request." });
  }

  try {
    if (pathname === "/api/auth/send-otp") {
      const result = await otpService.sendOtp({
        recipientEmail: body.email,
        pendingUsername: body.username || body.recipientName || "",
        purpose: body.purpose || "reset",
        enforceCooldown: true
      });
      return sendJson(res, 200, {
        ok: true,
        success: true,
        email: result.email,
        purpose: result.purpose,
        expiresInSeconds: result.expiresInSeconds,
        expiresAt: Date.now() + result.expiresInSeconds * 1000,
        cooldownSeconds: result.cooldownSeconds
      });
    }

    if (pathname === "/api/auth/can-resend-otp") {
      const status = otpService.canResendOtp(body.email || "");
      return sendJson(res, 200, {
        ok: true,
        canResend: status.canResend,
        allowed: status.canResend,
        remainingSeconds: status.remainingSeconds,
        remainingMs: status.remainingSeconds * 1000
      });
    }

    if (pathname === "/api/auth/verify-otp") {
      const verifyResult = otpService.verifyOtp({
        email: body.email,
        enteredCode: body.code,
        purpose: body.purpose || null
      });
      return sendJson(res, verifyResult.statusCode || 200, {
        ...verifyResult,
        verified: Boolean(verifyResult.ok),
        errorMessage: verifyResult.error || ""
      });
    }

    if (pathname === "/api/auth/consume-verification") {
      const consumed = otpService.consumeVerificationToken({
        email: body.email,
        purpose: body.purpose,
        verificationToken: body.verificationToken
      });
      return sendJson(res, consumed.ok ? 200 : 400, {
        ...consumed,
        valid: Boolean(consumed.ok),
        errorMessage: consumed.error || ""
      });
    }

    if (pathname === "/api/auth/login-rate-limit") {
      const email = String(body.email || "").trim();
      const action = String(body.action || "check");
      if (action === "reset") {
        otpService.resetLoginAttempts(email);
        return sendJson(res, 200, { ok: true, locked: false, remainingSeconds: 0, remainingSec: 0 });
      }
      if (action === "record_failure") {
        const resState = otpService.recordLoginFailure(email);
        return sendJson(res, 200, {
          ok: true,
          locked: resState.locked,
          remainingAttempts: resState.remainingAttempts,
          remainingSeconds: resState.lockRemainingSeconds,
          remainingSec: resState.lockRemainingSeconds
        });
      }
      const locked = otpService.isLoginLocked(email);
      const remainingSeconds = otpService.getLoginLockRemainingSeconds(email);
      return sendJson(res, 200, {
        ok: true,
        locked,
        remainingSeconds,
        remainingSec: remainingSeconds
      });
    }

    return sendJson(res, 404, { ok: false, error: "Unknown authentication endpoint." });
  } catch (err) {
    const status = err.statusCode || 500;
    const remSec = err.remainingSeconds || 0;
    return sendJson(res, status, {
      ok: false,
      success: false,
      error: err.message || "Authentication service error.",
      errorMessage: err.message || "Authentication service error.",
      remainingSeconds: remSec,
      cooldownRemainingMs: remSec * 1000
    });
  }
}

function createAppServer() {
  return http.createServer((req, res) => {
    const safePath = decodeURIComponent(req.url.split("?")[0]);

    // Trusted Backend Authentication & OTP API routes
    if (safePath.startsWith("/api/auth/")) {
      handleAuthApi(req, res, safePath);
      return;
    }

    let filePath = path.join(PUBLIC_DIR, safePath === "/" ? "index.html" : safePath);

    if (!filePath.startsWith(PUBLIC_DIR)) {
      res.writeHead(403);
      return res.end("Forbidden");
    }

    fs.stat(filePath, (err, stats) => {
      if (err || !stats.isFile()) {
        // SPA Fallback to index.html
        filePath = path.join(PUBLIC_DIR, "index.html");
      }
      const ext = path.extname(filePath).toLowerCase();
      const contentType = MIME_TYPES[ext] || "application/octet-stream";

      fs.readFile(filePath, (readErr, content) => {
        if (readErr) {
          res.writeHead(500);
          return res.end("Server Error");
        }
        res.writeHead(200, {
          "Content-Type": contentType,
          "Cache-Control": "no-cache",
          "X-Content-Type-Options": "nosniff"
        });
        res.end(content);
      });
    });
  });
}

function getLocalLanIp() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name] || []) {
      if (iface.family === "IPv4" && !iface.internal) {
        return iface.address;
      }
    }
  }
  return "localhost";
}

if (require.main === module) {
  const server = createAppServer();
  server.listen(PORT, "0.0.0.0", () => {
    const lanIp = getLocalLanIp();
    console.log("==============================================================");
    console.log("  Synced-N Web App (Real-Time Cloud Firestore + Trusted OTP API)");
    console.log("==============================================================");
    console.log(`  PC / Desktop URL : http://localhost:${PORT}`);
    console.log(`  Phone / Wi-Fi URL: http://${lanIp}:${PORT}`);
    console.log("==============================================================");
  });
}

module.exports = { createAppServer };
