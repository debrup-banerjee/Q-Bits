import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { AppRoutes } from '../../app/AppRoutes';
import { page, sectionsHandler, story } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
import { server } from '../../test/server';

// 003 R5.1
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

// 003 R5.2
it('shows the empty message for a quiet section', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, () => HttpResponse.json(page([]))),
  );
  renderAt(<AppRoutes />, '/section/innovations-research');

  expect(
    await screen.findByText('No AI Innovations news in the last 72 hours. Check back soon.'),
  ).toBeInTheDocument();
});

// 003 R2.5
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

// 003 R8.1
it('shows skeleton cards while the section loads', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, async () => {
      await delay('infinite');
      return HttpResponse.json(page([]));
    }),
  );
  renderAt(<AppRoutes />, '/section/india-ai');

  expect(await screen.findByRole('status', { name: 'Loading news' })).toBeInTheDocument();
  expect(screen.getAllByTestId('skeleton-card').length).toBeGreaterThan(0);
});

// 007 R5.1
it('titles the page with the section name', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, () => HttpResponse.json(page([story()]))),
  );
  renderAt(<AppRoutes />, '/section/india-ai');

  await screen.findByRole('heading', { level: 1, name: 'India AI' });
  expect(document.title).toBe('India AI: AI news from the last 72 hours | Q-Bits');
});
