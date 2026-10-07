import { expect, test } from '@playwright/test';
import { expectOnlyCreditedImages } from './images';
import { mockApi } from './mock-api';

// 004 R2.1, R2.2, R3.2, R4.4, R4.5 (no horizontal scroll)
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
  await expect(page.locator('iframe, video, embed, object')).toHaveCount(0);
  await expectOnlyCreditedImages(page); // 009 R5.2

  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
});

// 004 R4.2
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

// 006 R5.3, R5.4
test('shows the edition and when the next one is due, with no live updates', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');

  await expect(page.getByText(/digest · published/)).toBeVisible();
  await expect(page.getByText(/Next edition around/)).toBeVisible();
  await expect(page.getByRole('button', { name: /new stor/ })).toHaveCount(0);
});

// 006 R5.3
test("says when today's edition is running late", async ({ page }) => {
  await mockApi(page, { late: true });
  await page.goto('/');

  await expect(
    page.getByText("Today's edition is running late. Here is the last one."),
  ).toBeVisible();
});

// 003 R4.3
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

// 004 R4.5
test('card text stays readable on phones: 17px headline, 16px summary', async ({ page }, info) => {
  test.skip(info.project.name !== 'phone', 'phone layout only');
  await mockApi(page);
  await page.goto('/');
  const cards = page.getByRole('article');
  await expect(cards).toHaveCount(4);

  for (const card of await cards.all()) {
    const headline = await card
      .getByRole('heading', { level: 2 })
      .evaluate((el) => parseFloat(getComputedStyle(el).fontSize));
    const summary = await card
      .getByTestId('feed-summary')
      .evaluate((el) => parseFloat(getComputedStyle(el).fontSize));
    expect(headline).toBeGreaterThanOrEqual(17);
    expect(summary).toBeGreaterThanOrEqual(16);
  }
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
});
