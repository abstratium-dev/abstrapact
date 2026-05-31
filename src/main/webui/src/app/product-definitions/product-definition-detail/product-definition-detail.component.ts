import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ProductDefinition, ModelService } from '../../model.service';
import { Controller } from '../../controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-product-definition-detail',
  imports: [CommonModule],
  templateUrl: './product-definition-detail.component.html',
  styleUrl: './product-definition-detail.component.scss'
})
export class ProductDefinitionDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private modelService = inject(ModelService);
  private controller = inject(Controller);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);

  selectedProduct: Signal<ProductDefinition | null> = this.modelService.selectedProductDefinition$;

  loading = true;
  error: string | null = null;
  productId: string | null = null;

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

  formatDate(date: string | null): string {
    if (!date) return 'Not set';
    return new Date(date).toLocaleDateString();
  }
}
