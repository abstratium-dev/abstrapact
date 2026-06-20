import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { TermsAndConditions, TermsAndConditionsRequest, TermsAndConditionsModelService, TermsAndConditionsCodeSummary } from './terms-and-conditions.model.service';

@Injectable({
  providedIn: 'root',
})
export class TermsAndConditionsController {

  private modelService = inject(TermsAndConditionsModelService);
  private http = inject(HttpClient);

  loadTermsAndConditions() {
    this.modelService.setTermsAndConditionsLoading(true);
    this.modelService.setTermsAndConditionsError(null);

    this.http.get<TermsAndConditions[]>('/api/terms-and-conditions').subscribe({
      next: (terms) => {
        this.modelService.setTermsAndConditions(terms);
        this.modelService.setTermsAndConditionsLoading(false);
      },
      error: (err) => {
        console.error('Error loading terms and conditions:', err);
        this.modelService.setTermsAndConditions([]);
        this.modelService.setTermsAndConditionsError('Failed to load terms and conditions');
        this.modelService.setTermsAndConditionsLoading(false);
      }
    });
  }

  async getTermsAndConditions(id: string): Promise<TermsAndConditions | null> {
    try {
      const terms = await firstValueFrom(
        this.http.get<TermsAndConditions>(`/api/terms-and-conditions/${id}`)
      );
      this.modelService.setSelectedTermsAndConditions(terms);
      return terms;
    } catch (error) {
      console.error('Error loading terms and conditions:', error);
      this.modelService.setSelectedTermsAndConditions(null);
      return null;
    }
  }

  async createTermsAndConditions(request: TermsAndConditionsRequest): Promise<TermsAndConditions> {
    try {
      const response = await firstValueFrom(
        this.http.post<TermsAndConditions>('/api/terms-and-conditions', request)
      );
      this.loadTermsAndConditions();
      return response;
    } catch (error) {
      console.error('Error creating terms and conditions:', error);
      throw error;
    }
  }

  async updateTermsAndConditions(id: string, request: TermsAndConditionsRequest): Promise<TermsAndConditions> {
    try {
      const response = await firstValueFrom(
        this.http.put<TermsAndConditions>(`/api/terms-and-conditions/${id}`, request)
      );
      this.loadTermsAndConditions();
      return response;
    } catch (error) {
      console.error('Error updating terms and conditions:', error);
      throw error;
    }
  }

  async deleteTermsAndConditions(id: string): Promise<void> {
    try {
      await firstValueFrom(
        this.http.delete<void>(`/api/terms-and-conditions/${id}`)
      );
      this.loadTermsAndConditions();
    } catch (error) {
      console.error('Error deleting terms and conditions:', error);
      throw error;
    }
  }

  loadTermsAndConditionsCodes() {
    this.modelService.setTermsAndConditionsCodesLoading(true);
    this.modelService.setTermsAndConditionsCodesError(null);

    this.http.get<TermsAndConditionsCodeSummary[]>('/api/terms-and-conditions/codes').subscribe({
      next: (codes) => {
        this.modelService.setTermsAndConditionsCodes(codes);
        this.modelService.setTermsAndConditionsCodesLoading(false);
      },
      error: (err) => {
        console.error('Error loading terms and conditions codes:', err);
        this.modelService.setTermsAndConditionsCodes([]);
        this.modelService.setTermsAndConditionsCodesError('Failed to load terms and conditions codes');
        this.modelService.setTermsAndConditionsCodesLoading(false);
      }
    });
  }
}
