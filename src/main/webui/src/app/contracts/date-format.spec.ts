/// <reference types="jasmine" />
import { formatDateTime, formatDate } from './date-format';

describe('formatDate', () => {
  it('should format an ISO-8601 date as "YYYY-MM-DD"', () => {
    expect(formatDate('2026-10-04T20:55:27.412Z')).toBe('2026-10-04');
  });

  it('should format a date-only string as "YYYY-MM-DD"', () => {
    expect(formatDate('2024-01-15')).toBe('2024-01-15');
  });

  it('should return N/A for null', () => {
    expect(formatDate(null)).toBe('N/A');
  });

  it('should return N/A for undefined', () => {
    expect(formatDate(undefined)).toBe('N/A');
  });
});

describe('formatDateTime', () => {
  it('should format an ISO-8601 date in the browser time zone', () => {
    const input = '2026-10-04T20:55:27.412Z';
    const d = new Date(input);
    const pad2 = (n: number) => n.toString().padStart(2, '0');
    const pad3 = (n: number) => n.toString().padStart(3, '0');
    const expected = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ` +
      `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}.${pad3(d.getMilliseconds())}`;
    expect(formatDateTime(input)).toBe(expected);
  });

  it('should format a date-only string using the browser time zone', () => {
    const input = '2024-01-15';
    const d = new Date(input);
    const pad2 = (n: number) => n.toString().padStart(2, '0');
    const pad3 = (n: number) => n.toString().padStart(3, '0');
    const expected = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ` +
      `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}.${pad3(d.getMilliseconds())}`;
    expect(formatDateTime(input)).toBe(expected);
  });

  it('should return N/A for null', () => {
    expect(formatDateTime(null)).toBe('N/A');
  });

  it('should return N/A for undefined', () => {
    expect(formatDateTime(undefined)).toBe('N/A');
  });
});
