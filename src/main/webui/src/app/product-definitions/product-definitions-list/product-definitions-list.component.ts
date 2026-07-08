import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ProductDefinition, ProductDefinitionsModelService } from '../product-definitions.model.service';
import { ProductDefinitionsController } from '../product-definitions.controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-product-definitions-list',
  imports: [CommonModule],
  templateUrl: './product-definitions-list.component.html',
  styleUrl: './product-definitions-list.component.scss'
})
export class ProductDefinitionsListComponent implements OnInit {
  private modelService = inject(ProductDefinitionsModelService);
  private controller = inject(ProductDefinitionsController);
  private router = inject(Router);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);

  productDefinitions: Signal<ProductDefinition[]> = this.modelService.productDefinitions$;
  loading: Signal<boolean> = this.modelService.productDefinitionsLoading$;
  error: Signal<string | null> = this.modelService.productDefinitionsError$;

  ngOnInit(): void {
    this.controller.loadProductDefinitions();
  }

  onRetry(): void {
    this.controller.loadProductDefinitions();
  }

  onCreate(): void {
    this.router.navigate(['/product-definitions/new']);
  }

  onView(definition: ProductDefinition): void {
    this.router.navigate(['/product-definitions', definition.id]);
  }

  onViewDetail(definition: ProductDefinition, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/product-definitions', definition.id]);
  }

  onSimulate(definition: ProductDefinition, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/product-definitions', definition.id, 'simulate']);
  }

  onEdit(definition: ProductDefinition, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/product-definitions', definition.id, 'edit']);
  }

  async onDelete(definition: ProductDefinition, event: Event): Promise<void> {
    event.stopPropagation();

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
      await this.controller.deleteProductDefinition(definition.id);
      this.toastService.success('Product definition deleted successfully');
    } catch (err: any) {
      this.toastService.error('Failed to delete product definition. Please try again.');
    }
  }

  getBillingModelLabel(model: string): string {
    return model === 'FIXED_PRICE' ? 'Fixed Price' : 'Subscription';
  }

  getPaymentModelLabel(model: string | null | undefined): string {
    return model === 'POSTPAID' ? 'Postpaid' : 'Prepaid';
  }

  formatDate(date: string | null): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleDateString();
  }
}
