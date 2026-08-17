import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { HeaderComponent } from './header.component';
import { AuthService, Token } from '../core/auth.service';

describe('HeaderComponent', () => {
  let component: HeaderComponent;
  let fixture: ComponentFixture<HeaderComponent>;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  const authenticatedToken: Token = {
    sub: 'user-123',
    email_verified: true,
    iss: 'https://abstrauth.abstratium.dev',
    groups: ['users'],
    isAuthenticated: true,
    client_id: 'abstratium-abstracore',
    upn: 'test@example.com',
    auth_method: 'password',
    name: 'Test User',
    exp: Math.floor(Date.now() / 1000) + 3600,
    iat: Math.floor(Date.now() / 1000),
    email: 'test@example.com',
    jti: 'jwt-id-123',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    })
    .compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    fixture = TestBed.createComponent(HeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not render the session-clock when not signed in', () => {
    expect(fixture.nativeElement.querySelector('.session-clock')).toBeNull();
  });

  it('should include the user email in the session-clock tooltip when signed in', () => {
    authService.token$.set(authenticatedToken);
    authService.sessionMinutesRemaining$.set(60);
    fixture.detectChanges();

    const svg = fixture.nativeElement.querySelector('.session-clock');
    expect(svg.getAttribute('aria-label')).toBe('60 minutes until sign-out (test@example.com)');
    expect(svg.querySelector('title').textContent.trim()).toBe('60 min until sign-out (test@example.com)');
  });
});
