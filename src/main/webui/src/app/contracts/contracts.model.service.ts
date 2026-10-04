import { Injectable, signal, Signal } from '@angular/core';

export interface CustomerContractSummary {
  id: string;
  contractReference: string;
  sellerOrganisationId: string;
  contractDate: string | null;
  currency: string;
  grandTotal: number;
  state: string;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerContractLineItem {
  id: string;
  displayOrder: number;
  lineTotal: number;
  productInstance: unknown;
  productCode: string | null;
  productDescription: string | null;
}

export interface ContractTermsLink {
  id: string;
  termsCode: string | null;
  termsTitle: string | null;
  termsVersion: string | null;
  scope: string | null;
}

export interface CustomerContract {
  id: string;
  contractReference: string;
  sellerOrganisationId: string;
  contractDate: string | null;
  currency: string;
  grandTotal: number;
  state: string;
  publicNotes: string | null;
  createdAt: string;
  updatedAt: string;
  lineItems: CustomerContractLineItem[];
  termsLinks: ContractTermsLink[];
  checkoutUrl: string | null;
}

export interface OrganisationContractLineItem {
  id: string;
  displayOrder: number;
  lineTotal: number;
  productInstance: unknown;
}

export interface OrganisationContractSummary {
  id: string;
  contractReference: string;
  contractDate: string | null;
  currency: string;
  grandTotal: number;
  paymentModel: string;
  state: string;
  createdAt: string;
  updatedAt: string;
}

export interface OrganisationContract {
  id: string;
  contractReference: string;
  contractDate: string | null;
  currency: string;
  grandTotal: number;
  paymentModel: string;
  state: string;
  publicNotes: string | null;
  createdAt: string;
  updatedAt: string;
  lineItems: OrganisationContractLineItem[];
}

export interface ContractStateChange {
  id: string;
  processInstanceId: string;
  stepTimestamp: string;
  fromState: string | null;
  toState: string;
  actorUserId: string | null;
  reason: string | null;
}

export interface PaymentAttempt {
  id: string;
  status: string;
  grossAmount: number;
  feeAmount: number | null;
  netAmount: number | null;
  currency: string;
  pspIdentifier: string | null;
  pspSessionId: string | null;
  pspTransactionRef: string | null;
  checkoutUrl: string | null;
  correlationId: string;
  createdAt: string;
  updatedAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class ContractsModelService {

  // Contracts list state
  private contracts = signal<CustomerContractSummary[]>([]);
  private contractsLoading = signal<boolean>(false);
  private contractsError = signal<string | null>(null);

  // Selected contract state
  private selectedContract = signal<CustomerContract | null>(null);
  private selectedContractLoading = signal<boolean>(false);
  private selectedContractError = signal<string | null>(null);

  // State changes
  private stateChanges = signal<ContractStateChange[]>([]);
  private stateChangesLoading = signal<boolean>(false);
  private stateChangesError = signal<string | null>(null);

  // Payment attempts
  private paymentAttempts = signal<PaymentAttempt[]>([]);
  private paymentAttemptsLoading = signal<boolean>(false);
  private paymentAttemptsError = signal<string | null>(null);

  // Organisation contracts list state
  private orgContracts = signal<OrganisationContractSummary[]>([]);
  private orgContractsLoading = signal<boolean>(false);
  private orgContractsError = signal<string | null>(null);

  // Selected organisation contract state (loaded from the seller view endpoint)
  private selectedOrgContract = signal<CustomerContract | null>(null);
  private selectedOrgContractLoading = signal<boolean>(false);
  private selectedOrgContractError = signal<string | null>(null);

  // Organisation state changes
  private orgStateChanges = signal<ContractStateChange[]>([]);
  private orgStateChangesLoading = signal<boolean>(false);
  private orgStateChangesError = signal<string | null>(null);

  // Organisation payment attempts
  private orgPaymentAttempts = signal<PaymentAttempt[]>([]);
  private orgPaymentAttemptsLoading = signal<boolean>(false);
  private orgPaymentAttemptsError = signal<string | null>(null);

  // Readonly signals
  contracts$: Signal<CustomerContractSummary[]> = this.contracts.asReadonly();
  contractsLoading$: Signal<boolean> = this.contractsLoading.asReadonly();
  contractsError$: Signal<string | null> = this.contractsError.asReadonly();

  selectedContract$: Signal<CustomerContract | null> = this.selectedContract.asReadonly();
  selectedContractLoading$: Signal<boolean> = this.selectedContractLoading.asReadonly();
  selectedContractError$: Signal<string | null> = this.selectedContractError.asReadonly();

  stateChanges$: Signal<ContractStateChange[]> = this.stateChanges.asReadonly();
  stateChangesLoading$: Signal<boolean> = this.stateChangesLoading.asReadonly();
  stateChangesError$: Signal<string | null> = this.stateChangesError.asReadonly();

  paymentAttempts$: Signal<PaymentAttempt[]> = this.paymentAttempts.asReadonly();
  paymentAttemptsLoading$: Signal<boolean> = this.paymentAttemptsLoading.asReadonly();
  paymentAttemptsError$: Signal<string | null> = this.paymentAttemptsError.asReadonly();

  orgContracts$: Signal<OrganisationContractSummary[]> = this.orgContracts.asReadonly();
  orgContractsLoading$: Signal<boolean> = this.orgContractsLoading.asReadonly();
  orgContractsError$: Signal<string | null> = this.orgContractsError.asReadonly();

  selectedOrgContract$: Signal<CustomerContract | null> = this.selectedOrgContract.asReadonly();
  selectedOrgContractLoading$: Signal<boolean> = this.selectedOrgContractLoading.asReadonly();
  selectedOrgContractError$: Signal<string | null> = this.selectedOrgContractError.asReadonly();

  orgStateChanges$: Signal<ContractStateChange[]> = this.orgStateChanges.asReadonly();
  orgStateChangesLoading$: Signal<boolean> = this.orgStateChangesLoading.asReadonly();
  orgStateChangesError$: Signal<string | null> = this.orgStateChangesError.asReadonly();

  orgPaymentAttempts$: Signal<PaymentAttempt[]> = this.orgPaymentAttempts.asReadonly();
  orgPaymentAttemptsLoading$: Signal<boolean> = this.orgPaymentAttemptsLoading.asReadonly();
  orgPaymentAttemptsError$: Signal<string | null> = this.orgPaymentAttemptsError.asReadonly();

  // Setters
  setContracts(contracts: CustomerContractSummary[]) {
    this.contracts.set(contracts);
  }

  setContractsLoading(loading: boolean) {
    this.contractsLoading.set(loading);
  }

  setContractsError(error: string | null) {
    this.contractsError.set(error);
  }

  setSelectedContract(contract: CustomerContract | null) {
    this.selectedContract.set(contract);
  }

  setSelectedContractLoading(loading: boolean) {
    this.selectedContractLoading.set(loading);
  }

  setSelectedContractError(error: string | null) {
    this.selectedContractError.set(error);
  }

  setStateChanges(stateChanges: ContractStateChange[]) {
    this.stateChanges.set(stateChanges);
  }

  setStateChangesLoading(loading: boolean) {
    this.stateChangesLoading.set(loading);
  }

  setStateChangesError(error: string | null) {
    this.stateChangesError.set(error);
  }

  setPaymentAttempts(paymentAttempts: PaymentAttempt[]) {
    this.paymentAttempts.set(paymentAttempts);
  }

  setPaymentAttemptsLoading(loading: boolean) {
    this.paymentAttemptsLoading.set(loading);
  }

  setPaymentAttemptsError(error: string | null) {
    this.paymentAttemptsError.set(error);
  }

  setOrgContracts(contracts: OrganisationContractSummary[]) {
    this.orgContracts.set(contracts);
  }

  setOrgContractsLoading(loading: boolean) {
    this.orgContractsLoading.set(loading);
  }

  setOrgContractsError(error: string | null) {
    this.orgContractsError.set(error);
  }

  setSelectedOrgContract(contract: CustomerContract | null) {
    this.selectedOrgContract.set(contract);
  }

  setSelectedOrgContractLoading(loading: boolean) {
    this.selectedOrgContractLoading.set(loading);
  }

  setSelectedOrgContractError(error: string | null) {
    this.selectedOrgContractError.set(error);
  }

  setOrgStateChanges(stateChanges: ContractStateChange[]) {
    this.orgStateChanges.set(stateChanges);
  }

  setOrgStateChangesLoading(loading: boolean) {
    this.orgStateChangesLoading.set(loading);
  }

  setOrgStateChangesError(error: string | null) {
    this.orgStateChangesError.set(error);
  }

  setOrgPaymentAttempts(paymentAttempts: PaymentAttempt[]) {
    this.orgPaymentAttempts.set(paymentAttempts);
  }

  setOrgPaymentAttemptsLoading(loading: boolean) {
    this.orgPaymentAttemptsLoading.set(loading);
  }

  setOrgPaymentAttemptsError(error: string | null) {
    this.orgPaymentAttemptsError.set(error);
  }
}
