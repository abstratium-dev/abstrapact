import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductDefinitionFormComponent } from './product-definition-form.component';
import { Controller } from '../../controller';
import { ModelService, ProductDefinition } from '../../model.service';
import { ToastService } from '../../core/toast/toast.service';
import { signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';

describe('ProductDefinitionFormComponent', () => {
  let component: ProductDefinitionFormComponent;
  let fixture: ComponentFixture<ProductDefinitionFormComponent>;
  let controller: jasmine.SpyObj<Controller>;
  let modelService: jasmine.SpyObj<ModelService>;
  let toastService: jasmine.SpyObj<ToastService>;
  let router: jasmine.SpyObj<Router>;
  let activatedRoute: any;

  let selectedProductSignal: ReturnType<typeof signal<ProductDefinition | null>>;

  const mockProductDefinition: ProductDefinition = {
    id: '1',
    organisationId: 'org-1',
    productCode: 'PROD-001',
    description: 'Test Product',
    billingModel: 'FIXED_PRICE',
    productValidFrom: '2024-01-01',
    productValidUntil: '2024-12-31'
  };

  beforeEach(async () => {
    const controllerSpy = jasmine.createSpyObj('Controller', [
      'getProductDefinition',
      'createProductDefinition',
      'updateProductDefinition'
    ]);

    selectedProductSignal = signal<ProductDefinition | null>(null);

    const modelServiceSpy = jasmine.createSpyObj('ModelService', [], {
      selectedProductDefinition$: selectedProductSignal.asReadonly()
    });

    const toastServiceSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [ProductDefinitionFormComponent, FormsModule],
      providers: [
        { provide: Controller, useValue: controllerSpy },
        { provide: ModelService, useValue: modelServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => key === 'id' ? null : null
              }
            }
          }
        }
      ]
    }).compileComponents();

    controller = TestBed.inject(Controller) as jasmine.SpyObj<Controller>;
    modelService = TestBed.inject(ModelService) as jasmine.SpyObj<ModelService>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    activatedRoute = TestBed.inject(ActivatedRoute);

    fixture = TestBed.createComponent(ProductDefinitionFormComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Create Mode', () => {
    it('should initialize in create mode when no id param', () => {
      fixture.detectChanges();
      expect(component.isEditMode).toBe(false);
      expect(component.getPageTitle()).toBe('Create Product Definition');
    });

    it('should have empty form fields in create mode', () => {
      fixture.detectChanges();
      expect(component.productCode).toBe('');
      expect(component.description).toBe('');
      expect(component.billingModel).toBe('FIXED_PRICE');
      expect(component.productValidFrom).toBeNull();
      expect(component.productValidUntil).toBeNull();
    });
  });

  describe('Edit Mode', () => {
    beforeEach(async () => {
      activatedRoute.snapshot.paramMap.get = (key: string) => key === 'id' ? '1' : null;
      controller.getProductDefinition.and.returnValue(Promise.resolve(mockProductDefinition));

      fixture = TestBed.createComponent(ProductDefinitionFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should initialize in edit mode when id param present', () => {
      expect(component.isEditMode).toBe(true);
      expect(component.productId).toBe('1');
    });

    it('should load product definition in edit mode', () => {
      expect(controller.getProductDefinition).toHaveBeenCalledWith('1');
    });

    it('should populate form fields', async () => {
      await component.loadProductDefinition('1');
      expect(component.productCode).toBe('PROD-001');
      expect(component.description).toBe('Test Product');
      expect(component.billingModel).toBe('FIXED_PRICE');
      expect(component.productValidFrom).toBe('2024-01-01');
      expect(component.productValidUntil).toBe('2024-12-31');
    });

    it('should show error when product not found', async () => {
      controller.getProductDefinition.and.returnValue(Promise.resolve(null));
      await component.loadProductDefinition('999');
      expect(component.formError).toBe('Failed to load product definition');
    });
  });

  describe('Form Validation', () => {
    beforeEach(() => {
      fixture.detectChanges();
    });

    it('should validate required product code', () => {
      component.productCode = '';
      const isValid = component.validateForm();
      expect(isValid).toBe(false);
      expect(component.fieldErrors['productCode']).toBe('Product code is required');
    });

    it('should validate product code max length', () => {
      component.productCode = 'a'.repeat(51);
      const isValid = component.validateForm();
      expect(isValid).toBe(false);
      expect(component.fieldErrors['productCode']).toBe('Product code must be 50 characters or less');
    });

    it('should validate description max length', () => {
      component.productCode = 'PROD-001';
      component.description = 'a'.repeat(256);
      const isValid = component.validateForm();
      expect(isValid).toBe(false);
      expect(component.fieldErrors['description']).toBe('Description must be 255 characters or less');
    });

    it('should validate date range', () => {
      component.productCode = 'PROD-001';
      component.productValidFrom = '2024-12-31';
      component.productValidUntil = '2024-01-01';
      const isValid = component.validateForm();
      expect(isValid).toBe(false);
      expect(component.fieldErrors['productValidUntil']).toBe('Valid until date must be after valid from date');
    });

    it('should pass validation with valid data', () => {
      component.productCode = 'PROD-001';
      component.description = 'Valid description';
      component.billingModel = 'FIXED_PRICE';
      component.productValidFrom = '2024-01-01';
      component.productValidUntil = '2024-12-31';
      const isValid = component.validateForm();
      expect(isValid).toBe(true);
      expect(Object.keys(component.fieldErrors).length).toBe(0);
    });
  });

  describe('Form Submission', () => {
    beforeEach(() => {
      fixture.detectChanges();
      component.productCode = 'PROD-001';
      component.description = 'Test Product';
      component.billingModel = 'FIXED_PRICE';
    });

    it('should not submit if validation fails', async () => {
      component.productCode = '';
      await component.onSubmit();
      expect(controller.createProductDefinition).not.toHaveBeenCalled();
    });

    it('should create product definition successfully', async () => {
      controller.createProductDefinition.and.returnValue(Promise.resolve(mockProductDefinition));

      await component.onSubmit();

      expect(controller.createProductDefinition).toHaveBeenCalledWith({
        productCode: 'PROD-001',
        description: 'Test Product',
        billingModel: 'FIXED_PRICE',
        productValidFrom: null,
        productValidUntil: null
      });
      expect(toastService.success).toHaveBeenCalledWith('Product definition created successfully');
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions']);
    });

    it('should handle 409 conflict error', async () => {
      const error = { status: 409, error: 'Product code already exists' };
      controller.createProductDefinition.and.returnValue(Promise.reject(error));

      await component.onSubmit();

      expect(component.formError).toBe('A product definition with this code already exists');
      expect(component.submitting).toBe(false);
    });

    it('should handle 400 bad request error', async () => {
      const error = { status: 400, error: 'Invalid product code' };
      controller.createProductDefinition.and.returnValue(Promise.reject(error));

      await component.onSubmit();

      expect(component.formError).toBe('Invalid product code');
    });

    it('should handle generic error', async () => {
      controller.createProductDefinition.and.returnValue(Promise.reject(new Error('Network error')));

      await component.onSubmit();

      expect(component.formError).toBe('Failed to save product definition. Please try again.');
    });

    it('should set submitting state during submission', async () => {
      let submittingDuringCall = false;
      controller.createProductDefinition.and.callFake(() => {
        submittingDuringCall = component.submitting;
        return Promise.resolve(mockProductDefinition);
      });

      await component.onSubmit();

      expect(submittingDuringCall).toBe(true);
      expect(component.submitting).toBe(false);
    });
  });

  describe('Cancel', () => {
    it('should navigate to list on cancel in create mode', () => {
      fixture.detectChanges();
      component.onCancel();
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions']);
    });

    it('should navigate to detail on cancel in edit mode', () => {
      activatedRoute.snapshot.paramMap.get = (key: string) => key === 'id' ? '123' : null;
      fixture = TestBed.createComponent(ProductDefinitionFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();

      component.onCancel();
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions', '123']);
    });
  });

  describe('Page Title', () => {
    it('should return create title in create mode', () => {
      fixture.detectChanges();
      expect(component.getPageTitle()).toBe('Create Product Definition');
    });

    it('should return edit title in edit mode', () => {
      activatedRoute.snapshot.paramMap.get = (key: string) => key === 'id' ? '1' : null;
      fixture = TestBed.createComponent(ProductDefinitionFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      expect(component.getPageTitle()).toBe('Edit Product Definition');
    });
  });
});
