import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { InfoDialogService } from './info-dialog.service';

@Component({
  selector: 'ux-info-dialog',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './info-dialog.component.html',
  styleUrl: './info-dialog.component.scss'
})
export class InfoDialogComponent {
  private infoService = inject(InfoDialogService);

  state = this.infoService.state$;

  ok(): void {
    this.infoService.handleOk();
  }

  /**
   * Called when the backdrop overlay is clicked. Only dismisses the dialog
   * when it is dismissable; non-dismissable warnings (e.g. "no roles") must
   * stay open so the user cannot interact with the application behind them.
   */
  onOverlayClick(): void {
    if (this.state().config?.dismissable !== false) {
      this.ok();
    }
  }

  onActionLinkClick(): void {
    this.infoService.handleActionLink();
  }
}
