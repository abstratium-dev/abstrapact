import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LegalComponent } from './legal.component';

describe('LegalComponent', () => {
  let component: LegalComponent;
  let fixture: ComponentFixture<LegalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LegalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(LegalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should compute copyrightYears', () => {
    expect(component.copyrightYears).toBeTruthy();
    const currentYear = new Date().getFullYear();
    expect(component.copyrightYears).toContain(String(currentYear));
  });

  describe('Template Rendering', () => {
    it('should display legal page heading', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const heading = compiled.querySelector('.legal-title');
      expect(heading).toBeTruthy();
      expect(heading?.textContent).toContain('Legal');
    });

    it('should render copyright notice section', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const sections = compiled.querySelectorAll('.notice-card-title');
      const titles = Array.from(sections).map(el => el.textContent);
      expect(titles).toContain('Copyright Notice');
    });

    it('should render AI transparency section', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const sections = compiled.querySelectorAll('.notice-card-title');
      const titles = Array.from(sections).map(el => el.textContent);
      expect(titles).toContain('AI Transparency & Authorship Notice');
    });

    it('should render terms of use section', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const sections = compiled.querySelectorAll('.notice-card-title');
      const titles = Array.from(sections).map(el => el.textContent);
      expect(titles).toContain('Terms of Use & Disclaimer');
    });

    it('should render privacy section', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const sections = compiled.querySelectorAll('.notice-card-title');
      const titles = Array.from(sections).map(el => el.textContent);
      expect(titles).toContain('Privacy & Data');
    });

    it('should render contact section', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const sections = compiled.querySelectorAll('.notice-card-title');
      const titles = Array.from(sections).map(el => el.textContent);
      expect(titles).toContain('Contact');
    });

    it('should render contact image', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      const img = compiled.querySelector('.contact-card-img');
      expect(img).toBeTruthy();
      expect(img?.getAttribute('src')).toBe('https://abstratium.dev/contact.png');
    });
  });
});
