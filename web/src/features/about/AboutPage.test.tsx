import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { AppRoutes } from '../../app/AppRoutes';
import { sectionsHandler } from '../../test/fixtures';
import { API, renderAt } from '../../test/render';
import { server } from '../../test/server';

beforeEach(() =>
  server.use(
    sectionsHandler,
    http.get(`${API}/api/v1/sources`, () =>
      HttpResponse.json([
        { name: 'The Hindu', homepage: 'https://www.thehindu.com/' },
        { name: 'MIT News', homepage: 'https://news.mit.edu/' },
      ]),
    ),
    http.get(`${API}/api/v1/site`, () =>
      HttpResponse.json({ name: 'Q-Bits', contactEmail: 'debrup28.nitdgp@gmail.com' }),
    ),
  ),
);

it('explains how stories are made', async () => {
  renderAt(<AppRoutes />, '/about');

  expect(screen.getByRole('heading', { level: 1, name: 'How Q-Bits works' })).toBeInTheDocument();
  expect(screen.getByText(/only from the headline and short teaser/)).toBeInTheDocument();
  expect(screen.getByText(/Every story links to the original/)).toBeInTheDocument();
});

it('lists the sources from the API', async () => {
  renderAt(<AppRoutes />, '/about');

  const list = await screen.findByRole('region', { name: 'Where the news comes from' });
  const link = await within(list).findByRole('link', { name: 'The Hindu' });
  expect(link).toHaveAttribute('href', 'https://www.thehindu.com/');
  expect(within(list).getByRole('link', { name: 'MIT News' })).toBeInTheDocument();
});

it('gives the contact address for corrections and removal', async () => {
  renderAt(<AppRoutes />, '/about');

  const mail = await screen.findByRole('link', { name: 'debrup28.nitdgp@gmail.com' });
  expect(mail).toHaveAttribute('href', 'mailto:debrup28.nitdgp@gmail.com');
});
