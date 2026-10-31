import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import DashboardLayout from './components/DashboardLayout.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import { useAuth } from './context/AuthContext.jsx';
import { Login, Register, NotFound, Unauthorized } from './pages/public.jsx';
import {
  ApplyLeave,
  EmployeeDashboard,
  LeaveBalancePage,
  MyApplications,
  Notifications,
  Profile,
} from './pages/employee.jsx';
import {
  AllTeamRequests,
  ManagerDashboard,
  PendingApprovals,
  TeamOverview,
} from './pages/manager.jsx';
import {
  AdminDashboard,
  DepartmentManagement,
  EmployeeManagement,
  LeaveApplications,
  LeaveBalanceManagement,
  Reports,
} from './pages/admin.jsx';

/** "/" sends each role to its own dashboard. */
function HomeRedirect() {
  const { user, booting } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (booting) return;
    if (!user) {
      navigate('/login', { replace: true });
      return;
    }
    const target =
      user.role === 'ADMIN' ? '/admin' : user.role === 'MANAGER' ? '/manager' : '/dashboard';
    navigate(target, { replace: true });
  }, [user, booting, navigate]);

  return null;
}

const EMPLOYEE = ['EMPLOYEE', 'MANAGER', 'ADMIN'];

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/unauthorized" element={<Unauthorized />} />

      <Route
        element={
          <ProtectedRoute>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<HomeRedirect />} />
        <Route path="/dashboard" element={<EmployeeDashboard />} />
        <Route path="/leave/apply" element={<ApplyLeave />} />
        <Route path="/leave/applications" element={<MyApplications />} />
        <Route path="/leave/balance" element={<LeaveBalancePage />} />
        <Route path="/notifications" element={<Notifications />} />
        <Route path="/profile" element={<Profile />} />

        <Route
          path="/manager"
          element={
            <ProtectedRoute roles={['MANAGER']}>
              <ManagerDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="/manager/pending"
          element={
            <ProtectedRoute roles={['MANAGER']}>
              <PendingApprovals />
            </ProtectedRoute>
          }
        />
        <Route
          path="/manager/requests"
          element={
            <ProtectedRoute roles={['MANAGER']}>
              <AllTeamRequests />
            </ProtectedRoute>
          }
        />
        <Route
          path="/manager/team"
          element={
            <ProtectedRoute roles={['MANAGER']}>
              <TeamOverview />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <AdminDashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/employees"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <EmployeeManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/departments"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <DepartmentManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/applications"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <LeaveApplications />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/balances"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <LeaveBalanceManagement />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/reports"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <Reports />
            </ProtectedRoute>
          }
        />
      </Route>

      <Route path="/home" element={<Navigate to="/" replace />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
