import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InfoDialogComponent } from './info-dialog.component';
import { InfoDialogService } from './info-dialog.service';

describe('InfoDialogComponent', () => {
  let component: InfoDialogComponent;
  let fixture: ComponentFixture<InfoDialogComponent>;
  let service: InfoDialogService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InfoDialogComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(InfoDialogComponent);
    component = fixture.componentInstance;
    service = TestBed.inject(InfoDialogService);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not display dialog initially', () => {
    const compiled = fixture.nativeElement;
    const overlay = compiled.querySelector('.dialog-overlay');
    expect(overlay).toBeFalsy();
  });

  it('should display dialog when opened', () => {
    service.show({
      title: 'Test Title',
      message: 'Test Message'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const overlay = compiled.querySelector('.dialog-overlay');
    expect(overlay).toBeTruthy();
  });

  it('should display title and message', () => {
    service.show({
      title: 'Journal Locked',
      message: 'This journal is locked.'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.textContent).toContain('Journal Locked');
    expect(compiled.textContent).toContain('This journal is locked.');
  });

  it('should call ok when ok button clicked', () => {
    spyOn(component, 'ok');

    service.show({
      title: 'Test',
      message: 'Test'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const okBtn = compiled.querySelector('.btn-secondary');
    okBtn.click();

    expect(component.ok).toHaveBeenCalled();
  });

  it('should close dialog when overlay clicked', () => {
    spyOn(component, 'ok');

    service.show({
      title: 'Test',
      message: 'Test'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const overlay = compiled.querySelector('.dialog-overlay');
    overlay.click();

    expect(component.ok).toHaveBeenCalled();
  });

  it('should NOT call ok when overlay clicked on a non-dismissable dialog', () => {
    spyOn(component, 'ok');

    service.show({
      title: 'No roles',
      message: 'Contact your administrator',
      dismissable: false,
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const overlay = compiled.querySelector('.dialog-overlay');
    overlay.click();

    expect(component.ok).not.toHaveBeenCalled();
    expect(service.state$().isOpen).toBe(true);
  });

  it('should apply dialog-warning class for warning variant', () => {
    service.show({ title: 'Test', message: 'Test', variant: 'warning' });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('.dialog-container.dialog-warning')).toBeTruthy();
  });

  it('should NOT apply dialog-warning class for info variant', () => {
    service.show({ title: 'Test', message: 'Test', variant: 'info' });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('.dialog-container.dialog-warning')).toBeFalsy();
  });

  it('should hide the OK button when dismissable is false', () => {
    service.show({ title: 'Test', message: 'Test', dismissable: false });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('.btn-secondary')).toBeFalsy();
  });

  it('should show the OK button when dismissable is true', () => {
    service.show({ title: 'Test', message: 'Test' });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('.btn-secondary')).toBeTruthy();
  });

  it('should render the action link when configured', () => {
    service.show({
      title: 'No roles',
      message: 'Contact your administrator',
      dismissable: false,
      actionLink: { text: 'Sign out', action: () => {} },
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const link = compiled.querySelector('.dialog-action-link');
    expect(link).toBeTruthy();
    expect(link.textContent).toContain('Sign out');
  });

  it('should NOT render the action link when not configured', () => {
    service.show({ title: 'Test', message: 'Test' });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    expect(compiled.querySelector('.dialog-action-link')).toBeFalsy();
  });

  it('should call handleActionLink when the action link is clicked', () => {
    spyOn(component, 'onActionLinkClick');

    service.show({
      title: 'No roles',
      message: 'Contact your administrator',
      dismissable: false,
      actionLink: { text: 'Sign out', action: () => {} },
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement;
    const link = compiled.querySelector('.dialog-action-link');
    link.click();

    expect(component.onActionLinkClick).toHaveBeenCalled();
  });
});
