import { Component, inject, Input, OnInit, OnChanges, Output, EventEmitter, Signal, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PartAttributeDefinition, PartDefinition, ModelService } from '../../model.service';
import { Controller } from '../../controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-part-attributes-list',
  imports: [CommonModule],
  templateUrl: './part-attributes-list.component.html',
  styleUrl: './part-attributes-list.component.scss'
})
export class PartAttributesListComponent implements OnInit, OnChanges {
  private modelService = inject(ModelService);
  private controller = inject(Controller);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);

  @Input() part: PartDefinition | null = null;
  @Input() readOnly = false;

  @Output() addAttribute = new EventEmitter<void>();
  @Output() editAttribute = new EventEmitter<PartAttributeDefinition>();

  attributes: Signal<PartAttributeDefinition[]> = this.modelService.partAttributes$;
  loading: Signal<boolean> = this.modelService.partAttributesLoading$;
  error: Signal<string | null> = this.modelService.partAttributesError$;

  selectedAttributeId: string | null = null;

  ngOnInit(): void {
    this.loadAttributes();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['part'] && changes['part'].currentValue) {
      this.loadAttributes();
      this.selectedAttributeId = null;
    }
  }

  loadAttributes(): void {
    if (this.part) {
      this.controller.loadPartAttributes(this.part.id);
    }
  }

  selectAttribute(attribute: PartAttributeDefinition): void {
    this.selectedAttributeId = attribute.id;
  }

  isSelected(attributeId: string): boolean {
    return this.selectedAttributeId === attributeId;
  }

  async onAddAttribute(): Promise<void> {
    if (this.readOnly || !this.part) return;
    this.addAttribute.emit();
  }

  async onEditAttribute(attribute: PartAttributeDefinition, event: Event): Promise<void> {
    event.stopPropagation();
    if (this.readOnly) return;
    this.editAttribute.emit(attribute);
  }

  async onDeleteAttribute(attribute: PartAttributeDefinition, event: Event): Promise<void> {
    event.stopPropagation();
    if (this.readOnly || !this.part) return;

    const confirmed = await this.confirmService.confirm({
      title: 'Delete Attribute',
      message: `Are you sure you want to delete "${attribute.attributeName}"? This action cannot be undone.`,
      confirmText: 'Delete',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.deleteAttribute(attribute.id, this.part.id);
      this.toastService.success('Attribute deleted successfully');
      if (this.selectedAttributeId === attribute.id) {
        this.selectedAttributeId = null;
      }
    } catch (err: any) {
      this.toastService.error('Failed to delete attribute. Please try again.');
    }
  }

  getDataTypeLabel(dataType: string): string {
    const labels: Record<string, string> = {
      'STRING': 'String',
      'INTEGER': 'Integer',
      'DECIMAL': 'Decimal',
      'BOOLEAN': 'Boolean',
      'DATE': 'Date'
    };
    return labels[dataType] || dataType;
  }

  hasAllowedValues(attribute: PartAttributeDefinition): boolean {
    return attribute.allowedValues && attribute.allowedValues.length > 0;
  }

  getAllowedValuesText(attribute: PartAttributeDefinition): string {
    if (!this.hasAllowedValues(attribute)) {
      return 'Any value';
    }
    const values = attribute.allowedValues.map(av => av.allowedValue);
    return values.join(', ');
  }
}
