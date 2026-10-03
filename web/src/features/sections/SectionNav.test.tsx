import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '../../app/AppRoutes';
import { sectionsHandler } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { server } from '../../test/server';

beforeEach(() => server.use(sectionsHandler));

it('lists the sections from the API in order', async () => {
  renderAt(<AppRoutes />);

  const nav = (await screen.findAllByRole('navigation', { name: 'Sections' }))[0]!;
  await within(nav).findByRole('link', { name: 'India AI' });
  const names = within(nav)
    .getAllByRole('link')
    .map((a) => a.textContent);
  expect(names).toEqual([
    'All',
    'Global AI Tech',
    'World Business',
    'India AI',
    'Innovations & Research',
  ]);
});

it('marks the current section as active', async () => {
  renderAt(<AppRoutes />, '/section/india-ai');

  const nav = (await screen.findAllByRole('navigation', { name: 'Sections' }))[0]!;
  const link = await within(nav).findByRole('link', { name: 'India AI' });
  expect(link).toHaveAttribute('aria-current', 'page');
  expect(within(nav).getByRole('link', { name: 'All' })).not.toHaveAttribute('aria-current');
});

it('is reachable by keyboard', async () => {
  renderAt(<AppRoutes />);
  const user = userEvent.setup();
  await screen.findAllByRole('link', { name: 'India AI' });

  await user.tab(); // skip link
  await user.tab(); // logo
  await user.tab();
  expect(document.activeElement).toHaveTextContent('All');
});
