import { useCallback, useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  BarChart3,
  Building2,
  CheckCircle2,
  ClipboardList,
  Download,
  Pencil,
  Plus,
  Search,
  Trash2,
  UserPlus,
  Users,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
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
import { formatDateRange, formatDateTime, formatDays } from '../utils/format.js';

/* -------------------------------------------------------------- dashboard */
export function AdminDashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const load = useCallback(() => {
    setLoading(true);
    api
      .get('/admin/dashboard')
      .then((response) => {
        setData(response.data);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  if (loading) return <Spinner label="Loading HR statistics…" />;

  return (
    <>
      <PageHeader title="HR dashboard" subtitle="Organisation-wide leave administration." />
      <ErrorBanner message={error} onRetry={load} />
      {data && (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard label="Total Employees" value={data.totalEmployees} icon={Users} hint={`${data.activeEmployees} active`} />
          <StatCard label="Departments" value={data.totalDepartments} icon={Building2} tone="slate" />
          <StatCard label="Total Applications" value={data.totalApplications} icon={ClipboardList} tone="amber" />
          <StatCard label="Pending" value={data.pendingCount} icon={BarChart3} tone="rose" />
          <StatCard label="Approved" value={data.approvedCount} icon={CheckCircle2} tone="emerald" />
          <StatCard label="Rejected" value={data.rejectedCount} icon={ClipboardList} tone="rose" />
        </div>
      )}
    </>
  );
}

/* ---------------------------------------------------- employee management */
export function EmployeeManagement() {
  const toast = useToast();
  const [employees, setEmployees] = useState([]);
  const [departments, setDepartments] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [query, setQuery] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editor, setEditor] = useState(null); // null | 'create' | employee object
  const [deleting, setDeleting] = useState(null);
  const [busy, setBusy] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm();

  const load = useCallback(() => {
    setLoading(true);
    const params = new URLSearchParams({ page: String(page), size: '10' });
    if (query.trim()) params.set('q', query.trim());
    if (departmentId) params.set('departmentId', departmentId);
    api
      .get(`/admin/employees?${params.toString()}`)
      .then((response) => {
        setEmployees(response.data.content);
        setTotalPages(response.data.totalPages);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, [page, query, departmentId]);

  useEffect(load, [load]);

  useEffect(() => {
    api
      .get('/admin/departments')
      .then((response) => setDepartments(response.data))
      .catch(() => setDepartments([]));
  }, []);

  const openCreate = () => {
    reset({ fullName: '', email: '', password: '', departmentId: '', managerId: '', designation: '', role: 'EMPLOYEE' });
    setEditor('create');
  };

  const openEdit = (employee) => {
    reset({
      fullName: employee.fullName,
      email: employee.email,
      password: '',
      departmentId: employee.departmentId,
      managerId: '',
      designation: employee.designation,
      role: employee.role,
    });
    setEditor(employee);
  };

  const onSubmit = async (values) => {
    setBusy(true);
    const payload = {
      fullName: values.fullName,
      email: values.email,
      designation: values.designation,
      role: values.role || 'EMPLOYEE',
      departmentId: Number(values.departmentId),
      managerId: values.managerId ? Number(values.managerId) : undefined,
      password: values.password || undefined,
    };
    try {
      if (editor === 'create') {
        await api.post('/admin/employees', payload);
        toast.success('Employee created');
      } else {
        await api.put(`/admin/employees/${editor.id}`, payload);
        toast.success('Employee updated');
      }
      setEditor(null);
      load();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const deactivate = async () => {
    if (!deleting) return;
    setBusy(true);
    try {
      await api.delete(`/admin/employees/${deleting.id}`);
      toast.success(`${deleting.fullName} deactivated`);
      setDeleting(null);
      load();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Employee management"
        subtitle="Add, edit and deactivate employee records."
        action={
          <button type="button" className="btn-primary" onClick={openCreate}>
            <UserPlus size={16} /> Add employee
          </button>
        }
      />

      <div className="mb-4 flex flex-col gap-2 sm:flex-row">
        <div className="relative flex-1">
          <Search size={16} className="pointer-events-none absolute left-3 top-2.5 text-slate-400" />
          <input
            className="input pl-9"
            placeholder="Search by name, email or employee id"
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setPage(0);
            }}
            aria-label="Search employees"
          />
        </div>
        <select
          className="input sm:w-56"
          value={departmentId}
          onChange={(event) => {
            setDepartmentId(event.target.value);
            setPage(0);
          }}
          aria-label="Filter by department"
        >
          <option value="">All departments</option>
          {departments.map((department) => (
            <option key={department.id} value={department.id}>
              {department.departmentName}
            </option>
          ))}
        </select>
      </div>

      <ErrorBanner message={error} onRetry={load} />

      {loading ? (
        <Spinner />
      ) : employees.length === 0 ? (
        <EmptyState title="No employees found" description="Adjust the search or add a new employee." />
      ) : (
        <>
          <div className="card overflow-x-auto p-1">
            <table className="table-base">
              <thead>
                <tr>
                  <th>Employee</th>
                  <th>ID</th>
                  <th>Department</th>
                  <th>Designation</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {employees.map((employee) => (
                  <tr key={employee.id}>
                    <td>
                      <p className="font-medium">{employee.fullName}</p>
                      <p className="text-xs text-slate-400">{employee.email}</p>
                    </td>
                    <td className="text-slate-500">{employee.employeeId}</td>
                    <td>{employee.departmentName}</td>
                    <td className="text-slate-500">{employee.designation}</td>
                    <td>
                      <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs font-semibold dark:bg-slate-800">
                        {employee.role}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                          employee.active
                            ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300'
                            : 'bg-slate-200 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
                        }`}
                      >
                        {employee.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="whitespace-nowrap text-right">
                      <button type="button" className="btn-secondary !px-2 !py-1 text-xs" onClick={() => openEdit(employee)}>
                        <Pencil size={13} /> Edit
                      </button>{' '}
                      {employee.active && (
                        <button
                          type="button"
                          className="btn-danger !px-2 !py-1 text-xs"
                          onClick={() => setDeleting(employee)}
                        >
                          <Trash2 size={13} /> Deactivate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}

      {editor && (
        <div className="fixed inset-0 z-40 flex items-start justify-center overflow-y-auto bg-slate-900/50 px-4 py-10">
          <form onSubmit={handleSubmit(onSubmit)} className="card w-full max-w-lg p-5">
            <h2 className="text-lg font-semibold">{editor === 'create' ? 'Add employee' : 'Edit employee'}</h2>

            <div className="mt-4">
              <FormField label="Full name" required error={errors.fullName?.message}>
                <input className="input" {...register('fullName', { required: 'Full name is required' })} />
              </FormField>
            </div>
            <div className="mt-3">
              <FormField label="Email" required error={errors.email?.message}>
                <input
                  type="email"
                  className="input"
                  {...register('email', {
                    required: 'Email is required',
                    pattern: { value: /^\S+@\S+\.\S+$/, message: 'Enter a valid email' },
                  })}
                />
              </FormField>
            </div>
            <div className="mt-3">
              <FormField
                label={editor === 'create' ? 'Password' : 'New password (leave blank to keep)'}
                required={editor === 'create'}
                error={errors.password?.message}
              >
                <input
                  type="password"
                  className="input"
                  {...register('password', editor === 'create' ? { required: 'Password is required', minLength: { value: 8, message: 'Minimum 8 characters' } } : { minLength: { value: 8, message: 'Minimum 8 characters' } })}
                />
              </FormField>
            </div>

            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <FormField label="Department" required error={errors.departmentId?.message}>
                <select className="input" {...register('departmentId', { required: 'Choose a department' })}>
                  <option value="">Select</option>
                  {departments.map((department) => (
                    <option key={department.id} value={department.id}>
                      {department.departmentName}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="Designation" required error={errors.designation?.message}>
                <input className="input" {...register('designation', { required: 'Designation is required' })} />
              </FormField>
            </div>

            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <FormField label="Role" error={errors.role?.message}>
                <select className="input" {...register('role')}>
                  <option value="EMPLOYEE">EMPLOYEE</option>
                  <option value="MANAGER">MANAGER</option>
                </select>
              </FormField>
              <FormField label="Manager id (optional)" error={errors.managerId?.message}>
                <input className="input" placeholder="e.g. 2" {...register('managerId')} />
              </FormField>
            </div>

            <div className="mt-5 flex justify-end gap-2">
              <button type="button" className="btn-secondary" onClick={() => setEditor(null)}>
                Cancel
              </button>
              <button type="submit" className="btn-primary" disabled={busy}>
                {busy ? 'Saving…' : editor === 'create' ? 'Create employee' : 'Save changes'}
              </button>
            </div>
          </form>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Deactivate employee?"
        description={`${deleting?.fullName} will no longer be able to sign in. Historical leave records are kept.`}
        confirmLabel="Deactivate"
        busy={busy}
        onConfirm={deactivate}
        onCancel={() => setDeleting(null)}
      />
    </>
  );
}

/* -------------------------------------------------- department management */
export function DepartmentManagement() {
  const toast = useToast();
  const [departments, setDepartments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const { register, handleSubmit, reset, formState: { errors } } = useForm({
    defaultValues: { departmentName: '', description: '' },
  });

  const load = useCallback(() => {
    setLoading(true);
    api
      .get('/admin/departments')
      .then((response) => {
        setDepartments(response.data);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(load, [load]);

  const onSubmit = async (values) => {
    setBusy(true);
    try {
      await api.post('/admin/departments', {
        departmentName: values.departmentName.trim(),
        description: values.description?.trim() || '',
      });
      toast.success('Department created');
      reset({ departmentName: '', description: '' });
      load();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader title="Departments" subtitle="Organisational units and their headcount." />
      <ErrorBanner message={error} onRetry={load} />

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="card p-4 lg:col-span-2">
          {loading ? (
            <Spinner />
          ) : departments.length === 0 ? (
            <EmptyState title="No departments yet" />
          ) : (
            <table className="table-base">
              <thead>
                <tr>
                  <th>Department</th>
                  <th>Description</th>
                  <th>Employees</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {departments.map((department) => (
                  <tr key={department.id}>
                    <td className="font-medium">{department.departmentName}</td>
                    <td className="text-slate-500">{department.description || '—'}</td>
                    <td>{department.employeeCount}</td>
                    <td className="text-slate-500">{formatDateTime(department.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="card h-fit p-4">
          <h2 className="mb-3 font-semibold text-slate-700 dark:text-slate-200">New department</h2>
          <FormField label="Department name" required error={errors.departmentName?.message}>
            <input className="input" placeholder="e.g. Quality Assurance" {...register('departmentName', { required: 'Name is required' })} />
          </FormField>
          <div className="mt-3">
            <FormField label="Description">
              <textarea className="input min-h-20" placeholder="What this team owns" {...register('description')} />
            </FormField>
          </div>
          <button type="submit" className="btn-primary mt-4 w-full" disabled={busy}>
            <Plus size={16} />
            {busy ? 'Creating…' : 'Create department'}
          </button>
        </form>
      </div>
    </>
  );
}

/* ------------------------------------------------------ leave applications */
export function LeaveApplications() {
  const [applications, setApplications] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    const params = new URLSearchParams({ page: String(page), size: '10' });
    if (status) params.set('status', status);
    api
      .get(`/admin/applications?${params.toString()}`)
      .then((response) => {
        setApplications(response.data.content);
        setTotalPages(response.data.totalPages);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, [page, status]);

  return (
    <>
      <PageHeader
        title="All leave applications"
        subtitle="Every request in the system, across departments."
        action={
          <select
            className="input !w-44"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
            aria-label="Filter applications"
          >
            <option value="">All statuses</option>
            {['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'].map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        }
      />

      <ErrorBanner message={error} />

      {loading ? (
        <Spinner />
      ) : applications.length === 0 ? (
        <EmptyState title="No applications found" />
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
                  <th>Status</th>
                  <th>Applied on</th>
                </tr>
              </thead>
              <tbody>
                {applications.map((application) => (
                  <tr key={application.id}>
                    <td className="font-semibold text-slate-500">#{application.id}</td>
                    <td className="font-medium">{application.employee?.fullName}</td>
                    <td className="text-slate-500">{application.employee?.departmentName}</td>
                    <td>{application.leaveType?.leaveName}</td>
                    <td>{formatDateRange(application.startDate, application.endDate)}</td>
                    <td>{formatDays(application.totalDays)}</td>
                    <td>
                      <StatusBadge status={application.status} />
                    </td>
                    <td className="text-slate-500">{formatDateTime(application.appliedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </>
      )}
    </>
  );
}

/* ---------------------------------------------------- balance management */
export function LeaveBalanceManagement() {
  const toast = useToast();
  const [employees, setEmployees] = useState([]);
  const [selected, setSelected] = useState('');
  const [balances, setBalances] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null);
  const [totalDays, setTotalDays] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api
      .get('/admin/employees?size=100')
      .then((response) => setEmployees(response.data.content))
      .catch((err) => setError(errorMessage(err)));
  }, []);

  const loadBalances = useCallback(async () => {
    if (!selected) {
      setBalances([]);
      return;
    }
    setLoading(true);
    try {
      const response = await api.get(`/admin/employees/${selected}/leave-balances`);
      setBalances(response.data);
      setError('');
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [selected]);

  useEffect(() => {
    loadBalances();
  }, [loadBalances]);

  const save = async () => {
    if (!editing) return;
    setBusy(true);
    try {
      await api.put(`/admin/leave-balances/${editing.id}`, {
        id: editing.id,
        totalDays: Number(totalDays),
      });
      toast.success('Entitlement updated');
      setEditing(null);
      loadBalances();
    } catch (err) {
      toast.error(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const employee = employees.find((item) => String(item.id) === String(selected));

  return (
    <>
      <PageHeader
        title="Leave balance management"
        subtitle="Review and correct yearly entitlements. Used days are recalculated automatically."
      />

      <div className="mb-4 max-w-md">
        <FormField label="Employee">
          <select className="input" value={selected} onChange={(event) => setSelected(event.target.value)}>
            <option value="">Select an employee</option>
            {employees.map((item) => (
              <option key={item.id} value={item.id}>
                {item.fullName} ({item.employeeId})
              </option>
            ))}
          </select>
        </FormField>
      </div>

      <ErrorBanner message={error} />

      {loading ? (
        <Spinner />
      ) : !selected ? (
        <EmptyState title="Select an employee" description="Choose a person to inspect their leave ledger." />
      ) : balances.length === 0 ? (
        <EmptyState title="No balances yet" description="Balances are created on the first application." />
      ) : (
        <div className="card overflow-x-auto p-1">
          <table className="table-base">
            <thead>
              <tr>
                <th>Leave type</th>
                <th>Year</th>
                <th>Total</th>
                <th>Used</th>
                <th>Available</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {balances.map((balance) => (
                <tr key={balance.id}>
                  <td className="font-medium">{balance.leaveName}</td>
                  <td>{balance.year}</td>
                  <td>{formatDays(balance.totalDays)}</td>
                  <td>{formatDays(balance.usedDays)}</td>
                  <td className="font-semibold">{formatDays(balance.availableDays)}</td>
                  <td className="text-right">
                    <button
                      type="button"
                      className="btn-secondary !px-2 !py-1 text-xs"
                      onClick={() => {
                        setEditing(balance);
                        setTotalDays(String(balance.totalDays));
                      }}
                    >
                      <Pencil size={13} /> Adjust
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(editing)}
        title={`Adjust entitlement for ${employee?.fullName || ''}`}
        description={
          editing
            ? `${editing.leaveName} ${editing.year} - already used: ${formatDays(editing.usedDays)} day(s)`
            : ''
        }
        confirmLabel="Save"
        busy={busy}
        onConfirm={save}
        onCancel={() => setEditing(null)}
      >
        <FormField label="Total entitlement (days)">
          <input
            type="number"
            min="0"
            step="0.5"
            className="input"
            value={totalDays}
            onChange={(event) => setTotalDays(event.target.value)}
          />
        </FormField>
      </ConfirmDialog>
    </>
  );
}

/* ---------------------------------------------------------------- reports */
export function Reports() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api
      .get('/admin/reports')
      .then((response) => {
        setData(response.data);
        setError('');
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false));
  }, []);

  const monthly = useMemo(() => {
    if (!data) return [];
    return [...data.byMonth]
      .sort((a, b) => a.year - b.year || a.month - b.month)
      .map((row) => ({
        label: `${row.year}-${String(row.month).padStart(2, '0')}`,
        applications: row.applicationCount,
        approvedDays: Number(row.approvedDays),
      }));
  }, [data]);

  if (loading) return <Spinner label="Generating reports…" />;
  if (error) return <ErrorBanner message={error} />;
  if (!data) return null;

  return (
    <>
      <PageHeader
        title="Reports"
        subtitle="Department-wise and monthly leave statistics straight from MySQL."
        action={
          <button
            type="button"
            className="btn-secondary"
            onClick={() => window.print()}
          >
            <Download size={16} /> Print
          </button>
        }
      />

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="card p-4">
          <h2 className="mb-3 font-semibold text-slate-700 dark:text-slate-200">Applications by department</h2>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.byDepartment}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="departmentName" fontSize={12} />
                <YAxis allowDecimals={false} fontSize={12} />
                <Tooltip />
                <Legend />
                <Bar dataKey="applicationCount" name="Applications" fill="#1f3494" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="card p-4">
          <h2 className="mb-3 font-semibold text-slate-700 dark:text-slate-200">Monthly application trend</h2>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={monthly}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="label" fontSize={12} />
                <YAxis allowDecimals={false} fontSize={12} />
                <Tooltip />
                <Legend />
                <Line type="monotone" dataKey="applications" name="Applications" stroke="#1f3494" strokeWidth={2} />
                <Line type="monotone" dataKey="approvedDays" name="Approved days" stroke="#059669" strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      <div className="card mt-4 overflow-x-auto p-1">
        <table className="table-base">
          <thead>
            <tr>
              <th>Department</th>
              <th>Employees</th>
              <th>Applications</th>
              <th>Approved days</th>
            </tr>
          </thead>
          <tbody>
            {data.byDepartment.map((row) => (
              <tr key={row.departmentName}>
                <td className="font-medium">{row.departmentName}</td>
                <td>{row.employeeCount}</td>
                <td>{row.applicationCount}</td>
                <td>{formatDays(row.approvedDays)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card mt-4 overflow-x-auto p-1">
        <table className="table-base">
          <thead>
            <tr>
              <th>Month</th>
              <th>Applications</th>
              <th>Approved</th>
              <th>Approved days</th>
            </tr>
          </thead>
          <tbody>
            {data.byMonth.map((row) => (
              <tr key={`${row.year}-${row.month}`}>
                <td className="font-medium">
                  {row.year}-{String(row.month).padStart(2, '0')}
                </td>
                <td>{row.applicationCount}</td>
                <td>{row.approvedCount}</td>
                <td>{formatDays(row.approvedDays)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <p className="mt-3 text-xs text-slate-400">Generated at {formatDateTime(data.generatedAt)}</p>
    </>
  );
}
