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
}
