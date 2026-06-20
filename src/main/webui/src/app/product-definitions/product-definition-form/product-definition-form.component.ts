import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { BillingModel, ProductDefinition, ProductDefinitionRequest, ProductDefinitionsModelService } from '../product-definitions.model.service';
import { ProductDefinitionsController } from '../product-definitions.controller';
import { TermsAndConditionsController } from '../../terms-and-conditions/terms-and-conditions.controller';
import { TermsAndConditionsModelService, TermsAndConditionsCodeSummary } from '../../terms-and-conditions/terms-and-conditions.model.service';
import { ToastService } from '../../core/toast/toast.service';

@Component({
  selector: 'app-product-definition-form',
  imports: [CommonModule, FormsModule],
  templateUrl: './product-definition-form.component.html',
  styleUrl: './product-definition-form.component.scss'
})
export class ProductDefinitionFormComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private modelService = inject(ProductDefinitionsModelService);
  private controller = inject(ProductDefinitionsController);
  private termsController = inject(TermsAndConditionsController);
  private termsModelService = inject(TermsAndConditionsModelService);
  private toastService = inject(ToastService);

  selectedProduct: Signal<ProductDefinition | null> = this.modelService.selectedProductDefinition$;
  termsAndConditionsCodes: Signal<TermsAndConditionsCodeSummary[]> = this.termsModelService.termsAndConditionsCodes$;
  termsAndConditionsCodesLoading: Signal<boolean> = this.termsModelService.termsAndConditionsCodesLoading$;

  isEditMode = false;
  productId: string | null = null;

  // Form data
  productCode = '';
  description = '';
  billingModel: BillingModel = 'FIXED_PRICE';
  productValidFrom: string | null = null;
  productValidUntil: string | null = null;
  termsAndConditionsCode: string | null = null;

  // Form state
  submitting = false;
  formError: string | null = null;
  fieldErrors: Record<string, string> = {};

  billingModels: { value: BillingModel; label: string }[] = [
    { value: 'FIXED_PRICE', label: 'Fixed Price' },
    { value: 'SUBSCRIPTION', label: 'Subscription' }
  ];

  ngOnInit(): void {
    this.productId = this.route.snapshot.paramMap.get('id');
    this.isEditMode = !!this.productId;

    this.termsController.loadTermsAndConditionsCodes();

    if (this.isEditMode && this.productId) {
      this.loadProductDefinition(this.productId);
    }
  }

  async loadProductDefinition(id: string): Promise<void> {
    const definition = await this.controller.getProductDefinition(id);
    if (definition) {
      this.populateForm(definition);
    } else {
      this.formError = 'Failed to load product definition';
    }
  }

  populateForm(definition: ProductDefinition): void {
    this.productCode = definition.productCode;
    this.description = definition.description || '';
    this.billingModel = definition.billingModel;
    this.productValidFrom = definition.productValidFrom;
    this.productValidUntil = definition.productValidUntil;
    this.termsAndConditionsCode = definition.termsAndConditionsCode;
  }

  validateForm(): boolean {
    this.fieldErrors = {};

    if (!this.productCode || this.productCode.trim().length === 0) {
      this.fieldErrors['productCode'] = 'Product code is required';
    } else if (this.productCode.trim().length > 50) {
      this.fieldErrors['productCode'] = 'Product code must be 50 characters or less';
    }

    if (this.description && this.description.length > 255) {
      this.fieldErrors['description'] = 'Description must be 255 characters or less';
    }

    if (!this.billingModel) {
      this.fieldErrors['billingModel'] = 'Billing model is required';
    }

    // Validate date range if both dates are provided
    if (this.productValidFrom && this.productValidUntil) {
      const fromDate = new Date(this.productValidFrom);
      const untilDate = new Date(this.productValidUntil);
      if (fromDate > untilDate) {
        this.fieldErrors['productValidUntil'] = 'Valid until date must be after valid from date';
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

    const request: ProductDefinitionRequest = {
      productCode: this.productCode.trim(),
      description: this.description.trim(),
      billingModel: this.billingModel,
      productValidFrom: this.productValidFrom || null,
      productValidUntil: this.productValidUntil || null,
      termsAndConditionsCode: this.termsAndConditionsCode || null
    };

    try {
      if (this.isEditMode && this.productId) {
        await this.controller.updateProductDefinition(this.productId, request);
        this.toastService.success('Product definition updated successfully');
      } else {
        await this.controller.createProductDefinition(request);
        this.toastService.success('Product definition created successfully');
      }
      this.router.navigate(['/product-definitions']);
    } catch (err: any) {
      if (err.status === 409) {
        this.formError = 'A product definition with this code already exists';
      } else if (err.status === 400) {
        this.formError = err.error || 'Invalid request. Please check your input.';
      } else {
        this.formError = 'Failed to save product definition. Please try again.';
      }
    } finally {
      this.submitting = false;
    }
  }

  onCancel(): void {
    if (this.isEditMode && this.productId) {
      this.router.navigate(['/product-definitions', this.productId]);
    } else {
      this.router.navigate(['/product-definitions']);
    }
  }

  getPageTitle(): string {
    return this.isEditMode ? 'Edit Product Definition' : 'Create Product Definition';
  }
}
