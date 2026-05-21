import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { WINDOW } from './window.token';
import { Router } from '@angular/router';
import { RouteTrackingService } from './route-tracking.service';

export const CLIENT_ID = 'abstratium-abstracore';
export const ISSUER = 'https://abstrauth.abstratium.dev';

export interface Token {
    sub: string; // id of the user
    email_verified: boolean;
    iss: string;
    groups: string[];
    isAuthenticated: boolean;
    client_id: string;
    upn: string;
    auth_method: string;
    name: string;
    exp: number; // expires at
    iat: number; // issued at
    email: string;
    jti: string;
}

export const ANONYMOUS: Token = {
    sub: '2354372b-1704-4b88-9d62-b03395e0131c',
    email_verified: false,
    iss: ISSUER,
    groups: [],
    isAuthenticated: false,
    client_id: CLIENT_ID,
    upn: 'anon@abstratium.dev',
    auth_method: 'none',
    name: 'Anonymous',
    exp: Date.now() + 3650 * 24 * 60 * 60 * 1000,
    iat: Date.now(),
    email: 'anon@abstratium.dev',
    jti: 'aeede9a0-3cc3-4536-81c2-5b47a6952abf',
};

@Injectable({
    providedIn: 'root',
})
export class AuthService {
    private http = inject(HttpClient);
    private router = inject(Router);
    private window = inject(WINDOW);
    private routeTracking = inject(RouteTrackingService);

    token$ = signal<Token>(ANONYMOUS);
    private token = ANONYMOUS;
    private initialized = false;


    /**
     * Initialize auth service by loading user info from backend.
     * Called by APP_INITIALIZER before app starts.
     * 
     * If user is authenticated (has OIDC session), loads their info.
     * If not authenticated, sets ANONYMOUS token.
     */
    initialize(): Observable<void> {
        console.debug('[AUTH] initialize() called');
        if (this.initialized) {
            console.debug('[AUTH] Already initialized, skipping');
            return of(void 0);
        }

        // Capture the raw browser URL *now*, before the Angular Router has a
        // chance to redirect. This is only reliable in the unauthenticated path
        // (to remember where an unauthenticated user was trying to go).
        //
        // SpaRoutingNotFoundMapper redirects unknown paths to
        // /?_spa=<encodedOriginalPath>. Decode that parameter if present so we
        // restore the intended URL rather than '/'.
        const params = new URLSearchParams(this.window.location.search);
        const spaRedirect = params.get('_spa');
        const rawUrl = spaRedirect ? decodeURIComponent(spaRedirect) : this.window.location.pathname + this.window.location.search;
        console.debug('[AUTH] Raw browser URL at bootstrap:', rawUrl);

        return this.http.get<Token>('/api/core/userinfo').pipe(
            tap(token => {
                console.debug('[AUTH] User is authenticated:', token.email);
                this.token = token;
                this.token$.set(token);
                this.initialized = true;
                this.setupTokenExpiryTimer(token.exp);

                // Navigation priority:
                // 1. _spa redirect: server couldn't serve the path directly,
                //    encoded it as /?_spa=<path> — navigate to the decoded path.
                // 2. All other cases: navigate to the actual browser path.
                //    Post-login, LoginResource redirects to /signed-in which is an
                //    Angular route that reads lastRoute and redirects there itself.
                const target = spaRedirect ? rawUrl : this.window.location.pathname;
                console.debug('[AUTH] Navigating to target:', target, '(spaRedirect:', spaRedirect, ', rawUrl:', rawUrl, ')');
                this.router.navigateByUrl(target);
            }),
            catchError((err) => {
                console.debug('[AUTH] User is NOT authenticated, error:', err.status);

                // Persist where the user was trying to go so that after sign-in
                // (which always redirects to /) we can send them back here.
                if (rawUrl && rawUrl !== '/' ) {
                    this.routeTracking.saveRoute(rawUrl);
                }

                // Use ANONYMOUS token
                this.token = ANONYMOUS;
                this.token$.set(ANONYMOUS);
                this.initialized = true;

                // If a _spa redirect brought us here (e.g. /?_spa=%2FTODO), navigate
                // to the intended path so authGuard can fire and redirect to /signed-out.
                if (spaRedirect) {
                    this.router.navigateByUrl(rawUrl);
                }

                return of(ANONYMOUS);
            }),
            map(() => void 0)
        );
    }


    /**
     * Setup timer to redirect to sign-in when session expires.
     * Redirects 1 minute before actual expiry to ensure smooth UX.
     */
    private setupTokenExpiryTimer(exp: number): void {
        const now = Date.now();
        const expiry = new Date(exp * 1000);
        const millisUntilExpiry = expiry.getTime() - now;
        const oneMinLessThanMillisUntilExpiry = Math.max(0, millisUntilExpiry - (1 * 60 * 1000));
        
        console.debug("Token expires in", millisUntilExpiry, "ms, redirecting to sign-out in", oneMinLessThanMillisUntilExpiry, "ms");
        
        setTimeout(() => {
            console.info("Session expired, redirecting to sign-out");
            this.signOut();
        }, oneMinLessThanMillisUntilExpiry);
    }

    getAccessToken() {
        return this.token;
    }

    getEmail() {
        return this.token.email;
    }

    getName() {
        return this.token.name;
    }

    getGroups() {
        return this.token.groups;
    }

    isAuthenticated() {
        return this.token.email !== ANONYMOUS.email;
    }

    isExpired() {
        // exp is in seconds, Date.now() is in milliseconds
        return this.token.exp * 1000 < Date.now();
    }

    isAboutToExpire() {
        // exp is in seconds, Date.now() is in milliseconds
        return this.token.exp * 1000 < Date.now() + 60 * 60 * 1000;
    }

    resetToken() {
        this.token = ANONYMOUS;
        this.token.isAuthenticated = false;
        this.token$.set(this.token);
    }

    signIn(): void {
        // Navigate to the login endpoint which triggers OIDC authentication
        // This is a BROWSER navigation (not XHR), so Quarkus OIDC can redirect properly
        // Flow:
        // 1. Browser navigates to /api/auth/login
        // 2. Quarkus OIDC returns 302 to https://auth.abstratium.dev/oauth2/authorize (with PKCE)
        // 3. User authenticates at auth server
        // 4. Auth server redirects to /oauth/callback with authorization code
        // 5. Quarkus exchanges code for tokens and creates session cookie
        // 6. Quarkus redirects back to /api/auth/login (restore-path-after-redirect)
        // 7. LoginResource redirects to /signed-in (Angular route)
        // 8. SignedInComponent restores lastRoute from localStorage
        // 9. AuthService fetches user info, user is authenticated
        this.window.location.href = '/api/auth/login';
    }

    signOut() {
        console.debug('[AUTH] signout() called');
        this.resetToken();
        console.debug('[AUTH] Calling logout endpoint to invalidate session');

        // don't follow the redirect, just call the endpoint, then navigate to signed-out.
        // this prevents the browser going to the logout url and following 
        // the redirect to /signed-out, which results in a 404,
        // since quinoa is configured to ignore calls to /api and so quarkus
        // sends a 404 since it can't find signed-out. using navigation, we don't
        // lose the angular application context.
        this.http.get('/api/auth/logout', {
            redirect: 'manual'
        }).subscribe({
            next: () => {
                console.debug('[AUTH] Logout endpoint called successfully');
                this.router.navigate(['/signed-out']);
            },
            error: (err) => {
                console.error('[AUTH] Error calling logout endpoint:', err);
                this.router.navigate(['/signed-out']);
            }
        });
    }

    hasRole(role: string): boolean {
        return this.token.groups.includes(role);
    }
}
