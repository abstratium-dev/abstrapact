import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TermsAndConditionsController } from './terms-and-conditions.controller';
import { TermsAndConditionsModelService } from './terms-and-conditions.model.service';

describe('TermsAndConditionsController', () => {
  let controller: TermsAndConditionsController;
  let modelService: TermsAndConditionsModelService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    controller = TestBed.inject(TermsAndConditionsController);
    modelService = TestBed.inject(TermsAndConditionsModelService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(controller).toBeTruthy();
  });

  describe('loadTermsAndConditions', () => {
    it('should load terms and update model service', () => {
      const mockTerms = [
        { id: '1', organisationId: 'org-1', code: 'T001', title: 'Test', contentFr: 'Content FR', contentDe: 'Content DE', contentEn: 'Content', currentVersion: '1.0', effectiveFrom: null, effectiveUntil: null }
      ];

      controller.loadTermsAndConditions();

      const req = httpMock.expectOne('/api/terms-and-conditions');
      expect(req.request.method).toBe('GET');
      req.flush(mockTerms);

      expect(modelService.termsAndConditions$()).toEqual(mockTerms);
      expect(modelService.termsAndConditionsLoading$()).toBe(false);
      expect(modelService.termsAndConditionsError$()).toBeNull();
    });

    it('should handle error response', () => {
      controller.loadTermsAndConditions();

      const req = httpMock.expectOne('/api/terms-and-conditions');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      expect(modelService.termsAndConditions$()).toEqual([]);
      expect(modelService.termsAndConditionsLoading$()).toBe(false);
      expect(modelService.termsAndConditionsError$()).toBe('Failed to load terms and conditions');
    });
  });

  describe('getTermsAndConditions', () => {
    it('should get terms and update model', async () => {
      const mockTerms = { id: '1', organisationId: 'org-1', code: 'T001', title: 'Test', contentFr: 'Content FR', contentDe: 'Content DE', contentEn: 'Content', currentVersion: '1.0', effectiveFrom: null, effectiveUntil: null };

      const promise = controller.getTermsAndConditions('1');
      const req = httpMock.expectOne('/api/terms-and-conditions/1');
      req.flush(mockTerms);

      const result = await promise;
      expect(result).toEqual(mockTerms);
      expect(modelService.selectedTermsAndConditions$()).toEqual(mockTerms);
    });

    it('should return null on error', async () => {
      const promise = controller.getTermsAndConditions('999');
      const req = httpMock.expectOne('/api/terms-and-conditions/999');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      const result = await promise;
      expect(result).toBeNull();
      expect(modelService.selectedTermsAndConditions$()).toBeNull();
    });
  });

  describe('createTermsAndConditions', () => {
    it('should create and reload list', async () => {
      const request = { code: 'NEW', title: 'New', contentFr: 'Content FR', contentDe: 'Content DE', contentEn: 'Content', currentVersion: '1.0', effectiveFrom: null, effectiveUntil: null };
      const response = { id: '2', organisationId: 'org-1', ...request };

      const promise = controller.createTermsAndConditions(request);
      const req = httpMock.expectOne('/api/terms-and-conditions');
      expect(req.request.method).toBe('POST');
      req.flush(response);

      await promise;

      const reloadReq = httpMock.expectOne('/api/terms-and-conditions');
      reloadReq.flush([response]);

      expect(modelService.termsAndConditions$()).toEqual([response]);
    });

    it('should throw on error', async () => {
      const request = { code: 'NEW', title: 'New', contentFr: 'Content FR', contentDe: 'Content DE', contentEn: 'Content', currentVersion: '1.0', effectiveFrom: null, effectiveUntil: null };

      const promise = controller.createTermsAndConditions(request);
      const req = httpMock.expectOne('/api/terms-and-conditions');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      await expectAsync(promise).toBeRejected();
    });
  });

  describe('updateTermsAndConditions', () => {
    it('should update and reload list', async () => {
      const request = { code: 'UPDATED', title: 'Updated', contentFr: 'New Content FR', contentDe: 'New Content DE', contentEn: 'New Content', currentVersion: '2.0', effectiveFrom: null, effectiveUntil: null };
      const response = { id: '1', organisationId: 'org-1', ...request };

      const promise = controller.updateTermsAndConditions('1', request);
      const req = httpMock.expectOne('/api/terms-and-conditions/1');
      expect(req.request.method).toBe('PUT');
      req.flush(response);

      await promise;

      const reloadReq = httpMock.expectOne('/api/terms-and-conditions');
      reloadReq.flush([response]);

      expect(modelService.termsAndConditions$()).toEqual([response]);
    });

    it('should throw on error', async () => {
      const request = { code: 'UPDATED', title: 'Updated', contentFr: 'New Content FR', contentDe: 'New Content DE', contentEn: 'New Content', currentVersion: '2.0', effectiveFrom: null, effectiveUntil: null };

      const promise = controller.updateTermsAndConditions('1', request);
      const req = httpMock.expectOne('/api/terms-and-conditions/1');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      await expectAsync(promise).toBeRejected();
    });
  });

  describe('deleteTermsAndConditions', () => {
    it('should delete and reload list', async () => {
      const promise = controller.deleteTermsAndConditions('1');
      const req = httpMock.expectOne('/api/terms-and-conditions/1');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      await promise;

      const reloadReq = httpMock.expectOne('/api/terms-and-conditions');
      reloadReq.flush([]);

      expect(modelService.termsAndConditions$()).toEqual([]);
    });

    it('should throw on error', async () => {
      const promise = controller.deleteTermsAndConditions('1');
      const req = httpMock.expectOne('/api/terms-and-conditions/1');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      await expectAsync(promise).toBeRejected();
    });
  });
});
