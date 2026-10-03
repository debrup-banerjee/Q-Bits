import { expect, test } from '@playwright/test';
import { BREAKING, mockApi } from './mock-api';

test('opens on AI Latest with the last 24 hours only', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1, name: 'AI Latest' })).toBeVisible();
  const nav = page.getByRole('navigation', { name: 'Sections' }).filter({ visible: true });
  await expect(nav.getByRole('link').first()).toHaveText('AI Latest');
  await expect(nav.getByRole('link', { name: 'AI Latest' })).toHaveAttribute(
    'aria-current',
    'page',
  );

  await expect(page.getByRole('article')).toHaveCount(4);
  await expect(page.getByText('A day-old story about AI chip prices')).toHaveCount(0);
  await expect(page.locator('img, iframe')).toHaveCount(0);

  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
});

test('a card expands in place to show the words to know', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  const card = page.getByRole('article').first();

  await expect(card.getByText('Words to know')).toHaveCount(0);
  await card.getByRole('button', { name: 'Show more' }).click();

  await expect(card.getByRole('button', { name: 'Show less' })).toHaveAttribute(
    'aria-expanded',
    'true',
  );
  await expect(card.getByText('Words to know')).toBeVisible();
});

test('new stories wait behind a button instead of moving the feed', async ({ page }) => {
  const state = { breaking: false };
  await page.clock.install();
  await mockApi(page, state);
  await page.goto('/');
  await expect(page.getByRole('article')).toHaveCount(4);
  const firstBefore = await page.getByRole('article').first().getByRole('heading').textContent();

  state.breaking = true;
  await page.clock.runFor(125_000);

  const button = page.getByRole('button', { name: /1 new story/ });
  await expect(button).toBeVisible();
  await expect(page.getByRole('article').first().getByRole('heading')).toHaveText(firstBefore!);

  await button.click();

  await expect(page.getByRole('article').first().getByRole('heading')).toHaveText(
    BREAKING.headline,
  );
  await expect(button).toHaveCount(0);
});

test('the tab bar stays at the top while scrolling on phones', async ({ page }, info) => {
  test.skip(info.project.name !== 'phone', 'phone layout only');
  await mockApi(page);
  await page.goto('/');
  await expect(page.getByRole('article')).toHaveCount(4);

  await page.mouse.wheel(0, 1500);
  await page.waitForTimeout(200);

  const nav = page.getByRole('navigation', { name: 'Sections' }).filter({ visible: true });
  const box = await nav.boundingBox();
  expect(box?.y ?? -1).toBeGreaterThanOrEqual(-1);
  expect(box?.y ?? 99).toBeLessThanOrEqual(1);
});
