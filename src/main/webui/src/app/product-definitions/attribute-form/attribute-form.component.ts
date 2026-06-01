import { Component, inject, Input, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  PartAttributeDefinition,
  PartAttributeRequest,
  PartAttributeAllowedValueRequest,
  DataType,
  ProductDefinitionsModelService
} from '../product-definitions.model.service';
import { ProductDefinitionsController } from '../product-definitions.controller';
import { ToastService } from '../../core/toast/toast.service';

@Component({
  selector: 'app-attribute-form',
  imports: [CommonModule, FormsModule],
  templateUrl: './attribute-form.component.html',
  styleUrl: './attribute-form.component.scss'
})
export class AttributeFormComponent implements OnInit {
  private modelService = inject(ProductDefinitionsModelService);
  private controller = inject(ProductDefinitionsController);
  private toastService = inject(ToastService);

  @Input() partId!: string;
  @Input() isEditMode = false;
  @Input() existingAttribute: PartAttributeDefinition | null = null;

  @Output() saved = new EventEmitter<PartAttributeDefinition>();
  @Output() cancelled = new EventEmitter<void>();

  dataTypes: { value: DataType; label: string }[] = [
    { value: 'STRING', label: 'String' },
    { value: 'INTEGER', label: 'Integer' },
    { value: 'DECIMAL', label: 'Decimal' },
    { value: 'BOOLEAN', label: 'Boolean' },
    { value: 'DATE', label: 'Date' }
  ];

  // Form data
  attributeName = '';
  dataType: DataType = 'STRING';
  isRequired = false;
  defaultValue: string | null = null;
  allowedValues: PartAttributeAllowedValueRequest[] = [];
  newAllowedValue = '';

  // Form state
  submitting = false;
  formError: string | null = null;
  fieldErrors: Record<string, string> = {};

  ngOnInit(): void {
    if (this.isEditMode && this.existingAttribute) {
      this.populateForm(this.existingAttribute);
    }
  }

  populateForm(attribute: PartAttributeDefinition): void {
    this.attributeName = attribute.attributeName;
    this.dataType = attribute.dataType;
    this.isRequired = attribute.isRequired;
    this.defaultValue = attribute.defaultValue;
    this.allowedValues = attribute.allowedValues?.map(av => ({
      id: av.id,
      allowedValue: av.allowedValue
    })) || [];
  }

  validateForm(): boolean {
    this.fieldErrors = {};

    if (!this.attributeName || this.attributeName.trim().length === 0) {
      this.fieldErrors['attributeName'] = 'Attribute name is required';
    } else if (this.attributeName.trim().length > 50) {
      this.fieldErrors['attributeName'] = 'Attribute name must be 50 characters or less';
    }

    if (!this.dataType) {
      this.fieldErrors['dataType'] = 'Data type is required';
    }

    if (this.defaultValue && this.defaultValue.length > 255) {
      this.fieldErrors['defaultValue'] = 'Default value must be 255 characters or less';
    }

    return Object.keys(this.fieldErrors).length === 0;
  }

  addAllowedValue(): void {
    if (!this.newAllowedValue.trim()) {
      return;
    }

    const trimmedValue = this.newAllowedValue.trim();
    if (this.allowedValues.some(av => av.allowedValue === trimmedValue)) {
      this.formError = 'This value already exists in the allowed values list';
      return;
    }

    this.allowedValues.push({ allowedValue: trimmedValue });
    this.newAllowedValue = '';
    this.formError = null;
  }

  removeAllowedValue(index: number): void {
    this.allowedValues.splice(index, 1);
  }

  onAllowedValueKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      this.addAllowedValue();
    }
  }

  async onSubmit(): Promise<void> {
    this.formError = null;

    if (!this.validateForm()) {
      return;
    }

    this.submitting = true;

    const request: PartAttributeRequest = {
      attributeName: this.attributeName.trim(),
      dataType: this.dataType,
      isRequired: this.isRequired,
      defaultValue: this.defaultValue?.trim() || null,
      allowedValues: this.allowedValues
    };

    try {
      let result: PartAttributeDefinition;
      if (this.isEditMode && this.existingAttribute) {
        result = await this.controller.updateAttribute(this.existingAttribute.id, request);
        this.toastService.success('Attribute updated successfully');
      } else {
        result = await this.controller.createAttribute(this.partId, request);
        this.toastService.success('Attribute created successfully');
      }
      this.saved.emit(result);
    } catch (err: any) {
      if (err.status === 409) {
        this.formError = 'An attribute with this name already exists for this part';
      } else if (err.status === 400) {
        this.formError = err.error || 'Invalid request. Please check your input.';
      } else {
        this.formError = 'Failed to save attribute. Please try again.';
      }
    } finally {
      this.submitting = false;
    }
  }

  onCancel(): void {
    this.cancelled.emit();
  }

  getPageTitle(): string {
    return this.isEditMode ? 'Edit Attribute' : 'Add Attribute';
  }
}
