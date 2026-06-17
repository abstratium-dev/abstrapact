import { expect, Page } from '@playwright/test';

// ─── Terms and Conditions List Page ───────────────────────────────────────────

export function termsAndConditionsListPage(page: Page) {
    return page.getByTestId('terms-and-conditions-list-page');
}

export function addTermsButton(page: Page) {
    return page.locator('.btn-add');
}

export function termsTileByCode(page: Page, code: string) {
    return page.locator('.tile').filter({ hasText: code });
}

export function deleteTermsButton(page: Page, code: string) {
    return termsTileByCode(page, code).locator('.btn-icon-danger');
}

export function editTermsButton(page: Page, code: string) {
    return termsTileByCode(page, code).locator('.btn-icon').first();
}

export async function assertOnTermsAndConditionsListPage(page: Page) {
    console.log('[TermsAndConditionsListPage] Asserting on terms and conditions list page');
    await expect(termsAndConditionsListPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Terms and Conditions' })).toBeVisible();
}

export async function navigateToTermsAndConditions(page: Page) {
    console.log('[TermsAndConditionsListPage] Navigating to terms and conditions via header');
    await page.locator('#terms-link').click();
    await assertOnTermsAndConditionsListPage(page);
}

export async function clickAddTermsAndConditions(page: Page) {
    console.log('[TermsAndConditionsListPage] Clicking add terms and conditions button');
    await addTermsButton(page).click();
    await assertOnTermsAndConditionsFormPage(page);
}

export async function assertTermsExists(page: Page, code: string) {
    console.log(`[TermsAndConditionsListPage] Asserting terms with code '${code}' exists`);
    await expect(termsTileByCode(page, code)).toBeVisible();
}

export async function deleteTermsByCode(page: Page, code: string) {
    console.log(`[TermsAndConditionsListPage] Deleting terms with code '${code}'`);
    const deleteBtn = deleteTermsButton(page, code);
    await deleteBtn.click();
    // Wait for confirm dialog and click delete
    const confirmDeleteBtn = page.getByRole('button', { name: 'Delete' });
    await confirmDeleteBtn.waitFor({ state: 'visible', timeout: 5000 });
    await confirmDeleteBtn.click();
    await page.waitForTimeout(500); // Small wait for deletion to process
}

// ─── Terms and Conditions Form Page ───────────────────────────────────────────

export function termsAndConditionsFormPage(page: Page) {
    return page.getByTestId('terms-and-conditions-form-page');
}

export function codeInput(page: Page) {
    return page.getByTestId('code-input');
}

export function titleInput(page: Page) {
    return page.getByTestId('title-input');
}

export function contentFrInput(page: Page) {
    return page.getByTestId('content-fr-input');
}

export function contentDeInput(page: Page) {
    return page.getByTestId('content-de-input');
}

export function contentEnInput(page: Page) {
    return page.getByTestId('content-en-input');
}

export function versionInput(page: Page) {
    return page.getByTestId('version-input');
}

export function effectiveFromInput(page: Page) {
    return page.getByTestId('effective-from-input');
}

export function effectiveUntilInput(page: Page) {
    return page.getByTestId('effective-until-input');
}

export function submitButton(page: Page) {
    return page.getByTestId('submit-button');
}

export function formError(page: Page) {
    return page.getByTestId('form-error');
}

export async function assertOnTermsAndConditionsFormPage(page: Page) {
    console.log('[TermsAndConditionsFormPage] Asserting on terms and conditions form page');
    await expect(termsAndConditionsFormPage(page)).toBeVisible({ timeout: 10000 });
}

export async function fillTermsAndConditionsForm(
    page: Page,
    data: {
        code: string;
        title: string;
        contentFr: string;
        contentDe: string;
        contentEn: string;
        version: string;
        effectiveFrom?: string;
        effectiveUntil?: string;
    }
) {
    console.log(`[TermsAndConditionsFormPage] Filling form for code '${data.code}'`);
    await codeInput(page).fill(data.code);
    await titleInput(page).fill(data.title);
    await contentFrInput(page).fill(data.contentFr);
    await contentDeInput(page).fill(data.contentDe);
    await contentEnInput(page).fill(data.contentEn);
    await versionInput(page).fill(data.version);
    
    if (data.effectiveFrom) {
        await effectiveFromInput(page).fill(data.effectiveFrom);
    }
    if (data.effectiveUntil) {
        await effectiveUntilInput(page).fill(data.effectiveUntil);
    }
}

export async function submitTermsAndConditionsForm(page: Page) {
    console.log('[TermsAndConditionsFormPage] Submitting form');
    await submitButton(page).click();
}

export async function createTermsAndConditions(
    page: Page,
    data: {
        code: string;
        title: string;
        contentFr: string;
        contentDe: string;
        contentEn: string;
        version: string;
        effectiveFrom?: string;
        effectiveUntil?: string;
    }
) {
    console.log(`[TermsAndConditionsFormPage] Creating terms with code '${data.code}'`);
    await fillTermsAndConditionsForm(page, data);
    await submitTermsAndConditionsForm(page);
}

export async function assertFormErrorContains(page: Page, expectedText: string) {
    console.log(`[TermsAndConditionsFormPage] Asserting form error contains: ${expectedText}`);
    await expect(formError(page)).toBeVisible({ timeout: 5000 });
    await expect(formError(page)).toContainText(expectedText);
}

export async function assertNoFormError(page: Page) {
    console.log('[TermsAndConditionsFormPage] Asserting no form error is shown');
    await expect(formError(page)).not.toBeVisible();
}
