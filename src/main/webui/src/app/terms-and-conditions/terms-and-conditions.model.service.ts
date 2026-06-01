import { Injectable, signal, Signal } from '@angular/core';

export interface TermsAndConditions {
  id: string;
  organisationId: string;
  code: string;
  title: string;
  contentFr: string;
  contentDe: string;
  contentEn: string;
  currentVersion: string;
  effectiveFrom: string | null;
  effectiveUntil: string | null;
}

export interface TermsAndConditionsRequest {
  code: string;
  title: string;
  contentFr: string;
  contentDe: string;
  contentEn: string;
  currentVersion: string;
  effectiveFrom: string | null;
  effectiveUntil: string | null;
}

@Injectable({
  providedIn: 'root',
})
export class TermsAndConditionsModelService {

  private termsAndConditions = signal<TermsAndConditions[]>([]);
  private termsAndConditionsLoading = signal<boolean>(false);
  private termsAndConditionsError = signal<string | null>(null);
  private selectedTermsAndConditions = signal<TermsAndConditions | null>(null);

  termsAndConditions$: Signal<TermsAndConditions[]> = this.termsAndConditions.asReadonly();
  termsAndConditionsLoading$: Signal<boolean> = this.termsAndConditionsLoading.asReadonly();
  termsAndConditionsError$: Signal<string | null> = this.termsAndConditionsError.asReadonly();
  selectedTermsAndConditions$: Signal<TermsAndConditions | null> = this.selectedTermsAndConditions.asReadonly();

  setTermsAndConditions(terms: TermsAndConditions[]) {
    this.termsAndConditions.set(terms);
  }

  setTermsAndConditionsLoading(loading: boolean) {
    this.termsAndConditionsLoading.set(loading);
  }

  setTermsAndConditionsError(error: string | null) {
    this.termsAndConditionsError.set(error);
  }

  setSelectedTermsAndConditions(terms: TermsAndConditions | null) {
    this.selectedTermsAndConditions.set(terms);
  }
}
