import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CompleteProductResponse,
  PartDefinition,
  PartAttributeDefinition,
  DataType
} from '../../model.service';
import { Controller } from '../../controller';
import { ToastService } from '../../core/toast/toast.service';

interface SimulatedPartInstance {
  instanceId: string;
  attributeValues: Record<string, string>;
  children: SimulatedPart[];
}

interface SimulatedPart {
  definition: PartDefinition;
  instances: SimulatedPartInstance[];
}

interface InstanceResult {
  productCode: string;
  description: string;
  parts: SimulatedPart[];
  totalPrice: number;
}

@Component({
  selector: 'app-product-simulator',
  imports: [CommonModule, FormsModule],
  templateUrl: './product-simulator.component.html',
  styleUrl: './product-simulator.component.scss'
})
export class ProductSimulatorComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private controller = inject(Controller);
  private toastService = inject(ToastService);

  productId: string | null = null;
  completeProduct: CompleteProductResponse | null = null;
  loading = true;
  error: string | null = null;

  simulatedParts: SimulatedPart[] = [];
  showResult = false;
  instanceResult: InstanceResult | null = null;

  ngOnInit(): void {
    this.productId = this.route.snapshot.paramMap.get('id');
    if (this.productId) {
      this.loadProduct();
    } else {
      this.error = 'No product ID provided';
      this.loading = false;
    }
  }

  async loadProduct(): Promise<void> {
    this.loading = true;
    this.error = null;

    const response = await this.controller.getCompleteProduct(this.productId!);
    if (response) {
      this.completeProduct = response;
      this.simulatedParts = response.parts.map(part => this.createSimulatedPart(part));
      this.loading = false;
    } else {
      this.error = 'Product definition not found';
      this.loading = false;
    }
  }

  private createSimulatedPart(part: PartDefinition): SimulatedPart {
    const min = part.minCardinality || 1;
    const instances: SimulatedPartInstance[] = [];
    for (let i = 0; i < min; i++) {
      instances.push(this.createInstance(part));
    }
    return {
      definition: part,
      instances: instances
    };
  }

  private createInstance(part: PartDefinition): SimulatedPartInstance {
    const attrValues: Record<string, string> = {};
    for (const attr of part.attributes || []) {
      attrValues[attr.id] = attr.defaultValue || '';
    }
    return {
      instanceId: crypto.randomUUID(),
      attributeValues: attrValues,
      children: (part.childParts || []).map(child => this.createSimulatedPart(child))
    };
  }

  canAddInstance(simulatedPart: SimulatedPart): boolean {
    const max = simulatedPart.definition.maxCardinality || 1;
    return simulatedPart.instances.length < max;
  }

  canRemoveInstance(simulatedPart: SimulatedPart): boolean {
    const min = simulatedPart.definition.minCardinality || 1;
    return simulatedPart.instances.length > min;
  }

  addInstance(simulatedPart: SimulatedPart): void {
    if (!this.canAddInstance(simulatedPart)) {
      return;
    }
    simulatedPart.instances.push(this.createInstance(simulatedPart.definition));
  }

  removeInstance(simulatedPart: SimulatedPart, index: number): void {
    if (!this.canRemoveInstance(simulatedPart)) {
      return;
    }
    simulatedPart.instances.splice(index, 1);
  }

  setAttributeValue(instance: SimulatedPartInstance, attrId: string, value: string): void {
    instance.attributeValues[attrId] = value;
  }

  getAttributeInputType(dataType: DataType): string {
    switch (dataType) {
      case 'INTEGER': return 'number';
      case 'DECIMAL': return 'number';
      case 'DATE': return 'date';
      case 'BOOLEAN': return 'checkbox';
      default: return 'text';
    }
  }

  getAttributeLabel(attribute: PartAttributeDefinition): string {
    let label = attribute.attributeName;
    if (attribute.isRequired) {
      label += ' *';
    }
    return label;
  }

  isAttributeValid(instance: SimulatedPartInstance, attribute: PartAttributeDefinition): boolean {
    if (!attribute.isRequired) {
      return true;
    }
    const value = instance.attributeValues[attribute.id];
    return value !== undefined && value.trim().length > 0;
  }

  areAllRequiredAttributesFilled(instance: SimulatedPartInstance, definition: PartDefinition): boolean {
    for (const attr of definition.attributes || []) {
      if (attr.isRequired) {
        const value = instance.attributeValues[attr.id];
        if (!value || value.trim().length === 0) {
          return false;
        }
      }
    }
    return true;
  }

  isConfigurationValid(): boolean {
    return this.validateSimulatedParts(this.simulatedParts);
  }

  private validateSimulatedParts(parts: SimulatedPart[]): boolean {
    for (const part of parts) {
      const min = part.definition.minCardinality || 1;
      const max = part.definition.maxCardinality || 1;
      if (part.instances.length < min || part.instances.length > max) {
        return false;
      }
      for (const instance of part.instances) {
        if (!this.areAllRequiredAttributesFilled(instance, part.definition)) {
          return false;
        }
        if (!this.validateSimulatedParts(instance.children)) {
          return false;
        }
      }
    }
    return true;
  }

  calculateTotalPrice(parts: SimulatedPart[]): number {
    let total = 0;
    for (const part of parts) {
      for (const instance of part.instances) {
        total += part.definition.unitPrice || 0;
        total += this.calculateTotalPrice(instance.children);
      }
    }
    return total;
  }

  generateInstance(): void {
    if (!this.isConfigurationValid()) {
      this.toastService.error('Please fill in all required attributes before generating the instance.');
      return;
    }

    this.instanceResult = {
      productCode: this.completeProduct!.productCode,
      description: this.completeProduct!.description,
      parts: this.simulatedParts,
      totalPrice: this.calculateTotalPrice(this.simulatedParts)
    };
    this.showResult = true;
  }

  resetSimulator(): void {
    this.showResult = false;
    this.instanceResult = null;
    if (this.completeProduct) {
      this.simulatedParts = this.completeProduct.parts.map(part => this.createSimulatedPart(part));
    }
  }

  onBack(): void {
    if (this.productId) {
      this.router.navigate(['/product-definitions', this.productId]);
    } else {
      this.router.navigate(['/product-definitions']);
    }
  }

  formatPrice(price: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD'
    }).format(price || 0);
  }

  getDataTypeLabel(dataType: string): string {
    const labels: Record<string, string> = {
      'STRING': 'String',
      'INTEGER': 'Integer',
      'DECIMAL': 'Decimal',
      'BOOLEAN': 'Boolean',
      'DATE': 'Date'
    };
    return labels[dataType] || dataType;
  }

  getCardinalityLabel(definition: PartDefinition): string {
    const min = definition.minCardinality || 1;
    const max = definition.maxCardinality || 1;
    return `${min}..${max}`;
  }

  collectIncludedInstances(parts: SimulatedPart[], result: { definition: PartDefinition; attributeValues: Record<string, string> }[] = []): { definition: PartDefinition; attributeValues: Record<string, string> }[] {
    for (const part of parts) {
      for (const instance of part.instances) {
        result.push({ definition: part.definition, attributeValues: instance.attributeValues });
        this.collectIncludedInstances(instance.children, result);
      }
    }
    return result;
  }
}
