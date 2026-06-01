import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { TermsAndConditionsDetailComponent } from './terms-and-conditions-detail.component';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideMarkdown } from 'ngx-markdown';
import { TermsAndConditionsModelService } from '../terms-and-conditions.model.service';
import { TermsAndConditionsController } from '../terms-and-conditions.controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';

describe('TermsAndConditionsDetailComponent', () => {
  let component: TermsAndConditionsDetailComponent;
  let fixture: ComponentFixture<TermsAndConditionsDetailComponent>;
  let modelService = null as unknown as TermsAndConditionsModelService;
  let controller: jasmine.SpyObj<TermsAndConditionsController>;
  let toastService: jasmine.SpyObj<ToastService>;
  let confirmService: jasmine.SpyObj<ConfirmDialogService>;

  beforeEach(async () => {
    const controllerSpy = jasmine.createSpyObj('TermsAndConditionsController', [
      'getTermsAndConditions',
      'deleteTermsAndConditions'
    ]);
    const toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);
    const confirmSpy = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);

    await TestBed.configureTestingModule({
      imports: [TermsAndConditionsDetailComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideMarkdown(),
        TermsAndConditionsModelService,
        { provide: TermsAndConditionsController, useValue: controllerSpy },
        { provide: ToastService, useValue: toastSpy },
        { provide: ConfirmDialogService, useValue: confirmSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } }
        }
      ]
    }).compileComponents();

    modelService = TestBed.inject(TermsAndConditionsModelService);
    controller = TestBed.inject(TermsAndConditionsController) as jasmine.SpyObj<TermsAndConditionsController>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    confirmService = TestBed.inject(ConfirmDialogService) as jasmine.SpyObj<ConfirmDialogService>;

    fixture = TestBed.createComponent(TermsAndConditionsDetailComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load terms and conditions on init', () => {
    const mockTerms = {
      id: '1',
      organisationId: 'org-1',
      code: 'T001',
      title: 'Test Terms',
      contentFr: 'Test Content FR',
      contentDe: 'Test Content DE',
      contentEn: 'Test Content',
      currentVersion: '1.0',
      effectiveFrom: null,
      effectiveUntil: null
    };
    controller.getTermsAndConditions.and.returnValue(Promise.resolve(mockTerms));
    fixture.detectChanges();

    expect(controller.getTermsAndConditions).toHaveBeenCalledWith('1');
  });

  it('should display loaded terms', async () => {
    const mockTerms = {
      id: '1',
      organisationId: 'org-1',
      code: 'T001',
      title: 'Test Terms',
      contentFr: 'Test Content FR',
      contentDe: 'Test Content DE',
      contentEn: 'Test Content',
      currentVersion: '1.0',
      effectiveFrom: '2024-01-01',
      effectiveUntil: null
    };
    controller.getTermsAndConditions.and.callFake(() => {
      modelService.setSelectedTermsAndConditions(mockTerms);
      return Promise.resolve(mockTerms);
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('T001');
    expect(compiled.textContent).toContain('Test Terms');
    expect(compiled.textContent).toContain('EN');
    expect(component.selectedContent()).toBe('Test Content');
  });

  it('should show error when terms not found', async () => {
    controller.getTermsAndConditions.and.returnValue(Promise.resolve(null));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.error).toBe('Terms and conditions not found');
  });

  it('should delete after confirmation', async () => {
    const mockTerms = {
      id: '1',
      organisationId: 'org-1',
      code: 'T001',
      title: 'Test Terms',
      contentFr: 'Content FR',
      contentDe: 'Content DE',
      contentEn: 'Content',
      currentVersion: '1.0',
      effectiveFrom: null,
      effectiveUntil: null
    };
    controller.getTermsAndConditions.and.callFake(() => {
      modelService.setSelectedTermsAndConditions(mockTerms);
      return Promise.resolve(mockTerms);
    });
    fixture.detectChanges();
    await fixture.whenStable();

    confirmService.confirm.and.returnValue(Promise.resolve(true));
    controller.deleteTermsAndConditions.and.returnValue(Promise.resolve());

    await component.onDelete();

    expect(confirmService.confirm).toHaveBeenCalled();
    expect(controller.deleteTermsAndConditions).toHaveBeenCalledWith('1');
    expect(toastService.success).toHaveBeenCalled();
  });

  it('should not delete if user cancels', async () => {
    const mockTerms = {
      id: '1',
      organisationId: 'org-1',
      code: 'T001',
      title: 'Test Terms',
      contentFr: 'Content FR',
      contentDe: 'Content DE',
      contentEn: 'Content',
      currentVersion: '1.0',
      effectiveFrom: null,
      effectiveUntil: null
    };
    controller.getTermsAndConditions.and.callFake(() => {
      modelService.setSelectedTermsAndConditions(mockTerms);
      return Promise.resolve(mockTerms);
    });
    fixture.detectChanges();
    await fixture.whenStable();

    confirmService.confirm.and.returnValue(Promise.resolve(false));

    await component.onDelete();

    expect(controller.deleteTermsAndConditions).not.toHaveBeenCalled();
  });

  it('should format dates correctly', () => {
    expect(component.formatDate(null)).toBe('N/A');
    expect(component.formatDate('2024-01-15')).toBe(new Date('2024-01-15').toLocaleDateString());
  });
});
