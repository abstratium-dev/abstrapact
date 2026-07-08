import { test, Page } from '@playwright/test';
import { signInViaHeader, testStepLogger } from '../pages/test-helpers';
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
    clickAddRootPart,
    fillPartForm,
    submitPartForm,
    assertPartExists,
    assertPartNotExists,
    deletePartByCode,
} from '../pages/product-definitions.page';

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function cleanupTestData(page: Page, codes: string[]) {
    console.log(`[TestHelper] Cleaning up product definitions for codes: ${codes.join(', ')}`);
    for (const code of codes) {
        const lookup = await page.request.get(`/api/product-definitions/code/${code}`);
        if (lookup.status() === 404) {
            console.log(`[TestHelper] Product '${code}' not found, skipping`);
            continue;
        }
        if (!lookup.ok()) {
            console.log(`[TestHelper] Could not look up '${code}': ${lookup.status()}`);
            continue;
        }
        const product = await lookup.json();
        const del = await page.request.delete(`/api/product-definitions/${product.id}/complete`);
        if (del.ok()) {
            console.log(`[TestHelper] Deleted '${code}' (id=${product.id})`);
        } else {
            console.log(`[TestHelper] Failed to delete '${code}': ${del.status()}`);
        }
    }
}

// ─── Product Definitions Management Tests ───────────────────────────────────────

test.describe('Product Definitions Management', () => {

    test.beforeEach(async ({ page }: { page: Page }) => {
        page.on('console', msg => { if (msg.type() === 'error') console.log(`[Browser Error] ${msg.text()}`); });
        page.on('pageerror', err => console.log(`[Page Error] ${err.message}`));
        await page.goto('/');
        await signInViaHeader(page);
        // Clean up all test codes before each test for a clean slate.
        await cleanupTestData(page, [
            'PD-CRUD-01',
            'PD-DUPE-01',
            'PD-DATE-01',
        ]);
    });

    // PD1: Full product lifecycle - create, edit, add parts, delete a part
    test('PD1: create, edit, and manage parts for a product definition', async ({ page }: { page: Page }) => {
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

        log('Edit the product definition');
        await clickEditOnDetailPage(page);
        await fillProductDefinitionForm(page, {
            productCode: 'PD-CRUD-01',
            description: 'Product after edit',
            billingModel: 'SUBSCRIPTION',
            paymentModel: 'POSTPAID',
            validFrom: '2025-01-01',
            validUntil: '2026-12-31',
        }, true);
        await submitProductDefinitionForm(page);
        await page.waitForURL('**/product-definitions', { timeout: 10000 });

        log('Verify edited product details');
        await assertOnProductDefinitionsListPage(page);
        await viewProductDefinitionDetail(page, 'PD-CRUD-01');
        await assertProductDefinitionDetailField(page, 'Description', 'Product after edit');
        await assertProductDefinitionDetailField(page, 'Billing Model', 'Subscription');
        await assertProductDefinitionDetailField(page, 'Payment Model', 'Postpaid');
        await assertProductDefinitionDetailField(page, 'Valid Until', /2026/);

        log('Add root part PART-001');
        await clickAddRootPart(page);
        await fillPartForm(page, {
            partCode: 'PART-001',
            description: 'First root part',
            unitPrice: '9.99',
            minCardinality: '1',
            maxCardinality: '1',
        });
        await submitPartForm(page);
        await assertPartExists(page, 'PART-001');

        log('Add root part PART-002');
        await clickAddRootPart(page);
        await fillPartForm(page, {
            partCode: 'PART-002',
            description: 'Second root part',
            unitPrice: '19.99',
            minCardinality: '0',
            maxCardinality: '3',
        });
        await submitPartForm(page);
        await assertPartExists(page, 'PART-001');
        await assertPartExists(page, 'PART-002');

        log('Delete PART-002 and verify PART-001 remains');
        await deletePartByCode(page, 'PART-002');
        await assertPartNotExists(page, 'PART-002');
        await assertPartExists(page, 'PART-001');
    });

    // PD6: Duplicate product code must be rejected
    test('PD6: duplicate product code is rejected', async ({ page }: { page: Page }) => {
        const log = testStepLogger('PD6');
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
        await fillProductDefinitionForm(page, {
            productCode: 'PD-DUPE-01',
            description: 'Second duplicate test product',
            billingModel: 'SUBSCRIPTION',
            paymentModel: 'POSTPAID',
        });
        await submitProductDefinitionForm(page);

        log('Assert duplicate code error is shown');
        await assertOnProductDefinitionFormPage(page);
        await assertFormErrorContains(page, 'already exists');
    });

    // PD7: Invalid date range (valid until before valid from) must be rejected
    test('PD7: invalid valid-until date is rejected', async ({ page }: { page: Page }) => {
        const log = testStepLogger('PD7');
        log('Navigate to product definitions list');
        await navigateToProductDefinitions(page);
        log('Open add product definition form');
        await clickAddProductDefinition(page);

        log('Attempt to create product with invalid date range');
        await fillProductDefinitionForm(page, {
            productCode: 'PD-DATE-01',
            description: 'Invalid date range product',
            billingModel: 'FIXED_PRICE',
            paymentModel: 'PREPAID',
            validFrom: '2024-12-31',
            validUntil: '2024-01-01',
        });
        await submitProductDefinitionForm(page);

        log('Assert date validation error is shown and product was not created');
        await assertOnProductDefinitionFormPage(page);
        await assertValidUntilErrorContains(page, 'after valid from');
        await assertProductDefinitionNotExists(page, 'PD-DATE-01');
    });

});
