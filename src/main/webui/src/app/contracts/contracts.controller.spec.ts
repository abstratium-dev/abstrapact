/// <reference types="jasmine" />
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ContractsController } from './contracts.controller';
import { ContractsModelService, CustomerContractSummary, CustomerContract, OrganisationContractSummary, ContractStateChange, PaymentAttempt } from './contracts.model.service';

describe('ContractsController', () => {
  let controller: ContractsController;
  let httpMock: HttpTestingController;
  let modelService: ContractsModelService;

  const mockContractSummary: CustomerContractSummary = {
    id: 'contract-1',
    contractReference: 'REF-001',
    sellerOrganisationId: 'org-1',
    contractDate: '2024-01-15',
    currency: 'EUR',
    grandTotal: 123.45,
    state: 'DRAFT',
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
  };

  const mockContract: CustomerContract = {
    ...mockContractSummary,
    publicNotes: null,
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
    publicNotes: null,
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
    lineItems: [],
    termsLinks: [],
    checkoutUrl: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ContractsController, ContractsModelService],
    });

    controller = TestBed.inject(ContractsController);
    httpMock = TestBed.inject(HttpTestingController);
    modelService = TestBed.inject(ContractsModelService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(controller).toBeTruthy();
  });

  describe('loadContracts', () => {
    it('should load contracts into model', () => {
      controller.loadContracts();

      expect(modelService.contractsLoading$()).toBe(true);
      expect(modelService.contractsError$()).toBeNull();

      const req = httpMock.expectOne('/api/public/sales/contracts');
      req.flush([mockContractSummary]);

      expect(modelService.contracts$()).toEqual([mockContractSummary]);
      expect(modelService.contractsLoading$()).toBe(false);
    });

    it('should set error state on failure', () => {
      controller.loadContracts();

      const req = httpMock.expectOne('/api/public/sales/contracts');
      req.flush('error', { status: 500, statusText: 'Server Error' });

      expect(modelService.contracts$()).toEqual([]);
      expect(modelService.contractsError$()).toBe('Failed to load contracts');
      expect(modelService.contractsLoading$()).toBe(false);
    });
  });

  describe('getContract', () => {
    it('should load contract details, state changes and payment attempts', async () => {
      const promise = controller.getContract('contract-1');

      const contractReq = httpMock.expectOne('/api/public/sales/contracts/contract-1');
      contractReq.flush(mockContract);

      // Let the controller's async continuation reach the related loads.
      await Promise.resolve();

      const stateChangeReq = httpMock.expectOne('/api/public/sales/contracts/contract-1/state-changes');
      stateChangeReq.flush([mockStateChange]);

      const paymentReq = httpMock.expectOne('/api/public/sales/contracts/contract-1/payment-attempts');
      paymentReq.flush([mockPaymentAttempt]);

      const result = await promise;
      expect(result).toEqual(mockContract);
      expect(modelService.selectedContract$()).toEqual(mockContract);
      expect(modelService.stateChanges$()).toEqual([mockStateChange]);
      expect(modelService.paymentAttempts$()).toEqual([mockPaymentAttempt]);
      expect(modelService.selectedContractLoading$()).toBe(false);
    });

    it('should sort state changes and payment attempts by timestamp descending', async () => {
      const earlierStateChange: ContractStateChange = {
        ...mockStateChange,
        id: 'step-earlier',
        stepTimestamp: '2024-01-15T09:00:00Z',
      };
      const laterStateChange: ContractStateChange = {
        ...mockStateChange,
        id: 'step-later',
        stepTimestamp: '2024-01-15T11:00:00Z',
      };
      const earlierPayment: PaymentAttempt = {
        ...mockPaymentAttempt,
        id: 'tx-earlier',
        createdAt: '2024-01-15T09:00:00Z',
        updatedAt: '2024-01-15T09:00:00Z',
      };
      const laterPayment: PaymentAttempt = {
        ...mockPaymentAttempt,
        id: 'tx-later',
        createdAt: '2024-01-15T11:00:00Z',
        updatedAt: '2024-01-15T11:00:00Z',
      };

      const promise = controller.getContract('contract-1');

      const contractReq = httpMock.expectOne('/api/public/sales/contracts/contract-1');
      contractReq.flush(mockContract);

      await Promise.resolve();

      const stateChangeReq = httpMock.expectOne('/api/public/sales/contracts/contract-1/state-changes');
      stateChangeReq.flush([earlierStateChange, mockStateChange, laterStateChange]);

      const paymentReq = httpMock.expectOne('/api/public/sales/contracts/contract-1/payment-attempts');
      paymentReq.flush([laterPayment, earlierPayment, mockPaymentAttempt]);

      await promise;

      expect(modelService.stateChanges$().map(c => c.id))
        .toEqual(['step-later', 'step-1', 'step-earlier']);
      expect(modelService.paymentAttempts$().map(t => t.id))
        .toEqual(['tx-later', 'tx-1', 'tx-earlier']);
    });

    it('should set error state when contract load fails', async () => {
      const promise = controller.getContract('contract-1');

      const contractReq = httpMock.expectOne('/api/public/sales/contracts/contract-1');
      contractReq.flush('error', { status: 500, statusText: 'Server Error' });

      const result = await promise;
      expect(result).toBeNull();
      expect(modelService.selectedContractError$()).toBe('Failed to load contract');
      expect(modelService.selectedContractLoading$()).toBe(false);
    });
  });

  describe('loadOrganisationContracts', () => {
    it('should load organisation contracts into model', () => {
      controller.loadOrganisationContracts();

      expect(modelService.orgContractsLoading$()).toBe(true);
      expect(modelService.orgContractsError$()).toBeNull();

      const req = httpMock.expectOne('/api/contracts');
      req.flush([mockOrgContractSummary]);

      expect(modelService.orgContracts$()).toEqual([mockOrgContractSummary]);
      expect(modelService.orgContractsLoading$()).toBe(false);
    });

    it('should set error state on failure', () => {
      controller.loadOrganisationContracts();

      const req = httpMock.expectOne('/api/contracts');
      req.flush('error', { status: 500, statusText: 'Server Error' });

      expect(modelService.orgContracts$()).toEqual([]);
      expect(modelService.orgContractsError$()).toBe('Failed to load organisation contracts');
      expect(modelService.orgContractsLoading$()).toBe(false);
    });
  });

  describe('getOrganisationContract', () => {
    it('should load organisation contract details, state changes and payment attempts', async () => {
      const promise = controller.getOrganisationContract('org-contract-1');

      const contractReq = httpMock.expectOne('/api/public/sales/contracts/org-contract-1');
      contractReq.flush(mockOrgContract);

      await Promise.resolve();

      const stateChangeReq = httpMock.expectOne('/api/public/sales/contracts/org-contract-1/state-changes');
      stateChangeReq.flush([mockStateChange]);

      const paymentReq = httpMock.expectOne('/api/public/sales/contracts/org-contract-1/payment-attempts');
      paymentReq.flush([mockPaymentAttempt]);

      const result = await promise;
      expect(result).toEqual(mockOrgContract);
      expect(modelService.selectedOrgContract$()).toEqual(mockOrgContract);
      expect(modelService.orgStateChanges$()).toEqual([mockStateChange]);
      expect(modelService.orgPaymentAttempts$()).toEqual([mockPaymentAttempt]);
      expect(modelService.selectedOrgContractLoading$()).toBe(false);
    });

    it('should sort organisation state changes and payment attempts by timestamp descending', async () => {
      const earlierStateChange: ContractStateChange = {
        ...mockStateChange,
        id: 'step-earlier',
        stepTimestamp: '2024-01-15T09:00:00Z',
      };
      const laterStateChange: ContractStateChange = {
        ...mockStateChange,
        id: 'step-later',
        stepTimestamp: '2024-01-15T11:00:00Z',
      };
      const earlierPayment: PaymentAttempt = {
        ...mockPaymentAttempt,
        id: 'tx-earlier',
        createdAt: '2024-01-15T09:00:00Z',
        updatedAt: '2024-01-15T09:00:00Z',
      };
      const laterPayment: PaymentAttempt = {
        ...mockPaymentAttempt,
        id: 'tx-later',
        createdAt: '2024-01-15T11:00:00Z',
        updatedAt: '2024-01-15T11:00:00Z',
      };

      const promise = controller.getOrganisationContract('org-contract-1');

      const contractReq = httpMock.expectOne('/api/public/sales/contracts/org-contract-1');
      contractReq.flush(mockOrgContract);

      await Promise.resolve();

      const stateChangeReq = httpMock.expectOne('/api/public/sales/contracts/org-contract-1/state-changes');
      stateChangeReq.flush([earlierStateChange, mockStateChange, laterStateChange]);

      const paymentReq = httpMock.expectOne('/api/public/sales/contracts/org-contract-1/payment-attempts');
      paymentReq.flush([laterPayment, earlierPayment, mockPaymentAttempt]);

      await promise;

      expect(modelService.orgStateChanges$().map(c => c.id))
        .toEqual(['step-later', 'step-1', 'step-earlier']);
      expect(modelService.orgPaymentAttempts$().map(t => t.id))
        .toEqual(['tx-later', 'tx-1', 'tx-earlier']);
    });

    it('should set error state when organisation contract load fails', async () => {
      const promise = controller.getOrganisationContract('org-contract-1');

      const contractReq = httpMock.expectOne('/api/public/sales/contracts/org-contract-1');
      contractReq.flush('error', { status: 500, statusText: 'Server Error' });

      const result = await promise;
      expect(result).toBeNull();
      expect(modelService.selectedOrgContractError$()).toBe('Failed to load organisation contract');
      expect(modelService.selectedOrgContractLoading$()).toBe(false);
    });
  });
});
