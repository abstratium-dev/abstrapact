import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PartAttributesListComponent } from './part-attributes-list.component';
import { ProductDefinitionsModelService, PartAttributeDefinition, PartDefinition, DataType } from '../product-definitions.model.service';
import { ProductDefinitionsController } from '../product-definitions.controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';
import { signal } from '@angular/core';

describe('PartAttributesListComponent', () => {
  let component: PartAttributesListComponent;
  let fixture: ComponentFixture<PartAttributesListComponent>;
  let modelService: jasmine.SpyObj<ProductDefinitionsModelService>;
  let controller: jasmine.SpyObj<ProductDefinitionsController>;
  let toastService: jasmine.SpyObj<ToastService>;
  let confirmService: jasmine.SpyObj<ConfirmDialogService>;

  const mockPart: PartDefinition = {
    id: 'part-1',
    organisationId: 'org-1',
    partCode: 'PART-001',
    description: 'Test Part',
    unitPrice: 99.99,
    displayOrder: 1,
    minCardinality: 1,
    maxCardinality: 1,
    childParts: [],
    attributes: []
  };

  const mockAttributes: PartAttributeDefinition[] = [
    {
      id: 'attr-1',
      organisationId: 'org-1',
      attributeName: 'color',
      dataType: 'STRING' as DataType,
      isRequired: true,
      defaultValue: 'red',
      allowedValues: [
        { id: 'val-1', organisationId: 'org-1', allowedValue: 'red' },
        { id: 'val-2', organisationId: 'org-1', allowedValue: 'blue' }
      ]
    },
    {
      id: 'attr-2',
      organisationId: 'org-1',
      attributeName: 'size',
      dataType: 'INTEGER' as DataType,
      isRequired: false,
      defaultValue: '10',
      allowedValues: []
    }
  ];

  beforeEach(async () => {
    const modelServiceMock = {
      partAttributes$: signal(mockAttributes),
      partAttributesLoading$: signal(false),
      partAttributesError$: signal(null),
      selectedAttribute$: signal(null),
      setSelectedAttribute: jasmine.createSpy('setSelectedAttribute')
    };

    controller = jasmine.createSpyObj('ProductDefinitionsController', ['loadPartAttributes', 'deleteAttribute']);
    toastService = jasmine.createSpyObj('ToastService', ['success', 'error']);
    confirmService = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);

    await TestBed.configureTestingModule({
      imports: [PartAttributesListComponent],
      providers: [
        { provide: ProductDefinitionsModelService, useValue: modelServiceMock },
        { provide: ProductDefinitionsController, useValue: controller },
        { provide: ToastService, useValue: toastService },
        { provide: ConfirmDialogService, useValue: confirmService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PartAttributesListComponent);
    component = fixture.componentInstance;
    modelService = TestBed.inject(ProductDefinitionsModelService) as jasmine.SpyObj<ProductDefinitionsModelService>;
    component.part = mockPart;

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load attributes on init', () => {
    expect(controller.loadPartAttributes).toHaveBeenCalledWith('part-1');
  });

  it('should reload attributes when part changes', () => {
    const newPart: PartDefinition = { ...mockPart, id: 'part-2' };
    component.part = newPart;
    component.ngOnChanges({
      part: {
        currentValue: newPart,
        previousValue: mockPart,
        firstChange: false,
        isFirstChange: () => false
      }
    });

    expect(controller.loadPartAttributes).toHaveBeenCalledWith('part-2');
  });

  it('should select an attribute', () => {
    const attribute = mockAttributes[0];
    component.selectAttribute(attribute);
    expect(component.isSelected(attribute.id)).toBe(true);
  });

  it('should get correct data type label', () => {
    expect(component.getDataTypeLabel('STRING')).toBe('String');
    expect(component.getDataTypeLabel('INTEGER')).toBe('Integer');
    expect(component.getDataTypeLabel('DECIMAL')).toBe('Decimal');
    expect(component.getDataTypeLabel('BOOLEAN')).toBe('Boolean');
    expect(component.getDataTypeLabel('DATE')).toBe('Date');
    expect(component.getDataTypeLabel('UNKNOWN')).toBe('UNKNOWN');
  });

  it('should check if attribute has allowed values', () => {
    expect(component.hasAllowedValues(mockAttributes[0])).toBe(true);
    expect(component.hasAllowedValues(mockAttributes[1])).toBe(false);
  });

  it('should get allowed values text', () => {
    expect(component.getAllowedValuesText(mockAttributes[0])).toBe('red, blue');
    expect(component.getAllowedValuesText(mockAttributes[1])).toBe('Any value');
  });

  it('should not delete attribute if confirmation is cancelled', async () => {
    confirmService.confirm.and.returnValue(Promise.resolve(false));

    await component.onDeleteAttribute(mockAttributes[0], new MouseEvent('click'));

    expect(confirmService.confirm).toHaveBeenCalled();
    expect(controller.deleteAttribute).not.toHaveBeenCalled();
    expect(toastService.success).not.toHaveBeenCalled();
  });

  it('should not delete attribute when readOnly is true', async () => {
    component.readOnly = true;
    confirmService.confirm.and.returnValue(Promise.resolve(true));

    await component.onDeleteAttribute(mockAttributes[0], new MouseEvent('click'));

    expect(confirmService.confirm).not.toHaveBeenCalled();
    expect(controller.deleteAttribute).not.toHaveBeenCalled();
  });

  it('should show info message when no part is selected', () => {
    component.part = null;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Select a part to view its attributes');
  });
});
