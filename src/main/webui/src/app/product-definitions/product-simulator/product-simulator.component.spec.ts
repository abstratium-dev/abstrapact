import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductSimulatorComponent } from './product-simulator.component';
import { Controller } from '../../controller';
import { ToastService } from '../../core/toast/toast.service';
import { ActivatedRoute, Router } from '@angular/router';
import { CompleteProductResponse, DataType } from '../../model.service';

describe('ProductSimulatorComponent', () => {
  let component: ProductSimulatorComponent;
  let fixture: ComponentFixture<ProductSimulatorComponent>;
  let controller: jasmine.SpyObj<Controller>;
  let toastService: jasmine.SpyObj<ToastService>;
  let router: jasmine.SpyObj<Router>;
  let activatedRoute: any;

  const mockCompleteProduct: CompleteProductResponse = {
    id: 'prod-1',
    productCode: 'LAPTOP-BUNDLE',
    description: 'Laptop Bundle',
    billingModel: 'FIXED_PRICE',
    productValidFrom: null,
    productValidUntil: null,
    parts: [
      {
        id: 'part-1',
        organisationId: 'org-1',
        partCode: 'LAPTOP',
        description: 'Laptop Base',
        unitPrice: 999,
        displayOrder: 1,
        attributes: [
          {
            id: 'attr-1',
            organisationId: 'org-1',
            attributeName: 'color',
            dataType: 'STRING' as DataType,
            isRequired: true,
            defaultValue: 'silver',
            allowedValues: [
              { id: 'val-1', organisationId: 'org-1', allowedValue: 'silver' },
              { id: 'val-2', organisationId: 'org-1', allowedValue: 'black' }
            ]
          }
        ],
        childParts: [
          {
            id: 'part-2',
            organisationId: 'org-1',
            partCode: 'PROCESSOR',
            description: 'Processor Choice',
            unitPrice: 0,
            displayOrder: 1,
            attributes: [],
            childParts: [
              {
                id: 'part-3',
                organisationId: 'org-1',
                partCode: 'I7',
                description: 'Intel i7',
                unitPrice: 200,
                displayOrder: 1,
                attributes: [],
                childParts: []
              },
              {
                id: 'part-4',
                organisationId: 'org-1',
                partCode: 'I5',
                description: 'Intel i5',
                unitPrice: 0,
                displayOrder: 2,
                attributes: [],
                childParts: []
              }
            ]
          }
        ]
      }
    ]
  };

  beforeEach(async () => {
    controller = jasmine.createSpyObj('Controller', ['getCompleteProduct']);
    toastService = jasmine.createSpyObj('ToastService', ['success', 'error']);
    router = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [ProductSimulatorComponent],
      providers: [
        { provide: Controller, useValue: controller },
        { provide: ToastService, useValue: toastService },
        { provide: Router, useValue: router },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => key === 'id' ? 'prod-1' : null
              }
            }
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProductSimulatorComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    controller.getCompleteProduct.and.returnValue(Promise.resolve(mockCompleteProduct));
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  describe('Loading', () => {
    it('should load product on init', async () => {
      controller.getCompleteProduct.and.returnValue(Promise.resolve(mockCompleteProduct));
      fixture.detectChanges();
      await fixture.whenStable();

      expect(controller.getCompleteProduct).toHaveBeenCalledWith('prod-1');
      expect(component.completeProduct).toEqual(mockCompleteProduct);
      expect(component.loading).toBe(false);
    });

    it('should show error when product not found', async () => {
      controller.getCompleteProduct.and.returnValue(Promise.resolve(null));
      fixture.detectChanges();
      await fixture.whenStable();

      expect(component.error).toBe('Product definition not found');
      expect(component.loading).toBe(false);
    });

    it('should show error when no product ID', async () => {
      const route = TestBed.inject(ActivatedRoute);
      route.snapshot.paramMap.get = () => null;

      fixture = TestBed.createComponent(ProductSimulatorComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();

      expect(component.error).toBe('No product ID provided');
    });
  });

  describe('Configuration', () => {
    beforeEach(async () => {
      controller.getCompleteProduct.and.returnValue(Promise.resolve(mockCompleteProduct));
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should create simulated parts from definition', () => {
      expect(component.simulatedParts.length).toBe(1);
      expect(component.simulatedParts[0].definition.partCode).toBe('LAPTOP');
      expect(component.simulatedParts[0].included).toBe(true);
    });

    it('should set default attribute values', () => {
      const laptop = component.simulatedParts[0];
      expect(laptop.attributeValues['attr-1']).toBe('silver');
    });

    it('should toggle part inclusion', () => {
      const laptop = component.simulatedParts[0];
      laptop.included = false;
      expect(laptop.included).toBe(false);
    });

    it('should set attribute value', () => {
      const laptop = component.simulatedParts[0];
      component.setAttributeValue(laptop, 'attr-1', 'black');
      expect(laptop.attributeValues['attr-1']).toBe('black');
    });

    it('should detect required attribute validation', () => {
      const laptop = component.simulatedParts[0];
      laptop.attributeValues['attr-1'] = '';
      expect(component.areAllRequiredAttributesFilled(laptop)).toBe(false);
    });

    it('should pass validation with filled required attributes', () => {
      const laptop = component.simulatedParts[0];
      expect(component.areAllRequiredAttributesFilled(laptop)).toBe(true);
    });

    it('should be valid when all required attributes are filled', () => {
      expect(component.isConfigurationValid()).toBe(true);
    });

    it('should be invalid when required attribute is empty', () => {
      component.simulatedParts[0].attributeValues['attr-1'] = '';
      expect(component.isConfigurationValid()).toBe(false);
    });
  });

  describe('Price Calculation', () => {
    beforeEach(async () => {
      controller.getCompleteProduct.and.returnValue(Promise.resolve(mockCompleteProduct));
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should calculate total price with all parts included', () => {
      // LAPTOP: 999 + I7: 200 = 1199
      const total = component.calculateTotalPrice(component.simulatedParts);
      expect(total).toBe(1199);
    });

    it('should exclude unchecked parts from price', () => {
      const laptop = component.simulatedParts[0];
      const processor = laptop.children[0];
      const i7 = processor.children[0];
      i7.included = false;
      i7.children.forEach(c => c.included = false);

      // LAPTOP: 999 + I5: 0 = 999
      const total = component.calculateTotalPrice(component.simulatedParts);
      expect(total).toBe(999);
    });

    it('should exclude parent when unchecked', () => {
      const laptop = component.simulatedParts[0];
      laptop.included = false;

      const total = component.calculateTotalPrice(component.simulatedParts);
      expect(total).toBe(0);
    });
  });

  describe('Instance Generation', () => {
    beforeEach(async () => {
      controller.getCompleteProduct.and.returnValue(Promise.resolve(mockCompleteProduct));
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should generate instance when valid', () => {
      component.generateInstance();

      expect(component.showResult).toBe(true);
      expect(component.instanceResult).not.toBeNull();
      expect(component.instanceResult!.productCode).toBe('LAPTOP-BUNDLE');
      expect(component.instanceResult!.totalPrice).toBe(1199);
    });

    it('should show error toast when invalid', () => {
      component.simulatedParts[0].attributeValues['attr-1'] = '';
      component.generateInstance();

      expect(component.showResult).toBe(false);
      expect(toastService.error).toHaveBeenCalledWith('Please fill in all required attributes before generating the instance.');
    });

    it('should collect only included parts', () => {
      component.generateInstance();
      const included = component.collectIncludedParts(component.instanceResult!.parts);
      expect(included.length).toBe(4); // LAPTOP, PROCESSOR, I7, I5
    });

    it('should reset simulator', () => {
      component.generateInstance();
      expect(component.showResult).toBe(true);

      component.resetSimulator();
      expect(component.showResult).toBe(false);
      expect(component.instanceResult).toBeNull();
      expect(component.simulatedParts.length).toBe(1);
    });
  });

  describe('Navigation', () => {
    beforeEach(async () => {
      controller.getCompleteProduct.and.returnValue(Promise.resolve(mockCompleteProduct));
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('should navigate back to product detail', () => {
      component.onBack();
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions', 'prod-1']);
    });

    it('should navigate to list when no product ID', () => {
      component.productId = null;
      component.onBack();
      expect(router.navigate).toHaveBeenCalledWith(['/product-definitions']);
    });
  });

  describe('Utility Methods', () => {
    it('should format price correctly', () => {
      expect(component.formatPrice(99.99)).toBe('$99.99');
      expect(component.formatPrice(0)).toBe('$0.00');
    });

    it('should return correct data type labels', () => {
      expect(component.getDataTypeLabel('STRING')).toBe('String');
      expect(component.getDataTypeLabel('INTEGER')).toBe('Integer');
      expect(component.getDataTypeLabel('DECIMAL')).toBe('Decimal');
      expect(component.getDataTypeLabel('BOOLEAN')).toBe('Boolean');
      expect(component.getDataTypeLabel('DATE')).toBe('Date');
    });

    it('should return correct input types', () => {
      expect(component.getAttributeInputType('STRING')).toBe('text');
      expect(component.getAttributeInputType('INTEGER')).toBe('number');
      expect(component.getAttributeInputType('DECIMAL')).toBe('number');
      expect(component.getAttributeInputType('DATE')).toBe('date');
      expect(component.getAttributeInputType('BOOLEAN')).toBe('checkbox');
    });
  });
});
