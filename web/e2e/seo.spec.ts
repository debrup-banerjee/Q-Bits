import { expect, test } from '@playwright/test';
import { FIRST_INDIA_STORY, mockApi } from './mock-api';

test.beforeEach(async ({ page }) => mockApi(page));

// 007 R5.1
test('the title follows the page during navigation', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle("Q-Bits: today's AI news, explained in plain words");

  await page.goto('/sections');
  await expect(page).toHaveTitle('AI news by section | Q-Bits');

  await page.getByRole('link', { name: 'See all India AI' }).click();
  await expect(page).toHaveTitle('India AI: AI news from the last 72 hours | Q-Bits');

  await page.getByRole('link', { name: FIRST_INDIA_STORY.headline }).first().click();
  await expect(page).toHaveTitle(`${FIRST_INDIA_STORY.headline} | Q-Bits`);
});

// 007 R4.2: the real bundle reads the server's initial data and makes no request for it.
test('a page with initial data renders without fetching its story', async ({ page }) => {
  const path = `/story/${FIRST_INDIA_STORY.id}`;
  const storyRequests: string[] = [];
  page.on('request', (r) => {
    if (r.url().includes(`/api/v1/stories/${FIRST_INDIA_STORY.id}`)) {
      storyRequests.push(r.url());
    }
  });
  // Stand in for the backend: add the initial data element to the built index.html.
  await page.route(`**${path}`, async (route) => {
    const response = await route.fetch();
    const data = JSON.stringify({
      path,
      queries: [
        {
          key: ['story', FIRST_INDIA_STORY.id],
          infinite: false,
          data: FIRST_INDIA_STORY,
          dataAsOf: null,
        },
      ],
    }).replace(/</g, '\\u003c');
    const html = (await response.text()).replace(
      '</head>',
      `<script id="qbits-initial-data" type="application/json">${data}</script></head>`,
    );
    await route.fulfill({ response, body: html });
  });

  await page.goto(path);

  await expect(
    page.getByRole('heading', { level: 1, name: FIRST_INDIA_STORY.headline }),
  ).toBeVisible();
  await expect(page).toHaveTitle(`${FIRST_INDIA_STORY.headline} | Q-Bits`);
  expect(storyRequests).toEqual([]);
});
