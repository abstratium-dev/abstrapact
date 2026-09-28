#!/usr/bin/env node
/**
 * Starts the Stripe CLI listener, captures the webhook signing secret from its
 * output, and exposes it via a small HTTP server so E2E tests can fetch it.
 *
 * Authentication:
 *   The Stripe CLI reads the STRIPE_API_KEY environment variable, so no
 *   `stripe login` is needed. Set it once in your environment:
 *     export STRIPE_API_KEY=sk_test_...
 *
 * Usage:
 *   node start-stripe-cli.js
 *
 * What it does:
 *   1. Spawns `stripe listen --all-snapshot --forward-to localhost:8088/public/payment/webhook`
 *   2. Mirrors stdout/stderr to the console and to tmp/stripe-cli.log
 *   3. Scrapes the whsec_... signing secret from the output
 *   4. Starts an HTTP server on port 19999 that serves the secret as JSON
 *   5. Writes the Stripe CLI PID to tmp/stripe-cli.pid
 *   6. On Ctrl+C or exit, kills the Stripe CLI and the HTTP server
 *
 * The E2E tests fetch the secret via:
 *   GET http://localhost:19999/webhook-secret
 *   → { "secret": "whsec_..." }
 */
const { spawn } = require('child_process');
const http = require('http');
const fs = require('fs');
const path = require('path');

const FORWARD_TO = 'localhost:8088/public/payment/webhook';
const HTTP_PORT = 19997;
const TMP_DIR = path.resolve(__dirname, '..', 'tmp');
const PID_FILE = path.join(TMP_DIR, 'stripe-cli.pid');
const LOG_FILE = path.join(TMP_DIR, 'stripe-cli.log');
const SECRET_FILE = path.join(TMP_DIR, 'stripe-webhook-secret.txt');

// Ensure tmp dir exists
if (!fs.existsSync(TMP_DIR)) {
    fs.mkdirSync(TMP_DIR, { recursive: true });
}

let webhookSecret = null;
let stripeProc = null;
let httpServer = null;

// ─── Start the HTTP server that serves the webhook secret ─────────────────────
httpServer = http.createServer((req, res) => {
    if (req.url === '/webhook-secret' && req.method === 'GET') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ secret: webhookSecret }));
        console.log(`[http] GET /webhook-secret → ${webhookSecret ? webhookSecret.substring(0, 10) + '...' : 'null'}`);
        return;
    }
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Not found' }));
});

httpServer.listen(HTTP_PORT, () => {
    console.log(`[http] Secret server listening on http://localhost:${HTTP_PORT}`);
    console.log(`[http] GET /webhook-secret to retrieve the Stripe webhook signing secret`);
});

// ─── Start the Stripe CLI listener ────────────────────────────────────────────
// --all-snapshot is required by newer stripe-cli versions (>= 1.25) to forward
// all events with full payload. Without it, the CLI exits with:
//   "must specify events to forward using --events, --all-snapshot, or --all-thin"
const stripeArgs = ['listen', '--all-snapshot', '--forward-to', FORWARD_TO];
console.log(`[stripe] Starting: stripe ${stripeArgs.join(' ')}`);

stripeProc = spawn('stripe', stripeArgs, {
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env },
});

// Write PID file
fs.writeFileSync(PID_FILE, String(stripeProc.pid));
console.log(`[stripe] PID ${stripeProc.pid} written to ${PID_FILE}`);

// Open log file for writing
const logStream = fs.createWriteStream(LOG_FILE, { flags: 'w' });

// Regex to extract the webhook secret from Stripe CLI output
const secretRegex = /whsec_[A-Za-z0-9]+/;

function processOutput(data, stream) {
    const text = data.toString();
    // Mirror to console
    process.stdout.write(text);
    // Write to log file
    logStream.write(text);

    // Scrape the webhook secret if not yet found
    if (!webhookSecret) {
        const match = text.match(secretRegex);
        if (match) {
            webhookSecret = match[0];
            console.log(`\n[stripe] ✅ Captured webhook secret: ${webhookSecret.substring(0, 10)}...`);
            // Also write to a file for convenience
            fs.writeFileSync(SECRET_FILE, webhookSecret);
            console.log(`[stripe] Secret written to ${SECRET_FILE}`);
        }
    }
}

stripeProc.stdout.on('data', (data) => processOutput(data, 'stdout'));
stripeProc.stderr.on('data', (data) => processOutput(data, 'stderr'));

stripeProc.on('exit', (code, signal) => {
    console.log(`[stripe] Stripe CLI exited (code=${code}, signal=${signal})`);
    cleanup();
});

// ─── Cleanup ──────────────────────────────────────────────────────────────────
function cleanup() {
    if (stripeProc && !stripeProc.killed) {
        console.log('[stripe] Killing Stripe CLI process...');
        stripeProc.kill('SIGTERM');
    }
    if (httpServer && httpServer.listening) {
        console.log('[http] Stopping secret server...');
        httpServer.close();
    }
    if (fs.existsSync(PID_FILE)) {
        fs.unlinkSync(PID_FILE);
    }
    if (logStream && !logStream.destroyed) {
        logStream.end();
    }
    process.exit(0);
}

process.on('SIGINT', () => {
    console.log('\n[main] Received SIGINT, shutting down...');
    cleanup();
});

process.on('SIGTERM', () => {
    console.log('\n[main] Received SIGTERM, shutting down...');
    cleanup();
});

console.log('[main] Press Ctrl+C to stop.');
