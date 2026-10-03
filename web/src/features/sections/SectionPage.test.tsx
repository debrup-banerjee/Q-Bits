import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { AppRoutes } from '../../app/AppRoutes';
import { page, sectionsHandler, story } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
import { server } from '../../test/server';

it('lists a section and loads more pages with the cursor', async () => {
  const cursors: (string | null)[] = [];
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, ({ request }) => {
      const url = new URL(request.url);
      expect(url.searchParams.get('section')).toBe('india-ai');
      const cursor = url.searchParams.get('cursor');
      cursors.push(cursor);
      return cursor === null
        ? HttpResponse.json(page([story({ id: '1', headline: 'First story' })], 'next-1'))
        : HttpResponse.json(page([story({ id: '2', headline: 'Second story' })]));
    }),
  );
  renderAt(<AppRoutes />, '/section/india-ai');

  expect(await screen.findByRole('heading', { level: 1, name: 'India AI' })).toBeInTheDocument();
  expect(await screen.findByRole('heading', { name: 'First story' })).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'Load more' }));

  expect(await screen.findByRole('heading', { name: 'Second story' })).toBeInTheDocument();
  expect(cursors).toEqual([null, 'next-1']);
  expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument();
});

it('shows the empty message for a quiet section', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, () => HttpResponse.json(page([]))),
  );
  renderAt(<AppRoutes />, '/section/innovations-research');

  expect(
    await screen.findByText(
      'No Innovations & Research news in the last 72 hours. Check back soon.',
    ),
  ).toBeInTheDocument();
});

it('treats an unknown section as not found', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, () =>
      HttpResponse.json({ code: 'UNKNOWN_SECTION' }, { status: 400 }),
    ),
  );
  renderAt(<AppRoutes />, '/section/sports');

  expect(
    await screen.findByRole('heading', { name: "We couldn't find that page." }),
  ).toBeInTheDocument();
});
