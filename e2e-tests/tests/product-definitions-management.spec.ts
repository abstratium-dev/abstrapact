import { test, Page } from '@playwright/test';
import {
    handleAuthServer,
    assertHeaderSignedIn,
    headerSignInLink,
} from '../pages/TODO.page';
import {
    navigateToProductDefinitions,
    clickAddProductDefinition,
    createProductDefinition,
    fillProductDefinitionForm,
    submitProductDefinitionForm,
    assertOnProductDefinitionsListPage,
    assertOnProductDefinitionFormPage,
    assertProductDefinitionExists,
    assertProductDefinitionNotExists,
    assertFormErrorContains,
    assertValidUntilErrorContains,
    viewProductDefinitionDetail,
    assertProductDefinitionDetailContains,
    assertProductDefinitionDetailField,
    clickEditOnDetailPage,
    deleteProductDefinitionByCode,
    productDefinitionTileByCode,
    dismissCookieNoticeIfPresent,
} from '../pages/product-definitions.page';

const EMAIL = 'test@abstratium.dev';
const PASSWORD = 'secretLong';

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function signInViaHeader(page: Page) {
    console.log('[TestHelper] Signing in via header');
    await dismissCookieNoticeIfPresent(page);
    await headerSignInLink(page).click();
    await handleAuthServer(page, EMAIL, PASSWORD);
    await assertHeaderSignedIn(page);
}

async function cleanupTestData(page: Page, codes: string[]) {
    console.log(`[TestHelper] Cleaning up product definitions for codes: ${codes.join(', ')}`);
    await navigateToProductDefinitions(page);
    for (const code of codes) {
        try {
            const tile = productDefinitionTileByCode(page, code);
            if (await tile.isVisible().catch(() => false)) {
                await deleteProductDefinitionByCode(page, code);
            }
        } catch (e) {
            console.log(`[TestHelper] Could not delete product definition ${code}, may not exist`);
        }
    }
}

function testStepLogger(testName: string) {
    let step = 0;
    return (message: string) => console.log(`[${testName} ${++step}] ${message}`);
}

// ─── Product Definitions Management Tests ───────────────────────────────────────

test.describe('Product Definitions Management', () => {

    test.beforeEach(async ({ page }: { page: Page }) => {
        await page.goto('/');
        await signInViaHeader(page);
        // Remove any stale product definitions from previous runs before this test creates anything.
        await cleanupTestData(page, [
            'PD-CRUD-01',
            'PD-EDIT-01',
            'PD-DEL-01',
            'PD-DUPE-01',
            'PD-DATE-01',
        ]);
    });

    // PD1: Create a fixed-price product definition and inspect its details
    test('PD1: create a fixed-price product definition and view its details', async ({ page }: { page: Page }) => {
        const log = testStepLogger('PD1');
        log('Navigate to product definitions list');
        await navigateToProductDefinitions(page);
        log('Open add product definition form');
        await clickAddProductDefinition(page);

        log('Create fixed-price product definition PD-CRUD-01');
        await createProductDefinition(page, {
            productCode: 'PD-CRUD-01',
            description: 'E2E fixed-price product definition',
            billingModel: 'FIXED_PRICE',
            paymentModel: 'PREPAID',
            validFrom: '2024-01-01',
            validUntil: '2024-12-31',
        });

        log('Assert product appears in list');
        await assertOnProductDefinitionsListPage(page);
        await assertProductDefinitionExists(page, 'PD-CRUD-01');

        log('View product details and verify fields');
        await viewProductDefinitionDetail(page, 'PD-CRUD-01');
        await assertProductDefinitionDetailContains(page, 'PD-CRUD-01');
        await assertProductDefinitionDetailField(page, 'Description', 'E2E fixed-price product definition');
        await assertProductDefinitionDetailField(page, 'Billing Model', 'Fixed Price');
        await assertProductDefinitionDetailField(page, 'Payment Model', 'Prepaid');
        await assertProductDefinitionDetailField(page, 'Valid From', /2024/);
        await assertProductDefinitionDetailField(page, 'Valid Until', /2024/);
    });

    // PD2: Edit an existing product definition
    test('PD2: edit an existing product definition', async ({ page }: { page: Page }) => {
        const log = testStepLogger('PD2');
        log('Navigate to product definitions list');
        await navigateToProductDefinitions(page);
        log('Open add product definition form');
        await clickAddProductDefinition(page);

        log('Create product definition PD-EDIT-01');
        await createProductDefinition(page, {
            productCode: 'PD-EDIT-01',
            description: 'Product before edit',
            billingModel: 'FIXED_PRICE',
            paymentModel: 'PREPAID',
            validFrom: '2025-01-01',
            validUntil: '2025-12-31',
        });

        log('Assert product appears in list');
        await assertOnProductDefinitionsListPage(page);
        await assertProductDefinitionExists(page, 'PD-EDIT-01');

        log('Open product details and edit');
        await viewProductDefinitionDetail(page, 'PD-EDIT-01');
        await clickEditOnDetailPage(page);

        log('Submit edit form with new values');
        await fillProductDefinitionForm(page, {
            productCode: 'PD-EDIT-01',
            description: 'Product after edit',
            billingModel: 'SUBSCRIPTION',
            paymentModel: 'POSTPAID',
            validFrom: '2025-01-01',
            validUntil: '2026-12-31',
        });
        await submitProductDefinitionForm(page);

        log('Verify edited product details');
        await assertOnProductDefinitionsListPage(page);
        await viewProductDefinitionDetail(page, 'PD-EDIT-01');
        await assertProductDefinitionDetailField(page, 'Description', 'Product after edit');
        await assertProductDefinitionDetailField(page, 'Billing Model', 'Subscription');
        await assertProductDefinitionDetailField(page, 'Payment Model', 'Postpaid');
        await assertProductDefinitionDetailField(page, 'Valid Until', /2026/);
    });

    // PD3: Delete a product definition from the list
    test('PD3: delete a product definition from the list', async ({ page }: { page: Page }) => {
        const log = testStepLogger('PD3');
        log('Navigate to product definitions list');
        await navigateToProductDefinitions(page);
        log('Open add product definition form');
        await clickAddProductDefinition(page);

        log('Create product definition PD-DEL-01');
        await createProductDefinition(page, {
            productCode: 'PD-DEL-01',
            description: 'Product to delete',
            billingModel: 'FIXED_PRICE',
            paymentModel: 'PREPAID',
        });

        log('Assert product appears in list');
        await assertOnProductDefinitionsListPage(page);
        await assertProductDefinitionExists(page, 'PD-DEL-01');

        log('Delete product and verify it is gone');
        await deleteProductDefinitionByCode(page, 'PD-DEL-01');
        await assertProductDefinitionNotExists(page, 'PD-DEL-01');
    });

    // PD4: Duplicate product code must be rejected
    test('PD4: duplicate product code is rejected', async ({ page }: { page: Page }) => {
        const log = testStepLogger('PD4');
        log('Navigate to product definitions list');
        await navigateToProductDefinitions(page);
        log('Open add product definition form');
        await clickAddProductDefinition(page);

        log('Create first product definition PD-DUPE-01');
        await createProductDefinition(page, {
            productCode: 'PD-DUPE-01',
            description: 'First duplicate test product',
            billingModel: 'FIXED_PRICE',
            paymentModel: 'PREPAID',
        });

        log('Assert first product appears in list');
        await assertOnProductDefinitionsListPage(page);
        await assertProductDefinitionExists(page, 'PD-DUPE-01');

        log('Attempt to create second product with same code');
        await clickAddProductDefinition(page);
        await createProductDefinition(page, {
            productCode: 'PD-DUPE-01',
            description: 'Second duplicate test product',
            billingModel: 'SUBSCRIPTION',
            paymentModel: 'POSTPAID',
        });

        log('Assert duplicate code error is shown');
        await assertOnProductDefinitionFormPage(page);
        await assertFormErrorContains(page, 'already exists');
    });

    // PD5: Invalid date range (valid until before valid from) must be rejected
    test('PD5: invalid valid-until date is rejected', async ({ page }: { page: Page }) => {
        const log = testStepLogger('PD5');
        log('Navigate to product definitions list');
        await navigateToProductDefinitions(page);
        log('Open add product definition form');
        await clickAddProductDefinition(page);

        log('Attempt to create product with invalid date range');
        await createProductDefinition(page, {
            productCode: 'PD-DATE-01',
            description: 'Invalid date range product',
            billingModel: 'FIXED_PRICE',
            paymentModel: 'PREPAID',
            validFrom: '2024-12-31',
            validUntil: '2024-01-01',
        });

        log('Assert date validation error is shown and product was not created');
        await assertOnProductDefinitionFormPage(page);
        await assertValidUntilErrorContains(page, 'after valid from');
        await assertProductDefinitionNotExists(page, 'PD-DATE-01');
    });

});
