// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import type { ReactNode } from 'react';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { createQBitsApi, type StoryView } from './client';
import { ApiProvider, countNewer, useNewStories, useStories } from './hooks';

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

function story(id: string): StoryView {
  return {
    id,
    section: { slug: 'india-ai', name: 'India AI' },
    headline: 'H' + id,
    summary: 'S',
    keyTerms: [],
    source: { name: 'Src', homepage: null },
    originalUrl: 'https://a.example/' + id,
    publishedAt: '2026-10-03T04:00:00Z',
    dateEstimated: false,
    attribution: 'A',
    resources: [],
  };
}

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

describe('countNewer', () => {
  it('counts stories above the current top story', () => {
    expect(countNewer('c', [story('a'), story('b'), story('c')])).toEqual({
      count: 2,
      more: false,
    });
  });
  it('reports a full page as "more" when the top story has scrolled out', () => {
    const page = Array.from({ length: 20 }, (_, i) => story('n' + i));
    expect(countNewer('old', page)).toEqual({ count: 20, more: true });
  });
  it('treats every story as new when the feed was empty', () => {
    expect(countNewer(undefined, [story('a')])).toEqual({ count: 1, more: false });
  });
});

describe('useStories', () => {
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

describe('useNewStories', () => {
  const first = { data: { data: [story('a')], nextCursor: null }, dataAsOf: null };

  it('waits two minutes, then reports newer stories without a first extra request', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const calls: string[] = [];
    server.use(
      http.get(`${BASE}/api/v1/stories`, ({ request }) => {
        calls.push(request.url);
        return HttpResponse.json({ data: [story('new'), story('a')], nextCursor: null });
      }),
    );
    const { result } = renderHook(() => useNewStories(first, Date.now()), { wrapper });

    expect(result.current.count).toBe(0);
    expect(calls).toHaveLength(0);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(120_000);
    });

    await waitFor(() => expect(result.current.count).toBe(1));
    expect(calls).toHaveLength(1);
    expect(new URL(calls[0]!).searchParams.get('hours')).toBe('24');
    expect(new URL(calls[0]!).searchParams.get('limit')).toBe('20');
  });

  it('does not check while the page is hidden', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let calls = 0;
    server.use(
      http.get(`${BASE}/api/v1/stories`, () => {
        calls++;
        return HttpResponse.json({ data: [story('a')], nextCursor: null });
      }),
    );
    renderHook(() => useNewStories(first, Date.now()), { wrapper });
    setVisibility('hidden');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(360_000);
    });
    expect(calls).toBe(0);

    setVisibility('visible');
    await waitFor(() => expect(calls).toBe(1));
  });
});
