import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { TermsAndConditions, ModelService } from '../../model.service';
import { Controller } from '../../controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-terms-and-conditions-detail',
  imports: [CommonModule],
  templateUrl: './terms-and-conditions-detail.component.html',
  styleUrl: './terms-and-conditions-detail.component.scss'
})
export class TermsAndConditionsDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private modelService = inject(ModelService);
  private controller = inject(Controller);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);

  terms: Signal<TermsAndConditions | null> = this.modelService.selectedTermsAndConditions$;
  loading = false;
  error: string | null = null;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadTermsAndConditions(id);
    } else {
      this.error = 'No ID provided';
    }
  }

  async loadTermsAndConditions(id: string): Promise<void> {
    this.loading = true;
    this.error = null;
    const result = await this.controller.getTermsAndConditions(id);
    this.loading = false;
    if (!result) {
      this.error = 'Terms and conditions not found';
    }
  }

  onEdit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.router.navigate(['/terms-and-conditions', id, 'edit']);
    }
  }

  async onDelete(): Promise<void> {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) return;

    const terms = this.terms();
    const confirmed = await this.confirmService.confirm({
      title: 'Delete Terms and Conditions',
      message: `Are you sure you want to delete "${terms?.code || 'this item'}"? This action cannot be undone.`,
      confirmText: 'Delete',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.deleteTermsAndConditions(id);
      this.toastService.success('Terms and conditions deleted successfully');
      this.router.navigate(['/terms-and-conditions']);
    } catch (err: any) {
      this.toastService.error('Failed to delete terms and conditions. Please try again.');
    }
  }

  onBack(): void {
    this.router.navigate(['/terms-and-conditions']);
  }

  formatDate(date: string | null): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleDateString();
  }
}
