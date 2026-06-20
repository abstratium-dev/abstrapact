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

export interface TermsAndConditionsCodeSummary {
  code: string;
  title: string;
}

@Injectable({
  providedIn: 'root',
})
export class TermsAndConditionsModelService {

  private termsAndConditions = signal<TermsAndConditions[]>([]);
  private termsAndConditionsLoading = signal<boolean>(false);
  private termsAndConditionsError = signal<string | null>(null);
  private selectedTermsAndConditions = signal<TermsAndConditions | null>(null);
  private termsAndConditionsCodes = signal<TermsAndConditionsCodeSummary[]>([]);
  private termsAndConditionsCodesLoading = signal<boolean>(false);
  private termsAndConditionsCodesError = signal<string | null>(null);

  termsAndConditions$: Signal<TermsAndConditions[]> = this.termsAndConditions.asReadonly();
  termsAndConditionsLoading$: Signal<boolean> = this.termsAndConditionsLoading.asReadonly();
  termsAndConditionsError$: Signal<string | null> = this.termsAndConditionsError.asReadonly();
  selectedTermsAndConditions$: Signal<TermsAndConditions | null> = this.selectedTermsAndConditions.asReadonly();
  termsAndConditionsCodes$: Signal<TermsAndConditionsCodeSummary[]> = this.termsAndConditionsCodes.asReadonly();
  termsAndConditionsCodesLoading$: Signal<boolean> = this.termsAndConditionsCodesLoading.asReadonly();
  termsAndConditionsCodesError$: Signal<string | null> = this.termsAndConditionsCodesError.asReadonly();

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

  setTermsAndConditionsCodes(codes: TermsAndConditionsCodeSummary[]) {
    this.termsAndConditionsCodes.set(codes);
  }

  setTermsAndConditionsCodesLoading(loading: boolean) {
    this.termsAndConditionsCodesLoading.set(loading);
  }

  setTermsAndConditionsCodesError(error: string | null) {
    this.termsAndConditionsCodesError.set(error);
  }
}
