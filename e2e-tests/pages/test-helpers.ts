import { Page } from '@playwright/test';
import {
    handleAuthServer,
    assertHeaderSignedIn,
    headerSignInLink,
} from './TODO.page';

export async function dismissCookieNoticeIfPresent(page: Page) {
    console.log('[CookieNotice] Checking for cookie notice');
    const gotItButton = page.locator('.cookie-notice-actions button', { hasText: 'Got it!' });
    try {
        await gotItButton.waitFor({ state: 'visible', timeout: 3000 });
        console.log('[CookieNotice] Dismissing cookie notice');
        await gotItButton.click();
        await gotItButton.waitFor({ state: 'hidden', timeout: 3000 });
    } catch (e) {
        console.log('[CookieNotice] No cookie notice found or already dismissed');
    }
}

const EMAIL = 'test@abstratium.dev';
const PASSWORD = 'secretLong';

export async function signInViaHeader(page: Page) {
    console.log('[TestHelper] Signing in via header');
    await dismissCookieNoticeIfPresent(page);
    const alreadySignedIn = await page.locator('#signout-link').isVisible().catch(() => false);
    if (alreadySignedIn) {
        console.log('[TestHelper] Already signed in, skipping auth flow');
        await assertHeaderSignedIn(page);
        return;
    }
    await headerSignInLink(page).click();
    await handleAuthServer(page, EMAIL, PASSWORD);
    await assertHeaderSignedIn(page);
}

export function testStepLogger(testName: string) {
    let step = 0;
    return (message: string) => console.log(`[${testName} ${++step}] ${message}`);
}
