import { expect, Page } from '@playwright/test';

// ─── Cookie notice ────────────────────────────────────────────────────────────

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

// ─── Signed-Out / Login page ──────────────────────────────────────────────────

export function signedOutHeading(page: Page) {
    return page.getByTestId('signed-out-heading');
}

export function signInButton(page: Page) {
    return page.getByTestId('sign-in-btn');
}

export async function assertOnSignedOutPage(page: Page) {
    console.log('[LoginPage] Asserting on signed-out page');
    await expect(signedOutHeading(page)).toBeVisible({ timeout: 10000 });
    await expect(signedOutHeading(page)).toHaveText('Sign In Required');
}

// ─── Auth server login form ───────────────────────────────────────────────────

/**
 * Handle the auth-server states after any action that triggers the OIDC flow.
 * Handles three possible states:
 *  1. Auth server shows a login form  → fill credentials, then maybe consent
 *  2. Auth server shows consent only  → click Approve
 *  3. App redirected straight back    → nothing extra needed
 */
export async function handleAuthServer(page: Page, email: string, password: string) {
    const emailField = page.getByRole('textbox', { name: /email/i });
    const approveBtn = page.getByRole('button', { name: 'Approve' });
    const onApp = () => /localhost/.test(page.url());

    await Promise.race([
        emailField.waitFor({ state: 'visible', timeout: 15000 }),
        approveBtn.waitFor({ state: 'visible', timeout: 15000 }),
        page.waitForURL(/localhost/, { timeout: 15000 }),
    ]);

    console.log(`[AuthServer] URL after trigger: ${page.url()}`);

    // The shared test IdP rate-limits (HTTP 429) under load, which can stall the
    // post-consent redirect back to the app. Retry the consent step a few times;
    // if the page left the consent screen for an error state, reload to re-enter
    // the OIDC flow.
    const maxAttempts = 3;
    for (let attempt = 1; attempt <= maxAttempts && !onApp(); attempt++) {
        if (await emailField.isVisible().catch(() => false)) {
            console.log('[AuthServer] Login form detected, filling credentials');
            await emailField.fill(email);
            await page.getByRole('textbox', { name: /password/i }).fill(password);
            await page.getByRole('button', { name: /^sign in$/i }).click();
            await approveBtn.waitFor({ state: 'visible', timeout: 10000 }).catch(() => null);
        }

        if (await approveBtn.isVisible().catch(() => false)) {
            console.log('[AuthServer] Consent screen detected, approving');
            await approveBtn.click();
        }

        try {
            await page.waitForURL(/localhost/, { timeout: 15000 });
        } catch (e) {
            if (attempt === maxAttempts) throw e;
            console.log(`[AuthServer] Redirect to app timed out (attempt ${attempt}/${maxAttempts}), retrying`);
            await page.waitForTimeout(4000);
            if (!await emailField.isVisible().catch(() => false)
                && !await approveBtn.isVisible().catch(() => false)) {
                console.log('[AuthServer] Neither login nor consent visible, reloading OIDC flow');
                await page.reload().catch(() => null);
                await Promise.race([
                    emailField.waitFor({ state: 'visible', timeout: 15000 }),
                    approveBtn.waitFor({ state: 'visible', timeout: 15000 }),
                    page.waitForURL(/localhost/, { timeout: 15000 }),
                ]).catch(() => null);
            }
        }
    }

    if (onApp()) {
        console.log('[AuthServer] Complete, URL: ' + page.url());
    } else {
        console.log('[AuthServer] Did not return to app, URL: ' + page.url());
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

export function headerHomeLink(page: Page) {
    return page.locator('#home-link');
}

export function headerSignOutLink(page: Page) {
    return page.locator('#signout-link');
}

export function headerSignInLink(page: Page) {
    return page.locator('#signin-link');
}

export async function signOut(page: Page) {
    console.log('[Header] Signing out');
    await headerSignOutLink(page).click();
    await assertOnSignedOutPage(page);
}

export async function assertHeaderSignedIn(page: Page) {
    console.log('[Header] Asserting header shows signed-in state');
    await expect(headerHomeLink(page)).toBeVisible({ timeout: 10000 });
    await expect(page.locator('#terms-link')).toBeVisible();
    await expect(page.locator('#products-link')).toBeVisible();
    await expect(headerSignOutLink(page)).toBeVisible();
}

// ─── Sign-in convenience ─────────────────────────────────────────────────────

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
