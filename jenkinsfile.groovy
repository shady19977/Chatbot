pipeline {
    agent any

    // =========================================================
    //  BUILD PARAMETERS — exposed in "Build with Parameters"
    // =========================================================
    parameters {

        // ── Browser ──────────────────────────────────────────
        choice(
            name: 'BROWSER',
            choices: ['chromium', 'firefox', 'webkit', 'all'],
            description: '''
                <b>🌐 Browser Selection</b><br/>
                • chromium  — Google Chrome engine (default)<br/>
                • firefox   — Mozilla Firefox engine<br/>
                • webkit    — Safari engine<br/>
                • all       — Run on all 3 browsers in parallel
            '''
        )

        // ── Test-file selection mode ──────────────────────────
        choice(
            name: 'TEST_SELECTION_MODE',
            choices: ['ALL', 'SINGLE', 'MULTIPLE'],
            description: '''
                <b>🗂 Test Selection Mode</b><br/>
                • ALL      — Execute every spec file discovered in /tests<br/>
                • SINGLE   — Run one specific spec file<br/>
                • MULTIPLE — Run a comma-separated list of spec files
            '''
        )

        // ── Spec file(s) — used when mode = SINGLE or MULTIPLE ─
        string(
            name: 'TEST_FILES',
            defaultValue: '',
            description: '''
                <b>📄 Test File(s)</b> — Required when mode is SINGLE or MULTIPLE.<br/>
                SINGLE   → e.g. <code>tests/login.spec.ts</code><br/>
                MULTIPLE → e.g. <code>tests/login.spec.ts,tests/checkout.spec.ts</code>
            '''
        )

        // ── Parallel workers ──────────────────────────────────
        choice(
            name: 'PARALLEL_WORKERS',
            choices: ['2', '1', '4', '8'],
            description: '<b>⚙️ Parallel Workers</b> — Number of concurrent test processes'
        )

        // ── Headed / headless ─────────────────────────────────
        booleanParam(
            name: 'HEADED_MODE',
            defaultValue: false,
            description: '<b>🖥 Headed Mode</b> — Show browser UI. Keep false for CI servers.'
        )

        // ── Execution timing ──────────────────────────────────
        choice(
            name: 'EXECUTION_TIMING',
            choices: ['NOW', 'SCHEDULED'],
            description: '''
                <b>⏰ Execution Timing</b><br/>
                • NOW       — Start immediately<br/>
                • SCHEDULED — Wait until SCHEDULE_DATETIME
            '''
        )

        string(
            name: 'SCHEDULE_DATETIME',
            defaultValue: '',
            description: '''
                <b>📅 Scheduled Date/Time</b> — Used only when EXECUTION_TIMING = SCHEDULED.<br/>
                Format: <code>yyyy-MM-dd HH:mm</code>  (e.g. 2025-08-01 09:30)
            '''
        )

        // ── Email settings ────────────────────────────────────
        booleanParam(
            name: 'SEND_EMAIL',
            defaultValue: true,
            description: '<b>📧 Send Email Report</b> — Email results with full artifact ZIP'
        )

        string(
            name: 'EMAIL_RECIPIENTS',
            defaultValue: 'qa-team@company.com',
            description: '<b>📬 Email Recipients</b> — Comma-separated list of addresses'
        )
    }

    // =========================================================
    //  ENVIRONMENT
    // =========================================================
    environment {
        CI                      = 'true'
        PLAYWRIGHT_BROWSERS_PATH = '0'

        // Timestamped names so every build keeps its own artifacts
        BUILD_TS    = "${new Date().format('yyyyMMdd_HHmmss')}"
        REPORT_DIR  = "playwright-report"
        RESULTS_DIR = "test-results"
        ZIP_NAME    = "playwright-artifacts-build-${BUILD_NUMBER}.zip"
    }

    // =========================================================
    //  STAGES
    // =========================================================
    stages {

        // ─────────────────────────────────────────────────────
        stage('⏰  Schedule / Validate') {
        // ─────────────────────────────────────────────────────
            steps {
                script {
                    echo "════════════════════════════════════════════"
                    echo " Playwright CI Pipeline  |  Build #${BUILD_NUMBER}"
                    echo "════════════════════════════════════════════"

                    // Validate required params
                    if (params.TEST_SELECTION_MODE != 'ALL' && !params.TEST_FILES?.trim()) {
                        error("❌  TEST_FILES is required when TEST_SELECTION_MODE is ${params.TEST_SELECTION_MODE}")
                    }

                    // Handle scheduled execution
                    if (params.EXECUTION_TIMING == 'SCHEDULED') {
                        if (!params.SCHEDULE_DATETIME?.trim()) {
                            error("❌  SCHEDULE_DATETIME is required when EXECUTION_TIMING = SCHEDULED")
                        }

                        def fmt       = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                        def target    = fmt.parse(params.SCHEDULE_DATETIME)
                        def now       = new Date()
                        def delayMs   = target.time - now.time

                        if (delayMs <= 0) {
                            error("❌  SCHEDULE_DATETIME '${params.SCHEDULE_DATETIME}' is in the past!")
                        }

                        def delayMin = (delayMs / 60_000).toLong()
                        echo "⏳  Waiting ${delayMin} minute(s) until ${params.SCHEDULE_DATETIME} …"
                        sleep(time: delayMs, unit: 'MILLISECONDS')
                        echo "✅  Scheduled time reached — proceeding with execution."
                    }

                    // Print effective config
                    echo """
╔═══════════════════════════════════════════╗
║         EFFECTIVE CONFIGURATION           ║
╠═══════════════════════════════════════════╣
║  Browser       : ${params.BROWSER.padRight(30)}║
║  Test Mode     : ${params.TEST_SELECTION_MODE.padRight(30)}║
║  Test Files    : ${(params.TEST_FILES ?: '(all)').padRight(30)}║
║  Workers       : ${params.PARALLEL_WORKERS.padRight(30)}║
║  Headed        : ${params.HEADED_MODE.toString().padRight(30)}║
║  Timing        : ${params.EXECUTION_TIMING.padRight(30)}║
║  Send Email    : ${params.SEND_EMAIL.toString().padRight(30)}║
║  Recipients    : ${params.EMAIL_RECIPIENTS.padRight(30)}║
╚═══════════════════════════════════════════╝"""
                }
            }
        }

        // ─────────────────────────────────────────────────────
        stage('📦  Checkout') {
        // ─────────────────────────────────────────────────────
            steps {
                echo "🔄  Cloning repository …"
                git(
                    changelog : false,
                    poll      : false,
                    url       : 'https://github.com/shady19977/Chatbot.git',
                    branch    : 'master'
                )
                script {
                    // Windows-compatible git commands
                    env.GIT_SHORT_COMMIT = bat(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.GIT_BRANCH_NAME  = bat(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()
                    echo "✅  Checked out branch '${env.GIT_BRANCH_NAME}' @ ${env.GIT_SHORT_COMMIT}"
                }
            }
        }

        // ─────────────────────────────────────────────────────
        stage('🔧  Install Dependencies') {
        // ─────────────────────────────────────────────────────
            steps {
                nodejs(nodeJSInstallationName: 'NodeJS-18') {
                    echo "📥  Installing npm packages …"
                    bat 'npm ci --prefer-offline'

                    echo "🎭  Installing Playwright browser(s) …"
                    script {
                        def targets = (params.BROWSER == 'all') ? 'chromium firefox webkit' : params.BROWSER
                        bat "npx playwright install ${targets} --with-deps"
                    }
                }
            }
        }

        // ─────────────────────────────────────────────────────
        stage('🔍  Discover Test Files') {
        // ─────────────────────────────────────────────────────
            steps {
                script {
                    echo "🔍  Scanning for spec files in ./tests …"

                    // Windows-compatible file discovery
                    def rawOutput = bat(
                        script: '@echo off & for /r "tests" %i in (*.spec.ts *.test.ts) do @echo %i',
                        returnStdout: true
                    ).trim()

                    def discovered = rawOutput
                        .split(/\r?\n/)
                        .collect { it.trim() }
                        .findAll  { it && !it.contains("File not found") }

                    if (discovered.isEmpty()) {
                        error("❌  No *.spec.ts / *.test.ts files found under ./tests")
                    }

                    echo "Found ${discovered.size()} test file(s):"
                    discovered.each { f -> echo "   • ${f}" }

                    // ── Build the npx playwright test command ──────
                    def cmd = "npx playwright test"

                    switch (params.TEST_SELECTION_MODE) {
                        case 'SINGLE':
                            cmd += " \"${params.TEST_FILES.trim()}\""
                            break
                        case 'MULTIPLE':
                            params.TEST_FILES.split(',').each { f ->
                                if (f.trim()) cmd += " \"${f.trim()}\""
                            }
                            break
                        // ALL  → no file argument; Playwright picks up everything
                    }

                    if (params.BROWSER != 'all') {
                        cmd += " --project=${params.BROWSER}"
                    }

                    cmd += " --workers=${params.PARALLEL_WORKERS}"

                    if (params.HEADED_MODE) { cmd += " --headed" }

                    cmd += " --reporter=html,junit,list"

                    env.PW_TEST_CMD = cmd
                    echo "📝  Resolved command → ${cmd}"
                }
            }
        }

        // ─────────────────────────────────────────────────────
        stage('🚀  Execute Tests') {
        // ─────────────────────────────────────────────────────
            steps {
                echo "🚀  Running: ${env.PW_TEST_CMD}"
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    nodejs(nodeJSInstallationName: 'NodeJS-18') {
                        bat "${env.PW_TEST_CMD}"
                    }
                }
            }
            post {
                always {
                    echo "📊  Test execution finished (build status: ${currentBuild.currentResult})"
                }
            }
        }

        // ─────────────────────────────────────────────────────
        stage('📦  Package Artifacts') {
        // ─────────────────────────────────────────────────────
            steps {
                echo "🗜  Packaging screenshots, logs, videos, reports → ${env.ZIP_NAME}"
                script {
                    // Windows-compatible packaging using PowerShell
                    bat """
                        @echo off
                        powershell -Command "
                            \$filesToZip = @();
                            if (Test-Path '${env.REPORT_DIR}') { 
                                \$filesToZip += Get-ChildItem -Path '${env.REPORT_DIR}' -Recurse
                                Write-Host 'HTML report found'
                            } else { 
                                Write-Host 'No HTML report dir' 
                            }
                            if (Test-Path '${env.RESULTS_DIR}') { 
                                \$filesToZip += Get-ChildItem -Path '${env.RESULTS_DIR}' -Recurse
                                Write-Host 'Test results found'
                            } else { 
                                Write-Host 'No test-results dir' 
                            }
                            if (\$filesToZip.Count -gt 0) {
                                Compress-Archive -Path \$filesToZip -DestinationPath '${env.ZIP_NAME}' -Force
                                Write-Host 'ZIP created: ${env.ZIP_NAME}'
                            } else {
                                Write-Host 'No files to zip'
                            }
                        "
                    """
                }
            }
        }

        // ─────────────────────────────────────────────────────
        stage('📌  Publish to Jenkins') {
        // ─────────────────────────────────────────────────────
            steps {
                // JUnit XML — keeps all historical runs on the build page
                junit(
                    testResults     : "${env.RESULTS_DIR}/**/junit.xml",
                    allowEmptyResults: true,
                    keepLongStdio   : true
                )

                // HTML Report — accessible from the left-hand build menu
                publishHTML([
                    allowMissing         : true,
                    alwaysLinkToLastBuild: true,
                    keepAll              : true,      // ← preserves EVERY build's report
                    reportDir            : "${env.REPORT_DIR}",
                    reportFiles          : 'index.html',
                    reportName           : "🎭 Playwright Report — Build #${BUILD_NUMBER}"
                ])

                // Archive the ZIP and all raw artifacts for download
                archiveArtifacts(
                    artifacts          : "${env.ZIP_NAME}, ${env.RESULTS_DIR}/**/*",
                    fingerprint        : true,
                    allowEmptyArchive  : true
                )

                echo "✅  Reports published. View at: ${BUILD_URL}Playwright_Report/"
            }
        }

        // ─────────────────────────────────────────────────────
        stage('📧  Send Email Report') {
        // ─────────────────────────────────────────────────────
            when {
                expression { params.SEND_EMAIL == true }
            }
            steps {
                script {
                    sendPlaywrightEmail()
                }
            }
        }
    }

    // =========================================================
    //  POST
    // =========================================================
    post {
        always {
            echo "🧹  Workspace cleanup …"
            cleanWs(
                cleanWhenSuccess   : false,   // keep artifacts on success for debugging
                cleanWhenUnstable  : false,
                cleanWhenFailure   : false,
                cleanWhenNotBuilt  : true
            )
        }
        success  { echo "✅  All tests passed — Build #${BUILD_NUMBER} SUCCESS" }
        unstable { echo "⚠️   Some tests FAILED — review HTML report and email" }
        failure  { echo "❌  Pipeline FAILED — check console log for errors" }
    }
}

// =============================================================
//  HELPER — Build and send the detailed HTML email
// =============================================================
def sendPlaywrightEmail() {
    def status   = currentBuild.currentResult
    def color    = (status == 'SUCCESS') ? '#2ecc71' : (status == 'UNSTABLE') ? '#f39c12' : '#e74c3c'
    def icon     = (status == 'SUCCESS') ? '✅' : (status == 'UNSTABLE') ? '⚠️' : '❌'
    def subject  = "${icon} Playwright Tests [${status}] — Build #${BUILD_NUMBER} — ${params.BROWSER.toUpperCase()}"

    def body = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<style>
  body        { font-family: 'Segoe UI', Arial, sans-serif; background:#f5f6fa; color:#2c3e50; margin:0; padding:0; }
  .wrapper    { max-width:700px; margin:30px auto; background:#fff; border-radius:10px; overflow:hidden; box-shadow:0 2px 12px rgba(0,0,0,.12); }
  .header     { background:${color}; padding:28px 32px; }
  .header h1  { margin:0; color:#fff; font-size:22px; }
  .header p   { margin:4px 0 0; color:rgba(255,255,255,.85); font-size:13px; }
  .body       { padding:28px 32px; }
  table       { width:100%; border-collapse:collapse; margin-top:14px; font-size:13px; }
  th          { background:#f0f2f8; text-align:left; padding:9px 12px; font-weight:600; }
  td          { padding:9px 12px; border-bottom:1px solid #ecf0f1; }
  .badge      { display:inline-block; padding:2px 9px; border-radius:12px; font-size:11px; font-weight:700; color:#fff; background:${color}; }
  .btn        { display:inline-block; margin:6px 4px 0 0; padding:9px 18px; background:${color}; color:#fff; text-decoration:none; border-radius:6px; font-size:12px; font-weight:600; }
  .footer     { background:#f0f2f8; padding:14px 32px; font-size:11px; color:#95a5a6; text-align:center; }
</style>
</head>
<body>
<div class="wrapper">

  <div class="header">
    <h1>${icon}&nbsp; Playwright Test Report</h1>
    <p>Build #${BUILD_NUMBER} &nbsp;|&nbsp; ${new Date().format('dd MMM yyyy HH:mm')} &nbsp;|&nbsp; ${status}</p>
  </div>

  <div class="body">

    <h3 style="margin-top:0">📊 Execution Summary</h3>
    <table>
      <tr><th>Parameter</th><th>Value</th></tr>
      <tr><td>Status</td>           <td><span class="badge">${status}</span></td></tr>
      <tr><td>Browser</td>          <td>${params.BROWSER}</td></tr>
      <tr><td>Test Selection</td>   <td>${params.TEST_SELECTION_MODE}</td></tr>
      <tr><td>Test Files</td>       <td>${params.TEST_FILES ?: '(all tests)'}</td></tr>
      <tr><td>Parallel Workers</td> <td>${params.PARALLEL_WORKERS}</td></tr>
      <tr><td>Git Branch</td>       <td>${env.GIT_BRANCH_NAME ?: 'N/A'}</td></tr>
      <tr><td>Git Commit</td>       <td>${env.GIT_SHORT_COMMIT ?: 'N/A'}</td></tr>
      <tr><td>Jenkins Node</td>     <td>${NODE_NAME}</td></tr>
      <tr><td>Duration</td>         <td>${currentBuild.durationString}</td></tr>
    </table>

    <h3>🔗 Quick Links</h3>
    <a href="${BUILD_URL}" class="btn">🏗 Build Page</a>
    <a href="${BUILD_URL}Playwright_Report/" class="btn">📄 HTML Report</a>
    <a href="${BUILD_URL}artifact/${ZIP_NAME}" class="btn">📦 Download ZIP</a>
    <a href="${BUILD_URL}console" class="btn">🖥 Console Log</a>

    <h3 style="margin-top:24px">📎 Attachments in this email</h3>
    <ul style="font-size:13px; line-height:1.9">
      <li><b>playwright-artifacts-build-${BUILD_NUMBER}.zip</b> — Full report, screenshots, videos, logs</li>
      <li><b>build.log</b> — Jenkins console output for this build</li>
    </ul>

  </div>

  <div class="footer">
    Sent automatically by Jenkins CI/CD &nbsp;|&nbsp; ${JOB_NAME} &nbsp;|&nbsp; Build #${BUILD_NUMBER}
  </div>

</div>
</body>
</html>
"""

    emailext(
        to               : params.EMAIL_RECIPIENTS,
        subject          : subject,
        body             : body,
        mimeType         : 'text/html',
        attachLog        : true,           // attaches console log
        attachmentsPattern: "${env.ZIP_NAME}",
        compressLog      : true
    )

    echo "📧  Email dispatched → ${params.EMAIL_RECIPIENTS}"
}