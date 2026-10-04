import { Component, inject, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import {
  ContractsModelService,
  OrganisationContractSummary,
} from '../contracts.model.service';
import { ContractsController } from '../contracts.controller';
import { formatDateTime } from '../date-format';

@Component({
  selector: 'app-organisation-contracts-list',
  imports: [CommonModule],
  templateUrl: './organisation-contracts-list.component.html',
  styleUrl: './organisation-contracts-list.component.scss'
})
export class OrganisationContractsListComponent implements OnInit {
  private modelService = inject(ContractsModelService);
  private controller = inject(ContractsController);
  private router = inject(Router);

  contracts: Signal<OrganisationContractSummary[]> = this.modelService.orgContracts$;
  loading: Signal<boolean> = this.modelService.orgContractsLoading$;
  error: Signal<string | null> = this.modelService.orgContractsError$;

  ngOnInit(): void {
    this.controller.loadOrganisationContracts();
  }

  onRetry(): void {
    this.controller.loadOrganisationContracts();
  }

  onView(contract: OrganisationContractSummary): void {
    this.router.navigate(['/organisation-contracts', contract.id]);
  }

  formatDate(date: string | null): string {
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
