/// <reference types="jasmine" />
import { TestBed } from '@angular/core/testing';
import {
  ContractsModelService,
  CustomerContractSummary,
  CustomerContract,
  OrganisationContractSummary,
  ContractStateChange,
  PaymentAttempt,
} from './contracts.model.service';

describe('ContractsModelService', () => {
  let service: ContractsModelService;

  const mockContractSummary: CustomerContractSummary = {
    id: 'contract-1',
    contractReference: 'REF-001',
    sellerOrganisationId: 'org-1',
    contractDate: '2024-01-15',
    currency: 'EUR',
    grandTotal: 123.45,
    state: 'AWAITING_PAYMENT',
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
  };

  const mockContract: CustomerContract = {
    ...mockContractSummary,
    publicNotes: 'notes',
    lineItems: [],
    termsLinks: [],
    checkoutUrl: null,
  };

  const mockStateChange: ContractStateChange = {
    id: 'step-1',
    processInstanceId: 'pi-1',
    stepTimestamp: '2024-01-15T10:00:00Z',
    fromState: 'DRAFT',
    toState: 'OFFERED',
    actorUserId: 'user-1',
    reason: null,
  };

  const mockPaymentAttempt: PaymentAttempt = {
    id: 'tx-1',
    status: 'PENDING',
    grossAmount: 123.45,
    feeAmount: null,
    netAmount: null,
    currency: 'EUR',
    pspIdentifier: 'stripe',
    pspSessionId: 'cs_123',
    pspTransactionRef: null,
    checkoutUrl: null,
    correlationId: 'corr-1',
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
  };

  const mockOrgContractSummary: OrganisationContractSummary = {
    id: 'org-contract-1',
    contractReference: 'ORG-REF-001',
    contractDate: '2024-01-15',
    currency: 'EUR',
    grandTotal: 123.45,
    paymentModel: 'PREPAID',
    state: 'RUNNING',
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
  };

  const mockOrgContract: CustomerContract = {
    id: 'org-contract-1',
    contractReference: 'ORG-REF-001',
    sellerOrganisationId: 'seller-org-1',
    contractDate: '2024-01-15',
    currency: 'EUR',
    grandTotal: 123.45,
    state: 'RUNNING',
    publicNotes: 'org notes',
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
    lineItems: [],
    termsLinks: [],
    checkoutUrl: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ContractsModelService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Contracts list', () => {
    it('should have empty contracts initially', () => {
      expect(service.contracts$()).toEqual([]);
      expect(service.contractsLoading$()).toBe(false);
      expect(service.contractsError$()).toBeNull();
    });

    it('should set contracts', () => {
      service.setContracts([mockContractSummary]);
      expect(service.contracts$()).toEqual([mockContractSummary]);
    });

    it('should set loading and error states', () => {
      service.setContractsLoading(true);
      expect(service.contractsLoading$()).toBe(true);
      service.setContractsError('Failed');
      expect(service.contractsError$()).toBe('Failed');
    });
  });

  describe('Selected contract', () => {
    it('should have no selected contract initially', () => {
      expect(service.selectedContract$()).toBeNull();
    });

    it('should set selected contract', () => {
      service.setSelectedContract(mockContract);
      expect(service.selectedContract$()).toEqual(mockContract);
    });

    it('should set loading and error states', () => {
      service.setSelectedContractLoading(true);
      expect(service.selectedContractLoading$()).toBe(true);
      service.setSelectedContractError('Failed');
      expect(service.selectedContractError$()).toBe('Failed');
    });
  });

  describe('State changes', () => {
    it('should have empty state changes initially', () => {
      expect(service.stateChanges$()).toEqual([]);
    });

    it('should set state changes', () => {
      service.setStateChanges([mockStateChange]);
      expect(service.stateChanges$()).toEqual([mockStateChange]);
    });

    it('should set loading and error states', () => {
      service.setStateChangesLoading(true);
      expect(service.stateChangesLoading$()).toBe(true);
      service.setStateChangesError('Failed');
      expect(service.stateChangesError$()).toBe('Failed');
    });
  });

  describe('Payment attempts', () => {
    it('should have empty payment attempts initially', () => {
      expect(service.paymentAttempts$()).toEqual([]);
    });

    it('should set payment attempts', () => {
      service.setPaymentAttempts([mockPaymentAttempt]);
      expect(service.paymentAttempts$()).toEqual([mockPaymentAttempt]);
    });

    it('should set loading and error states', () => {
      service.setPaymentAttemptsLoading(true);
      expect(service.paymentAttemptsLoading$()).toBe(true);
      service.setPaymentAttemptsError('Failed');
      expect(service.paymentAttemptsError$()).toBe('Failed');
    });
  });

  describe('State isolation', () => {
    it('should keep different state types isolated', () => {
      service.setContracts([mockContractSummary]);
      service.setSelectedContract(mockContract);
      service.setStateChanges([mockStateChange]);
      service.setPaymentAttempts([mockPaymentAttempt]);

      expect(service.contracts$()).toEqual([mockContractSummary]);
      expect(service.selectedContract$()).toEqual(mockContract);
      expect(service.stateChanges$()).toEqual([mockStateChange]);
      expect(service.paymentAttempts$()).toEqual([mockPaymentAttempt]);
    });
  });

  describe('Organisation contracts list', () => {
    it('should have empty organisation contracts initially', () => {
      expect(service.orgContracts$()).toEqual([]);
      expect(service.orgContractsLoading$()).toBe(false);
      expect(service.orgContractsError$()).toBeNull();
    });

    it('should set organisation contracts', () => {
      service.setOrgContracts([mockOrgContractSummary]);
      expect(service.orgContracts$()).toEqual([mockOrgContractSummary]);
    });

    it('should set loading and error states', () => {
      service.setOrgContractsLoading(true);
      expect(service.orgContractsLoading$()).toBe(true);
      service.setOrgContractsError('Failed');
      expect(service.orgContractsError$()).toBe('Failed');
    });
  });

  describe('Selected organisation contract', () => {
    it('should have no selected organisation contract initially', () => {
      expect(service.selectedOrgContract$()).toBeNull();
    });

    it('should set selected organisation contract', () => {
      service.setSelectedOrgContract(mockOrgContract);
      expect(service.selectedOrgContract$()).toEqual(mockOrgContract);
    });

    it('should set loading and error states', () => {
      service.setSelectedOrgContractLoading(true);
      expect(service.selectedOrgContractLoading$()).toBe(true);
      service.setSelectedOrgContractError('Failed');
      expect(service.selectedOrgContractError$()).toBe('Failed');
    });
  });

  describe('Organisation state changes and payment attempts', () => {
    it('should set organisation state changes', () => {
      service.setOrgStateChanges([mockStateChange]);
      expect(service.orgStateChanges$()).toEqual([mockStateChange]);
    });

    it('should set organisation payment attempts', () => {
      service.setOrgPaymentAttempts([mockPaymentAttempt]);
      expect(service.orgPaymentAttempts$()).toEqual([mockPaymentAttempt]);
    });
  });

  describe('Customer and organisation state isolation', () => {
    it('should keep customer and organisation contract state isolated', () => {
      service.setContracts([mockContractSummary]);
      service.setOrgContracts([mockOrgContractSummary]);

      expect(service.contracts$()).toEqual([mockContractSummary]);
      expect(service.orgContracts$()).toEqual([mockOrgContractSummary]);
    });
  });
});
