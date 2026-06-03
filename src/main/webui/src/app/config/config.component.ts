import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ModelService } from '../model.service';
import { ConfigController } from './config.controller';
import { ToastService } from '../core/toast/toast.service';

@Component({
  selector: 'app-config',
  imports: [CommonModule, FormsModule],
  templateUrl: './config.component.html',
  styleUrl: './config.component.scss'
})
export class ConfigComponent implements OnInit {
  private controller = inject(ConfigController);
  private toastService = inject(ToastService);
  private modelService = inject(ModelService);
  private router = inject(Router);

  currencyCode = '';
  locale = '';

  submitting = false;
  formError: string | null = null;
  fieldErrors: Record<string, string> = {};

  ngOnInit(): void {
    this.currencyCode = this.modelService.currencyCode$();
    this.locale = this.modelService.locale$();
  }

  validateForm(): boolean {
    this.fieldErrors = {};

    if (!this.currencyCode || this.currencyCode.trim().length === 0) {
      this.fieldErrors['currencyCode'] = 'Currency code is required';
    } else if (!/^[A-Z]{3}$/.test(this.currencyCode.trim())) {
      this.fieldErrors['currencyCode'] = 'Currency code must be a 3-letter uppercase ISO code (e.g., CHF, USD, EUR)';
    }

    if (!this.locale || this.locale.trim().length === 0) {
      this.fieldErrors['locale'] = 'Locale is required';
    } else if (!/^[a-z]{2}-[A-Z]{2}$/.test(this.locale.trim())) {
      this.fieldErrors['locale'] = 'Locale must be in BCP 47 format (e.g., en-US, de-DE, fr-CH)';
    }

    return Object.keys(this.fieldErrors).length === 0;
  }

  async onSubmit(): Promise<void> {
    this.formError = null;

    if (!this.validateForm()) {
      return;
    }

    this.submitting = true;

    try {
      await this.controller.updateConfig({
        currencyCode: this.currencyCode.trim(),
        locale: this.locale.trim()
      });
      this.toastService.success('Configuration updated successfully');
      this.router.navigate(['/']);
    } catch (err: any) {
      if (err.status === 400) {
        this.formError = err.error || 'Invalid request. Please check your input.';
      } else {
        this.formError = 'Failed to update configuration. Please try again.';
      }
    } finally {
      this.submitting = false;
    }
  }

  onCancel(): void {
    this.router.navigate(['/']);
  }
}
