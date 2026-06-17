import { inject, Injectable } from '@angular/core';
import { WINDOW } from './window.token';

@Injectable({
  providedIn: 'root',
})
export class DomainService {
  readonly isAbstratiumDomain: boolean;

  constructor() {
    const hostname = inject(WINDOW).location.hostname;
    this.isAbstratiumDomain =
      hostname.endsWith('.abstratium.dev') ||
      hostname === 'localhost';
  }
}
