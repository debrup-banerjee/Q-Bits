import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { FIRST_INDIA_STORY, mockApi } from './mock-api';

test.beforeEach(async ({ page }) => mockApi(page));

for (const [name, path, ready] of [
  ['home', '/', 'AI news from the last 72 hours'],
  ['section', '/section/india-ai', 'India AI'],
  ['story', `/story/${FIRST_INDIA_STORY.id}`, FIRST_INDIA_STORY.headline],
  ['about', '/about', 'How Q-Bits works'],
] as const) {
  for (const scheme of ['light', 'dark'] as const) {
    test(`${name} page has no accessibility violations (${scheme})`, async ({ page }) => {
      await page.emulateMedia({ colorScheme: scheme });
      await page.goto(path);
      await expect(page.getByRole('heading', { level: 1, name: ready })).toBeVisible();
      await page.waitForLoadState('networkidle');

      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();

      expect(
        results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target).join(', ')}`),
      ).toEqual([]);
    });
  }
}
