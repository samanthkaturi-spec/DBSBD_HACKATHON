import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../context/AuthContext.jsx';
import { ThemeProvider } from '../context/ThemeContext.jsx';
import { ToastProvider } from '../context/ToastContext.jsx';

/**
 * Shared mock of the Axios wrapper. Every test file calls
 * `vi.mock('../services/api.js', apiMockFactory)` so pages hit an in-memory
 * API instead of the network.
 */
export function apiMockFactory() {
  const api = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  };
  return {
    default: api,
    api,
    setAuthToken: vi.fn(),
    getAuthToken: vi.fn(() => null),
    onUnauthorized: vi.fn(() => () => {}),
    errorMessage: (error) => error?.response?.data?.message || error?.message || 'Something went wrong',
  };
}

/** Renders a page inside the same providers the app uses at runtime. */
export function renderWithProviders(ui, { route = '/' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ThemeProvider>
        <ToastProvider>
          <AuthProvider>{ui}</AuthProvider>
        </ToastProvider>
      </ThemeProvider>
    </MemoryRouter>
  );
}

/** A user payload as returned by /api/auth/me. */
export function sampleUser(overrides = {}) {
  return {
    id: 4,
    employeeId: 'EMP1007',
    fullName: 'Kavya Nair',
    email: 'employee4@smartleave.com',
    role: 'EMPLOYEE',
    departmentId: 1,
    departmentName: 'Engineering',
    managerId: 2,
    managerName: 'Priya Sharma',
    designation: 'DevOps Engineer',
    active: true,
    ...overrides,
  };
}
