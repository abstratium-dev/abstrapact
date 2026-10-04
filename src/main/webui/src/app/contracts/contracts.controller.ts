import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import {
  ContractsModelService,
  CustomerContract,
  CustomerContractSummary,
  ContractStateChange,
  PaymentAttempt,
  OrganisationContract,
  OrganisationContractSummary,
} from './contracts.model.service';

@Injectable({
  providedIn: 'root',
})
export class ContractsController {

  private modelService = inject(ContractsModelService);
  private http = inject(HttpClient);

  loadContracts() {
    this.modelService.setContractsLoading(true);
    this.modelService.setContractsError(null);

    this.http.get<CustomerContractSummary[]>('/api/public/sales/contracts').subscribe({
      next: (contracts) => {
        this.modelService.setContracts(contracts);
        this.modelService.setContractsLoading(false);
      },
      error: (err) => {
        console.error('Error loading contracts:', err);
        this.modelService.setContracts([]);
        this.modelService.setContractsError('Failed to load contracts');
        this.modelService.setContractsLoading(false);
      }
    });
  }

  async getContract(id: string): Promise<CustomerContract | null> {
    this.modelService.setSelectedContractLoading(true);
    this.modelService.setSelectedContractError(null);
    this.modelService.setStateChanges([]);
    this.modelService.setStateChangesError(null);
    this.modelService.setPaymentAttempts([]);
    this.modelService.setPaymentAttemptsError(null);

    try {
      const contract = await firstValueFrom(
        this.http.get<CustomerContract>(`/api/public/sales/contracts/${id}`)
      );
      this.modelService.setSelectedContract(contract);
      this.modelService.setSelectedContractLoading(false);

      // Load related state changes and payment attempts. They are independent,
      // so they can be triggered synchronously without awaiting either.
      this.loadStateChanges(id);
      this.loadPaymentAttempts(id);

      return contract;
    } catch (error) {
      console.error('Error loading contract:', error);
      this.modelService.setSelectedContract(null);
      this.modelService.setSelectedContractError('Failed to load contract');
      this.modelService.setSelectedContractLoading(false);
      return null;
    }
  }

  private loadStateChanges(id: string): void {
    this.modelService.setStateChangesLoading(true);
    this.modelService.setStateChangesError(null);

    this.http.get<ContractStateChange[]>(`/api/public/sales/contracts/${id}/state-changes`).subscribe({
      next: (changes) => {
        const sorted = [...changes].sort(
          (a, b) => new Date(b.stepTimestamp).getTime() - new Date(a.stepTimestamp).getTime()
        );
        this.modelService.setStateChanges(sorted);
        this.modelService.setStateChangesLoading(false);
      },
      error: (err) => {
        console.error('Error loading state changes:', err);
        this.modelService.setStateChanges([]);
        this.modelService.setStateChangesError('Failed to load state changes');
        this.modelService.setStateChangesLoading(false);
      }
    });
  }

  private loadPaymentAttempts(id: string): void {
    this.modelService.setPaymentAttemptsLoading(true);
    this.modelService.setPaymentAttemptsError(null);

    this.http.get<PaymentAttempt[]>(`/api/public/sales/contracts/${id}/payment-attempts`).subscribe({
      next: (attempts) => {
        const sorted = [...attempts].sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
        this.modelService.setPaymentAttempts(sorted);
        this.modelService.setPaymentAttemptsLoading(false);
      },
      error: (err) => {
        console.error('Error loading payment attempts:', err);
        this.modelService.setPaymentAttempts([]);
        this.modelService.setPaymentAttemptsError('Failed to load payment attempts');
        this.modelService.setPaymentAttemptsLoading(false);
      }
    });
  }

  loadOrganisationContracts() {
    this.modelService.setOrgContractsLoading(true);
    this.modelService.setOrgContractsError(null);

    this.http.get<OrganisationContractSummary[]>('/api/contracts').subscribe({
      next: (contracts) => {
        this.modelService.setOrgContracts(contracts);
        this.modelService.setOrgContractsLoading(false);
      },
      error: (err) => {
        console.error('Error loading organisation contracts:', err);
        this.modelService.setOrgContracts([]);
        this.modelService.setOrgContractsError('Failed to load organisation contracts');
        this.modelService.setOrgContractsLoading(false);
      }
    });
  }

  async getOrganisationContract(id: string): Promise<OrganisationContract | null> {
    this.modelService.setSelectedOrgContractLoading(true);
    this.modelService.setSelectedOrgContractError(null);
    this.modelService.setOrgStateChanges([]);
    this.modelService.setOrgStateChangesError(null);
    this.modelService.setOrgPaymentAttempts([]);
    this.modelService.setOrgPaymentAttemptsError(null);

    try {
      const contract = await firstValueFrom(
        this.http.get<OrganisationContract>(`/api/contracts/${id}`)
      );
      this.modelService.setSelectedOrgContract(contract);
      this.modelService.setSelectedOrgContractLoading(false);

      this.loadOrganisationStateChanges(id);
      this.loadOrganisationPaymentAttempts(id);

      return contract;
    } catch (error) {
      console.error('Error loading organisation contract:', error);
      this.modelService.setSelectedOrgContract(null);
      this.modelService.setSelectedOrgContractError('Failed to load organisation contract');
      this.modelService.setSelectedOrgContractLoading(false);
      return null;
    }
  }

  private loadOrganisationStateChanges(id: string): void {
    this.modelService.setOrgStateChangesLoading(true);
    this.modelService.setOrgStateChangesError(null);

    this.http.get<ContractStateChange[]>(`/api/contracts/${id}/state-changes`).subscribe({
      next: (changes) => {
        const sorted = [...changes].sort(
          (a, b) => new Date(b.stepTimestamp).getTime() - new Date(a.stepTimestamp).getTime()
        );
        this.modelService.setOrgStateChanges(sorted);
        this.modelService.setOrgStateChangesLoading(false);
      },
      error: (err) => {
        console.error('Error loading organisation state changes:', err);
        this.modelService.setOrgStateChanges([]);
        this.modelService.setOrgStateChangesError('Failed to load organisation state changes');
        this.modelService.setOrgStateChangesLoading(false);
      }
    });
  }

  private loadOrganisationPaymentAttempts(id: string): void {
    this.modelService.setOrgPaymentAttemptsLoading(true);
    this.modelService.setOrgPaymentAttemptsError(null);

    this.http.get<PaymentAttempt[]>(`/api/contracts/${id}/payment-attempts`).subscribe({
      next: (attempts) => {
        const sorted = [...attempts].sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
        this.modelService.setOrgPaymentAttempts(sorted);
        this.modelService.setOrgPaymentAttemptsLoading(false);
      },
      error: (err) => {
        console.error('Error loading organisation payment attempts:', err);
        this.modelService.setOrgPaymentAttempts([]);
        this.modelService.setOrgPaymentAttemptsError('Failed to load organisation payment attempts');
        this.modelService.setOrgPaymentAttemptsLoading(false);
      }
    });
  }
}
