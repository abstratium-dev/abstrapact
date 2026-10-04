/// <reference types="jasmine" />
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Router } from '@angular/router';
import { OrganisationContractDetailComponent } from './organisation-contract-detail.component';
import {
  ContractsModelService,
  CustomerContract,
  ContractStateChange,
  PaymentAttempt,
} from '../contracts.model.service';
import { ContractsController } from '../contracts.controller';

describe('OrganisationContractDetailComponent', () => {
  let component: OrganisationContractDetailComponent;
  let fixture: ComponentFixture<OrganisationContractDetailComponent>;
  let controller: jasmine.SpyObj<ContractsController>;
  let router: jasmine.SpyObj<Router>;

  let selectedContractSignal: ReturnType<typeof signal<CustomerContract | null>>;
  let selectedContractLoadingSignal: ReturnType<typeof signal<boolean>>;
  let selectedContractErrorSignal: ReturnType<typeof signal<string | null>>;
  let stateChangesSignal: ReturnType<typeof signal<ContractStateChange[]>>;
  let stateChangesLoadingSignal: ReturnType<typeof signal<boolean>>;
  let paymentAttemptsSignal: ReturnType<typeof signal<PaymentAttempt[]>>;
  let paymentAttemptsLoadingSignal: ReturnType<typeof signal<boolean>>;

  const mockContract: CustomerContract = {
    id: 'org-contract-1',
    contractReference: 'ORG-REF-001',
    sellerOrganisationId: 'seller-org-1',
    contractDate: '2024-01-15',
    currency: 'EUR',
    grandTotal: 123.45,
    state: 'RUNNING',
    publicNotes: 'Test notes',
    createdAt: '2024-01-15T10:00:00Z',
    updatedAt: '2024-01-15T10:00:00Z',
    lineItems: [
      {
        id: 'li-1',
        displayOrder: 0,
        lineTotal: 123.45,
        productInstance: {},
        productCode: 'PROD-001',
        productDescription: 'Test product',
      }
    ],
    termsLinks: [
      {
        id: 'tl-1',
        termsCode: 'T&C-001',
        termsTitle: 'General Terms',
        termsVersion: '1.0',
        scope: 'GENERAL',
      }
    ],
    checkoutUrl: null,
  };

  const mockStateChanges: ContractStateChange[] = [
    {
      id: 'step-1',
      processInstanceId: 'pi-1',
      stepTimestamp: '2024-01-15T10:00:00Z',
      fromState: 'DRAFT',
      toState: 'OFFERED',
      actorUserId: 'user-1',
      reason: null,
    }
  ];

  const mockPaymentAttempts: PaymentAttempt[] = [
    {
      id: 'tx-1',
      status: 'SUCCEEDED',
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
    }
  ];

  beforeEach(async () => {
    const controllerSpy = jasmine.createSpyObj('ContractsController', ['getOrganisationContract']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    selectedContractSignal = signal<CustomerContract | null>(null);
    selectedContractLoadingSignal = signal<boolean>(false);
    selectedContractErrorSignal = signal<string | null>(null);
    stateChangesSignal = signal<ContractStateChange[]>([]);
    stateChangesLoadingSignal = signal<boolean>(false);
    paymentAttemptsSignal = signal<PaymentAttempt[]>([]);
    paymentAttemptsLoadingSignal = signal<boolean>(false);

    const modelServiceSpy = jasmine.createSpyObj('ContractsModelService', [], {
      selectedOrgContract$: selectedContractSignal.asReadonly(),
      selectedOrgContractLoading$: selectedContractLoadingSignal.asReadonly(),
      selectedOrgContractError$: selectedContractErrorSignal.asReadonly(),
      orgStateChanges$: stateChangesSignal.asReadonly(),
      orgStateChangesLoading$: stateChangesLoadingSignal.asReadonly(),
      orgPaymentAttempts$: paymentAttemptsSignal.asReadonly(),
      orgPaymentAttemptsLoading$: paymentAttemptsLoadingSignal.asReadonly(),
    });

    await TestBed.configureTestingModule({
      imports: [OrganisationContractDetailComponent],
      providers: [
        { provide: ContractsController, useValue: controllerSpy },
        { provide: ContractsModelService, useValue: modelServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ id: 'org-contract-1' })
            }
          }
        }
      ]
    }).compileComponents();

    controller = TestBed.inject(ContractsController) as jasmine.SpyObj<ContractsController>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    fixture = TestBed.createComponent(OrganisationContractDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Initialization', () => {
    it('should load organisation contract on init', () => {
      fixture.detectChanges();
      expect(controller.getOrganisationContract).toHaveBeenCalledWith('org-contract-1');
    });

    it('should navigate back when no contract id', async () => {
      TestBed.resetTestingModule();
      const controllerSpy = jasmine.createSpyObj('ContractsController', ['getOrganisationContract']);
      const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
      const modelServiceSpy = jasmine.createSpyObj('ContractsModelService', [], {
        selectedOrgContract$: selectedContractSignal.asReadonly(),
        selectedOrgContractLoading$: selectedContractLoadingSignal.asReadonly(),
        selectedOrgContractError$: selectedContractErrorSignal.asReadonly(),
        orgStateChanges$: stateChangesSignal.asReadonly(),
        orgStateChangesLoading$: stateChangesLoadingSignal.asReadonly(),
        orgPaymentAttempts$: paymentAttemptsSignal.asReadonly(),
        orgPaymentAttemptsLoading$: paymentAttemptsLoadingSignal.asReadonly(),
      });

      await TestBed.configureTestingModule({
        imports: [OrganisationContractDetailComponent],
        providers: [
          { provide: ContractsController, useValue: controllerSpy },
          { provide: ContractsModelService, useValue: modelServiceSpy },
          { provide: Router, useValue: routerSpy },
          {
            provide: ActivatedRoute,
            useValue: {
              snapshot: {
                paramMap: convertToParamMap({})
              }
            }
          }
        ]
      }).compileComponents();

      const localFixture = TestBed.createComponent(OrganisationContractDetailComponent);
      localFixture.detectChanges();

      expect(routerSpy.navigate).toHaveBeenCalledWith(['/organisation-contracts']);
    });
  });

  describe('Navigation', () => {
    it('should navigate back to organisation contracts list', () => {
      component.onBack();
      expect(router.navigate).toHaveBeenCalledWith(['/organisation-contracts']);
    });
  });

  describe('Utility Methods', () => {
    it('should format date correctly', () => {
      expect(component.formatDate('2024-01-15')).toBe('2024-01-15');
    });

    it('should return N/A for null date', () => {
      expect(component.formatDate(null)).toBe('N/A');
    });

    it('should format datetime correctly', () => {
      const input = '2024-01-15T10:00:00Z';
      const d = new Date(input);
      const pad2 = (n: number) => n.toString().padStart(2, '0');
      const expected = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ` +
        `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}.${d.getMilliseconds().toString().padStart(3, '0')}`;
      expect(component.formatDateTime(input)).toBe(expected);
    });

    it('should format currency correctly', () => {
      expect(component.formatCurrency(123.45, 'EUR')).toContain('€');
    });

    it('should return correct state classes', () => {
      expect(component.getStateClass('DRAFT')).toBe('badge-secondary');
      expect(component.getStateClass('OFFERED')).toBe('badge-info');
      expect(component.getStateClass('ACCEPTED')).toBe('badge-success');
      expect(component.getStateClass('AWAITING_PAYMENT')).toBe('badge-warning');
      expect(component.getStateClass('CANCELLED')).toBe('badge-error');
    });

    it('should return correct payment status classes', () => {
      expect(component.getPaymentStatusClass('SUCCEEDED')).toBe('badge-success');
      expect(component.getPaymentStatusClass('FAILED')).toBe('badge-error');
      expect(component.getPaymentStatusClass('PENDING')).toBe('badge-warning');
      expect(component.getPaymentStatusClass('UNKNOWN')).toBe('badge-secondary');
    });
  });

  describe('Template Rendering', () => {
    it('should show loading state', () => {
      selectedContractLoadingSignal.set(true);
      fixture.detectChanges();

      const loadingElement = fixture.nativeElement.querySelector('.loading');
      expect(loadingElement).toBeTruthy();
      expect(loadingElement.textContent).toContain('Loading contract');
    });

    it('should show error state', () => {
      selectedContractErrorSignal.set('Failed to load contract');
      fixture.detectChanges();

      const errorElement = fixture.nativeElement.querySelector('.error-box');
      expect(errorElement).toBeTruthy();
      expect(errorElement.textContent).toContain('Failed to load contract');
    });

    it('should render organisation contract details', () => {
      selectedContractSignal.set(mockContract);
      fixture.detectChanges();

      const title = fixture.nativeElement.querySelector('.card-header h2');
      expect(title.textContent).toContain('ORG-REF-001');

      const badge = fixture.nativeElement.querySelector('.badge');
      expect(badge.textContent).toContain('RUNNING');
    });

    it('should render state changes', () => {
      selectedContractSignal.set(mockContract);
      stateChangesSignal.set(mockStateChanges);
      fixture.detectChanges();

      const rows = fixture.nativeElement.querySelectorAll('.detail-row');
      expect(rows.length).toBeGreaterThan(0);
    });

    it('should render payment attempts', () => {
      selectedContractSignal.set(mockContract);
      paymentAttemptsSignal.set(mockPaymentAttempts);
      fixture.detectChanges();

      const badges = fixture.nativeElement.querySelectorAll('.badge');
      expect(badges.length).toBeGreaterThan(0);
    });

    it('should render line items with product code and description', () => {
      selectedContractSignal.set(mockContract);
      fixture.detectChanges();

      const lineItemsCard = fixture.nativeElement.querySelector('[data-testid="organisation-line-items-card"]');
      expect(lineItemsCard).toBeTruthy();
      expect(lineItemsCard.textContent).toContain('PROD-001');
      expect(lineItemsCard.textContent).toContain('Test product');
    });

    it('should render terms and conditions', () => {
      selectedContractSignal.set(mockContract);
      fixture.detectChanges();

      const termsCard = fixture.nativeElement.querySelector('[data-testid="organisation-terms-links-card"]');
      expect(termsCard).toBeTruthy();
      expect(termsCard.textContent).toContain('General Terms');
      expect(termsCard.textContent).toContain('v1.0');
    });

    it('should render fee and net amounts for payment attempts', () => {
      const attemptWithFeeAndNet: PaymentAttempt = {
        ...mockPaymentAttempts[0],
        feeAmount: 2.50,
        netAmount: 120.95,
      };
      selectedContractSignal.set(mockContract);
      paymentAttemptsSignal.set([attemptWithFeeAndNet]);
      fixture.detectChanges();

      const row = fixture.nativeElement.querySelector('.payment-attempt-row');
      expect(row.textContent).toContain('fee');
      expect(row.textContent).toContain('net');
      expect(row.textContent).toContain('2.50');
      expect(row.textContent).toContain('120.95');
    });
  });
});
