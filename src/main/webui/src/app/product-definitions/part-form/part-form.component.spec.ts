import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PartFormComponent } from './part-form.component';
import { ModelService, PartDefinition } from '../../model.service';
import { Controller } from '../../controller';
import { ToastService } from '../../core/toast/toast.service';
import { signal } from '@angular/core';

describe('PartFormComponent', () => {
  let component: PartFormComponent;
  let fixture: ComponentFixture<PartFormComponent>;
  let modelService: jasmine.SpyObj<ModelService>;
  let controller: jasmine.SpyObj<Controller>;
  let toastService: jasmine.SpyObj<ToastService>;

  const mockExistingPart: PartDefinition = {
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

  beforeEach(async () => {
    modelService = jasmine.createSpyObj('ModelService', ['setSelectedPart']);
    controller = jasmine.createSpyObj('Controller', ['createPart', 'updatePart']);
    toastService = jasmine.createSpyObj('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [PartFormComponent],
      providers: [
        { provide: ModelService, useValue: modelService },
        { provide: Controller, useValue: controller },
        { provide: ToastService, useValue: toastService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PartFormComponent);
    component = fixture.componentInstance;
    component.productId = 'prod-1';
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show correct title for create mode', () => {
    component.isEditMode = false;
    expect(component.getPageTitle()).toBe('Add Part');
  });

  it('should show correct title for edit mode', () => {
    component.isEditMode = true;
    expect(component.getPageTitle()).toBe('Edit Part');
  });

  it('should show correct title for child part creation', () => {
    component.isEditMode = false;
    component.parentPartId = 'parent-1';
    expect(component.getPageTitle()).toBe('Add Child Part');
  });

  it('should populate form when in edit mode with existing part', () => {
    component.isEditMode = true;
    component.existingPart = mockExistingPart;
    component.ngOnInit();

    expect(component.partCode).toBe('PART-001');
    expect(component.description).toBe('Test Part');
    expect(component.unitPrice).toBe(99.99);
    expect(component.displayOrder).toBe(1);
  });

  it('should validate empty part code', () => {
    component.partCode = '';
    expect(component.validateForm()).toBe(false);
    expect(component.fieldErrors['partCode']).toBe('Part code is required');
  });

  it('should validate part code too long', () => {
    component.partCode = 'a'.repeat(51);
    expect(component.validateForm()).toBe(false);
    expect(component.fieldErrors['partCode']).toBe('Part code must be 50 characters or less');
  });

  it('should validate description too long', () => {
    component.partCode = 'PART-001';
    component.description = 'a'.repeat(256);
    expect(component.validateForm()).toBe(false);
    expect(component.fieldErrors['description']).toBe('Description must be 255 characters or less');
  });

  it('should validate negative unit price', () => {
    component.partCode = 'PART-001';
    component.unitPrice = -1;
    expect(component.validateForm()).toBe(false);
    expect(component.fieldErrors['unitPrice']).toBe('Unit price cannot be negative');
  });

  it('should validate negative display order', () => {
    component.partCode = 'PART-001';
    component.displayOrder = -1;
    expect(component.validateForm()).toBe(false);
    expect(component.fieldErrors['displayOrder']).toBe('Display order cannot be negative');
  });

  it('should pass validation with valid data', () => {
    component.partCode = 'PART-001';
    component.description = 'Test Part';
    component.unitPrice = 99.99;
    component.displayOrder = 1;
    expect(component.validateForm()).toBe(true);
    expect(Object.keys(component.fieldErrors).length).toBe(0);
  });

  it('should emit saved event on successful create', async () => {
    const savedSpy = spyOn(component.saved, 'emit');
    controller.createPart.and.returnValue(Promise.resolve(mockExistingPart));

    component.partCode = 'PART-001';
    component.description = 'Test Part';
    component.unitPrice = 99.99;
    component.displayOrder = 1;

    await component.onSubmit();

    expect(controller.createPart).toHaveBeenCalledWith('prod-1', jasmine.any(Object));
    expect(toastService.success).toHaveBeenCalledWith('Part created successfully');
    expect(savedSpy).toHaveBeenCalledWith(mockExistingPart);
  });

  it('should emit saved event on successful update', async () => {
    component.isEditMode = true;
    component.existingPart = mockExistingPart;
    component.productId = 'prod-1';
    component.ngOnInit();

    const savedSpy = spyOn(component.saved, 'emit');
    controller.updatePart.and.returnValue(Promise.resolve(mockExistingPart));

    await component.onSubmit();

    expect(controller.updatePart).toHaveBeenCalledWith('part-1', jasmine.any(Object));
    expect(toastService.success).toHaveBeenCalledWith('Part updated successfully');
    expect(savedSpy).toHaveBeenCalledWith(mockExistingPart);
  });

  it('should show conflict error when part code exists', async () => {
    controller.createPart.and.returnValue(Promise.reject({ status: 409 }));

    component.partCode = 'PART-001';
    component.description = 'Test Part';
    component.unitPrice = 99.99;
    component.displayOrder = 1;

    await component.onSubmit();

    expect(component.formError).toBe('A part with this code already exists');
  });

  it('should show generic error on server failure', async () => {
    controller.createPart.and.returnValue(Promise.reject({ status: 500 }));

    component.partCode = 'PART-001';
    component.description = 'Test Part';
    component.unitPrice = 99.99;
    component.displayOrder = 1;

    await component.onSubmit();

    expect(component.formError).toBe('Failed to save part. Please try again.');
  });

  it('should emit cancelled event on cancel', () => {
    const cancelledSpy = spyOn(component.cancelled, 'emit');
    component.onCancel();
    expect(cancelledSpy).toHaveBeenCalled();
  });

  it('should not submit when form is invalid', async () => {
    component.partCode = '';
    const createPartSpy = controller.createPart;

    await component.onSubmit();

    expect(createPartSpy).not.toHaveBeenCalled();
  });
});
