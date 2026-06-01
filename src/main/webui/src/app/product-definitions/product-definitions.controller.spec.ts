import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProductDefinitionsController } from './product-definitions.controller';
import { ProductDefinitionsModelService, ProductDefinition, ProductDefinitionRequest, PartDefinition, PartAttributeDefinition } from './product-definitions.model.service';

describe('ProductDefinitionsController', () => {
  let controller: ProductDefinitionsController;
  let modelService: ProductDefinitionsModelService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    controller = TestBed.inject(ProductDefinitionsController);
    modelService = TestBed.inject(ProductDefinitionsModelService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(controller).toBeTruthy();
  });

  describe('loadProductDefinitions', () => {
    it('should load product definitions and update model service', () => {
      const mockDefinitions: ProductDefinition[] = [
        { id: '1', organisationId: 'org-1', productCode: 'PROD-001', description: 'Test', billingModel: 'FIXED_PRICE', productValidFrom: null, productValidUntil: null }
      ];

      controller.loadProductDefinitions();

      const req = httpMock.expectOne('/api/product-definitions');
      expect(req.request.method).toBe('GET');
      req.flush(mockDefinitions);

      expect(modelService.productDefinitions$()).toEqual(mockDefinitions);
      expect(modelService.productDefinitionsLoading$()).toBe(false);
      expect(modelService.productDefinitionsError$()).toBeNull();
    });

    it('should handle error response', () => {
      controller.loadProductDefinitions();

      const req = httpMock.expectOne('/api/product-definitions');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      expect(modelService.productDefinitions$()).toEqual([]);
      expect(modelService.productDefinitionsLoading$()).toBe(false);
      expect(modelService.productDefinitionsError$()).toBe('Failed to load product definitions');
    });
  });

  describe('getProductDefinition', () => {
    it('should get a product definition and update model', async () => {
      const mockDefinition: ProductDefinition = { id: '1', organisationId: 'org-1', productCode: 'PROD-001', description: 'Test', billingModel: 'FIXED_PRICE', productValidFrom: null, productValidUntil: null };

      const promise = controller.getProductDefinition('1');
      const req = httpMock.expectOne('/api/product-definitions/1');
      req.flush(mockDefinition);

      const result = await promise;
      expect(result).toEqual(mockDefinition);
      expect(modelService.selectedProductDefinition$()).toEqual(mockDefinition);
    });

    it('should return null on error', async () => {
      const promise = controller.getProductDefinition('999');
      const req = httpMock.expectOne('/api/product-definitions/999');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      const result = await promise;
      expect(result).toBeNull();
      expect(modelService.selectedProductDefinition$()).toBeNull();
    });
  });

  describe('createProductDefinition', () => {
    it('should create and reload list', async () => {
      const request: ProductDefinitionRequest = { productCode: 'NEW', description: 'New', billingModel: 'FIXED_PRICE', productValidFrom: null, productValidUntil: null };
      const response = { id: '2', organisationId: 'org-1', ...request };

      const promise = controller.createProductDefinition(request);
      const req = httpMock.expectOne('/api/product-definitions');
      expect(req.request.method).toBe('POST');
      req.flush(response);

      await promise;

      const reloadReq = httpMock.expectOne('/api/product-definitions');
      reloadReq.flush([response]);

      expect(modelService.productDefinitions$()).toEqual([response]);
    });

    it('should throw on error', async () => {
      const request: ProductDefinitionRequest = { productCode: 'NEW', description: 'New', billingModel: 'FIXED_PRICE', productValidFrom: null, productValidUntil: null };

      const promise = controller.createProductDefinition(request);
      const req = httpMock.expectOne('/api/product-definitions');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      await expectAsync(promise).toBeRejected();
    });
  });

  describe('updateProductDefinition', () => {
    it('should update and reload list', async () => {
      const request: ProductDefinitionRequest = { productCode: 'UPDATED', description: 'Updated', billingModel: 'SUBSCRIPTION', productValidFrom: null, productValidUntil: null };
      const response = { id: '1', organisationId: 'org-1', ...request };

      const promise = controller.updateProductDefinition('1', request);
      const req = httpMock.expectOne('/api/product-definitions/1');
      expect(req.request.method).toBe('PUT');
      req.flush(response);

      await promise;

      const reloadReq = httpMock.expectOne('/api/product-definitions');
      reloadReq.flush([response]);

      expect(modelService.productDefinitions$()).toEqual([response]);
    });

    it('should throw on error', async () => {
      const request: ProductDefinitionRequest = { productCode: 'UPDATED', description: 'Updated', billingModel: 'SUBSCRIPTION', productValidFrom: null, productValidUntil: null };

      const promise = controller.updateProductDefinition('1', request);
      const req = httpMock.expectOne('/api/product-definitions/1');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      await expectAsync(promise).toBeRejected();
    });
  });

  describe('deleteProductDefinition', () => {
    it('should delete and reload list', async () => {
      const promise = controller.deleteProductDefinition('1');
      const req = httpMock.expectOne('/api/product-definitions/1');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);

      await promise;

      const reloadReq = httpMock.expectOne('/api/product-definitions');
      reloadReq.flush([]);

      expect(modelService.productDefinitions$()).toEqual([]);
    });

    it('should throw on error', async () => {
      const promise = controller.deleteProductDefinition('1');
      const req = httpMock.expectOne('/api/product-definitions/1');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      await expectAsync(promise).toBeRejected();
    });
  });

  describe('loadProductParts', () => {
    it('should load parts and update model service', () => {
      const mockParts: PartDefinition[] = [
        { id: 'p1', organisationId: 'org-1', partCode: 'PART-1', description: 'Part 1', unitPrice: 10, displayOrder: 1, minCardinality: 1, maxCardinality: 1, childParts: [], attributes: [] }
      ];

      controller.loadProductParts('prod-1');

      const req = httpMock.expectOne('/api/product-definitions/prod-1/parts');
      req.flush(mockParts);

      expect(modelService.productParts$()).toEqual(mockParts);
      expect(modelService.productPartsLoading$()).toBe(false);
    });
  });

  describe('loadPartAttributes', () => {
    it('should load attributes and update model service', () => {
      const mockAttributes: PartAttributeDefinition[] = [
        { id: 'a1', organisationId: 'org-1', attributeName: 'Color', dataType: 'STRING', isRequired: true, defaultValue: null, allowedValues: [] }
      ];

      controller.loadPartAttributes('part-1');

      const req = httpMock.expectOne('/api/product-definitions/parts/part-1/attributes');
      req.flush(mockAttributes);

      expect(modelService.partAttributes$()).toEqual(mockAttributes);
      expect(modelService.partAttributesLoading$()).toBe(false);
    });
  });
});
