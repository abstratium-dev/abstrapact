/// <reference types="jasmine" />
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router } from '@angular/router';
import { OrganisationContractsListComponent } from './organisation-contracts-list.component';
import { ContractsModelService, OrganisationContractSummary } from '../contracts.model.service';
import { ContractsController } from '../contracts.controller';

describe('OrganisationContractsListComponent', () => {
  let component: OrganisationContractsListComponent;
  let fixture: ComponentFixture<OrganisationContractsListComponent>;
  let controller: jasmine.SpyObj<ContractsController>;
  let modelService: jasmine.SpyObj<ContractsModelService>;
  let router: jasmine.SpyObj<Router>;

  let contractsSignal: ReturnType<typeof signal<OrganisationContractSummary[]>>;
  let loadingSignal: ReturnType<typeof signal<boolean>>;
  let errorSignal: ReturnType<typeof signal<string | null>>;

  const mockContracts: OrganisationContractSummary[] = [
    {
      id: 'org-contract-1',
      contractReference: 'ORG-REF-001',
      contractDate: '2024-01-15',
      currency: 'EUR',
      grandTotal: 123.45,
      paymentModel: 'PREPAID',
      state: 'RUNNING',
      createdAt: '2024-01-15T10:00:00Z',
      updatedAt: '2024-01-15T10:00:00Z',
    },
    {
      id: 'org-contract-2',
      contractReference: 'ORG-REF-002',
      contractDate: null,
      currency: 'CHF',
      grandTotal: 99.99,
      paymentModel: 'POSTPAID',
      state: 'DRAFT',
      createdAt: '2024-01-16T10:00:00Z',
      updatedAt: '2024-01-16T10:00:00Z',
    }
  ];

  beforeEach(async () => {
    const controllerSpy = jasmine.createSpyObj('ContractsController', ['loadOrganisationContracts']);

    contractsSignal = signal<OrganisationContractSummary[]>([]);
    loadingSignal = signal<boolean>(false);
    errorSignal = signal<string | null>(null);

    const modelServiceSpy = jasmine.createSpyObj('ContractsModelService', [], {
      orgContracts$: contractsSignal.asReadonly(),
      orgContractsLoading$: loadingSignal.asReadonly(),
      orgContractsError$: errorSignal.asReadonly(),
    });

    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [OrganisationContractsListComponent],
      providers: [
        { provide: ContractsController, useValue: controllerSpy },
        { provide: ContractsModelService, useValue: modelServiceSpy },
        { provide: Router, useValue: routerSpy },
      ]
    }).compileComponents();

    controller = TestBed.inject(ContractsController) as jasmine.SpyObj<ContractsController>;
    modelService = TestBed.inject(ContractsModelService) as jasmine.SpyObj<ContractsModelService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    fixture = TestBed.createComponent(OrganisationContractsListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Initialization', () => {
    it('should load organisation contracts on init', () => {
      fixture.detectChanges();
      expect(controller.loadOrganisationContracts).toHaveBeenCalled();
    });
  });

  describe('Retry', () => {
    it('should retry loading on error', () => {
      fixture.detectChanges();
      component.onRetry();
      expect(controller.loadOrganisationContracts).toHaveBeenCalledTimes(2);
    });
  });

  describe('Navigation', () => {
    it('should navigate to organisation contract detail page', () => {
      const contract = mockContracts[0];
      component.onView(contract);
      expect(router.navigate).toHaveBeenCalledWith(['/organisation-contracts', contract.id]);
    });
  });

  describe('Utility Methods', () => {
    it('should format date correctly', () => {
      expect(component.formatDate('2024-01-15')).toBe('2024-01-15 00:00:00.000');
    });

    it('should return N/A for null date', () => {
      expect(component.formatDate(null)).toBe('N/A');
    });

    it('should format currency correctly', () => {
      expect(component.formatCurrency(123.45, 'EUR')).toContain('€');
      expect(component.formatCurrency(99.99, 'CHF')).toContain('99.99');
    });

    it('should return correct state classes', () => {
      expect(component.getStateClass('DRAFT')).toBe('badge-secondary');
      expect(component.getStateClass('OFFERED')).toBe('badge-info');
      expect(component.getStateClass('ACCEPTED')).toBe('badge-success');
      expect(component.getStateClass('AWAITING_PAYMENT')).toBe('badge-warning');
      expect(component.getStateClass('CANCELLED')).toBe('badge-error');
      expect(component.getStateClass('UNKNOWN')).toBe('badge-secondary');
    });
  });

  describe('Template Rendering', () => {
    it('should show loading state', () => {
      loadingSignal.set(true);
      fixture.detectChanges();

      const loadingElement = fixture.nativeElement.querySelector('.loading');
      expect(loadingElement).toBeTruthy();
      expect(loadingElement.textContent).toContain('Loading contracts');
    });

    it('should show error state', () => {
      errorSignal.set('Failed to load');
      fixture.detectChanges();

      const errorElement = fixture.nativeElement.querySelector('.error-box');
      expect(errorElement).toBeTruthy();
      expect(errorElement.textContent).toContain('Failed to load');
    });

    it('should show empty state when no contracts', () => {
      contractsSignal.set([]);
      fixture.detectChanges();

      const emptyElement = fixture.nativeElement.querySelector('.info-message');
      expect(emptyElement).toBeTruthy();
      expect(emptyElement.textContent).toContain('No contracts found');
    });

    it('should render organisation contract cards', () => {
      contractsSignal.set(mockContracts);
      fixture.detectChanges();

      const cards = fixture.nativeElement.querySelectorAll('.card');
      expect(cards.length).toBe(2);

      const firstTitle = cards[0].querySelector('.tile-title');
      expect(firstTitle.textContent).toContain('ORG-REF-001');

      const badges = fixture.nativeElement.querySelectorAll('.badge');
      expect(badges.length).toBe(2);
    });
  });
});
