<#
================================================================================
 stop_all.ps1  -  CargoService Sprint 1 full-stack teardown (Windows / PowerShell)
================================================================================
 Cleans up everything started by start_all.ps1, in reverse order:

   1) the qak apps        (Sprint 1 prototype TCP 8030, robotsmart26 TCP 8020)
   2) the Gradle daemons  (gradlew --stop in both project dirs)
   3) the docker stacks   (vrWithGui26, then mosquitto)
   4) the docker network  (iss-network, only if now unused)

 USAGE (from the repo root, in a SEPARATE window from start_all.ps1):
     powershell -ExecutionPolicy Bypass -File .\stop_all.ps1
   or simply:
     .\stop_all.ps1

 NOTE: uses docker-compose (v1). If you are on Docker Compose v2, replace
       'docker-compose' with 'docker compose' below.
================================================================================
#>

# Cleanup must be best-effort: keep going even if one step fails.
$ErrorActionPreference = 'Continue'
$root         = $PSScriptRoot
$mosquittoYml = Join-Path $root 'yamls\mosquitto.yml'
$vrYaml       = Join-Path $root 'yamls\vrWithGui26.yaml'
$robotDir     = Join-Path $root 'robotsmart26'
$sprint1Dir   = Join-Path $root 'CargoService\Sprint1'

function Write-Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }

# Stop whatever process is LISTENING on a local TCP port (the qak contexts).
function Stop-Port {
    param([int]$Port, [string]$Label = "port $Port")
    try {
        $conns = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
        if (-not $conns) { Write-Host "    $Label : nothing listening."; return }
        $procIds = $conns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($procId in $procIds) {
            $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
            if ($p) {
                Write-Host "    stopping $Label -> PID $procId ($($p.ProcessName))" -ForegroundColor Yellow
                Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
            }
        }
    } catch {
        Write-Warning "could not inspect $Label : $($_.Exception.Message)"
    }
}

# --- 1) stop the qak apps --------------------------------------------------
Write-Step 1 "Stopping the qak services (prototype TCP 8030, robotsmart26 TCP 8020) ..."
Stop-Port -Port 8030 -Label "cargoservice prototype (8030)"
Stop-Port -Port 8020 -Label "robotsmart26 (8020)"

# --- 2) stop the Gradle daemons --------------------------------------------
Write-Step 2 "Stopping Gradle daemons ..."
if (Test-Path $sprint1Dir) { Push-Location $sprint1Dir; & .\gradlew.bat --stop; Pop-Location }
if (Test-Path $robotDir)   { Push-Location $robotDir;   & .\gradlew.bat --stop; Pop-Location }

# --- 3) tear down the docker stacks (reverse order of start) ---------------
Write-Step 3 "Stopping docker containers ..."
if (Test-Path $vrYaml)       { docker-compose -f $vrYaml -p cargo-vr down }
if (Test-Path $mosquittoYml) { docker-compose -f $mosquittoYml -p cargo-mqtt down }


Write-Host "`nCleanup done. If the 'robotsmart26' window is still open, you can close it." -ForegroundColor Green
