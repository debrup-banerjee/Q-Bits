import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end checks against the production build, served by `vite preview`. The API is mocked in
 * each test with page.route, so no backend is needed. Set PW_CHROMIUM_PATH to use a preinstalled
 * Chromium instead of Playwright's own download.
 */
const executablePath = process.env.PW_CHROMIUM_PATH || undefined;

export default defineConfig({
  testDir: 'e2e',
  fullyParallel: true,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4173',
    launchOptions: { executablePath },
  },
  projects: [
    { name: 'phone', use: { ...devices['Pixel 5'], viewport: { width: 360, height: 780 } } },
    {
      name: 'desktop',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 900 } },
    },
  ],
  webServer: {
    command: 'npm run build && npm run preview',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
