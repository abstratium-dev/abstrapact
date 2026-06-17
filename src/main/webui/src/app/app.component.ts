import { Component, inject, Signal } from '@angular/core';
import { RouterModule, RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from './header/header.component';
import { ToastComponent } from './core/toast/toast.component';
import { ConfirmDialogComponent } from './core/confirm-dialog/confirm-dialog.component';
import { CookieNoticeComponent } from './core/cookie-notice/cookie-notice.component';
import { MaintenanceComponent } from './core/maintenance/maintenance.component';
import { ModelService } from './model.service';
import { DomainService } from './core/domain.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, HeaderComponent, ToastComponent, ConfirmDialogComponent, CookieNoticeComponent, MaintenanceComponent, RouterModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'abstrapact';
  copyrightYear: any;
  protected warningMessage$ = inject(ModelService).warningMessage$;
  protected warningBgColor$ = inject(ModelService).warningBgColor$;
  isCorrectDomain: boolean;
  legalContent$: Signal<string | null>;

  constructor() {
    this.copyrightYear = new Date().getFullYear();
    if(this.copyrightYear > 2026) {
      this.copyrightYear = '2026 - ' + this.copyrightYear;
    }

    this.isCorrectDomain = inject(DomainService).isAbstratiumDomain;
    this.legalContent$ = inject(ModelService).legalContent$;
  }
}
