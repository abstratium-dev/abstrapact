import { expect, Page } from '@playwright/test';
export { dismissCookieNoticeIfPresent } from './test-helpers';

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
    console.log(`[TermsAndConditionsListPage] Current URL: ${page.url()}`);
    await page.screenshot({ path: `test-results/before-list-assert-${Date.now()}.png` }).catch(() => null);
    await expect(termsAndConditionsListPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Terms and Conditions' })).toBeVisible();
    // Wait for the list data to finish loading so callers can safely interact with tiles.
    const loadingIndicator = page.getByText('Loading terms and conditions...');
    await loadingIndicator.waitFor({ state: 'visible', timeout: 2000 }).catch(() => null);
    await loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => null);
}

export async function navigateToTermsAndConditions(page: Page) {
    console.log('[TermsAndConditionsListPage] Navigating to terms and conditions via header');
    const listLoadPromise = page.waitForResponse(
        response => new URL(response.url()).pathname === '/api/terms-and-conditions' && response.request().method() === 'GET',
        { timeout: 10000 }
    );
    await page.goto('/terms-and-conditions');
    await listLoadPromise;
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
    console.log(`[TermsAndConditionsListPage] Deleting all terms with code '${code}'`);
    while (true) {
        // Wait for the list to be stable (not reloading) before checking for a tile.
        const loadingIndicator = page.getByText('Loading terms and conditions...');
        await loadingIndicator.waitFor({ state: 'visible', timeout: 2000 }).catch(() => null);
        await loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => null);

        const tile = termsTileByCode(page, code).first();
        if (!(await tile.isVisible().catch(() => false))) {
            break;
        }

        const tileText = await tile.textContent().catch(() => '<unknown>');
        console.log(`[TermsAndConditionsListPage] Deleting tile: '${tileText?.trim().substring(0, 80)}'`);

        const deleteBtn = tile.locator('.btn-icon-danger');
        await deleteBtn.click();
        const confirmDeleteBtn = page.getByRole('button', { name: 'Delete' });
        await confirmDeleteBtn.waitFor({ state: 'visible', timeout: 5000 });

        // Set up both response listeners before clicking confirm.
        const deleteResponsePromise = page.waitForResponse(
            response => {
                const url = new URL(response.url());
                return url.pathname.startsWith('/api/terms-and-conditions/') && response.request().method() === 'DELETE';
            },
            { timeout: 10000 }
        );
        const reloadPromise = page.waitForResponse(
            response => new URL(response.url()).pathname === '/api/terms-and-conditions' && response.request().method() === 'GET',
            { timeout: 10000 }
        );
        await confirmDeleteBtn.click();
        const deleteResp = await deleteResponsePromise;
        console.log(`[TermsAndConditionsListPage] DELETE response status: ${deleteResp.status()}`);
        await reloadPromise;
    }
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

    const actualCode = await codeInput(page).inputValue();
    const actualEffectiveFrom = await effectiveFromInput(page).inputValue();
    const actualEffectiveUntil = await effectiveUntilInput(page).inputValue();
    console.log(`[TermsAndConditionsFormPage] Actual field values - code='${actualCode}', effectiveFrom='${actualEffectiveFrom}', effectiveUntil='${actualEffectiveUntil}'`);
}

export async function submitTermsAndConditionsForm(page: Page) {
    console.log('[TermsAndConditionsFormPage] Submitting form');
    const responsePromise = page.waitForResponse(
        response => new URL(response.url()).pathname === '/api/terms-and-conditions' &&
            ['POST', 'PUT'].includes(response.request().method()),
        { timeout: 15000 }
    );
    await submitButton(page).click();
    try {
        const response = await responsePromise;
        const body = await response.text().catch(() => '<unreadable>');
        console.log(`[TermsAndConditionsFormPage] API response: status=${response.status()}, url=${response.url()}, body=${body}`);
    } catch (e) {
        console.log(`[TermsAndConditionsFormPage] No API response received: ${e}`);
    }
    console.log(`[TermsAndConditionsFormPage] After submit, current URL: ${page.url()}`);
    await page.screenshot({ path: `test-results/after-submit-${Date.now()}.png` }).catch(() => null);
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
