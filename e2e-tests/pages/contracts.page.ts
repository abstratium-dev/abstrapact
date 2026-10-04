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

export function organisationContractsHeaderLink(page: Page) {
    return page.locator('#organisation-contracts-link');
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
    const rows = page.getByTestId('state-change-row');
    const actualCount = await rows.count();
    const texts: string[] = [];
    for (let i = 0; i < actualCount; i++) {
        const text = await rows.nth(i).textContent();
        texts.push(text?.trim() ?? '');
    }
    console.log(`[ContractDetailPage] Found ${actualCount} state change rows:`);
    texts.forEach((t, i) => console.log(`  [${i}] ${t}`));
    await expect(rows).toHaveCount(count, { timeout: 10000 });
}

export async function assertStateChangesInclude(page: Page, minimumCount: number, expectedStates: string[]) {
    console.log(`[ContractDetailPage] Asserting at least ${minimumCount} state change(s) and states: [${expectedStates.join(', ')}]`);
    await expect(stateChangesCard(page)).toBeVisible({ timeout: 10000 });
    const rows = page.getByTestId('state-change-row');
    const actualCount = await rows.count();
    expect(actualCount).toBeGreaterThanOrEqual(minimumCount);
    const allText = (await rows.allTextContents()).join(' ');
    for (const state of expectedStates) {
        expect(allText).toContain(state);
    }
}

export async function goBackToContractsList(page: Page) {
    console.log('[ContractDetailPage] Going back to contracts list');
    await backToContractsButton(page).click();
    await expect(page).toHaveURL(/\/contracts$/, { timeout: 10000 });
    await assertOnContractsListPage(page);
}

// ─── Organisation contracts list page ────────────────────────────────────────────

export function organisationContractsListPage(page: Page) {
    return page.getByTestId('organisation-contracts-list-page');
}

export function organisationContractsCardList(page: Page) {
    return page.getByTestId('organisation-contracts-card-list');
}

export function organisationContractTile(page: Page, contractId: string) {
    return page.getByTestId(`organisation-contract-tile-${contractId}`);
}

export async function navigateToOrganisationContractsList(page: Page) {
    console.log('[OrganisationContractsPage] Navigating to "organisation contracts" via header');
    await organisationContractsHeaderLink(page).click();
    await expect(page).toHaveURL(/\/organisation-contracts$/, { timeout: 10000 });
    await assertOnOrganisationContractsListPage(page);
}

export async function assertOnOrganisationContractsListPage(page: Page) {
    console.log('[OrganisationContractsListPage] Asserting on organisation contracts list page');
    await expect(organisationContractsListPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Organisation Contracts' })).toBeVisible();
    const loadingIndicator = page.getByText('Loading contracts...');
    await loadingIndicator.waitFor({ state: 'visible', timeout: 2000 }).catch(() => null);
    await loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => null);
}

export async function assertOrganisationContractVisibleInList(page: Page, contractId: string, expectedState?: string) {
    console.log(`[OrganisationContractsListPage] Asserting organisation contract ${contractId} is visible in list`);
    const tile = organisationContractTile(page, contractId);
    await expect(tile).toBeVisible({ timeout: 10000 });
    if (expectedState) {
        const badge = tile.getByTestId('organisation-contract-state-badge');
        await expect(badge).toContainText(expectedState, { timeout: 5000 });
    }
}

export async function openOrganisationContractDetail(page: Page, contractId: string) {
    console.log(`[OrganisationContractsListPage] Opening detail for organisation contract ${contractId}`);
    await organisationContractTile(page, contractId).click();
    await expect(page).toHaveURL(new RegExp(`/organisation-contracts/${contractId}$`), { timeout: 10000 });
    await assertOnOrganisationContractDetailPage(page);
}

// ─── Organisation contract detail page ───────────────────────────────────────────

export function organisationContractDetailPage(page: Page) {
    return page.getByTestId('organisation-contract-detail-page');
}

export function organisationContractSummaryCard(page: Page) {
    return page.getByTestId('organisation-contract-summary-card');
}

export function organisationContractStateBadge(page: Page) {
    return organisationContractSummaryCard(page).getByTestId('organisation-contract-state-badge');
}

export function organisationStateChangesCard(page: Page) {
    return page.getByTestId('organisation-state-changes-card');
}

export function organisationPaymentAttemptsCard(page: Page) {
    return page.getByTestId('organisation-payment-attempts-card');
}

export function organisationPaymentAttemptRows(page: Page) {
    return page.getByTestId('organisation-payment-attempt-row');
}

export function backToOrganisationContractsButton(page: Page) {
    return page.getByRole('button', { name: 'Back to Organisation Contracts' });
}

export async function assertOnOrganisationContractDetailPage(page: Page) {
    console.log('[OrganisationContractDetailPage] Asserting on organisation contract detail page');
    await expect(organisationContractDetailPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Contract Details' })).toBeVisible();
    const loadingIndicator = page.getByText('Loading contract...');
    await loadingIndicator.waitFor({ state: 'visible', timeout: 2000 }).catch(() => null);
    await loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 }).catch(() => null);
}

export async function assertOrganisationContractDetailState(page: Page, expectedState: string) {
    console.log(`[OrganisationContractDetailPage] Asserting organisation contract state is ${expectedState}`);
    const summaryCard = organisationContractSummaryCard(page);
    await expect(summaryCard).toBeVisible({ timeout: 10000 });
    await expect(organisationContractStateBadge(page)).toContainText(expectedState, { timeout: 5000 });
}

export async function assertOrganisationPaymentAttemptCount(page: Page, count: number) {
    console.log(`[OrganisationContractDetailPage] Asserting ${count} organisation payment attempt(s) are shown`);
    await expect(organisationPaymentAttemptsCard(page)).toBeVisible({ timeout: 10000 });
    await expect(organisationPaymentAttemptRows(page)).toHaveCount(count, { timeout: 10000 });
}

export async function assertOrganisationPaymentAttemptStatus(page: Page, index: number, expectedStatus: string) {
    console.log(`[OrganisationContractDetailPage] Asserting organisation payment attempt ${index} status is ${expectedStatus}`);
    const row = organisationPaymentAttemptRows(page).nth(index);
    await expect(row).toBeVisible({ timeout: 10000 });
    const badge = row.getByTestId('organisation-payment-status-badge');
    await expect(badge).toContainText(expectedStatus, { timeout: 5000 });
}

export async function assertOrganisationPaymentAttemptShowsNetAndFee(page: Page, index: number) {
    console.log(`[OrganisationContractDetailPage] Asserting organisation payment attempt ${index} shows fee and net amounts`);
    const row = organisationPaymentAttemptRows(page).nth(index);
    await expect(row).toBeVisible({ timeout: 10000 });
    const rowText = await row.textContent() ?? '';
    const hasFee = /fee\s/i.test(rowText);
    const hasNet = /net\s/i.test(rowText);
    expect(hasFee, `Expected payment attempt row to show fee amount, got: ${rowText}`).toBe(true);
    expect(hasNet, `Expected payment attempt row to show net amount, got: ${rowText}`).toBe(true);
}

export async function assertOrganisationPaymentAttemptShowsPspDetails(page: Page, index: number) {
    console.log(`[OrganisationContractDetailPage] Asserting organisation payment attempt ${index} shows PSP details`);
    const row = organisationPaymentAttemptRows(page).nth(index);
    await expect(row).toBeVisible({ timeout: 10000 });
    const rowText = await row.textContent() ?? '';
    expect(rowText).toContain('PSP:');
    expect(rowText).toContain('Session:');
    expect(rowText).toContain('Transaction:');
    expect(rowText).toContain('Correlation:');
}

export async function assertOrganisationStateChangeCount(page: Page, count: number) {
    console.log(`[OrganisationContractDetailPage] Asserting ${count} organisation state change(s) are shown`);
    await expect(organisationStateChangesCard(page)).toBeVisible({ timeout: 10000 });
    const rows = page.getByTestId('organisation-state-change-row');
    const actualCount = await rows.count();
    const texts: string[] = [];
    for (let i = 0; i < actualCount; i++) {
        const text = await rows.nth(i).textContent();
        texts.push(text?.trim() ?? '');
    }
    console.log(`[OrganisationContractDetailPage] Found ${actualCount} organisation state change rows:`);
    texts.forEach((t, i) => console.log(`  [${i}] ${t}`));
    await expect(rows).toHaveCount(count, { timeout: 10000 });
}

export async function assertOrganisationStateChangesInclude(page: Page, minimumCount: number, expectedStates: string[]) {
    console.log(`[OrganisationContractDetailPage] Asserting at least ${minimumCount} organisation state change(s) and states: [${expectedStates.join(', ')}]`);
    await expect(organisationStateChangesCard(page)).toBeVisible({ timeout: 10000 });
    const rows = page.getByTestId('organisation-state-change-row');
    const actualCount = await rows.count();
    expect(actualCount).toBeGreaterThanOrEqual(minimumCount);
    const allText = (await rows.allTextContents()).join(' ');
    for (const state of expectedStates) {
        expect(allText).toContain(state);
    }
}

export async function assertOrganisationTermsLinksVisible(page: Page) {
    console.log('[OrganisationContractDetailPage] Asserting terms and conditions are displayed');
    await expect(page.getByTestId('organisation-terms-links-card')).toBeVisible({ timeout: 10000 });
    await expect(page.getByTestId('organisation-terms-link-row')).toBeVisible({ timeout: 10000 });
}

export async function goBackToOrganisationContractsList(page: Page) {
    console.log('[OrganisationContractDetailPage] Going back to organisation contracts list');
    await backToOrganisationContractsButton(page).click();
    await expect(page).toHaveURL(/\/organisation-contracts$/, { timeout: 10000 });
    await assertOnOrganisationContractsListPage(page);
}
