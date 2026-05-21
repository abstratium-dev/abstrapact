import { Injectable, inject } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';

export const LAST_ROUTE_KEY = 'lastRoute';
export const EXCLUDED_ROUTES = ['/signed-out', '/signed-in'];

@Injectable({
    providedIn: 'root',
})
export class RouteTrackingService {
    private router = inject(Router);

    /**
     * Start listening to router NavigationEnd events and persist the current
     * URL to localStorage, unless the route is one that should never be
     * restored (e.g. /signed-out).
     */
    start(): void {
        this.router.events
            .pipe(filter(event => event instanceof NavigationEnd))
            .subscribe((event: NavigationEnd) => {
                const url = event.urlAfterRedirects;
                if (!EXCLUDED_ROUTES.some(excluded => url.startsWith(excluded))) {
                    localStorage.setItem(LAST_ROUTE_KEY, url);
                    console.debug('[ROUTE TRACKING] Saved route:', url);
                }
            });
    }

    /**
     * Return the last persisted route, or null if none / excluded.
     */
    getLastRoute(): string | null {
        const saved = localStorage.getItem(LAST_ROUTE_KEY);
        if (!saved) {
            return null;
        }
        if (EXCLUDED_ROUTES.some(excluded => saved.startsWith(excluded))) {
            return null;
        }
        return saved;
    }

    /**
     * Save a route explicitly (used when user is unauthenticated and we want
     * to remember where they were trying to go before the login redirect).
     * Does nothing if the route is excluded.
     */
    saveRoute(url: string): void {
        if (!url || EXCLUDED_ROUTES.some(excluded => url.startsWith(excluded))) {
            return;
        }
        localStorage.setItem(LAST_ROUTE_KEY, url);
        console.debug('[ROUTE TRACKING] Explicitly saved route:', url);
    }
}
