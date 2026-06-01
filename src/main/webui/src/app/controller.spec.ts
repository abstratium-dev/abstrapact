import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Controller } from './controller';
import { ModelService, Config } from './model.service';

describe('Controller', () => {
  let controller: Controller;
  let modelService: ModelService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    controller = TestBed.inject(Controller);
    modelService = TestBed.inject(ModelService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(controller).toBeTruthy();
  });

  describe('loadConfig', () => {
    it('should load config and update model service', async () => {
      const mockConfig: Config = {
        logLevel: 'INFO',
        warningMessage: 'Test warning',
        warningBgColor: '#fff3cd',
        brandLogoUrl: 'https://example.com/logo.png',
        brandLogoAlt: 'Logo',
        brandName: 'Example'
      };

      const promise = controller.loadConfig();

      const req = httpMock.expectOne('/public/config');
      expect(req.request.method).toBe('GET');
      req.flush(mockConfig);

      const result = await promise;
      expect(result).toEqual(mockConfig);
      expect(modelService.config$()).toEqual(mockConfig);
      expect(modelService.warningMessage$()).toBe('Test warning');
    });

    it('should handle error when loading config', async () => {
      const promise = controller.loadConfig();

      const req = httpMock.expectOne('/public/config');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      await expectAsync(promise).toBeRejected();
    });
  });
});
