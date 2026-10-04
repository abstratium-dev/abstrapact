import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import {
  ContractsModelService,
  OrganisationContract,
  ContractStateChange,
  PaymentAttempt,
} from '../contracts.model.service';
import { ContractsController } from '../contracts.controller';
import { formatDateTime } from '../date-format';

@Component({
  selector: 'app-organisation-contract-detail',
  imports: [CommonModule],
  templateUrl: './organisation-contract-detail.component.html',
  styleUrl: './organisation-contract-detail.component.scss'
})
export class OrganisationContractDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private modelService = inject(ContractsModelService);
  private controller = inject(ContractsController);

  contract: Signal<OrganisationContract | null> = this.modelService.selectedOrgContract$;
  contractLoading: Signal<boolean> = this.modelService.selectedOrgContractLoading$;
  contractError: Signal<string | null> = this.modelService.selectedOrgContractError$;

  stateChanges: Signal<ContractStateChange[]> = this.modelService.orgStateChanges$;
  stateChangesLoading: Signal<boolean> = this.modelService.orgStateChangesLoading$;

  paymentAttempts: Signal<PaymentAttempt[]> = this.modelService.orgPaymentAttempts$;
  paymentAttemptsLoading: Signal<boolean> = this.modelService.orgPaymentAttemptsLoading$;

  contractId: string | null = null;

  ngOnInit(): void {
    this.contractId = this.route.snapshot.paramMap.get('id');
    if (this.contractId) {
      this.controller.getOrganisationContract(this.contractId);
    } else {
      this.router.navigate(['/organisation-contracts']);
    }
  }

  onBack(): void {
    this.router.navigate(['/organisation-contracts']);
  }

  formatDate(date: string | null): string {
    return formatDateTime(date);
  }

  formatDateTime(date: string | null): string {
    return formatDateTime(date);
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
