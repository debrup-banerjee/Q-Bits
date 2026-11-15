import type { QueryClient } from '@tanstack/react-query';

/** The element the backend fills on the first response of each page (spec 007 R4.1). */
export const INITIAL_DATA_ID = 'qbits-initial-data';

type Seed = { key: unknown[]; infinite: boolean; data: unknown; dataAsOf: string | null };
type InitialData = { path: string; queries: Seed[] };

function isInitialData(value: unknown): value is InitialData {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const v = value as { path?: unknown; queries?: unknown };
  return (
    typeof v.path === 'string' &&
    Array.isArray(v.queries) &&
    v.queries.every(
      (q: unknown) =>
        typeof q === 'object' &&
        q !== null &&
        Array.isArray((q as Seed).key) &&
        typeof (q as Seed).infinite === 'boolean' &&
        'data' in q,
    )
  );
}

/**
 * Seeds the query cache with the data the server built this page from, so the first render shows
 * content instead of a skeleton (spec 007 R4.2). Data for another path, or anything unreadable,
 * is ignored and the app fetches as usual (R4.3). Seeded data counts as just fetched, so the
 * normal stale time applies (R4.4). Returns the number of queries seeded.
 */
export function seedInitialData(
  client: QueryClient,
  doc: Document = document,
  path: string = window.location.pathname,
): number {
  const text = doc.getElementById(INITIAL_DATA_ID)?.textContent;
  if (!text) {
    return 0;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return 0;
  }
  if (!isInitialData(parsed) || parsed.path !== path) {
    return 0;
  }
  for (const q of parsed.queries) {
    // The same shapes the api-client hooks cache: WithAsOf<T>, or one page of an infinite query.
    const value = { data: q.data, dataAsOf: q.dataAsOf ?? null };
    client.setQueryData(q.key, q.infinite ? { pages: [value], pageParams: [undefined] } : value);
  }
  return parsed.queries.length;
}
