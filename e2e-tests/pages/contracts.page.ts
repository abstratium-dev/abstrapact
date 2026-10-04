import { expect, Page } from '@playwright/test';

// ─── Payment success page (non-Angular backend page) ─────────────────────────────

export function paymentSuccessReturnLink(page: Page) {
    return page.getByTestId('payment-success-return-link');
}

export async function clickPaymentSuccessReturnLink(page: Page, contractId: string) {
    console.log(`[PaymentSuccessPage] Clicking "View my contract" for ${contractId}`);
    await paymentSuccessReturnLink(page).click();
    await expect(page).toHaveURL(new RegExp(`/contracts/${contractId}$`), { timeout: 10000 });
    await assertOnContractDetailPage(page);
}

// ─── Header navigation ──────────────────────────────────────────────────────────

export function contractsHeaderLink(page: Page) {
    return page.locator('#contracts-link');
}

export async function navigateToContractsList(page: Page) {
    console.log('[ContractsPage] Navigating to "my contracts" via header');
    await contractsHeaderLink(page).click();
    await expect(page).toHaveURL(/\/contracts$/, { timeout: 10000 });
    await assertOnContractsListPage(page);
}

// ─── Contracts list page ──────────────────────────────────────────────────────

export function contractsListPage(page: Page) {
    return page.getByTestId('contracts-list-page');
}

export function contractsCardList(page: Page) {
    return page.getByTestId('contracts-card-list');
}

export function contractTile(page: Page, contractId: string) {
    return page.getByTestId(`contract-tile-${contractId}`);
}

export function contractTileByReference(page: Page, reference: string) {
    return page.locator('.contract-tile').filter({ hasText: reference });
}

export async function assertOnContractsListPage(page: Page) {
    console.log('[ContractsListPage] Asserting on contracts list page');
    await expect(contractsListPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'My Contracts' })).toBeVisible();
    const loadingIndicator = page.getByText('Loading contracts...');
    await loadingIndicator.waitFor({ state: 'visible', timeout: 2000 }).catch(() => null);
    await loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => null);
}

export async function assertContractVisibleInList(page: Page, contractId: string, expectedState?: string) {
    console.log(`[ContractsListPage] Asserting contract ${contractId} is visible in list`);
    const tile = contractTile(page, contractId);
    await expect(tile).toBeVisible({ timeout: 10000 });
    if (expectedState) {
        const badge = tile.getByTestId('contract-state-badge');
        await expect(badge).toContainText(expectedState, { timeout: 5000 });
    }
}

export async function assertContractNotInList(page: Page, contractId: string) {
    console.log(`[ContractsListPage] Asserting contract ${contractId} is not in list`);
    await expect(contractTile(page, contractId)).not.toBeVisible({ timeout: 5000 });
}

export async function openContractDetail(page: Page, contractId: string) {
    console.log(`[ContractsListPage] Opening detail for contract ${contractId}`);
    await contractTile(page, contractId).click();
    await expect(page).toHaveURL(new RegExp(`/contracts/${contractId}$`), { timeout: 10000 });
    await assertOnContractDetailPage(page);
}

// ─── Contract detail page ─────────────────────────────────────────────────────

export function contractDetailPage(page: Page) {
    return page.getByTestId('contract-detail-page');
}

export function contractSummaryCard(page: Page) {
    return page.getByTestId('contract-summary-card');
}

export function contractStateBadge(page: Page) {
    return contractSummaryCard(page).getByTestId('contract-state-badge');
}

export function stateChangesCard(page: Page) {
    return page.getByTestId('state-changes-card');
}

export function paymentAttemptsCard(page: Page) {
    return page.getByTestId('payment-attempts-card');
}

export function paymentAttemptRows(page: Page) {
    return page.getByTestId('payment-attempt-row');
}

export function backToContractsButton(page: Page) {
    return page.getByRole('button', { name: 'Back to My Contracts' });
}

export async function assertOnContractDetailPage(page: Page) {
    console.log('[ContractDetailPage] Asserting on contract detail page');
    await expect(contractDetailPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Contract Details' })).toBeVisible();
    const loadingIndicator = page.getByText('Loading contract...');
    await loadingIndicator.waitFor({ state: 'visible', timeout: 2000 }).catch(() => null);
    await loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => null);
}

export async function assertContractDetailState(page: Page, expectedState: string) {
    console.log(`[ContractDetailPage] Asserting contract state is ${expectedState}`);
    const summaryCard = contractSummaryCard(page);
    await expect(summaryCard).toBeVisible({ timeout: 10000 });
    await expect(contractStateBadge(page)).toContainText(expectedState, { timeout: 5000 });
}

export async function assertContractDetailShowsReference(page: Page, reference: string) {
    console.log(`[ContractDetailPage] Asserting contract reference ${reference} is shown`);
    const summaryCard = contractSummaryCard(page);
    await expect(summaryCard).toBeVisible({ timeout: 10000 });
    await expect(summaryCard.locator('h2')).toContainText(reference, { timeout: 5000 });
}

export async function assertPaymentAttemptCount(page: Page, count: number) {
    console.log(`[ContractDetailPage] Asserting ${count} payment attempt(s) are shown`);
    await expect(paymentAttemptsCard(page)).toBeVisible({ timeout: 10000 });
    await expect(paymentAttemptRows(page)).toHaveCount(count, { timeout: 10000 });
}

export async function assertPaymentAttemptStatus(page: Page, index: number, expectedStatus: string) {
    console.log(`[ContractDetailPage] Asserting payment attempt ${index} status is ${expectedStatus}`);
    const row = paymentAttemptRows(page).nth(index);
    await expect(row).toBeVisible({ timeout: 10000 });
    const badge = row.getByTestId('payment-status-badge');
    await expect(badge).toContainText(expectedStatus, { timeout: 5000 });
}

export async function assertStateChangeCount(page: Page, count: number) {
    console.log(`[ContractDetailPage] Asserting ${count} state change(s) are shown`);
    await expect(stateChangesCard(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('state-change-row')).toHaveCount(count, { timeout: 10000 });
}

export async function goBackToContractsList(page: Page) {
    console.log('[ContractDetailPage] Going back to contracts list');
    await backToContractsButton(page).click();
    await expect(page).toHaveURL(/\/contracts$/, { timeout: 10000 });
    await assertOnContractsListPage(page);
}
