import { test, expect, Page } from '@playwright/test';
import { signInViaHeader, testStepLogger } from '../pages/test-helpers';
import { handleAuthServer, headerSignInLink, signOut } from '../pages/TODO.page';
import { registerNewUser } from '../pages/auth-server.page';

// ─── Constants ─────────────────────────────────────────────────────────────────

const RUN_ID = Date.now().toString();

function productCodeFor(testId: string): string {
    return `CT-PROD-${RUN_ID}-${testId}`;
}

function partCodeFor(testId: string): string {
    return `CT-PART-${RUN_ID}-${testId}`;
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
            description: 'E2E cross-tenant contract test product',
            billingModel: 'FIXED_PRICE',
            paymentModel: 'PREPAID',
            crossTenantApiAllowed: true,
        },
    });
    expect(resp.status(), `Create product failed: ${resp.status()}`).toBe(201);
    const product = await resp.json();
    console.log(`[TestHelper] Product created, id=${product.id}`);

    const partResp = await page.request.post(`/api/product-definitions/${product.id}/parts`, {
        data: {
            partCode: partCode,
            description: 'E2E cross-tenant part',
            unitPrice: 25.00,
            minCardinality: 1,
            maxCardinality: 1,
        },
    });
    expect(partResp.status(), `Create part failed: ${partResp.status()}`).toBe(201);
    const part = await partResp.json();
    console.log(`[TestHelper] Part created, id=${part.id}`);

    return product.id;
}

// ─── Test ──────────────────────────────────────────────────────────────────────

test.describe('03 Customer Contract Creation', () => {

    const timestamp = RUN_ID;
    const newUserEmail = `e2e-customer-${timestamp}@example.com`;
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
        for (const testId of ['CC1', 'CC2', 'CC3', 'CC4']) {
            await cleanupProduct(page, sellerOrgId, productCodeFor(testId));
        }
    });

    test('CC1: new customer registers, signs in, and creates a draft contract', async ({ page }: { page: Page }) => {
        const log = testStepLogger('CC1');

        // ── Step 1: create the cross-tenant product as the seller user ──────────
        const productCode = productCodeFor('CC1');
        const partCode = partCodeFor('CC1');

        log('Create product with crossTenantApiAllowed=true as seller user');
        await createCrossTenantProduct(page, productCode, partCode);

        // ── Step 2 / Step 3: sign out seller, trigger OIDC, register + sign in ──
        log('Navigate back to app and sign out the seller user');
        await page.goto('/');
        await signOut(page);

        log('Trigger OIDC flow – lands on auth server sign-in page');
        await headerSignInLink(page).click();
        await page.waitForURL(/auth-t\.abstratium\.dev\/signin\//, { timeout: 15000 });
        console.log(`[CC1] On auth server sign-in page: ${page.url()}`);

        log(`Register new user from sign-in page: ${newUserEmail}`);
        await registerNewUser(page, {
            email: newUserEmail,
            fullName: `E2E Customer ${timestamp}`,
            orgName: `E2E Org ${timestamp}`,
            password: newUserPassword,
        });

        log('Complete sign-in for the newly registered user');
        await handleAuthServer(page, newUserEmail, newUserPassword);

        log('Verify new user is signed in');
        await expect(page.locator('#signout-link')).toBeVisible({ timeout: 15000 });
        console.log(`[CC1] New user signed in, current URL: ${page.url()}`);

        // ── Step 4: POST /api/public/sales/contracts as the new customer user ─────────
        log('Fetch CSRF token for the new user session');
        const csrfCookie = await page.context().cookies();
        const xsrfToken = csrfCookie.find(c => c.name === 'XSRF-TOKEN');
        console.log(`[CC1] XSRF-TOKEN cookie present: ${!!xsrfToken}`);

        log('Create draft contract via POST /api/public/sales/contracts');
        const contractRequest = {
            orgId: sellerOrgId,
            contractReference: `E2E-REF-${timestamp}`,
            publicNotes: 'Created by e2e test CC1',
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
        console.log(`[CC1] Contract request:\n${JSON.stringify(contractRequest, null, 2)}`);
        const contractResp = await page.request.post('/api/public/sales/contracts', {
            headers: xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken.value } : {},
            data: contractRequest,
        });

        log('Assert contract was created successfully (HTTP 201)');
        const responseBody = await contractResp.text();
        const prettyResponse = (() => { try { return JSON.stringify(JSON.parse(responseBody), null, 2); } catch { return responseBody; } })();
        console.log(`[CC1] Contract response status: ${contractResp.status()}\n${prettyResponse}`);
        expect(contractResp.status(), `Expected 201 Created but got ${contractResp.status()}. Body: ${responseBody}`).toBe(201);

        // ── Step 5: assert response body shape ──────────────────────────────────
        log('Assert response body is a valid CustomerContractResponse');
        const contract = JSON.parse(responseBody);

        expect(contract.id, 'Contract id must be present').toBeTruthy();
        expect(contract.state, 'Contract state must be DRAFT').toBe('DRAFT');
        expect(contract.sellerOrganisationId, 'sellerOrganisationId must match the seller org').toBe(sellerOrgId);
        expect(contract.contractReference, 'contractReference must match').toBe(`E2E-REF-${timestamp}`);
        expect(contract.publicNotes, 'publicNotes must match').toBe('Created by e2e test CC1');
        expect(contract.lineItems, 'lineItems must be an array').toBeInstanceOf(Array);
        expect(contract.lineItems.length, 'lineItems must contain exactly one item').toBe(1);

        const lineItem = contract.lineItems[0];
        expect(lineItem.id, 'Line item id must be present').toBeTruthy();
        expect(lineItem.displayOrder, 'Line item displayOrder must be 1').toBe(1);
        expect(lineItem.productInstance, 'Line item productInstance must be present').toBeTruthy();

        console.log(`[CC1] Contract created successfully: id=${contract.id}, state=${contract.state}`);
    });

    test('CC2: contract creation with non-existent product code is rejected', async ({ page }: { page: Page }) => {
        const log = testStepLogger('CC2');

        // CC2 tests a non-existent product code — no need to create a real product.
        log('Sign out seller and register + sign in as a new customer');
        await page.goto('/');
        await signOut(page);
        await headerSignInLink(page).click();
        await page.waitForURL(/auth-t\.abstratium\.dev\/signin\//, { timeout: 15000 });

        const cc2Email = `e2e-cc2-${timestamp}@example.com`;
        await registerNewUser(page, {
            email: cc2Email,
            fullName: `E2E CC2 ${timestamp}`,
            orgName: `E2E CC2 Org ${timestamp}`,
            password: newUserPassword,
        });
        await handleAuthServer(page, cc2Email, newUserPassword);
        await expect(page.locator('#signout-link')).toBeVisible({ timeout: 15000 });

        // ── Attempt to create a contract with a non-existent product code ────────
        log('POST contract with non-existent product code NON-EXISTENT-PROD');
        const xsrfCookie = await page.context().cookies();
        const xsrfToken = xsrfCookie.find(c => c.name === 'XSRF-TOKEN');

        const contractResp = await page.request.post('/api/public/sales/contracts', {
            headers: xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken.value } : {},
            data: {
                orgId: sellerOrgId,
                contractReference: `E2E-CC2-${timestamp}`,
                publicNotes: 'Should fail - non-existent product',
                lineItems: [
                    {
                        productCode: 'NON-EXISTENT-PROD',
                        displayOrder: 1,
                        partInstances: [
                            {
                                partCode: 'NON-EXISTENT-PART',
                                attributeValues: [],
                                childPartInstances: [],
                            },
                        ],
                    },
                ],
            },
        });

        log('Assert 422 Unprocessable Entity for non-existent product code');
        console.log(`[CC2] Response status: ${contractResp.status()}`);
        expect(contractResp.status(), `Expected 422 but got ${contractResp.status()}`).toBe(422);
    });

    test('CC3: contract creation with missing orgId is rejected', async ({ page }: { page: Page }) => {
        const log = testStepLogger('CC3');

        // CC3 tests missing orgId — no need to create a real product.
        await page.goto('/');
        await signOut(page);
        await headerSignInLink(page).click();
        await page.waitForURL(/auth-t\.abstratium\.dev\/signin\//, { timeout: 15000 });

        const cc3Email = `e2e-cc3-${timestamp}@example.com`;
        await registerNewUser(page, {
            email: cc3Email,
            fullName: `E2E CC3 ${timestamp}`,
            orgName: `E2E CC3 Org ${timestamp}`,
            password: newUserPassword,
        });
        await handleAuthServer(page, cc3Email, newUserPassword);
        await expect(page.locator('#signout-link')).toBeVisible({ timeout: 15000 });

        log('POST contract with missing orgId');
        const xsrfCookie = await page.context().cookies();
        const xsrfToken = xsrfCookie.find(c => c.name === 'XSRF-TOKEN');

        const contractResp = await page.request.post('/api/public/sales/contracts', {
            headers: xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken.value } : {},
            data: {
                contractReference: `E2E-CC3-${timestamp}`,
                publicNotes: 'Should fail - missing orgId',
                lineItems: [
                    {
                        productCode: 'ANY-PROD',
                        displayOrder: 1,
                        partInstances: [
                            {
                                partCode: 'ANY-PART',
                                attributeValues: [],
                                childPartInstances: [],
                            },
                        ],
                    },
                ],
            },
        });

        log('Assert 400 Bad Request for missing orgId');
        console.log(`[CC3] Response status: ${contractResp.status()}`);
        expect(contractResp.status(), `Expected 400 but got ${contractResp.status()}`).toBe(400);
    });

    test('CC4: contract creation with empty line items is rejected', async ({ page }: { page: Page }) => {
        const log = testStepLogger('CC4');

        // CC4 tests empty line items — no need to create a real product.
        await page.goto('/');
        await signOut(page);
        await headerSignInLink(page).click();
        await page.waitForURL(/auth-t\.abstratium\.dev\/signin\//, { timeout: 15000 });

        const cc4Email = `e2e-cc4-${timestamp}@example.com`;
        await registerNewUser(page, {
            email: cc4Email,
            fullName: `E2E CC4 ${timestamp}`,
            orgName: `E2E CC4 Org ${timestamp}`,
            password: newUserPassword,
        });
        await handleAuthServer(page, cc4Email, newUserPassword);
        await expect(page.locator('#signout-link')).toBeVisible({ timeout: 15000 });

        log('POST contract with empty line items array');
        const xsrfCookie = await page.context().cookies();
        const xsrfToken = xsrfCookie.find(c => c.name === 'XSRF-TOKEN');

        const contractResp = await page.request.post('/api/public/sales/contracts', {
            headers: xsrfToken ? { 'X-XSRF-TOKEN': xsrfToken.value } : {},
            data: {
                orgId: sellerOrgId,
                contractReference: `E2E-CC4-${timestamp}`,
                publicNotes: 'Should fail - empty line items',
                lineItems: [],
            },
        });

        log('Assert 400 Bad Request for empty line items');
        console.log(`[CC4] Response status: ${contractResp.status()}`);
        expect(contractResp.status(), `Expected 400 but got ${contractResp.status()}`).toBe(400);
    });

});
