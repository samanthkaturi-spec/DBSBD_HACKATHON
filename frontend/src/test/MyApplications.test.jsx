import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { api } from '../services/api.js';
import { MyApplications } from '../pages/employee.jsx';
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

const applications = [
  {
    id: 21,
    employee: sampleUser(),
    leaveType: { id: 1, leaveName: 'Casual Leave' },
    startDate: '2026-11-02',
    endDate: '2026-11-03',
    totalDays: 2,
    reason: 'Wedding',
    status: 'PENDING',
    managerRemarks: null,
    approvedByName: null,
  },
  {
    id: 22,
    employee: sampleUser(),
    leaveType: { id: 2, leaveName: 'Sick Leave' },
    startDate: '2026-10-19',
    endDate: '2026-10-20',
    totalDays: 2,
    reason: 'Fever',
    status: 'APPROVED',
    managerRemarks: 'Approved',
    approvedByName: 'Priya Sharma',
  },
  {
    id: 23,
    employee: sampleUser(),
    leaveType: { id: 3, leaveName: 'Earned Leave' },
    startDate: '2026-10-08',
    endDate: '2026-10-09',
    totalDays: 2,
    reason: 'Trip',
    status: 'REJECTED',
    managerRemarks: 'Release week',
    approvedByName: 'Priya Sharma',
  },
];

function setup(apps = applications) {
  api.get.mockImplementation((url) => {
    if (url === '/auth/me') return Promise.resolve({ data: sampleUser() });
    if (url === '/leaves/my-requests') return Promise.resolve({ data: apps });
    return Promise.reject(new Error(`unexpected call ${url}`));
  });
}

describe('My applications', () => {
  it('lists every request with its status badge', async () => {
    setup();
    renderWithProviders(<MyApplications />);

    expect(await screen.findByTestId('status-PENDING')).toBeInTheDocument();
    expect(screen.getByTestId('status-APPROVED')).toBeInTheDocument();
    expect(screen.getByTestId('status-REJECTED')).toBeInTheDocument();
    expect(screen.getAllByText(/Priya Sharma/).length).toBeGreaterThan(0);
    expect(screen.getByText('Release week')).toBeInTheDocument();
  });

  it('filters the list by status', async () => {
    setup();
    renderWithProviders(<MyApplications />);
    await screen.findByTestId('status-PENDING');

    await userEvent.click(screen.getByRole('button', { name: 'APPROVED' }));

    await waitFor(() => {
      expect(screen.queryByTestId('status-PENDING')).not.toBeInTheDocument();
    });
    expect(screen.getAllByTestId('status-APPROVED')).toHaveLength(1);
    expect(screen.queryByTestId('status-REJECTED')).not.toBeInTheDocument();
  });

  it('cancels a pending request after confirmation', async () => {
    setup();
    api.put.mockResolvedValue({ data: { id: 21, status: 'CANCELLED' } });
    renderWithProviders(<MyApplications />);
    await screen.findByTestId('status-PENDING');

    await userEvent.click(screen.getAllByRole('button', { name: 'Cancel' })[0]);
    expect(await screen.findByRole('dialog')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Cancel request' }));

    await waitFor(() => expect(api.put).toHaveBeenCalledTimes(1));
    expect(api.put).toHaveBeenCalledWith('/leaves/21/cancel', { remarks: 'Cancelled from the portal' });
  });

  it('shows an error banner when the list cannot be loaded', async () => {
    api.get.mockImplementation((url) => {
      if (url === '/auth/me') return Promise.resolve({ data: sampleUser() });
      return Promise.reject({ response: { data: { message: 'Session expired' } } });
    });

    renderWithProviders(<MyApplications />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Session expired');
  });
});
