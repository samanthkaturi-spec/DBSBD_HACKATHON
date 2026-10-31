import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { api } from '../services/api.js';
import { ApplyLeave } from '../pages/employee.jsx';
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

const leaveTypes = [
  { id: 1, leaveName: 'Casual Leave', annualEntitlement: 12, active: true },
  { id: 2, leaveName: 'Sick Leave', annualEntitlement: 10, active: true },
];

function setupGet() {
  api.get.mockImplementation((url) => {
    if (url === '/auth/me') return Promise.resolve({ data: sampleUser() });
    if (url === '/leave-types') return Promise.resolve({ data: leaveTypes });
    return Promise.reject(new Error(`unexpected call ${url}`));
  });
}

async function fillAndSubmit() {
  await userEvent.selectOptions(screen.getByLabelText(/leave category/i), '1');
  fireEvent.change(screen.getByLabelText(/start date/i), { target: { value: '2026-11-02' } });
  fireEvent.change(screen.getByLabelText(/end date/i), { target: { value: '2026-11-06' } });
  await userEvent.type(screen.getByLabelText(/reason/i), 'Family function out of town');
  await userEvent.click(screen.getByRole('button', { name: /submit request/i }));
}

describe('Apply for leave form', () => {
  it('loads the leave categories offered by the backend', async () => {
    setupGet();
    renderWithProviders(<ApplyLeave />);

    const select = await screen.findByLabelText(/leave category/i);
    expect(select).toHaveTextContent('Casual Leave (12 days/year)');
    expect(select).toHaveTextContent('Sick Leave (10 days/year)');
  });

  it('calculates working days from Monday to Friday and ignores weekends', async () => {
    setupGet();
    renderWithProviders(<ApplyLeave />);
    await screen.findByLabelText(/leave category/i);

    // 2026-11-02 is a Monday, 2026-11-06 a Friday -> 5 working days
    fireEvent.change(screen.getByLabelText(/start date/i), { target: { value: '2026-11-02' } });
    fireEvent.change(screen.getByLabelText(/end date/i), { target: { value: '2026-11-06' } });

    expect(screen.getByTestId('duration-preview')).toHaveTextContent('5 working days');
  });

  it('requires a reason before submitting', async () => {
    setupGet();
    renderWithProviders(<ApplyLeave />);
    await screen.findByLabelText(/leave category/i);

    await userEvent.selectOptions(screen.getByLabelText(/leave category/i), '1');
    fireEvent.change(screen.getByLabelText(/start date/i), { target: { value: '2026-11-02' } });
    fireEvent.change(screen.getByLabelText(/end date/i), { target: { value: '2026-11-02' } });
    await userEvent.click(screen.getByRole('button', { name: /submit request/i }));

    expect(await screen.findByText('Reason is required')).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('submits the application and reports success', async () => {
    setupGet();
    api.post.mockResolvedValue({ data: { id: 42, status: 'PENDING' } });
    renderWithProviders(<ApplyLeave />);
    await screen.findByLabelText(/leave category/i);

    await fillAndSubmit();

    expect(api.post).toHaveBeenCalledWith('/leaves/apply', {
      leaveTypeId: 1,
      startDate: '2026-11-02',
      endDate: '2026-11-06',
      reason: 'Family function out of town',
    });
    expect(await screen.findByText(/submitted for manager approval/i)).toBeInTheDocument();
  });

  it('shows the backend validation message when the request is rejected', async () => {
    setupGet();
    api.post.mockRejectedValue({
      response: { data: { message: 'Start date cannot be in the past', status: 400 } },
    });
    renderWithProviders(<ApplyLeave />);
    await screen.findByLabelText(/leave category/i);

    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent('Start date cannot be in the past');
  });
});
