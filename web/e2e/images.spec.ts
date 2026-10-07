import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { LINKED_STORY_ID, mockApi, PHOTO } from './mock-api';
import { expectOnlyCreditedImages } from './images';

test.beforeEach(async ({ page }) => mockApi(page));

// 009 R1, R5
test('every story shows a credited photo or its own cover art', async ({ page }) => {
  await page.goto('/');
  const cards = page.getByRole('article');
  await expect(cards).not.toHaveCount(0);

  for (const card of await cards.all()) {
    const pictures = card.locator('img, [data-testid="cover-art"]');
    await expect(pictures).toHaveCount(1);
  }
  await expectOnlyCreditedImages(page);

  const photo = page.getByRole('img', { name: PHOTO.alt });
  await expect(photo).toBeVisible();
  await expect(photo).toHaveAttribute('referrerpolicy', 'no-referrer');
  const credit = page.getByText(/^Illustrative photo by Asha Rao on Pexels$/);
  await expect(credit.getByRole('link', { name: 'Asha Rao' })).toHaveAttribute(
    'href',
    PHOTO.creditUrl!,
  );
  await expect(credit.getByRole('link', { name: 'Pexels' })).toHaveAttribute(
    'href',
    PHOTO.providerUrl!,
  );
  // Pexels asks for a prominent link to it wherever its photos appear.
  await expect(page.getByRole('link', { name: 'Photos provided by Pexels' })).toHaveAttribute(
    'href',
    'https://www.pexels.com',
  );
});

// 009 R5.3
test('a photo that fails to load falls back to cover art', async ({ page }) => {
  await page.route('https://images.pexels.com/**', (route) => route.abort());
  await page.goto(`/story/${LINKED_STORY_ID}`);

  await expect(page.getByTestId('cover-art')).toBeVisible();
  await expect(page.locator('img')).toHaveCount(0);
});

// 009 R5.4
for (const scheme of ['light', 'dark'] as const) {
  test(`a story page with a photo has no accessibility violations (${scheme})`, async ({
    page,
  }) => {
    await page.emulateMedia({ colorScheme: scheme });
    await page.goto(`/story/${LINKED_STORY_ID}`);
    await expect(page.getByRole('img', { name: PHOTO.alt })).toBeVisible();
    await page.waitForLoadState('networkidle');

    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();

    expect(
      results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target).join(', ')}`),
    ).toEqual([]);
  });
}
