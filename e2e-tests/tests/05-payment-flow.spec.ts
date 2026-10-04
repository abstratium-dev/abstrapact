import { env } from 'node:process';
import { test, expect, Page } from '@playwright/test';
import { signInViaHeader, testStepLogger } from '../pages/test-helpers';
import { handleAuthServer, headerSignInLink, signOut } from '../pages/test-helpers';
import { registerNewUser } from '../pages/auth-server.page';

// ─── Constants ─────────────────────────────────────────────────────────────────

const RUN_ID = Date.now().toString();
const PART_UNIT_PRICE = 25.00;

// Stripe test credentials.
// The API key is read from STRIPE_API_KEY (used by both the Stripe CLI and abstrapact).
// The webhook secret is fetched from the start-stripe-cli.js helper script's HTTP
// server on localhost:19997 (override with STRIPE_CLI_HELPER_PORT). It can also
// be overridden via STRIPE_TEST_WEBHOOK_SECRET.
const STRIPE_TEST_SECRET_KEY = env.STRIPE_API_KEY || env.STRIPE_TEST_SECRET_KEY;
let stripeWebhookSecret: string | null = env.STRIPE_TEST_WEBHOOK_SECRET || null;

// Standard Stripe test card number.
const STRIPE_TEST_CARD_NUMBER = '4242424242424242';
const STRIPE_TEST_CARD_EXPIRY = '1230'; // 12/30
const STRIPE_TEST_CARD_CVC = '123';

/**
 * Fetches the Stripe webhook signing secret from the start-stripe-cli.js helper
 * script, which serves it via HTTP on localhost:19997
 * (override with STRIPE_CLI_HELPER_PORT).
 *
 * The helper script must be running (see start-stripe-cli.js). It starts the
 * Stripe CLI listener, scrapes the whsec_... from the output, and exposes it
 * via a small HTTP server.
 *
 * Returns null if the helper script is not running or the secret hasn't been
 * captured yet.
 */
async function fetchStripeWebhookSecret(): Promise<string | null> {
    try {
        const resp = await fetch(`http://localhost:${env.STRIPE_CLI_HELPER_PORT || 19997}/webhook-secret`, {
            signal: AbortSignal.timeout(5000),
        });
        if (!resp.ok) {
            console.warn(`[Stripe] Secret server returned ${resp.status}`);
            return null;
        }
        const body = await resp.json();
        if (body.secret && body.secret.startsWith('whsec_')) {
            console.log(`[Stripe] Fetched webhook secret from helper: ${body.secret.substring(0, 10)}...`);
            return body.secret;
        }
        console.warn(`[Stripe] Secret not yet captured by helper (stripe listen may still be starting)`);
        return null;
    } catch (e) {
        console.warn(`[Stripe] Could not fetch webhook secret from helper: ${(e as Error).message}`);
        console.warn('[Stripe] Ensure start-stripe-cli.js is running (node e2e-tests/start-stripe-cli.js)');        return null;
    }
}

function productCodeFor(testId: string): string {
    return `PAY-PROD-${RUN_ID}-${testId}`;
}

function partCodeFor(testId: string): string {
    return `PAY-PART-${RUN_ID}-${testId}`;
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
            stripeSecretKey: STRIPE_TEST_SECRET_KEY,
            stripeWebhookSecret: stripeWebhookSecret,
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
    // The public contract endpoints require an Idempotency-Key on mutating
    // requests; endpoints that don't need it ignore the header.
    return {
        ...(xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken.value } : {}),
        'Idempotency-Key': crypto.randomUUID(),
    };
}

/**
 * Fills in the Stripe hosted checkout form and submits the payment.
 *
 * Stripe Checkout uses an accordion for payment methods. The "Card" option must
 * be clicked to expand its panel and reveal the card input fields. The fields are
 * direct textboxes (not iframes in the current Stripe Checkout version).
 *
 * GBP note: Stripe auto-detects the customer's location and shows a local currency
 * (e.g. GBP) alongside the merchant's currency (CHF). This is normal Stripe behavior.
 */
/**
 * Asserts that the Stripe checkout page shows expected content before filling
 * payment details. These assertions verify that the contract details (amount,
 * currency, product info) are correctly passed through to Stripe's hosted page.
 */
async function assertStripeCheckoutContent(page: Page, expectedAmount: number): Promise<void> {
    console.log('[StripeCheckout] Asserting checkout page content...');

    const body = page.locator('body');
    const bodyText = await body.textContent();
    expect(bodyText).toBeTruthy();

    // The CHF amount should be displayed (the merchant's integration currency).
    // Stripe formats it differently depending on locale, but CHF should always
    // be visible somewhere on the page.
    const hasChf = bodyText!.toUpperCase().includes('CHF') ||
                   bodyText!.toLowerCase().includes('fr.');
    expect(hasChf, `Checkout page should show merchant currency CHF (got: ${bodyText!.substring(0, 200)}...)`)
        .toBe(true);

    // The numeric amount must be present.
    const hasAmount = bodyText!.includes(expectedAmount.toString());
    expect(hasAmount, `Checkout page should show amount ${expectedAmount}`).toBe(true);

    // The Pay button must be present and actionable.
    const payButton = page.getByTestId('hosted-payment-submit-button');
    await expect(payButton).toBeVisible({ timeout: 10000 });

    console.log('[StripeCheckout] Checkout page content verified');
}

async function fillStripeCheckoutAndPay(page: Page, email: string): Promise<void> {
    console.log('[StripeCheckout] Waiting for Stripe checkout page to load...');
    await page.waitForLoadState('networkidle');

    // Verify the checkout page shows the correct amount and details.
    await assertStripeCheckoutContent(page, PART_UNIT_PRICE);

    // Fill email if the field is visible and empty.
    const emailInput = page.getByLabel('Email');
    try {
        await emailInput.waitFor({ state: 'visible', timeout: 10000 });
        const currentValue = await emailInput.inputValue();
        if (!currentValue) {
            await emailInput.fill(email);
            console.log('[StripeCheckout] Filled email field');
        } else {
            console.log(`[StripeCheckout] Email already filled: ${currentValue}`);
        }
    } catch {
        console.warn('[StripeCheckout] Email field not found — may be pre-filled');
    }

    // ── Expand the "Card" payment accordion ────────────────────────────────────
    // The card input fields may already be visible when Card is the default method.
    const cardNumberInput = page.locator('input[name="cardNumber"]').or(
        page.getByRole('textbox', { name: 'Card number' }),
    ).first();
    if (!(await cardNumberInput.isVisible())) {
        console.log('[StripeCheckout] Expanding Card payment accordion...');
        const cardAccordion = page.getByTestId('card-accordion-item');
        await expect(cardAccordion).toBeVisible({ timeout: 10000 });
        await cardAccordion.click({ timeout: 10000 });
    } else {
        console.log('[StripeCheckout] Card fields already expanded');
    }

    await expect(cardNumberInput).toBeVisible({ timeout: 10000 });

    // ── Fill card number ────────────────────────────────────────────────────────
    console.log('[StripeCheckout] Filling card number...');
    await cardNumberInput.fill(STRIPE_TEST_CARD_NUMBER, { timeout: 10000 });

    // ── Fill expiry date ────────────────────────────────────────────────────────
    console.log('[StripeCheckout] Filling expiry...');
    await page.locator('input[name="cardExpiry"]').or(
        page.getByRole('textbox', { name: 'Expiration' }),
    ).first().fill(STRIPE_TEST_CARD_EXPIRY, { timeout: 10000 });

    // ── Fill CVC ────────────────────────────────────────────────────────────────
    // Use getByRole('textbox') to avoid matching the SVG icon which also has an
    // aria-label containing "CVC".
    console.log('[StripeCheckout] Filling CVC...');
    await page.locator('input[name="cardCvc"]').or(
        page.getByRole('textbox', { name: 'CVC' }),
    ).first().fill(STRIPE_TEST_CARD_CVC, { timeout: 10000 });

    // Cardholder name (if present — some configurations require it).
    const nameInput = page.getByLabel('Cardholder name');
    try {
        await nameInput.waitFor({ state: 'visible', timeout: 3000 });
        await nameInput.fill('E2E Test Customer');
    } catch {
        // Not required — ignore.
    }

    // Postal code (if present).
    const postalInput = page.getByLabel('Postal code');
    try {
        await postalInput.waitFor({ state: 'visible', timeout: 3000 });
        await postalInput.fill('12345');
    } catch {
        // Not required — ignore.
    }

    // Click the Pay button.
    // Use the specific data-testid to avoid matching the "Pay with card" accordion button.
    console.log('[StripeCheckout] Clicking Pay button...');
    await page.getByTestId('hosted-payment-submit-button').click();

    console.log('[StripeCheckout] Payment submitted, waiting for redirect...');
}

/**
 * Polls the contract state until it reaches the expected state or times out.
 */
async function waitForContractState(
    page: Page,
    contractId: string,
    expectedState: string,
    timeoutMs: number = 60000,
): Promise<void> {
    const deadline = Date.now() + timeoutMs;
    let lastStatus = 0;
    let lastState = 'unknown';
    while (Date.now() < deadline) {
        const resp = await page.request.get(`/api/public/sales/contracts/${contractId}`);
        lastStatus = resp.status();
        if (resp.ok()) {
            const contract = await resp.json();
            lastState = contract.state;
            console.log(`[Poll] Contract ${contractId} state: ${contract.state} (status=${lastStatus})`);
            if (contract.state === expectedState) {
                return;
            }
        } else {
            console.log(`[Poll] GET contract ${contractId} returned status=${lastStatus}`);
        }
        await page.waitForTimeout(2000);
    }
    throw new Error(`Contract ${contractId} did not reach state ${expectedState} within ${timeoutMs}ms (lastStatus=${lastStatus}, lastState=${lastState})`);
}

// ─── Test ──────────────────────────────────────────────────────────────────────

test.describe('05 Payment Flow', () => {

    const timestamp = RUN_ID;
    const newUserEmail = `e2e-payment-${timestamp}@example.com`;
    const newUserPassword = 'secretLong123!';
    let sellerOrgId: string;

    test.beforeEach(async ({ page }: { page: Page }) => {
        // Fetch the webhook secret from the helper if not already set.
        if (!stripeWebhookSecret) {
            stripeWebhookSecret = await fetchStripeWebhookSecret();
        }

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
        for (const testId of ['PF1', 'PF2', 'PF3']) {
            await cleanupProduct(page, sellerOrgId, productCodeFor(testId));
        }
    });

    /**
     * PF1: Full payment flow — create contract, offer, accept (get real checkout URL),
     * navigate to Stripe's hosted checkout page, complete payment with test card,
     * verify the real webhook (forwarded by Stripe CLI) transitions the contract to RUNNING.
     *
     * Prerequisites:
     * - Stripe CLI running: stripe listen --forward-to localhost:8088/public/payment/webhook
     * - STRIPE_TEST_SECRET_KEY env var set (Stripe test mode API key)
     * - STRIPE_TEST_WEBHOOK_SECRET env var set (from stripe listen output)
     */
    test('PF1: real Stripe checkout payment transitions contract to RUNNING', async ({ page }: { page: Page }) => {
        if (!STRIPE_TEST_SECRET_KEY) {
            throw new Error(
                'STRIPE_TEST_SECRET_KEY (or STRIPE_API_KEY) env var is not set. ' +
                'Set it with: export STRIPE_API_KEY=sk_test_...\n' +
                'Get the key from Stripe Dashboard → Developers → API Keys (test mode).'
            );
        }
        if (!stripeWebhookSecret) {
            throw new Error(
                'Stripe webhook secret is not available. ' +
                'Start the helper script in a separate terminal:\n' +
                '  node e2e-tests/start-stripe-cli.js\n' +
                'This script starts "stripe listen", captures the whsec_... secret, ' +
                'and serves it on http://localhost:19997/webhook-secret for the tests to fetch.'
            );
        }

        // The full flow (auth, product creation, Stripe checkout, webhook) takes time.
        test.setTimeout(180_000);
        const log = testStepLogger('PF1');

        // ── Step 1: create the cross-tenant product as the seller user ──────────
        const productCode = productCodeFor('PF1');
        const partCode = partCodeFor('PF1');
        log('Create product with real Stripe credentials as seller user');
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

        // ── Step 5: accept the contract → AWAITING_PAYMENT + real checkoutUrl ───
        log('Accept the contract — should return real Stripe checkoutUrl');
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

        // Verify the checkout URL points to the real Stripe checkout page.
        expect(acceptJson.checkoutUrl).toContain('checkout.stripe.com');

        // ── Step 6: navigate to Stripe's hosted checkout page and pay ───────────
        log('Navigate to Stripe checkout page and complete payment');
        await page.goto(acceptJson.checkoutUrl);
        await fillStripeCheckoutAndPay(page, newUserEmail);

        // ── Step 7: wait for Stripe to redirect back to abstrapact's success endpoint
        // Stripe redirects the browser after payment processing. The redirect is part
        // of the real user experience — we must verify it works, not just the webhook.
        log('Wait for Stripe redirect to abstrapact success endpoint');
        console.log('[PF1] Waiting for redirect from Stripe...');

        // Poll the browser URL until it leaves checkout.stripe.com (up to 30s).
        const redirectDeadline = Date.now() + 30000;
        let finalUrl = page.url();
        while (Date.now() < redirectDeadline && finalUrl.includes('checkout.stripe.com')) {
            await page.waitForTimeout(500);
            finalUrl = page.url();
        }

        console.log(`[PF1] Final URL after redirect: ${finalUrl}`);

        // Assert the browser was redirected to our success endpoint (not stuck on Stripe
        // or on a browser error page).
        expect(finalUrl, 'Browser should leave Stripe checkout page')
            .not.toContain('checkout.stripe.com');
        expect(finalUrl, 'Browser should land on abstrapact success endpoint')
            .toContain('/public/payment/success');
        expect(finalUrl, 'Success URL should contain session_id')
            .toContain('session_id=');

        // Verify the success page loaded and shows a success message (not an error
        // like ERR_CONNECTION_REFUSED or a 404).
        const pageContent = await page.content();
        expect(pageContent, 'Success page should show payment successful')
            .toContain('Payment successful');
        console.log('[PF1] Success page loaded correctly');

        // ── Step 8: poll contract state until RUNNING ───────────────────────────
        // The Stripe CLI forwards the webhook asynchronously. The contract may
        // already be RUNNING (if the webhook was processed before the redirect)
        // or may still be AWAITING_PAYMENT (if the webhook is still in flight).
        log('Poll contract state until RUNNING');
        await waitForContractState(page, contractId, 'RUNNING', 60000);

        console.log(`[PF1] Payment flow completed successfully: id=${contractId}, state=RUNNING`);
    });

    /**
     * PF2: Webhook with invalid signature returns 400 and does not transition the contract.
     * This test does not require the Stripe CLI — it sends a manually constructed
     * webhook payload with an invalid signature directly to the webhook endpoint.
     */
    test('PF2: webhook with invalid signature returns 400 and does not transition', async ({ page }: { page: Page }) => {
        if (!STRIPE_TEST_SECRET_KEY) {
            throw new Error(
                'STRIPE_TEST_SECRET_KEY (or STRIPE_API_KEY) env var is not set. ' +
                'Set it with: export STRIPE_API_KEY=sk_test_...\n' +
                'Get the key from Stripe Dashboard → Developers → API Keys (test mode).'
            );
        }
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

        const offerResp = await page.request.post(`/api/public/sales/contracts/${contractId}/offer`, {
            headers: await getXsrfHeader(page),
        });
        expect(offerResp.status(), `Offer failed: ${await offerResp.text()}`).toBe(200);

        const acceptResp = await page.request.post(`/api/public/sales/contracts/${contractId}/accept`, {
            headers: await getXsrfHeader(page),
        });
        expect(acceptResp.status()).toBe(200);
        const acceptJson = await acceptResp.json();
        expect(acceptJson.checkoutUrl).toContain('checkout.stripe.com');

        // Extract the real Stripe session ID from the checkout URL.
        // The URL format is: https://checkout.stripe.com/c/pay/cs_test_...#fid...
        const urlMatch = acceptJson.checkoutUrl.match(/(cs_test_[A-Za-z0-9]+)/);
        expect(urlMatch, 'Could not extract session ID from checkout URL').not.toBeNull();
        const sessionId = urlMatch![1];
        console.log(`[PF2] Extracted session ID: ${sessionId}`);

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
                    metadata: { correlation_id: 'invalid-correlation-id' },
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

    /**
     * PF3: session expiry + retry-payment — create contract, offer, accept (real
     * checkout URL), expire the session on Stripe's side, wait for the forwarded
     * checkout.session.expired webhook to mark the transaction EXPIRED, then call
     * POST /{id}/retry-payment and expect a NEW checkout session.
     *
     * Prerequisites: same as PF1 (stripe CLI forwarding + STRIPE_API_KEY).
     */
    test('PF3: expired checkout session can be retried via retry-payment', async ({ page }: { page: Page }) => {
        if (!STRIPE_TEST_SECRET_KEY) {
            throw new Error(
                'STRIPE_TEST_SECRET_KEY (or STRIPE_API_KEY) env var is not set. ' +
                'Set it with: export STRIPE_API_KEY=sk_test_...\n' +
                'Get the key from Stripe Dashboard → Developers → API Keys (test mode).'
            );
        }
        if (!stripeWebhookSecret) {
            throw new Error(
                'Stripe webhook secret is not available. ' +
                'Start the helper: node e2e-tests/start-stripe-cli.js'
            );
        }
        const log = testStepLogger('PF3');

        // ── Setup: create product, register customer, create + offer + accept ──
        const productCode = productCodeFor('PF3');
        const partCode = partCodeFor('PF3');
        log('Create product and set up contract');
        await createCrossTenantProduct(page, productCode, partCode);

        await page.goto('/');
        await signOut(page);
        await headerSignInLink(page).click();
        await page.waitForURL(/auth-t\.abstratium\.dev\/signin\//, { timeout: 15000 });

        const pf3Email = `e2e-pf3-${timestamp}@example.com`;
        await registerNewUser(page, {
            email: pf3Email,
            fullName: `E2E PF3 ${timestamp}`,
            orgName: `E2E PF3 Org ${timestamp}`,
            password: newUserPassword,
        });
        await handleAuthServer(page, pf3Email, newUserPassword);
        await expect(page.locator('#signout-link')).toBeVisible({ timeout: 15000 });

        const createResp = await page.request.post('/api/public/sales/contracts', {
            headers: await getXsrfHeader(page),
            data: {
                orgId: sellerOrgId,
                contractReference: `E2E-PF3-${timestamp}`,
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

        const offerResp = await page.request.post(`/api/public/sales/contracts/${contractId}/offer`, {
            headers: await getXsrfHeader(page),
        });
        expect(offerResp.status(), `Offer failed: ${await offerResp.text()}`).toBe(200);

        const acceptResp = await page.request.post(`/api/public/sales/contracts/${contractId}/accept`, {
            headers: await getXsrfHeader(page),
        });
        expect(acceptResp.status(), `Accept failed: ${await acceptResp.text()}`).toBe(200);
        const acceptJson = await acceptResp.json();
        const sessionMatch = acceptJson.checkoutUrl.match(/(cs_test_[A-Za-z0-9]+)/);
        expect(sessionMatch, 'Could not extract session ID from checkout URL').not.toBeNull();
        const sessionId = sessionMatch![1];
        console.log(`[PF3] Initial session: ${sessionId}`);

        // ── Expire the session on Stripe's side ───────────────────────────────
        log('Expire the checkout session via Stripe API');
        const expireResp = await page.request.post(
            `https://api.stripe.com/v1/checkout/sessions/${sessionId}/expire`,
            { headers: { 'Authorization': `Bearer ${STRIPE_TEST_SECRET_KEY}` } },
        );
        expect(expireResp.status(), `Expire session failed: ${await expireResp.text()}`).toBe(200);
        console.log(`[PF3] Session ${sessionId} expired on Stripe, waiting for webhook`);

        // ── Poll retry-payment until the expired webhook lands and a NEW session
        //    is created (while the tx is still PENDING it returns the old URL) ──
        log('Poll retry-payment until a new session is issued');
        let newSessionId: string | null = null;
        const deadline = Date.now() + 60000;
        while (Date.now() < deadline) {
            const retryResp = await page.request.post(`/api/public/sales/contracts/${contractId}/retry-payment`, {
                headers: await getXsrfHeader(page),
            });
            expect(retryResp.status(), `Retry-payment failed: ${await retryResp.text()}`).toBe(200);
            const retryJson = await retryResp.json();
            const m = (retryJson.checkoutUrl || '').match(/(cs_test_[A-Za-z0-9]+)/);
            if (m && m[1] !== sessionId) {
                newSessionId = m[1];
                break;
            }
            await page.waitForTimeout(2000);
        }
        expect(newSessionId,
            'retry-payment should return a NEW session after the old one expired').not.toBeNull();
        console.log(`[PF3] retry-payment returned new session: ${newSessionId}`);

        const getResp = await page.request.get(`/api/public/sales/contracts/${contractId}`);
        const finalContract = await getResp.json();
        expect(finalContract.state, 'Contract must remain AWAITING_PAYMENT').toBe('AWAITING_PAYMENT');
        console.log(`[PF3] Contract still AWAITING_PAYMENT with fresh session ${newSessionId}`);
    });
});
