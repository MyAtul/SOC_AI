$ErrorActionPreference = 'Stop'

$expectedUrl = 'jdbc:postgresql://127.0.0.1:55432/aisoc_test'
$expectedIdentity = 'aisoc_test|aisoc_test|5432|18.3'
$testBackendPort = 8082
$password = $env:AISOC_V2_TEST_PASSWORD

if ([string]::IsNullOrWhiteSpace($password)) {
    throw 'Set AISOC_V2_TEST_PASSWORD securely before running this script.'
}
if ($env:SPRING_DATASOURCE_URL -and $env:SPRING_DATASOURCE_URL -ne $expectedUrl) {
    throw 'Refusing a conflicting SPRING_DATASOURCE_URL override.'
}
if ($env:SPRING_DATASOURCE_PASSWORD -and $env:SPRING_DATASOURCE_PASSWORD -cne $password) {
    throw 'Refusing a conflicting SPRING_DATASOURCE_PASSWORD override.'
}
if ($env:SPRING_APPLICATION_JSON) {
    throw 'Refusing SPRING_APPLICATION_JSON because it can override the guarded datasource.'
}
if (-not (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue)) {
    throw 'Get-NetTCPConnection is required to ensure port 8082 is unused.'
}
$portListener = @(Get-NetTCPConnection -LocalPort $testBackendPort -State Listen -ErrorAction SilentlyContinue)
if (@($portListener).Count -gt 0) {
    throw 'Port 8082 is already occupied. Stop and identify the listener before starting the test backend.'
}

$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    throw 'psql is required for the explicit test-database identity check.'
}
$env:PGPASSWORD = $password
try {
    # These explicit libpq arguments prove the host-published endpoint. Inside
    # Docker, inet_server_port() reports the container-side PostgreSQL port.
    $identity = & $psql.Source -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 55432 -U aisoc_test -d aisoc_test -At -c "SELECT current_database() || '|' || session_user || '|' || inet_server_port()::text || '|' || substring(current_setting('server_version') from '^[0-9]+\.[0-9]+')"
    if ($LASTEXITCODE -ne 0 -or $identity.Trim() -ne $expectedIdentity) {
        throw 'Refusing startup: PostgreSQL identity/version did not match the approved test target.'
    }

    $publicTables = & $psql.Source -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 55432 -U aisoc_test -d aisoc_test -At -c "SELECT string_agg(table_name, ',' ORDER BY table_name) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('flyway_schema_history','alerts','incidents','ai_investigations','ai_investigation_evidence','alert_detection_work','incident_investigation_work')"
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect test schema state.' }
    if (-not [string]::IsNullOrWhiteSpace($publicTables)) {
        if ($publicTables -notmatch '(^|,)flyway_schema_history(,|$)') {
            throw 'Test database contains application tables without Flyway history. Stop for owner review; automatic baseline is disabled.'
        }
        $successfulMigrations = & $psql.Source -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 55432 -U aisoc_test -d aisoc_test -At -c "SELECT count(DISTINCT version) FROM public.flyway_schema_history WHERE success AND version IN ('1','2')"
        if ($LASTEXITCODE -ne 0 -or $successfulMigrations.Trim() -ne '2') {
            throw 'Test database does not show successful V1 and V2 history. Stop for owner review.'
        }
        if ($publicTables -match '(^|,)alert_detection_work(,|$)') {
            $unprocessedWork = & $psql.Source -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p 55432 -U aisoc_test -d aisoc_test -At -c "SELECT count(*) FROM public.alert_detection_work WHERE status IN ('PENDING','IN_PROGRESS')"
            if ($LASTEXITCODE -ne 0 -or $unprocessedWork.Trim() -ne '0') {
                throw 'Detection work is already pending or leased. Stop and reconcile it before enabling the worker.'
            }
        }
    }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

$env:SPRING_PROFILES_ACTIVE = 'pipeline-test'
$env:SPRING_DATASOURCE_URL = $expectedUrl
$env:SPRING_DATASOURCE_USERNAME = 'aisoc_test'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
$env:SPRING_FLYWAY_ENABLED = 'true'
$env:SPRING_FLYWAY_URL = $expectedUrl
$env:SPRING_FLYWAY_USER = 'aisoc_test'
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = 'false'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8082'
$env:AISOC_DETECTION_WORKER_ENABLED = 'true'
$env:AISOC_INVESTIGATION_WORKER_ENABLED = 'false'

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $maven) {
    throw 'mvn.cmd is required to start the isolated pipeline-test backend.'
}

$logId = [Guid]::NewGuid().ToString('N')
$stdoutLog = Join-Path $env:TEMP "aisoc-pipeline-test-$logId.out.log"
$stderrLog = Join-Path $env:TEMP "aisoc-pipeline-test-$logId.err.log"
$backendProcess = Start-Process -FilePath $maven.Source `
    -ArgumentList 'spring-boot:run' `
    -WorkingDirectory $PSScriptRoot `
    -WindowStyle Hidden `
    -PassThru `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog

$readinessUri = 'http://127.0.0.1:8082/api/incidents'
$readinessDeadline = (Get-Date).AddSeconds(120)
$ready = $false
while ((Get-Date) -lt $readinessDeadline) {
    $backendProcess.Refresh()
    if ($backendProcess.HasExited) {
        throw "Pipeline-test Maven process exited before HTTP readiness (exit code $($backendProcess.ExitCode)). Inspect logs: $stdoutLog ; $stderrLog"
    }

    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $readinessUri -TimeoutSec 3
        if ($response.StatusCode -eq 200 -and $response.Content.TrimStart().StartsWith('[')) {
            $null = ConvertFrom-Json -InputObject $response.Content -ErrorAction Stop
            $ready = $true
            break
        }
    }
    catch {
        # The server may still be starting. The process-exit and deadline checks
        # below distinguish startup failure from a transient not-ready response.
    }

    Start-Sleep -Seconds 2
}

if (-not $ready) {
    $backendProcess.Refresh()
    if ($backendProcess.HasExited) {
        throw "Pipeline-test Maven process exited before HTTP readiness (exit code $($backendProcess.ExitCode)). Inspect logs: $stdoutLog ; $stderrLog"
    }
    throw "Pipeline-test backend did not pass HTTP readiness within 120 seconds. Process ID $($backendProcess.Id) is still running; inspect logs before retrying: $stdoutLog ; $stderrLog"
}

Write-Output "Pipeline-test backend is HTTP-ready at $readinessUri (PID $($backendProcess.Id)). Logs: $stdoutLog ; $stderrLog"
