import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-signed-out',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './signed-out.component.html',
  styleUrl: './signed-out.component.css'
})
export class SignedOutComponent {

  private authService = inject(AuthService);

  signIn(): void {
    this.authService.signIn();
  }
}
