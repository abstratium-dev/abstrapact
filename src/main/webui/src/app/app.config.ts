import { HttpClient, provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { AuthService } from './core/auth.service';
import { Controller } from './controller';
import { RouteTrackingService } from './core/route-tracking.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN',
      })
    ),
    provideAppInitializer(() => {
      const controller = inject(Controller);
      // Load config first (doesn't require authentication)
      return controller.loadConfig();
    }),
    provideAppInitializer(() => {
      const authService = inject(AuthService);
      // Convert Observable to Promise so Angular waits for initialization
      return firstValueFrom(authService.initialize());
    }),
    provideAppInitializer(() => {
      const routeTracking = inject(RouteTrackingService);
      // Start persisting route changes to localStorage after app is initialised
      routeTracking.start();
    }),
  ]
};
