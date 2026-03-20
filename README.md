# Chatbot QA Automation

Production-grade Playwright + TypeScript automation suite that:
1. Logs in to the HPA portal
2. Opens the chatbot widget
3. Sends questions loaded from a CSV file
4. Captures each chatbot reply
5. Writes results to a timestamped CSV
6. Publishes an `index.html` HTML report committed back to the repo branch

---

## Project Structure

```
chatbot-qa/
├── .github/
│   └── workflows/
│       └── chatbot-qa.yml          # CI pipeline (headless + commit report)
│
├── data/
│   └── questions.csv               # ← put your questions here
│
├── results/                        # created at runtime — output CSVs land here
│
├── src/
│   ├── config/
│   │   ├── app.config.ts           # all runtime values (reads from env vars)
│   │   └── selectors.ts            # ALL CSS / role selectors in one place
│   ├── pages/
│   │   ├── login.page.ts           # Login Page Object
│   │   └── chatbot.page.ts         # Chatbot Page Object
│   ├── types/
│   │   └── index.ts                # shared TypeScript interfaces
│   └── utils/
│       ├── csv.utils.ts            # CSV reader + crash-safe writer
│       └── logger.ts               # structured timestamped logger
│
├── tests/
│   └── chatbot-qa.spec.ts          # main Playwright test
│
├── playwright.config.ts            # Playwright + HTML report config
├── tsconfig.json
├── package.json
├── .env.example                    # template — copy to .env for local runs
└── .gitignore
```

---

## Prerequisites

- Node.js ≥ 18
- `npm install`
- `npx playwright install chromium`

---

## Local Development

```bash
# 1. Copy the env template
cp .env.example .env

# 2. Edit .env with real credentials (never commit this file!)
#    BASE_URL, APP_USERNAME, APP_PASSWORD are the minimum required values.

# 3. Run headlessly (same as CI)
npm test

# 4. Run with a visible browser window (great for debugging)
npm run test:headed

# 5. Step-through debugger
npm run test:debug

# 6. Interactive Playwright UI
npm run test:ui

# 7. Open the last HTML report
npm run report
```

---

## Per‑question tests 🔍

The Playwright suite now generates **one test case per row** in the
`questions.csv`.  Each question appears individually in the HTML report, so
it's easy to tell at a glance which question failed and view the chatbot's
reply (or error) alongside the failure.  The test name includes the question
ID, category and text, and the answer/error is attached to the test as a
plain‑text artefact.

Tests run **serially in a single browser session**: the user logs in only
once at the start, the chatbot widget is opened once, and all subsequent
question tests reuse that same page.  This avoids tearing down the browser
between questions while still giving separate entries in the report.


## Questions CSV Format

Edit `data/questions.csv` — one row per question:

```csv
id,question,category
Q01,What services does the portal provide?,general
Q02,How do I submit a new request?,requests
```

Column names are case-insensitive. The `id` column is auto-generated if omitted.

---

## Output CSV Format

Each run writes `results/chatbot-qa-<timestamp>.csv`:

| Column | Description |
|---|---|
| `ID` | Question ID |
| `Category` | Category from input CSV |
| `Question` | The question text sent |
| `Answer` | The chatbot's full reply |
| `Status` | `success` / `timeout` / `error` |
| `ResponseTimeMs` | Time from send → stable reply (ms) |
| `Timestamp` | ISO-8601 |
| `ErrorMessage` | Populated only on failure |

---

## Updating Selectors

When the chatbot UI changes, open `src/config/selectors.ts` and update the
relevant selector strings.  **No other file needs to change.**

The file is heavily commented — each selector has a strategy note explaining
which attribute to inspect in DevTools.

---

## GitHub Actions

### Required Secrets

Set these under **Repo → Settings → Secrets and variables → Actions**:

| Secret | Description |
|---|---|
| `APP_USERNAME` | Portal login username |
| `APP_PASSWORD` | Portal login password |
| `BASE_URL` | Base URL (if different from default) |

### Triggers

| Event | Behaviour |
|---|---|
| Push to `main` / `master` / `develop` | Runs automatically |
| Pull request to `main` / `master` | Runs automatically |
| **Actions → Run workflow** | Manual trigger with optional URL override |

### What the pipeline does

1. Installs Node 20 + Chromium
2. Runs all tests **headlessly** (`headless: true` + `--no-sandbox` flags)
3. Produces `playwright-report/index.html`
4. Uploads report + CSV as **downloadable artifacts** (30-day retention)
5. **Commits `playwright-report/index.html` back** to the triggering branch
6. Prints a summary of where to find the report

### GitHub Pages (optional)

1. Repo → Settings → Pages
2. Source: Deploy from a branch
3. Branch: `main`, Folder: `/playwright-report`
4. Report URL: `https://<org>.github.io/<repo>/`

---

## Chatbot Response Wait Strategy

The `ChatbotPage.ask()` method uses three detection strategies in priority order:

1. **Typing indicator lifecycle** — waits for the spinner/dots to appear then disappear (most reliable)
2. **New message count** — polls until a new bot bubble appears in the DOM
3. **Text-stability check** — polls the last bot message until it stops changing for 2.5 s (handles streaming / typewriter responses)

## Running Project

1. pm install playwright@latest
2. npx playwright install
3. npm run test:headed
4. npx playwright show-report

All timeouts are configurable via environment variables.
