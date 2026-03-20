/**
 * Lightweight structured logger.
 * Outputs timestamped, colour-coded lines in CI-friendly format.
 */

const RESET  = '\x1b[0m';
const CYAN   = '\x1b[36m';
const GREEN  = '\x1b[32m';
const YELLOW = '\x1b[33m';
const RED    = '\x1b[31m';
const GREY   = '\x1b[90m';

function ts(): string {
  return new Date().toISOString();
}

export const logger = {
  info(msg: string, ...args: unknown[]): void {
    console.log(`${CYAN}[INFO]${RESET}  ${GREY}${ts()}${RESET}  ${msg}`, ...args);
  },
  success(msg: string, ...args: unknown[]): void {
    console.log(`${GREEN}[OK]${RESET}    ${GREY}${ts()}${RESET}  ${msg}`, ...args);
  },
  warn(msg: string, ...args: unknown[]): void {
    console.warn(`${YELLOW}[WARN]${RESET}  ${GREY}${ts()}${RESET}  ${msg}`, ...args);
  },
  error(msg: string, ...args: unknown[]): void {
    console.error(`${RED}[ERR]${RESET}   ${GREY}${ts()}${RESET}  ${msg}`, ...args);
  },
  step(label: string, msg: string): void {
    console.log(`${CYAN}[STEP]${RESET}  ${GREY}${ts()}${RESET}  ${label.padEnd(20)} ${msg}`);
  },
  separator(): void {
    console.log(GREY + '─'.repeat(72) + RESET);
  },
};
