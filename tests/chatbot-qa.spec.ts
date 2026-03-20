import { test, expect, type TestInfo, type Page } from '@playwright/test';
import { LoginPage }    from '../src/pages/login.page';
import { ChatbotPage }  from '../src/pages/chatbot.page';
import { readQuestionsCsv, CsvResultWriter } from '../src/utils/csv.utils';
import { appConfig }    from '../src/config/app.config';
import { logger }       from '../src/utils/logger';
import { ResultRow }    from '../src/types';

// ── Module-level state (shared across the entire run) ───────────────────────
let csvWriter: CsvResultWriter;
let page: Page;                     // single browser page reused by all tests
let chatbot: ChatbotPage;           // initialized after login

const questions = readQuestionsCsv(appConfig.inputCsvPath);
if (questions.length === 0) {
  throw new Error('Input CSV must contain at least one question');
}

// describe block gives us a separate test per question in the report
// also run serially to avoid hammering the service and preserve predictable CSV order

test.describe.configure({ mode: 'serial' });

test.describe('Chatbot QA', () => {
  test.beforeAll(async ({ browser }) => {
    logger.separator();
    logger.info('Chatbot QA Automation — starting run');
    logger.separator();

    csvWriter = new CsvResultWriter(appConfig.outputCsvDir);

    // launch a single page and perform initial navigation/login once
    page = await browser.newPage();
    const loginPage = new LoginPage(page);
    await loginPage.navigateAndLogin();

    chatbot = new ChatbotPage(page);
    await chatbot.openChatbot();
  });

  test.afterAll(async () => {
    const count = csvWriter.getCount();
    logger.separator();
    logger.info(`Run complete — ${count} result(s) written to ${csvWriter.getFilePath()}`);
    logger.separator();

    // clean up browser page
    if (page) {
      await page.close();
    }
  });

  // dynamically generate one test per question
  for (const { id, question, category } of questions) {
    test(`${id} (${category}) — ${question}`, async ({}, testInfo: TestInfo) => {
      logger.separator();
      logger.info(`[${id}]  Q: "${question}"`);

      // chatbot instance and page already prepared in beforeAll

      let result: ResultRow;

      try {
        const { answer, responseTimeMs } = await chatbot.ask(question);

        expect(
          answer.length,
          `[${id}] Chatbot returned an empty answer`,
        ).toBeGreaterThan(0);

        result = {
          id,
          question,
          category,
          answer,
          status: 'success',
          responseTimeMs,
          timestamp: new Date().toISOString(),
        };

        // attach successful answer to test report
        await testInfo.attach('answer', { body: answer, contentType: 'text/plain' });

      } catch (err) {
        const isTimeout = err instanceof Error && err.message.toLowerCase().includes('timeout');
        const status    = isTimeout ? 'timeout' : 'error';
        const errorMsg  = err instanceof Error ? err.message : String(err);

        logger.error(`[${id}] ${status.toUpperCase()} — ${errorMsg}`);

        result = {
          id,
          question,
          category,
          answer: '',
          status,
          responseTimeMs: 0,
          timestamp: new Date().toISOString(),
          errorMessage: errorMsg,
        };

        await testInfo.attach('error', { body: errorMsg, contentType: 'text/plain' });
      }

      csvWriter.write(result);

      if (appConfig.interQuestionDelayMs > 0) {
        await page.waitForTimeout(appConfig.interQuestionDelayMs);
      }
    });
  }
});
