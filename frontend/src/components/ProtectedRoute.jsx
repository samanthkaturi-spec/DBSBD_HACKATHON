import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { Spinner } from './ui.jsx';

/**
 * Route guard.
 *
 * - while the session is being restored the app shows a loader
 * - anonymous visitors are redirected to /login
 * - authenticated users without the required role land on /unauthorized
 *
 * The role always comes from the authenticated principal (`/api/auth/me`),
 * never from anything the client stores or forges.
 */
export default function ProtectedRoute({ roles, children }) {
  const { user, booting } = useAuth();
  const location = useLocation();

  if (booting) {
    return <Spinner label="Restoring your session…" />;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/unauthorized" replace />;
  }
  return children;
}
