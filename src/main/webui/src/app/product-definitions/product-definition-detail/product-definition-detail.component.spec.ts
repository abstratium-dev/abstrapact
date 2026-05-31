import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductDefinitionDetailComponent } from './product-definition-detail.component';
import { Controller } from '../../controller';
import { ModelService, ProductDefinition, PartDefinition } from '../../model.service';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';
import { signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

describe('ProductDefinitionDetailComponent', () => {
  let component: ProductDefinitionDetailComponent;
  let fixture: ComponentFixture<ProductDefinitionDetailComponent>;
  let controller: jasmine.SpyObj<Controller>;
  let modelService: jasmine.SpyObj<ModelService>;
  let toastService: jasmine.SpyObj<ToastService>;
  let confirmService: jasmine.SpyObj<ConfirmDialogService>;
  let router: jasmine.SpyObj<Router>;
  let activatedRoute: any;

  let selectedProductSignal: ReturnType<typeof signal<ProductDefinition | null>>;
  let selectedPartSignal: ReturnType<typeof signal<PartDefinition | null>>;

  const mockProductDefinition: ProductDefinition = {
    id: '1',
    organisationId: 'org-1',
    productCode: 'PROD-001',
    description: 'Test Product',
    billingModel: 'FIXED_PRICE',
    productValidFrom: '2024-01-01',
    productValidUntil: '2024-12-31'
  };

  const mockPart: PartDefinition = {
    id: 'part-1',
    organisationId: 'org-1',
    partCode: 'ROOT-PART',
    description: 'Root Part',
    unitPrice: 100,
    displayOrder: 1,
    minCardinality: 1,
    maxCardinality: 1,
    childParts: [],
    attributes: []
  };

  beforeEach(async () => {
    // Reset the signals before each test to ensure clean state
    selectedProductSignal = signal<ProductDefinition | null>(null);
    selectedPartSignal = signal<PartDefinition | null>(null);

    const modelServiceSpy = jasmine.createSpyObj('ModelService',
      ['setSelectedProductDefinition', 'setSelectedPart'],
      {
        selectedProductDefinition$: selectedProductSignal.asReadonly(),
        selectedPart$: selectedPartSignal.asReadonly()
      }
    );
    // Implement setSelectedProductDefinition to actually update the signal
    modelServiceSpy.setSelectedProductDefinition.and.callFake((product: ProductDefinition | null) => {
      selectedProductSignal.set(product);
    });
    // Implement setSelectedPart to actually update the signal
    modelServiceSpy.setSelectedPart.and.callFake((part: PartDefinition | null) => {
      selectedPartSignal.set(part);
    });

    // Create controller spy that also updates the model service like the real controller does
    const controllerSpy = jasmine.createSpyObj('Controller', [
      'getProductDefinition',
      'deleteProductDefinition',
      'loadProductParts'
    ]);
    // Make getProductDefinition update the model service like the real implementation
    controllerSpy.getProductDefinition.and.callFake((id: string) => {
      const product = id === '1' ? mockProductDefinition : null;
      modelServiceSpy.setSelectedProductDefinition(product);
      return Promise.resolve(product);
    });

    // Add mock signals for parts to support ProductStructureComponent
    Object.defineProperty(modelServiceSpy, 'productParts$', {
      get: () => signal([]).asReadonly()
    });
    Object.defineProperty(modelServiceSpy, 'productPartsLoading$', {
      get: () => signal(false).asReadonly()
    });
    Object.defineProperty(modelServiceSpy, 'productPartsError$', {
      get: () => signal(null).asReadonly()
    });

    const toastServiceSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);
    const confirmServiceSpy = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [ProductDefinitionDetailComponent],
      providers: [
        { provide: Controller, useValue: controllerSpy },
        { provide: ModelService, useValue: modelServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: ConfirmDialogService, useValue: confirmServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => key === 'id' ? '1' : null
              }
            }
          }
        }
      ]
    }).compileComponents();

    controller = TestBed.inject(Controller) as jasmine.SpyObj<Controller>;
    modelService = TestBed.inject(ModelService) as jasmine.SpyObj<ModelService>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    confirmService = TestBed.inject(ConfirmDialogService) as jasmine.SpyObj<ConfirmDialogService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    activatedRoute = TestBed.inject(ActivatedRoute);

    // Don't create fixture here - each test will create its own to avoid state pollution
  });

  it('should create', () => {
    controller.getProductDefinition.and.returnValue(Promise.resolve(mockProductDefinition));
    fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
    component = fixture.componentInstance;
    expect(component).toBeTruthy();
  });

  describe('Initialization', () => {
    beforeEach(() => {
      controller.getProductDefinition.and.returnValue(Promise.resolve(mockProductDefinition));
    });

    it('should load product definition on init', async () => {
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      expect(controller.getProductDefinition).toHaveBeenCalledWith('1');
    });

    it('should set productId from route param', () => {
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      expect(component.productId).toBe('1');
    });

    it('should show error when no id param', async () => {
      activatedRoute.snapshot.paramMap.get = () => null;
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      expect(component.error).toBe('No product ID provided');
      expect(component.loading).toBe(false);
    });

    it('should show error when product not found', async () => {
      controller.getProductDefinition.and.returnValue(Promise.resolve(null));
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();

      expect(component.error).toBe('Product definition not found');
      expect(component.loading).toBe(false);
    });

    it('should stop loading when product loaded', async () => {
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();

      expect(component.loading).toBe(false);
    });
  });

  describe('Navigation', () => {
    beforeEach(async () => {
      controller.getProductDefinition.and.returnValue(Promise.resolve(mockProductDefinition));
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should navigate back to list', () => {
      component.onBack();
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions']);
    });

    it('should navigate to edit page', () => {
      component.onEdit();
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions', '1', 'edit']);
    });
  });

  describe('Delete', () => {
    it('should delete product when confirmed', async () => {
      // Create fresh component - the callFake in beforeEach handles loading the product
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();

      confirmService.confirm.and.returnValue(Promise.resolve(true));
      controller.deleteProductDefinition.and.returnValue(Promise.resolve());

      await component.onDelete();

      expect(confirmService.confirm).toHaveBeenCalledWith({
        title: 'Delete Product Definition',
        message: `Are you sure you want to delete "${mockProductDefinition.productCode}"? This action cannot be undone.`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmClass: 'btn-danger'
      });
      expect(controller.deleteProductDefinition).toHaveBeenCalledWith('1');
      expect(toastService.success).toHaveBeenCalledWith('Product definition deleted successfully');
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions']);
    });

    it('should not delete when cancelled', async () => {
      // Create fresh component - the callFake in beforeEach handles loading the product
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();

      confirmService.confirm.and.returnValue(Promise.resolve(false));

      await component.onDelete();

      expect(confirmService.confirm).toHaveBeenCalled();
      expect(controller.deleteProductDefinition).not.toHaveBeenCalled();
      expect(toastService.success).not.toHaveBeenCalled();
    });

    it('should show error when delete fails', async () => {
      // Create fresh component - the callFake in beforeEach handles loading the product
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();

      confirmService.confirm.and.returnValue(Promise.resolve(true));
      controller.deleteProductDefinition.and.returnValue(Promise.reject(new Error('Delete failed')));

      await component.onDelete();

      expect(toastService.error).toHaveBeenCalledWith('Failed to delete product definition. Please try again.');
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should not delete if product is null', async () => {
      // Override the callFake to return null for this test
      controller.getProductDefinition.and.callFake((id: string) => {
        modelService.setSelectedProductDefinition(null);
        return Promise.resolve(null);
      });

      // Create a fresh component instance
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();

      // Now try to delete - component.selectedProduct will be null
      await component.onDelete();

      expect(confirmService.confirm).not.toHaveBeenCalled();
      expect(controller.deleteProductDefinition).not.toHaveBeenCalled();
    });
  });

  describe('Utility Methods', () => {
    beforeEach(async () => {
      controller.getProductDefinition.and.returnValue(Promise.resolve(mockProductDefinition));
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should return Fixed Price label', () => {
      expect(component.getBillingModelLabel('FIXED_PRICE')).toBe('Fixed Price');
    });

    it('should return Subscription label', () => {
      expect(component.getBillingModelLabel('SUBSCRIPTION')).toBe('Subscription');
    });

    it('should format date correctly', () => {
      const date = '2024-06-15';
      const formatted = component.formatDate(date);
      expect(formatted).toContain('15');
      expect(formatted).toContain('2024');
    });

    it('should return Not set for null date', () => {
      expect(component.formatDate(null)).toBe('Not set');
    });
  });

  describe('Template Rendering', () => {
    it('should show loading state', async () => {
      // Override callFake to never resolve for this test
      controller.getProductDefinition.and.callFake(() => new Promise(() => {}));
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      const loadingElement = fixture.nativeElement.querySelector('.loading');
      expect(loadingElement).toBeTruthy();
      expect(loadingElement.textContent).toContain('Loading product definition');
    });

    it('should show error state when product not found', async () => {
      // Override callFake to return null for this test
      controller.getProductDefinition.and.callFake((id: string) => {
        modelService.setSelectedProductDefinition(null);
        return Promise.resolve(null);
      });
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const errorElement = fixture.nativeElement.querySelector('.error-box');
      expect(errorElement).toBeTruthy();
      expect(errorElement.textContent).toContain('Product definition not found');
    });

    it('should render product details when loaded', async () => {
      // callFake in beforeEach handles loading the product by default
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      // After loading, the card should be rendered
      const card = fixture.nativeElement.querySelector('.card');
      expect(card).toBeTruthy();

      const title = card.querySelector('h2');
      expect(title.textContent).toContain('PROD-001');
    });

    it('should render action buttons when loaded', async () => {
      // callFake in beforeEach handles loading the product by default
      fixture = TestBed.createComponent(ProductDefinitionDetailComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const cardActions = fixture.nativeElement.querySelector('.card-actions');
      expect(cardActions).toBeTruthy();

      const buttons = cardActions.querySelectorAll('button');
      expect(buttons.length).toBe(3); // Simulate, Edit and Delete
    });
  });
});
