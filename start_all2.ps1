<#
================================================================================
 start_all2.ps1  -  CargoService Sprint 2 full-stack launcher (Windows / PowerShell)
================================================================================
 Brings up the whole Sprint 2 demo stack, in order. Unlike Sprint 1 (whose IOPort
 was a console reading stdin), here the IOPort is a WEB GUI: the pushbutton is a
 button in the browser, so this script also opens the GUI page for you.

   1) mosquitto            (MQTT broker, docker) - TWO listeners:
                             * 1883  native MQTT   (board, application, sim devices)
                             * 9001  MQTT/WebSocket(the browser GUI)
   2) vrWithGui26          (virtual robot + GUI,    docker, port 8090)
   3) robotsmart26         (robot service,          gradlew run, TCP 8020) -> new window
   4) Sprint 2 application  (ctxcargoservice:        gradlew run, TCP 8030) -> new window
                             cargoservice + cargorobot + logic sonar
   5) devices              (FIELD  = flash the Pico W by hand, see below;
                            SIMULATED = -Simulated flag: launch the sim-devices model)
   6) the web GUI          (static ioport page, opened in your default browser)

 USAGE (from the repo root):
     # FIELD variant (physical Pico W publishes on sonar_data / reads led_data):
     powershell -ExecutionPolicy Bypass -File .\start_all2.ps1

     # SIMULATED variant (no hardware: the sim-devices model stands in for the board):
     powershell -ExecutionPolicy Bypass -File .\start_all2.ps1 -Simulated

 STOP:
     .\stop_all2.ps1

 NOTE (adjust once the Sprint 2 code is generated):
   - $guiPage        : where the static GUI page lands.
   - $simDevicesTask : the Gradle task that launches cargoservice_sprint2_simdevices.qak.
   - mosquitto.yml must expose BOTH listeners (1883 native + 9001 websocket).
================================================================================
#>

param(
    [switch]$Simulated   # when set, also start the simulated-devices model (no Pico W)
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot                      # this script lives in the repo root
$mosquittoYml = Join-Path $root 'yamls\mosquitto.yml'
$vrYaml       = Join-Path $root 'yamls\vrWithGui26.yaml'
$robotDir     = Join-Path $root 'robotsmart26'
$sprint2Dir   = Join-Path $root 'CargoService\Sprint2'

# --- adjust these to match the Sprint 2 build once the code exists ----------
$guiPage        = Join-Path $sprint2Dir 'web\ioport.html'   # static GUI page (MQTT-over-WebSocket client)
$simDevicesTask = 'runSimDevices'                            # Gradle task launching cargoservice_sprint2_simdevices.qak

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
foreach ($p in @($mosquittoYml, $vrYaml, $robotDir, $sprint2Dir)) {
    if (-not (Test-Path $p)) { throw "Expected path not found: $p" }
}

# --- 1) mosquitto (native 1883 + websocket 9001) ---------------------------
Write-Step 1 "Starting mosquitto (MQTT broker: 1883 native + 9001 websocket) ..."
docker-compose -f $mosquittoYml -p cargo-mqtt up -d
Wait-Tcp -Port 1883 -TimeoutSec 60 -Label "mosquitto MQTT"
Wait-Tcp -Port 9001 -TimeoutSec 30 -Label "mosquitto WebSocket"

# --- 2) vrWithGui26 --------------------------------------------------------
Write-Step 2 "Starting vrWithGui26 (virtual robot + GUI) ..."
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

# --- 4) Sprint 2 application (separate window, server; the GUI drives it) ---
Write-Step 4 "Starting the Sprint 2 application (ctxcargoservice: cargoservice + cargorobot + logic sonar) ..."
Start-Process -FilePath 'powershell' -ArgumentList @(
    '-NoExit', '-Command',
    "Set-Location '$sprint2Dir'; Write-Host 'cargoservice (Sprint 2) - leave this window open' -ForegroundColor Cyan; .\gradlew.bat run --console=plain"
)
Wait-Tcp -Port 8030 -TimeoutSec 120 -Label "cargoservice (TCP 8030)"

# --- 5) devices: simulated model, or a reminder to flash the Pico W --------
if ($Simulated) {
    Write-Step 5 "Starting the SIMULATED devices model ($simDevicesTask) in a separate window ..."
    Start-Process -FilePath 'powershell' -ArgumentList @(
        '-NoExit', '-Command',
        "Set-Location '$sprint2Dir'; Write-Host 'sim devices (table sonar + simulated LED) - leave this window open' -ForegroundColor Cyan; .\gradlew.bat $simDevicesTask --console=plain"
    )
} else {
    Write-Step 5 "FIELD variant: no simulated devices started."
    Write-Host "    -> Flash pico_code\pico_mqtt.py on the Pico W (set Wi-Fi SSID/password and the broker IP" -ForegroundColor Yellow
    Write-Host "       to this machine's LAN address); the board joins on sonar_data / led_data automatically." -ForegroundColor Yellow
    Write-Host "    -> (Run with  -Simulated  to skip the hardware and use the table-driven sonar instead.)" -ForegroundColor Yellow
}

# --- 6) open the web GUI (IOPort) in the default browser -------------------
Write-Step 6 "Opening the web GUI (IOPort) ..."
if (Test-Path $guiPage) {
    Start-Process $guiPage
    Write-Host "    -> GUI opened: $guiPage" -ForegroundColor Green
} else {
    Write-Warning "GUI page not found at $guiPage - adjust `$guiPage` once the Sprint 2 GUI is in place, then open it manually."
}

Write-Host "`nAll services launched." -ForegroundColor Green
Write-Host "  Broker: 1883 (native) / 9001 (websocket) | robot: 8020 | cargoservice: 8030 | virtual robot: http://localhost:8090" -ForegroundColor Green
Write-Host "  Drive the demo from the browser GUI (pushbutton). To tear everything down:  .\stop_all2.ps1`n" -ForegroundColor Green
