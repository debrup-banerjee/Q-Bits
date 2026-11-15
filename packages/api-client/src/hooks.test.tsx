// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import type { ReactNode } from 'react';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { createQBitsApi } from './client';
import { ApiProvider, useEdition, useStories } from './hooks';

const BASE = 'http://api.test';
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  vi.useRealTimers();
  setVisibility('visible');
});
afterAll(() => server.close());

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={client}>
      <ApiProvider api={createQBitsApi(BASE)}>{children}</ApiProvider>
    </QueryClientProvider>
  );
}

function setVisibility(state: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state });
  window.dispatchEvent(new Event('visibilitychange'));
}

describe('useStories', () => {
  // 004 R3.2
  it('sends the hours window', async () => {
    let hours: string | null = null;
    server.use(
      http.get(`${BASE}/api/v1/stories`, ({ request }) => {
        hours = new URL(request.url).searchParams.get('hours');
        return HttpResponse.json({ data: [], nextCursor: null });
      }),
    );
    const { result } = renderHook(() => useStories(undefined, 20, 24), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(hours).toBe('24');
  });
});

describe('useEdition', () => {
  it('returns the latest edition', async () => {
    server.use(
      http.get(`${BASE}/api/v1/edition`, () =>
        HttpResponse.json({
          id: 'e1',
          cutoffAt: '2026-10-04T00:30:00Z',
          publishedAt: '2026-10-04T01:12:00Z',
          storyCount: 12,
          nextCutoffAt: '2026-10-05T00:30:00Z',
          late: false,
        }),
      ),
    );
    const { result } = renderHook(() => useEdition(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data.storyCount).toBe(12);
  });

  it('returns null before the first edition', async () => {
    server.use(
      http.get(`${BASE}/api/v1/edition`, () =>
        HttpResponse.json({ code: 'NO_EDITION' }, { status: 404 }),
      ),
    );
    const { result } = renderHook(() => useEdition(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBeNull();
  });
});
