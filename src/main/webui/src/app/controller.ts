import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Config, ModelService, ProductDefinition, ProductDefinitionRequest } from './model.service';

@Injectable({
  providedIn: 'root',
})
export class Controller {

  private modelService = inject(ModelService);
  private http = inject(HttpClient);

  async loadConfig(): Promise<Config> {
    try {
      const config = await firstValueFrom(
        this.http.get<Config>('/public/config')
      );
      this.modelService.setConfig(config);
      return config;
    } catch (error) {
      console.error('Error loading config:', error);
      throw error;
    }
  }

  // Product Definition methods
  loadProductDefinitions() {
    this.modelService.setProductDefinitionsLoading(true);
    this.modelService.setProductDefinitionsError(null);

    this.http.get<ProductDefinition[]>('/api/v1/product-definitions').subscribe({
      next: (definitions) => {
        this.modelService.setProductDefinitions(definitions);
        this.modelService.setProductDefinitionsLoading(false);
      },
      error: (err) => {
        console.error('Error loading product definitions:', err);
        this.modelService.setProductDefinitions([]);
        this.modelService.setProductDefinitionsError('Failed to load product definitions');
        this.modelService.setProductDefinitionsLoading(false);
      }
    });
  }

  async getProductDefinition(id: string): Promise<ProductDefinition | null> {
    try {
      const definition = await firstValueFrom(
        this.http.get<ProductDefinition>(`/api/v1/product-definitions/${id}`)
      );
      this.modelService.setSelectedProductDefinition(definition);
      return definition;
    } catch (error) {
      console.error('Error loading product definition:', error);
      this.modelService.setSelectedProductDefinition(null);
      return null;
    }
  }

  async createProductDefinition(request: ProductDefinitionRequest): Promise<ProductDefinition> {
    try {
      const response = await firstValueFrom(
        this.http.post<ProductDefinition>('/api/v1/product-definitions', request)
      );
      this.loadProductDefinitions();
      return response;
    } catch (error) {
      console.error('Error creating product definition:', error);
      throw error;
    }
  }

  async updateProductDefinition(id: string, request: ProductDefinitionRequest): Promise<ProductDefinition> {
    try {
      const response = await firstValueFrom(
        this.http.put<ProductDefinition>(`/api/v1/product-definitions/${id}`, request)
      );
      this.loadProductDefinitions();
      return response;
    } catch (error) {
      console.error('Error updating product definition:', error);
      throw error;
    }
  }

  async deleteProductDefinition(id: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/v1/product-definitions/${id}`)
      );
      this.loadProductDefinitions();
    } catch (error) {
      console.error('Error deleting product definition:', error);
      throw error;
    }
  }
}
