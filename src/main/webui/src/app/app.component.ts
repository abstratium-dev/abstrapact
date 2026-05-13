import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from './header/header.component';
import { ToastComponent } from './core/toast/toast.component';
import { ConfirmDialogComponent } from './core/confirm-dialog/confirm-dialog.component';
import { ModelService } from './model.service';

@Component({
  selector: 'app-root',
  imports: [CommonModule, RouterOutlet, HeaderComponent, ToastComponent, ConfirmDialogComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {
  title = 'TODO';
  copyrightYear: any;
  protected warningMessage$ = inject(ModelService).warningMessage$;

  constructor() {
    this.copyrightYear = new Date().getFullYear();
    if(this.copyrightYear > 2026) {
      this.copyrightYear = '2026 - ' + this.copyrightYear;
    }
  }
}
