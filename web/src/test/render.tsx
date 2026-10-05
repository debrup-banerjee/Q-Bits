import { createQBitsApi } from '@qbits/api-client';
import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router';
import { Providers } from '../app/providers';

export const API = 'http://api.test';

/** Renders with real providers against the MSW-mocked API at {@link API}. */
export function renderAt(ui: ReactElement, route = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity } },
  });
  return render(
    <Providers api={createQBitsApi(API)} queryClient={queryClient}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </Providers>,
  );
}
