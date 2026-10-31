import { cloneElement, isValidElement, useId } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, ChevronLeft, ChevronRight, Inbox, Loader2 } from 'lucide-react';

/** Blocking loading indicator. */
export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="flex items-center justify-center gap-2 py-10 text-slate-500 dark:text-slate-400" role="status">
      <Loader2 className="animate-spin" size={18} />
      <span className="text-sm">{label}</span>
    </div>
  );
}

/** Friendly empty state with an optional call to action. */
export function EmptyState({ title = 'Nothing here yet', description, action }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-6 py-12 text-center dark:border-slate-700 dark:bg-slate-900/40">
      <Inbox className="text-slate-400" size={28} />
      <p className="font-medium text-slate-600 dark:text-slate-300">{title}</p>
      {description && <p className="max-w-md text-sm text-slate-500 dark:text-slate-400">{description}</p>}
      {action}
    </div>
  );
}

/** Inline API error banner. */
export function ErrorBanner({ message, onRetry }) {
  if (!message) return null;
  return (
    <div className="mb-4 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950 dark:text-rose-300" role="alert">
      <AlertCircle size={16} className="mt-0.5 shrink-0" />
      <span className="flex-1">{message}</span>
      {onRetry && (
        <button type="button" onClick={onRetry} className="font-semibold underline">
          Retry
        </button>
      )}
    </div>
  );
}

const STATUS_STYLES = {
  PENDING: 'bg-amber-100 text-amber-800 ring-amber-200 dark:bg-amber-950 dark:text-amber-300 dark:ring-amber-900',
  APPROVED: 'bg-emerald-100 text-emerald-800 ring-emerald-200 dark:bg-emerald-950 dark:text-emerald-300 dark:ring-emerald-900',
  REJECTED: 'bg-rose-100 text-rose-800 ring-rose-200 dark:bg-rose-950 dark:text-rose-300 dark:ring-rose-900',
  CANCELLED: 'bg-slate-100 text-slate-600 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700',
};

/** Pill badge used for leave status everywhere in the app. */
export function StatusBadge({ status }) {
  const key = String(status || '').toUpperCase();
  const style = STATUS_STYLES[key] || STATUS_STYLES.CANCELLED;
  return (
    <span
      data-testid={`status-${key}`}
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${style}`}
    >
      {key}
    </span>
  );
}

/** KPI tile for dashboards. Always fed with real API values. */
export function StatCard({ label, value, hint, icon: Icon, tone = 'navy' }) {
  const tones = {
    navy: 'bg-navy-700 text-white',
    amber: 'bg-amber-500 text-white',
    emerald: 'bg-emerald-600 text-white',
    rose: 'bg-rose-600 text-white',
    slate: 'bg-slate-700 text-white',
  };
  return (
    <div className="card flex items-start gap-3 p-4" data-testid="stat-card">
      {Icon && (
        <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${tones[tone] || tones.navy}`}>
          <Icon size={18} />
        </div>
      )}
      <div className="min-w-0">
        <p className="truncate text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">{label}</p>
        <p className="text-2xl font-semibold text-slate-800 dark:text-slate-100" data-testid={`stat-${label}`}>
          {value}
        </p>
        {hint && <p className="truncate text-xs text-slate-400">{hint}</p>}
      </div>
    </div>
  );
}

/** Page heading with optional primary action. */
export function PageHeader({ title, subtitle, action }) {
  return (
    <div className="mb-5 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 className="text-xl font-semibold text-slate-800 dark:text-slate-100">{title}</h1>
        {subtitle && <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

/** Modal confirmation used before destructive / irreversible actions. */
export function ConfirmDialog({ open, title, description, confirmLabel = 'Confirm', busy, onConfirm, onCancel, children }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-900/50 px-4" role="dialog" aria-modal="true">
      <div className="card w-full max-w-md p-5">
        <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">{title}</h2>
        {description && <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{description}</p>}
        {children && <div className="mt-3">{children}</div>}
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={onCancel}>
            Cancel
          </button>
          <button type="button" className="btn-primary" onClick={onConfirm} disabled={busy}>
            {busy ? 'Working…' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Label + control + validation message. The control is wired to the label
 * with a generated id so screen readers (and testing-library getByLabelText)
 * find the pair.
 */
export function FormField({ label, error, required, icon, children }) {
  const id = useId();
  const errorId = `${id}-error`;
  const isControl =
    isValidElement(children) && ['input', 'select', 'textarea'].includes(children.type);

  const control = isControl
    ? cloneElement(children, {
        id,
        'aria-invalid': error ? 'true' : undefined,
        'aria-describedby': error ? errorId : undefined,
      })
    : children;

  return (
    <div>
      <label htmlFor={id} className="label">
        {label}
        {required && <span className="text-rose-500"> *</span>}
      </label>
      {icon ? (
        <div className="relative">
          <span className="pointer-events-none absolute left-3 top-2.5 text-slate-400">{icon}</span>
          {control}
        </div>
      ) : (
        control
      )}
      {error && (
        <p id={errorId} className="mt-1 text-xs text-rose-600 dark:text-rose-400" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

/** Simple page-size aware pagination control. */
export function Pagination({ page, totalPages, onChange, size = 10 }) {
  if (!totalPages || totalPages <= 1) return null;
  const from = page * size + 1;
  return (
    <div className="mt-4 flex items-center justify-between text-sm text-slate-500 dark:text-slate-400">
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <div className="flex gap-2">
        <button
          type="button"
          className="btn-secondary"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
          aria-label="Previous page"
        >
          <ChevronLeft size={16} /> Prev
        </button>
        <button
          type="button"
          className="btn-secondary"
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
          aria-label="Next page"
        >
          Next <ChevronRight size={16} />
        </button>
      </div>
      <span className="hidden sm:inline">Showing {from}–{from}</span>
    </div>
  );
}

/** Renders a list of error messages returned by the backend. */
export function FieldErrors({ errors, fields }) {
  if (!errors) return null;
  return (
    <ul className="mt-2 space-y-1 text-xs text-rose-600 dark:text-rose-400">
      {fields
        .filter((field) => errors[field])
        .map((field) => (
          <li key={field}>{errors[field].message}</li>
        ))}
    </ul>
  );
}

export { Link };
