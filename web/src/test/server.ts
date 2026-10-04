import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

/**
 * API mock for component tests. Tests add handlers with server.use(...). By default there is no
 * edition yet (spec 006), which pages must handle.
 */
export const server = setupServer(
  http.get('http://api.test/api/v1/edition', () =>
    HttpResponse.json({ code: 'NO_EDITION', detail: 'No edition yet.' }, { status: 404 }),
  ),
);
