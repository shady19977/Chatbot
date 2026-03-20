import { Page, expect } from '@playwright/test';
import { LOGIN_SELECTORS } from '../config/selectors';
import { appConfig } from '../config/app.config';
import { logger } from '../utils/logger';

/**
 * LoginPage encapsulates all interactions on the login screen.
 *
 * Strategy
 * ─────────
 * 1. Navigate to the configured login path.
 * 2. Wait for Angular's initial bootstrap (networkidle may not fire reliably on
 *    AngularJS hash-based SPAs so we wait for a key form element instead).
 * 3. Fill credentials and submit.
 * 4. Assert that we reached the post-login view before returning.
 */
export class LoginPage {
  constructor(private readonly page: Page) {}

  async navigate(): Promise<void> {
    logger.step('LoginPage', `Navigating to ${appConfig.baseUrl}${appConfig.loginPath}`);
    await this.page.goto(appConfig.baseUrl + appConfig.loginPath, {
      waitUntil: 'domcontentloaded',
    });

    // The page may redirect or load asynchronously — wait for the username
    // input to become visible rather than trusting the load event alone.
    await this.page
      .locator(LOGIN_SELECTORS.usernameInput)
      .first()
      .waitFor({ state: 'visible', timeout: 60_000 });

    logger.success('LoginPage', 'Login form is ready');
  }

  async login(
    username = appConfig.username,
    password = appConfig.password,
  ): Promise<void> {
    logger.step('LoginPage', `Logging in as "${username}"`);

    const usernameField = this.page.locator(LOGIN_SELECTORS.usernameInput).first();
    const passwordField = this.page.locator(LOGIN_SELECTORS.passwordInput).first();
    const submitBtn     = this.page.locator(LOGIN_SELECTORS.submitButton).first();

    // Clear then fill — some Angular inputs need explicit clearing
    await usernameField.click();
    await usernameField.fill('');
    await usernameField.fill(username);

    await passwordField.click();
    await passwordField.fill('');
    await passwordField.fill(password);

    // Take a screenshot just before submission (useful for debugging)
    logger.step('LoginPage', 'Submitting credentials');
    await submitBtn.click();

    // Wait for the application to transition to the authenticated view.
    // We wait for a DOM element that only exists post-login.
    await this.page
      .locator(LOGIN_SELECTORS.postLoginLandmark)
      .first()
      .waitFor({ state: 'visible', timeout: 90_000 });

    logger.success('LoginPage', 'Login successful — authenticated view loaded');
  }

  /** Convenience: navigate and login in one call */
  async navigateAndLogin(
    username = appConfig.username,
    password = appConfig.password,
  ): Promise<void> {
    await this.navigate();
    await this.login(username, password);
  }
}
