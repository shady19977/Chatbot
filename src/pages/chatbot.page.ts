import { Page, Locator } from '@playwright/test';
import { CHATBOT_TRIGGER_SELECTORS, CHATBOT_PANEL_SELECTORS } from '../config/selectors';
import { appConfig } from '../config/app.config';
import { logger } from '../utils/logger';

/**
 * ChatbotPage encapsulates every interaction with the chatbot widget.
 *
 * Response-ready detection (in priority order)
 * ─────────────────────────────────────────────
 * 1. Typing indicator lifecycle  → spinner appears then disappears
 * 2. New message count           → a new bot bubble appears in the DOM
 * 3. Text-stability polling      → text stops changing for STABLE_MS (streaming)
 *
 * Any of the three conditions being satisfied is enough to return.
 */
export class ChatbotPage {
  /** How many ms of text-stability counts as "bot has finished typing" */
  private static readonly STABLE_MS = 2_500;
  /** Polling interval for text-stability check */
  private static readonly POLL_MS   = 500;

  constructor(private readonly page: Page) {}

  // ── Open the chatbot ────────────────────────────────────────────────────────

  /**
   * Click the floating chatbot icon and wait for the chat panel to be visible.
   */
  async openChatbot(): Promise<void> {
    logger.step('ChatbotPage', 'Looking for chatbot trigger button');

    const trigger = this.page.locator(CHATBOT_TRIGGER_SELECTORS.openButton).first();

    await trigger.waitFor({ state: 'visible', timeout: 30_000 });
    logger.success('ChatbotPage', 'Trigger button found — clicking');
    await trigger.click();

    // Wait for the chat panel container to become visible
    await this.page
      .locator(CHATBOT_PANEL_SELECTORS.container)
      .first()
      .waitFor({ state: 'visible', timeout: 30_000 });

    // Also wait for the input field to be ready
    await this.page
      .locator(CHATBOT_PANEL_SELECTORS.inputField)
      .first()
      .waitFor({ state: 'visible', timeout: 15_000 });

    logger.success('ChatbotPage', 'Chat panel is open and ready');
  }

  // ── Send a message and capture the reply ────────────────────────────────────

  /**
   * Type `question` into the chat input, submit it, wait for the bot to finish
   * responding, and return the answer text + response time in ms.
   */
  async ask(question: string): Promise<{ answer: string; responseTimeMs: number }> {
    const t0 = Date.now();

    const inputField  = this.page.locator(CHATBOT_PANEL_SELECTORS.inputField).first();
    const sendButton  = this.page.locator(CHATBOT_PANEL_SELECTORS.sendButton).first();
    const botBubbles  = this.page.locator(CHATBOT_PANEL_SELECTORS.botMessageBubble);

    // Count how many bot messages exist right now (used for "new message" check)
    const prevCount = await botBubbles.count();

    // ── Type the question ───────────────────────────────────────────────────
    await inputField.waitFor({ state: 'visible', timeout: 15_000 });
    await inputField.click();
    await inputField.fill('');           // ensure field is clear
    await inputField.fill(question);

    logger.step('ChatbotPage', `Sending: "${question.slice(0, 80)}${question.length > 80 ? '…' : ''}"`);

    // ── Submit ──────────────────────────────────────────────────────────────
    const canClick = await sendButton.isVisible().catch(() => false);
    if (canClick) {
      await sendButton.click();
    } else {
      // Fall back to Enter key (common in Angular chat inputs)
      await inputField.press('Enter');
    }

    // ── Wait for the bot to respond ─────────────────────────────────────────
    await this._waitForBotResponse(prevCount);

    const answer = await this._getLatestBotMessage();
    const responseTimeMs = Date.now() - t0;

    logger.success(
      'ChatbotPage',
      `Got answer in ${responseTimeMs}ms — "${answer.slice(0, 100)}${answer.length > 100 ? '…' : ''}"`,
    );

    return { answer, responseTimeMs };
  }

  // ── Private helpers ─────────────────────────────────────────────────────────

  /**
   * Three-strategy wait (stops as soon as any one succeeds).
   */
  private async _waitForBotResponse(prevBotMessageCount: number): Promise<void> {
    const deadline = Date.now() + appConfig.chatResponseTimeoutMs;

    // ── Strategy 1 : typing indicator lifecycle ───────────────────────────
    const typingIndicator = this.page.locator(CHATBOT_PANEL_SELECTORS.typingIndicator).first();

    try {
      // Give the indicator 8 s to appear — it won't appear on very fast replies
      await typingIndicator.waitFor({ state: 'visible', timeout: 8_000 });
      logger.step('ChatbotPage', 'Typing indicator appeared — waiting for it to hide');

      await typingIndicator.waitFor({
        state: 'hidden',
        timeout: Math.max(1_000, deadline - Date.now()),
      });

      // Allow a small grace period for streaming text to finalise
      await this.page.waitForTimeout(600);
      logger.step('ChatbotPage', 'Typing indicator hidden');
      return;
    } catch {
      // Indicator never appeared or disappeared too quickly — fall through
    }

    // ── Strategy 2 : new bot message element in DOM ───────────────────────
    const selector = CHATBOT_PANEL_SELECTORS.botMessageBubble;

    try {
      await this.page.waitForFunction(
        ({ sel, prev }: { sel: string; prev: number }) =>
          document.querySelectorAll(sel).length > prev,
        { sel: selector, prev: prevBotMessageCount },
        { timeout: Math.max(1_000, deadline - Date.now()) },
      );
      logger.step('ChatbotPage', 'New bot message element detected');
    } catch {
      logger.warn('ChatbotPage', 'New message element not detected — falling back to text-stability check');
    }

    // ── Strategy 3 : wait for text to stop changing (streaming / typewriter) ─
    await this._waitForTextStability(Math.max(1_000, deadline - Date.now()));
  }

  /**
   * Poll the latest bot message until its text has been stable for STABLE_MS.
   * This covers streaming / typewriter-style responses that update the DOM
   * incrementally.
   */
  private async _waitForTextStability(remainingMs: number): Promise<void> {
    const deadline   = Date.now() + remainingMs;
    let lastText     = '';
    let stableStart  = Date.now();

    logger.step('ChatbotPage', 'Running text-stability check');

    while (Date.now() < deadline) {
      const current = await this._getLatestBotMessage();

      if (current !== lastText) {
        lastText    = current;
        stableStart = Date.now();
      } else if (current.length > 0 && Date.now() - stableStart >= ChatbotPage.STABLE_MS) {
        return;
      }

      await this.page.waitForTimeout(ChatbotPage.POLL_MS);
    }

    // Timed out — return whatever we have; the test will mark it accordingly
    logger.warn('ChatbotPage', 'Text-stability timeout — returning partial answer');
  }

  /**
   * Get the innerText of the last bot message bubble currently in the DOM.
   */
  private async _getLatestBotMessage(): Promise<string> {
    const bubbles = this.page.locator(CHATBOT_PANEL_SELECTORS.botMessageBubble);
    const count   = await bubbles.count();
    if (!count) return '';

    const last = bubbles.nth(count - 1);
    return (await last.innerText().catch(() => '')).trim();
  }
}
