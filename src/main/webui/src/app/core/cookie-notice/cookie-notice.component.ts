import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { WINDOW } from '../window.token';

const COOKIE_CONSENT_KEY = 'cookieNoticeAccepted';

@Component({
  selector: 'cookie-notice',
  imports: [CommonModule, RouterLink],
  templateUrl: './cookie-notice.component.html',
  styleUrl: './cookie-notice.component.scss',
})
export class CookieNoticeComponent {
  private readonly window = inject(WINDOW);
  protected visible = false;

  constructor() {
    const accepted = this.window?.localStorage.getItem(COOKIE_CONSENT_KEY);
    this.visible = accepted !== 'true';
  }

  accept(): void {
    this.window?.localStorage.setItem(COOKIE_CONSENT_KEY, 'true');
    this.visible = false;
  }

  decline(): void {
    this.window?.location.assign('https://www.google.com');
  }
}
