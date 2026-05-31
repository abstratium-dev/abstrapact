import { Injectable, signal, Signal } from '@angular/core';

export interface Config {
  logLevel: string;
  warningMessage: string;
  warningBgColor: string;
  brandLogoUrl: string;
  brandLogoAlt: string;
  brandName: string;
}

export type BillingModel = 'FIXED_PRICE' | 'SUBSCRIPTION';

export type DataType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'DATE';

export interface ProductDefinition {
  id: string;
  organisationId: string;
  productCode: string;
  description: string;
  billingModel: BillingModel;
  productValidFrom: string | null;
  productValidUntil: string | null;
}

export interface ProductDefinitionRequest {
  productCode: string;
  description: string;
  billingModel: BillingModel;
  productValidFrom: string | null;
  productValidUntil: string | null;
}

export interface PartAttributeAllowedValue {
  id: string;
  organisationId: string;
  allowedValue: string;
}

export interface PartAttributeAllowedValueRequest {
  id?: string;
  allowedValue: string;
}

export interface PartAttributeDefinition {
  id: string;
  organisationId: string;
  attributeName: string;
  dataType: DataType;
  isRequired: boolean;
  defaultValue: string | null;
  allowedValues: PartAttributeAllowedValue[];
}

export interface PartAttributeRequest {
  id?: string;
  attributeName: string;
  dataType: DataType;
  isRequired: boolean;
  defaultValue: string | null;
  allowedValues: PartAttributeAllowedValueRequest[];
}

export interface PartDefinition {
  id: string;
  organisationId: string;
  partCode: string;
  description: string;
  unitPrice: number;
  displayOrder: number;
  minCardinality: number;
  maxCardinality: number;
  childParts: PartDefinition[];
  attributes: PartAttributeDefinition[];
}

export interface PartRequest {
  id?: string;
  partCode: string;
  description: string;
  unitPrice: number;
  displayOrder: number;
  minCardinality: number;
  maxCardinality: number;
  childParts: PartRequest[];
  attributes: PartAttributeRequest[];
}

export interface CompleteProductRequest {
  id?: string;
  productCode: string;
  description: string;
  billingModel: BillingModel;
  productValidFrom: string | null;
  productValidUntil: string | null;
  parts: PartRequest[];
}

export interface CompleteProductResponse {
  id: string;
  productCode: string;
  description: string;
  billingModel: BillingModel;
  productValidFrom: string | null;
  productValidUntil: string | null;
  parts: PartDefinition[];
}

@Injectable({
  providedIn: 'root',
})
export class ModelService {

  private config = signal<Config | null>(null);
  private warningMessage = signal<string>('');
  private warningBgColor = signal<string>('#fff3cd');
  private readonly defaultBrandLogoUrl = 'https://abstratium.dev/abstratium-logo-small.png';
  private readonly defaultBrandLogoAlt = 'Abstratium Logo';
  private readonly defaultBrandName = 'ABSTRATIUM';

  private brandLogoUrl = signal<string>(this.defaultBrandLogoUrl);
  private brandLogoAlt = signal<string>(this.defaultBrandLogoAlt);
  private brandName = signal<string>(this.defaultBrandName);

  // Product Definitions state
  private productDefinitions = signal<ProductDefinition[]>([]);
  private productDefinitionsLoading = signal<boolean>(false);
  private productDefinitionsError = signal<string | null>(null);
  private selectedProductDefinition = signal<ProductDefinition | null>(null);

  // Parts state
  private productParts = signal<PartDefinition[]>([]);
  private productPartsLoading = signal<boolean>(false);
  private productPartsError = signal<string | null>(null);
  private selectedPart = signal<PartDefinition | null>(null);

  // Attributes state
  private partAttributes = signal<PartAttributeDefinition[]>([]);
  private partAttributesLoading = signal<boolean>(false);
  private partAttributesError = signal<string | null>(null);
  private selectedAttribute = signal<PartAttributeDefinition | null>(null);

  config$: Signal<Config | null> = this.config.asReadonly();
  warningMessage$: Signal<string> = this.warningMessage.asReadonly();
  warningBgColor$: Signal<string> = this.warningBgColor.asReadonly();
  brandLogoUrl$: Signal<string> = this.brandLogoUrl.asReadonly();
  brandLogoAlt$: Signal<string> = this.brandLogoAlt.asReadonly();
  brandName$: Signal<string> = this.brandName.asReadonly();

  // Product Definitions signals
  productDefinitions$: Signal<ProductDefinition[]> = this.productDefinitions.asReadonly();
  productDefinitionsLoading$: Signal<boolean> = this.productDefinitionsLoading.asReadonly();
  productDefinitionsError$: Signal<string | null> = this.productDefinitionsError.asReadonly();
  selectedProductDefinition$: Signal<ProductDefinition | null> = this.selectedProductDefinition.asReadonly();

  // Parts signals
  productParts$: Signal<PartDefinition[]> = this.productParts.asReadonly();
  productPartsLoading$: Signal<boolean> = this.productPartsLoading.asReadonly();
  productPartsError$: Signal<string | null> = this.productPartsError.asReadonly();
  selectedPart$: Signal<PartDefinition | null> = this.selectedPart.asReadonly();

  // Attributes signals
  partAttributes$: Signal<PartAttributeDefinition[]> = this.partAttributes.asReadonly();
  partAttributesLoading$: Signal<boolean> = this.partAttributesLoading.asReadonly();
  partAttributesError$: Signal<string | null> = this.partAttributesError.asReadonly();
  selectedAttribute$: Signal<PartAttributeDefinition | null> = this.selectedAttribute.asReadonly();

  setConfig(config: Config) {
    this.config.set(config);
    if (config.warningMessage === '-') {
      this.warningMessage.set('');
    } else {
      this.warningMessage.set(config.warningMessage);
    }
    this.warningBgColor.set(config.warningBgColor);
    this.brandLogoUrl.set(config.brandLogoUrl || this.defaultBrandLogoUrl);
    this.brandLogoAlt.set(config.brandLogoAlt || this.defaultBrandLogoAlt);
    this.brandName.set(config.brandName || this.defaultBrandName);
  }

  // Product Definitions setters
  setProductDefinitions(productDefinitions: ProductDefinition[]) {
    this.productDefinitions.set(productDefinitions);
  }

  setProductDefinitionsLoading(loading: boolean) {
    this.productDefinitionsLoading.set(loading);
  }

  setProductDefinitionsError(error: string | null) {
    this.productDefinitionsError.set(error);
  }

  setSelectedProductDefinition(productDefinition: ProductDefinition | null) {
    this.selectedProductDefinition.set(productDefinition);
  }

  // Parts setters
  setProductParts(parts: PartDefinition[]) {
    this.productParts.set(parts);
  }

  setProductPartsLoading(loading: boolean) {
    this.productPartsLoading.set(loading);
  }

  setProductPartsError(error: string | null) {
    this.productPartsError.set(error);
  }

  setSelectedPart(part: PartDefinition | null) {
    this.selectedPart.set(part);
  }

  // Attributes setters
  setPartAttributes(attributes: PartAttributeDefinition[]) {
    this.partAttributes.set(attributes);
  }

  setPartAttributesLoading(loading: boolean) {
    this.partAttributesLoading.set(loading);
  }

  setPartAttributesError(error: string | null) {
    this.partAttributesError.set(error);
  }

  setSelectedAttribute(attribute: PartAttributeDefinition | null) {
    this.selectedAttribute.set(attribute);
  }
}
