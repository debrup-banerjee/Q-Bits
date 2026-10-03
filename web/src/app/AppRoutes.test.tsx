import { screen } from '@testing-library/react';
import { sectionsHandler } from '../test/fixtures';
import { renderAt } from '../test/render';
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
