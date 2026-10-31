import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  Bell,
  CalendarDays,
  Building2,
  ChevronDown,
  ClipboardList,
  FileText,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  PieChart,
  Sun,
  User,
  UserCog,
  Wallet,
  X,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';
import { useTheme } from '../context/ThemeContext.jsx';
import api from '../services/api.js';
import { initials } from '../utils/format.js';

/** Navigation is derived from the role returned by the backend. */
const NAV = {
  EMPLOYEE: [
    { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/leave/apply', label: 'Apply for Leave', icon: CalendarDays },
    { to: '/leave/applications', label: 'My Applications', icon: ClipboardList },
    { to: '/leave/balance', label: 'Leave Balance', icon: Wallet },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/profile', label: 'Profile', icon: User },
  ],
  MANAGER: [
    { to: '/manager', label: 'Manager Dashboard', icon: LayoutDashboard },
    { to: '/manager/pending', label: 'Pending Approvals', icon: FileText },
    { to: '/manager/requests', label: 'All Team Requests', icon: ClipboardList },
    { to: '/manager/team', label: 'Team Overview', icon: UserCog },
    { to: '/leave/apply', label: 'Apply for Leave', icon: CalendarDays },
    { to: '/leave/applications', label: 'My Applications', icon: ClipboardList },
    { to: '/leave/balance', label: 'Leave Balance', icon: Wallet },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/profile', label: 'Profile', icon: User },
  ],
  ADMIN: [
    { to: '/admin', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/admin/employees', label: 'Employees', icon: UserCog },
    { to: '/admin/departments', label: 'Departments', icon: Building2 },
    { to: '/admin/applications', label: 'Leave Applications', icon: ClipboardList },
    { to: '/admin/balances', label: 'Leave Balances', icon: Wallet },
    { to: '/admin/reports', label: 'Reports', icon: PieChart },
    { to: '/notifications', label: 'Notifications', icon: Bell },
    { to: '/profile', label: 'Profile', icon: User },
  ],
};

export default function DashboardLayout() {
  const { user, logout } = useAuth();
  const { isDark, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();

  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [unread, setUnread] = useState(0);

  const items = NAV[user?.role] || NAV.EMPLOYEE;

  useEffect(() => {
    let active = true;
    api
      .get('/notifications')
      .then((response) => {
        if (active) setUnread(response.data.filter((item) => !item.read).length);
      })
      .catch(() => {
        if (active) setUnread(0);
      });
    return () => {
      active = false;
    };
  }, [location.pathname]);

  useEffect(() => {
    setSidebarOpen(false);
    setProfileOpen(false);
  }, [location.pathname]);

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-slate-100 dark:bg-slate-950">
      {/* ---------------------------------------------------------- sidebar */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-20 bg-slate-900/50 lg:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-30 flex w-64 transform flex-col bg-navy-900 text-slate-200 transition-transform lg:translate-x-0 ${
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-16 items-center justify-between px-5">
          <Link to="/" className="flex items-center gap-2 font-semibold text-white">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-navy-600 text-sm font-bold">SL</span>
            Smart Leave
          </Link>
          <button type="button" className="lg:hidden" onClick={() => setSidebarOpen(false)} aria-label="Close menu">
            <X size={18} />
          </button>
        </div>

        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4" aria-label="Main navigation">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/dashboard' || item.to === '/manager' || item.to === '/admin'}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive
                    ? 'bg-navy-700 text-white'
                    : 'text-slate-300 hover:bg-navy-800 hover:text-white'
                }`
              }
            >
              <item.icon size={17} />
              {item.label}
              {item.to === '/notifications' && unread > 0 && (
                <span className="ml-auto rounded-full bg-rose-500 px-1.5 text-xs font-semibold text-white">
                  {unread}
                </span>
              )}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-navy-800 px-5 py-4 text-xs text-slate-400">
          Signed in as
          <p className="truncate font-medium text-slate-200">{user?.fullName}</p>
          <p className="mt-0.5 inline-flex rounded bg-navy-800 px-1.5 py-0.5 font-semibold text-slate-200">
            {user?.role}
          </p>
        </div>
      </aside>

      {/* ------------------------------------------------------------ main */}
      <div className="lg:pl-64">
        <header className="sticky top-0 z-10 flex h-16 items-center justify-between gap-3 border-b border-slate-200 bg-white/90 px-4 backdrop-blur dark:border-slate-800 dark:bg-slate-900/90 sm:px-6">
          <div className="flex items-center gap-3">
            <button
              type="button"
              className="rounded-lg border border-slate-200 p-2 dark:border-slate-700 lg:hidden"
              onClick={() => setSidebarOpen(true)}
              aria-label="Open menu"
            >
              <Menu size={18} />
            </button>
            <div className="hidden text-sm text-slate-500 dark:text-slate-400 sm:block">
              Smart Leave Management System
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Link
              to="/notifications"
              className="relative rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
              aria-label={`Notifications, ${unread} unread`}
            >
              <Bell size={17} />
              {unread > 0 && (
                <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white">
                  {unread}
                </span>
              )}
            </Link>

            <button
              type="button"
              onClick={toggleTheme}
              className="rounded-lg border border-slate-200 p-2 text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
              aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
            >
              {isDark ? <Sun size={17} /> : <Moon size={17} />}
            </button>

            <div className="relative">
              <button
                type="button"
                onClick={() => setProfileOpen((open) => !open)}
                className="flex items-center gap-2 rounded-lg border border-slate-200 py-1 pl-1 pr-2 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
                aria-haspopup="menu"
                aria-expanded={profileOpen}
              >
                <span className="flex h-8 w-8 items-center justify-center rounded-md bg-navy-700 text-xs font-bold text-white">
                  {initials(user?.fullName)}
                </span>
                <span className="hidden text-sm font-medium sm:block">{user?.fullName}</span>
                <ChevronDown size={15} />
              </button>

              {profileOpen && (
                <div className="absolute right-0 mt-2 w-52 rounded-lg border border-slate-200 bg-white py-1 shadow-lg dark:border-slate-700 dark:bg-slate-900" role="menu">
                  <div className="border-b border-slate-100 px-3 py-2 text-xs text-slate-500 dark:border-slate-800">
                    <p className="truncate font-medium text-slate-700 dark:text-slate-200">{user?.email}</p>
                    <p>{user?.departmentName}</p>
                  </div>
                  <Link to="/profile" className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-slate-50 dark:hover:bg-slate-800" role="menuitem">
                    <User size={15} /> Profile
                  </Link>
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-rose-600 hover:bg-slate-50 dark:text-rose-400 dark:hover:bg-slate-800"
                    role="menuitem"
                  >
                    <LogOut size={15} /> Sign out
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
