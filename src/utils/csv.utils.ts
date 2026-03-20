import * as fs from 'fs';
import * as path from 'path';
import { QuestionRow, ResultRow } from '../types';
import { logger } from './logger';

// ── Reader ─────────────────────────────────────────────────────────────────────

/**
 * Parse a CSV file into QuestionRow objects.
 *
 * Expected columns (case-insensitive, order-independent):
 *   id, question, category
 *
 * Handles:
 *   • BOM (Excel-exported CSVs)
 *   • Quoted fields containing commas
 *   • Windows line-endings (\r\n)
 */
export function readQuestionsCsv(filePath: string): QuestionRow[] {
  if (!fs.existsSync(filePath)) {
    throw new Error(`Input CSV not found: ${filePath}`);
  }

  const raw = fs.readFileSync(filePath, 'utf8').replace(/^\uFEFF/, ''); // strip BOM
  const lines = raw.split(/\r?\n/).filter(l => l.trim().length > 0);

  if (lines.length < 2) {
    throw new Error(`CSV must have a header row + at least one data row. Found: ${lines.length} line(s).`);
  }

  const headers = parseCsvLine(lines[0]).map(h => h.toLowerCase().trim());

  const idx = {
    id:       resolveColumnIndex(headers, ['id', 'no', 'number', '#']),
    question: resolveColumnIndex(headers, ['question', 'query', 'input', 'text']),
    category: resolveColumnIndex(headers, ['category', 'type', 'group', 'topic']),
  };

  const rows: QuestionRow[] = [];

  for (let i = 1; i < lines.length; i++) {
    const cells = parseCsvLine(lines[i]);

    rows.push({
      id:       idx.id       >= 0 ? cells[idx.id]?.trim()       || `Q${String(i).padStart(2, '0')}` : `Q${String(i).padStart(2, '0')}`,
      question: idx.question >= 0 ? cells[idx.question]?.trim() || '' : '',
      category: idx.category >= 0 ? cells[idx.category]?.trim() || 'general' : 'general',
    });
  }

  const valid = rows.filter(r => r.question.length > 0);
  logger.info(`Loaded ${valid.length} questions from ${path.basename(filePath)}`);
  return valid;
}

// ── Writer ─────────────────────────────────────────────────────────────────────

export class CsvResultWriter {
  private readonly filePath: string;
  private count = 0;

  constructor(outputDir: string) {
    fs.mkdirSync(outputDir, { recursive: true });
    const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    this.filePath = path.join(outputDir, `chatbot-qa-${stamp}.csv`);
    this._writeHeader();
    logger.info(`CSV output → ${this.filePath}`);
  }

  private _writeHeader(): void {
    const header = [
      'ID', 'Category', 'Question', 'Answer',
      'Status', 'ResponseTimeMs', 'Timestamp', 'ErrorMessage',
    ].map(esc).join(',') + '\n';

    fs.writeFileSync(this.filePath, header, 'utf8');
  }

  /**
   * Append one result row immediately (crash-safe — never lose already-captured data).
   */
  write(row: ResultRow): void {
    const line = [
      row.id,
      row.category,
      row.question,
      row.answer,
      row.status,
      row.responseTimeMs,
      row.timestamp,
      row.errorMessage ?? '',
    ].map(v => esc(String(v))).join(',') + '\n';

    fs.appendFileSync(this.filePath, line, 'utf8');
    this.count++;
    logger.success(`[${row.id}] saved  status=${row.status}  time=${row.responseTimeMs}ms`);
  }

  getFilePath(): string { return this.filePath; }
  getCount(): number    { return this.count; }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Escape a CSV cell value: wrap in double-quotes, escape inner double-quotes.
 */
function esc(value: string): string {
  return `"${value.replace(/"/g, '""')}"`;
}

/**
 * Parse a single CSV line, respecting quoted fields that may contain commas.
 */
function parseCsvLine(line: string): string[] {
  const result: string[] = [];
  let current = '';
  let inQuotes = false;

  for (let i = 0; i < line.length; i++) {
    const ch = line[i];

    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        // Escaped double-quote inside a quoted field
        current += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (ch === ',' && !inQuotes) {
      result.push(current);
      current = '';
    } else {
      current += ch;
    }
  }

  result.push(current);
  return result;
}

/**
 * Find the index of a column by trying several candidate names.
 * Returns -1 if none match.
 */
function resolveColumnIndex(headers: string[], candidates: string[]): number {
  for (const c of candidates) {
    const idx = headers.indexOf(c);
    if (idx >= 0) return idx;
  }
  return -1;
}
