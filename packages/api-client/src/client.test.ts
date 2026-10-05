import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { ApiError, createQBitsApi } from './client';

const BASE = 'http://api.test';
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const api = createQBitsApi(BASE);

describe('createQBitsApi', () => {
  // 003 R2.1, R2.7
  it('returns stories with the data-as-of time and passes query params', async () => {
    let seen: URL | undefined;
    server.use(
      http.get(`${BASE}/api/v1/stories`, ({ request }) => {
        seen = new URL(request.url);
        return HttpResponse.json(
          { data: [], nextCursor: null },
          { headers: { 'X-Data-As-Of': '2026-10-03T05:00:00Z' } },
        );
      }),
    );

    const result = await api.stories({ section: 'india-ai', limit: 5 });

    expect(seen?.searchParams.get('section')).toBe('india-ai');
    expect(seen?.searchParams.get('limit')).toBe('5');
    expect(result.data.nextCursor).toBeNull();
    expect(result.dataAsOf).toBe('2026-10-03T05:00:00Z');
  });

  // 003 R2.6
  it('turns problem details into ApiError with the backend code', async () => {
    server.use(
      http.get(`${BASE}/api/v1/stories/:id`, () =>
        HttpResponse.json(
          { title: 'Not Found', status: 404, detail: 'No such story.', code: 'STORY_NOT_FOUND' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    await expect(api.story('00000000-0000-0000-0000-000000000000')).rejects.toMatchObject({
      status: 404,
      code: 'STORY_NOT_FOUND',
    });
  });

  // 003 R8.2
  it('reports network failures as ApiError', async () => {
    server.use(http.get(`${BASE}/api/v1/site`, () => HttpResponse.error()));

    const error = await api.site().catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('NETWORK');
  });
});
