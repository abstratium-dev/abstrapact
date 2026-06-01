import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { TermsAndConditions, ModelService } from '../../model.service';
import { Controller } from '../../controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-terms-and-conditions-list',
  imports: [CommonModule],
  templateUrl: './terms-and-conditions-list.component.html',
  styleUrl: './terms-and-conditions-list.component.scss'
})
export class TermsAndConditionsListComponent implements OnInit {
  private modelService = inject(ModelService);
  private controller = inject(Controller);
  private router = inject(Router);
  private toastService = inject(ToastService);
  private confirmService = inject(ConfirmDialogService);

  termsList: Signal<TermsAndConditions[]> = this.modelService.termsAndConditions$;
  loading: Signal<boolean> = this.modelService.termsAndConditionsLoading$;
  error: Signal<string | null> = this.modelService.termsAndConditionsError$;

  ngOnInit(): void {
    this.controller.loadTermsAndConditions();
  }

  onRetry(): void {
    this.controller.loadTermsAndConditions();
  }

  onCreate(): void {
    this.router.navigate(['/terms-and-conditions/new']);
  }

  onView(terms: TermsAndConditions): void {
    this.router.navigate(['/terms-and-conditions', terms.id]);
  }

  onEdit(terms: TermsAndConditions, event: Event): void {
    event.stopPropagation();
    this.router.navigate(['/terms-and-conditions', terms.id, 'edit']);
  }

  async onDelete(terms: TermsAndConditions, event: Event): Promise<void> {
    event.stopPropagation();

    const confirmed = await this.confirmService.confirm({
      title: 'Delete Terms and Conditions',
      message: `Are you sure you want to delete "${terms.code}"? This action cannot be undone.`,
      confirmText: 'Delete',
      cancelText: 'Cancel',
      confirmClass: 'btn-danger'
    });

    if (!confirmed) {
      return;
    }

    try {
      await this.controller.deleteTermsAndConditions(terms.id);
      this.toastService.success('Terms and conditions deleted successfully');
    } catch (err: any) {
      this.toastService.error('Failed to delete terms and conditions. Please try again.');
    }
  }

  formatDate(date: string | null): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleDateString();
  }
}
