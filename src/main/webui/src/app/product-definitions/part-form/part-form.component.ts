import { Component, inject, Input, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PartDefinition, PartRequest, ModelService } from '../../model.service';
import { Controller } from '../../controller';
import { ToastService } from '../../core/toast/toast.service';

@Component({
  selector: 'app-part-form',
  imports: [CommonModule, FormsModule],
  templateUrl: './part-form.component.html',
  styleUrl: './part-form.component.scss'
})
export class PartFormComponent implements OnInit {
  private modelService = inject(ModelService);
  private controller = inject(Controller);
  private toastService = inject(ToastService);

  @Input() productId!: string;
  @Input() parentPartId: string | null = null;
  @Input() isEditMode = false;
  @Input() existingPart: PartDefinition | null = null;

  @Output() saved = new EventEmitter<PartDefinition>();
  @Output() cancelled = new EventEmitter<void>();

  // Form data
  partCode = '';
  description = '';
  unitPrice = 0;
  displayOrder = 0;
  minCardinality = 1;
  maxCardinality = 1;

  // Form state
  submitting = false;
  formError: string | null = null;
  fieldErrors: Record<string, string> = {};

  ngOnInit(): void {
    if (this.isEditMode && this.existingPart) {
      this.populateForm(this.existingPart);
    }
  }

  populateForm(part: PartDefinition): void {
    this.partCode = part.partCode;
    this.description = part.description || '';
    this.unitPrice = part.unitPrice || 0;
    this.displayOrder = part.displayOrder || 0;
    this.minCardinality = part.minCardinality || 1;
    this.maxCardinality = part.maxCardinality || 1;
  }

  validateForm(): boolean {
    this.fieldErrors = {};

    if (!this.partCode || this.partCode.trim().length === 0) {
      this.fieldErrors['partCode'] = 'Part code is required';
    } else if (this.partCode.trim().length > 50) {
      this.fieldErrors['partCode'] = 'Part code must be 50 characters or less';
    }

    if (this.description && this.description.length > 255) {
      this.fieldErrors['description'] = 'Description must be 255 characters or less';
    }

    if (this.unitPrice < 0) {
      this.fieldErrors['unitPrice'] = 'Unit price cannot be negative';
    }

    if (this.displayOrder < 0) {
      this.fieldErrors['displayOrder'] = 'Display order cannot be negative';
    }

    if (this.minCardinality < 1) {
      this.fieldErrors['minCardinality'] = 'Minimum cardinality must be at least 1';
    }

    if (this.maxCardinality < 1) {
      this.fieldErrors['maxCardinality'] = 'Maximum cardinality must be at least 1';
    }

    if (this.minCardinality > this.maxCardinality) {
      this.fieldErrors['maxCardinality'] = 'Maximum cardinality must be greater than or equal to minimum cardinality';
    }

    return Object.keys(this.fieldErrors).length === 0;
  }

  async onSubmit(): Promise<void> {
    this.formError = null;

    if (!this.validateForm()) {
      return;
    }

    this.submitting = true;

    const request: PartRequest = {
      partCode: this.partCode.trim(),
      description: this.description.trim(),
      unitPrice: this.unitPrice,
      displayOrder: this.displayOrder,
      minCardinality: this.minCardinality,
      maxCardinality: this.maxCardinality,
      childParts: this.existingPart?.childParts || [],
      attributes: this.existingPart?.attributes || []
    };

    try {
      let result: PartDefinition;
      if (this.isEditMode && this.existingPart) {
        result = await this.controller.updatePart(this.existingPart.id, request);
        this.toastService.success('Part updated successfully');
      } else {
        result = await this.controller.createPart(this.productId, request);
        this.toastService.success('Part created successfully');
      }
      this.saved.emit(result);
    } catch (err: any) {
      if (err.status === 409) {
        this.formError = 'A part with this code already exists';
      } else if (err.status === 400) {
        this.formError = err.error || 'Invalid request. Please check your input.';
      } else {
        this.formError = 'Failed to save part. Please try again.';
      }
    } finally {
      this.submitting = false;
    }
  }

  onCancel(): void {
    this.cancelled.emit();
  }

  getPageTitle(): string {
    if (this.isEditMode) {
      return 'Edit Part';
    }
    return this.parentPartId ? 'Add Child Part' : 'Add Part';
  }
}
