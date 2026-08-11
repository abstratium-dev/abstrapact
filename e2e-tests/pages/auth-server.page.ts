import { Page } from '@playwright/test';

// ─── Registration (Signup) ─────────────────────────────────────────────────────

/**
 * Register a new user on the abstrauth signup page.
 *
 * PRECONDITION: the page must already be on the auth server sign-in page
 * (i.e. the app's OIDC flow has been triggered so the auth-server session
 * context is active).  The function clicks the "Sign up" link, fills the
 * form, submits, and returns once the browser is back on the sign-in page
 * so the caller can complete sign-in immediately.
 */
export async function registerNewUser(
    page: Page,
    opts: {
        email: string;
        fullName: string;
        orgName: string;
        password: string;
    }
): Promise<void> {
    console.log(`[AuthServer] Registering new user: ${opts.email}`);

    await page.getByRole('link', { name: /sign up/i }).click();

    const emailField = page.getByRole('textbox', { name: /email address/i });
    await emailField.waitFor({ state: 'visible', timeout: 15000 });

    await emailField.fill(opts.email);
    await page.getByRole('textbox', { name: /full name/i }).fill(opts.fullName);
    await page.getByRole('textbox', { name: /organisation name/i }).fill(opts.orgName);

    const passwordFields = page.getByRole('textbox', { name: /password/i });
    await passwordFields.first().fill(opts.password);
    await passwordFields.last().fill(opts.password);

    await page.getByRole('button', { name: /create account/i }).click();

    await page.waitForURL(/abstrauth|auth-t\.abstratium\.dev.*signin/, { timeout: 15000 });
    console.log(`[AuthServer] Registration successful, landed on: ${page.url()}`);
}
