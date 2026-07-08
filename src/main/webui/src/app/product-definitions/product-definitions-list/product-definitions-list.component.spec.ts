import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductDefinitionsListComponent } from './product-definitions-list.component';
import { ProductDefinitionsModelService, ProductDefinition } from '../product-definitions.model.service';
import { ProductDefinitionsController } from '../product-definitions.controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';
import { signal } from '@angular/core';
import { Router } from '@angular/router';

describe('ProductDefinitionsListComponent', () => {
  let component: ProductDefinitionsListComponent;
  let fixture: ComponentFixture<ProductDefinitionsListComponent>;
  let controller: jasmine.SpyObj<ProductDefinitionsController>;
  let modelService: jasmine.SpyObj<ProductDefinitionsModelService>;
  let toastService: jasmine.SpyObj<ToastService>;
  let confirmService: jasmine.SpyObj<ConfirmDialogService>;
  let router: jasmine.SpyObj<Router>;

  let productDefinitionsSignal: ReturnType<typeof signal<ProductDefinition[]>>;
  let loadingSignal: ReturnType<typeof signal<boolean>>;
  let errorSignal: ReturnType<typeof signal<string | null>>;

  const mockProductDefinitions: ProductDefinition[] = [
    {
      id: '1',
      organisationId: 'org-1',
      productCode: 'PROD-001',
      description: 'Test Product 1',
      billingModel: 'FIXED_PRICE',
      productValidFrom: '2024-01-01',
      productValidUntil: '2024-12-31',
      termsAndConditionsCode: null
    },
    {
      id: '2',
      organisationId: 'org-1',
      productCode: 'PROD-002',
      description: 'Test Product 2',
      billingModel: 'SUBSCRIPTION',
      productValidFrom: null,
      productValidUntil: null,
      termsAndConditionsCode: null
    }
  ];

  beforeEach(async () => {
    const controllerSpy = jasmine.createSpyObj('ProductDefinitionsController', [
      'loadProductDefinitions',
      'deleteProductDefinition'
    ]);

    productDefinitionsSignal = signal<ProductDefinition[]>([]);
    loadingSignal = signal<boolean>(false);
    errorSignal = signal<string | null>(null);

    const modelServiceSpy = jasmine.createSpyObj('ProductDefinitionsModelService', [], {
      productDefinitions$: productDefinitionsSignal.asReadonly(),
      productDefinitionsLoading$: loadingSignal.asReadonly(),
      productDefinitionsError$: errorSignal.asReadonly()
    });

    const toastServiceSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);
    const confirmServiceSpy = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [ProductDefinitionsListComponent],
      providers: [
        { provide: ProductDefinitionsController, useValue: controllerSpy },
        { provide: ProductDefinitionsModelService, useValue: modelServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: ConfirmDialogService, useValue: confirmServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    controller = TestBed.inject(ProductDefinitionsController) as jasmine.SpyObj<ProductDefinitionsController>;
    modelService = TestBed.inject(ProductDefinitionsModelService) as jasmine.SpyObj<ProductDefinitionsModelService>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    confirmService = TestBed.inject(ConfirmDialogService) as jasmine.SpyObj<ConfirmDialogService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    fixture = TestBed.createComponent(ProductDefinitionsListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Initialization', () => {
    it('should load product definitions on init', () => {
      fixture.detectChanges();
      expect(controller.loadProductDefinitions).toHaveBeenCalled();
    });
  });

  describe('Retry', () => {
    it('should retry loading on error', () => {
      fixture.detectChanges();
      component.onRetry();
      expect(controller.loadProductDefinitions).toHaveBeenCalledTimes(2);
    });
  });

  describe('Navigation', () => {
    it('should navigate to create page', () => {
      component.onCreate();
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions/new']);
    });

    it('should navigate to detail page', () => {
      const product = mockProductDefinitions[0];
      component.onView(product);
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions', product.id]);
    });

    it('should navigate to edit page', () => {
      const product = mockProductDefinitions[0];
      const event = new MouseEvent('click');
      spyOn(event, 'stopPropagation');
      component.onEdit(product, event);
      expect(event.stopPropagation).toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions', product.id, 'edit']);
    });

    it('should navigate to detail page via view button', () => {
      const product = mockProductDefinitions[0];
      const event = new MouseEvent('click');
      spyOn(event, 'stopPropagation');
      component.onViewDetail(product, event);
      expect(event.stopPropagation).toHaveBeenCalled();
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions', product.id]);
    });
  });

  describe('Delete', () => {
    it('should delete product definition when confirmed', async () => {
      confirmService.confirm.and.returnValue(Promise.resolve(true));
      controller.deleteProductDefinition.and.returnValue(Promise.resolve());

      const product = mockProductDefinitions[0];
      const event = new MouseEvent('click');
      spyOn(event, 'stopPropagation');

      await component.onDelete(product, event);

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(confirmService.confirm).toHaveBeenCalledWith({
        title: 'Delete Product Definition',
        message: `Are you sure you want to delete "${product.productCode}"? This action cannot be undone.`,
        confirmText: 'Delete',
        cancelText: 'Cancel',
        confirmClass: 'btn-danger'
      });
      expect(controller.deleteProductDefinition).toHaveBeenCalledWith(product.id);
      expect(toastService.success).toHaveBeenCalledWith('Product definition deleted successfully');
    });

    it('should not delete when cancelled', async () => {
      confirmService.confirm.and.returnValue(Promise.resolve(false));

      const product = mockProductDefinitions[0];
      const event = new MouseEvent('click');
      spyOn(event, 'stopPropagation');

      await component.onDelete(product, event);

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(controller.deleteProductDefinition).not.toHaveBeenCalled();
      expect(toastService.success).not.toHaveBeenCalled();
    });

    it('should show error when delete fails', async () => {
      confirmService.confirm.and.returnValue(Promise.resolve(true));
      controller.deleteProductDefinition.and.returnValue(Promise.reject(new Error('Delete failed')));

      const product = mockProductDefinitions[0];
      const event = new MouseEvent('click');
      spyOn(event, 'stopPropagation');

      await component.onDelete(product, event);

      expect(event.stopPropagation).toHaveBeenCalled();
      expect(toastService.error).toHaveBeenCalledWith('Failed to delete product definition. Please try again.');
    });
  });

  describe('Utility Methods', () => {
    it('should return correct billing model label for FIXED_PRICE', () => {
      expect(component.getBillingModelLabel('FIXED_PRICE')).toBe('Fixed Price');
    });

    it('should return correct billing model label for SUBSCRIPTION', () => {
      expect(component.getBillingModelLabel('SUBSCRIPTION')).toBe('Subscription');
    });

    it('should format date correctly', () => {
      const date = '2024-01-15';
      const formatted = component.formatDate(date);
      expect(formatted).toContain('15');
      expect(formatted).toContain('2024');
    });

    it('should return N/A for null date', () => {
      expect(component.formatDate(null)).toBe('N/A');
    });
  });

  describe('Template Rendering', () => {
    it('should show loading state', () => {
      loadingSignal.set(true);
      fixture.detectChanges();

      const loadingElement = fixture.nativeElement.querySelector('.loading');
      expect(loadingElement).toBeTruthy();
      expect(loadingElement.textContent).toContain('Loading product definitions');
    });

    it('should show error state', () => {
      errorSignal.set('Failed to load');
      fixture.detectChanges();

      const errorElement = fixture.nativeElement.querySelector('.error-box');
      expect(errorElement).toBeTruthy();
      expect(errorElement.textContent).toContain('Failed to load');
    });

    it('should show empty state when no products', () => {
      productDefinitionsSignal.set([]);
      fixture.detectChanges();

      const emptyElement = fixture.nativeElement.querySelector('.info-message');
      expect(emptyElement).toBeTruthy();
      expect(emptyElement.textContent).toContain('No product definitions found');
    });

    it('should render product tiles', () => {
      productDefinitionsSignal.set(mockProductDefinitions);
      fixture.detectChanges();

      const tiles = fixture.nativeElement.querySelectorAll('.tile');
      expect(tiles.length).toBe(2);

      const firstTileTitle = tiles[0].querySelector('.tile-title');
      expect(firstTileTitle.textContent).toContain('PROD-001');
    });

    it('should render billing model badges', () => {
      productDefinitionsSignal.set(mockProductDefinitions);
      fixture.detectChanges();

      const badges = fixture.nativeElement.querySelectorAll('.badge');
      expect(badges.length).toBe(4);
    });
  });
});
