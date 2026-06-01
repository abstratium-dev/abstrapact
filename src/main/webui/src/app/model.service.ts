import { Injectable, signal, Signal } from '@angular/core';

export interface Config {
  logLevel: string;
  warningMessage: string;
  warningBgColor: string;
  brandLogoUrl: string;
  brandLogoAlt: string;
  brandName: string;
}

@Injectable({
  providedIn: 'root',
})
export class ModelService {

  private config = signal<Config | null>(null);
  private warningMessage = signal<string>('');
  private warningBgColor = signal<string>('#fff3cd');
  private readonly defaultBrandLogoUrl = 'https://abstratium.dev/abstratium-logo-small.png';
  private readonly defaultBrandLogoAlt = 'Abstratium Logo';
  private readonly defaultBrandName = 'ABSTRATIUM';

  private brandLogoUrl = signal<string>(this.defaultBrandLogoUrl);
  private brandLogoAlt = signal<string>(this.defaultBrandLogoAlt);
  private brandName = signal<string>(this.defaultBrandName);

  config$: Signal<Config | null> = this.config.asReadonly();
  warningMessage$: Signal<string> = this.warningMessage.asReadonly();
  warningBgColor$: Signal<string> = this.warningBgColor.asReadonly();
  brandLogoUrl$: Signal<string> = this.brandLogoUrl.asReadonly();
  brandLogoAlt$: Signal<string> = this.brandLogoAlt.asReadonly();
  brandName$: Signal<string> = this.brandName.asReadonly();

  setConfig(config: Config) {
    this.config.set(config);
    if (config.warningMessage === '-') {
      this.warningMessage.set('');
    } else {
      this.warningMessage.set(config.warningMessage);
    }
    this.warningBgColor.set(config.warningBgColor);
    this.brandLogoUrl.set(config.brandLogoUrl || this.defaultBrandLogoUrl);
    this.brandLogoAlt.set(config.brandLogoAlt || this.defaultBrandLogoAlt);
    this.brandName.set(config.brandName || this.defaultBrandName);
  }
}
