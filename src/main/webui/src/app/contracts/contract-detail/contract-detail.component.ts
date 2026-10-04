import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import {
  ContractsModelService,
  CustomerContract,
  ContractStateChange,
  PaymentAttempt,
} from '../contracts.model.service';
import { ContractsController } from '../contracts.controller';
import { formatDateTime as formatDateTimeString } from '../date-format';

@Component({
  selector: 'app-contract-detail',
  imports: [CommonModule],
  templateUrl: './contract-detail.component.html',
  styleUrl: './contract-detail.component.scss'
})
export class ContractDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private modelService = inject(ContractsModelService);
  private controller = inject(ContractsController);

  contract: Signal<CustomerContract | null> = this.modelService.selectedContract$;
  contractLoading: Signal<boolean> = this.modelService.selectedContractLoading$;
  contractError: Signal<string | null> = this.modelService.selectedContractError$;

  stateChanges: Signal<ContractStateChange[]> = this.modelService.stateChanges$;
  stateChangesLoading: Signal<boolean> = this.modelService.stateChangesLoading$;

  paymentAttempts: Signal<PaymentAttempt[]> = this.modelService.paymentAttempts$;
  paymentAttemptsLoading: Signal<boolean> = this.modelService.paymentAttemptsLoading$;

  contractId: string | null = null;

  ngOnInit(): void {
    this.contractId = this.route.snapshot.paramMap.get('id');
    if (this.contractId) {
      this.controller.getContract(this.contractId);
    } else {
      this.router.navigate(['/contracts']);
    }
  }

  onBack(): void {
    this.router.navigate(['/contracts']);
  }

  formatDate(date: string | null): string {
    return formatDateTimeString(date);
  }

  formatDateTime(date: string | null): string {
    return formatDateTimeString(date);
  }

  formatCurrency(amount: number | null, currency: string): string {
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

  getPaymentStatusClass(status: string): string {
    switch (status) {
      case 'SUCCEEDED': return 'badge-success';
      case 'FAILED': return 'badge-error';
      case 'PENDING': return 'badge-warning';
      default: return 'badge-secondary';
    }
  }
}
