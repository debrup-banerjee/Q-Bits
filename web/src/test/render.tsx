import { createQBitsApi } from '@qbits/api-client';
import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router';
import { Providers } from '../app/providers';

export const API = 'http://api.test';

export function testQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity } },
  });
}

/**
 * Renders with real providers against the MSW-mocked API at {@link API}. Pass a query client to
 * start from a seeded cache (spec 007 R4.2).
 */
export function renderAt(ui: ReactElement, route = '/', queryClient = testQueryClient()) {
  return render(
    <Providers api={createQBitsApi(API)} queryClient={queryClient}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </Providers>,
  );
}
