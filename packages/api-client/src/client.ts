import createClient from 'openapi-fetch';
import type { components, paths } from './schema';

type Schemas = components['schemas'];

export type KeyTerm = Schemas['KeyTerm'];
export type SectionRef = Schemas['SectionRef'];
export type SectionView = Schemas['SectionView'];
export type SourceView = Schemas['SourceView'];
export type SiteView = Schemas['SiteView'];
/** The latest daily edition (spec 006). */
export type EditionView = Schemas['EditionView'];
/** A verified open-source link: code, model, dataset or paper. */
export type ResourceLink = Schemas['ResourceLink'];
/** A source's homepage is null if the source was removed from the approved list. */
export type SourceRef = Schemas['SourceRef'];
export type StoryView = Schemas['StoryView'];
export type StoryPage = { data: StoryView[]; nextCursor: string | null };
/** A signed session token plus the username it belongs to (optional accounts). */
export type Session = Schemas['SessionView'];

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

/** `hours` limits the window (1–72, default 72). AI Latest uses 24. */
export type StoriesQuery = { section?: string; cursor?: string; limit?: number; hours?: number };

/** Typed access to the Q-Bits read API. Shared by the web app and, later, the mobile app. */
export interface QBitsApi {
  sections(): Promise<WithAsOf<SectionView[]>>;
  stories(query?: StoriesQuery): Promise<WithAsOf<StoryPage>>;
  story(id: string): Promise<WithAsOf<StoryView>>;
  sources(): Promise<WithAsOf<SourceView[]>>;
  site(): Promise<WithAsOf<SiteView>>;
  /** Latest published edition; rejects with code NO_EDITION before the first one. */
  edition(): Promise<WithAsOf<EditionView>>;
  /** Optional accounts (never required to browse). Rejects with USERNAME_TAKEN, WEAK_PASSWORD, etc. */
  register(username: string, password: string): Promise<Session>;
  /** Rejects with code INVALID_CREDENTIALS on a wrong username or password. */
  login(username: string, password: string): Promise<Session>;
  /** Exchanges a Google Identity Services ID token for a Q-Bits session. */
  googleSignIn(idToken: string): Promise<Session>;
  /** Confirms a stored token is still valid; rejects with code INVALID_TOKEN otherwise. */
  me(token: string): Promise<Session>;
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

/** Like {@link unwrap}, but for endpoints with no X-Data-As-Of header (auth isn't catalog data). */
async function unwrapPlain<T>(call: Promise<Result<T>>): Promise<T> {
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
  return data;
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
      unwrap(client.GET('/api/v1/stories', { params: { query } })) as Promise<WithAsOf<StoryPage>>,
    story: (id) => unwrap(client.GET('/api/v1/stories/{id}', { params: { path: { id } } })),
    sources: () => unwrap(client.GET('/api/v1/sources')),
    site: () => unwrap(client.GET('/api/v1/site')),
    edition: () => unwrap(client.GET('/api/v1/edition')),
    register: (username, password) =>
      unwrapPlain(
        client.POST('/api/v1/auth/register', { body: { username, password } }),
      ),
    login: (username, password) =>
      unwrapPlain(client.POST('/api/v1/auth/login', { body: { username, password } })),
    googleSignIn: (idToken) =>
      unwrapPlain(client.POST('/api/v1/auth/google', { body: { idToken } })),
    me: (token) =>
      unwrapPlain(
        client.GET('/api/v1/auth/me', { headers: { Authorization: `Bearer ${token}` } }),
      ),
  };
}
