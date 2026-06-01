import { Component, inject, Input, OnInit, Output, EventEmitter, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PartDefinition, PartAttributeDefinition, ProductDefinitionsModelService } from '../product-definitions.model.service';
import { ProductDefinitionsController } from '../product-definitions.controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-product-structure',
  imports: [CommonModule],
  templateUrl: './product-structure.component.html',
  styleUrl: './product-structure.component.scss'
})
export class ProductStructureComponent implements OnInit {
  private modelService = inject(ProductDefinitionsModelService);
  private controller = inject(ProductDefinitionsController);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);

  @Input() productId!: string;
  @Input() readOnly = false;

  @Output() addPart = new EventEmitter<void>();
  @Output() editPart = new EventEmitter<PartDefinition>();

  parts: Signal<PartDefinition[]> = this.modelService.productParts$;
  loading: Signal<boolean> = this.modelService.productPartsLoading$;
  error: Signal<string | null> = this.modelService.productPartsError$;

  expandedParts: Set<string> = new Set();
  selectedPartId: string | null = null;

  ngOnInit(): void {
    if (this.productId) {
      this.controller.loadProductParts(this.productId);
    }
  }

  onRefresh(): void {
    if (this.productId) {
      this.controller.loadProductParts(this.productId);
    }
  }

  toggleExpand(partId: string): void {
    if (this.expandedParts.has(partId)) {
      this.expandedParts.delete(partId);
    } else {
      this.expandedParts.add(partId);
    }
  }

  isExpanded(partId: string): boolean {
    return this.expandedParts.has(partId);
  }

  selectPart(part: PartDefinition): void {
    this.selectedPartId = part.id;
    this.modelService.setSelectedPart(part);
  }

  isSelected(partId: string): boolean {
    return this.selectedPartId === partId;
  }

  hasChildren(part: PartDefinition): boolean {
    return part.childParts && part.childParts.length > 0;
  }

  hasAttributes(part: PartDefinition): boolean {
    return part.attributes && part.attributes.length > 0;
  }

  getAttributeCount(part: PartDefinition): number {
    return part.attributes ? part.attributes.length : 0;
  }

  getChildCount(part: PartDefinition): number {
    return part.childParts ? part.childParts.length : 0;
  }

  async onAddPart(): Promise<void> {
    if (this.readOnly) return;
    this.addPart.emit();
  }

  async onEditPart(part: PartDefinition, event: Event): Promise<void> {
    event.stopPropagation();
    if (this.readOnly) return;
    this.editPart.emit(part);
  }

  async onDeletePart(part: PartDefinition, event: Event): Promise<void> {
    event.stopPropagation();
    if (this.readOnly) return;

    const confirmed = await this.confirmService.confirm({
      title: 'Delete Part',
      message: `Are you sure you want to delete "${part.partCode}"? This action cannot be undone.`,
      confirmText: 'Delete',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.deletePart(part.id, this.productId);
      this.toastService.success('Part deleted successfully');
      if (this.selectedPartId === part.id) {
        this.selectedPartId = null;
        this.modelService.setSelectedPart(null);
      }
    } catch (err: any) {
      this.toastService.error('Failed to delete part. Please try again.');
    }
  }

  onAddChildPart(parentPart: PartDefinition, event: Event): void {
    event.stopPropagation();
    if (this.readOnly) return;
    this.expandedParts.add(parentPart.id);
  }

  onViewAttributes(part: PartDefinition, event: Event): void {
    event.stopPropagation();
    this.controller.loadPartAttributes(part.id);
  }

  formatPrice(price: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD'
    }).format(price || 0);
  }

  getIndentLevel(level: number): string {
    return `${level * 20}px`;
  }

  collectAllParts(parts: PartDefinition[], result: PartDefinition[] = []): PartDefinition[] {
    for (const part of parts) {
      result.push(part);
      if (part.childParts) {
        this.collectAllParts(part.childParts, result);
      }
    }
    return result;
  }
}
