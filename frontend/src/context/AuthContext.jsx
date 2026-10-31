import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import api, { onUnauthorized, setAuthToken, errorMessage } from '../services/api.js';

/**
 * Holds the authenticated user.
 *
 * On boot it asks the backend who the current principal is (the httpOnly
 * cookie carries the session), so a page refresh keeps the user signed in
 * without ever persisting a token in web storage.
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [booting, setBooting] = useState(true);

  useEffect(() => {
    let active = true;
    api
      .get('/auth/me')
      .then((response) => {
        if (active) setUser(response.data);
      })
      .catch(() => {
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setBooting(false);
      });

    // 401 from any request (expired token, deactivated account) -> sign out.
    const unsubscribe = onUnauthorized(() => setUser(null));
    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  const login = useCallback(async (email, password) => {
    try {
      const response = await api.post('/auth/login', { email, password });
      setAuthToken(response.data.token);
      setUser(response.data.user);
      return response.data.user;
    } catch (error) {
      throw new Error(errorMessage(error));
    }
  }, []);

  const register = useCallback(async (payload) => {
    try {
      const response = await api.post('/auth/register', payload);
      setAuthToken(response.data.token);
      setUser(response.data.user);
      return response.data.user;
    } catch (error) {
      throw new Error(errorMessage(error));
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout');
    } catch {
      // even if the call fails the local session is dropped below
    } finally {
      setAuthToken(null);
      setUser(null);
    }
  }, []);

  const hasRole = useCallback(
    (...roles) => Boolean(user && roles.includes(user.role)),
    [user]
  );

  const value = useMemo(
    () => ({ user, booting, login, register, logout, hasRole, setUser }),
    [user, booting, login, register, logout, hasRole]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
