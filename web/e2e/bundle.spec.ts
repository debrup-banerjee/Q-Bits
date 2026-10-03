import { expect, test } from '@playwright/test';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

/** Keeps the first download small so the home page is quick on a phone (spec 003 non-functional). */
const BUDGET_GZIP_KB = 120;

test('JavaScript bundle stays within budget', async ({}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'checked once');
  const dir = join(process.cwd(), 'dist', 'assets');
  const total = readdirSync(dir)
    .filter((f) => f.endsWith('.js'))
    .map((f) => gzipSync(readFileSync(join(dir, f))).length)
    .reduce((a, b) => a + b, 0);

  expect(Math.round(total / 1024)).toBeLessThanOrEqual(BUDGET_GZIP_KB);
});
