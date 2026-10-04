import { expect, test, type Page } from '@playwright/test';
import { FIRST_INDIA_STORY, mockApi } from './mock-api';

test.beforeEach(async ({ page }) => mockApi(page));

const PAGES = [
  ['/', 'AI Latest'],
  ['/sections', 'Browse by section'],
  ['/section/india-ai', 'India AI'],
  [`/story/${FIRST_INDIA_STORY.id}`, FIRST_INDIA_STORY.headline],
  ['/about', 'How Q-Bits works'],
] as const;

async function horizontalOverflow(page: Page) {
  return page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
}

/** Number of columns in the grid holding a section's story cards on /sections. */
async function overviewColumns(page: Page, section: string) {
  const region = page.getByRole('region', { name: section });
  const grid = region.getByRole('article').first().locator('..');
  return grid.evaluate((el) => getComputedStyle(el).gridTemplateColumns.split(' ').length);
}

// 003 R9.1
test('the section grids use one column on phones', async ({ page }, info) => {
  test.skip(info.project.name !== 'phone', 'phone width only');
  await page.goto('/sections');
  await expect(
    page.getByRole('region', { name: 'AI Wire' }).getByRole('article'),
  ).toHaveCount(2);

  expect(await overviewColumns(page, 'AI Wire')).toBe(1);
  expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0);
});

// 003 R9.1
for (const [width, columns] of [
  [768, 2],
  [1280, 3],
  [1440, 3],
] as const) {
  test(`the section grids use ${columns} columns at ${width}px`, async ({ page }, info) => {
    test.skip(info.project.name !== 'desktop', 'wide layouts are checked on desktop');
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/sections');
    await expect(
      page.getByRole('region', { name: 'AI Wire' }).getByRole('article'),
    ).toHaveCount(2);

    expect(await overviewColumns(page, 'AI Wire')).toBe(columns);
    expect(await horizontalOverflow(page)).toBeLessThanOrEqual(0);
  });
}

// 003 R9.1: no horizontal scrolling on any page, at the narrowest and widest supported widths
for (const [path, heading] of PAGES) {
  test(`${path} has no horizontal scroll at 360px and 1440px`, async ({ page }, info) => {
    const widths = info.project.name === 'desktop' ? [1440] : [360];
    for (const width of widths) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto(path);
      await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();

      expect(await horizontalOverflow(page), `${path} at ${width}px`).toBeLessThanOrEqual(0);
    }
  });
}

// 003 R9.2
for (const path of ['/sections', `/story/${FIRST_INDIA_STORY.id}`]) {
  test(`story summaries on ${path} are at least 16px with lines of at most 75 characters`, async ({
    page,
  }, info) => {
    if (info.project.name === 'desktop') await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(path);
    const summary = page
      .getByRole('article')
      .first()
      .getByText(/^According to The Hindu/);
    await expect(summary).toBeVisible();

    const { fontSize, width, maxLine } = await summary.evaluate((el) => {
      const probe = document.createElement('span');
      probe.style.cssText = 'position:absolute;visibility:hidden;width:75ch;font:inherit';
      el.appendChild(probe);
      const maxLine = probe.getBoundingClientRect().width;
      probe.remove();
      return {
        fontSize: parseFloat(getComputedStyle(el).fontSize),
        width: el.getBoundingClientRect().width,
        maxLine,
      };
    });

    expect(fontSize).toBeGreaterThanOrEqual(16);
    expect(width).toBeLessThanOrEqual(maxLine);
  });
}
