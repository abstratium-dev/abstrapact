import { CommonModule } from '@angular/common';
import { Component, effect, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, Token } from '../core/auth.service';
import { ThemeService } from '../core/theme.service';
import { ModelService } from '../model.service';

@Component({
    selector: 'header',
    imports: [RouterLink, RouterLinkActive, CommonModule],
    templateUrl: './header.component.html',
    styleUrl: './header.component.scss',
})
export class HeaderComponent implements OnInit {
    private authService = inject(AuthService);
    themeService = inject(ThemeService);
    private modelService = inject(ModelService);
    protected brandLogoUrl$ = this.modelService.brandLogoUrl$;
    protected brandLogoAlt$ = this.modelService.brandLogoAlt$;
    protected brandName$ = this.modelService.brandName$;

    token!: Token;
    isSignedIn = false;
    sessionFraction = 1;
    sessionMinutesRemaining = 0;

    constructor() {
        effect(() => {
            this.token = this.authService.token$();
            this.isSignedIn = this.token.isAuthenticated;
        });

        effect(() => {
            this.sessionFraction = this.authService.sessionFraction$();
            this.sessionMinutesRemaining = this.authService.sessionMinutesRemaining$();
        });
    }

    ngOnInit(): void {
        // No initialization needed
    }

    toggleTheme(): void {
        this.themeService.toggleTheme();
    }

    signOut() {
        this.authService.signOut();
    }

    signIn() {
        this.authService.signIn();
    }

    get sessionClockDashoffset(): number {
        const circumference = 2 * Math.PI * 7;
        return circumference * (1 - this.sessionFraction);
    }
}
