import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { errorMessage, getAuthToken, setAuthToken } from '../services/api.js';
import { StatusBadge } from '../components/ui.jsx';
import { clientWorkingDays, formatDateRange, formatDays } from '../utils/format.js';

describe('errorMessage()', () => {
  it('prefers field level validation errors from the backend', () => {
    const error = {
      response: {
        data: {
          message: 'Validation failed',
          fieldErrors: [
            { field: 'email', message: 'Email must be valid' },
            { field: 'password', message: 'Password must be between 8 and 72 characters' },
          ],
        },
      },
    };
    expect(errorMessage(error)).toBe(
      'email: Email must be valid, password: Password must be between 8 and 72 characters'
    );
  });

  it('falls back to the backend message and then to a network hint', () => {
    expect(errorMessage({ response: { data: { message: 'Insufficient leave balance' } } })).toBe(
      'Insufficient leave balance'
    );
    expect(errorMessage({ message: 'Network Error' })).toMatch(/Cannot reach the server/);
    expect(errorMessage(undefined)).toBe('Something went wrong');
  });
});

describe('in-memory token holder', () => {
  it('keeps the token in memory only (never in web storage)', () => {
    setAuthToken('header-token');
    expect(getAuthToken()).toBe('header-token');
    expect(window.localStorage.getItem('token')).toBeNull();
    expect(window.sessionStorage.length).toBe(0);
    setAuthToken(null);
    expect(getAuthToken()).toBeNull();
  });
});

describe('StatusBadge', () => {
  it.each(['PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'])('renders %s', (status) => {
    render(<StatusBadge status={status} />);
    expect(screen.getByTestId(`status-${status}`)).toHaveTextContent(status);
  });
});

describe('format helpers', () => {
  it('renders BigDecimal days without trailing zeros', () => {
    expect(formatDays(3)).toBe('3');
    expect(formatDays('3.0')).toBe('3');
    expect(formatDays(2.5)).toBe('2.5');
    expect(formatDays(null)).toBe('0');
  });

  it('formats single day and multi day ranges differently', () => {
    expect(formatDateRange('2026-11-02', '2026-11-02')).not.toContain('→');
    expect(formatDateRange('2026-11-02', '2026-11-06')).toContain('→');
  });

  it('counts weekdays only, mirroring the backend rule', () => {
    expect(clientWorkingDays('2026-11-02', '2026-11-06')).toBe(5);
    expect(clientWorkingDays('2026-11-07', '2026-11-08')).toBe(0);
    expect(clientWorkingDays('', '')).toBe(0);
  });
});
