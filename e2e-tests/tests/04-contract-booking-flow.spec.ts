import { test, expect, Page } from '@playwright/test';
import { signInViaHeader, testStepLogger } from '../pages/test-helpers';
import { handleAuthServer, headerSignInLink, signOut } from '../pages/TODO.page';
import { registerNewUser } from '../pages/auth-server.page';

// ─── Constants ─────────────────────────────────────────────────────────────────

const RUN_ID = Date.now().toString();
const PART_UNIT_PRICE = 25.00;

function productCodeFor(testId: string): string {
    return `BK-PROD-${RUN_ID}-${testId}`;
}

function partCodeFor(testId: string): string {
    return `BK-PART-${RUN_ID}-${testId}`;
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
    if (lookup.status() === 404) {
        console.log(`[TestHelper] Product '${productCode}' not found, nothing to clean up`);
        return;
    }
    if (!lookup.ok()) {
        console.log(`[TestHelper] Could not look up '${productCode}': ${lookup.status()}`);
        return;
    }
    const product = await lookup.json();
    const del = await page.request.delete(`/api/product-definitions/${product.id}/complete`);
    if (del.ok()) {
        console.log(`[TestHelper] Deleted product '${productCode}' (id=${product.id})`);
    } else {
        console.log(`[TestHelper] Failed to delete product '${productCode}': ${del.status()}`);
    }
}

/**
 * Create a product definition with crossTenantApiAllowed=true and one part via the REST API.
 * Relies on the current browser session (signed in as the seller org user).
 */
async function createCrossTenantProduct(page: Page, productCode: string, partCode: string): Promise<string> {
    console.log(`[TestHelper] Creating cross-tenant product '${productCode}'`);
    const resp = await page.request.post('/api/product-definitions', {
        data: {
            productCode: productCode,
            description: 'E2E booking flow test product',
            billingModel: 'FIXED_PRICE',
            paymentModel: 'PREPAID',
            crossTenantApiAllowed: true,
            stripeSecretKey: 'sk_test_e2e_mock',
            stripeWebhookSecret: 'whsec_e2e_mock',
        },
    });
    expect(resp.status(), `Create product failed: ${resp.status()}`).toBe(201);
    const product = await resp.json();
    console.log(`[TestHelper] Product created, id=${product.id}`);

    const partResp = await page.request.post(`/api/product-definitions/${product.id}/parts`, {
        data: {
            partCode: partCode,
            description: 'E2E booking flow part',
            unitPrice: PART_UNIT_PRICE,
            minCardinality: 1,
            maxCardinality: 1,
        },
    });
    expect(partResp.status(), `Create part failed: ${partResp.status()}`).toBe(201);
    const part = await partResp.json();
    console.log(`[TestHelper] Part created, id=${part.id}`);

    return product.id;
}

async function getXsrfHeader(page: Page): Promise<Record<string, string>> {
    const cookies = await page.context().cookies();
    const xsrfToken = cookies.find(c => c.name === 'XSRF-TOKEN');
    return xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken.value } : {};
}

// ─── Test ──────────────────────────────────────────────────────────────────────

test.describe('04 Contract Booking Flow', () => {

    const timestamp = RUN_ID;
    const newUserEmail = `e2e-booking-${timestamp}@example.com`;
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

        // Sign in as the main (seller) user to clean up and set up product data.
        await page.goto('/');
        await signInViaHeader(page);
        sellerOrgId = await resolveSellerOrgId(page);
        // Clean up products from all tests in this file.
        for (const testId of ['BK1', 'BK2', 'BK3']) {
            await cleanupProduct(page, sellerOrgId, productCodeFor(testId));
        }
    });

    test('BK1: customer creates, offers, and accepts a contract with correct pricing', async ({ page }: { page: Page }) => {
        const log = testStepLogger('BK1');

        // ── Step 1: create the cross-tenant product as the seller user ──────────
        const productCode = productCodeFor('BK1');
        const partCode = partCodeFor('BK1');

        log('Create product with crossTenantApiAllowed=true as seller user');
        await createCrossTenantProduct(page, productCode, partCode);

        // ── Step 2: sign out seller, register + sign in as a new customer ───────
        log('Navigate back to app and sign out the seller user');
        await page.goto('/');
        await signOut(page);

        log('Trigger OIDC flow – lands on auth server sign-in page');
        await headerSignInLink(page).click();
        await page.waitForURL(/auth-t\.abstratium\.dev\/signin\//, { timeout: 15000 });
        console.log(`[BK1] On auth server sign-in page: ${page.url()}`);

        log(`Register new user from sign-in page: ${newUserEmail}`);
        await registerNewUser(page, {
            email: newUserEmail,
            fullName: `E2E Booking ${timestamp}`,
            orgName: `E2E Booking Org ${timestamp}`,
            password: newUserPassword,
        });

        log('Complete sign-in for the newly registered user');
        await handleAuthServer(page, newUserEmail, newUserPassword);

        log('Verify new user is signed in');
        await expect(page.locator('#signout-link')).toBeVisible({ timeout: 15000 });
        console.log(`[BK1] New user signed in, current URL: ${page.url()}`);

        // ── Step 3: POST /api/public/sales/contracts — create draft ────────────
        log('Create draft contract via POST /api/public/sales/contracts');
        const contractRequest = {
            orgId: sellerOrgId,
            contractReference: `E2E-BK-${timestamp}`,
            publicNotes: 'Created by e2e test BK1',
            lineItems: [
                {
                    productCode: productCode,
                    displayOrder: 1,
                    partInstances: [
                        {
                            partCode: partCode,
                            attributeValues: [],
                            childPartInstances: [],
                        },
                    ],
                },
            ],
        };
        console.log(`[BK1] Contract request:\n${JSON.stringify(contractRequest, null, 2)}`);

        const createResp = await page.request.post('/api/public/sales/contracts', {
            headers: await getXsrfHeader(page),
            data: contractRequest,
        });

        const createBody = await createResp.text();
        const prettyCreate = (() => { try { return JSON.stringify(JSON.parse(createBody), null, 2); } catch { return createBody; } })();
        console.log(`[BK1] Create response status: ${createResp.status()}\n${prettyCreate}`);
        expect(createResp.status(), `Expected 201 Created but got ${createResp.status()}. Body: ${createBody}`).toBe(201);

        const contract = JSON.parse(createBody);
        const contractId = contract.id;

        expect(contract.id, 'Contract id must be present').toBeTruthy();
        expect(contract.state, 'Contract state must be DRAFT').toBe('DRAFT');
        expect(contract.sellerOrganisationId, 'sellerOrganisationId must match the seller org').toBe(sellerOrgId);
        expect(contract.contractReference, 'contractReference must match').toBe(`E2E-BK-${timestamp}`);
        expect(contract.lineItems, 'lineItems must be an array').toBeInstanceOf(Array);
        expect(contract.lineItems.length, 'lineItems must contain exactly one item').toBe(1);

        // ── Step 4: verify price calculation ────────────────────────────────────
        log('Verify price calculation: grandTotal and lineTotal match part unitPrice');
        const lineItem = contract.lineItems[0];
        expect(lineItem.id, 'Line item id must be present').toBeTruthy();
        expect(lineItem.displayOrder, 'Line item displayOrder must be 1').toBe(1);
        expect(lineItem.productInstance, 'Line item productInstance must be present').toBeTruthy();

        const expectedTotal = PART_UNIT_PRICE;
        expect(Number(contract.grandTotal), `grandTotal must be ${expectedTotal}`).toBe(expectedTotal);
        expect(Number(lineItem.lineTotal), `lineTotal must be ${expectedTotal}`).toBe(expectedTotal);
        console.log(`[BK1] Price verified: grandTotal=${contract.grandTotal}, lineTotal=${lineItem.lineTotal}`);

        // ── Step 5: GET /api/public/sales/contracts/{id} — verify draft state ───
        log('GET contract by id to verify DRAFT state');
        const getResp = await page.request.get(`/api/public/sales/contracts/${contractId}`);
        expect(getResp.status(), `GET contract failed: ${getResp.status()}`).toBe(200);
        const fetchedContract = await getResp.json();
        expect(fetchedContract.state, 'Fetched contract state must be DRAFT').toBe('DRAFT');
        expect(fetchedContract.id, 'Fetched contract id must match').toBe(contractId);

        // ── Step 6: POST /api/public/sales/contracts/{id}/offer — DRAFT → OFFERED
        log('Offer the contract via POST /api/public/sales/contracts/{id}/offer');
        const offerResp = await page.request.post(`/api/public/sales/contracts/${contractId}/offer`, {
            headers: await getXsrfHeader(page),
        });
        const offerBody = await offerResp.text();
        console.log(`[BK1] Offer response status: ${offerResp.status()}\n${offerBody}`);
        expect(offerResp.status(), `Expected 200 but got ${offerResp.status()}. Body: ${offerBody}`).toBe(200);

        log('GET contract by id to verify OFFERED state');
        const getAfterOffer = await page.request.get(`/api/public/sales/contracts/${contractId}`);
        expect(getAfterOffer.status(), `GET contract after offer failed: ${getAfterOffer.status()}`).toBe(200);
        const offeredContract = await getAfterOffer.json();
        expect(offeredContract.state, 'Contract state must be OFFERED after offer').toBe('OFFERED');
        console.log(`[BK1] Contract is now OFFERED`);

        // ── Step 7: POST /api/public/sales/contracts/{id}/accept — OFFERED → AWAITING_PAYMENT
        log('Accept the contract via POST /api/public/sales/contracts/{id}/accept');
        const acceptResp = await page.request.post(`/api/public/sales/contracts/${contractId}/accept`, {
            headers: await getXsrfHeader(page),
        });
        const acceptBody = await acceptResp.text();
        console.log(`[BK1] Accept response status: ${acceptResp.status()}\n${acceptBody}`);
        expect(acceptResp.status(), `Expected 200 but got ${acceptResp.status()}. Body: ${acceptBody}`).toBe(200);

        // Accept now returns a checkoutUrl for prepaid contracts (auto-approval + payment).
        const acceptJson = JSON.parse(acceptBody);
        expect(acceptJson.checkoutUrl, 'Accept response must contain checkoutUrl').toBeTruthy();
        console.log(`[BK1] Checkout URL: ${acceptJson.checkoutUrl}`);

        log('GET contract by id to verify AWAITING_PAYMENT state');
        const getAfterAccept = await page.request.get(`/api/public/sales/contracts/${contractId}`);
        expect(getAfterAccept.status(), `GET contract after accept failed: ${getAfterAccept.status()}`).toBe(200);
        const acceptedContract = await getAfterAccept.json();
        expect(acceptedContract.state, 'Contract state must be AWAITING_PAYMENT after accept').toBe('AWAITING_PAYMENT');
        console.log(`[BK1] Contract is now AWAITING_PAYMENT`);

        // ── Step 8: GET /api/public/sales/contracts — list must contain the contract
        log('List contracts via GET /api/public/sales/contracts');
        const listResp = await page.request.get('/api/public/sales/contracts');
        expect(listResp.status(), `List contracts failed: ${listResp.status()}`).toBe(200);
        const contracts = await listResp.json();
        expect(contracts, 'Contracts list must be an array').toBeInstanceOf(Array);
        const found = contracts.find((c: any) => c.id === contractId);
        expect(found, 'Contract must appear in the list').toBeTruthy();
        expect(found.state, 'Listed contract state must be AWAITING_PAYMENT').toBe('AWAITING_PAYMENT');
        expect(found.contractReference, 'Listed contractReference must match').toBe(`E2E-BK-${timestamp}`);
        expect(Number(found.grandTotal), 'Listed grandTotal must match').toBe(expectedTotal);
        console.log(`[BK1] Contract found in list with state=${found.state}`);

        console.log(`[BK1] Booking flow completed successfully: id=${contractId}, state=AWAITING_PAYMENT`);
    });

    test('BK2: cannot accept a DRAFT contract (must be offered first)', async ({ page }: { page: Page }) => {
        const log = testStepLogger('BK2');

        log('Create product and sign in as customer');
        const bk2ProductCode = productCodeFor('BK2');
        const bk2PartCode = partCodeFor('BK2');
        await createCrossTenantProduct(page, bk2ProductCode, bk2PartCode);

        await page.goto('/');
        await signOut(page);
        await headerSignInLink(page).click();
        await page.waitForURL(/auth-t\.abstratium\.dev\/signin\//, { timeout: 15000 });

        const bk2Email = `e2e-bk2-${timestamp}@example.com`;
        await registerNewUser(page, {
            email: bk2Email,
            fullName: `E2E BK2 ${timestamp}`,
            orgName: `E2E BK2 Org ${timestamp}`,
            password: newUserPassword,
        });
        await handleAuthServer(page, bk2Email, newUserPassword);
        await expect(page.locator('#signout-link')).toBeVisible({ timeout: 15000 });

        log('Create a draft contract');
        const contractResp = await page.request.post('/api/public/sales/contracts', {
            headers: await getXsrfHeader(page),
            data: {
                orgId: sellerOrgId,
                contractReference: `E2E-BK2-${timestamp}`,
                publicNotes: 'BK2 negative test',
                lineItems: [
                    {
                        productCode: bk2ProductCode,
                        displayOrder: 1,
                        partInstances: [
                            {
                                partCode: bk2PartCode,
                                attributeValues: [],
                                childPartInstances: [],
                            },
                        ],
                    },
                ],
            },
        });
        expect(contractResp.status(), `Create failed: ${contractResp.status()}`).toBe(201);
        const contract = await contractResp.json();
        expect(contract.state, 'Contract must be DRAFT').toBe('DRAFT');

        log('Attempt to accept a DRAFT contract — should return 422');
        const acceptResp = await page.request.post(`/api/public/sales/contracts/${contract.id}/accept`, {
            headers: await getXsrfHeader(page),
        });
        console.log(`[BK2] Accept DRAFT response status: ${acceptResp.status()}`);
        expect(acceptResp.status(), `Expected 422 but got ${acceptResp.status()}`).toBe(422);

        log('Verify contract is still DRAFT');
        const getResp = await page.request.get(`/api/public/sales/contracts/${contract.id}`);
        const fetched = await getResp.json();
        expect(fetched.state, 'Contract must still be DRAFT').toBe('DRAFT');
    });

    test('BK3: cannot offer an already-accepted contract', async ({ page }: { page: Page }) => {
        const log = testStepLogger('BK3');

        log('Create product and sign in as customer');
        const bk3ProductCode = productCodeFor('BK3');
        const bk3PartCode = partCodeFor('BK3');
        await createCrossTenantProduct(page, bk3ProductCode, bk3PartCode);

        await page.goto('/');
        await signOut(page);
        await headerSignInLink(page).click();
        await page.waitForURL(/auth-t\.abstratium\.dev\/signin\//, { timeout: 15000 });

        const bk3Email = `e2e-bk3-${timestamp}@example.com`;
        await registerNewUser(page, {
            email: bk3Email,
            fullName: `E2E BK3 ${timestamp}`,
            orgName: `E2E BK3 Org ${timestamp}`,
            password: newUserPassword,
        });
        await handleAuthServer(page, bk3Email, newUserPassword);
        await expect(page.locator('#signout-link')).toBeVisible({ timeout: 15000 });

        log('Create and advance contract through DRAFT → OFFERED → ACCEPTED');
        const createResp = await page.request.post('/api/public/sales/contracts', {
            headers: await getXsrfHeader(page),
            data: {
                orgId: sellerOrgId,
                contractReference: `E2E-BK3-${timestamp}`,
                publicNotes: 'BK3 negative test',
                lineItems: [
                    {
                        productCode: bk3ProductCode,
                        displayOrder: 1,
                        partInstances: [
                            {
                                partCode: bk3PartCode,
                                attributeValues: [],
                                childPartInstances: [],
                            },
                        ],
                    },
                ],
            },
        });
        expect(createResp.status(), `Create failed: ${createResp.status()}`).toBe(201);
        const contract = await createResp.json();
        const contractId = contract.id;

        const offerResp = await page.request.post(`/api/public/sales/contracts/${contractId}/offer`, {
            headers: await getXsrfHeader(page),
        });
        expect(offerResp.status(), `Offer failed: ${offerResp.status()}`).toBe(200);

        const acceptResp = await page.request.post(`/api/public/sales/contracts/${contractId}/accept`, {
            headers: await getXsrfHeader(page),
        });
        expect(acceptResp.status(), `Accept failed: ${acceptResp.status()}`).toBe(200);

        log('Attempt to offer the AWAITING_PAYMENT contract — should return 422');
        const reOfferResp = await page.request.post(`/api/public/sales/contracts/${contractId}/offer`, {
            headers: await getXsrfHeader(page),
        });
        console.log(`[BK3] Re-offer AWAITING_PAYMENT response status: ${reOfferResp.status()}`);
        expect(reOfferResp.status(), `Expected 422 but got ${reOfferResp.status()}`).toBe(422);

        log('Verify contract is still AWAITING_PAYMENT');
        const getResp = await page.request.get(`/api/public/sales/contracts/${contractId}`);
        const fetched = await getResp.json();
        expect(fetched.state, 'Contract must still be AWAITING_PAYMENT').toBe('AWAITING_PAYMENT');
    });

});
