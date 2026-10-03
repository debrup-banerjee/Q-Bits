import { createContext, useContext, type ReactNode } from 'react';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import type { QBitsApi } from './client';

const ApiContext = createContext<QBitsApi | null>(null);

export function ApiProvider({ api, children }: { api: QBitsApi; children: ReactNode }) {
  return <ApiContext.Provider value={api}>{children}</ApiContext.Provider>;
}

export function useApi(): QBitsApi {
  const api = useContext(ApiContext);
  if (!api) {
    throw new Error('useApi must be used inside <ApiProvider>');
  }
  return api;
}

const STALE_MS = 60_000;

export function useSections() {
  const api = useApi();
  return useQuery({ queryKey: ['sections'], queryFn: () => api.sections(), staleTime: STALE_MS });
}

/** Newest-first stories, optionally for one section, loaded page by page. */
export function useStories(section?: string, limit = 20) {
  const api = useApi();
  return useInfiniteQuery({
    queryKey: ['stories', section ?? 'all', limit],
    queryFn: ({ pageParam }) =>
      api.stories({ ...(section ? { section } : {}), ...(pageParam ? { cursor: pageParam } : {}), limit }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.data.nextCursor ?? undefined,
    staleTime: STALE_MS,
  });
}

export function useStory(id: string) {
  const api = useApi();
  return useQuery({ queryKey: ['story', id], queryFn: () => api.story(id), staleTime: STALE_MS });
}

export function useSources() {
  const api = useApi();
  return useQuery({ queryKey: ['sources'], queryFn: () => api.sources(), staleTime: STALE_MS });
}

export function useSite() {
  const api = useApi();
  return useQuery({ queryKey: ['site'], queryFn: () => api.site(), staleTime: STALE_MS });
}
