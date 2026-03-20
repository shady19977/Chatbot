// ─────────────────────────────────────────────────────────────────────────────
//  Shared types used across the automation suite
// ─────────────────────────────────────────────────────────────────────────────

/** One row from the input CSV file */
export interface QuestionRow {
  id: string;
  question: string;
  category: string;
}

/** One row written to the output CSV file */
export interface ResultRow extends QuestionRow {
  answer: string;
  status: 'success' | 'timeout' | 'error';
  responseTimeMs: number;
  timestamp: string;
  errorMessage?: string;
}

/** Shape of the environment / secrets config */
export interface AppConfig {
  baseUrl: string;
  loginPath: string;
  username: string;
  password: string;
  inputCsvPath: string;
  outputCsvDir: string;
  /** Max ms to wait for the chatbot to finish typing a reply */
  chatResponseTimeoutMs: number;
  /** Pause between consecutive questions to avoid rate-limiting */
  interQuestionDelayMs: number;
}
