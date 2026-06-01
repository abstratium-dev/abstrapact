import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TermsAndConditionsFormComponent } from './terms-and-conditions-form.component';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TermsAndConditionsModelService } from '../terms-and-conditions.model.service';
import { TermsAndConditionsController } from '../terms-and-conditions.controller';
import { ToastService } from '../../core/toast/toast.service';

describe('TermsAndConditionsFormComponent', () => {
  let component: TermsAndConditionsFormComponent;
  let fixture: ComponentFixture<TermsAndConditionsFormComponent>;
  let controller: jasmine.SpyObj<TermsAndConditionsController>;
  let toastService: jasmine.SpyObj<ToastService>;

  beforeEach(async () => {
    const controllerSpy = jasmine.createSpyObj('TermsAndConditionsController', [
      'getTermsAndConditions',
      'createTermsAndConditions',
      'updateTermsAndConditions'
    ]);
    const toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [TermsAndConditionsFormComponent],
      providers: [
        provideRouter([]),
        TermsAndConditionsModelService,
        { provide: TermsAndConditionsController, useValue: controllerSpy },
        { provide: ToastService, useValue: toastSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({}) } }
        }
      ]
    }).compileComponents();

    controller = TestBed.inject(TermsAndConditionsController) as jasmine.SpyObj<TermsAndConditionsController>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;

    fixture = TestBed.createComponent(TermsAndConditionsFormComponent);
    component = fixture.componentInstance;
  });

  it('should create in create mode', () => {
    expect(component).toBeTruthy();
    expect(component.isEditMode).toBeFalse();
  });

  it('should validate required code', () => {
    component.code = '';
    component.title = 'Title';
    component.contentEn = 'Content';
    component.currentVersion = '1.0';
    expect(component.validateForm()).toBeFalse();
    expect(component.fieldErrors['code']).toBe('Code is required');
  });

  it('should validate required title', () => {
    component.code = 'CODE';
    component.title = '';
    component.contentEn = 'Content';
    component.currentVersion = '1.0';
    expect(component.validateForm()).toBeFalse();
    expect(component.fieldErrors['title']).toBe('Title is required');
  });

  it('should validate required content', () => {
    component.code = 'CODE';
    component.title = 'Title';
    component.contentEn = '';
    component.currentVersion = '1.0';
    expect(component.validateForm()).toBeFalse();
    expect(component.fieldErrors['contentEn']).toBe('English content is required');
  });

  it('should validate required version', () => {
    component.code = 'CODE';
    component.title = 'Title';
    component.contentEn = 'Content';
    component.currentVersion = '';
    expect(component.validateForm()).toBeFalse();
    expect(component.fieldErrors['currentVersion']).toBe('Version is required');
  });

  it('should validate code max length', () => {
    component.code = 'a'.repeat(51);
    component.title = 'Title';
    component.contentEn = 'Content';
    component.currentVersion = '1.0';
    expect(component.validateForm()).toBeFalse();
    expect(component.fieldErrors['code']).toBe('Code must be 50 characters or less');
  });

  it('should validate date range', () => {
    component.code = 'CODE';
    component.title = 'Title';
    component.contentEn = 'Content';
    component.currentVersion = '1.0';
    component.effectiveFrom = '2024-12-01';
    component.effectiveUntil = '2024-01-01';
    expect(component.validateForm()).toBeFalse();
    expect(component.fieldErrors['effectiveUntil']).toContain('must be after');
  });

  it('should pass validation with valid data', () => {
    component.code = 'CODE';
    component.title = 'Title';
    component.contentFr = 'Content FR';
    component.contentDe = 'Content DE';
    component.contentEn = 'Content';
    component.currentVersion = '1.0';
    expect(component.validateForm()).toBeTrue();
  });

  it('should show create page title', () => {
    expect(component.getPageTitle()).toBe('Create Terms and Conditions');
  });

  it('should show edit page title in edit mode', () => {
    component.isEditMode = true;
    expect(component.getPageTitle()).toBe('Edit Terms and Conditions');
  });

  it('should populate form from loaded terms', () => {
    const terms = {
      id: '1',
      organisationId: 'org-1',
      code: 'TEST',
      title: 'Test Title',
      contentFr: 'Test Content FR',
      contentDe: 'Test Content DE',
      contentEn: 'Test Content',
      currentVersion: '2.0',
      effectiveFrom: '2024-01-01',
      effectiveUntil: '2024-12-31'
    };
    component.populateForm(terms);
    expect(component.code).toBe('TEST');
    expect(component.title).toBe('Test Title');
    expect(component.contentEn).toBe('Test Content');
    expect(component.currentVersion).toBe('2.0');
    expect(component.effectiveFrom).toBe('2024-01-01');
    expect(component.effectiveUntil).toBe('2024-12-31');
  });
});
