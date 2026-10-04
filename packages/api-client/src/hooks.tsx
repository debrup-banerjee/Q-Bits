import { createContext, useContext, type ReactNode } from 'react';
import { useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query';
import { ApiError, type QBitsApi } from './client';

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

/** Newest-first stories, optionally for one section and window, loaded page by page. */
export function useStories(section?: string, limit = 20, hours?: number) {
  const api = useApi();
  return useInfiniteQuery({
    queryKey: storiesKey(section, limit, hours),
    queryFn: ({ pageParam }) =>
      api.stories({
        ...(section ? { section } : {}),
        ...(pageParam ? { cursor: pageParam } : {}),
        ...(hours ? { hours } : {}),
        limit,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.data.nextCursor ?? undefined,
    staleTime: STALE_MS,
  });
}

export function storiesKey(section: string | undefined, limit: number, hours?: number) {
  return ['stories', section ?? 'all', limit, hours ?? 72] as const;
}

export const LATEST_HOURS = 24;
export const LATEST_PAGE = 20;

/** AI Latest: today's edition, newest first (spec 004 R3.2, 006 R4.2). */
export function useLatestFeed() {
  const query = useStories(undefined, LATEST_PAGE, LATEST_HOURS);
  return query;
}

/**
 * The latest daily edition, or null before the first one (spec 006 R5). Refetched when the tab is
 * focused again, so a reader who comes back after the cut-off sees the new edition.
 */
export function useEdition() {
  const api = useApi();
  return useQuery({
    queryKey: ['edition'],
    queryFn: async () => {
      try {
        return await api.edition();
      } catch (e) {
        if (e instanceof ApiError && e.code === 'NO_EDITION') {
          return null;
        }
        throw e;
      }
    },
    staleTime: STALE_MS,
    refetchOnWindowFocus: true,
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

/** Optional accounts: register, log in, or sign in with Google (never required to browse). */
export function useRegister() {
  const api = useApi();
  return useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      api.register(username, password),
  });
}

export function useLogin() {
  const api = useApi();
  return useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      api.login(username, password),
  });
}

export function useGoogleSignIn() {
  const api = useApi();
  return useMutation({ mutationFn: (idToken: string) => api.googleSignIn(idToken) });
}

/** Confirms a stored token is still valid. Disabled (no request) when there is no token. */
export function useMe(token: string | null) {
  const api = useApi();
  return useQuery({
    queryKey: ['me', token],
    queryFn: () => api.me(token!),
    enabled: token !== null,
    retry: false,
  });
}
