import { test, expect, Page } from '@playwright/test';
import { createHash } from 'crypto';
import { signInViaHeader, testStepLogger } from '../pages/test-helpers';
import { handleAuthServer, headerSignInLink, signOut } from '../pages/TODO.page';
import { registerNewUser } from '../pages/auth-server.page';

// ─── Constants ─────────────────────────────────────────────────────────────────

const RUN_ID = Date.now().toString();
const PART_UNIT_PRICE = 25.00;
const STRIPE_WEBHOOK_SECRET = 'whsec_e2e_mock';

function productCodeFor(testId: string): string {
    return `PAY-PROD-${RUN_ID}-${testId}`;
}

function partCodeFor(testId: string): string {
    return `PAY-PART-${RUN_ID}-${testId}`;
}

// ─── Stripe signature helper ──────────────────────────────────────────────────

/**
 * Computes a Stripe webhook signature for the given payload and secret.
 * Uses the same scheme as Stripe: HMAC-SHA256 of "timestamp.payload".
 */
function computeStripeSignature(payload: string, secret: string, timestamp: number): string {
    const signedPayload = `${timestamp}.${payload}`;
    const hmac = createHash('sha256');
    // Stripe uses HMAC-SHA256, not plain SHA256. We need the crypto module's createHmac.
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const crypto = require('crypto');
    const signature = crypto.createHmac('sha256', secret).update(signedPayload).digest('hex');
    return `t=${timestamp},v1=${signature}`;
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function resolveSellerOrgId(page: Page): Promise<string> {
    const resp = await page.request.get('/api/core/userinfo');
    const info = await resp.json();
    console.log(`[TestHelper] Resolved seller orgId: ${info.orgId}`);
    return info.orgId as string;
}

async function cleanupProduct(page: Page, sellerOrgId: string, productCode: string): Promise<void> {
    const prefixedCode = `${sellerOrgId}::${productCode}`;
    console.log(`[TestHelper] Cleaning up product '${prefixedCode}'`);
    const lookup = await page.request.get(`/api/product-definitions/code/${encodeURIComponent(prefixedCode)}`);
    if (lookup.status() === 404) return;
    if (!lookup.ok()) return;
    const product = await lookup.json();
    await page.request.delete(`/api/product-definitions/${product.id}/complete`);
}

async function createCrossTenantProduct(page: Page, productCode: string, partCode: string): Promise<string> {
    console.log(`[TestHelper] Creating cross-tenant product '${productCode}'`);
    const resp = await page.request.post('/api/product-definitions', {
        data: {
            productCode: productCode,
            description: 'E2E payment flow test product',
            billingModel: 'FIXED_PRICE',
            paymentModel: 'PREPAID',
            crossTenantApiAllowed: true,
            stripeSecretKey: 'sk_test_e2e_mock',
            stripeWebhookSecret: STRIPE_WEBHOOK_SECRET,
        },
    });
    expect(resp.status(), `Create product failed: ${resp.status()}`).toBe(201);
    const product = await resp.json();

    const partResp = await page.request.post(`/api/product-definitions/${product.id}/parts`, {
        data: {
            partCode: partCode,
            description: 'E2E payment flow part',
            unitPrice: PART_UNIT_PRICE,
            minCardinality: 1,
            maxCardinality: 1,
        },
    });
    expect(partResp.status(), `Create part failed: ${partResp.status()}`).toBe(201);
    return product.id;
}

async function getXsrfHeader(page: Page): Promise<Record<string, string>> {
    const cookies = await page.context().cookies();
    const xsrfToken = cookies.find(c => c.name === 'XSRF-TOKEN');
    return xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken.value } : {};
}

// ─── Test ──────────────────────────────────────────────────────────────────────

test.describe('05 Payment Flow', () => {

    const timestamp = RUN_ID;
    const newUserEmail = `e2e-payment-${timestamp}@example.com`;
    const newUserPassword = 'secretLong123!';
    let sellerOrgId: string;

    test.beforeEach(async ({ page }: { page: Page }) => {
        page.on('console', msg => {
            if (msg.type() === 'error') {
                const text = msg.text();
                if (text.includes('CORS policy') || text.includes('Mixed Content') || text.includes('ERR_FAILED') || text.includes('AUTH] Error calling logout')) {
                    return;
                }
                console.log(`[Browser Error] ${text}`);
            }
        });
        page.on('pageerror', err => console.log(`[Page Error] ${err.message}`));

        await page.goto('/');
        await signInViaHeader(page);
        sellerOrgId = await resolveSellerOrgId(page);
        for (const testId of ['PF1', 'PF2']) {
            await cleanupProduct(page, sellerOrgId, productCodeFor(testId));
        }
    });

    /**
     * PF1: Full payment flow — create contract, offer, accept (get checkoutUrl),
     * simulate Stripe webhook, verify contract transitions to RUNNING.
     */
    test('PF1: webhook success transitions contract from AWAITING_PAYMENT to RUNNING', async ({ page }: { page: Page }) => {
        const log = testStepLogger('PF1');

        // ── Step 1: create the cross-tenant product as the seller user ──────────
        const productCode = productCodeFor('PF1');
        const partCode = partCodeFor('PF1');
        log('Create product with Stripe credentials as seller user');
        await createCrossTenantProduct(page, productCode, partCode);

        // ── Step 2: sign out seller, register + sign in as a new customer ───────
        log('Sign out seller and register new customer');
        await page.goto('/');
        await signOut(page);
        await headerSignInLink(page).click();
        await page.waitForURL(/auth-t\.abstratium\.dev\/signin\//, { timeout: 15000 });

        await registerNewUser(page, {
            email: newUserEmail,
            fullName: `E2E Payment ${timestamp}`,
            orgName: `E2E Payment Org ${timestamp}`,
            password: newUserPassword,
        });
        await handleAuthServer(page, newUserEmail, newUserPassword);
        await expect(page.locator('#signout-link')).toBeVisible({ timeout: 15000 });

        // ── Step 3: create draft contract ───────────────────────────────────────
        log('Create draft contract');
        const createResp = await page.request.post('/api/public/sales/contracts', {
            headers: await getXsrfHeader(page),
            data: {
                orgId: sellerOrgId,
                contractReference: `E2E-PF1-${timestamp}`,
                publicNotes: 'Created by e2e test PF1',
                lineItems: [{
                    productCode: productCode,
                    displayOrder: 1,
                    partInstances: [{
                        partCode: partCode,
                        attributeValues: [],
                        childPartInstances: [],
                    }],
                }],
            },
        });
        expect(createResp.status(), `Create failed: ${createResp.status()}`).toBe(201);
        const contract = await createResp.json();
        const contractId = contract.id;
        expect(contract.state).toBe('DRAFT');

        // ── Step 4: offer the contract ──────────────────────────────────────────
        log('Offer the contract');
        const offerResp = await page.request.post(`/api/public/sales/contracts/${contractId}/offer`, {
            headers: await getXsrfHeader(page),
        });
        expect(offerResp.status()).toBe(200);

        // ── Step 5: accept the contract → AWAITING_PAYMENT + checkoutUrl ────────
        log('Accept the contract — should return checkoutUrl');
        const acceptResp = await page.request.post(`/api/public/sales/contracts/${contractId}/accept`, {
            headers: await getXsrfHeader(page),
        });
        const acceptBody = await acceptResp.text();
        console.log(`[PF1] Accept response: ${acceptBody}`);
        expect(acceptResp.status()).toBe(200);

        const acceptJson = JSON.parse(acceptBody);
        expect(acceptJson.checkoutUrl, 'Accept must return checkoutUrl').toBeTruthy();
        expect(acceptJson.state, 'Contract must be AWAITING_PAYMENT').toBe('AWAITING_PAYMENT');
        console.log(`[PF1] Checkout URL: ${acceptJson.checkoutUrl}`);

        // Extract the session id from the checkout URL (mock Stripe returns cs_mock_e2e_*)
        const sessionId = acceptJson.checkoutUrl.split('/').pop();
        console.log(`[PF1] Session ID: ${sessionId}`);

        // ── Step 6: simulate Stripe webhook (checkout.session.completed) ────────
        log('Simulate Stripe webhook with valid signature');
        const webhookPayload = JSON.stringify({
            id: `evt_e2e_${timestamp}`,
            type: 'checkout.session.completed',
            data: {
                object: {
                    id: sessionId,
                    object: 'checkout.session',
                    payment_intent: `pi_e2e_${timestamp}`,
                    payment_status: 'paid',
                    amount_total: Math.round(PART_UNIT_PRICE * 100),
                    currency: 'eur',
                    metadata: { correlation_id: contractId },
                },
            },
        });
        const webhookTimestamp = Math.floor(Date.now() / 1000);
        const signature = computeStripeSignature(webhookPayload, STRIPE_WEBHOOK_SECRET, webhookTimestamp);

        const webhookResp = await page.request.post('/public/payment/webhook', {
            headers: {
                'Stripe-Signature': signature,
                'Content-Type': 'application/json',
            },
            data: webhookPayload,
        });
        const webhookBody = await webhookResp.text();
        console.log(`[PF1] Webhook response status: ${webhookResp.status()}\n${webhookBody}`);
        expect(webhookResp.status(), `Webhook failed: ${webhookResp.status()}`).toBe(200);

        // ── Step 7: verify contract transitioned to RUNNING ─────────────────────
        log('Verify contract is now RUNNING');
        const getResp = await page.request.get(`/api/public/sales/contracts/${contractId}`);
        expect(getResp.status()).toBe(200);
        const finalContract = await getResp.json();
        expect(finalContract.state, 'Contract must be RUNNING after successful webhook').toBe('RUNNING');
        console.log(`[PF1] Contract is now RUNNING`);

        console.log(`[PF1] Payment flow completed successfully: id=${contractId}, state=RUNNING`);
    });

    /**
     * PF2: Webhook with invalid signature returns 400 and does not transition the contract.
     */
    test('PF2: webhook with invalid signature returns 400 and does not transition', async ({ page }: { page: Page }) => {
        const log = testStepLogger('PF2');

        // ── Setup: create product, sign in as customer, create + offer + accept ──
        const productCode = productCodeFor('PF2');
        const partCode = partCodeFor('PF2');
        log('Create product and set up contract');
        await createCrossTenantProduct(page, productCode, partCode);

        await page.goto('/');
        await signOut(page);
        await headerSignInLink(page).click();
        await page.waitForURL(/auth-t\.abstratium\.dev\/signin\//, { timeout: 15000 });

        const pf2Email = `e2e-pf2-${timestamp}@example.com`;
        await registerNewUser(page, {
            email: pf2Email,
            fullName: `E2E PF2 ${timestamp}`,
            orgName: `E2E PF2 Org ${timestamp}`,
            password: newUserPassword,
        });
        await handleAuthServer(page, pf2Email, newUserPassword);
        await expect(page.locator('#signout-link')).toBeVisible({ timeout: 15000 });

        const createResp = await page.request.post('/api/public/sales/contracts', {
            headers: await getXsrfHeader(page),
            data: {
                orgId: sellerOrgId,
                contractReference: `E2E-PF2-${timestamp}`,
                lineItems: [{
                    productCode: productCode,
                    displayOrder: 1,
                    partInstances: [{
                        partCode: partCode,
                        attributeValues: [],
                        childPartInstances: [],
                    }],
                }],
            },
        });
        expect(createResp.status()).toBe(201);
        const contract = await createResp.json();
        const contractId = contract.id;

        await page.request.post(`/api/public/sales/contracts/${contractId}/offer`, {
            headers: await getXsrfHeader(page),
        });

        const acceptResp = await page.request.post(`/api/public/sales/contracts/${contractId}/accept`, {
            headers: await getXsrfHeader(page),
        });
        expect(acceptResp.status()).toBe(200);
        const acceptJson = await acceptResp.json();
        const sessionId = acceptJson.checkoutUrl.split('/').pop();

        // ── Send webhook with invalid signature ─────────────────────────────────
        log('Send webhook with invalid signature');
        const webhookPayload = JSON.stringify({
            id: `evt_bad_${timestamp}`,
            type: 'checkout.session.completed',
            data: {
                object: {
                    id: sessionId,
                    object: 'checkout.session',
                    payment_intent: `pi_bad_${timestamp}`,
                    payment_status: 'paid',
                    amount_total: Math.round(PART_UNIT_PRICE * 100),
                    currency: 'eur',
                    metadata: { correlation_id: contractId },
                },
            },
        });

        const badSignature = `t=${Math.floor(Date.now() / 1000)},v1=invalid_signature_hex`;

        const webhookResp = await page.request.post('/public/payment/webhook', {
            headers: {
                'Stripe-Signature': badSignature,
                'Content-Type': 'application/json',
            },
            data: webhookPayload,
        });
        console.log(`[PF2] Invalid webhook response status: ${webhookResp.status()}`);
        expect(webhookResp.status(), `Expected 400 but got ${webhookResp.status()}`).toBe(400);

        // ── Verify contract is still AWAITING_PAYMENT ────────────────────────────
        log('Verify contract is still AWAITING_PAYMENT');
        const getResp = await page.request.get(`/api/public/sales/contracts/${contractId}`);
        const finalContract = await getResp.json();
        expect(finalContract.state, 'Contract must still be AWAITING_PAYMENT').toBe('AWAITING_PAYMENT');
        console.log(`[PF2] Contract remains AWAITING_PAYMENT after invalid webhook`);
    });
});
