/**
 * Centralised selector registry.
 *
 * Selector strategy priority (Playwright best-practice):
 *   1. Role-based  → getByRole()        — most resilient, accessibility-aligned
 *   2. Test-id     → getByTestId()      — stable, not tied to CSS class names
 *   3. Label/Text  → getByLabel()       — tied to human-readable labels
 *   4. CSS/XPath   → locator()          — last resort, fragile
 *
 * HOW TO UPDATE
 * ─────────────
 * Open DevTools on the target page, inspect each element, and update the
 * values below.  The rest of the automation (pages, tests) uses these
 * constants — you never need to touch those files when the UI changes.
 */

// ── Login page ────────────────────────────────────────────────────────────────
export const LOGIN_SELECTORS = {
  /**
   * Username field — try role first, fall back to common attribute patterns.
   * Update the `name` / `placeholder` / `id` values to match the real DOM.
   */
  usernameInput:   'input[aria-label="User Name"], input[placeholder="User Name"], input[type="text"]:has-text("User Name")',
  passwordInput:   'input[aria-label="Password"], input[placeholder="Password"], input[type="password"]',
  submitButton:    'button:has-text("Sign In"):visible, button[type="submit"]:visible',

  /** Element that only appears after a successful login (proves we are in) */
  postLoginLandmark: '[class*="dashboard"], [class*="home"], [id*="main-content"], nav[role="navigation"], .sidebar',
} as const;

// ── Chatbot trigger ───────────────────────────────────────────────────────────
export const CHATBOT_TRIGGER_SELECTORS = {
  /**
   * The floating icon / FAB button that opens the chat panel.
   * Add any selector that matches the real DOM — Playwright tries the first
   * one that is visible.
   */
  openButton: [
    '[data-testid="chatbot-icon"]',
    '[data-testid="chat-icon"]',
    '[aria-label*="chat" i]',
    '[aria-label*="assistant" i]',
    '[title*="chat" i]',
    '.chatbot-icon',
    '.chat-icon',
    '.chatbot-trigger',
    '.chat-trigger',
    '#chatbot-icon',
    '#chat-icon',
    'button[class*="chat"]',
    // Angular Material FAB pattern
    'button[mat-fab]',
    'button[mat-mini-fab]',
    // SVG icon inside a button (common in Angular apps)
    'button:has(svg[class*="chat"])',
    'button:has(mat-icon)',
  ].join(', '),
} as const;

// ── Chatbot panel ─────────────────────────────────────────────────────────────
export const CHATBOT_PANEL_SELECTORS = {
  /** The panel / drawer that appears after clicking the trigger */
  container: [
    '[data-testid="chatbot-panel"]',
    '[class*="chatbot-panel"]',
    '[class*="chat-panel"]',
    '[class*="chat-window"]',
    '[class*="chatbot-container"]',
    '[id*="chatbot"]',
    '[id*="chat-window"]',
    'mat-sidenav[role="complementary"]',
    '.chat-container',
    // Match the actual container with AI Assistant
    'div:has-text("AI Assistant")',
  ].join(', '),

  /** Text input where the user types a message */
  inputField: [
    '[data-testid="chat-input"]',
    'textarea[placeholder*="message" i]',
    'textarea[placeholder*="ask" i]',
    'textarea[placeholder*="type" i]',
    'input[placeholder*="message" i]',
    'input[placeholder*="ask" i]',
    'input[type="text"][class*="chat"]',
    '.chat-input textarea',
    '.message-input',
    '#chat-input',
    'mat-form-field textarea',
    // Match the actual input field
    'input[placeholder="Type your message..."]',
    'textarea[placeholder="Type your message..."]',
  ].join(', '),

  /** Send / submit button inside the chat panel */
  sendButton: [
    '[data-testid="chat-send"]',
    'button[aria-label*="send" i]',
    'button[type="submit"]',
    'button:has-text("Send")',
    'button:has(mat-icon:has-text("send"))',
    '.send-btn',
    '.chat-send',
    '#send-button',
    // Match the actual send button
    'button:has-text("Send message")',
  ].join(', '),

  /** Container holding all message bubbles */
  messagesContainer: [
    '[data-testid="chat-messages"]',
    '[class*="chat-messages"]',
    '[class*="message-list"]',
    '[class*="messages-wrapper"]',
    '.conversation',
    '#chat-messages',
  ].join(', '),

  /**
   * Individual bot / assistant message bubble.
   * We query ALL matching elements and take the last one after sending.
   */
  botMessageBubble: [
    '[data-testid="bot-message"]',
    '[data-role="assistant"]',
    '[data-sender="bot"]',
    '[class*="bot-message"]',
    '[class*="assistant-message"]',
    '[class*="chatbot-message"]',
    '[class*="ai-message"]',
    '.bot-bubble',
    '.assistant-bubble',
  ].join(', '),

  /**
   * Typing / loading indicator shown while the bot is generating a reply.
   * Its presence → absence is the most reliable signal that the reply is done.
   */
  typingIndicator: [
    '[data-testid="typing-indicator"]',
    '[class*="typing-indicator"]',
    '[class*="chat-loading"]',
    '[class*="bot-typing"]',
    '[class*="thinking"]',
    '[aria-label*="typing" i]',
    '.dot-flashing',
    '.loading-dots',
    '.typing-dots',
    'mat-progress-bar',
    'mat-spinner',
  ].join(', '),
} as const;
