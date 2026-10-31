import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  CalendarCheck,
  CheckCircle2,
  Filter,
  Hourglass,
  Search,
  ThumbsDown,
  ThumbsUp,
  Users,
  XCircle,
} from 'lucide-react';
import api, { errorMessage } from '../services/api.js';
import { useToast } from '../context/ToastContext.jsx';
import {
  ConfirmDialog,
  EmptyState,
  ErrorBanner,
  FormField,
  PageHeader,
  Pagination,
  Spinner,
  StatCard,
  StatusBadge,
} from '../components/ui.jsx';
import { formatDateRange, formatDays } from '../utils/format.js';

/* --------------------------------------------------------------- dashboard */
export function ManagerDashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    api
      .get('/manager/dashboard')
      .then((response) => {
        setData(response.data);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  if (loading) return <Spinner label="Loading team data…" />;

  return (
    <>
      <PageHeader
        title="Manager dashboard"
        subtitle="Leave requests from the employees assigned to you."
        action={
          <Link to="/manager/pending" className="btn-primary">
            Review pending requests
          </Link>
        }
      />
      <ErrorBanner message={error} onRetry={load} />

      {data && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
            <StatCard label="Team Members" value={data.teamSize} icon={Users} tone="navy" />
            <StatCard label="Pending" value={data.pendingCount} icon={Hourglass} tone="amber" />
            <StatCard label="Approved" value={data.approvedCount} icon={CheckCircle2} tone="emerald" />
            <StatCard label="Rejected" value={data.rejectedCount} icon={XCircle} tone="rose" />
            <StatCard label="On Leave Today" value={data.onLeaveToday} icon={CalendarCheck} tone="slate" />
          </div>

          <div className="mt-6 grid gap-4 lg:grid-cols-3">
            <div className="card p-4 lg:col-span-2">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="font-semibold text-slate-700 dark:text-slate-200">Recent team requests</h2>
                <Link to="/manager/requests" className="text-sm font-medium text-navy-700 hover:underline dark:text-navy-500">
                  View all
                </Link>
              </div>
              {data.recentApplications.length === 0 ? (
                <EmptyState title="No requests yet" description="Requests from your team will appear here." />
              ) : (
                <div className="overflow-x-auto">
                  <table className="table-base">
                    <thead>
                      <tr>
                        <th>Employee</th>
                        <th>Leave</th>
                        <th>Dates</th>
                        <th>Days</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.recentApplications.map((application) => (
                        <tr key={application.id}>
                          <td className="font-medium">{application.employee?.fullName}</td>
                          <td>{application.leaveType?.leaveName}</td>
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
              <h2 className="mb-3 font-semibold text-slate-700 dark:text-slate-200">Team overview</h2>
              <ul className="space-y-2">
                {data.team.map((member) => (
                  <li key={member.employee.id} className="rounded-lg border border-slate-200 p-3 dark:border-slate-800">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium">{member.employee.fullName}</span>
                      <span className="text-xs text-slate-400">{member.employee.departmentName}</span>
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      {member.pendingCount} pending · {member.approvedCount} approved · {member.rejectedCount} rejected
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

/* -------------------------------------------------- shared request table */
function RequestsTable({ status, showFilters = true, title, subtitle }) {
  const toast = useToast();
  const [requests, setRequests] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [filter, setFilter] = useState(status || 'ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [decision, setDecision] = useState(null);
  const [remarks, setRemarks] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    const params = new URLSearchParams({ page: String(page), size: '10' });
    if (filter && filter !== 'ALL') params.set('status', filter);
    api
      .get(`/manager/requests?${params.toString()}`)
      .then((response) => {
        setRequests(response.data.content);
        setTotalPages(response.data.totalPages);
        setTotalElements(response.data.totalElements);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, [page, filter]);

  useEffect(load, [load]);

  const decide = async (action) => {
    if (!decision) return;
    setBusy(true);
    try {
      await api.put(`/manager/requests/${decision.id}/${action}`, { remarks });
      toast.success(action === 'approve' ? 'Leave request approved' : 'Leave request rejected');
      setDecision(null);
      setRemarks('');
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
        title={title}
        subtitle={subtitle}
        action={
          showFilters ? (
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <Filter size={15} />
              <select
                className="input !w-44"
                value={filter}
                onChange={(event) => {
                  setFilter(event.target.value);
                  setPage(0);
                }}
                aria-label="Filter by status"
              >
                {['ALL', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </div>
          ) : null
        }
      />

      <ErrorBanner message={error} onRetry={load} />

      {loading ? (
        <Spinner />
      ) : requests.length === 0 ? (
        <EmptyState title="No requests found" description="There is nothing to review with the current filter." />
      ) : (
        <>
          <div className="card overflow-x-auto p-1">
            <table className="table-base">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Employee</th>
                  <th>Department</th>
                  <th>Leave</th>
                  <th>Dates</th>
                  <th>Days</th>
                  <th>Reason</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {requests.map((application) => (
                  <tr key={application.id}>
                    <td className="font-semibold text-slate-500">#{application.id}</td>
                    <td className="font-medium">{application.employee?.fullName}</td>
                    <td className="text-slate-500">{application.employee?.departmentName}</td>
                    <td>{application.leaveType?.leaveName}</td>
                    <td>{formatDateRange(application.startDate, application.endDate)}</td>
                    <td>{formatDays(application.totalDays)}</td>
                    <td className="max-w-52 truncate text-slate-500" title={application.reason}>
                      {application.reason}
                    </td>
                    <td>
                      <StatusBadge status={application.status} />
                    </td>
                    <td className="whitespace-nowrap text-right">
                      {application.status === 'PENDING' ? (
                        <div className="flex justify-end gap-1.5">
                          <button
                            type="button"
                            className="btn-primary !px-2.5 !py-1 text-xs"
                            onClick={() => {
                              setDecision(application);
                              setRemarks('');
                            }}
                            data-testid={`approve-${application.id}`}
                          >
                            <ThumbsUp size={13} /> Approve
                          </button>
                          <button
                            type="button"
                            className="btn-danger !px-2.5 !py-1 text-xs"
                            onClick={() => {
                              setDecision(application);
                              setRemarks('');
                            }}
                            data-testid={`reject-${application.id}`}
                          >
                            <ThumbsDown size={13} /> Reject
                          </button>
                        </div>
                      ) : (
                        <span className="text-xs text-slate-400">
                          {application.approvedByName ? `by ${application.approvedByName}` : ''}
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          <p className="mt-2 text-xs text-slate-400">{totalElements} request(s) in total</p>
        </>
      )}

      <ConfirmDialog
        open={Boolean(decision)}
        title="Process leave request"
        description={
          decision
            ? `${decision.employee?.fullName} · ${decision.leaveType?.leaveName} · ${formatDateRange(
                decision.startDate,
                decision.endDate
              )} (${formatDays(decision.totalDays)} day(s))`
            : ''
        }
        confirmLabel="Approve"
        busy={busy}
        onConfirm={() => decide('approve')}
        onCancel={() => setDecision(null)}
      >
        <FormField label="Remarks (required when rejecting)">
          <textarea
            className="input min-h-20"
            value={remarks}
            onChange={(event) => setRemarks(event.target.value)}
            placeholder="Optional for approval, mandatory for rejection"
          />
        </FormField>
        <button type="button" className="btn-danger mt-3 w-full" onClick={() => decide('reject')} disabled={busy}>
          <ThumbsDown size={15} /> Reject with these remarks
        </button>
      </ConfirmDialog>
    </>
  );
}

/* ------------------------------------------------------ pending approvals */
export function PendingApprovals() {
  return (
    <RequestsTable
      status="PENDING"
      title="Pending approvals"
      subtitle="Approve or reject requests from your team. Rejection requires remarks."
    />
  );
}

/* ---------------------------------------------------- all team requests */
export function AllTeamRequests() {
  return (
    <RequestsTable
      title="All team requests"
      subtitle="Complete history of leave requests from your team, filterable by status."
    />
  );
}

/* ---------------------------------------------------------- team overview */
export function TeamOverview() {
  const [team, setTeam] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');

  useEffect(() => {
    api
      .get('/manager/team')
      .then((response) => {
        setTeam(response.data);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(
    () =>
      team.filter((member) =>
        `${member.employee.fullName} ${member.employee.departmentName} ${member.employee.designation}`
          .toLowerCase()
          .includes(query.toLowerCase())
      ),
    [team, query]
  );

  return (
    <>
      <PageHeader title="Team overview" subtitle="Leave activity per employee in your team." />

      <div className="mb-4 flex items-center gap-2">
        <Search size={16} className="text-slate-400" />
        <input
          className="input max-w-xs"
          placeholder="Search a team member"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          aria-label="Search team members"
        />
      </div>

      <ErrorBanner message={error} />

      {loading ? (
        <Spinner />
      ) : filtered.length === 0 ? (
        <EmptyState title="No team members found" />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {filtered.map((member) => (
            <div key={member.employee.id} className="card p-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-semibold text-slate-800 dark:text-slate-100">{member.employee.fullName}</p>
                  <p className="text-xs text-slate-500">
                    {member.employee.designation} · {member.employee.departmentName}
                  </p>
                </div>
                <span className="text-xs font-medium text-slate-400">{member.employee.employeeId}</span>
              </div>
              <div className="mt-3 grid grid-cols-3 gap-2 text-center">
                <div className="rounded-lg bg-amber-50 py-2 dark:bg-amber-950">
                  <p className="text-lg font-semibold text-amber-700 dark:text-amber-300">{member.pendingCount}</p>
                  <p className="text-[11px] uppercase text-amber-600">Pending</p>
                </div>
                <div className="rounded-lg bg-emerald-50 py-2 dark:bg-emerald-950">
                  <p className="text-lg font-semibold text-emerald-700 dark:text-emerald-300">{member.approvedCount}</p>
                  <p className="text-[11px] uppercase text-emerald-600">Approved</p>
                </div>
                <div className="rounded-lg bg-rose-50 py-2 dark:bg-rose-950">
                  <p className="text-lg font-semibold text-rose-700 dark:text-rose-300">{member.rejectedCount}</p>
                  <p className="text-[11px] uppercase text-rose-600">Rejected</p>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
