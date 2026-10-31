import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { api } from '../services/api.js';
import { EmployeeDashboard } from '../pages/employee.jsx';
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

const dashboard = {
  totalEntitlementDays: 37,
  totalAvailableDays: 34,
  totalUsedDays: 3,
  pendingCount: 1,
  approvedCount: 2,
  rejectedCount: 1,
  unreadNotifications: 2,
  balances: [
    { id: 1, leaveTypeId: 1, leaveName: 'Casual Leave', year: 2026, totalDays: 12, usedDays: 3, availableDays: 9 },
    { id: 2, leaveTypeId: 2, leaveName: 'Sick Leave', year: 2026, totalDays: 10, usedDays: 0, availableDays: 10 },
  ],
  recentApplications: [
    {
      id: 11,
      employee: sampleUser(),
      leaveType: { id: 1, leaveName: 'Casual Leave' },
      startDate: '2026-11-02',
      endDate: '2026-11-04',
      totalDays: 3,
      reason: 'Family function',
      status: 'PENDING',
      appliedAt: '2026-10-30T10:00:00',
    },
    {
      id: 9,
      employee: sampleUser(),
      leaveType: { id: 2, leaveName: 'Sick Leave' },
      startDate: '2026-10-19',
      endDate: '2026-10-20',
      totalDays: 2,
      reason: 'Fever',
      status: 'APPROVED',
      appliedAt: '2026-10-15T09:00:00',
    },
  ],
};

describe('Employee dashboard', () => {
  it('renders balances and application status coming from the API', async () => {
    api.get.mockImplementation((url) => {
      if (url === '/auth/me') return Promise.resolve({ data: sampleUser() });
      if (url === '/employee/dashboard') return Promise.resolve({ data: dashboard });
      return Promise.reject(new Error(`unexpected call ${url}`));
    });

    renderWithProviders(<EmployeeDashboard />);

    expect(await screen.findByText('Welcome back, Kavya')).toBeInTheDocument();
    expect(screen.getByTestId('stat-Annual Entitlement')).toHaveTextContent('37');
    expect(screen.getByTestId('stat-Available Balance')).toHaveTextContent('34');
    expect(screen.getByTestId('stat-Used Leave')).toHaveTextContent('3');
    expect(screen.getByTestId('stat-Pending Requests')).toHaveTextContent('1');

    // recent applications table
    expect(screen.getAllByText('Casual Leave').length).toBeGreaterThan(0);
    expect(screen.getByTestId('status-PENDING')).toBeInTheDocument();
    expect(screen.getByTestId('status-APPROVED')).toBeInTheDocument();

    // balance breakdown
    expect(screen.getByText('3 used of 12')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /apply for leave/i })).toBeInTheDocument();
  });

  it('shows a retryable error banner when the API fails', async () => {
    api.get.mockImplementation((url) => {
      if (url === '/auth/me') return Promise.resolve({ data: sampleUser() });
      return Promise.reject({ response: { data: { message: 'Database connection failed' } } });
    });

    renderWithProviders(<EmployeeDashboard />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Database connection failed');
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });
});
