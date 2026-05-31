import { TestBed } from '@angular/core/testing';
import {
  Config, ModelService, ProductDefinition,
  PartDefinition, PartAttributeDefinition, DataType
} from './model.service';

describe('ModelService', () => {
  let service: ModelService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ModelService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Service Singleton', () => {
    it('should be a singleton across injections', () => {
      const service2 = TestBed.inject(ModelService);
      const config: Config = { logLevel: 'INFO', warningMessage: 'Singleton test', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example' };
      service.setConfig(config);
      expect(service2.warningMessage$()).toBe('Singleton test');
    });
  });

  describe('Config Management', () => {
    it('should set warningMessage from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: 'Test warning', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example' };
      service.setConfig(config);
      expect(service.warningMessage$()).toBe('Test warning');
    });

    it('should clear warningMessage when config value is "-"', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '-', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example' };
      service.setConfig(config);
      expect(service.warningMessage$()).toBe('');
    });

    it('should default warningBgColor to #fff3cd initially', () => {
      expect(service.warningBgColor$()).toBe('#fff3cd');
    });

    it('should set warningBgColor from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: 'alert', warningBgColor: '#ff0000', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example' };
      service.setConfig(config);
      expect(service.warningBgColor$()).toBe('#ff0000');
    });

    it('should set warningBgColor to empty string when config value is empty', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: 'alert', warningBgColor: '', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example' };
      service.setConfig(config);
      expect(service.warningBgColor$()).toBe('');
    });

    it('should update warningBgColor when config changes', () => {
      service.setConfig({ logLevel: 'INFO', warningMessage: 'a', warningBgColor: '#aabbcc', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example' });
      expect(service.warningBgColor$()).toBe('#aabbcc');
      service.setConfig({ logLevel: 'INFO', warningMessage: 'b', warningBgColor: '#112233', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example' });
      expect(service.warningBgColor$()).toBe('#112233');
    });

    it('should have default brand values initially', () => {
      expect(service.brandLogoUrl$()).toBe('https://abstratium.dev/abstratium-logo-small.png');
      expect(service.brandLogoAlt$()).toBe('Abstratium Logo');
      expect(service.brandName$()).toBe('ABSTRATIUM');
    });

    it('should set brand values from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: 'https://my.app/logo.svg', brandLogoAlt: 'My App', brandName: 'My App' };
      service.setConfig(config);
      expect(service.brandLogoUrl$()).toBe('https://my.app/logo.svg');
      expect(service.brandLogoAlt$()).toBe('My App');
      expect(service.brandName$()).toBe('My App');
    });

    it('should fall back to defaults when brand fields are empty strings', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: '', brandLogoAlt: '', brandName: '' };
      service.setConfig(config);
      expect(service.brandLogoUrl$()).toBe('https://abstratium.dev/abstratium-logo-small.png');
      expect(service.brandLogoAlt$()).toBe('Abstratium Logo');
      expect(service.brandName$()).toBe('ABSTRATIUM');
    });
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

    it('should set product definitions loading state', () => {
      service.setProductDefinitionsLoading(true);
      expect(service.productDefinitionsLoading$()).toBe(true);
    });

    it('should update product definitions loading state', () => {
      service.setProductDefinitionsLoading(true);
      expect(service.productDefinitionsLoading$()).toBe(true);

      service.setProductDefinitionsLoading(false);
      expect(service.productDefinitionsLoading$()).toBe(false);
    });

    it('should set product definitions error', () => {
      service.setProductDefinitionsError('Failed to load');
      expect(service.productDefinitionsError$()).toBe('Failed to load');
    });

    it('should clear product definitions error', () => {
      service.setProductDefinitionsError('Some error');
      service.setProductDefinitionsError(null);
      expect(service.productDefinitionsError$()).toBeNull();
    });

    it('should set selected product definition', () => {
      service.setSelectedProductDefinition(mockProductDefinitions[0]);
      expect(service.selectedProductDefinition$()).toEqual(mockProductDefinitions[0]);
    });

    it('should clear selected product definition', () => {
      service.setSelectedProductDefinition(mockProductDefinitions[0]);
      service.setSelectedProductDefinition(null);
      expect(service.selectedProductDefinition$()).toBeNull();
    });
  });

  describe('Product Definitions - Combined State Management', () => {
    it('should manage all product definition states independently', () => {
      const mockProduct: ProductDefinition = {
        id: '1',
        organisationId: 'org-1',
        productCode: 'PROD-001',
        description: 'Test',
        billingModel: 'FIXED_PRICE',
        productValidFrom: null,
        productValidUntil: null
      };

      service.setProductDefinitions([mockProduct]);
      service.setProductDefinitionsLoading(true);
      service.setProductDefinitionsError('Error');
      service.setSelectedProductDefinition(mockProduct);

      expect(service.productDefinitions$()).toEqual([mockProduct]);
      expect(service.productDefinitionsLoading$()).toBe(true);
      expect(service.productDefinitionsError$()).toBe('Error');
      expect(service.selectedProductDefinition$()).toEqual(mockProduct);
    });

    it('should reset all product definition states', () => {
      const mockProduct: ProductDefinition = {
        id: '1',
        organisationId: 'org-1',
        productCode: 'PROD-001',
        description: 'Test',
        billingModel: 'FIXED_PRICE',
        productValidFrom: null,
        productValidUntil: null
      };

      service.setProductDefinitions([mockProduct]);
      service.setProductDefinitionsLoading(true);
      service.setProductDefinitionsError('Error');
      service.setSelectedProductDefinition(mockProduct);

      service.setProductDefinitions([]);
      service.setProductDefinitionsLoading(false);
      service.setProductDefinitionsError(null);
      service.setSelectedProductDefinition(null);

      expect(service.productDefinitions$()).toEqual([]);
      expect(service.productDefinitionsLoading$()).toBe(false);
      expect(service.productDefinitionsError$()).toBeNull();
      expect(service.selectedProductDefinition$()).toBeNull();
    });
  });

  describe('Parts - Initial State', () => {
    it('should have empty parts initially', () => {
      expect(service.productParts$()).toEqual([]);
    });

    it('should not be loading parts initially', () => {
      expect(service.productPartsLoading$()).toBe(false);
    });

    it('should have no parts error initially', () => {
      expect(service.productPartsError$()).toBeNull();
    });

    it('should have no selected part initially', () => {
      expect(service.selectedPart$()).toBeNull();
    });
  });

  describe('Parts - State Management', () => {
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

    const mockChildPart: PartDefinition = {
      id: 'part-2',
      organisationId: 'org-1',
      partCode: 'PART-002',
      description: 'Child Part',
      unitPrice: 49.99,
      displayOrder: 2,
      minCardinality: 1,
      maxCardinality: 1,
      childParts: [],
      attributes: []
    };

    it('should set product parts', () => {
      service.setProductParts([mockPart]);
      expect(service.productParts$()).toEqual([mockPart]);
    });

    it('should update product parts', () => {
      service.setProductParts([mockPart]);
      service.setProductParts([{ ...mockPart, childParts: [mockChildPart] }]);
      expect(service.productParts$()[0].childParts).toEqual([mockChildPart]);
    });

    it('should handle nested parts', () => {
      const nestedPart: PartDefinition = {
        ...mockPart,
        childParts: [mockChildPart]
      };
      service.setProductParts([nestedPart]);
      expect(service.productParts$()[0].childParts.length).toBe(1);
      expect(service.productParts$()[0].childParts[0].partCode).toBe('PART-002');
    });

    it('should set parts loading state', () => {
      service.setProductPartsLoading(true);
      expect(service.productPartsLoading$()).toBe(true);
    });

    it('should set parts error', () => {
      service.setProductPartsError('Failed to load parts');
      expect(service.productPartsError$()).toBe('Failed to load parts');
    });

    it('should set selected part', () => {
      service.setSelectedPart(mockPart);
      expect(service.selectedPart$()).toEqual(mockPart);
    });

    it('should clear selected part', () => {
      service.setSelectedPart(mockPart);
      service.setSelectedPart(null);
      expect(service.selectedPart$()).toBeNull();
    });
  });

  describe('Attributes - Initial State', () => {
    it('should have empty attributes initially', () => {
      expect(service.partAttributes$()).toEqual([]);
    });

    it('should not be loading attributes initially', () => {
      expect(service.partAttributesLoading$()).toBe(false);
    });

    it('should have no attributes error initially', () => {
      expect(service.partAttributesError$()).toBeNull();
    });

    it('should have no selected attribute initially', () => {
      expect(service.selectedAttribute$()).toBeNull();
    });
  });

  describe('Attributes - State Management', () => {
    const mockAttribute: PartAttributeDefinition = {
      id: 'attr-1',
      organisationId: 'org-1',
      attributeName: 'color',
      dataType: 'STRING' as DataType,
      isRequired: true,
      defaultValue: 'red',
      allowedValues: [
        { id: 'val-1', organisationId: 'org-1', allowedValue: 'red' },
        { id: 'val-2', organisationId: 'org-1', allowedValue: 'blue' }
      ]
    };

    it('should set part attributes', () => {
      service.setPartAttributes([mockAttribute]);
      expect(service.partAttributes$()).toEqual([mockAttribute]);
    });

    it('should handle attributes with allowed values', () => {
      service.setPartAttributes([mockAttribute]);
      expect(service.partAttributes$()[0].allowedValues.length).toBe(2);
      expect(service.partAttributes$()[0].allowedValues[0].allowedValue).toBe('red');
    });

    it('should set attributes loading state', () => {
      service.setPartAttributesLoading(true);
      expect(service.partAttributesLoading$()).toBe(true);
    });

    it('should set attributes error', () => {
      service.setPartAttributesError('Failed to load attributes');
      expect(service.partAttributesError$()).toBe('Failed to load attributes');
    });

    it('should set selected attribute', () => {
      service.setSelectedAttribute(mockAttribute);
      expect(service.selectedAttribute$()).toEqual(mockAttribute);
    });

    it('should clear selected attribute', () => {
      service.setSelectedAttribute(mockAttribute);
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

  describe('Combined State Management', () => {
    it('should manage all states independently', () => {
      const mockProduct: ProductDefinition = {
        id: '1',
        organisationId: 'org-1',
        productCode: 'PROD-001',
        description: 'Test',
        billingModel: 'FIXED_PRICE',
        productValidFrom: null,
        productValidUntil: null
      };

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

      const mockAttribute: PartAttributeDefinition = {
        id: 'attr-1',
        organisationId: 'org-1',
        attributeName: 'color',
        dataType: 'STRING' as DataType,
        isRequired: false,
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
