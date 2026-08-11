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
});
