import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TermsAndConditions, TermsAndConditionsRequest, TermsAndConditionsModelService } from '../terms-and-conditions.model.service';
import { TermsAndConditionsController } from '../terms-and-conditions.controller';
import { ToastService } from '../../core/toast/toast.service';

@Component({
  selector: 'app-terms-and-conditions-form',
  imports: [CommonModule, FormsModule],
  templateUrl: './terms-and-conditions-form.component.html',
  styleUrl: './terms-and-conditions-form.component.scss'
})
export class TermsAndConditionsFormComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private modelService = inject(TermsAndConditionsModelService);
  private controller = inject(TermsAndConditionsController);
  private toastService = inject(ToastService);

  selectedTerms: Signal<TermsAndConditions | null> = this.modelService.selectedTermsAndConditions$;

  isEditMode = false;
  termsId: string | null = null;

  code = '';
  title = '';
  contentFr = '';
  contentDe = '';
  contentEn = '';
  currentVersion = '';
  effectiveFrom: string | null = null;
  effectiveUntil: string | null = null;

  submitting = false;
  formError: string | null = null;
  fieldErrors: Record<string, string> = {};

  ngOnInit(): void {
    this.termsId = this.route.snapshot.paramMap.get('id');
    this.isEditMode = !!this.termsId;

    if (this.isEditMode && this.termsId) {
      this.loadTermsAndConditions(this.termsId);
    }
  }

  async loadTermsAndConditions(id: string): Promise<void> {
    const terms = await this.controller.getTermsAndConditions(id);
    if (terms) {
      this.populateForm(terms);
    } else {
      this.formError = 'Failed to load terms and conditions';
    }
  }

  populateForm(terms: TermsAndConditions): void {
    this.code = terms.code;
    this.title = terms.title || '';
    this.contentFr = terms.contentFr || '';
    this.contentDe = terms.contentDe || '';
    this.contentEn = terms.contentEn || '';
    this.currentVersion = terms.currentVersion || '';
    this.effectiveFrom = terms.effectiveFrom;
    this.effectiveUntil = terms.effectiveUntil;
  }

  validateForm(): boolean {
    this.fieldErrors = {};

    if (!this.code || this.code.trim().length === 0) {
      this.fieldErrors['code'] = 'Code is required';
    } else if (this.code.trim().length > 50) {
      this.fieldErrors['code'] = 'Code must be 50 characters or less';
    }

    if (!this.title || this.title.trim().length === 0) {
      this.fieldErrors['title'] = 'Title is required';
    }

    if (this.title && this.title.length > 255) {
      this.fieldErrors['title'] = 'Title must be 255 characters or less';
    }

    if (!this.contentFr || this.contentFr.trim().length === 0) {
      this.fieldErrors['contentFr'] = 'French content is required';
    }

    if (!this.contentDe || this.contentDe.trim().length === 0) {
      this.fieldErrors['contentDe'] = 'German content is required';
    }

    if (!this.contentEn || this.contentEn.trim().length === 0) {
      this.fieldErrors['contentEn'] = 'English content is required';
    }

    if (!this.currentVersion || this.currentVersion.trim().length === 0) {
      this.fieldErrors['currentVersion'] = 'Version is required';
    }


    if (this.effectiveFrom && this.effectiveUntil) {
      const fromDate = new Date(this.effectiveFrom);
      const untilDate = new Date(this.effectiveUntil);
      if (fromDate > untilDate) {
        this.fieldErrors['effectiveUntil'] = 'Effective until date must be after effective from date';
      }
    }

    return Object.keys(this.fieldErrors).length === 0;
  }

  async onSubmit(): Promise<void> {
    this.formError = null;

    if (!this.validateForm()) {
      return;
    }

    this.submitting = true;

    const request: TermsAndConditionsRequest = {
      code: this.code.trim(),
      title: this.title.trim(),
      contentFr: this.contentFr.trim(),
      contentDe: this.contentDe.trim(),
      contentEn: this.contentEn.trim(),
      currentVersion: this.currentVersion.trim(),
      effectiveFrom: this.effectiveFrom || null,
      effectiveUntil: this.effectiveUntil || null
    };

    try {
      if (this.isEditMode && this.termsId) {
        await this.controller.updateTermsAndConditions(this.termsId, request);
        this.toastService.success('Terms and conditions updated successfully');
      } else {
        await this.controller.createTermsAndConditions(request);
        this.toastService.success('Terms and conditions created successfully');
      }
      this.router.navigate(['/terms-and-conditions']);
    } catch (err: any) {
      if (err.status === 409) {
        this.formError = 'Terms and conditions with this code already exists';
      } else if (err.status === 400) {
        // Handle RFC 7807 Problem Details response (object with detail field)
        const errorBody = err.error;
        if (typeof errorBody === 'object' && errorBody !== null && errorBody.detail) {
          this.formError = errorBody.detail;
        } else if (typeof errorBody === 'string') {
          this.formError = errorBody;
        } else {
          this.formError = 'Invalid request. Please check your input.';
        }
      } else {
        this.formError = 'Failed to save terms and conditions. Please try again.';
      }
    } finally {
      this.submitting = false;
    }
  }

  onCancel(): void {
    this.router.navigate(['/terms-and-conditions']);
  }

  getPageTitle(): string {
    return this.isEditMode ? 'Edit Terms and Conditions' : 'Create Terms and Conditions';
  }
}
