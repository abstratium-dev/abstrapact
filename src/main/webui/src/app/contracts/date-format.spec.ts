/// <reference types="jasmine" />
import { formatDateTime } from './date-format';

describe('formatDateTime', () => {
  it('should format an ISO-8601 date as "YYYY-MM-DD HH:mm:ss.SSS"', () => {
    expect(formatDateTime('2026-10-04T20:55:27.412Z')).toBe('2026-10-04 20:55:27.412');
  });

  it('should format a date-only string as "YYYY-MM-DD 00:00:00.000"', () => {
    expect(formatDateTime('2024-01-15')).toBe('2024-01-15 00:00:00.000');
  });

  it('should return N/A for null', () => {
    expect(formatDateTime(null)).toBe('N/A');
  });

  it('should return N/A for undefined', () => {
    expect(formatDateTime(undefined)).toBe('N/A');
  });
});
