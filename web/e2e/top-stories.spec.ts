import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockApi } from './mock-api';

test.beforeEach(async ({ page }) => mockApi(page, { top: true }));

// 009 R3.1, R3.2, R3.3
test('the top stories slider shows three labelled slides and moves on click', async ({ page }) => {
  await page.goto('/');
  const slider = page.getByRole('region', { name: 'Top stories' });
  await expect(slider.getByRole('group')).toHaveCount(3);
  await expect(slider.getByText('Covered by 4 outlets')).toBeVisible();

  await slider.getByRole('button', { name: 'Next story' }).click();
  await expect(slider.getByRole('button', { name: 'Previous story' })).toBeEnabled();
});

// 009 R3.5
test('the slider adds no horizontal page scroll and no images', async ({ page }) => {
  await page.goto('/');
  const slider = page.getByRole('region', { name: 'Top stories' });
  await expect(slider).toBeVisible();

  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
  await expect(slider.locator('img, iframe')).toHaveCount(0);
  const headline = slider.getByRole('heading', { level: 3 }).first();
  const size = await headline.evaluate((el) => parseFloat(getComputedStyle(el).fontSize));
  expect(size).toBeGreaterThanOrEqual(17);
});

// 009 R3.3
for (const scheme of ['light', 'dark'] as const) {
  test(`the slider has no accessibility violations (${scheme})`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: scheme });
    await page.goto('/');
    await expect(page.getByRole('region', { name: 'Top stories' })).toBeVisible();
    await page.waitForLoadState('networkidle');

    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();

    expect(
      results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target).join(', ')}`),
    ).toEqual([]);
  });
}
