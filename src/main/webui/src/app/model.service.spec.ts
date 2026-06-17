import { TestBed } from '@angular/core/testing';
import { Config, ModelService } from './model.service';

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
      const config: Config = { logLevel: 'INFO', warningMessage: 'Singleton test', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: 'USD', locale: 'en-US' };
      service.setConfig(config);
      expect(service2.warningMessage$()).toBe('Singleton test');
    });
  });

  describe('Config Management', () => {
    it('should set warningMessage from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: 'Test warning', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: 'USD', locale: 'en-US' };
      service.setConfig(config);
      expect(service.warningMessage$()).toBe('Test warning');
    });

    it('should clear warningMessage when config value is "-"', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '-', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: 'USD', locale: 'en-US' };
      service.setConfig(config);
      expect(service.warningMessage$()).toBe('');
    });

    it('should default warningBgColor to #fff3cd initially', () => {
      expect(service.warningBgColor$()).toBe('#fff3cd');
    });

    it('should set warningBgColor from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: 'alert', warningBgColor: '#ff0000', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: 'USD', locale: 'en-US' };
      service.setConfig(config);
      expect(service.warningBgColor$()).toBe('#ff0000');
    });

    it('should set warningBgColor to empty string when config value is empty', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: 'alert', warningBgColor: '', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: 'USD', locale: 'en-US' };
      service.setConfig(config);
      expect(service.warningBgColor$()).toBe('');
    });

    it('should update warningBgColor when config changes', () => {
      service.setConfig({ logLevel: 'INFO', warningMessage: 'a', warningBgColor: '#aabbcc', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: 'USD', locale: 'en-US' });
      expect(service.warningBgColor$()).toBe('#aabbcc');
      service.setConfig({ logLevel: 'INFO', warningMessage: 'b', warningBgColor: '#112233', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: 'USD', locale: 'en-US' });
      expect(service.warningBgColor$()).toBe('#112233');
    });

    it('should have default brand values initially', () => {
      expect(service.brandLogoUrl$()).toBe('https://abstratium.dev/abstratium-logo-small.png');
      expect(service.brandLogoAlt$()).toBe('Abstratium Logo');
      expect(service.brandName$()).toBe('ABSTRATIUM');
    });

    it('should set brand values from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: 'https://my.app/logo.svg', brandLogoAlt: 'My App', brandName: 'My App', legalContent: null, currencyCode: 'USD', locale: 'en-US' };
      service.setConfig(config);
      expect(service.brandLogoUrl$()).toBe('https://my.app/logo.svg');
      expect(service.brandLogoAlt$()).toBe('My App');
      expect(service.brandName$()).toBe('My App');
    });

    it('should fall back to defaults when brand fields are empty strings', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: '', brandLogoAlt: '', brandName: '', legalContent: null, currencyCode: '', locale: '' };
      service.setConfig(config);
      expect(service.brandLogoUrl$()).toBe('https://abstratium.dev/abstratium-logo-small.png');
      expect(service.brandLogoAlt$()).toBe('Abstratium Logo');
      expect(service.brandName$()).toBe('ABSTRATIUM');
    });

    it('should default currencyCode to CHF initially', () => {
      expect(service.currencyCode$()).toBe('CHF');
    });

    it('should set currencyCode from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: 'USD', locale: 'en-US' };
      service.setConfig(config);
      expect(service.currencyCode$()).toBe('USD');
    });

    it('should fall back to default when currencyCode is empty string', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: '', locale: 'en-US' };
      service.setConfig(config);
      expect(service.currencyCode$()).toBe('CHF');
    });

    it('should default locale to en-US initially', () => {
      expect(service.locale$()).toBe('en-US');
    });

    it('should set locale from config', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: 'USD', locale: 'de-DE' };
      service.setConfig(config);
      expect(service.locale$()).toBe('de-DE');
    });

    it('should fall back to default when locale is empty string', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: 'https://example.com/logo.png', brandLogoAlt: 'Logo', brandName: 'Example', legalContent: null, currencyCode: 'USD', locale: '' };
      service.setConfig(config);
      expect(service.locale$()).toBe('en-US');
    });
    
    it('should set legalContent$ when config has legalContent', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: '', brandLogoAlt: '', brandName: 'Example', legalContent: '<p>Custom Legal</p>', currencyCode: 'USD', locale: '' };
      service.setConfig(config);
      expect(service.legalContent$()).toBe('<p>Custom Legal</p>');
    });

    it('should set legalContent$ to null when config legalContent is null', () => {
      const config: Config = { logLevel: 'INFO', warningMessage: '', warningBgColor: '#fff3cd', brandLogoUrl: '', brandLogoAlt: '', brandName: '', legalContent: null, currencyCode: 'USD', locale: '' };
      service.setConfig(config);
      expect(service.legalContent$()).toBeNull();
    });
  });

});
