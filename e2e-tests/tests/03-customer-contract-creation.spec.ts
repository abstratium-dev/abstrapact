import { test, expect, Page } from '@playwright/test';
import { signInViaHeader, testStepLogger } from '../pages/test-helpers';
import { handleAuthServer, headerSignInLink, signOut } from '../pages/TODO.page';
import { registerNewUser } from '../pages/auth-server.page';

// ─── Constants ─────────────────────────────────────────────────────────────────

const PRODUCT_CODE = 'CT-PROD-001';
const PART_CODE = 'CT-PART-001';

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function resolveSellerOrgId(page: Page): Promise<string> {
    const resp = await page.request.get('/api/core/userinfo');
    const info = await resp.json();
    console.log(`[TestHelper] Resolved seller orgId: ${info.orgId}`);
    return info.orgId as string;
}

async function cleanupProduct(page: Page, sellerOrgId: string): Promise<void> {
    const prefixedCode = `${sellerOrgId}::${PRODUCT_CODE}`;
    console.log(`[TestHelper] Cleaning up product '${prefixedCode}'`);
    const lookup = await page.request.get(`/api/product-definitions/code/${encodeURIComponent(prefixedCode)}`);
    if (lookup.status() === 404) {
        console.log(`[TestHelper] Product '${PRODUCT_CODE}' not found, nothing to clean up`);
        return;
    }
    if (!lookup.ok()) {
        console.log(`[TestHelper] Could not look up '${PRODUCT_CODE}': ${lookup.status()}`);
        return;
    }
    const product = await lookup.json();
    const del = await page.request.delete(`/api/product-definitions/${product.id}/complete`);
    if (del.ok()) {
        console.log(`[TestHelper] Deleted product '${PRODUCT_CODE}' (id=${product.id})`);
    } else {
        console.log(`[TestHelper] Failed to delete product '${PRODUCT_CODE}': ${del.status()}`);
    }
}

/**
 * Create a product definition with crossTenantApiAllowed=true and one part via the REST API.
 * Relies on the current browser session (signed in as the seller org user).
 */
async function createCrossTenantProduct(page: Page): Promise<string> {
    console.log(`[TestHelper] Creating cross-tenant product '${PRODUCT_CODE}'`);
    const resp = await page.request.post('/api/product-definitions', {
        data: {
            productCode: PRODUCT_CODE,
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
            partCode: PART_CODE,
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

    const timestamp = Date.now();
    const newUserEmail = `e2e-customer-${timestamp}@example.com`;
    const newUserPassword = 'secretLong123!';
    let sellerOrgId: string;

    test.beforeEach(async ({ page }: { page: Page }) => {
        page.on('console', msg => { if (msg.type() === 'error') console.log(`[Browser Error] ${msg.text()}`); });
        page.on('pageerror', err => console.log(`[Page Error] ${err.message}`));

        // Sign in as the main (seller) user to clean up and set up product data.
        await page.goto('/');
        await signInViaHeader(page);
        sellerOrgId = await resolveSellerOrgId(page);
        await cleanupProduct(page, sellerOrgId);
    });

    test('CC1: new customer registers, signs in, and creates a draft contract', async ({ page }: { page: Page }) => {
        const log = testStepLogger('CC1');

        // ── Step 1: create the cross-tenant product as the seller user ──────────
        log('Create product with crossTenantApiAllowed=true as seller user');
        await createCrossTenantProduct(page);

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

        // ── Step 4: POST /api/public/contracts as the new customer user ─────────
        log('Fetch CSRF token for the new user session');
        const csrfCookie = await page.context().cookies();
        const xsrfToken = csrfCookie.find(c => c.name === 'XSRF-TOKEN');
        console.log(`[CC1] XSRF-TOKEN cookie present: ${!!xsrfToken}`);

        log('Create draft contract via POST /api/public/contracts');
        const contractRequest = {
            orgId: sellerOrgId,
            contractReference: `E2E-REF-${timestamp}`,
            publicNotes: 'Created by e2e test CC1',
            lineItems: [
                {
                    productCode: PRODUCT_CODE,
                    displayOrder: 1,
                    partInstances: [
                        {
                            partCode: PART_CODE,
                            attributeValues: [],
                            childPartInstances: [],
                        },
                    ],
                },
            ],
        };
        console.log(`[CC1] Contract request:\n${JSON.stringify(contractRequest, null, 2)}`);
        const contractResp = await page.request.post('/api/public/contracts', {
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

});
