<#
================================================================================
 start_all.ps1  -  CargoService Sprint 1 full-stack launcher (Windows / PowerShell)
================================================================================
 Brings up the whole demo stack, in order, and finally hands you an interactive
 console on the Sprint 1 prototype so you can drive the IOPort pushbutton:

   1) mosquitto            (MQTT broker,            docker, port 1883)
   2) vrWithGui26          (virtual robot + GUI,    docker, ports 8090/8091/8085)
   3) robotsmart26         (robot service,          gradlew run, TCP 8020) -> new window
   4) Sprint 1 prototype   (cargoservice.qak,       gradlew run, TCP 8030) -> THIS window

 The prototype runs in THIS console (foreground): the IOPort actor reads stdin,
 so you press ENTER here to simulate a pushbutton press.

 USAGE (from the repo root):
     powershell -ExecutionPolicy Bypass -File .\start_all.ps1

 STOP:
   - Close the "robotsmart26" window (or Ctrl+C in it).
   - Ctrl+C here to stop the prototype.
   - docker-compose -f yamls\mosquitto.yml -p cargo-mqtt down
   - docker-compose -f yamls\vrWithGui26.yaml -p cargo-vr down

================================================================================
#>

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot                      # this script lives in the repo root
$mosquittoYml = Join-Path $root 'yamls\mosquitto.yml'
$vrYaml       = Join-Path $root 'yamls\vrWithGui26.yaml'
$robotDir     = Join-Path $root 'robotsmart26'
$sprint1Dir   = Join-Path $root 'CargoService\Sprint1'

function Write-Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }

# Wait until a TCP port on localhost accepts a connection (or time out).
function Wait-Tcp {
    param([int]$Port, [int]$TimeoutSec = 90, [string]$Label = "port $Port")
    Write-Host "    waiting for $Label (localhost:$Port) ..." -NoNewline
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $c = New-Object System.Net.Sockets.TcpClient
            $c.Connect('127.0.0.1', $Port)
            $c.Close()
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

# --- sanity checks ---------------------------------------------------------
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker not found on PATH. Start Docker Desktop and retry."
}
foreach ($p in @($mosquittoYml, $vrYaml, $robotDir, $sprint1Dir)) {
    if (-not (Test-Path $p)) { throw "Expected path not found: $p" }
}

# --- 1) mosquitto ----------------------------------------------------------
Write-Step 1 "Starting mosquitto (MQTT broker) ..."
docker-compose -f $mosquittoYml -p cargo-mqtt up -d
Wait-Tcp -Port 1883 -TimeoutSec 60 -Label "mosquitto MQTT"

# --- 2) vrWithGui26 --------------------------------------------------------
Write-Step 2 "Starting vrWithGui26 (virtual robot + GUI) ..."
# vrWithGui26.yaml references an EXTERNAL docker network 'iss-network': create it
# if it does not exist yet (harmless if it already does).
docker-compose -f $vrYaml -p cargo-vr up -d
Wait-Tcp -Port 8090 -TimeoutSec 90 -Label "virtual robot"

# --- 3) robotsmart26 (separate window, long-running server) ----------------
Write-Step 3 "Starting robotsmart26 (gradlew run) in a separate window ..."
Start-Process -FilePath 'powershell' -ArgumentList @(
    '-NoExit', '-Command',
    "Set-Location '$robotDir'; Write-Host 'robotsmart26 - leave this window open' -ForegroundColor Cyan; .\gradlew.bat run --console=plain"
)
Wait-Tcp -Port 8020 -TimeoutSec 120 -Label "robotsmart26 (TCP 8020)"
Start-Sleep -Seconds 3   # small grace period after the port opens

# --- 4) Sprint 1 prototype (THIS window, interactive) ----------------------
Write-Step 4 "Starting the Sprint 1 prototype (cargoservice) in THIS window."
Write-Host "    -> The IOPort console reads from here: press ENTER to send a load request." -ForegroundColor Green
Write-Host "    -> Ctrl+C to stop.`n" -ForegroundColor Green
Set-Location $sprint1Dir
# standardInput = System.in is set in build.gradle, so stdin reaches the ioport actor.
& .\gradlew.bat run --console=plain
