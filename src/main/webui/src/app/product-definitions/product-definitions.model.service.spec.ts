import { TestBed } from '@angular/core/testing';
import {
  ProductDefinitionsModelService,
  ProductDefinition,
  PartDefinition,
  PartAttributeDefinition,
  DataType
} from './product-definitions.model.service';

describe('ProductDefinitionsModelService', () => {
  let service: ProductDefinitionsModelService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ProductDefinitionsModelService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Product Definitions - Initial State', () => {
    it('should have empty product definitions initially', () => {
      expect(service.productDefinitions$()).toEqual([]);
    });

    it('should not be loading product definitions initially', () => {
      expect(service.productDefinitionsLoading$()).toBe(false);
    });

    it('should have no product definitions error initially', () => {
      expect(service.productDefinitionsError$()).toBeNull();
    });

    it('should have no selected product definition initially', () => {
      expect(service.selectedProductDefinition$()).toBeNull();
    });
  });

  describe('Product Definitions - State Management', () => {
    const mockProductDefinitions: ProductDefinition[] = [
      {
        id: '1',
        organisationId: 'org-1',
        productCode: 'PROD-001',
        description: 'Test Product 1',
        billingModel: 'FIXED_PRICE',
        productValidFrom: '2024-01-01',
        productValidUntil: '2024-12-31'
      },
      {
        id: '2',
        organisationId: 'org-1',
        productCode: 'PROD-002',
        description: 'Test Product 2',
        billingModel: 'SUBSCRIPTION',
        productValidFrom: null,
        productValidUntil: null
      }
    ];

    it('should set product definitions', () => {
      service.setProductDefinitions(mockProductDefinitions);
      expect(service.productDefinitions$()).toEqual(mockProductDefinitions);
    });

    it('should update product definitions', () => {
      service.setProductDefinitions([mockProductDefinitions[0]]);
      expect(service.productDefinitions$()).toEqual([mockProductDefinitions[0]]);
      service.setProductDefinitions([mockProductDefinitions[1]]);
      expect(service.productDefinitions$()).toEqual([mockProductDefinitions[1]]);
    });

    it('should handle empty product definitions list', () => {
      service.setProductDefinitions(mockProductDefinitions);
      service.setProductDefinitions([]);
      expect(service.productDefinitions$()).toEqual([]);
    });

    it('should set loading state', () => {
      service.setProductDefinitionsLoading(true);
      expect(service.productDefinitionsLoading$()).toBe(true);
      service.setProductDefinitionsLoading(false);
      expect(service.productDefinitionsLoading$()).toBe(false);
    });

    it('should set error state', () => {
      service.setProductDefinitionsError('Failed to load');
      expect(service.productDefinitionsError$()).toBe('Failed to load');
      service.setProductDefinitionsError(null);
      expect(service.productDefinitionsError$()).toBeNull();
    });

    it('should set selected product definition', () => {
      service.setSelectedProductDefinition(mockProductDefinitions[0]);
      expect(service.selectedProductDefinition$()).toEqual(mockProductDefinitions[0]);
      service.setSelectedProductDefinition(null);
      expect(service.selectedProductDefinition$()).toBeNull();
    });
  });

  describe('Parts - State Management', () => {
    const mockPart: PartDefinition = {
      id: '1',
      organisationId: 'org-1',
      partCode: 'PART-001',
      description: 'Test Part',
      unitPrice: 100,
      displayOrder: 1,
      minCardinality: 1,
      maxCardinality: 1,
      childParts: [],
      attributes: []
    };

    it('should have empty parts initially', () => {
      expect(service.productParts$()).toEqual([]);
    });

    it('should set product parts', () => {
      service.setProductParts([mockPart]);
      expect(service.productParts$()).toEqual([mockPart]);
    });

    it('should set parts loading state', () => {
      service.setProductPartsLoading(true);
      expect(service.productPartsLoading$()).toBe(true);
      service.setProductPartsLoading(false);
      expect(service.productPartsLoading$()).toBe(false);
    });

    it('should set parts error state', () => {
      service.setProductPartsError('Failed to load parts');
      expect(service.productPartsError$()).toBe('Failed to load parts');
    });

    it('should set selected part', () => {
      service.setSelectedPart(mockPart);
      expect(service.selectedPart$()).toEqual(mockPart);
      service.setSelectedPart(null);
      expect(service.selectedPart$()).toBeNull();
    });
  });

  describe('Attributes - State Management', () => {
    const mockAttribute: PartAttributeDefinition = {
      id: '1',
      organisationId: 'org-1',
      attributeName: 'Color',
      dataType: 'STRING' as DataType,
      isRequired: true,
      defaultValue: null,
      allowedValues: []
    };

    it('should have empty attributes initially', () => {
      expect(service.partAttributes$()).toEqual([]);
    });

    it('should set part attributes', () => {
      service.setPartAttributes([mockAttribute]);
      expect(service.partAttributes$()).toEqual([mockAttribute]);
    });

    it('should set attributes loading state', () => {
      service.setPartAttributesLoading(true);
      expect(service.partAttributesLoading$()).toBe(true);
      service.setPartAttributesLoading(false);
      expect(service.partAttributesLoading$()).toBe(false);
    });

    it('should set attributes error state', () => {
      service.setPartAttributesError('Failed to load attributes');
      expect(service.partAttributesError$()).toBe('Failed to load attributes');
    });

    it('should set selected attribute', () => {
      service.setSelectedAttribute(mockAttribute);
      expect(service.selectedAttribute$()).toEqual(mockAttribute);
      service.setSelectedAttribute(null);
      expect(service.selectedAttribute$()).toBeNull();
    });

    it('should handle different data types', () => {
      const types: DataType[] = ['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE'];
      types.forEach(dataType => {
        const attr: PartAttributeDefinition = { ...mockAttribute, id: `attr-${dataType}`, dataType };
        service.setPartAttributes([attr]);
        expect(service.partAttributes$()[0].dataType).toBe(dataType);
      });
    });
  });

  describe('Combined State - Isolation', () => {
    it('should keep different state types isolated', () => {
      const mockProduct: ProductDefinition = {
        id: '1',
        organisationId: 'org-1',
        productCode: 'PROD-001',
        description: 'Test Product',
        billingModel: 'FIXED_PRICE',
        productValidFrom: null,
        productValidUntil: null
      };
      const mockPart: PartDefinition = {
        id: '2',
        organisationId: 'org-1',
        partCode: 'PART-001',
        description: 'Test Part',
        unitPrice: 100,
        displayOrder: 1,
        minCardinality: 1,
        maxCardinality: 1,
        childParts: [],
        attributes: []
      };
      const mockAttribute: PartAttributeDefinition = {
        id: '3',
        organisationId: 'org-1',
        attributeName: 'Color',
        dataType: 'STRING' as DataType,
        isRequired: true,
        defaultValue: null,
        allowedValues: []
      };

      service.setProductDefinitions([mockProduct]);
      service.setProductParts([mockPart]);
      service.setPartAttributes([mockAttribute]);
      service.setProductDefinitionsLoading(true);
      service.setProductPartsLoading(true);
      service.setPartAttributesLoading(true);

      expect(service.productDefinitions$()).toEqual([mockProduct]);
      expect(service.productParts$()).toEqual([mockPart]);
      expect(service.partAttributes$()).toEqual([mockAttribute]);
      expect(service.productDefinitionsLoading$()).toBe(true);
      expect(service.productPartsLoading$()).toBe(true);
      expect(service.partAttributesLoading$()).toBe(true);
    });
  });
});
