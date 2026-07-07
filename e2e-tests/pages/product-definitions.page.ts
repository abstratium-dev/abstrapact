import { expect, Page } from '@playwright/test';
export { dismissCookieNoticeIfPresent } from './test-helpers';

// ─── Product Definitions List Page ────────────────────────────────────────────

export function productDefinitionsListPage(page: Page) {
    return page.getByTestId('product-definitions-list-page');
}

export function addProductDefinitionButton(page: Page) {
    return page.locator('.btn-add');
}

export function productDefinitionTileByCode(page: Page, code: string) {
    return page.locator('.tile').filter({ hasText: code });
}

export function viewProductDefinitionButton(page: Page, code: string) {
    return productDefinitionTileByCode(page, code).locator('button[title="View product details"]');
}

export function editProductDefinitionButton(page: Page, code: string) {
    return productDefinitionTileByCode(page, code).locator('button[title="Edit product definition"]');
}

export function simulateProductDefinitionButton(page: Page, code: string) {
    return productDefinitionTileByCode(page, code).locator('button[title="Simulate product instance"]');
}

export function deleteProductDefinitionButton(page: Page, code: string) {
    return productDefinitionTileByCode(page, code).locator('button[title="Delete product definition"]');
}

export async function assertOnProductDefinitionsListPage(page: Page) {
    console.log('[ProductDefinitionsListPage] Asserting on product definitions list page');
    await expect(productDefinitionsListPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Product Definitions' })).toBeVisible();
    // Wait for the list data to finish loading so callers can safely interact with tiles.
    const loadingIndicator = page.getByText('Loading product definitions...');
    await loadingIndicator.waitFor({ state: 'visible', timeout: 2000 }).catch(() => null);
    await loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => null);
}

export async function navigateToProductDefinitions(page: Page) {
    console.log('[ProductDefinitionsListPage] Navigating to product definitions via header');
    await page.locator('#products-link').click();
    await assertOnProductDefinitionsListPage(page);
}

export async function clickAddProductDefinition(page: Page) {
    console.log('[ProductDefinitionsListPage] Clicking add product definition button');
    await addProductDefinitionButton(page).click();
    await assertOnProductDefinitionFormPage(page);
}

export async function assertProductDefinitionExists(page: Page, code: string) {
    console.log(`[ProductDefinitionsListPage] Asserting product definition with code '${code}' exists`);
    await expect(productDefinitionTileByCode(page, code)).toBeVisible();
}

export async function assertProductDefinitionNotExists(page: Page, code: string) {
    console.log(`[ProductDefinitionsListPage] Asserting product definition with code '${code}' does not exist`);
    await expect(productDefinitionTileByCode(page, code)).not.toBeVisible();
}

export async function deleteProductDefinitionByCode(page: Page, code: string) {
    console.log(`[ProductDefinitionsListPage] Deleting product definition with code '${code}'`);
    const deleteBtn = deleteProductDefinitionButton(page, code);
    await deleteBtn.click();
    const confirmDeleteBtn = page.getByRole('button', { name: 'Delete' });
    await confirmDeleteBtn.waitFor({ state: 'visible', timeout: 5000 });
    await confirmDeleteBtn.click();
    await expect(productDefinitionTileByCode(page, code)).not.toBeVisible({ timeout: 5000 });
}

// ─── Product Definition Form Page ─────────────────────────────────────────────

export function productDefinitionFormPage(page: Page) {
    return page.getByTestId('product-definition-form-page');
}

export function productCodeInput(page: Page) {
    return page.getByTestId('product-code-input');
}

export function descriptionInput(page: Page) {
    return page.getByTestId('description-input');
}

export function billingModelSelect(page: Page) {
    return page.getByTestId('billing-model-select');
}

export function paymentModelSelect(page: Page) {
    return page.getByTestId('payment-model-select');
}

export function termsAndConditionsSelect(page: Page) {
    return page.getByTestId('terms-and-conditions-select');
}

export function validFromInput(page: Page) {
    return page.getByTestId('valid-from-input');
}

export function validUntilInput(page: Page) {
    return page.getByTestId('valid-until-input');
}

export function submitButton(page: Page) {
    return page.getByTestId('submit-button');
}

export function cancelButton(page: Page) {
    return page.getByTestId('cancel-button');
}

export function formError(page: Page) {
    return page.getByTestId('form-error');
}

export function productCodeError(page: Page) {
    return page.getByTestId('product-code-error');
}

export function validUntilError(page: Page) {
    return page.getByTestId('valid-until-error');
}

export async function assertOnProductDefinitionFormPage(page: Page) {
    console.log('[ProductDefinitionFormPage] Asserting on product definition form page');
    await expect(productDefinitionFormPage(page)).toBeVisible({ timeout: 10000 });
}

export async function fillProductDefinitionForm(
    page: Page,
    data: {
        productCode: string;
        description?: string;
        billingModel?: string;
        paymentModel?: string;
        termsAndConditionsCode?: string;
        validFrom?: string;
        validUntil?: string;
    }
) {
    console.log(`[ProductDefinitionFormPage] Filling form for product code '${data.productCode}'`);
    await productCodeInput(page).fill(data.productCode);
    if (data.description !== undefined) {
        await descriptionInput(page).fill(data.description);
    }
    if (data.billingModel !== undefined) {
        await billingModelSelect(page).selectOption(data.billingModel);
    }
    if (data.paymentModel !== undefined) {
        await paymentModelSelect(page).selectOption(data.paymentModel);
    }
    if (data.termsAndConditionsCode !== undefined) {
        await termsAndConditionsSelect(page).selectOption(data.termsAndConditionsCode);
    }
    if (data.validFrom !== undefined) {
        await validFromInput(page).fill(data.validFrom);
    }
    if (data.validUntil !== undefined) {
        await validUntilInput(page).fill(data.validUntil);
    }
}

export async function submitProductDefinitionForm(page: Page) {
    console.log('[ProductDefinitionFormPage] Submitting form');
    await submitButton(page).click();
}

export async function createProductDefinition(
    page: Page,
    data: {
        productCode: string;
        description?: string;
        billingModel?: string;
        paymentModel?: string;
        termsAndConditionsCode?: string;
        validFrom?: string;
        validUntil?: string;
    }
) {
    console.log(`[ProductDefinitionFormPage] Creating product definition '${data.productCode}'`);
    await fillProductDefinitionForm(page, data);
    await submitProductDefinitionForm(page);
}

export async function assertFormErrorContains(page: Page, expectedText: string) {
    console.log(`[ProductDefinitionFormPage] Asserting form error contains: ${expectedText}`);
    await expect(formError(page)).toBeVisible({ timeout: 5000 });
    await expect(formError(page)).toContainText(expectedText);
}

export async function assertProductCodeErrorContains(page: Page, expectedText: string) {
    console.log(`[ProductDefinitionFormPage] Asserting product code error contains: ${expectedText}`);
    await expect(productCodeError(page)).toBeVisible({ timeout: 5000 });
    await expect(productCodeError(page)).toContainText(expectedText);
}

export async function assertValidUntilErrorContains(page: Page, expectedText: string) {
    console.log(`[ProductDefinitionFormPage] Asserting valid until error contains: ${expectedText}`);
    await expect(validUntilError(page)).toBeVisible({ timeout: 5000 });
    await expect(validUntilError(page)).toContainText(expectedText);
}

export async function assertNoFormError(page: Page) {
    console.log('[ProductDefinitionFormPage] Asserting no form error is shown');
    await expect(formError(page)).not.toBeVisible();
}

// ─── Product Definition Detail Page ───────────────────────────────────────────

export function productDefinitionDetailPage(page: Page) {
    return page.getByTestId('product-definition-detail-page');
}

export function backToListButton(page: Page) {
    return page.getByRole('button', { name: 'Back to List' });
}

export function editButtonOnDetail(page: Page) {
    return page.locator('button[title="Edit product definition"]');
}

export function deleteButtonOnDetail(page: Page) {
    return page.locator('button[title="Delete product definition"]');
}

export async function assertOnProductDefinitionDetailPage(page: Page) {
    console.log('[ProductDefinitionDetailPage] Asserting on product definition detail page');
    await expect(productDefinitionDetailPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Product Definition Details' })).toBeVisible();
}

export async function viewProductDefinitionDetail(page: Page, code: string) {
    console.log(`[ProductDefinitionDetailPage] Opening detail page for product code '${code}'`);
    await productDefinitionTileByCode(page, code).click();
    await assertOnProductDefinitionDetailPage(page);
}

export async function assertProductDefinitionDetailContains(page: Page, code: string) {
    console.log(`[ProductDefinitionDetailPage] Asserting detail page shows product code '${code}'`);
    await expect(page.getByRole('heading', { name: code, exact: false })).toBeVisible();
}

export async function assertProductDefinitionDetailField(page: Page, label: string, value: string | RegExp) {
    console.log(`[ProductDefinitionDetailPage] Asserting field '${label}' contains '${value}'`);
    const row = page.locator('.detail-row').filter({ hasText: label });
    await expect(row).toBeVisible();
    await expect(row.locator('.value')).toContainText(value);
}

export async function clickBackToList(page: Page) {
    console.log('[ProductDefinitionDetailPage] Navigating back to list');
    await backToListButton(page).click();
    await assertOnProductDefinitionsListPage(page);
}

export async function clickEditOnDetailPage(page: Page) {
    console.log('[ProductDefinitionDetailPage] Clicking edit on detail page');
    await editButtonOnDetail(page).click();
    await assertOnProductDefinitionFormPage(page);
}

export async function deleteProductDefinitionFromDetail(page: Page) {
    console.log('[ProductDefinitionDetailPage] Clicking delete on detail page');
    await deleteButtonOnDetail(page).click();
    const confirmDeleteBtn = page.getByRole('button', { name: 'Delete' });
    await confirmDeleteBtn.waitFor({ state: 'visible', timeout: 5000 });
    await confirmDeleteBtn.click();
    await assertOnProductDefinitionsListPage(page);
}
