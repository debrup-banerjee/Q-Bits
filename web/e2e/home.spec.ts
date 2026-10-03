import { expect, test } from '@playwright/test';
import { FIRST_INDIA_STORY, mockApi } from './mock-api';

test.beforeEach(async ({ page }) => mockApi(page));

async function expectNoHorizontalScroll(page: import('@playwright/test').Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

test('section overview shows the four sections without horizontal scrolling', async ({ page }) => {
  await page.goto('/sections');

  await expect(page.getByRole('heading', { level: 1, name: 'Browse by section' })).toBeVisible();
  for (const name of ['Global AI Tech', 'World Business', 'India AI', 'Innovations & Research']) {
    await expect(page.getByRole('heading', { level: 2, name })).toBeVisible();
  }
  await expect(
    page.getByText('No Innovations & Research news in the last 72 hours. Check back soon.'),
  ).toBeVisible();
  await expect(page.locator('img, iframe')).toHaveCount(0);
  await expectNoHorizontalScroll(page);
});

test('overview → section → the link out to the publisher', async ({ page }) => {
  await page.goto('/sections');
  await page.getByRole('link', { name: 'See all India AI' }).click();

  await expect(page).toHaveURL(/\/section\/india-ai$/);
  await expect(page.getByRole('heading', { level: 1, name: 'India AI' })).toBeVisible();
  const out = page.getByRole('link', {
    name: 'Read the full story at The Hindu (opens in a new tab)',
  });
  await expect(out).toHaveAttribute('href', FIRST_INDIA_STORY.originalUrl);
  await expect(out).toHaveAttribute('target', '_blank');
  await expect(out).toHaveAttribute('rel', 'noopener noreferrer');
  await expectNoHorizontalScroll(page);
});

test('section navigation is visible and usable at this width', async ({ page }) => {
  await page.goto('/');
  const nav = page.getByRole('navigation', { name: 'Sections' }).filter({ visible: true });

  await expect(nav).toHaveCount(1);
  await nav.getByRole('link', { name: 'World Business' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'World Business' })).toBeVisible();
});

test('a story page can be opened directly', async ({ page }) => {
  await page.goto(`/story/${FIRST_INDIA_STORY.id}`);

  await expect(
    page.getByRole('heading', { level: 1, name: FIRST_INDIA_STORY.headline }),
  ).toBeVisible();
  await expectNoHorizontalScroll(page);
});
