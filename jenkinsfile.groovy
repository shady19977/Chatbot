pipeline {
    agent any

    // =========================================================
    //  BUILD PARAMETERS
    // =========================================================
    parameters {

        choice(
            name: 'BROWSER',
            choices: ['chromium', 'firefox', 'webkit', 'all'],
            description: '''
                <b>Browser Selection</b><br/>
                <ul>
                  <li><b>chromium</b> &mdash; Google Chrome engine (default)</li>
                  <li><b>firefox</b>  &mdash; Mozilla Firefox engine</li>
                  <li><b>webkit</b>   &mdash; Safari engine</li>
                  <li><b>all</b>      &mdash; Run on all 3 browsers</li>
                </ul>
            '''
        )

        choice(
            name: 'TEST_SELECTION_MODE',
            choices: ['ALL', 'SINGLE', 'MULTIPLE'],
            description: '''
                <b>Test Selection Mode</b><br/>
                <ul>
                  <li><b>ALL</b>      &mdash; Execute every spec file in /tests</li>
                  <li><b>SINGLE</b>   &mdash; Run one specific spec file</li>
                  <li><b>MULTIPLE</b> &mdash; Run a comma-separated list of spec files</li>
                </ul>
            '''
        )

        string(
            name: 'TEST_FILES',
            defaultValue: '',
            description: '''
                <b>Test File(s)</b> &mdash; Required when mode is SINGLE or MULTIPLE.<br/>
                SINGLE   &rarr; e.g. <code>tests/login.spec.ts</code><br/>
                MULTIPLE &rarr; e.g. <code>tests/login.spec.ts,tests/checkout.spec.ts</code>
            '''
        )

        choice(
            name: 'PARALLEL_WORKERS',
            choices: ['2', '1', '4', '8'],
            description: '<b>Parallel Workers</b> &mdash; Number of concurrent test processes'
        )

        booleanParam(
            name: 'HEADED_MODE',
            defaultValue: false,
            description: '<b>Headed Mode</b> &mdash; Show browser UI. Keep false for CI servers.'
        )

        choice(
            name: 'EXECUTION_TIMING',
            choices: ['NOW', 'SCHEDULED'],
            description: '''
                <b>Execution Timing</b><br/>
                <ul>
                  <li><b>NOW</b>       &mdash; Start immediately</li>
                  <li><b>SCHEDULED</b> &mdash; Wait until SCHEDULE_DATETIME</li>
                </ul>
            '''
        )

        string(
            name: 'SCHEDULE_DATETIME',
            defaultValue: '',
            description: '''
                <b>Scheduled Date/Time</b> &mdash; Used only when EXECUTION_TIMING = SCHEDULED.<br/>
                Format: <code>yyyy-MM-dd HH:mm</code> &nbsp;(e.g. 2025-08-01 09:30)
            '''
        )

        booleanParam(
            name: 'SEND_EMAIL',
            defaultValue: true,
            description: '<b>Send Email Report</b> &mdash; Email results with full artifact ZIP'
        )

        string(
            name: 'EMAIL_RECIPIENTS',
            defaultValue: 'qa-team@company.com',
            description: '<b>Email Recipients</b> &mdash; Comma-separated list of addresses'
        )
    }

    // =========================================================
    //  ENVIRONMENT
    // =========================================================
    environment {
        CI                       = 'true'
        PLAYWRIGHT_BROWSERS_PATH = '0'
        REPORT_DIR               = 'playwright-report'
        RESULTS_DIR              = 'test-results'
        ZIP_NAME                 = "playwright-artifacts-build-${BUILD_NUMBER}.zip"
    }

    // =========================================================
    //  STAGES
    // =========================================================
    stages {

        // -----------------------------------------------------
        stage('Validate / Schedule') {
        // -----------------------------------------------------
            steps {
                script {
                    // FIX 1: Force Windows console to UTF-8 for the entire session.
                    // This prevents Playwright's Arabic/Unicode output from becoming
                    // mojibake (e.g. "â€"" instead of actual characters) in the log.
                    bat '@chcp 65001 > nul'

                    echo "================================================================"
                    echo "  Playwright CI Pipeline  |  Job: ${JOB_NAME}  |  Build #${BUILD_NUMBER}"
                    echo "================================================================"

                    // Validate required params
                    if (params.TEST_SELECTION_MODE != 'ALL' && !params.TEST_FILES?.trim()) {
                        error("ERROR: TEST_FILES is required when TEST_SELECTION_MODE = ${params.TEST_SELECTION_MODE}")
                    }

                    // Handle scheduled execution
                    if (params.EXECUTION_TIMING == 'SCHEDULED') {
                        if (!params.SCHEDULE_DATETIME?.trim()) {
                            error("ERROR: SCHEDULE_DATETIME is required when EXECUTION_TIMING = SCHEDULED")
                        }

                        def fmt     = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                        def target  = fmt.parse(params.SCHEDULE_DATETIME)
                        def now     = new Date()
                        def delayMs = target.time - now.time

                        if (delayMs <= 0) {
                            error("ERROR: SCHEDULE_DATETIME '${params.SCHEDULE_DATETIME}' is in the past!")
                        }

                        def delayMin = (delayMs / 60_000).toLong()
                        echo "  [WAIT] Scheduled at ${params.SCHEDULE_DATETIME} -- waiting ${delayMin} minute(s) ..."
                        sleep(time: delayMs, unit: 'MILLISECONDS')
                        echo "  [GO]   Scheduled time reached -- starting execution."
                    }

                    // Print config using plain ASCII only (no emoji, no special chars)
                    echo ""
                    echo "  EFFECTIVE CONFIGURATION"
                    echo "  -----------------------"
                    echo "  Browser        : ${params.BROWSER}"
                    echo "  Test Mode      : ${params.TEST_SELECTION_MODE}"
                    echo "  Test Files     : ${params.TEST_FILES ?: '(all)'}"
                    echo "  Workers        : ${params.PARALLEL_WORKERS}"
                    echo "  Headed         : ${params.HEADED_MODE}"
                    echo "  Timing         : ${params.EXECUTION_TIMING}"
                    echo "  Send Email     : ${params.SEND_EMAIL}"
                    echo "  Recipients     : ${params.EMAIL_RECIPIENTS}"
                    echo ""
                }
            }
        }

        // -----------------------------------------------------
        stage('Checkout') {
        // -----------------------------------------------------
            steps {
                echo "  [GIT] Cloning repository ..."
                git(
                    changelog : false,
                    poll      : false,
                    url       : 'https://github.com/shady19977/Chatbot.git',
                    branch    : 'master'
                )
                script {
                    // FIX 2: Prefix git commands with '@' to suppress the echoed
                    // command line itself -- prevents the double-line output where
                    // the command shows before the result.
                    def rawBranch = bat(script: '@git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()
                    def rawCommit = bat(script: '@git rev-parse --short HEAD',       returnStdout: true).trim()

                    // Split on newlines and take the last line (actual git output,
                    // not the "C:\...\workspace>" prompt line).
                    env.GIT_BRANCH_NAME  = rawBranch.split(/\r?\n/).last().trim()
                    env.GIT_SHORT_COMMIT = rawCommit.split(/\r?\n/).last().trim()

                    echo "  [GIT] Branch: ${env.GIT_BRANCH_NAME}  |  Commit: ${env.GIT_SHORT_COMMIT}"
                }
            }
        }

        // -----------------------------------------------------
        stage('Install Dependencies') {
        // -----------------------------------------------------
            steps {
                nodejs(nodeJSInstallationName: 'NodeJS-18') {
                    echo "  [NPM] Installing packages ..."
                    bat 'npm ci --prefer-offline'

                    echo "  [PW]  Installing Playwright browser(s) ..."
                    script {
                        def targets = (params.BROWSER == 'all') ? 'chromium firefox webkit' : params.BROWSER
                        bat "npx playwright install ${targets} --with-deps"
                    }
                }
            }
        }

        // -----------------------------------------------------
        stage('Discover Test Files') {
        // -----------------------------------------------------
            steps {
                script {
                    echo "  [SCAN] Discovering spec files under ./tests ..."

                    def rawOutput = bat(
                        script: '''@echo off
                            if not exist tests (
                                echo NO_TESTS_DIR
                                exit /b 0
                            )
                            for /r tests %%i in (*.spec.ts *.test.ts) do @echo %%i
                        ''',
                        returnStdout: true
                    ).trim()

                    def discovered = rawOutput
                        .split(/\r?\n/)
                        .collect { it.trim() }
                        .findAll { it && it != 'NO_TESTS_DIR' }

                    if (discovered.isEmpty()) {
                        error("No *.spec.ts / *.test.ts files found under ./tests")
                    }

                    echo "  [SCAN] Found ${discovered.size()} file(s):"
                    discovered.each { f -> echo "         - ${f}" }

                    // Build playwright test command
                    def cmd = 'npx playwright test'

                    switch (params.TEST_SELECTION_MODE) {
                        case 'SINGLE':
                            cmd += " \"${params.TEST_FILES.trim()}\""
                            break
                        case 'MULTIPLE':
                            params.TEST_FILES.split(',').each { f ->
                                if (f.trim()) cmd += " \"${f.trim()}\""
                            }
                            break
                        // ALL: no file args
                    }

                    if (params.BROWSER != 'all') {
                        cmd += " --project=${params.BROWSER}"
                    }

                    cmd += " --workers=${params.PARALLEL_WORKERS}"
                    if (params.HEADED_MODE) { cmd += ' --headed' }
                    cmd += ' --reporter=html,junit,list'

                    env.PW_TEST_CMD = cmd
                    echo "  [CMD]  ${cmd}"
                }
            }
        }

        // -----------------------------------------------------
        stage('Execute Tests') {
        // -----------------------------------------------------
            steps {
                echo "  [RUN]  Executing: ${env.PW_TEST_CMD}"
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    nodejs(nodeJSInstallationName: 'NodeJS-18') {
                        // FIX 3: chcp 65001 before running Playwright so Arabic/RTL
                        // characters in test output are stored as proper UTF-8 in the
                        // Jenkins log instead of being mangled by the default CP1252.
                        bat """@chcp 65001 > nul
${env.PW_TEST_CMD}"""
                    }
                }
            }
            post {
                always {
                    echo "  [DONE] Stage complete -- build status: ${currentBuild.currentResult}"
                }
            }
        }

        // -----------------------------------------------------
        stage('Package Artifacts') {
        // -----------------------------------------------------
            steps {
                echo "  [ZIP]  Creating ${env.ZIP_NAME} ..."
                bat """@echo off
                    if exist "${env.ZIP_NAME}" del /f /q "${env.ZIP_NAME}"

                    if exist "${env.REPORT_DIR}" (
                        powershell -NoProfile -Command "Compress-Archive -Path '${env.REPORT_DIR}' -DestinationPath '${env.ZIP_NAME}' -Force"
                        echo [ZIP] Added playwright-report
                    ) else (
                        echo [ZIP] playwright-report not found -- skipping
                    )

                    if exist "${env.RESULTS_DIR}" (
                        powershell -NoProfile -Command "Compress-Archive -Path '${env.RESULTS_DIR}' -DestinationPath '${env.ZIP_NAME}' -Update"
                        echo [ZIP] Added test-results
                    ) else (
                        echo [ZIP] test-results not found -- skipping
                    )

                    if exist results (
                        powershell -NoProfile -Command "Compress-Archive -Path 'results' -DestinationPath '${env.ZIP_NAME}' -Update"
                        echo [ZIP] Added results (CSV outputs)
                    )

                    echo [ZIP] Done: ${env.ZIP_NAME}
                """
            }
        }

        // -----------------------------------------------------
        stage('Publish to Jenkins') {
        // -----------------------------------------------------
            steps {
                // JUnit XML keeps all historical runs on the build page
                junit(
                    testResults      : "${env.RESULTS_DIR}/**/junit.xml",
                    allowEmptyResults: true,
                    keepLongStdio    : true
                )

                // HTML Report -- keepAll=true preserves every build's report
                publishHTML([
                    allowMissing         : true,
                    alwaysLinkToLastBuild: true,
                    keepAll              : true,
                    reportDir            : "${env.REPORT_DIR}",
                    reportFiles          : 'index.html',
                    reportName           : "Playwright Report - Build #${BUILD_NUMBER}"
                ])

                // Archive ZIP + raw artifacts
                archiveArtifacts(
                    artifacts        : "${env.ZIP_NAME}, ${env.RESULTS_DIR}/**/*",
                    fingerprint      : true,
                    allowEmptyArchive: true
                )

                echo "  [PUB]  Reports live at: ${BUILD_URL}Playwright_Report/"
            }
        }

        // -----------------------------------------------------
        stage('Send Email Report') {
        // -----------------------------------------------------
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
            echo "  [CLEAN] Running workspace cleanup ..."
            cleanWs(
                cleanWhenSuccess : false,
                cleanWhenUnstable: false,
                cleanWhenFailure : false,
                cleanWhenNotBuilt: true
            )
        }
        success  { echo "  [OK]   Build #${BUILD_NUMBER} -- ALL TESTS PASSED" }
        unstable { echo "  [WARN] Build #${BUILD_NUMBER} -- SOME TESTS FAILED -- review HTML report" }
        failure  { echo "  [FAIL] Build #${BUILD_NUMBER} -- PIPELINE FAILED -- check console log" }
    }
}

// =============================================================
//  HELPER: Rich HTML email
// =============================================================
def sendPlaywrightEmail() {
    def status      = currentBuild.currentResult
    def color       = (status == 'SUCCESS') ? '#27ae60' : (status == 'UNSTABLE') ? '#e67e22' : '#c0392b'
    def statusLabel = (status == 'SUCCESS') ? 'PASSED'  : (status == 'UNSTABLE') ? 'UNSTABLE' : 'FAILED'

    // FIX 4: Subject uses plain ASCII + HTML entities that resolve correctly.
    // No emoji in the subject -- emoji in email subjects get encoded as
    // =?UTF-8?B?...?= MIME tokens which many clients display as garbled text.
    def subject = "[${statusLabel}] Playwright Tests | Build #${BUILD_NUMBER} | ${params.BROWSER.toUpperCase()} | ${JOB_NAME}"

    def body = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
  <title>Playwright Report - Build #${BUILD_NUMBER}</title>
  <style>
    body     { margin:0; padding:0; background:#f0f2f5; font-family:'Segoe UI',Arial,sans-serif; color:#2c3e50; }
    .shell   { max-width:680px; margin:30px auto; background:#fff; border-radius:8px; overflow:hidden; box-shadow:0 2px 14px rgba(0,0,0,.12); }
    .hdr     { background:${color}; padding:26px 32px; }
    .hdr h1  { margin:0 0 4px; color:#fff; font-size:20px; font-weight:700; letter-spacing:.3px; }
    .hdr p   { margin:0; color:rgba(255,255,255,.82); font-size:12px; }
    .body    { padding:26px 32px; }
    h3       { margin:20px 0 8px; font-size:13px; color:#34495e; text-transform:uppercase; letter-spacing:.6px; border-bottom:1px solid #ecf0f1; padding-bottom:5px; }
    table    { width:100%; border-collapse:collapse; font-size:13px; }
    th       { background:#f7f9fc; text-align:left; padding:8px 12px; font-weight:600; color:#555; }
    td       { padding:8px 12px; border-bottom:1px solid #f0f2f5; color:#2c3e50; }
    td:first-child { font-weight:600; color:#555; width:40%; }
    .badge   { display:inline-block; padding:3px 10px; border-radius:12px; font-size:11px; font-weight:700; color:#fff; background:${color}; letter-spacing:.4px; }
    .links   { margin-top:8px; }
    .btn     { display:inline-block; margin:4px 4px 0 0; padding:8px 16px; background:${color}; color:#fff !important; text-decoration:none; border-radius:5px; font-size:12px; font-weight:600; }
    .abox    { background:#f7f9fc; border-left:4px solid ${color}; padding:12px 16px; border-radius:0 5px 5px 0; margin-top:8px; font-size:13px; }
    .abox li { margin:4px 0; }
    .footer  { background:#f0f2f5; padding:12px 32px; font-size:11px; color:#95a5a6; text-align:center; border-top:1px solid #e8eaed; }
  </style>
</head>
<body>
<div class="shell">

  <div class="hdr">
    <h1>Playwright Automation Report</h1>
    <p>Build #${BUILD_NUMBER} &nbsp;&bull;&nbsp; ${new Date().format('dd MMM yyyy  HH:mm')} &nbsp;&bull;&nbsp; ${JOB_NAME}</p>
  </div>

  <div class="body">

    <h3>Execution Summary</h3>
    <table>
      <tr><th>Parameter</th><th>Value</th></tr>
      <tr><td>Status</td>           <td><span class="badge">${statusLabel}</span></td></tr>
      <tr><td>Browser</td>          <td>${params.BROWSER}</td></tr>
      <tr><td>Test Selection</td>   <td>${params.TEST_SELECTION_MODE}</td></tr>
      <tr><td>Test Files</td>       <td>${params.TEST_FILES ?: '(all tests)'}</td></tr>
      <tr><td>Parallel Workers</td> <td>${params.PARALLEL_WORKERS}</td></tr>
      <tr><td>Headed Mode</td>      <td>${params.HEADED_MODE}</td></tr>
      <tr><td>Git Branch</td>       <td>${env.GIT_BRANCH_NAME  ?: 'N/A'}</td></tr>
      <tr><td>Git Commit</td>       <td>${env.GIT_SHORT_COMMIT ?: 'N/A'}</td></tr>
      <tr><td>Jenkins Node</td>     <td>${NODE_NAME}</td></tr>
      <tr><td>Duration</td>         <td>${currentBuild.durationString}</td></tr>
    </table>

    <h3>Quick Links</h3>
    <div class="links">
      <a href="${BUILD_URL}"                     class="btn">Build Page</a>
      <a href="${BUILD_URL}Playwright_Report/"   class="btn">HTML Report</a>
      <a href="${BUILD_URL}artifact/${ZIP_NAME}" class="btn">Download ZIP</a>
      <a href="${BUILD_URL}console"              class="btn">Console Log</a>
    </div>

    <h3>Attachments</h3>
    <div class="abox">
      <ul style="margin:0; padding-left:18px;">
        <li><b>${ZIP_NAME}</b> &mdash; HTML report, screenshots, videos, CSV results</li>
        <li><b>build.log</b> &mdash; Full Jenkins console output (compressed)</li>
      </ul>
    </div>

  </div>

  <div class="footer">
    Generated automatically by Jenkins CI/CD &nbsp;&bull;&nbsp; ${JOB_NAME} &nbsp;&bull;&nbsp; Build #${BUILD_NUMBER}
  </div>

</div>
</body>
</html>"""

    // FIX 5: Removed 'charset' parameter -- it is not a valid emailext parameter
    // and causes "WARNING: Unknown parameter(s) found for class type..." in the log.
    // The HTML body already declares charset=UTF-8 in its meta tag, which is
    // sufficient for mail clients to render the content correctly.
    emailext(
        to                : params.EMAIL_RECIPIENTS,
        subject           : subject,
        body              : body,
        mimeType          : 'text/html',
        attachLog         : true,
        compressLog       : true,
        attachmentsPattern: "${env.ZIP_NAME}"
    )

    echo "  [MAIL] Report sent to: ${params.EMAIL_RECIPIENTS}"
}