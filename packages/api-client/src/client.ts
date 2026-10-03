import createClient from 'openapi-fetch';
import type { components, paths } from './schema';

type Schemas = components['schemas'];

export type KeyTerm = Schemas['KeyTerm'];
export type SectionRef = Schemas['SectionRef'];
export type SectionView = Schemas['SectionView'];
export type SourceView = Schemas['SourceView'];
export type SiteView = Schemas['SiteView'];
/** A source's homepage can be missing if the source was removed from the approved list. */
export type SourceRef = Omit<Schemas['SourceRef'], 'homepage'> & { homepage: string | null };
export type StoryView = Omit<Schemas['StoryView'], 'source'> & { source: SourceRef };
export type StoryPage = { data: StoryView[]; nextCursor: string | null };

/** A response plus the time of the newest story (from the X-Data-As-Of header). */
export type WithAsOf<T> = { data: T; dataAsOf: string | null };

/** A failed request. `code` is the backend's stable error code, e.g. STORY_NOT_FOUND. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export type StoriesQuery = { section?: string; cursor?: string; limit?: number };

/** Typed access to the Q-Bits read API. Shared by the web app and, later, the mobile app. */
export interface QBitsApi {
  sections(): Promise<WithAsOf<SectionView[]>>;
  stories(query?: StoriesQuery): Promise<WithAsOf<StoryPage>>;
  story(id: string): Promise<WithAsOf<StoryView>>;
  sources(): Promise<WithAsOf<SourceView[]>>;
  site(): Promise<WithAsOf<SiteView>>;
}

type Result<T> = { data?: T; error?: unknown; response: Response };

async function unwrap<T>(call: Promise<Result<T>>): Promise<WithAsOf<T>> {
  let result: Result<T>;
  try {
    result = await call;
  } catch {
    throw new ApiError(0, 'NETWORK', 'Could not reach the server.');
  }
  const { data, error, response } = result;
  if (!response.ok || data === undefined) {
    const problem = (error ?? {}) as { code?: string; detail?: string; title?: string };
    throw new ApiError(
      response.status,
      problem.code ?? 'HTTP_' + response.status,
      problem.detail ?? problem.title ?? 'Request failed.',
    );
  }
  return { data, dataAsOf: response.headers.get('X-Data-As-Of') };
}

export function createQBitsApi(baseUrl: string, fetchImpl?: typeof fetch): QBitsApi {
  const client = createClient<paths>({
    baseUrl,
    // Resolve fetch at call time so test mocks and polyfills installed later are used.
    fetch: (request: Request) => (fetchImpl ?? globalThis.fetch)(request),
  });
  return {
    sections: () => unwrap(client.GET('/api/v1/sections')),
    stories: (query = {}) =>
      unwrap(client.GET('/api/v1/stories', { params: { query } })) as Promise<
        WithAsOf<StoryPage>
      >,
    story: (id) =>
      unwrap(client.GET('/api/v1/stories/{id}', { params: { path: { id } } })) as Promise<
        WithAsOf<StoryView>
      >,
    sources: () => unwrap(client.GET('/api/v1/sources')),
    site: () => unwrap(client.GET('/api/v1/site')),
  };
}
