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

  // Format in UTC so the rendered value matches the ISO-8601 input verbatim.
  const year = d.getUTCFullYear();
  const month = pad2(d.getUTCMonth() + 1);
  const day = pad2(d.getUTCDate());
  const hours = pad2(d.getUTCHours());
  const minutes = pad2(d.getUTCMinutes());
  const seconds = pad2(d.getUTCSeconds());
  const millis = pad3(d.getUTCMilliseconds());

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}.${millis}`;
}
