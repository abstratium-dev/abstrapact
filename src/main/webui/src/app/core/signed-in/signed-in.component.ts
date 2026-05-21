import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { RouteTrackingService } from '../route-tracking.service';

@Component({
    selector: 'app-signed-in',
    imports: [],
    template: ``
})
export class SignedInComponent implements OnInit {
    private router = inject(Router);
    private routeTracking = inject(RouteTrackingService);

    ngOnInit(): void {
        const lastRoute = this.routeTracking.getLastRoute();
        const target = lastRoute ?? '/';
        console.debug('[SIGNED-IN] Post-login redirect to:', target);
        this.router.navigateByUrl(target, { replaceUrl: true });
    }
}
