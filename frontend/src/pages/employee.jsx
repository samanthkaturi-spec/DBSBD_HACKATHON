import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import {
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  Clock,
  Send,
  Wallet,
} from 'lucide-react';
import api, { errorMessage } from '../services/api.js';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import {
  ConfirmDialog,
  EmptyState,
  ErrorBanner,
  FormField,
  PageHeader,
  Spinner,
  StatCard,
  StatusBadge,
} from '../components/ui.jsx';
import { clientWorkingDays, formatDate, formatDateRange, formatDateTime, formatDays, todayIso } from '../utils/format.js';

/* ------------------------------------------------------------- dashboard */
export function EmployeeDashboard() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    api
      .get('/employee/dashboard')
      .then((response) => {
        setData(response.data);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  if (loading) return <Spinner label="Loading your dashboard…" />;

  return (
    <>
      <PageHeader
        title={`Welcome back, ${user?.fullName?.split(' ')[0] || 'there'}`}
        subtitle="Your leave balance and application activity at a glance."
        action={
          <Link to="/leave/apply" className="btn-primary">
            Apply for Leave <ArrowRight size={16} />
          </Link>
        }
      />

      <ErrorBanner message={error} onRetry={load} />

      {data && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Annual Entitlement" value={formatDays(data.totalEntitlementDays)} icon={CalendarDays} tone="navy" hint="days configured for this year" />
            <StatCard label="Available Balance" value={formatDays(data.totalAvailableDays)} icon={Wallet} tone="emerald" hint="across all leave types" />
            <StatCard label="Used Leave" value={formatDays(data.totalUsedDays)} icon={CheckCircle2} tone="amber" hint="days approved so far" />
            <StatCard label="Pending Requests" value={data.pendingCount} icon={Clock} tone="rose" hint="awaiting manager decision" />
          </div>

          <div className="mt-6 grid gap-4 lg:grid-cols-3">
            <div className="card p-4 lg:col-span-2">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="font-semibold text-slate-700 dark:text-slate-200">Recent applications</h2>
                <Link to="/leave/applications" className="text-sm font-medium text-navy-700 hover:underline dark:text-navy-500">
                  View all
                </Link>
              </div>

              {data.recentApplications.length === 0 ? (
                <EmptyState
                  title="No leave applications yet"
                  description="Submit your first request and it will show up here."
                  action={
                    <Link to="/leave/apply" className="btn-primary mt-2">
                      Apply now
                    </Link>
                  }
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="table-base">
                    <thead>
                      <tr>
                        <th>Leave type</th>
                        <th>Dates</th>
                        <th>Days</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.recentApplications.map((application) => (
                        <tr key={application.id}>
                          <td className="font-medium">{application.leaveType?.leaveName}</td>
                          <td>{formatDateRange(application.startDate, application.endDate)}</td>
                          <td>{formatDays(application.totalDays)}</td>
                          <td>
                            <StatusBadge status={application.status} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div className="card p-4">
              <h2 className="mb-3 font-semibold text-slate-700 dark:text-slate-200">Leave balances</h2>
              <ul className="space-y-3">
                {data.balances.map((balance) => (
                  <li key={balance.id} className="rounded-lg border border-slate-200 p-3 dark:border-slate-800">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium">{balance.leaveName}</span>
                      <span className="text-sm font-semibold text-navy-700 dark:text-navy-500">
                        {formatDays(balance.availableDays)}d
                      </span>
                    </div>
                    <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                      <div
                        className="h-full rounded-full bg-navy-600"
                        style={{
                          width: `${
                            Number(balance.totalDays) > 0
                              ? Math.min(100, (Number(balance.usedDays) / Number(balance.totalDays)) * 100)
                              : 0
                          }%`,
                        }}
                      />
                    </div>
                    <p className="mt-1 text-xs text-slate-400">
                      {formatDays(balance.usedDays)} used of {formatDays(balance.totalDays)}
                    </p>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </>
      )}
    </>
  );
}

/* ------------------------------------------------------------ apply page */
export function ApplyLeave() {
  const toast = useToast();
  const [leaveTypes, setLeaveTypes] = useState([]);
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm({ defaultValues: { leaveTypeId: '', startDate: todayIso(), endDate: todayIso(), reason: '' } });

  useEffect(() => {
    api
      .get('/leave-types')
      .then((response) => setLeaveTypes(response.data))
      .catch((error) => setServerError(errorMessage(error)));
  }, []);

  const startDate = watch('startDate');
  const endDate = watch('endDate');
  const previewDays = clientWorkingDays(startDate, endDate);

  const onSubmit = async (values) => {
    setSubmitting(true);
    setServerError('');
    try {
      await api.post('/leaves/apply', {
        leaveTypeId: Number(values.leaveTypeId),
        startDate: values.startDate,
        endDate: values.endDate,
        reason: values.reason.trim(),
      });
      toast.success('Leave request submitted for manager approval');
      setDone(true);
      reset({ leaveTypeId: '', startDate: todayIso(), endDate: todayIso(), reason: '' });
    } catch (error) {
      setServerError(errorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Apply for leave"
        subtitle="Weekends (Saturday and Sunday) are excluded automatically. Public holidays are not configured in this MVP."
      />

      <ErrorBanner message={serverError} />

      {done && (
        <div className="mb-4 flex items-center justify-between rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
          <span>Your request was saved as PENDING. Track it under My Applications.</span>
          <Link to="/leave/applications" className="font-semibold underline">
            Open
          </Link>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} noValidate className="card max-w-2xl p-5">
        <FormField label="Leave category" required error={errors.leaveTypeId?.message}>
          <select className="input" {...register('leaveTypeId', { required: 'Choose a leave category' })}>
            <option value="">Select a category</option>
            {leaveTypes.map((type) => (
              <option key={type.id} value={type.id}>
                {type.leaveName} ({formatDays(type.annualEntitlement)} days/year)
              </option>
            ))}
          </select>
        </FormField>

        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <FormField label="Start date" required error={errors.startDate?.message}>
            <input type="date" className="input" min={todayIso()} {...register('startDate', { required: 'Start date is required' })} />
          </FormField>
          <FormField label="End date" required error={errors.endDate?.message}>
            <input type="date" className="input" min={startDate || todayIso()} {...register('endDate', { required: 'End date is required' })} />
          </FormField>
        </div>

        <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600 dark:bg-slate-800/60 dark:text-slate-300">
          Requested duration:{' '}
          <span className="font-semibold" data-testid="duration-preview">
            {formatDays(previewDays)} working day{previewDays === 1 ? '' : 's'}
          </span>
        </div>

        <div className="mt-4">
          <FormField label="Reason" required error={errors.reason?.message}>
            <textarea
              className="input min-h-24"
              placeholder="Describe why you need the leave (at least 5 characters)"
              {...register('reason', {
                required: 'Reason is required',
                minLength: { value: 5, message: 'Reason must be at least 5 characters' },
                maxLength: { value: 500, message: 'Reason must be at most 500 characters' },
              })}
            />
          </FormField>
        </div>

        <div className="mt-5 flex gap-2">
          <button type="submit" className="btn-primary" disabled={submitting}>
            <Send size={16} />
            {submitting ? 'Submitting…' : 'Submit request'}
          </button>
          <Link to="/leave/applications" className="btn-secondary">
            Cancel
          </Link>
        </div>
      </form>
    </>
  );
}

/* -------------------------------------------------------- my applications */
export function MyApplications() {
  const toast = useToast();
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [cancelling, setCancelling] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    api
      .get('/leaves/my-requests')
      .then((response) => {
        setApplications(response.data);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  const filtered = useMemo(
    () => (filter === 'ALL' ? applications : applications.filter((item) => item.status === filter)),
    [applications, filter]
  );

  const cancel = async () => {
    if (!cancelling) return;
    setBusy(true);
    try {
      await api.put(`/leaves/${cancelling.id}/cancel`, { remarks: 'Cancelled from the portal' });
      toast.success('Leave request cancelled');
      setCancelling(null);
      load();
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title="My applications"
        subtitle="Every leave request you have submitted, with its current status."
        action={
          <Link to="/leave/apply" className="btn-primary">
            New request
          </Link>
        }
      />

      <ErrorBanner message={error} onRetry={load} />

      <div className="mb-4 flex flex-wrap gap-2">
        {['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].map((status) => (
          <button
            key={status}
            type="button"
            onClick={() => setFilter(status)}
            className={`rounded-full px-3 py-1 text-sm font-medium transition ${
              filter === status
                ? 'bg-navy-700 text-white'
                : 'border border-slate-300 text-slate-600 hover:bg-white dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800'
            }`}
          >
            {status}
          </button>
        ))}
      </div>

      {loading ? (
        <Spinner />
      ) : filtered.length === 0 ? (
        <EmptyState title="No applications in this view" description="Change the filter or submit a new leave request." />
      ) : (
        <div className="card overflow-x-auto p-1">
          <table className="table-base">
            <thead>
              <tr>
                <th>#</th>
                <th>Leave type</th>
                <th>Dates</th>
                <th>Days</th>
                <th>Reason</th>
                <th>Status</th>
                <th>Decision</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {filtered.map((application) => (
                <tr key={application.id}>
                  <td className="font-semibold text-slate-500">#{application.id}</td>
                  <td className="font-medium">{application.leaveType?.leaveName}</td>
                  <td>{formatDateRange(application.startDate, application.endDate)}</td>
                  <td>{formatDays(application.totalDays)}</td>
                  <td className="max-w-56 truncate text-slate-500" title={application.reason}>
                    {application.reason}
                  </td>
                  <td>
                    <StatusBadge status={application.status} />
                  </td>
                  <td className="text-xs text-slate-500">
                    {application.managerRemarks ? application.managerRemarks : '—'}
                    {application.approvedByName && (
                      <span className="block text-slate-400">by {application.approvedByName}</span>
                    )}
                  </td>
                  <td className="text-right">
                    {(application.status === 'PENDING' || application.status === 'APPROVED') && (
                      <button
                        type="button"
                        className="btn-secondary !px-2 !py-1 text-xs"
                        onClick={() => setCancelling(application)}
                      >
                        Cancel
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(cancelling)}
        title="Cancel this leave request?"
        description={
          cancelling?.status === 'APPROVED'
            ? 'This request is already approved - the deducted balance will be restored if the leave has not started.'
            : 'The request will be marked as CANCELLED and your manager will be notified.'
        }
        confirmLabel="Cancel request"
        busy={busy}
        onConfirm={cancel}
        onCancel={() => setCancelling(null)}
      />
    </>
  );
}

/* ----------------------------------------------------------- leave balance */
export function LeaveBalancePage() {
  const [balances, setBalances] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api
      .get('/employee/leave-balance')
      .then((response) => {
        setBalances(response.data);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  const total = balances.reduce((sum, item) => sum + Number(item.totalDays), 0);
  const used = balances.reduce((sum, item) => sum + Number(item.usedDays), 0);
  const available = balances.reduce((sum, item) => sum + Number(item.availableDays), 0);

  return (
    <>
      <PageHeader title="Leave balance" subtitle="Entitlement, usage and available days per leave category." />
      <ErrorBanner message={error} />

      <div className="mb-5 grid gap-4 sm:grid-cols-3">
        <StatCard label="Total Entitlement" value={formatDays(total)} icon={CalendarDays} />
        <StatCard label="Used" value={formatDays(used)} icon={CheckCircle2} tone="amber" />
        <StatCard label="Available" value={formatDays(available)} icon={Wallet} tone="emerald" />
      </div>

      {loading ? (
        <Spinner />
      ) : (
        <div className="card overflow-x-auto">
          <table className="table-base">
            <thead>
              <tr>
                <th>Leave type</th>
                <th>Year</th>
                <th>Total</th>
                <th>Used</th>
                <th>Available</th>
                <th>Utilisation</th>
              </tr>
            </thead>
            <tbody>
              {balances.map((balance) => {
                const percent = Number(balance.totalDays) > 0
                  ? Math.round((Number(balance.usedDays) / Number(balance.totalDays)) * 100)
                  : 0;
                return (
                  <tr key={balance.id}>
                    <td className="font-medium">{balance.leaveName}</td>
                    <td>{balance.year}</td>
                    <td>{formatDays(balance.totalDays)}</td>
                    <td>{formatDays(balance.usedDays)}</td>
                    <td className="font-semibold">{formatDays(balance.availableDays)}</td>
                    <td className="w-56">
                      <div className="flex items-center gap-2">
                        <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                          <div className="h-full rounded-full bg-navy-600" style={{ width: `${percent}%` }} />
                        </div>
                        <span className="text-xs text-slate-500">{percent}%</span>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

/* ---------------------------------------------------------------- profile */
export function Profile() {
  const { user } = useAuth();
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api
      .get('/employee/profile')
      .then((response) => setProfile(response.data))
      .catch((err) => setError(errorMessage(err)));
  }, []);

  if (error) return <ErrorBanner message={error} />;
  const data = profile || user;
  if (!data) return <Spinner />;

  const rows = [
    ['Employee ID', data.employeeId],
    ['Full name', data.fullName],
    ['Email', data.email],
    ['Role', data.role],
    ['Designation', data.designation],
    ['Department', data.departmentName],
    ['Reporting manager', data.managerName || 'Not assigned'],
    ['Account status', data.active ? 'Active' : 'Deactivated'],
  ];

  return (
    <>
      <PageHeader title="My profile" subtitle="Details maintained by HR for your account." />
      <div className="card max-w-2xl p-5">
        <dl className="divide-y divide-slate-100 dark:divide-slate-800">
          {rows.map(([label, value]) => (
            <div key={label} className="grid grid-cols-1 gap-1 py-3 sm:grid-cols-3">
              <dt className="text-sm font-medium text-slate-500">{label}</dt>
              <dd className="text-sm text-slate-800 dark:text-slate-100 sm:col-span-2">{value || '—'}</dd>
            </div>
          ))}
        </dl>
      </div>
    </>
  );
}

/* ---------------------------------------------------------- notifications */
export function Notifications() {
  const toast = useToast();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    api
      .get('/notifications')
      .then((response) => {
        setItems(response.data);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  const markAllRead = async () => {
    try {
      await api.post('/notifications/read-all');
      toast.success('All notifications marked as read');
      load();
    } catch (error) {
      toast.error(errorMessage(error));
    }
  };

  const unread = items.filter((item) => !item.read).length;

  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle={`${unread} unread message${unread === 1 ? '' : 's'}`}
        action={
          <button type="button" className="btn-secondary" onClick={markAllRead} disabled={unread === 0}>
            Mark all as read
          </button>
        }
      />

      <ErrorBanner message={error} onRetry={load} />

      {loading ? (
        <Spinner />
      ) : items.length === 0 ? (
        <EmptyState title="No notifications" description="Leave decisions and reminders will appear here." />
      ) : (
        <ul className="space-y-2">
          {items.map((item) => (
            <li
              key={item.id}
              className={`card flex items-start gap-3 p-3 ${item.read ? 'opacity-70' : ''}`}
            >
              <span className={`mt-1 h-2 w-2 shrink-0 rounded-full ${item.read ? 'bg-slate-300' : 'bg-navy-600'}`} />
              <div className="flex-1">
                <p className="text-sm text-slate-700 dark:text-slate-200">{item.message}</p>
                <p className="mt-0.5 text-xs text-slate-400">{formatDateTime(item.createdAt)}</p>
              </div>
              {!item.read && (
                <span className="rounded-full bg-navy-50 px-2 py-0.5 text-xs font-semibold text-navy-700 dark:bg-navy-900 dark:text-navy-100">
                  New
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
