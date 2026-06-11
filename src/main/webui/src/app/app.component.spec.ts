import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AppComponent } from './app.component';
import { WINDOW } from './core/window.token';
import { DomainService } from './core/domain.service';

async function buildFixture(isAbstratiumDomain: boolean) {
  TestBed.resetTestingModule();
  await TestBed.configureTestingModule({
    imports: [AppComponent],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: WINDOW, useValue: window },
      { provide: DomainService, useValue: { isAbstratiumDomain } },
    ]
  }).compileComponents();
  const fixture = TestBed.createComponent(AppComponent);
  fixture.detectChanges();
  return fixture;
}

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: WINDOW, useValue: window },
        { provide: DomainService, useValue: { isAbstratiumDomain: true } },
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'TODO' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('TODO');
  });

  it('should render router outlet', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  describe('host-disclaimer', () => {
    it('should not show disclaimer on abstratium domain', async () => {
      const fixture = await buildFixture(true);
      expect(fixture.nativeElement.querySelector('.host-disclaimer')).toBeNull();
    });

    it('should show disclaimer on non-abstratium domain', async () => {
      const fixture = await buildFixture(false);
      expect(fixture.nativeElement.querySelector('.host-disclaimer')).toBeTruthy();
    });

    it('should contain link to legal page in disclaimer', async () => {
      const fixture = await buildFixture(false);
      const link = fixture.nativeElement.querySelector('.host-disclaimer a');
      expect(link).toBeTruthy();
      const routerLink = link.getAttribute('ng-reflect-router-link') ?? link.getAttribute('href') ?? link.getAttribute('routerlink');
      expect(routerLink).toContain('legal');
    });
  });
});
