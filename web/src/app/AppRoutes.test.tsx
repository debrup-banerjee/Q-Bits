import { screen } from '@testing-library/react';
import { sectionsHandler } from '../test/fixtures';
import { API, renderAt } from '../test/render';
import { http, HttpResponse } from 'msw';
import { server } from '../test/server';
import { AppRoutes } from './AppRoutes';

it('shows a friendly page for unknown routes', async () => {
  server.use(sectionsHandler);
  renderAt(<AppRoutes />, '/no/such/page');

  expect(
    await screen.findByRole('heading', { name: "We couldn't find that page." }),
  ).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Back to the latest AI news' })).toHaveAttribute(
    'href',
    '/',
  );
});

it('has a footer link to the About page', async () => {
  server.use(sectionsHandler);
  renderAt(<AppRoutes />);

  expect(screen.getByRole('link', { name: 'How Q-Bits works' })).toHaveAttribute('href', '/about');
});

it('shows AI Latest at / and the section overview at /sections', async () => {
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, () => HttpResponse.json({ data: [], nextCursor: null })),
  );
  const { unmount } = renderAt(<AppRoutes />, '/');
  expect(screen.getByRole('heading', { level: 1, name: 'AI Latest' })).toBeInTheDocument();
  unmount();

  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/stories`, () => HttpResponse.json({ data: [], nextCursor: null })),
  );
  renderAt(<AppRoutes />, '/sections');
  expect(screen.getByRole('heading', { level: 1, name: 'Browse by section' })).toBeInTheDocument();
});

it('has a footer link to browse by section', async () => {
  server.use(sectionsHandler);
  renderAt(<AppRoutes />);

  expect(screen.getByRole('link', { name: 'Browse by section' })).toHaveAttribute(
    'href',
    '/sections',
  );
});
