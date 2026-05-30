import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { CookieNoticeComponent } from './cookie-notice.component';
import { WINDOW } from '../window.token';

describe('CookieNoticeComponent', () => {
  let component: CookieNoticeComponent;
  let fixture: ComponentFixture<CookieNoticeComponent>;
  let mockWindow: Partial<Window>;

  beforeEach(async () => {
    mockWindow = {
      localStorage: {
        getItem: jasmine.createSpy('getItem').and.returnValue(null),
        setItem: jasmine.createSpy('setItem'),
        removeItem: jasmine.createSpy('removeItem'),
        clear: jasmine.createSpy('clear'),
        length: 0,
        key: jasmine.createSpy('key'),
      } as Storage,
      location: {
        assign: jasmine.createSpy('assign'),
      } as unknown as Location,
    };

    await TestBed.configureTestingModule({
      imports: [CookieNoticeComponent],
      providers: [
        { provide: WINDOW, useValue: mockWindow },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: () => null
              }
            }
          }
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(CookieNoticeComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should be visible when no consent is stored', () => {
    fixture.detectChanges();
    const overlay = fixture.nativeElement.querySelector('.cookie-notice-overlay');
    expect(overlay).toBeTruthy();
  });

  it('should be hidden when consent is already stored', () => {
    (mockWindow.localStorage!.getItem as jasmine.Spy).and.returnValue('true');
    fixture = TestBed.createComponent(CookieNoticeComponent);
    fixture.detectChanges();
    const overlay = fixture.nativeElement.querySelector('.cookie-notice-overlay');
    expect(overlay).toBeFalsy();
  });

  it('should store consent and hide on accept', () => {
    fixture.detectChanges();
    component.accept();
    fixture.detectChanges();
    expect(mockWindow.localStorage!.setItem).toHaveBeenCalledWith('cookieNoticeAccepted', 'true');
    const overlay = fixture.nativeElement.querySelector('.cookie-notice-overlay');
    expect(overlay).toBeFalsy();
  });

  it('should navigate away on decline', () => {
    fixture.detectChanges();
    component.decline();
    expect(mockWindow.location!.assign).toHaveBeenCalledWith('https://www.google.com');
  });
});
