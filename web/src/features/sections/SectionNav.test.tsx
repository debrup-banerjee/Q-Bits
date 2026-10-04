import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppRoutes } from '../../app/AppRoutes';
import { sectionsHandler } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { server } from '../../test/server';

beforeEach(() => server.use(sectionsHandler));

// 003 R4.3, 004 R2.1
it('lists the sections from the API in order', async () => {
  renderAt(<AppRoutes />);

  const nav = (await screen.findAllByRole('navigation', { name: 'Sections' }))[0]!;
  await within(nav).findByRole('link', { name: 'India AI' });
  const names = within(nav)
    .getAllByRole('link')
    .map((a) => a.textContent);
  expect(names).toEqual([
    'AI Latest',
    'AI Wire',
    'New Releases',
    'AI in Business',
    'India AI',
    'AI Innovations',
  ]);
});

// 004 R2.2
it('marks AI Latest as current on the home page', async () => {
  renderAt(<AppRoutes />, '/');

  const nav = (await screen.findAllByRole('navigation', { name: 'Sections' }))[0]!;
  expect(within(nav).getByRole('link', { name: 'AI Latest' })).toHaveAttribute(
    'aria-current',
    'page',
  );
});

// 003 R4.3
it('marks the current section as active', async () => {
  renderAt(<AppRoutes />, '/section/india-ai');

  const nav = (await screen.findAllByRole('navigation', { name: 'Sections' }))[0]!;
  const link = await within(nav).findByRole('link', { name: 'India AI' });
  expect(link).toHaveAttribute('aria-current', 'page');
  expect(within(nav).getByRole('link', { name: 'AI Latest' })).not.toHaveAttribute('aria-current');
});

// 003 R9.3
it('is reachable by keyboard', async () => {
  renderAt(<AppRoutes />);
  const user = userEvent.setup();
  await screen.findAllByRole('link', { name: 'India AI' });

  await user.tab(); // skip link
  await user.tab(); // logo
  await user.tab();
  expect(document.activeElement).toHaveTextContent('AI Latest');
});
