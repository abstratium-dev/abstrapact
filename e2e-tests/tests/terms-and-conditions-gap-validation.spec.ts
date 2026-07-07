import { test, expect, Page } from '@playwright/test';
import { signInViaHeader, testStepLogger } from '../pages/test-helpers';
import {
    navigateToTermsAndConditions,
    clickAddTermsAndConditions,
    createTermsAndConditions,
    assertOnTermsAndConditionsListPage,
    assertOnTermsAndConditionsFormPage,
    assertFormErrorContains,
    assertNoFormError,
    assertTermsExists,
    deleteTermsByCode,
} from '../pages/terms-and-conditions.page';

async function cleanupTestData(page: Page, codes: string[]) {
    console.log(`[TestHelper] Cleaning up test data for codes: ${codes.join(', ')}`);
    await navigateToTermsAndConditions(page);
    for (const code of codes) {
        try {
            await deleteTermsByCode(page, code);
        } catch (e) {
            console.log(`[TestHelper] Could not delete ${code}, may not exist: ${e}`);
        }
    }
}

// ─── Terms and Conditions Gap Validation Tests ────────────────────────────────

test.describe('Terms and Conditions Gap Validation', () => {

    test.beforeEach(async ({ page }: { page: Page }) => {
        await page.goto('/');
        await signInViaHeader(page);
        // Remove any stale test data from previous runs/failed cleanups before this test creates anything.
        await cleanupTestData(page, ['GAP-TEST', 'OVERLAP-TEST', 'CHAIN-TEST', 'NULL-TEST']);
        console.log('[TestHelper] Cleanup complete, taking screenshot of current list state');
        await page.screenshot({ path: `test-results/after-cleanup-${Date.now()}.png` }).catch(() => null);
    });

    // GV1: Create two terms with same code - valid continuous chain (no gap)
    test('GV1: valid continuous chain without gap should succeed', async ({ page }: { page: Page }) => {
        const log = testStepLogger('GV1');
        log('Navigate to terms and conditions list');
        await navigateToTermsAndConditions(page);
        log('Open add terms form');
        await clickAddTermsAndConditions(page);

        // Create first term: Jan 1, 2024 to Jun 30, 2024
        log('Create first term CHAIN-TEST');
        await createTermsAndConditions(page, {
            code: 'CHAIN-TEST',
            title: 'First Terms',
            contentFr: 'Premier contenu',
            contentDe: 'Erster Inhalt',
            contentEn: 'First content',
            version: '1.0',
            effectiveFrom: '2024-01-01',
            effectiveUntil: '2024-06-30',
        });

        // Should redirect to list page
        log('Assert redirect to list and first term exists');
        await assertOnTermsAndConditionsListPage(page);
        await assertTermsExists(page, 'CHAIN-TEST');

        // Create second term: Jul 1, 2024 onwards (continuous, no gap)
        log('Open add terms form again');
        await clickAddTermsAndConditions(page);
        log('Create second term CHAIN-TEST');
        await createTermsAndConditions(page, {
            code: 'CHAIN-TEST',
            title: 'Second Terms',
            contentFr: 'Deuxième contenu',
            contentDe: 'Zweiter Inhalt',
            contentEn: 'Second content',
            version: '2.0',
            effectiveFrom: '2024-07-01',
            effectiveUntil: '',
        });

        // Should succeed - no error
        log('Assert final redirect to list');
        await assertOnTermsAndConditionsListPage(page);
    });

    // GV2: Create two terms with gap between them - should fail
    test('GV2: gap between terms should be rejected with error', async ({ page }: { page: Page }) => {
        const log = testStepLogger('GV2');
        log('Navigate to terms and conditions list');
        await navigateToTermsAndConditions(page);
        log('Open add terms form');
        await clickAddTermsAndConditions(page);

        // Create first term: Jan 1, 2024 to Jun 30, 2024
        log('Create first term GAP-TEST');
        await createTermsAndConditions(page, {
            code: 'GAP-TEST',
            title: 'First Terms',
            contentFr: 'Premier contenu',
            contentDe: 'Erster Inhalt',
            contentEn: 'First content',
            version: '1.0',
            effectiveFrom: '2024-01-01',
            effectiveUntil: '2024-06-30',
        });

        // Should redirect to list page
        log('Assert redirect to list');
        await assertOnTermsAndConditionsListPage(page);

        // Create second term with gap: Aug 1, 2024 onwards (gap of July)
        log('Open add terms form again');
        await clickAddTermsAndConditions(page);
        log('Create second term GAP-TEST with a gap');
        await createTermsAndConditions(page, {
            code: 'GAP-TEST',
            title: 'Second Terms',
            contentFr: 'Deuxième contenu',
            contentDe: 'Zweiter Inhalt',
            contentEn: 'Second content',
            version: '2.0',
            effectiveFrom: '2024-08-01',  // Gap: should be 2024-07-01
            effectiveUntil: '',
        });

        // Should show error about gap
        log('Assert gap error is shown');
        await assertOnTermsAndConditionsFormPage(page);
        await assertFormErrorContains(page, 'Gap');
    });

    // GV3: Create two terms with overlapping dates - should fail
    test('GV3: overlapping terms should be rejected with error', async ({ page }: { page: Page }) => {
        const log = testStepLogger('GV3');
        log('Navigate to terms and conditions list');
        await navigateToTermsAndConditions(page);
        log('Open add terms form');
        await clickAddTermsAndConditions(page);

        // Create first term: Jan 1, 2024 to Dec 31, 2024
        log('Create first term OVERLAP-TEST');
        await createTermsAndConditions(page, {
            code: 'OVERLAP-TEST',
            title: 'First Terms',
            contentFr: 'Premier contenu',
            contentDe: 'Erster Inhalt',
            contentEn: 'First content',
            version: '1.0',
            effectiveFrom: '2024-01-01',
            effectiveUntil: '2024-12-31',
        });

        // Should redirect to list page
        log('Assert redirect to list');
        await assertOnTermsAndConditionsListPage(page);

        // Create second term with overlap: Jun 1, 2024 onwards (overlaps with first)
        log('Open add terms form again');
        await clickAddTermsAndConditions(page);
        log('Create second term OVERLAP-TEST with overlap');
        await createTermsAndConditions(page, {
            code: 'OVERLAP-TEST',
            title: 'Second Terms',
            contentFr: 'Deuxième contenu',
            contentDe: 'Zweiter Inhalt',
            contentEn: 'Second content',
            version: '2.0',
            effectiveFrom: '2024-06-01',  // Overlaps with first term
            effectiveUntil: '',
        });

        // Should show error about overlap
        log('Assert overlap error is shown');
        await assertOnTermsAndConditionsFormPage(page);
        await assertFormErrorContains(page, 'overlap');
    });

    // GV4: Open-ended chain (null effectiveFrom and null effectiveUntil) - should succeed
    test('GV4: open-ended chain with null dates should succeed', async ({ page }: { page: Page }) => {
        const log = testStepLogger('GV4');
        log('Navigate to terms and conditions list');
        await navigateToTermsAndConditions(page);
        log('Open add terms form');
        await clickAddTermsAndConditions(page);

        // Create first term: beginning of time to Dec 31, 2024
        log('Create first term NULL-TEST (null effectiveFrom)');
        await createTermsAndConditions(page, {
            code: 'NULL-TEST',
            title: 'First Terms',
            contentFr: 'Premier contenu',
            contentDe: 'Erster Inhalt',
            contentEn: 'First content',
            version: '1.0',
            effectiveFrom: '',  // null - from beginning of time
            effectiveUntil: '2024-12-31',
        });

        // Should redirect to list page
        log('Assert redirect to list and first term exists');
        await assertOnTermsAndConditionsListPage(page);
        await assertTermsExists(page, 'NULL-TEST');

        // Create second term: Jan 1, 2025 onwards (null effectiveUntil = forever)
        log('Open add terms form again');
        await clickAddTermsAndConditions(page);
        log('Create second term NULL-TEST (null effectiveUntil)');
        await createTermsAndConditions(page, {
            code: 'NULL-TEST',
            title: 'Second Terms',
            contentFr: 'Deuxième contenu',
            contentDe: 'Zweiter Inhalt',
            contentEn: 'Second content',
            version: '2.0',
            effectiveFrom: '2025-01-01',
            effectiveUntil: '',  // null - forever
        });

        // Should succeed - no error
        log('Assert final redirect to list');
        await assertOnTermsAndConditionsListPage(page);
    });

    // GV5: Multiple terms with null effectiveFrom - should fail
    test('GV5: multiple terms with null effectiveFrom should be rejected', async ({ page }: { page: Page }) => {
        const log = testStepLogger('GV5');
        log('Navigate to terms and conditions list');
        await navigateToTermsAndConditions(page);
        log('Open add terms form');
        await clickAddTermsAndConditions(page);

        // Create first term: null effectiveFrom to Jun 30, 2024
        log('Create first term NULL-TEST (null effectiveFrom)');
        await createTermsAndConditions(page, {
            code: 'NULL-TEST',
            title: 'First Terms',
            contentFr: 'Premier contenu',
            contentDe: 'Erster Inhalt',
            contentEn: 'First content',
            version: '1.0',
            effectiveFrom: '',  // null
            effectiveUntil: '2024-06-30',
        });

        // Should redirect to list page
        log('Assert redirect to list');
        await assertOnTermsAndConditionsListPage(page);

        // Create second term with null effectiveFrom - should fail
        log('Open add terms form again');
        await clickAddTermsAndConditions(page);
        log('Create second term NULL-TEST (null effectiveFrom)');
        await createTermsAndConditions(page, {
            code: 'NULL-TEST',
            title: 'Second Terms',
            contentFr: 'Deuxième contenu',
            contentDe: 'Zweiter Inhalt',
            contentEn: 'Second content',
            version: '2.0',
            effectiveFrom: '',  // null - should fail, only one allowed
            effectiveUntil: '2024-12-31',
        });

        // Should show error about multiple null effectiveFrom
        log('Assert multiple null-effectiveFrom error is shown');
        await assertOnTermsAndConditionsFormPage(page);
        await assertFormErrorContains(page, 'no effective from date');
    });

});
