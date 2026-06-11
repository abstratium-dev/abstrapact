import { TestBed } from '@angular/core/testing';
import { Config, Demo, ModelService } from './model.service';

describe('ModelService', () => {
  let service: ModelService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ModelService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Initial State', () => {
    it('should have empty demos initially', () => {
      expect(service.demos$()).toEqual([]);
    });

    it('should not be loading initially', () => {
      expect(service.demosLoading$()).toBe(false);
    });

    it('should have no error initially', () => {
      expect(service.demosError$()).toBeNull();
    });
  });

  describe('Demo Management', () => {
    it('should set demos', () => {
      const demos: Demo[] = [{ id: '1' }, { id: '2' }];
      service.setDemos(demos);
      expect(service.demos$()).toEqual(demos);
    });

    it('should update demos', () => {
      const demos1: Demo[] = [{ id: '1' }];
      const demos2: Demo[] = [{ id: '2' }, { id: '3' }];
      
      service.setDemos(demos1);
      expect(service.demos$()).toEqual(demos1);
      
      service.setDemos(demos2);
      expect(service.demos$()).toEqual(demos2);
    });

    it('should handle empty demos list', () => {
      const demos: Demo[] = [{ id: '1' }];
      service.setDemos(demos);
      service.setDemos([]);
      expect(service.demos$()).toEqual([]);
    });

    it('should handle large demos list', () => {
      const demos: Demo[] = Array.from({ length: 100 }, (_, i) => ({ id: `${i}` }));
      service.setDemos(demos);
      expect(service.demos$()).toEqual(demos);
      expect(service.demos$().length).toBe(100);
    });
  });

  describe('Loading State Management', () => {
    it('should set loading state', () => {
      service.setDemosLoading(true);
      expect(service.demosLoading$()).toBe(true);
    });

    it('should update loading state', () => {
      service.setDemosLoading(true);
      expect(service.demosLoading$()).toBe(true);
      
      service.setDemosLoading(false);
      expect(service.demosLoading$()).toBe(false);
    });

    it('should toggle loading state multiple times', () => {
      service.setDemosLoading(true);
      service.setDemosLoading(false);
      service.setDemosLoading(true);
      expect(service.demosLoading$()).toBe(true);
    });
  });

  describe('Error State Management', () => {
    it('should set error', () => {
      service.setDemosError('Failed to load demos');
      expect(service.demosError$()).toBe('Failed to load demos');
    });

    it('should update error', () => {
      service.setDemosError('Error 1');
      expect(service.demosError$()).toBe('Error 1');
      
      service.setDemosError('Error 2');
      expect(service.demosError$()).toBe('Error 2');
    });

    it('should clear error', () => {
      service.setDemosError('Some error');
      service.setDemosError(null);
      expect(service.demosError$()).toBeNull();
    });

    it('should handle empty string error', () => {
      service.setDemosError('');
      expect(service.demosError$()).toBe('');
    });
  });

  describe('Combined State Management', () => {
    it('should manage all states independently', () => {
      const demos: Demo[] = [{ id: '1' }];
      service.setDemos(demos);
      service.setDemosLoading(true);
      service.setDemosError('Some error');

      expect(service.demos$()).toEqual(demos);
      expect(service.demosLoading$()).toBe(true);
      expect(service.demosError$()).toBe('Some error');
    });

    it('should reset all states', () => {
      service.setDemos([{ id: '1' }]);
      service.setDemosLoading(true);
      service.setDemosError('Error');

      service.setDemos([]);
      service.setDemosLoading(false);
      service.setDemosError(null);

      expect(service.demos$()).toEqual([]);
      expect(service.demosLoading$()).toBe(false);
      expect(service.demosError$()).toBeNull();
    });
  });

  describe('Signal Reactivity', () => {
    it('should emit signal updates for demos', () => {
      const demos1: Demo[] = [{ id: '1' }];
      const demos2: Demo[] = [{ id: '2' }];
      
      service.setDemos(demos1);
      expect(service.demos$()).toEqual(demos1);
      
      service.setDemos(demos2);
      expect(service.demos$()).toEqual(demos2);
    });

    it('should emit signal updates for loading', () => {
      service.setDemosLoading(true);
      expect(service.demosLoading$()).toBe(true);
      
      service.setDemosLoading(false);
      expect(service.demosLoading$()).toBe(false);
    });

    it('should emit signal updates for error', () => {
      service.setDemosError('Error 1');
      expect(service.demosError$()).toBe('Error 1');
      
      service.setDemosError('Error 2');
      expect(service.demosError$()).toBe('Error 2');
    });
  });

  describe('Service Singleton', () => {
    it('should be a singleton across injections', () => {
      const service2 = TestBed.inject(ModelService);
      service.setDemos([{ id: '1' }]);
      expect(service2.demos$()).toEqual([{ id: '1' }]);
    });
  });

  describe('Config Management', () => {
    it('should set warningMessage from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: 'Test warning', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null };
      service.setConfig(config);
      expect(service.warningMessage$()).toBe('Test warning');
    });

    it('should clear warningMessage when config value is "-"', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '-', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null };
      service.setConfig(config);
      expect(service.warningMessage$()).toBe('');
    });

    it('should default warningBgColor to #fff3cd initially', () => {
      expect(service.warningBgColor$()).toBe('#fff3cd');
    });

    it('should set warningBgColor from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: 'alert', warningBgColor: '#ff0000', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null };
      service.setConfig(config);
      expect(service.warningBgColor$()).toBe('#ff0000');
    });

    it('should set warningBgColor to empty string when config value is empty', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: 'alert', warningBgColor: '', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null };
      service.setConfig(config);
      expect(service.warningBgColor$()).toBe('');
    });

    it('should update warningBgColor when config changes', () => {
      service.setConfig({ logLevel: 'INFO', warningMessage: 'a', warningBgColor: '#aabbcc', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null });
      expect(service.warningBgColor$()).toBe('#aabbcc');
      service.setConfig({ logLevel: 'INFO', warningMessage: 'b', warningBgColor: '#112233', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null });
      expect(service.warningBgColor$()).toBe('#112233');
    });

    it('should have default brand values initially', () => {
      expect(service.brandLogoUrl$()).toBe('https://abstratium.dev/abstratium-logo-small.png');
      expect(service.brandLogoAlt$()).toBe('Abstratium Logo');
      expect(service.brandName$()).toBe('ABSTRATIUM');
    });

    it('should set brand values from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: 'https://my.app/logo.svg', brandLogoAlt: 'My App', brandName: 'My App', legalContent: null };
      service.setConfig(config);
      expect(service.brandLogoUrl$()).toBe('https://my.app/logo.svg');
      expect(service.brandLogoAlt$()).toBe('My App');
      expect(service.brandName$()).toBe('My App');
    });

    it('should fall back to defaults when brand fields are empty strings', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: '', brandLogoAlt: '', brandName: '', legalContent: null };
      service.setConfig(config);
      expect(service.brandLogoUrl$()).toBe('https://abstratium.dev/abstratium-logo-small.png');
      expect(service.brandLogoAlt$()).toBe('Abstratium Logo');
      expect(service.brandName$()).toBe('ABSTRATIUM');
    });

    it('should set legalContent$ when config has legalContent', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: '', brandLogoAlt: '', brandName: '', legalContent: '<p>Custom Legal</p>' };
      service.setConfig(config);
      expect(service.legalContent$()).toBe('<p>Custom Legal</p>');
    });

    it('should set legalContent$ to null when config legalContent is null', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: '', brandLogoAlt: '', brandName: '', legalContent: null };
      service.setConfig(config);
      expect(service.legalContent$()).toBeNull();
    });
  });
});
