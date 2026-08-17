import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService, ANONYMOUS, Token } from './auth.service';
import { WINDOW } from './window.token';
import { RouteTrackingService } from './route-tracking.service';
import { ToastService } from './toast/toast.service';
import { InfoDialogService, InfoDialogConfig } from './info-dialog/info-dialog.service';
import { Subject } from 'rxjs';

describe('AuthService (BFF Pattern)', () => {

  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;
  let routerEventsSubject: Subject<any>;
  let mockWindow: { location: { pathname: string; search: string; href: string } };
  let routeTrackingSpy: jasmine.SpyObj<RouteTrackingService>;
  let toastSpy: jasmine.SpyObj<ToastService>;
  let infoDialogSpy: jasmine.SpyObj<InfoDialogService>;
  
  // Helper function to set router URL
  const setRouterUrl = (url: string) => {
    Object.defineProperty(routerSpy, 'url', {
      value: url,
      writable: true,
      configurable: true
    });
  };

  const mockUserInfo: Token = {
    iss: 'https://abstrauth.abstratium.dev',
    sub: 'user-123',
    groups: ['admin', 'users'],
    email: 'test@example.com',
    email_verified: true,
    name: 'Test User',
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600,
    isAuthenticated: true,
    client_id: 'abstratium-component',
    jti: 'jwt-id-123',
    upn: 'test@example.com',
    auth_method: 'password'
  };

  beforeEach(() => {
    // Clear localStorage to ensure clean state
    localStorage.clear();
    
    // Create mock window
    mockWindow = {
      location: {
        pathname: '/accounts',
        search: '',
        href: ''
      }
    };
    
    // Create a Subject to simulate router events
    routerEventsSubject = new Subject();
    
    const spy = jasmine.createSpyObj('Router', ['navigate', 'navigateByUrl'], { url: '/' });
    spy.events = routerEventsSubject.asObservable();
    spy.navigateByUrl.and.returnValue(Promise.resolve(true));

    const routeTrackingSpyObj = jasmine.createSpyObj<RouteTrackingService>('RouteTrackingService', ['getLastRoute', 'saveRoute', 'start']);
    routeTrackingSpyObj.getLastRoute.and.returnValue(null);

    const toastSpyObj = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'info', 'warning', 'show', 'remove', 'clear']);

    const infoDialogSpyObj = jasmine.createSpyObj<InfoDialogService>('InfoDialogService', ['show', 'handleOk', 'handleActionLink']);
    infoDialogSpyObj.show.and.returnValue(new Promise<void>(() => {}));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AuthService,
        { provide: Router, useValue: spy },
        { provide: WINDOW, useValue: mockWindow },
        { provide: RouteTrackingService, useValue: routeTrackingSpyObj },
        { provide: ToastService, useValue: toastSpyObj },
        { provide: InfoDialogService, useValue: infoDialogSpyObj }
      ]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    routeTrackingSpy = TestBed.inject(RouteTrackingService) as jasmine.SpyObj<RouteTrackingService>;
    toastSpy = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    infoDialogSpy = TestBed.inject(InfoDialogService) as jasmine.SpyObj<InfoDialogService>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Initial State', () => {
    it('should start with anonymous token', () => {
      const token = service.getAccessToken();
      expect(token.email).toBe(ANONYMOUS.email);
      expect(token.isAuthenticated).toBe(false);
    });

    it('should have token$ signal set to anonymous', () => {
      const token = service.token$();
      expect(token.email).toBe(ANONYMOUS.email);
      expect(token.isAuthenticated).toBe(false);
    });

    it('should not be authenticated initially', () => {
      expect(service.isAuthenticated()).toBe(false);
    });

  });

  describe('BFF Pattern - Initialize from Backend', () => {
    it('should load user info from /api/core/userinfo when authenticated', (done) => {
      setRouterUrl('/accounts');
      service.initialize().subscribe(() => {
        const token = service.getAccessToken();
        expect(token.sub).toBe('user-123');
        expect(token.email).toBe('test@example.com');
        expect(token.name).toBe('Test User');
        expect(token.isAuthenticated).toBe(true);
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      expect(req.request.method).toBe('GET');
      req.flush(mockUserInfo);
    });

    it('should set anonymous token when /api/core/userinfo returns 401', (done) => {
      service.initialize().subscribe(() => {
        const token = service.getAccessToken();
        expect(token.email).toBe(ANONYMOUS.email);
        expect(token.isAuthenticated).toBe(false);
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should update token$ signal when loading user info', (done) => {
      setRouterUrl('/accounts');
      service.initialize().subscribe(() => {
        const token = service.token$();
        expect(token.sub).toBe('user-123');
        expect(token.email).toBe('test@example.com');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should not make duplicate requests if already initialized', (done) => {
      setRouterUrl('/accounts');
      // First initialization
      service.initialize().subscribe(() => {
        // Second initialization should not make HTTP request
        service.initialize().subscribe(() => {
          done();
        });
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
      
      // Verify no additional requests
      httpMock.expectNone('/api/core/userinfo');
    });

    it('should initialize without errors', (done) => {
      setRouterUrl('/accounts');
      
      service.initialize().subscribe(() => {
        expect(service.isAuthenticated()).toBe(true);
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should initialize from root route', (done) => {
      setRouterUrl('/');
      
      service.initialize().subscribe(() => {
        expect(service.isAuthenticated()).toBe(true);
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should handle initialization on specific route', (done) => {
      setRouterUrl('/clients');
      
      service.initialize().subscribe(() => {
        expect(service.isAuthenticated()).toBe(true);
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

  });

  describe('Empty roles warning', () => {
    it('should show a non-dismissable warning dialog when authenticated user has no roles', (done) => {
      setRouterUrl('/accounts');
      const noRolesToken: Token = { ...mockUserInfo, groups: [] };

      service.initialize().subscribe(() => {
        expect(infoDialogSpy.show).toHaveBeenCalledTimes(1);
        const config = infoDialogSpy.show.calls.mostRecent().args[0] as InfoDialogConfig;
        expect(config.variant).toBe('warning');
        expect(config.dismissable).toBe(false);
        expect(config.title).toBeTruthy();
        expect(config.message).toBeTruthy();
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(noRolesToken);
    });

    it('should provide a "Sign out" action link that calls signOut', (done) => {
      setRouterUrl('/accounts');
      const noRolesToken: Token = { ...mockUserInfo, groups: [] };

      service.initialize().subscribe(() => {
        const config = infoDialogSpy.show.calls.mostRecent().args[0] as InfoDialogConfig;
        expect(config.actionLink).toBeDefined();
        expect(config.actionLink?.text).toBe('Sign out');

        // Invoking the action link should trigger signOut (which makes a
        // GET /api/auth/logout request).
        config.actionLink!.action();
        const logoutReq = httpMock.expectOne('/api/auth/logout');
        logoutReq.flush({});
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(noRolesToken);
    });

    it('should NOT show a warning dialog when authenticated user has roles', (done) => {
      setRouterUrl('/accounts');

      service.initialize().subscribe(() => {
        expect(infoDialogSpy.show).not.toHaveBeenCalled();
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo); // groups: ['admin', 'users']
    });

    it('should NOT show a warning dialog when user is not authenticated', (done) => {
      service.initialize().subscribe(() => {
        expect(infoDialogSpy.show).not.toHaveBeenCalled();
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should still navigate when user has no roles', (done) => {
      mockWindow.location.pathname = '/accounts';
      mockWindow.location.search = '';
      setRouterUrl('/accounts');
      const noRolesToken: Token = { ...mockUserInfo, groups: [] };

      service.initialize().subscribe(() => {
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/accounts');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(noRolesToken);
    });
  });

  describe('Route Restoration', () => {
    it('should navigate to /signed-in when authenticated and on /signed-in (post-login)', (done) => {
      mockWindow.location.pathname = '/signed-in';
      mockWindow.location.search = '';

      service.initialize().subscribe(() => {
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/signed-in');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should navigate to / when authenticated and on root path (explicit navigation)', (done) => {
      mockWindow.location.pathname = '/';
      mockWindow.location.search = '';

      service.initialize().subscribe(() => {
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should navigate to /TODO when authenticated and on /TODO path', (done) => {
      mockWindow.location.pathname = '/TODO';
      mockWindow.location.search = '';

      service.initialize().subscribe(() => {
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/TODO');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should not call saveRoute during authenticated navigation', (done) => {
      mockWindow.location.pathname = '/signed-in';
      mockWindow.location.search = '';

      service.initialize().subscribe(() => {
        expect(routeTrackingSpy.saveRoute).not.toHaveBeenCalled();
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should prefer _spa path over lastRoute when authenticated', (done) => {
      mockWindow.location.pathname = '/';
      mockWindow.location.search = '?_spa=%2FTODO';
      routeTrackingSpy.getLastRoute.and.returnValue('/demo');

      service.initialize().subscribe(() => {
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/TODO');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should decode _spa query parameter to recover the originally requested path', (done) => {
      mockWindow.location.pathname = '/';
      mockWindow.location.search = '?_spa=%2FTODO';
      routeTrackingSpy.getLastRoute.and.returnValue(null);

      service.initialize().subscribe(() => {
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/TODO');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should decode _spa with query string from SpaRoutingNotFoundMapper', (done) => {
      mockWindow.location.pathname = '/';
      mockWindow.location.search = '?_spa=%2Fdemo%3Fpage%3D2';
      routeTrackingSpy.getLastRoute.and.returnValue(null);

      service.initialize().subscribe(() => {
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/demo?page=2');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should save _spa-decoded path to RouteTrackingService when not authenticated', (done) => {
      mockWindow.location.pathname = '/';
      mockWindow.location.search = '?_spa=%2FTODO';

      service.initialize().subscribe(() => {
        expect(routeTrackingSpy.saveRoute).toHaveBeenCalledWith('/TODO');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should save window.location to RouteTrackingService when not authenticated and on a deep path', (done) => {
      mockWindow.location.pathname = '/TODO';
      mockWindow.location.search = '';

      service.initialize().subscribe(() => {
        expect(routeTrackingSpy.saveRoute).toHaveBeenCalledWith('/TODO');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should not save window.location when not authenticated and on root path', (done) => {
      mockWindow.location.pathname = '/';
      mockWindow.location.search = '';

      service.initialize().subscribe(() => {
        expect(routeTrackingSpy.saveRoute).not.toHaveBeenCalled();
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should navigate to _spa path when unauthenticated so authGuard can redirect to /signed-out', (done) => {
      mockWindow.location.pathname = '/';
      mockWindow.location.search = '?_spa=%2FTODO';

      service.initialize().subscribe(() => {
        expect(routerSpy.navigateByUrl).toHaveBeenCalledWith('/TODO');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should not navigate when unauthenticated and no _spa redirect', (done) => {
      mockWindow.location.pathname = '/';
      mockWindow.location.search = '';

      service.initialize().subscribe(() => {
        expect(routerSpy.navigateByUrl).not.toHaveBeenCalled();
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should include query string when saving route for unauthenticated user', (done) => {
      mockWindow.location.pathname = '/demo';
      mockWindow.location.search = '?page=2';

      service.initialize().subscribe(() => {
        expect(routeTrackingSpy.saveRoute).toHaveBeenCalledWith('/demo?page=2');
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('Token Properties', () => {
    beforeEach((done) => {
      setRouterUrl('/accounts');
      service.initialize().subscribe(() => done());
      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });

    it('should return email', () => {
      expect(service.getEmail()).toBe('test@example.com');
    });

    it('should return name', () => {
      expect(service.getName()).toBe('Test User');
    });

    it('should return groups', () => {
      const groups = service.getGroups();
      expect(groups).toEqual(['admin', 'users']);
    });

    it('should check if user is authenticated', () => {
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should check if user has role', () => {
      expect(service.hasRole('admin')).toBe(true);
      expect(service.hasRole('users')).toBe(true);
      expect(service.hasRole('superadmin')).toBe(false);
    });
  });

  describe('Token Expiry', () => {
    it('should detect expired token', (done) => {
      setRouterUrl('/accounts');
      const expiredToken = { ...mockUserInfo, exp: Math.floor(Date.now() / 1000) - 3600 };
      
      service.initialize().subscribe(() => {
        expect(service.isExpired()).toBe(true);
        
        // The expiry timer will trigger signout() immediately for expired tokens
        // We need to flush the logout request to avoid "Expected no open requests" error
        setTimeout(() => {
          const logoutReq = httpMock.match('/api/auth/logout');
          if (logoutReq.length > 0) {
            logoutReq[0].flush({});
          }
          done();
        }, 100);
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(expiredToken);
    });

    it('should detect token about to expire', (done) => {
      setRouterUrl('/accounts');
      const soonToExpireToken = { ...mockUserInfo, exp: Math.floor(Date.now() / 1000) + 1800 }; // 30 min
      
      service.initialize().subscribe(() => {
        expect(service.isAboutToExpire()).toBe(true);
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(soonToExpireToken);
    });

    it('should not be expired for valid token', (done) => {
      setRouterUrl('/accounts');
      service.initialize().subscribe(() => {
        expect(service.isExpired()).toBe(false);
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });
  });

  describe('Reset Token', () => {
    it('should reset to anonymous token', (done) => {
      setRouterUrl('/accounts');
      service.initialize().subscribe(() => {
        expect(service.isAuthenticated()).toBe(true);
        
        service.resetToken();
        
        expect(service.isAuthenticated()).toBe(false);
        expect(service.getEmail()).toBe(ANONYMOUS.email);
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(mockUserInfo);
    });
  });

  describe('Signout', () => {
    it('should reset token, call logout endpoint, and navigate to signed-out on success', () => {
      service.signOut();
      
      // Verify token was reset
      expect(service.isAuthenticated()).toBe(false);
      expect(service.getEmail()).toBe(ANONYMOUS.email);
      
      // Verify HTTP call to logout endpoint
      const req = httpMock.expectOne('/api/auth/logout');
      expect(req.request.method).toBe('GET');
      
      // Simulate successful response
      req.flush({});
      
      // Verify navigation to signed-out
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/signed-out']);
    });

    it('should navigate to signed-out even if logout endpoint fails', () => {
      service.signOut();

      // Verify token was reset
      expect(service.isAuthenticated()).toBe(false);

      // Verify HTTP call to logout endpoint
      const req = httpMock.expectOne('/api/auth/logout');

      // Simulate error response
      req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });

      // Verify navigation to signed-out still happens
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/signed-out']);
    });
  });

  describe('Session expiry warning', () => {
    beforeEach(() => {
      jasmine.clock().install();
      // Freeze Date at epoch so exp/iat truncation to whole seconds has no
      // sub-second remainder — keeps timer delays deterministic in tests.
      jasmine.clock().mockDate(new Date(0));
    });

    afterEach(() => {
      jasmine.clock().uninstall();
    });

    it('should show a warning toast 2 minutes before signOut() fires', (done) => {
      setRouterUrl('/accounts');
      // Token expires in 10 minutes. signOut() fires at exp - 1min = 9min,
      // so the warning toast should fire at exp - 3min = 7min.
      const longLivedToken: Token = {
        ...mockUserInfo,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 10 * 60,
      };

      service.initialize().subscribe(() => {
        expect(toastSpy.warning).not.toHaveBeenCalled();

        // Advance to just before the warning fires.
        jasmine.clock().tick((7 * 60 * 1000) - 1);
        expect(toastSpy.warning).not.toHaveBeenCalled();

        // Cross the warning threshold.
        jasmine.clock().tick(1);
        expect(toastSpy.warning).toHaveBeenCalledTimes(1);
        expect(toastSpy.warning).toHaveBeenCalledWith('You will be signed out in 2 minutes.');

        // Note: signOut() fires at exp - 1min = 9min, which is beyond the
        // 7min tick above, so no logout request is made here.
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(longLivedToken);
    });

    it('should not show a warning toast for a short-lived token', (done) => {
      setRouterUrl('/accounts');
      // Token expires in 2 minutes — well within the 3-minute warning window,
      // so no warning toast should be scheduled.
      const shortLivedToken: Token = {
        ...mockUserInfo,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 2 * 60,
      };

      service.initialize().subscribe(() => {
        // Advance well past the entire session lifetime.
        jasmine.clock().tick(10 * 60 * 1000);
        expect(toastSpy.warning).not.toHaveBeenCalled();

        // Flush the logout request triggered by the immediate signOut() timer.
        const logoutReq = httpMock.expectOne('/api/auth/logout');
        logoutReq.flush({});
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(shortLivedToken);
    });

    it('should not show a warning toast if the user signs out manually before it fires', (done) => {
      setRouterUrl('/accounts');
      const longLivedToken: Token = {
        ...mockUserInfo,
        iat: Math.floor(Date.now() / 1000),
        exp: Math.floor(Date.now() / 1000) + 10 * 60,
      };

      service.initialize().subscribe(() => {
        // User signs out manually well before the 7-minute warning threshold.
        service.signOut();
        const logoutReq = httpMock.expectOne('/api/auth/logout');
        logoutReq.flush({});

        // Advance past the point where the warning would have fired.
        jasmine.clock().tick(8 * 60 * 1000);
        expect(toastSpy.warning).not.toHaveBeenCalled();
        done();
      });

      const req = httpMock.expectOne('/api/core/userinfo');
      req.flush(longLivedToken);
    });
  });

});
