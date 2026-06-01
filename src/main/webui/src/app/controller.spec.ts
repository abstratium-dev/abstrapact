import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Controller } from './controller';
import {
  ModelService, ProductDefinition, ProductDefinitionRequest,
  PartDefinition, PartRequest, PartAttributeDefinition, PartAttributeRequest,
  CompleteProductResponse
} from './model.service';

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

      const req = httpMock.expectOne('/api/product-definitions');
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

      const req = httpMock.expectOne('/api/product-definitions');
      req.flush([]);
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

      const req = httpMock.expectOne('/api/product-definitions/1');
      expect(req.request.method).toBe('GET');
      req.flush(mockDefinition);

      const result = await promise;
      expect(result).toEqual(mockDefinition);
      expect(modelService.selectedProductDefinition$()).toEqual(mockDefinition);
    });

    it('should return null when product not found', async () => {
      const promise = controller.getProductDefinition('999');

      const req = httpMock.expectOne('/api/product-definitions/999');
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

      const createReq = httpMock.expectOne('/api/product-definitions');
      expect(createReq.request.method).toBe('POST');
      expect(createReq.request.body).toEqual(request);
      createReq.flush(mockDefinition);

      // Await the create result first
      const result = await createPromise;
      expect(result).toEqual(mockDefinition);

      // Now expect the reload request that happens after successful create
      const loadReq = httpMock.expectOne('/api/product-definitions');
      expect(loadReq.request.method).toBe('GET');
      loadReq.flush([mockDefinition]);

      expect(modelService.productDefinitions$()).toEqual([mockDefinition]);
    });

    it('should throw error on create failure', async () => {
      const createPromise = controller.createProductDefinition(request);

      const createReq = httpMock.expectOne('/api/product-definitions');
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

      const updateReq = httpMock.expectOne('/api/product-definitions/1');
      expect(updateReq.request.method).toBe('PUT');
      expect(updateReq.request.body).toEqual(request);
      updateReq.flush(mockDefinition);

      // Await the update result first
      const result = await updatePromise;
      expect(result).toEqual(mockDefinition);

      // Now expect the reload request that happens after successful update
      const loadReq = httpMock.expectOne('/api/product-definitions');
      expect(loadReq.request.method).toBe('GET');
      loadReq.flush([mockDefinition]);
    });

    it('should throw error on update failure', async () => {
      const updatePromise = controller.updateProductDefinition('1', request);

      const updateReq = httpMock.expectOne('/api/product-definitions/1');
      updateReq.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      await expectAsync(updatePromise).toBeRejected();
    });
  });

  describe('Product Definitions - deleteProductDefinition', () => {
    it('should delete product definition and reload list', async () => {
      const deletePromise = controller.deleteProductDefinition('1');

      const deleteReq = httpMock.expectOne('/api/product-definitions/1');
      expect(deleteReq.request.method).toBe('DELETE');
      deleteReq.flush(null);

      // Await the delete result first
      await deletePromise;

      // Now expect the reload request that happens after successful delete
      const loadReq = httpMock.expectOne('/api/product-definitions');
      expect(loadReq.request.method).toBe('GET');
      loadReq.flush([]);

      expect(modelService.productDefinitions$()).toEqual([]);
    });

    it('should throw error on delete failure', async () => {
      const deletePromise = controller.deleteProductDefinition('1');

      const deleteReq = httpMock.expectOne('/api/product-definitions/1');
      deleteReq.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      await expectAsync(deletePromise).toBeRejected();
    });
  });

  describe('Parts - loadProductParts', () => {
    const mockParts: PartDefinition[] = [
      {
        id: 'part-1',
        organisationId: 'org-1',
        partCode: 'PART-001',
        description: 'Test Part',
        unitPrice: 99.99,
        displayOrder: 1,
        minCardinality: 1,
        maxCardinality: 1,
        childParts: [],
        attributes: []
      }
    ];

    it('should load product parts and update model service', () => {
      controller.loadProductParts('prod-1');

      const req = httpMock.expectOne('/api/product-definitions/prod-1/parts');
      expect(req.request.method).toBe('GET');
      req.flush(mockParts);

      expect(modelService.productParts$()).toEqual(mockParts);
      expect(modelService.productPartsLoading$()).toBe(false);
      expect(modelService.productPartsError$()).toBeNull();
    });

    it('should set loading state before parts request', () => {
      controller.loadProductParts('prod-1');

      expect(modelService.productPartsLoading$()).toBe(true);
      expect(modelService.productPartsError$()).toBeNull();

      const req = httpMock.expectOne('/api/product-definitions/prod-1/parts');
      req.flush([]);
    });

    it('should handle parts error response', () => {
      controller.loadProductParts('prod-1');

      const req = httpMock.expectOne('/api/product-definitions/prod-1/parts');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      expect(modelService.productParts$()).toEqual([]);
      expect(modelService.productPartsLoading$()).toBe(false);
      expect(modelService.productPartsError$()).toBe('Failed to load product parts');
    });
  });

  describe('Parts - getPart', () => {
    const mockPart: PartDefinition = {
      id: 'part-1',
      organisationId: 'org-1',
      partCode: 'PART-001',
      description: 'Test Part',
      unitPrice: 99.99,
      displayOrder: 1,
      minCardinality: 1,
      maxCardinality: 1,
      childParts: [],
      attributes: []
    };

    it('should get part by id', async () => {
      const promise = controller.getPart('part-1');

      const req = httpMock.expectOne('/api/product-definitions/parts/part-1');
      expect(req.request.method).toBe('GET');
      req.flush(mockPart);

      const result = await promise;
      expect(result).toEqual(mockPart);
      expect(modelService.selectedPart$()).toEqual(mockPart);
    });

    it('should return null when part not found', async () => {
      const promise = controller.getPart('999');

      const req = httpMock.expectOne('/api/product-definitions/parts/999');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      const result = await promise;
      expect(result).toBeNull();
      expect(modelService.selectedPart$()).toBeNull();
    });
  });

  describe('Parts - createPart', () => {
    const mockPart: PartDefinition = {
      id: 'part-1',
      organisationId: 'org-1',
      partCode: 'PART-001',
      description: 'Test Part',
      unitPrice: 99.99,
      displayOrder: 1,
      minCardinality: 1,
      maxCardinality: 1,
      childParts: [],
      attributes: []
    };

    const request: PartRequest = {
      partCode: 'PART-001',
      description: 'Test Part',
      unitPrice: 99.99,
      displayOrder: 1,
      minCardinality: 1,
      maxCardinality: 1,
      childParts: [],
      attributes: []
    };

    it('should create part and reload parts list', async () => {
      const createPromise = controller.createPart('prod-1', request);

      const createReq = httpMock.expectOne('/api/product-definitions/prod-1/parts');
      expect(createReq.request.method).toBe('POST');
      expect(createReq.request.body).toEqual(request);
      createReq.flush(mockPart);

      const result = await createPromise;
      expect(result).toEqual(mockPart);

      const loadReq = httpMock.expectOne('/api/product-definitions/prod-1/parts');
      loadReq.flush([mockPart]);
    });

    it('should throw error on create part failure', async () => {
      const createPromise = controller.createPart('prod-1', request);

      const createReq = httpMock.expectOne('/api/product-definitions/prod-1/parts');
      createReq.error(new ProgressEvent('error'), { status: 400, statusText: 'Bad Request' });

      await expectAsync(createPromise).toBeRejected();
    });
  });

  describe('Parts - updatePart', () => {
    const mockPart: PartDefinition = {
      id: 'part-1',
      organisationId: 'org-1',
      partCode: 'PART-001-UPDATED',
      description: 'Updated Part',
      unitPrice: 149.99,
      displayOrder: 2,
      minCardinality: 1,
      maxCardinality: 1,
      childParts: [],
      attributes: []
    };

    const request: PartRequest = {
      partCode: 'PART-001-UPDATED',
      description: 'Updated Part',
      unitPrice: 149.99,
      displayOrder: 2,
      minCardinality: 1,
      maxCardinality: 1,
      childParts: [],
      attributes: []
    };

    it('should update part', async () => {
      const updatePromise = controller.updatePart('part-1', request);

      const req = httpMock.expectOne('/api/product-definitions/parts/part-1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(request);
      req.flush(mockPart);

      const result = await updatePromise;
      expect(result).toEqual(mockPart);
    });

    it('should throw error on update part failure', async () => {
      const updatePromise = controller.updatePart('part-1', request);

      const req = httpMock.expectOne('/api/product-definitions/parts/part-1');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      await expectAsync(updatePromise).toBeRejected();
    });
  });

  describe('Parts - deletePart', () => {
    it('should delete part and reload parts list', async () => {
      const deletePromise = controller.deletePart('part-1', 'prod-1');

      const deleteReq = httpMock.expectOne('/api/product-definitions/parts/part-1');
      expect(deleteReq.request.method).toBe('DELETE');
      deleteReq.flush(null);

      await deletePromise;

      const loadReq = httpMock.expectOne('/api/product-definitions/prod-1/parts');
      loadReq.flush([]);
    });

    it('should throw error on delete part failure', async () => {
      const deletePromise = controller.deletePart('part-1', 'prod-1');

      const deleteReq = httpMock.expectOne('/api/product-definitions/parts/part-1');
      deleteReq.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      await expectAsync(deletePromise).toBeRejected();
    });
  });

  describe('Attributes - loadPartAttributes', () => {
    const mockAttributes: PartAttributeDefinition[] = [
      {
        id: 'attr-1',
        organisationId: 'org-1',
        attributeName: 'color',
        dataType: 'STRING',
        isRequired: true,
        defaultValue: 'red',
        allowedValues: []
      }
    ];

    it('should load part attributes and update model service', () => {
      controller.loadPartAttributes('part-1');

      const req = httpMock.expectOne('/api/product-definitions/parts/part-1/attributes');
      expect(req.request.method).toBe('GET');
      req.flush(mockAttributes);

      expect(modelService.partAttributes$()).toEqual(mockAttributes);
      expect(modelService.partAttributesLoading$()).toBe(false);
      expect(modelService.partAttributesError$()).toBeNull();
    });

    it('should set loading state before attributes request', () => {
      controller.loadPartAttributes('part-1');

      expect(modelService.partAttributesLoading$()).toBe(true);
      expect(modelService.partAttributesError$()).toBeNull();

      const req = httpMock.expectOne('/api/product-definitions/parts/part-1/attributes');
      req.flush([]);
    });

    it('should handle attributes error response', () => {
      controller.loadPartAttributes('part-1');

      const req = httpMock.expectOne('/api/product-definitions/parts/part-1/attributes');
      req.error(new ProgressEvent('error'), { status: 500, statusText: 'Server Error' });

      expect(modelService.partAttributes$()).toEqual([]);
      expect(modelService.partAttributesLoading$()).toBe(false);
      expect(modelService.partAttributesError$()).toBe('Failed to load part attributes');
    });
  });

  describe('Attributes - getAttribute', () => {
    const mockAttribute: PartAttributeDefinition = {
      id: 'attr-1',
      organisationId: 'org-1',
      attributeName: 'color',
      dataType: 'STRING',
      isRequired: true,
      defaultValue: 'red',
      allowedValues: []
    };

    it('should get attribute by id', async () => {
      const promise = controller.getAttribute('attr-1');

      const req = httpMock.expectOne('/api/product-definitions/attributes/attr-1');
      expect(req.request.method).toBe('GET');
      req.flush(mockAttribute);

      const result = await promise;
      expect(result).toEqual(mockAttribute);
      expect(modelService.selectedAttribute$()).toEqual(mockAttribute);
    });

    it('should return null when attribute not found', async () => {
      const promise = controller.getAttribute('999');

      const req = httpMock.expectOne('/api/product-definitions/attributes/999');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      const result = await promise;
      expect(result).toBeNull();
      expect(modelService.selectedAttribute$()).toBeNull();
    });
  });

  describe('Attributes - createAttribute', () => {
    const mockAttribute: PartAttributeDefinition = {
      id: 'attr-1',
      organisationId: 'org-1',
      attributeName: 'color',
      dataType: 'STRING',
      isRequired: true,
      defaultValue: 'red',
      allowedValues: []
    };

    const request: PartAttributeRequest = {
      attributeName: 'color',
      dataType: 'STRING',
      isRequired: true,
      defaultValue: 'red',
      allowedValues: []
    };

    it('should create attribute and reload attributes list', async () => {
      const createPromise = controller.createAttribute('part-1', request);

      const createReq = httpMock.expectOne('/api/product-definitions/parts/part-1/attributes');
      expect(createReq.request.method).toBe('POST');
      expect(createReq.request.body).toEqual(request);
      createReq.flush(mockAttribute);

      const result = await createPromise;
      expect(result).toEqual(mockAttribute);

      const loadReq = httpMock.expectOne('/api/product-definitions/parts/part-1/attributes');
      loadReq.flush([mockAttribute]);
    });

    it('should throw error on create attribute failure', async () => {
      const createPromise = controller.createAttribute('part-1', request);

      const createReq = httpMock.expectOne('/api/product-definitions/parts/part-1/attributes');
      createReq.error(new ProgressEvent('error'), { status: 400, statusText: 'Bad Request' });

      await expectAsync(createPromise).toBeRejected();
    });
  });

  describe('Attributes - updateAttribute', () => {
    const mockAttribute: PartAttributeDefinition = {
      id: 'attr-1',
      organisationId: 'org-1',
      attributeName: 'size',
      dataType: 'INTEGER',
      isRequired: false,
      defaultValue: '10',
      allowedValues: []
    };

    const request: PartAttributeRequest = {
      attributeName: 'size',
      dataType: 'INTEGER',
      isRequired: false,
      defaultValue: '10',
      allowedValues: []
    };

    it('should update attribute', async () => {
      const updatePromise = controller.updateAttribute('attr-1', request);

      const req = httpMock.expectOne('/api/product-definitions/attributes/attr-1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(request);
      req.flush(mockAttribute);

      const result = await updatePromise;
      expect(result).toEqual(mockAttribute);
    });

    it('should throw error on update attribute failure', async () => {
      const updatePromise = controller.updateAttribute('attr-1', request);

      const req = httpMock.expectOne('/api/product-definitions/attributes/attr-1');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      await expectAsync(updatePromise).toBeRejected();
    });
  });

  describe('Attributes - deleteAttribute', () => {
    it('should delete attribute and reload attributes list', async () => {
      const deletePromise = controller.deleteAttribute('attr-1', 'part-1');

      const deleteReq = httpMock.expectOne('/api/product-definitions/attributes/attr-1');
      expect(deleteReq.request.method).toBe('DELETE');
      deleteReq.flush(null);

      await deletePromise;

      const loadReq = httpMock.expectOne('/api/product-definitions/parts/part-1/attributes');
      loadReq.flush([]);
    });

    it('should throw error on delete attribute failure', async () => {
      const deletePromise = controller.deleteAttribute('attr-1', 'part-1');

      const deleteReq = httpMock.expectOne('/api/product-definitions/attributes/attr-1');
      deleteReq.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      await expectAsync(deletePromise).toBeRejected();
    });
  });

  describe('Complete Product - getCompleteProduct', () => {
    const mockCompleteProduct: CompleteProductResponse = {
      id: 'prod-1',
      productCode: 'PROD-001',
      description: 'Test Product',
      billingModel: 'FIXED_PRICE',
      productValidFrom: null,
      productValidUntil: null,
      parts: [
        {
          id: 'part-1',
          organisationId: 'org-1',
          partCode: 'PART-001',
          description: 'Test Part',
          unitPrice: 99.99,
          displayOrder: 1,
          minCardinality: 1,
          maxCardinality: 1,
          childParts: [],
          attributes: []
        }
      ]
    };

    it('should get complete product with parts tree', async () => {
      const promise = controller.getCompleteProduct('prod-1');

      const req = httpMock.expectOne('/api/product-definitions/prod-1/complete');
      expect(req.request.method).toBe('GET');
      req.flush(mockCompleteProduct);

      const result = await promise;
      expect(result).toEqual(mockCompleteProduct);
      expect(result!.parts.length).toBe(1);
      expect(result!.parts[0].partCode).toBe('PART-001');
    });

    it('should return null when complete product not found', async () => {
      const promise = controller.getCompleteProduct('999');

      const req = httpMock.expectOne('/api/product-definitions/999/complete');
      req.error(new ProgressEvent('error'), { status: 404, statusText: 'Not Found' });

      const result = await promise;
      expect(result).toBeNull();
    });
  });
});
