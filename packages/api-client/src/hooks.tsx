import { createContext, useContext, type ReactNode } from 'react';
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import type { QBitsApi, StoryPage, StoryView, WithAsOf } from './client';

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
export const NEW_STORIES_CHECK_MS = 120_000;

/**
 * AI Latest: every story from the last 24 hours, newest first (spec 004 R3.2). The list does not
 * refresh by itself, so it never jumps while someone reads; useNewStories reports what's new.
 */
export function useLatestFeed() {
  const query = useStories(undefined, LATEST_PAGE, LATEST_HOURS);
  return query;
}

export type NewStories = {
  /** How many newer stories exist (capped at one page). */
  count: number;
  /** True when there may be more than `count` (the whole check page was new). */
  more: boolean;
  /** Reload the feed from the top so the new stories appear. */
  apply: () => Promise<void>;
};

/**
 * Checks for newer stories every 2 minutes while the page is visible, without touching the feed
 * (spec 004 R5). Pass the feed's first page; the first check happens 2 minutes after it loaded.
 */
export function useNewStories(
  firstPage: WithAsOf<StoryPage> | undefined,
  loadedAt: number,
): NewStories {
  const api = useApi();
  const queryClient = useQueryClient();
  const check = useQuery({
    queryKey: ['stories', 'latest-check'],
    queryFn: () => api.stories({ hours: LATEST_HOURS, limit: LATEST_PAGE }),
    enabled: firstPage !== undefined,
    initialData: firstPage,
    initialDataUpdatedAt: loadedAt,
    staleTime: NEW_STORIES_CHECK_MS,
    refetchInterval: NEW_STORIES_CHECK_MS,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    retry: false,
  });
  const { count, more } = countNewer(firstPage?.data.data[0]?.id, check.data?.data.data ?? []);
  return {
    count,
    more,
    apply: async () => {
      await queryClient.resetQueries({
        queryKey: storiesKey(undefined, LATEST_PAGE, LATEST_HOURS),
      });
    },
  };
}

/** Stories in `latest` that come before the one currently at the top of the feed. */
export function countNewer(topId: string | undefined, latest: StoryView[]) {
  const index = topId === undefined ? -1 : latest.findIndex((s) => s.id === topId);
  if (index >= 0) {
    return { count: index, more: false };
  }
  return { count: latest.length, more: latest.length >= LATEST_PAGE };
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
