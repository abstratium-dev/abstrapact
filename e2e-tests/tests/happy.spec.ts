import { test, expect } from '@playwright/test';
import {
    assertOnPublicPage,
    assertOnSignedOutPage,
    assertOnDemoPage,
    assertOnTodoPage,
    assertHeaderSignedIn,
    handleAuthServer,
    signIn,
    signOut,
    headerTodoLink,
    headerHomeLink,
    headerSignOutLink,
    headerSignInLink,
    headerPublicLink,
} from '../pages/TODO.page';

const EMAIL = 'test@abstratium.dev';
const PASSWORD = 'secretLong';
const BASE = 'http://localhost:8081';

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Sign in via the header "Sign in" link from any page.
 * Triggers OIDC directly (no /signed-out step).
 * With no lastRoute saved, SignedInComponent redirects to /.
 */
async function signInViaHeader(page: any) {
    await headerSignInLink(page).click();
    await handleAuthServer(page, EMAIL, PASSWORD);
    await assertOnPublicPage(page);
    await assertHeaderSignedIn(page);
}

/**
 * Get authenticated on /demo by: sign in → navigate to /demo.
 * Used as setup for authenticated test cases.
 */
async function signInAndGoToDemo(page: any) {
    await page.goto('/');
    await signInViaHeader(page);
    await headerHomeLink(page).click();
    await assertOnDemoPage(page);
}

// ─── U: Unauthenticated ───────────────────────────────────────────────────────

test.describe('Unauthenticated', () => {

    // U1: on / → click "Public" → /public
    test('U1: click Public in header from / lands on /public', async ({ page }) => {
        await page.goto('/');
        await assertOnPublicPage(page);
        await headerPublicLink(page).click();
        await expect(page).toHaveURL(/\/public/);
        await assertOnPublicPage(page);
    });

    // U2: on /public → click 🏠 → /demo (auth required) → redirected to /signed-out
    test('U2: click home link from /public redirects to /signed-out', async ({ page }) => {
        await page.goto(`${BASE}/public`);
        await assertOnPublicPage(page);
        await headerHomeLink(page).click();
        await assertOnSignedOutPage(page);
    });

    // U3: on / → enter URL /public → /public
    test('U3: entering /public URL from / lands on /public', async ({ page }) => {
        await page.goto('/');
        await assertOnPublicPage(page); // wait for page to fully settle before navigating
        await page.goto(`${BASE}/public`);
        await expect(page).toHaveURL(/\/public/);
        await assertOnPublicPage(page);
    });

    // U4: on /public → enter URL / → /
    test('U4: entering / URL from /public lands on /', async ({ page }) => {
        await page.goto(`${BASE}/public`);
        await assertOnPublicPage(page); // wait for page to fully settle before navigating
        await page.goto('/');
        await expect(page).toHaveURL(/localhost:8081\/$/);
        await assertOnPublicPage(page);
    });

    // U5: enter /TODO → /signed-out → sign in → /TODO
    test('U5: entering /TODO redirects to /signed-out; after sign in lands on /TODO', async ({ page }) => {
        await page.goto(`${BASE}/TODO`);
        await assertOnSignedOutPage(page);
        await signIn(page, EMAIL, PASSWORD);
        await assertOnTodoPage(page);
        await expect(page).toHaveURL(/\/TODO/);
    });

});

// ─── A: Already authenticated ─────────────────────────────────────────────────

test.describe('Authenticated', () => {

    // A1: lastRoute = /demo → enter URL /demo → /demo
    test('A1: entering /demo URL while authenticated lands on /demo', async ({ page }) => {
        await signInAndGoToDemo(page);
        await page.goto(`${BASE}/demo`);
        await assertOnDemoPage(page);
        await expect(page).toHaveURL(/\/demo/);
    });

    // A2: on /demo → click "Public" → /public; still signed in
    test('A2: click Public in header from /demo lands on /public and stays signed in', async ({ page }) => {
        await signInAndGoToDemo(page);
        await headerPublicLink(page).click();
        await assertOnPublicPage(page);
        await expect(page).toHaveURL(/\/public/);
        await expect(headerSignOutLink(page)).toBeVisible();
    });

    // A3: on /demo → click "TODO" → /TODO; still signed in
    test('A3: click TODO in header from /demo lands on /TODO and stays signed in', async ({ page }) => {
        await signInAndGoToDemo(page);
        await headerTodoLink(page).click();
        await assertOnTodoPage(page);
        await expect(page).toHaveURL(/\/TODO/);
        await expect(headerSignOutLink(page)).toBeVisible();
    });

    // A4: on /demo → sign out → /signed-out → sign in → /demo (lastRoute restored)
    test('A4: sign out from /demo then sign in returns to /demo via lastRoute', async ({ page }) => {
        await signInAndGoToDemo(page);
        await signOut(page);
        await assertOnSignedOutPage(page);
        await signIn(page, EMAIL, PASSWORD);
        await assertOnDemoPage(page);
        await expect(page).toHaveURL(/\/demo/);
    });

});

