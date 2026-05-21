import { CommonModule } from '@angular/common';
import { Component, effect, inject, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService, Token } from '../core/auth.service';
import { ThemeService } from '../core/theme.service';

@Component({
    selector: 'header',
    imports: [RouterLink, RouterLinkActive, CommonModule],
    templateUrl: './header.component.html',
    styleUrl: './header.component.scss',
})
export class HeaderComponent implements OnInit {
    private authService = inject(AuthService);
    themeService = inject(ThemeService);

    token!: Token;
    isSignedIn = false;

    constructor() {
        effect(() => {
            this.token = this.authService.token$();
            this.isSignedIn = this.token.isAuthenticated;
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
}
