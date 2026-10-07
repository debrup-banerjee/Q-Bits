import { expect, type Page } from '@playwright/test';

/**
 * Every <img> on the page is a story picture with alt text and a visible credit right under it
 * (spec 009 R5.2). Stories without one show decorative cover art instead.
 */
export async function expectOnlyCreditedImages(page: Page) {
  const images = page.locator('img');
  for (const img of await images.all()) {
    await expect(img).toHaveAttribute('alt', /\S/);
    const caption = img.locator('xpath=following-sibling::figcaption');
    await expect(caption).toHaveText(/^(Illustrative photo by .+ on .+|Image: .+)$/);
  }
}
