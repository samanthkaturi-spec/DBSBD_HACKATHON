import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { api } from '../services/api.js';
import ProtectedRoute from '../components/ProtectedRoute.jsx';
import { renderWithProviders, sampleUser } from './helpers.jsx';

vi.mock('../services/api.js', () => {
  const apiInstance = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  };
  return {
    default: apiInstance,
    api: apiInstance,
    setAuthToken: vi.fn(),
    getAuthToken: vi.fn(() => null),
    onUnauthorized: vi.fn(() => () => {}),
    errorMessage: (error) => error?.response?.data?.message || error?.message || 'Something went wrong',
  };
});

function App() {
  return (
    <Routes>
      <Route path="/login" element={<div>LOGIN PAGE</div>} />
      <Route path="/unauthorized" element={<div>UNAUTHORIZED PAGE</div>} />
      <Route
        path="/secret"
        element={
          <ProtectedRoute roles={['ADMIN']}>
            <div>ADMIN AREA</div>
          </ProtectedRoute>
        }
      />
      <Route
        path="/mine"
        element={
          <ProtectedRoute>
            <div>EMPLOYEE AREA</div>
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}

describe('ProtectedRoute', () => {
  it('sends anonymous visitors to the login page', async () => {
    api.get.mockRejectedValue({ response: { status: 401 } });
    renderWithProviders(<App />, { route: '/secret' });

    expect(await screen.findByText('LOGIN PAGE')).toBeInTheDocument();
    expect(screen.queryByText('ADMIN AREA')).not.toBeInTheDocument();
  });

  it('sends an authenticated employee to /unauthorized for an admin page', async () => {
    api.get.mockResolvedValue({ data: sampleUser() });
    renderWithProviders(<App />, { route: '/secret' });

    expect(await screen.findByText('UNAUTHORIZED PAGE')).toBeInTheDocument();
    expect(screen.queryByText('ADMIN AREA')).not.toBeInTheDocument();
  });

  it('lets an employee into routes without a role restriction', async () => {
    api.get.mockResolvedValue({ data: sampleUser() });
    renderWithProviders(<App />, { route: '/mine' });

    expect(await screen.findByText('EMPLOYEE AREA')).toBeInTheDocument();
  });

  it('lets an administrator into admin routes', async () => {
    api.get.mockResolvedValue({ data: sampleUser({ id: 1, role: 'ADMIN', fullName: 'Ajay Kumar' }) });
    renderWithProviders(<App />, { route: '/secret' });

    expect(await screen.findByText('ADMIN AREA')).toBeInTheDocument();
  });
});
