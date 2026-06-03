import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfigComponent } from './config.component';
import { ConfigController } from './config.controller';
import { ModelService } from '../model.service';
import { ToastService } from '../core/toast/toast.service';
import { provideRouter, Router } from '@angular/router';

describe('ConfigComponent', () => {
  let component: ConfigComponent;
  let fixture: ComponentFixture<ConfigComponent>;
  let controller: jasmine.SpyObj<ConfigController>;
  let toastService: jasmine.SpyObj<ToastService>;
  let router: Router;

  beforeEach(async () => {
    const controllerSpy = jasmine.createSpyObj('ConfigController', ['updateConfig']);
    const toastSpy = jasmine.createSpyObj('ToastService', ['success', 'error']);

    await TestBed.configureTestingModule({
      imports: [ConfigComponent],
      providers: [
        provideRouter([]),
        ModelService,
        { provide: ConfigController, useValue: controllerSpy },
        { provide: ToastService, useValue: toastSpy }
      ]
    }).compileComponents();

    controller = TestBed.inject(ConfigController) as jasmine.SpyObj<ConfigController>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    router = TestBed.inject(Router);

    fixture = TestBed.createComponent(ConfigComponent);
    component = fixture.componentInstance;

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialise form fields from model service defaults', () => {
    expect(component.currencyCode).toBe('CHF');
    expect(component.locale).toBe('en-US');
  });

  it('should show validation error for empty currencyCode', () => {
    component.currencyCode = '';
    component.locale = 'en-US';
    component.onSubmit();
    expect(component.fieldErrors['currencyCode']).toBe('Currency code is required');
  });

  it('should show validation error for invalid currencyCode format', () => {
    component.currencyCode = 'abc';
    component.locale = 'en-US';
    component.onSubmit();
    expect(component.fieldErrors['currencyCode']).toBe('Currency code must be a 3-letter uppercase ISO code (e.g., CHF, USD, EUR)');
  });

  it('should show validation error for empty locale', () => {
    component.currencyCode = 'USD';
    component.locale = '';
    component.onSubmit();
    expect(component.fieldErrors['locale']).toBe('Locale is required');
  });

  it('should show validation error for invalid locale format', () => {
    component.currencyCode = 'USD';
    component.locale = 'invalid';
    component.onSubmit();
    expect(component.fieldErrors['locale']).toBe('Locale must be in BCP 47 format (e.g., en-US, de-DE, fr-CH)');
  });

  it('should submit valid form and navigate on success', async () => {
    controller.updateConfig.and.returnValue(Promise.resolve({
      id: '1',
      organisationId: 'org-1',
      currencyCode: 'USD',
      locale: 'en-US'
    }));

    component.currencyCode = 'USD';
    component.locale = 'en-US';
    await component.onSubmit();

    expect(controller.updateConfig).toHaveBeenCalledWith({ currencyCode: 'USD', locale: 'en-US' });
    expect(component.submitting).toBeFalse();
    expect(router.url).toBe('/');
  });

  it('should handle error on submit', async () => {
    const error = { status: 400, error: 'Bad request' };
    controller.updateConfig.and.returnValue(Promise.reject(error));

    component.currencyCode = 'USD';
    component.locale = 'en-US';
    await component.onSubmit();

    expect(component.formError).toBe('Bad request');
    expect(component.submitting).toBeFalse();
  });

  it('should navigate on cancel', () => {
    const navSpy = spyOn(router, 'navigate');
    component.onCancel();
    expect(navSpy).toHaveBeenCalledWith(['/']);
  });
});
