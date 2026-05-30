import { expect, Page } from '@playwright/test';

// ─── Public page (home, no auth required) ────────────────────────────────────

export function publicPage(page: Page) {
    return page.getByTestId('public-page');
}

export async function assertOnPublicPage(page: Page) {
    console.log('[PublicPage] Asserting on public page');
    await expect(publicPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Public Information' })).toBeVisible();
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

    await Promise.race([
        emailField.waitFor({ state: 'visible', timeout: 15000 }),
        approveBtn.waitFor({ state: 'visible', timeout: 15000 }),
        page.waitForURL(/localhost/, { timeout: 15000 }),
    ]);

    console.log(`[AuthServer] URL after trigger: ${page.url()}`);

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
        await page.waitForURL(/localhost/, { timeout: 15000 });
    } else {
        console.log('[AuthServer] No consent screen, already back on app');
    }

    console.log(`[AuthServer] Complete, URL: ${page.url()}`);
}

/**
 * Click the Sign In button on the /signed-out page and complete the OIDC flow.
 */
export async function signIn(page: Page, email: string, password: string) {
    console.log(`[LoginPage] Signing in as ${email}`);
    await signInButton(page).click();
    await handleAuthServer(page, email, password);
}

// ─── Header ───────────────────────────────────────────────────────────────────

export function headerTodoLink(page: Page) {
    return page.locator('#TODO-link');
}

export function headerHomeLink(page: Page) {
    return page.locator('#home-link');
}

export function headerSignOutLink(page: Page) {
    return page.locator('#signout-link');
}

export function headerSignInLink(page: Page) {
    return page.locator('#signin-link');
}

export function headerPublicLink(page: Page) {
    return page.locator('#public-link');
}

export async function signOut(page: Page) {
    console.log('[Header] Signing out');
    await headerSignOutLink(page).click();
    await assertOnSignedOutPage(page);
}

export async function assertHeaderSignedIn(page: Page) {
    console.log('[Header] Asserting header shows signed-in state');
    await expect(headerTodoLink(page)).toBeVisible({ timeout: 10000 });
    await expect(headerHomeLink(page)).toBeVisible();
    await expect(headerSignOutLink(page)).toBeVisible();
}

// ─── Demo page (home) ─────────────────────────────────────────────────────────

export function demoPage(page: Page) {
    return page.getByTestId('demo-page');
}

export async function assertOnDemoPage(page: Page) {
    console.log('[DemoPage] Asserting on demo page');
    await expect(demoPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'Demo Items' })).toBeVisible();
}

// ─── TODO page ────────────────────────────────────────────────────────────────

export function todoPage(page: Page) {
    return page.getByTestId('todo-page');
}

export async function assertOnTodoPage(page: Page) {
    console.log('[TodoPage] Asserting on TODO page');
    await expect(todoPage(page)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: 'TODO' })).toBeVisible();
}
