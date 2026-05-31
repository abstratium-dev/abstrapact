import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AttributeFormComponent } from './attribute-form.component';
import { ModelService, PartAttributeDefinition, DataType } from '../../model.service';
import { Controller } from '../../controller';
import { ToastService } from '../../core/toast/toast.service';
import { signal } from '@angular/core';

describe('AttributeFormComponent', () => {
  let component: AttributeFormComponent;
  let fixture: ComponentFixture<AttributeFormComponent>;
  let modelService: jasmine.SpyObj<ModelService>;
  let controller: jasmine.SpyObj<Controller>;
  let toastService: jasmine.SpyObj<ToastService>;

  const mockExistingAttribute: PartAttributeDefinition = {
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
  };

  beforeEach(async () => {
    modelService = jasmine.createSpyObj('ModelService', ['setSelectedAttribute']);
    controller = jasmine.createSpyObj('Controller', ['createAttribute', 'updateAttribute']);
    toastService = jasmine.createSpyObj('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [AttributeFormComponent],
      providers: [
        { provide: ModelService, useValue: modelService },
        { provide: Controller, useValue: controller },
        { provide: ToastService, useValue: toastService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AttributeFormComponent);
    component = fixture.componentInstance;
    component.partId = 'part-1';
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show correct title for create mode', () => {
    component.isEditMode = false;
    expect(component.getPageTitle()).toBe('Add Attribute');
  });

  it('should show correct title for edit mode', () => {
    component.isEditMode = true;
    expect(component.getPageTitle()).toBe('Edit Attribute');
  });

  it('should populate form when in edit mode with existing attribute', () => {
    component.isEditMode = true;
    component.existingAttribute = mockExistingAttribute;
    component.ngOnInit();

    expect(component.attributeName).toBe('color');
    expect(component.dataType).toBe('STRING');
    expect(component.isRequired).toBe(true);
    expect(component.defaultValue).toBe('red');
    expect(component.allowedValues.length).toBe(2);
  });

  it('should have all data types available', () => {
    expect(component.dataTypes.length).toBe(5);
    expect(component.dataTypes.map(t => t.value)).toContain('STRING');
    expect(component.dataTypes.map(t => t.value)).toContain('INTEGER');
    expect(component.dataTypes.map(t => t.value)).toContain('DECIMAL');
    expect(component.dataTypes.map(t => t.value)).toContain('BOOLEAN');
    expect(component.dataTypes.map(t => t.value)).toContain('DATE');
  });

  it('should validate empty attribute name', () => {
    component.attributeName = '';
    expect(component.validateForm()).toBe(false);
    expect(component.fieldErrors['attributeName']).toBe('Attribute name is required');
  });

  it('should validate attribute name too long', () => {
    component.attributeName = 'a'.repeat(51);
    expect(component.validateForm()).toBe(false);
    expect(component.fieldErrors['attributeName']).toBe('Attribute name must be 50 characters or less');
  });

  it('should validate default value too long', () => {
    component.attributeName = 'color';
    component.defaultValue = 'a'.repeat(256);
    expect(component.validateForm()).toBe(false);
    expect(component.fieldErrors['defaultValue']).toBe('Default value must be 255 characters or less');
  });

  it('should pass validation with valid data', () => {
    component.attributeName = 'color';
    component.dataType = 'STRING';
    component.isRequired = false;
    component.defaultValue = null;
    expect(component.validateForm()).toBe(true);
    expect(Object.keys(component.fieldErrors).length).toBe(0);
  });

  it('should add allowed value', () => {
    component.newAllowedValue = 'green';
    component.addAllowedValue();

    expect(component.allowedValues.length).toBe(1);
    expect(component.allowedValues[0].allowedValue).toBe('green');
    expect(component.newAllowedValue).toBe('');
  });

  it('should not add duplicate allowed value', () => {
    component.allowedValues = [{ allowedValue: 'red' }];
    component.newAllowedValue = 'red';
    component.addAllowedValue();

    expect(component.allowedValues.length).toBe(1);
    expect(component.formError).toBe('This value already exists in the allowed values list');
  });

  it('should not add empty allowed value', () => {
    component.newAllowedValue = '  ';
    component.addAllowedValue();

    expect(component.allowedValues.length).toBe(0);
  });

  it('should remove allowed value by index', () => {
    component.allowedValues = [
      { allowedValue: 'red' },
      { allowedValue: 'blue' }
    ];
    component.removeAllowedValue(0);

    expect(component.allowedValues.length).toBe(1);
    expect(component.allowedValues[0].allowedValue).toBe('blue');
  });

  it('should add allowed value on Enter key', () => {
    component.newAllowedValue = 'green';
    const event = new KeyboardEvent('keydown', { key: 'Enter' });
    spyOn(event, 'preventDefault');

    component.onAllowedValueKeydown(event);

    expect(event.preventDefault).toHaveBeenCalled();
    expect(component.allowedValues.length).toBe(1);
    expect(component.allowedValues[0].allowedValue).toBe('green');
  });

  it('should emit saved event on successful create', async () => {
    const savedSpy = spyOn(component.saved, 'emit');
    controller.createAttribute.and.returnValue(Promise.resolve(mockExistingAttribute));

    component.attributeName = 'color';
    component.dataType = 'STRING';
    component.isRequired = true;
    component.defaultValue = 'red';

    await component.onSubmit();

    expect(controller.createAttribute).toHaveBeenCalledWith('part-1', jasmine.any(Object));
    expect(toastService.success).toHaveBeenCalledWith('Attribute created successfully');
    expect(savedSpy).toHaveBeenCalledWith(mockExistingAttribute);
  });

  it('should emit saved event on successful update', async () => {
    component.isEditMode = true;
    component.existingAttribute = mockExistingAttribute;
    component.partId = 'part-1';
    component.ngOnInit();

    const savedSpy = spyOn(component.saved, 'emit');
    controller.updateAttribute.and.returnValue(Promise.resolve(mockExistingAttribute));

    await component.onSubmit();

    expect(controller.updateAttribute).toHaveBeenCalledWith('attr-1', jasmine.any(Object));
    expect(toastService.success).toHaveBeenCalledWith('Attribute updated successfully');
    expect(savedSpy).toHaveBeenCalledWith(mockExistingAttribute);
  });

  it('should show conflict error when attribute name exists', async () => {
    controller.createAttribute.and.returnValue(Promise.reject({ status: 409 }));

    component.attributeName = 'color';
    component.dataType = 'STRING';

    await component.onSubmit();

    expect(component.formError).toBe('An attribute with this name already exists for this part');
  });

  it('should show generic error on server failure', async () => {
    controller.createAttribute.and.returnValue(Promise.reject({ status: 500 }));

    component.attributeName = 'color';
    component.dataType = 'STRING';

    await component.onSubmit();

    expect(component.formError).toBe('Failed to save attribute. Please try again.');
  });

  it('should emit cancelled event on cancel', () => {
    const cancelledSpy = spyOn(component.cancelled, 'emit');
    component.onCancel();
    expect(cancelledSpy).toHaveBeenCalled();
  });

  it('should not submit when form is invalid', async () => {
    component.attributeName = '';
    const createAttributeSpy = controller.createAttribute;

    await component.onSubmit();

    expect(createAttributeSpy).not.toHaveBeenCalled();
  });
});
