import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Controller } from './controller';
import { ModelService, ProductDefinition, ProductDefinitionRequest } from './model.service';

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

  describe('Product Definitions - loadProductDefinitions', () => {
    it('should load product definitions and update model service', () => {
      const mockDefinitions: ProductDefinition[] = [
        {
          id: '1',
          organisationId: 'org-1',
          productCode: 'PROD-001',
          description: 'Test Product',
          billingModel: 'FIXED_PRICE',
          productValidFrom: null,
          productValidUntil: null
        }
      ];

      controller.loadProductDefinitions();

      const req = httpMock.expectOne('/api/v1/product-definitions');
      expect(req.request.method).toBe('GET');
      req.flush(mockDefinitions);

      expect(modelService.productDefinitions$()).toEqual(mockDefinitions);
      expect(modelService.productDefinitionsLoading$()).toBe(false);
      expect(modelService.productDefinitionsError$()).toBeNull();
    });

    it('should set loading state before request', () => {
      controller.loadProductDefinitions();

      expect(modelService.productDefinitionsLoading$()).toBe(true);
      expect(modelService.productDefinitionsError$()).toBeNull();

      const req = httpMock.expectOne('/api/v1/product-definitions');
      req.flush([]);
    });

    it('should handle error response', () => {
      controller.loadProductDefinitions();

      const req = httpMock.expectOne('/api/v1/product-definitions');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      expect(modelService.productDefinitions$()).toEqual([]);
      expect(modelService.productDefinitionsLoading$()).toBe(false);
      expect(modelService.productDefinitionsError$()).toBe('Failed to load product definitions');
    });
  });

  describe('Product Definitions - getProductDefinition', () => {
    const mockDefinition: ProductDefinition = {
      id: '1',
      organisationId: 'org-1',
      productCode: 'PROD-001',
      description: 'Test Product',
      billingModel: 'FIXED_PRICE',
      productValidFrom: '2024-01-01',
      productValidUntil: '2024-12-31'
    };

    it('should get product definition by id', async () => {
      const promise = controller.getProductDefinition('1');

      const req = httpMock.expectOne('/api/v1/product-definitions/1');
      expect(req.request.method).toBe('GET');
      req.flush(mockDefinition);

      const result = await promise;
      expect(result).toEqual(mockDefinition);
      expect(modelService.selectedProductDefinition$()).toEqual(mockDefinition);
    });

    it('should return null when product not found', async () => {
      const promise = controller.getProductDefinition('999');

      const req = httpMock.expectOne('/api/v1/product-definitions/999');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      const result = await promise;
      expect(result).toBeNull();
      expect(modelService.selectedProductDefinition$()).toBeNull();
    });
  });

  describe('Product Definitions - createProductDefinition', () => {
    const mockDefinition: ProductDefinition = {
      id: '1',
      organisationId: 'org-1',
      productCode: 'PROD-001',
      description: 'Test Product',
      billingModel: 'FIXED_PRICE',
      productValidFrom: null,
      productValidUntil: null
    };

    const request: ProductDefinitionRequest = {
      productCode: 'PROD-001',
      description: 'Test Product',
      billingModel: 'FIXED_PRICE',
      productValidFrom: null,
      productValidUntil: null
    };

    it('should create product definition and reload list', async () => {
      // Start the create operation
      const createPromise = controller.createProductDefinition(request);

      const createReq = httpMock.expectOne('/api/v1/product-definitions');
      expect(createReq.request.method).toBe('POST');
      expect(createReq.request.body).toEqual(request);
      createReq.flush(mockDefinition);

      // Await the create result first
      const result = await createPromise;
      expect(result).toEqual(mockDefinition);

      // Now expect the reload request that happens after successful create
      const loadReq = httpMock.expectOne('/api/v1/product-definitions');
      expect(loadReq.request.method).toBe('GET');
      loadReq.flush([mockDefinition]);

      expect(modelService.productDefinitions$()).toEqual([mockDefinition]);
    });

    it('should throw error on create failure', async () => {
      const createPromise = controller.createProductDefinition(request);

      const createReq = httpMock.expectOne('/api/v1/product-definitions');
      createReq.error(new ProgressEvent('error'), { status: 409, statusText: 'Conflict' });

      await expectAsync(createPromise).toBeRejected();
    });
  });

  describe('Product Definitions - updateProductDefinition', () => {
    const mockDefinition: ProductDefinition = {
      id: '1',
      organisationId: 'org-1',
      productCode: 'PROD-001-UPDATED',
      description: 'Updated Product',
      billingModel: 'SUBSCRIPTION',
      productValidFrom: '2024-01-01',
      productValidUntil: '2024-12-31'
    };

    const request: ProductDefinitionRequest = {
      productCode: 'PROD-001-UPDATED',
      description: 'Updated Product',
      billingModel: 'SUBSCRIPTION',
      productValidFrom: '2024-01-01',
      productValidUntil: '2024-12-31'
    };

    it('should update product definition and reload list', async () => {
      const updatePromise = controller.updateProductDefinition('1', request);

      const updateReq = httpMock.expectOne('/api/v1/product-definitions/1');
      expect(updateReq.request.method).toBe('PUT');
      expect(updateReq.request.body).toEqual(request);
      updateReq.flush(mockDefinition);

      // Await the update result first
      const result = await updatePromise;
      expect(result).toEqual(mockDefinition);

      // Now expect the reload request that happens after successful update
      const loadReq = httpMock.expectOne('/api/v1/product-definitions');
      expect(loadReq.request.method).toBe('GET');
      loadReq.flush([mockDefinition]);
    });

    it('should throw error on update failure', async () => {
      const updatePromise = controller.updateProductDefinition('1', request);

      const updateReq = httpMock.expectOne('/api/v1/product-definitions/1');
      updateReq.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      await expectAsync(updatePromise).toBeRejected();
    });
  });

  describe('Product Definitions - deleteProductDefinition', () => {
    it('should delete product definition and reload list', async () => {
      const deletePromise = controller.deleteProductDefinition('1');

      const deleteReq = httpMock.expectOne('/api/v1/product-definitions/1');
      expect(deleteReq.request.method).toBe('DELETE');
      deleteReq.flush(null);

      // Await the delete result first
      await deletePromise;

      // Now expect the reload request that happens after successful delete
      const loadReq = httpMock.expectOne('/api/v1/product-definitions');
      expect(loadReq.request.method).toBe('GET');
      loadReq.flush([]);

      expect(modelService.productDefinitions$()).toEqual([]);
    });

    it('should throw error on delete failure', async () => {
      const deletePromise = controller.deleteProductDefinition('1');

      const deleteReq = httpMock.expectOne('/api/v1/product-definitions/1');
      deleteReq.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      await expectAsync(deletePromise).toBeRejected();
    });
  });
});
