/** Formatting helpers shared across pages. */

export function formatDate(value) {
  if (!value) return '—';
  const date = typeof value === 'string' ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
}

export function formatDateTime(value) {
  if (!value) return '—';
  const date = typeof value === 'string' ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Backend returns BigDecimal days (3 or 3.0) - render them cleanly. */
export function formatDays(value) {
  if (value === null || value === undefined || value === '') return '0';
  const number = Number(value);
  if (Number.isNaN(number)) return value;
  return Number.isInteger(number) ? String(number) : number.toFixed(1);
}

export function initials(name = '') {
  return name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}

export function formatDateRange(start, end) {
  if (!start || !end) return '—';
  return start === end ? formatDate(start) : `${formatDate(start)} → ${formatDate(end)}`;
}

/** Working days between two ISO dates, Monday-Friday, mirroring the backend rule. */
export function clientWorkingDays(start, end) {
  if (!start || !end) return 0;
  const from = new Date(`${start}T00:00:00`);
  const to = new Date(`${end}T00:00:00`);
  if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime()) || to < from) return 0;
  let days = 0;
  for (let day = new Date(from); day <= to; day.setDate(day.getDate() + 1)) {
    const weekday = day.getDay();
    if (weekday !== 0 && weekday !== 6) days += 1;
  }
  return days;
}

export function todayIso() {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}
