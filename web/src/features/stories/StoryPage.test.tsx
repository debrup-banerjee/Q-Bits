import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { AppRoutes } from '../../app/AppRoutes';
import { sectionsHandler, story } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
import { server } from '../../test/server';

it('shows one story as the page heading', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories/:id`, () => HttpResponse.json(story())),
  );
  renderAt(<AppRoutes />, `/story/${story().id}`);

  expect(
    await screen.findByRole('heading', { level: 1, name: story().headline }),
  ).toBeInTheDocument();
});

it('explains when a story is gone', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories/:id`, () =>
      HttpResponse.json({ code: 'STORY_NOT_FOUND', detail: 'No such story.' }, { status: 404 }),
    ),
  );
  renderAt(<AppRoutes />, '/story/old-one');

  expect(
    await screen.findByRole('heading', { name: "This story isn't available." }),
  ).toBeInTheDocument();
  expect(screen.getByText('Q-Bits only keeps stories from the last 72 hours.')).toBeInTheDocument();
});
