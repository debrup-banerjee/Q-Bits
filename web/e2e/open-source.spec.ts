import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { LINKED_STORY_ID, mockApi } from './mock-api';

test.beforeEach(async ({ page }) => mockApi(page));

test('feed cards show open-source links without expanding', async ({ page }) => {
  await page.goto('/');
  const card = page
    .getByRole('article')
    .filter({ hasText: 'A new AI model can read a whole bookshelf' });

  const code = card.getByRole('link', {
    name: 'Code on GitHub: example-lab/bookshelf (opens in a new tab)',
  });
  await expect(code).toBeVisible();
  await expect(code).toHaveAttribute('href', 'https://github.com/example-lab/bookshelf');
  await expect(code).toHaveAttribute('rel', 'noopener noreferrer');
  await expect(card.getByRole('link', { name: /Paper on arXiv: 2410.01234/ })).toBeVisible();

  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
});

for (const scheme of ['light', 'dark'] as const) {
  test(`story page with links has no accessibility violations (${scheme})`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: scheme });
    await page.goto(`/story/${LINKED_STORY_ID}`);
    await expect(page.getByRole('link', { name: /Model on Hugging Face/ })).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();

    expect(
      results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target).join(', ')}`),
    ).toEqual([]);
  });
}
