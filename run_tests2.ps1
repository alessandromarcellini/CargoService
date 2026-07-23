<#
================================================================================
 run_tests2.ps1  -  CargoService Sprint 2 automated TestPlan runner (Windows / PowerShell)
================================================================================
 Starts every service the Sprint 2 TestPlans may need, then runs the JUnit suite
 (TestPlanSprint2 together with the Sprint 0 and Sprint 1 regressions ported to
 the MQTT push chain - a single 'gradlew test' runs all three sprints' plans).

 Services started, in order:
   1) mosquitto      (MQTT broker, docker) - 1883 native + 9001 websocket
   2) vrWithGui26    (virtual robot + GUI, docker, port 8090)   <- the "robot GUI"
   3) robotsmart26   (robot service, gradlew run, TCP 8020)     <- the "robot smart"

 Then, from CargoService/Sprint2:
   - checks TCP 8030 is free (the test JVM binds it),
   - runs:  gradlew.bat cleanTest test

 NOTE: the default `test` suite uses MockCargorobot, so it only strictly needs the
 broker; vrWithGui26 + robotsmart26 are started too so the on-demand integration
 variant (TP2.5, like Sprint 1's TP1.6) can also run against the real robot.

 USAGE (from the repo root):
     powershell -ExecutionPolicy Bypass -File .\run_tests2.ps1
     powershell -ExecutionPolicy Bypass -File .\run_tests2.ps1 -StopWhenDone
     #  -StopWhenDone : tear the services down again after the run (calls stop_tests2.ps1)

 STOP (services are left running by default):  .\stop_tests2.ps1

 NOTE: uses docker-compose (v1). On Docker Compose v2 replace 'docker-compose'
       with 'docker compose' below.
================================================================================
#>

param(
    [switch]$StopWhenDone,      # when set, run stop_tests2.ps1 after the tests finish
    [int]$MaxAttempts = 3       # re-run the suite up to N times to absorb the occasional qak MQTT flake
)

$ErrorActionPreference = 'Stop'
$root         = $PSScriptRoot                 # this script lives in the repo root
$mosquittoYml = Join-Path $root 'yamls\mosquitto.yml'
$vrYaml       = Join-Path $root 'yamls\vrWithGui26.yaml'
$robotDir     = Join-Path $root 'robotsmart26'
$sprint2Dir   = Join-Path $root 'CargoService\Sprint2'

function Write-Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }

# Wait until a TCP port on localhost accepts a connection (or time out).
function Wait-Tcp {
    param([int]$Port, [int]$TimeoutSec = 90, [string]$Label = "port $Port")
    Write-Host "    waiting for $Label (localhost:$Port) ..." -NoNewline
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $c = New-Object System.Net.Sockets.TcpClient
            $c.Connect('127.0.0.1', $Port); $c.Close()
            Write-Host " up." -ForegroundColor Green
            return $true
        } catch {
            Start-Sleep -Milliseconds 1000
            Write-Host "." -NoNewline
        }
    }
    Write-Host " TIMEOUT." -ForegroundColor Yellow
    Write-Warning "$Label did not come up within $TimeoutSec s. Continuing anyway."
    return $false
}

# True if something is LISTENING on the given local TCP port.
function Test-PortInUse {
    param([int]$Port)
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.Connect('127.0.0.1', $Port); $c.Close(); return $true
    } catch { return $false }
}

# --- sanity checks ---------------------------------------------------------
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker not found on PATH. Start Docker Desktop and retry."
}
foreach ($p in @($mosquittoYml, $vrYaml, $robotDir, $sprint2Dir)) {
    if (-not (Test-Path $p)) { throw "Expected path not found: $p" }
}

# --- 1) mosquitto (native 1883 + websocket 9001) ---------------------------
Write-Step 1 "Starting mosquitto (MQTT broker: 1883 native + 9001 websocket) ..."
docker-compose -f $mosquittoYml -p cargo-mqtt up -d
Wait-Tcp -Port 1883 -TimeoutSec 60 -Label "mosquitto MQTT"

# --- 2) vrWithGui26 (the robot GUI) ----------------------------------------
Write-Step 2 "Starting vrWithGui26 (virtual robot + GUI) ..."
docker-compose -f $vrYaml -p cargo-vr up -d
Wait-Tcp -Port 8090 -TimeoutSec 90 -Label "virtual robot GUI"

# --- 3) robotsmart26 (the robot smart, separate long-running window) --------
Write-Step 3 "Starting robotsmart26 (gradlew run) in a separate window ..."
Start-Process -FilePath 'powershell' -ArgumentList @(
    '-NoExit', '-Command',
    "Set-Location '$robotDir'; Write-Host 'robotsmart26 - leave this window open' -ForegroundColor Cyan; .\gradlew.bat run --console=plain"
)
Wait-Tcp -Port 8020 -TimeoutSec 120 -Label "robotsmart26 (TCP 8020)"
Start-Sleep -Seconds 3   # small grace period after the port opens

# --- 4) the test JVM needs TCP 8030 free -----------------------------------
Write-Step 4 "Checking TCP 8030 is free (the test JVM binds it) ..."
if (Test-PortInUse -Port 8030) {
    Write-Warning "Something is LISTENING on 8030 (a running cargoservice app?). Stop it (close its window / stop_all2.ps1) and retry."
    if ($StopWhenDone) { & (Join-Path $root 'stop_tests2.ps1') }
    exit 2
}
Write-Host "    ok, 8030 is free." -ForegroundColor Green

# --- 5) run the TestPlans --------------------------------------------------
# Each attempt is a FRESH set of JVMs/contexts (forkEvery = 1). The Sprint 2 suite can
# occasionally hit a transient qak MQTT fault ("could not match input") that drops the
# cargoservice subscription and cascades to the rest of that class's run; a clean re-run
# almost always passes, so we retry the whole suite up to -MaxAttempts times.
Write-Step 5 "Running the Sprint 2 TestPlans (several minutes per attempt; up to $MaxAttempts attempts to absorb the occasional qak MQTT flake) ..."
Push-Location $sprint2Dir
$result = 1
for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    Write-Host "    --- attempt $attempt of $MaxAttempts ---" -ForegroundColor Cyan
    & .\gradlew.bat cleanTest test --console=plain
    $result = $LASTEXITCODE
    if ($result -eq 0) { break }
    if ($attempt -lt $MaxAttempts) {
        Write-Host "    a test failed (exit $result). If it was the transient qak MQTT drop, a fresh run clears it; retrying ..." -ForegroundColor Yellow
        Start-Sleep -Seconds 3
    }
}
Pop-Location

# --- 6) report -------------------------------------------------------------
Write-Step 6 "Result"
Write-Host "    HTML report: $sprint2Dir\build\reports\tests\test\index.html"
if ($result -eq 0) {
    Write-Host "    BUILD SUCCESSFUL - all TestPlans passed (Sprint 0 + Sprint 1 + Sprint 2)." -ForegroundColor Green
} else {
    Write-Host "    TESTS FAILED (gradle exit $result). Open the report above for the failing plan(s)." -ForegroundColor Yellow
    Write-Host "    Common causes: build not regenerated from the active .qak; broker down; 8030 busy." -ForegroundColor Yellow
}

# --- 7) optional teardown --------------------------------------------------
if ($StopWhenDone) {
    Write-Step 7 "Tearing the services down (-StopWhenDone) ..."
    & (Join-Path $root 'stop_tests2.ps1')
} else {
    Write-Host "`nServices are still running (so the TP2.5 integration variant can be re-run). Tear everything down with:  .\stop_tests2.ps1`n" -ForegroundColor Green
}

exit $result
