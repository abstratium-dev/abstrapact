import { TestBed } from '@angular/core/testing';
import { TermsAndConditionsModelService, TermsAndConditions } from './terms-and-conditions.model.service';

describe('TermsAndConditionsModelService', () => {
  let service: TermsAndConditionsModelService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TermsAndConditionsModelService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Initial State', () => {
    it('should have empty terms and conditions initially', () => {
      expect(service.termsAndConditions$()).toEqual([]);
    });

    it('should not be loading initially', () => {
      expect(service.termsAndConditionsLoading$()).toBe(false);
    });

    it('should have no error initially', () => {
      expect(service.termsAndConditionsError$()).toBeNull();
    });

    it('should have no selected terms initially', () => {
      expect(service.selectedTermsAndConditions$()).toBeNull();
    });
  });

  describe('State Management', () => {
    const mockTerms: TermsAndConditions = {
      id: '1',
      organisationId: 'org-1',
      code: 'T001',
      title: 'Test Terms',
      contentFr: 'Test Content FR',
      contentDe: 'Test Content DE',
      contentEn: 'Test Content',
      currentVersion: '1.0',
      effectiveFrom: null,
      effectiveUntil: null
    };

    it('should set terms and conditions', () => {
      service.setTermsAndConditions([mockTerms]);
      expect(service.termsAndConditions$()).toEqual([mockTerms]);
    });

    it('should update terms and conditions', () => {
      service.setTermsAndConditions([mockTerms]);
      const updated = { ...mockTerms, title: 'Updated' };
      service.setTermsAndConditions([updated]);
      expect(service.termsAndConditions$()).toEqual([updated]);
    });

    it('should handle empty terms list', () => {
      service.setTermsAndConditions([mockTerms]);
      service.setTermsAndConditions([]);
      expect(service.termsAndConditions$()).toEqual([]);
    });

    it('should set loading state', () => {
      service.setTermsAndConditionsLoading(true);
      expect(service.termsAndConditionsLoading$()).toBe(true);
      service.setTermsAndConditionsLoading(false);
      expect(service.termsAndConditionsLoading$()).toBe(false);
    });

    it('should set error state', () => {
      service.setTermsAndConditionsError('Error occurred');
      expect(service.termsAndConditionsError$()).toBe('Error occurred');
      service.setTermsAndConditionsError(null);
      expect(service.termsAndConditionsError$()).toBeNull();
    });

    it('should set selected terms and conditions', () => {
      service.setSelectedTermsAndConditions(mockTerms);
      expect(service.selectedTermsAndConditions$()).toEqual(mockTerms);
      service.setSelectedTermsAndConditions(null);
      expect(service.selectedTermsAndConditions$()).toBeNull();
    });
  });

  describe('Multiple Terms', () => {
    it('should manage multiple terms', () => {
      const terms1: TermsAndConditions = {
        id: '1', organisationId: 'org-1', code: 'T1', title: 'Terms 1',
        contentFr: 'Content 1 FR', contentDe: 'Content 1 DE', contentEn: 'Content 1', currentVersion: '1.0', effectiveFrom: null, effectiveUntil: null
      };
      const terms2: TermsAndConditions = {
        id: '2', organisationId: 'org-1', code: 'T2', title: 'Terms 2',
        contentFr: 'Content 2 FR', contentDe: 'Content 2 DE', contentEn: 'Content 2', currentVersion: '2.0', effectiveFrom: '2024-01-01', effectiveUntil: null
      };
      service.setTermsAndConditions([terms1, terms2]);
      expect(service.termsAndConditions$().length).toBe(2);
      expect(service.termsAndConditions$()[0].code).toBe('T1');
      expect(service.termsAndConditions$()[1].code).toBe('T2');
    });
  });
});
