/**
 * Formats an ISO-8601 date/time string as "YYYY-MM-DD HH:mm:ss.SSS".
 * Returns "N/A" when the input is null or undefined.
 */
export function formatDateTime(date: string | null | undefined): string {
  if (!date) {
    return 'N/A';
  }
  const d = new Date(date);
  const pad2 = (n: number) => n.toString().padStart(2, '0');
  const pad3 = (n: number) => n.toString().padStart(3, '0');

  // Format in the browser's local time zone.
  const year = d.getFullYear();
  const month = pad2(d.getMonth() + 1);
  const day = pad2(d.getDate());
  const hours = pad2(d.getHours());
  const minutes = pad2(d.getMinutes());
  const seconds = pad2(d.getSeconds());
  const millis = pad3(d.getMilliseconds());

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}.${millis}`;
}

/**
 * Formats an ISO-8601 date string as "YYYY-MM-DD".
 * Returns "N/A" when the input is null or undefined.
 */
export function formatDate(date: string | null | undefined): string {
  if (!date) {
    return 'N/A';
  }
  const d = new Date(date);
  const pad2 = (n: number) => n.toString().padStart(2, '0');

  const year = d.getUTCFullYear();
  const month = pad2(d.getUTCMonth() + 1);
  const day = pad2(d.getUTCDate());

  return `${year}-${month}-${day}`;
}
