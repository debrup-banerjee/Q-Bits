import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { FIRST_INDIA_STORY, mockApi } from './mock-api';

// 003 R9.3, 004 R2.4
test.beforeEach(async ({ page }) => mockApi(page));

for (const [name, path, ready] of [
  ['AI Latest', '/', 'AI Latest'],
  ['overview', '/sections', 'Browse by section'],
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

// 003 R9.3: keyboard focus is visible
test('keyboard focus shows a visible outline', async ({ page }) => {
  await page.goto('/sections');
  await expect(page.getByRole('heading', { level: 1, name: 'Browse by section' })).toBeVisible();

  await page.keyboard.press('Tab');
  const focused = page.locator(':focus');
  await expect(focused).toHaveCount(1);
  const outline = await focused.evaluate((el) => {
    const style = getComputedStyle(el);
    return { style: style.outlineStyle, width: parseFloat(style.outlineWidth) };
  });

  expect(outline.style).not.toBe('none');
  expect(outline.width).toBeGreaterThanOrEqual(2);
});
