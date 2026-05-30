import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-legal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './legal.component.html',
  styleUrl: './legal.component.scss'
})
export class LegalComponent {
  copyrightYears: string;

  constructor() {
    const year = new Date().getFullYear();
    this.copyrightYears = year > 2026 ? `2026 - ${year}` : String(year);
  }
}
