import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import {
  ProductDefinition, ProductDefinitionRequest,
  PartDefinition, PartRequest, PartAttributeDefinition, PartAttributeRequest,
  CompleteProductRequest, CompleteProductResponse,
  ProductDefinitionsModelService
} from './product-definitions.model.service';

@Injectable({
  providedIn: 'root',
})
export class ProductDefinitionsController {

  private modelService = inject(ProductDefinitionsModelService);
  private http = inject(HttpClient);

  loadProductDefinitions() {
    this.modelService.setProductDefinitionsLoading(true);
    this.modelService.setProductDefinitionsError(null);

    this.http.get<ProductDefinition[]>('/api/product-definitions').subscribe({
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
        this.http.get<ProductDefinition>(`/api/product-definitions/${id}`)
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
        this.http.post<ProductDefinition>('/api/product-definitions', request)
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
        this.http.put<ProductDefinition>(`/api/product-definitions/${id}`, request)
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
        this.http.delete<void>(`/api/product-definitions/${id}`)
      );
      this.loadProductDefinitions();
    } catch (error) {
      console.error('Error deleting product definition:', error);
      throw error;
    }
  }

  // ==================== Complete Product with Parts ====================

  async createCompleteProduct(request: CompleteProductRequest): Promise<ProductDefinition> {
    try {
      const response = await firstValueFrom(
        this.http.post<ProductDefinition>('/api/product-definitions/complete', request)
      );
      this.loadProductDefinitions();
      return response;
    } catch (error) {
      console.error('Error creating complete product:', error);
      throw error;
    }
  }

  async updateCompleteProduct(id: string, request: CompleteProductRequest): Promise<ProductDefinition> {
    try {
      const response = await firstValueFrom(
        this.http.put<ProductDefinition>(`/api/product-definitions/${id}/complete`, request)
      );
      this.loadProductDefinitions();
      return response;
    } catch (error) {
      console.error('Error updating complete product:', error);
      throw error;
    }
  }

  async deleteCompleteProduct(id: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/product-definitions/${id}/complete`)
      );
      this.loadProductDefinitions();
    } catch (error) {
      console.error('Error deleting complete product:', error);
      throw error;
    }
  }

  async getCompleteProduct(id: string): Promise<CompleteProductResponse | null> {
    try {
      const response = await firstValueFrom(
        this.http.get<CompleteProductResponse>(`/api/product-definitions/${id}/complete`)
      );
      return response;
    } catch (error) {
      console.error('Error loading complete product:', error);
      return null;
    }
  }

  // ==================== Part Management ====================

  loadProductParts(productId: string) {
    this.modelService.setProductPartsLoading(true);
    this.modelService.setProductPartsError(null);

    this.http.get<PartDefinition[]>(`/api/product-definitions/${productId}/parts`).subscribe({
      next: (parts) => {
        this.modelService.setProductParts(parts);
        this.modelService.setProductPartsLoading(false);
      },
      error: (err) => {
        console.error('Error loading product parts:', err);
        this.modelService.setProductParts([]);
        this.modelService.setProductPartsError('Failed to load product parts');
        this.modelService.setProductPartsLoading(false);
      }
    });
  }

  async getPart(partId: string): Promise<PartDefinition | null> {
    try {
      const part = await firstValueFrom(
        this.http.get<PartDefinition>(`/api/product-definitions/parts/${partId}`)
      );
      this.modelService.setSelectedPart(part);
      return part;
    } catch (error) {
      console.error('Error loading part:', error);
      this.modelService.setSelectedPart(null);
      return null;
    }
  }

  async createPart(productId: string, part: PartRequest): Promise<PartDefinition> {
    try {
      const response = await firstValueFrom(
        this.http.post<PartDefinition>(`/api/product-definitions/${productId}/parts`, part)
      );
      this.loadProductParts(productId);
      return response;
    } catch (error) {
      console.error('Error creating part:', error);
      throw error;
    }
  }

  async updatePart(partId: string, part: PartRequest): Promise<PartDefinition> {
    try {
      const response = await firstValueFrom(
        this.http.put<PartDefinition>(`/api/product-definitions/parts/${partId}`, part)
      );
      return response;
    } catch (error) {
      console.error('Error updating part:', error);
      throw error;
    }
  }

  async deletePart(partId: string, productId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/product-definitions/parts/${partId}`)
      );
      this.loadProductParts(productId);
    } catch (error) {
      console.error('Error deleting part:', error);
      throw error;
    }
  }

  // ==================== Part Attribute Management ====================

  loadPartAttributes(partId: string) {
    this.modelService.setPartAttributesLoading(true);
    this.modelService.setPartAttributesError(null);

    this.http.get<PartAttributeDefinition[]>(`/api/product-definitions/parts/${partId}/attributes`).subscribe({
      next: (attributes) => {
        this.modelService.setPartAttributes(attributes);
        this.modelService.setPartAttributesLoading(false);
      },
      error: (err) => {
        console.error('Error loading part attributes:', err);
        this.modelService.setPartAttributes([]);
        this.modelService.setPartAttributesError('Failed to load part attributes');
        this.modelService.setPartAttributesLoading(false);
      }
    });
  }

  async getAttribute(attributeId: string): Promise<PartAttributeDefinition | null> {
    try {
      const attribute = await firstValueFrom(
        this.http.get<PartAttributeDefinition>(`/api/product-definitions/attributes/${attributeId}`)
      );
      this.modelService.setSelectedAttribute(attribute);
      return attribute;
    } catch (error) {
      console.error('Error loading attribute:', error);
      this.modelService.setSelectedAttribute(null);
      return null;
    }
  }

  async createAttribute(partId: string, attribute: PartAttributeRequest): Promise<PartAttributeDefinition> {
    try {
      const response = await firstValueFrom(
        this.http.post<PartAttributeDefinition>(`/api/product-definitions/parts/${partId}/attributes`, attribute)
      );
      this.loadPartAttributes(partId);
      return response;
    } catch (error) {
      console.error('Error creating attribute:', error);
      throw error;
    }
  }

  async updateAttribute(attributeId: string, attribute: PartAttributeRequest): Promise<PartAttributeDefinition> {
    try {
      const response = await firstValueFrom(
        this.http.put<PartAttributeDefinition>(`/api/product-definitions/attributes/${attributeId}`, attribute)
      );
      return response;
    } catch (error) {
      console.error('Error updating attribute:', error);
      throw error;
    }
  }

  async deleteAttribute(attributeId: string, partId: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/product-definitions/attributes/${attributeId}`)
      );
      this.loadPartAttributes(partId);
    } catch (error) {
      console.error('Error deleting attribute:', error);
      throw error;
    }
  }
}
