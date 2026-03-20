import { defineConfig, devices } from '@playwright/test';
import * as path from 'path';

/**
 * Playwright configuration
 * Reads runtime values from environment variables so nothing is hard-coded.
 * Local : copy .env.example → .env and fill in values
 * CI    : set GitHub Actions secrets / env block in the workflow YAML
 */
export default defineConfig({
  testDir: './tests',

  // One test at a time — the chatbot is a single stateful conversation
  fullyParallel: false,
  workers: 1,

  // Retry once in CI so transient network/loading blips don't fail the run
  retries: process.env.CI ? 1 : 0,

  // Global test timeout (chatbot pages can be slow to load + respond)
  timeout: 700_000,

  expect: { timeout: 60_000 },

  // ── Reporter ──────────────────────────────────────────────────────────────
  // HTML report is explicitly named index.html so it can be committed to the
  // repo and served directly via GitHub Pages without any redirect tricks.
  reporter: [
    [
      'html',
      {
        outputFolder: 'playwright-report',
        fileName: 'index.html',
        open: 'never',
      },
    ],
    ['list'],
    ['json', { outputFile: 'playwright-report/results.json' }],
  ],

  use: {
    baseURL: process.env.BASE_URL || 'http://hasibcore.hasib.com.sa:8090',

    // Always headless — overridden to false only by `npm run test:headed`
    headless: true,

    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'on-first-retry',

    viewport: { width: 1920, height: 1080 },

    // Per-action / navigation timeouts
    actionTimeout: 45_000,
    navigationTimeout: 90_000,

    // Keep slow-motion at 0 for CI; set e.g. 200 locally for debugging
    launchOptions: {
      slowMo: Number(process.env.SLOW_MO || 0),
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--disable-gpu',
      ],
    },
  },

  // Output folder for per-test artifacts (screenshots, videos, traces)
  outputDir: path.join('test-artifacts'),

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
