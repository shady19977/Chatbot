import * as path from 'path';
import { AppConfig } from '../types';

/**
 * Central application configuration.
 * All values resolve from environment variables with sensible defaults.
 *
 * Local development  → create a .env file (see .env.example) and run:
 *                       npx dotenv -e .env -- npx playwright test
 * GitHub Actions     → set secrets in the repository and inject them in the
 *                       workflow env block (see .github/workflows/chatbot-qa.yml)
 */
export const appConfig: AppConfig = {
  // ── Target application ────────────────────────────────────────────────────
  baseUrl: process.env.BASE_URL || 'http://hasibcore.hasib.com.sa:8090',
  loginPath: process.env.LOGIN_PATH || '/HPA.NBU.V3/#!/login',

  // ── Credentials (NEVER commit real values — use env vars / secrets) ───────
  username: process.env.APP_USERNAME || 'portaladmin',
  password: process.env.APP_PASSWORD || '1234560',

  // ── Data paths ────────────────────────────────────────────────────────────
  inputCsvPath: process.env.INPUT_CSV
    ? path.resolve(process.env.INPUT_CSV)
    : path.resolve(__dirname, '../../data/questions.csv'),

  outputCsvDir: process.env.OUTPUT_DIR
    ? path.resolve(process.env.OUTPUT_DIR)
    : path.resolve(__dirname, '../../results'),

  // ── Timing ────────────────────────────────────────────────────────────────
  /** How long (ms) to wait for the chatbot to stop typing and stabilise */
  chatResponseTimeoutMs: Number(process.env.CHAT_RESPONSE_TIMEOUT_MS || 120_000),

  /** Pause between questions so the chatbot doesn't conflate sessions */
  interQuestionDelayMs: Number(process.env.INTER_QUESTION_DELAY_MS || 2_000),
};
