import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { PaymentModel, ProductDefinition, PartDefinition, PartAttributeDefinition, ProductDefinitionsModelService } from '../product-definitions.model.service';
import { ProductDefinitionsController } from '../product-definitions.controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';
import { ProductStructureComponent } from '../product-structure/product-structure.component';
import { PartAttributesListComponent } from '../part-attributes-list/part-attributes-list.component';
import { PartFormComponent } from '../part-form/part-form.component';
import { AttributeFormComponent } from '../attribute-form/attribute-form.component';

@Component({
  selector: 'app-product-definition-detail',
  imports: [CommonModule, ProductStructureComponent, PartAttributesListComponent, PartFormComponent, AttributeFormComponent],
  templateUrl: './product-definition-detail.component.html',
  styleUrl: './product-definition-detail.component.scss'
})
export class ProductDefinitionDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private modelService = inject(ProductDefinitionsModelService);
  private controller = inject(ProductDefinitionsController);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);

  selectedProduct: Signal<ProductDefinition | null> = this.modelService.selectedProductDefinition$;
  selectedPart: Signal<PartDefinition | null> = this.modelService.selectedPart$;

  loading = true;
  error: string | null = null;
  productId: string | null = null;

  // Form visibility state
  showPartForm = false;
  showAttributeForm = false;
  editingPart: PartDefinition | null = null;
  editingAttribute: PartAttributeDefinition | null = null;

  ngOnInit(): void {
    this.productId = this.route.snapshot.paramMap.get('id');
    if (this.productId) {
      this.loadProductDefinition(this.productId);
    } else {
      this.error = 'No product ID provided';
      this.loading = false;
    }
  }

  async loadProductDefinition(id: string): Promise<void> {
    this.loading = true;
    this.error = null;

    const definition = await this.controller.getProductDefinition(id);
    if (definition) {
      this.loading = false;
    } else {
      this.error = 'Product definition not found';
      this.loading = false;
    }
  }

  onBack(): void {
    this.router.navigate(['/product-definitions']);
  }

  onEdit(): void {
    if (this.productId) {
      this.router.navigate(['/product-definitions', this.productId, 'edit']);
    }
  }

  onSimulate(): void {
    if (this.productId) {
      this.router.navigate(['/product-definitions', this.productId, 'simulate']);
    }
  }

  async onDelete(): Promise<void> {
    const definition = this.selectedProduct();
    if (!definition || !this.productId) return;

    const confirmed = await this.confirmService.confirm({
      title: 'Delete Product Definition',
      message: `Are you sure you want to delete "${definition.productCode}"? This action cannot be undone.`,
      confirmText: 'Delete',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.deleteProductDefinition(this.productId);
      this.toastService.success('Product definition deleted successfully');
      this.router.navigate(['/product-definitions']);
    } catch (err: any) {
      this.toastService.error('Failed to delete product definition. Please try again.');
    }
  }

  getBillingModelLabel(model: string): string {
    return model === 'FIXED_PRICE' ? 'Fixed Price' : 'Subscription';
  }

  getPaymentModelLabel(model: string): string {
    return model === 'PREPAID' ? 'Prepaid' : 'Postpaid';
  }

  formatDate(date: string | null): string {
    if (!date) return 'Not set';
    return new Date(date).toLocaleDateString();
  }

  // ==================== Part Form Handling ====================

  onAddPart(): void {
    this.editingPart = null;
    this.showPartForm = true;
    this.showAttributeForm = false;
  }

  onEditPart(part: PartDefinition): void {
    this.editingPart = part;
    this.showPartForm = true;
    this.showAttributeForm = false;
  }

  onPartSaved(): void {
    this.showPartForm = false;
    this.editingPart = null;
    if (this.productId) {
      this.controller.loadProductParts(this.productId);
    }
  }

  onPartCancelled(): void {
    this.showPartForm = false;
    this.editingPart = null;
  }

  // ==================== Attribute Form Handling ====================

  onAddAttribute(): void {
    this.editingAttribute = null;
    this.showAttributeForm = true;
    this.showPartForm = false;
  }

  onEditAttribute(attribute: PartAttributeDefinition): void {
    this.editingAttribute = attribute;
    this.showAttributeForm = true;
    this.showPartForm = false;
  }

  onAttributeSaved(): void {
    this.showAttributeForm = false;
    this.editingAttribute = null;
    const part = this.selectedPart();
    if (part) {
      this.controller.loadPartAttributes(part.id);
    }
  }

  onAttributeCancelled(): void {
    this.showAttributeForm = false;
    this.editingAttribute = null;
  }
}
