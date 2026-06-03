import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ModelService } from '../model.service';

export interface ConfigUpdateRequest {
  currencyCode: string;
  locale: string;
}

export interface ConfigResponse {
  id: string;
  organisationId: string;
  currencyCode: string;
  locale: string;
}

@Injectable({
  providedIn: 'root',
})
export class ConfigController {

  private modelService = inject(ModelService);
  private http = inject(HttpClient);

  async loadConfig(): Promise<ConfigResponse> {
    try {
      const config = await firstValueFrom(
        this.http.get<ConfigResponse>('/api/config')
      );
      return config;
    } catch (error) {
      console.error('Error loading config:', error);
      throw error;
    }
  }

  async updateConfig(request: ConfigUpdateRequest): Promise<ConfigResponse> {
    try {
      const response = await firstValueFrom(
        this.http.put<ConfigResponse>('/api/config', request)
      );
      const currentConfig = this.modelService.config$();
      this.modelService.setConfig({
        logLevel: currentConfig?.logLevel || 'INFO',
        warningMessage: currentConfig?.warningMessage || '',
        warningBgColor: currentConfig?.warningBgColor || '#fff3cd',
        brandLogoUrl: currentConfig?.brandLogoUrl || '',
        brandLogoAlt: currentConfig?.brandLogoAlt || '',
        brandName: currentConfig?.brandName || '',
        currencyCode: response.currencyCode,
        locale: response.locale
      });
      return response;
    } catch (error) {
      console.error('Error updating config:', error);
      throw error;
    }
  }
}
