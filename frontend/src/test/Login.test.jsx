import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { api } from '../services/api.js';
import { Login } from '../pages/public.jsx';
import { renderWithProviders } from './helpers.jsx';

vi.mock('../services/api.js', async (importOriginal) => {
  const actual = await importOriginal();
  const apiInstance = {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  };
  return {
    ...actual,
    default: apiInstance,
    api: apiInstance,
    setAuthToken: vi.fn(),
    getAuthToken: vi.fn(() => null),
    onUnauthorized: vi.fn(() => () => {}),
    // keep the real errorMessage() so its network / field-error handling is tested
    errorMessage: actual.errorMessage,
  };
});

function mockSession(user = null) {
  if (user) {
    api.get.mockResolvedValue({ data: user });
  } else {
    api.get.mockRejectedValue({ response: { status: 401 } });
  }
}

describe('Login form', () => {
  it('shows required validation messages when the form is empty', async () => {
    mockSession();
    renderWithProviders(<Login />);

    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it('rejects a malformed email address', async () => {
    mockSession();
    renderWithProviders(<Login />);

    const email = screen.getByPlaceholderText('you@company.com');
    await userEvent.type(email, 'not-an-email');
    await userEvent.tab();
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
  });

  it('surfaces the backend message when credentials are rejected', async () => {
    mockSession();
    api.post.mockRejectedValue({ response: { data: { message: 'Invalid email or password', status: 401 } } });
    renderWithProviders(<Login />);

    await userEvent.type(screen.getByPlaceholderText('you@company.com'), 'admin@smartleave.com');
    await userEvent.type(screen.getByPlaceholderText('••••••••'), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password');
    expect(api.post).toHaveBeenCalledWith('/auth/login', {
      email: 'admin@smartleave.com',
      password: 'wrong-password',
    });
  });

  it('shows a network hint when the backend is unreachable', async () => {
    mockSession();
    api.post.mockRejectedValue({ message: 'Network Error' });
    renderWithProviders(<Login />);

    await userEvent.type(screen.getByPlaceholderText('you@company.com'), 'admin@smartleave.com');
    await userEvent.type(screen.getByPlaceholderText('••••••••'), 'Admin@123');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Cannot reach the server/i);
  });

  it('fills the demo credentials when a demo row is clicked', async () => {
    mockSession();
    renderWithProviders(<Login />);

    await userEvent.click(screen.getByRole('button', { name: /manager1@smartleave.com/i }));
    await waitFor(() => {
      expect(screen.getByPlaceholderText('you@company.com')).toHaveValue('manager1@smartleave.com');
    });
    expect(screen.getByPlaceholderText('••••••••')).toHaveValue('Manager@123');
  });
});
