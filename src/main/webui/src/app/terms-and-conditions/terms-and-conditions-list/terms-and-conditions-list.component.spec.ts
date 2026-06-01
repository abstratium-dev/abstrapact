import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TermsAndConditionsListComponent } from './terms-and-conditions-list.component';
import { provideRouter } from '@angular/router';
import { TermsAndConditionsModelService } from '../terms-and-conditions.model.service';
import { TermsAndConditionsController } from '../terms-and-conditions.controller';
import { ToastService } from '../../core/toast/toast.service';
import { ConfirmDialogService } from '../../core/confirm-dialog/confirm-dialog.service';
import { of } from 'rxjs';

describe('TermsAndConditionsListComponent', () => {
  let component: TermsAndConditionsListComponent;
  let fixture: ComponentFixture<TermsAndConditionsListComponent>;
  let modelService: TermsAndConditionsModelService;
  let controller: jasmine.SpyObj<TermsAndConditionsController>;
  let toastService: jasmine.SpyObj<ToastService>;
  let confirmService: jasmine.SpyObj<ConfirmDialogService>;

  beforeEach(async () => {
    const controllerSpy = jasmine.createSpyObj('TermsAndConditionsController', [
      'loadTermsAndConditions',
      'deleteTermsAndConditions'
    ]);
    const toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);
    const confirmSpy = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);

    await TestBed.configureTestingModule({
      imports: [TermsAndConditionsListComponent],
      providers: [
        provideRouter([]),
        { provide: TermsAndConditionsController, useValue: controllerSpy },
        TermsAndConditionsModelService,
        { provide: ToastService, useValue: toastSpy },
        { provide: ConfirmDialogService, useValue: confirmSpy }
      ]
    }).compileComponents();

    modelService = TestBed.inject(TermsAndConditionsModelService);
    controller = TestBed.inject(TermsAndConditionsController) as jasmine.SpyObj<TermsAndConditionsController>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    confirmService = TestBed.inject(ConfirmDialogService) as jasmine.SpyObj<ConfirmDialogService>;

    fixture = TestBed.createComponent(TermsAndConditionsListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load terms and conditions on init', () => {
    fixture.detectChanges();
    expect(controller.loadTermsAndConditions).toHaveBeenCalled();
  });

  it('should retry loading when onRetry is called', () => {
    component.onRetry();
    expect(controller.loadTermsAndConditions).toHaveBeenCalled();
  });

  it('should display terms from model service', () => {
    const mockTerms = [
      { id: '1', organisationId: 'org-1', code: 'T001', title: 'Test Terms', contentFr: 'Content FR', contentDe: 'Content DE', contentEn: 'Content', currentVersion: '1.0', effectiveFrom: '2024-01-01', effectiveUntil: null }
    ];
    modelService.setTermsAndConditions(mockTerms);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('T001');
    expect(compiled.textContent).toContain('Test Terms');
  });

  it('should show empty state when no terms exist', () => {
    modelService.setTermsAndConditions([]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No terms and conditions found');
  });

  it('should show loading state', () => {
    modelService.setTermsAndConditionsLoading(true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Loading terms and conditions');
  });

  it('should show error state', () => {
    modelService.setTermsAndConditionsError('Failed to load');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Failed to load');
  });

  it('should delete terms after confirmation', async () => {
    confirmService.confirm.and.returnValue(Promise.resolve(true));
    controller.deleteTermsAndConditions.and.returnValue(Promise.resolve());

    const terms = { id: '1', organisationId: 'org-1', code: 'T001', title: 'Test Terms', contentFr: 'Content FR', contentDe: 'Content DE', contentEn: 'Content', currentVersion: '1.0', effectiveFrom: null, effectiveUntil: null };
    await component.onDelete(terms, new Event('click'));

    expect(confirmService.confirm).toHaveBeenCalled();
    expect(controller.deleteTermsAndConditions).toHaveBeenCalledWith('1');
    expect(toastService.success).toHaveBeenCalledWith('Terms and conditions deleted successfully');
  });

  it('should not delete if user cancels confirmation', async () => {
    confirmService.confirm.and.returnValue(Promise.resolve(false));

    const terms = { id: '1', organisationId: 'org-1', code: 'T001', title: 'Test Terms', contentFr: 'Content FR', contentDe: 'Content DE', contentEn: 'Content', currentVersion: '1.0', effectiveFrom: null, effectiveUntil: null };
    await component.onDelete(terms, new Event('click'));

    expect(controller.deleteTermsAndConditions).not.toHaveBeenCalled();
  });

  it('should show error toast when delete fails', async () => {
    confirmService.confirm.and.returnValue(Promise.resolve(true));
    controller.deleteTermsAndConditions.and.returnValue(Promise.reject(new Error('fail')));

    const terms = { id: '1', organisationId: 'org-1', code: 'T001', title: 'Test Terms', contentFr: 'Content FR', contentDe: 'Content DE', contentEn: 'Content', currentVersion: '1.0', effectiveFrom: null, effectiveUntil: null };
    await component.onDelete(terms, new Event('click'));

    expect(toastService.error).toHaveBeenCalledWith('Failed to delete terms and conditions. Please try again.');
  });

  it('should format dates correctly', () => {
    expect(component.formatDate(null)).toBe('N/A');
    expect(component.formatDate('2024-01-15')).toBe(new Date('2024-01-15').toLocaleDateString());
  });
});
