#!/usr/bin/env node
/**
 * Minimal mock Stripe API server for E2E tests.
 *
 * Responds to POST /v1/checkout/sessions with a fake checkout session
 * containing a predictable URL and session id.
 *
 * Usage: node mock-stripe-api.js [port]
 */
const http = require('http');

const port = parseInt(process.argv[2] || '19998', 10);

let sessionCounter = 0;

const server = http.createServer((req, res) => {
    if (req.method === 'POST' && req.url.startsWith('/v1/checkout/sessions')) {
        sessionCounter++;
        const sessionId = `cs_mock_e2e_${sessionCounter}`;
        const body = JSON.stringify({
            id: sessionId,
            object: 'checkout.session',
            url: `https://checkout.stripe.com/c/${sessionId}`,
            payment_status: 'unpaid',
            status: 'open',
        });
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(body);
        console.log(`[mock-stripe] POST /v1/checkout/sessions → ${sessionId}`);
        return;
    }

    // Default: 404 for unmocked endpoints
    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: { message: 'Not mocked: ' + req.method + ' ' + req.url } }));
});

server.listen(port, () => {
    console.log(`[mock-stripe] Mock Stripe API listening on port ${port}`);
});
