import axios from 'axios';

/**
 * Single Axios instance for the whole app.
 *
 * Auth strategy
 * -------------
 * 1. The SPA session lives in an **httpOnly cookie** (`SLM_TOKEN`) set by the
 *    backend, so no token is ever written to localStorage/sessionStorage.
 * 2. If the login response is still in memory (this tab), its bearer token is
 *    attached as an Authorization header as well - useful if cookies are
 *    blocked. The in-memory value disappears on refresh, and the cookie then
 *    carries the session.
 *
 * Base URL comes from VITE_API_BASE_URL (see .env.example) and defaults to the
 * Vite dev proxy, which forwards /api to http://localhost:8080.
 */
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';

export const api = axios.create({
  baseURL,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
  timeout: 20000,
});

let inMemoryToken = null;

export function setAuthToken(token) {
  inMemoryToken = token || null;
}

export function getAuthToken() {
  return inMemoryToken;
}

api.interceptors.request.use((config) => {
  if (inMemoryToken && !config.headers.Authorization) {
    config.headers.Authorization = `Bearer ${inMemoryToken}`;
  }
  return config;
});

/** Listeners notified when the backend says the session is no longer valid. */
const unauthorizedListeners = new Set();
export function onUnauthorized(listener) {
  unauthorizedListeners.add(listener);
  return () => unauthorizedListeners.delete(listener);
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const isAuthCall = error.config?.url?.includes('/auth/login') || error.config?.url?.includes('/auth/register');
    if (status === 401 && !isAuthCall) {
      inMemoryToken = null;
      unauthorizedListeners.forEach((listener) => listener());
    }
    return Promise.reject(error);
  }
);

/**
 * Turns any Axios error into a plain message the UI can display.
 * Field level validation errors from the backend are preferred when present.
 */
export function errorMessage(error) {
  const data = error?.response?.data;
  if (data?.fieldErrors?.length) {
    return data.fieldErrors.map((item) => `${item.field}: ${item.message}`).join(', ');
  }
  if (data?.message) {
    return data.message;
  }
  if (error?.message === 'Network Error') {
    return 'Cannot reach the server. Is the Spring Boot backend running on port 8080?';
  }
  return error?.message || 'Something went wrong';
}

export default api;
