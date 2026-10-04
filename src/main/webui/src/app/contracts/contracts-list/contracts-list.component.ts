import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import {
  ContractsModelService,
  CustomerContractSummary,
} from '../contracts.model.service';
import { ContractsController } from '../contracts.controller';
import { formatDate, formatDateTime } from '../date-format';

@Component({
  selector: 'app-contracts-list',
  imports: [CommonModule],
  templateUrl: './contracts-list.component.html',
  styleUrl: './contracts-list.component.scss'
})
export class ContractsListComponent implements OnInit {
  private modelService = inject(ContractsModelService);
  private controller = inject(ContractsController);
  private router = inject(Router);

  contracts: Signal<CustomerContractSummary[]> = this.modelService.contracts$;
  loading: Signal<boolean> = this.modelService.contractsLoading$;
  error: Signal<string | null> = this.modelService.contractsError$;

  ngOnInit(): void {
    this.controller.loadContracts();
  }

  onRetry(): void {
    this.controller.loadContracts();
  }

  onView(contract: CustomerContractSummary): void {
    this.router.navigate(['/contracts', contract.id]);
  }

  formatDate(date: string | null): string {
    return formatDate(date);
  }

  formatDateTime(date: string | null): string {
    return formatDateTime(date);
  }

  formatCurrency(amount: number, currency: string): string {
    if (amount === undefined || amount === null) return 'N/A';
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: currency || 'EUR'
    }).format(amount);
  }

  getStateClass(state: string): string {
    switch (state) {
      case 'DRAFT': return 'badge-secondary';
      case 'OFFERED': return 'badge-info';
      case 'ACCEPTED':
      case 'APPROVED':
      case 'RUNNING': return 'badge-success';
      case 'AWAITING_PAYMENT': return 'badge-warning';
      case 'CANCELLED':
      case 'EXPIRED':
      case 'TERMINATED': return 'badge-error';
      default: return 'badge-secondary';
    }
  }
}
