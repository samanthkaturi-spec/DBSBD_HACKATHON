import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { AlertCircle, CalendarDays, Lock, Mail, Moon, ShieldCheck, Sun, UserPlus } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';
import { useTheme } from '../context/ThemeContext.jsx';
import api, { errorMessage } from '../services/api.js';
import { FormField, PageHeader } from '../components/ui.jsx';

const DEMO_ACCOUNTS = [
  { role: 'Admin', email: 'admin@smartleave.com' },
  { role: 'Manager', email: 'manager1@smartleave.com' },
  { role: 'Employee', email: 'employee1@smartleave.com' },
];

function ThemeButton() {
  const { isDark, toggleTheme } = useTheme();
  return (
    <button
      type="button"
      onClick={toggleTheme}
      className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
      aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
    >
      {isDark ? <Sun size={17} /> : <Moon size={17} />}
    </button>
  );
}

function AuthShell({ title, subtitle, children, footer }) {
  return (
    <div className="min-h-screen bg-slate-100 dark:bg-slate-950">
      <div className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-4 py-10">
        <div className="mb-6 flex items-center justify-between">
          <div className="flex items-center gap-2 font-semibold text-slate-800 dark:text-slate-100">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-navy-700 text-sm font-bold text-white">
              SL
            </span>
            Smart Leave Management
          </div>
          <ThemeButton />
        </div>

        <div className="card p-6">
          <h1 className="text-lg font-semibold text-slate-800 dark:text-slate-100">{title}</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>
          <div className="mt-5">{children}</div>
        </div>

        {footer}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ login */
export function Login() {
  const { login, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm({ defaultValues: { email: '', password: '' } });

  useEffect(() => {
    if (user) navigate('/', { replace: true });
  }, [user, navigate]);

  const onSubmit = async (values) => {
    setSubmitting(true);
    setServerError('');
    try {
      const signedIn = await login(values.email.trim(), values.password);
      const target = location.state?.from?.pathname || '/';
      const roleHome =
        signedIn.role === 'ADMIN' ? '/admin' : signedIn.role === 'MANAGER' ? '/manager' : '/dashboard';
      navigate(target === '/' ? roleHome : target, { replace: true });
    } catch (error) {
      setServerError(error.message);
    } finally {
      setSubmitting(false);
    }
  };

  const fillDemo = (account) => {
    setValue('email', account.email);
  };

  return (
    <AuthShell
      title="Sign in"
      subtitle="Use your work email to access the leave portal."
      footer={
        <div className="mt-4 rounded-xl border border-slate-200 bg-white p-4 text-sm dark:border-slate-800 dark:bg-slate-900">
          <p className="mb-2 font-medium text-slate-600 dark:text-slate-300">Demo accounts</p>
          <div className="space-y-1.5">
            {DEMO_ACCOUNTS.map((account) => (
              <button
                key={account.role}
                type="button"
                onClick={() => fillDemo(account)}
                className="flex w-full items-center justify-between rounded-lg border border-slate-200 px-3 py-1.5 text-left text-xs hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
              >
                <span className="font-semibold text-slate-700 dark:text-slate-200">{account.role}</span>
                <span className="text-slate-500">{account.email}</span>
              </button>
            ))}
          </div>
          <p className="mt-2 text-xs text-slate-400">Click a row to fill the email, then enter the account password.</p>
        </div>
      }
    >
      {serverError && (
        <div className="mb-4 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950 dark:text-rose-300" role="alert">
          <AlertCircle size={16} className="mt-0.5" />
          {serverError}
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormField label="Email" required error={errors.email?.message} icon={<Mail size={15} />}>
          <input
            type="email"
            autoComplete="username"
            className="input pl-9"
            placeholder="you@company.com"
            {...register('email', {
              required: 'Email is required',
              pattern: { value: /^\S+@\S+\.\S+$/, message: 'Enter a valid email address' },
            })}
          />
        </FormField>

        <div className="mt-4">
          <FormField label="Password" required error={errors.password?.message} icon={<Lock size={15} />}>
            <input
              type="password"
              autoComplete="current-password"
              className="input pl-9"
              placeholder="••••••••"
              {...register('password', { required: 'Password is required' })}
            />
          </FormField>
        </div>

        <button type="submit" className="btn-primary mt-5 w-full" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-slate-500">
        No account yet?{' '}
        <Link to="/register" className="font-semibold text-navy-700 hover:underline dark:text-navy-500">
          Register as an employee
        </Link>
      </p>
    </AuthShell>
  );
}

/* --------------------------------------------------------------- register */
export function Register() {
  const { register: registerUser, user } = useAuth();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [departments, setDepartments] = useState([]);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ defaultValues: { fullName: '', email: '', password: '', departmentId: '', designation: '' } });

  useEffect(() => {
    if (user) navigate('/', { replace: true });
  }, [user, navigate]);

  useEffect(() => {
    api
      .get('/departments')
      .then((response) => setDepartments(response.data))
      .catch(() => setDepartments([]));
  }, []);

  const onSubmit = async (values) => {
    setSubmitting(true);
    setServerError('');
    try {
      await registerUser({
        fullName: values.fullName.trim(),
        email: values.email.trim().toLowerCase(),
        password: values.password,
        departmentId: Number(values.departmentId),
        designation: values.designation?.trim() || 'Executive',
      });
      navigate('/dashboard', { replace: true });
    } catch (error) {
      setServerError(error.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Employee registration"
      subtitle="Self-registration always creates an EMPLOYEE account. Managers and administrators are provisioned by HR."
      footer={
        <p className="mt-4 text-center text-sm text-slate-500">
          Already registered?{' '}
          <Link to="/login" className="font-semibold text-navy-700 hover:underline dark:text-navy-500">
            Sign in
          </Link>
        </p>
      }
    >
      {serverError && (
        <div className="mb-4 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950 dark:text-rose-300" role="alert">
          <AlertCircle size={16} className="mt-0.5" />
          {serverError}
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormField label="Full name" required error={errors.fullName?.message}>
          <input
            className="input"
            placeholder="Jane Doe"
            {...register('fullName', { required: 'Full name is required', minLength: { value: 3, message: 'Enter at least 3 characters' } })}
          />
        </FormField>

        <div className="mt-4">
          <FormField label="Email" required error={errors.email?.message}>
            <input
              type="email"
              className="input"
              placeholder="jane@company.com"
              {...register('email', {
                required: 'Email is required',
                pattern: { value: /^\S+@\S+\.\S+$/, message: 'Enter a valid email address' },
              })}
            />
          </FormField>
        </div>

        <div className="mt-4">
          <FormField label="Password" required error={errors.password?.message}>
            <input
              type="password"
              className="input"
              placeholder="At least 8 characters"
              {...register('password', {
                required: 'Password is required',
                minLength: { value: 8, message: 'Password must be at least 8 characters' },
              })}
            />
          </FormField>
        </div>

        <div className="mt-4">
          <FormField label="Department" required error={errors.departmentId?.message}>
            <select
              className="input"
              {...register('departmentId', { required: 'Choose a department' })}
            >
              <option value="">Select a department</option>
              {departments.map((department) => (
                <option key={department.id} value={department.id}>
                  {department.departmentName}
                </option>
              ))}
            </select>
          </FormField>
        </div>

        <div className="mt-4">
          <FormField label="Designation" error={errors.designation?.message}>
            <input className="input" placeholder="Software Engineer" {...register('designation')} />
          </FormField>
        </div>

        <button type="submit" className="btn-primary mt-5 w-full" disabled={submitting}>
          <UserPlus size={16} />
          {submitting ? 'Creating account…' : 'Create employee account'}
        </button>
      </form>
    </AuthShell>
  );
}

/* ----------------------------------------------------------- unauthorized */
export function Unauthorized() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-100 px-4 text-center dark:bg-slate-950">
      <div className="card w-full max-w-md p-6">
        <ShieldCheck className="mx-auto text-navy-700" size={34} />
        <h1 className="mt-3 text-lg font-semibold text-slate-800 dark:text-slate-100">Access denied</h1>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          Your account does not have permission to open this page. If you think this is a mistake,
          contact your administrator.
        </p>
        <Link to="/" className="btn-primary mt-5">
          Back to my dashboard
        </Link>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------- 404 */
export function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-100 px-4 text-center dark:bg-slate-950">
      <div className="card w-full max-w-md p-6">
        <CalendarDays className="mx-auto text-navy-700" size={34} />
        <h1 className="mt-3 text-lg font-semibold text-slate-800 dark:text-slate-100">Page not found</h1>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          The page you are looking for does not exist or has been moved.
        </p>
        <Link to="/login" className="btn-primary mt-5">
          Go to sign in
        </Link>
      </div>
    </div>
  );
}

export { PageHeader, errorMessage };
