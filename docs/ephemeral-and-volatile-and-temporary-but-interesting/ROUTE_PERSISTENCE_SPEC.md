# Route Persistence After Login / Refresh

## How it works

`localStorage['lastRoute']` is the authoritative record of where the user was. `RouteTrackingService` writes every `NavigationEnd` URL to it, skipping `/signed-out`.

On bootstrap, `AuthService.initialize()` calls `/api/core/userinfo`:

- **Authenticated** → read `lastRoute`, navigate there (fallback: `/`).
- **Unauthenticated** → save `window.location` (or `_spa` param) to `lastRoute`; if navigated via `_spa` redirect, re-navigate to the intended path so `authGuard` can redirect to `/signed-out`.

`/signed-out` is never persisted — session expiry navigates there but `lastRoute` retains the last meaningful page, so the next sign-in returns there.

## Routes

| Path | Auth required | Component |
|------|:---:|-----------|
| `/` | No | `PublicComponent` |
| `/public` | No | `PublicComponent` |
| `/demo` | Yes | `DemoComponent` |
| `/TODO` | Yes | `TodoComponent` |
| `/signed-in` | Yes | `SignedInComponent` — reads `lastRoute`, redirects |
| `/signed-out` | No | `SignedOutComponent` |

## E2E Test Scenarios

> `lastRoute` = `localStorage['lastRoute']`; ✅ passing; ❓ failing/untested
>
> "Fresh context" means a new browser context with no cookies and empty `localStorage` — the default for each Playwright test.

### Unauthenticated

| # | Starting state | Action | Expected outcome | Status |
|---|---------------|--------|-----------------|--------|
| U1 | Fresh context, on `/` | Click "Public" in header | Lands on `/public` | ✅ |
| U2 | Fresh context, on `/public` | Click 🏠 in header | Lands on `/demo`, redirected to `/signed-out` (auth required) | ✅ |
| U3 | Fresh context, on `/` | Enter URL `/public` | Lands on `/public` | ✅ |
| U4 | Fresh context, on `/public` | Enter URL `/` | Lands on `/` | ✅ |
| U5 | Fresh context | Enter URL `/TODO` | Redirected to `/signed-out`; click Sign In → lands on `/TODO` | ✅ |

### Already authenticated

| # | Starting state | Action | Expected outcome | Status |
|---|---------------|--------|-----------------|--------|
| A1 | Authenticated, `lastRoute = /demo` | Enter URL `/demo` | Lands on `/demo` | ✅ |
| A2 | Authenticated, on `/demo` | Click "Public" in header | Lands on `/public`; still signed in | ✅ |
| A3 | Authenticated, on `/demo` | Click "TODO" in header | Lands on `/TODO`; still signed in | ✅ |
| A4 | Authenticated, on `/demo` | Click "Sign out"; click "Sign in" | Sign out → `/signed-out`; sign in → `/demo` (restored via `lastRoute`) | ✅ |
