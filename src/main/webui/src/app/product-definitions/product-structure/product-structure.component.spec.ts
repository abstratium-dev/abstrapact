import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductStructureComponent } from './product-structure.component';
import { ProductDefinitionsModelService, PartDefinition } from '../product-definitions.model.service';
import { ProductDefinitionsController } from '../product-definitions.controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';
import { of } from 'rxjs';
import { signal } from '@angular/core';

describe('ProductStructureComponent', () => {
  let component: ProductStructureComponent;
  let fixture: ComponentFixture<ProductStructureComponent>;
  let modelService: jasmine.SpyObj<ProductDefinitionsModelService>;
  let controller: jasmine.SpyObj<ProductDefinitionsController>;
  let toastService: jasmine.SpyObj<ToastService>;
  let confirmService: jasmine.SpyObj<ConfirmDialogService>;

  const mockParts: PartDefinition[] = [
    {
      id: 'part-1',
      organisationId: 'org-1',
      partCode: 'ROOT',
      description: 'Root Part',
      unitPrice: 100,
      displayOrder: 1,
      minCardinality: 1,
      maxCardinality: 1,
      childParts: [
        {
          id: 'part-2',
          organisationId: 'org-1',
          partCode: 'CHILD',
          description: 'Child Part',
          unitPrice: 50,
          displayOrder: 2,
          minCardinality: 1,
          maxCardinality: 1,
          childParts: [],
          attributes: []
        }
      ],
      attributes: []
    }
  ];

  beforeEach(async () => {
    modelService = jasmine.createSpyObj('ProductDefinitionsModelService', [
      'setSelectedPart', 'setProductParts', 'setProductPartsLoading', 'setProductPartsError'
    ], {
      productParts$: signal(mockParts),
      productPartsLoading$: signal(false),
      productPartsError$: signal(null)
    });

    controller = jasmine.createSpyObj('ProductDefinitionsController', ['loadProductParts']);
    toastService = jasmine.createSpyObj('ToastService', ['success', 'error']);
    confirmService = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);

    await TestBed.configureTestingModule({
      imports: [ProductStructureComponent],
      providers: [
        { provide: ProductDefinitionsModelService, useValue: modelService },
        { provide: ProductDefinitionsController, useValue: controller },
        { provide: ToastService, useValue: toastService },
        { provide: ConfirmDialogService, useValue: confirmService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ProductStructureComponent);
    component = fixture.componentInstance;
    component.productId = 'prod-1';
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load parts on init when productId is provided', () => {
    expect(controller.loadProductParts).toHaveBeenCalledWith('prod-1');
  });

  it('should display parts from model service', () => {
    expect(component.parts()).toEqual(mockParts);
  });

  it('should toggle part expansion', () => {
    component.toggleExpand('part-1');
    expect(component.isExpanded('part-1')).toBe(true);

    component.toggleExpand('part-1');
    expect(component.isExpanded('part-1')).toBe(false);
  });

  it('should select a part', () => {
    const part = mockParts[0];
    component.selectPart(part);
    expect(component.isSelected(part.id)).toBe(true);
    expect(modelService.setSelectedPart).toHaveBeenCalledWith(part);
  });

  it('should check if part has children', () => {
    expect(component.hasChildren(mockParts[0])).toBe(true);
    expect(component.hasChildren(mockParts[0].childParts[0])).toBe(false);
  });

  it('should check if part has attributes', () => {
    expect(component.hasAttributes(mockParts[0])).toBe(false);
  });

  it('should get correct child count', () => {
    expect(component.getChildCount(mockParts[0])).toBe(1);
    expect(component.getChildCount(mockParts[0].childParts[0])).toBe(0);
  });

  it('should format price correctly', () => {
    expect(component.formatPrice(99.99)).toBe('$99.99');
    expect(component.formatPrice(0)).toBe('$0.00');
  });

  it('should get correct indent level', () => {
    expect(component.getIndentLevel(0)).toBe('0px');
    expect(component.getIndentLevel(1)).toBe('20px');
    expect(component.getIndentLevel(2)).toBe('40px');
  });

  it('should collect all parts recursively', () => {
    const allParts = component.collectAllParts(mockParts);
    expect(allParts.length).toBe(2);
    expect(allParts[0].partCode).toBe('ROOT');
    expect(allParts[1].partCode).toBe('CHILD');
  });

  it('should refresh parts on button click', () => {
    component.onRefresh();
    expect(controller.loadProductParts).toHaveBeenCalledWith('prod-1');
  });

  it('should not delete part if confirmation is cancelled', async () => {
    confirmService.confirm.and.returnValue(Promise.resolve(false));

    await component.onDeletePart(mockParts[0], new MouseEvent('click'));

    expect(confirmService.confirm).toHaveBeenCalled();
    expect(toastService.success).not.toHaveBeenCalled();
  });

  it('should show error toast when part delete fails', async () => {
    confirmService.confirm.and.returnValue(Promise.resolve(true));
    controller.deletePart = jasmine.createSpy('deletePart').and.returnValue(Promise.reject({ status: 500 }));

    await component.onDeletePart(mockParts[0], new MouseEvent('click'));

    expect(confirmService.confirm).toHaveBeenCalled();
    expect(toastService.error).toHaveBeenCalledWith('Failed to delete part. Please try again.');
  });
});
