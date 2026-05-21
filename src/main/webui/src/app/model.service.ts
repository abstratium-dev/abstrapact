import { Injectable, signal, Signal } from '@angular/core';

export interface Demo {
  id: string;
}

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

  private demos = signal<Demo[]>([]);
  private demosLoading = signal<boolean>(false);
  private demosError = signal<string | null>(null);
  private config = signal<Config | null>(null);
  private warningMessage = signal<string>('');
  private warningBgColor = signal<string>('#fff3cd');
  private brandLogoUrl = signal<string>('');
  private brandLogoAlt = signal<string>('');
  private brandName = signal<string>('');

  demos$: Signal<Demo[]> = this.demos.asReadonly();
  demosLoading$: Signal<boolean> = this.demosLoading.asReadonly();
  demosError$: Signal<string | null> = this.demosError.asReadonly();
  config$: Signal<Config | null> = this.config.asReadonly();
  warningMessage$: Signal<string> = this.warningMessage.asReadonly();
  warningBgColor$: Signal<string> = this.warningBgColor.asReadonly();
  brandLogoUrl$: Signal<string> = this.brandLogoUrl.asReadonly();
  brandLogoAlt$: Signal<string> = this.brandLogoAlt.asReadonly();
  brandName$: Signal<string> = this.brandName.asReadonly();

  setDemos(demos: Demo[]) {
    this.demos.set(demos);
  }

  setDemosLoading(loading: boolean) {
    this.demosLoading.set(loading);
  }

  setDemosError(error: string | null) {
    this.demosError.set(error);
  }

  setConfig(config: Config) {
    this.config.set(config);
    if (config.warningMessage === '-') {
      this.warningMessage.set('');
    } else {
      this.warningMessage.set(config.warningMessage);
    }
    this.warningBgColor.set(config.warningBgColor);
    this.brandLogoUrl.set(config.brandLogoUrl);
    this.brandLogoAlt.set(config.brandLogoAlt);
    this.brandName.set(config.brandName);
  }
}
