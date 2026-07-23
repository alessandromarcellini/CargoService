#!/usr/bin/env bash
# ============================================================================
# run_tests2.sh  -  run the Sprint 2 JUnit TestPlans (TestPlanSprint2)
# ----------------------------------------------------------------------------
# The unit TestPlans drive everything over MQTT and assert on the pushed
# hold_state. They need:
#   * the Mosquitto broker on localhost:1883   (started here if not already up)
#   * a REGENERATED build  (regenerate the active .qak -> .kt in Eclipse first!)
# They use MockCargorobot, so robotsmart26 is NOT required.
# The test JVM opens TCP 8030 itself, so no cargoservice app must be running.
#
# USAGE (from anywhere; Git Bash / WSL / Linux):
#     bash run_tests2.sh            # start broker if needed, run the tests
#     bash run_tests2.sh --no-broker  # assume the broker is already up
# ============================================================================
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPRINT2="$ROOT/CargoService/Sprint2"
MOSQ_YML="$ROOT/yamls/mosquitto.yml"
PROJECT="cargo-mqtt"
START_BROKER=1
[ "${1:-}" = "--no-broker" ] && START_BROKER=0

# tiny TCP port probe using bash /dev/tcp (falls back gracefully if unsupported)
port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null && { exec 3>&- 3<&-; return 0; } || return 1; }

# ---- pick docker compose v2 or v1 -----------------------------------------
if [ "$START_BROKER" = "1" ]; then
  if docker compose version >/dev/null 2>&1; then DC="docker compose"
  elif command -v docker-compose >/dev/null 2>&1; then DC="docker-compose"
  else echo "ERROR: docker / docker-compose not found. Start the broker yourself and re-run with --no-broker."; exit 1; fi
fi

# ---- 1) broker ------------------------------------------------------------
echo "==> [1/4] MQTT broker (localhost:1883)"
if [ "$START_BROKER" = "1" ]; then
  $DC -f "$MOSQ_YML" -p "$PROJECT" up -d || { echo "ERROR: could not start the broker"; exit 1; }
fi
printf "    waiting for 1883 "
BROKER_OK=0
for _ in $(seq 1 30); do
  if port_open 1883; then BROKER_OK=1; echo " up."; break; fi
  printf "."; sleep 1
done
if [ "$BROKER_OK" != "1" ]; then
  echo
  echo "    NOTE: could not confirm 1883 via /dev/tcp (this shell may not support it)."
  echo "          Make sure the broker is running, then continuing after a short wait."
  sleep 3
fi

# ---- 2) port 8030 must be free (the test JVM binds it) --------------------
echo "==> [2/4] Checking TCP 8030 is free"
if port_open 8030; then
  echo "    WARNING: something is LISTENING on 8030 (a running cargoservice app?)."
  echo "             Stop it (close its window / stop_all2.ps1), or the tests cannot bind."
  echo "             Aborting to avoid a misleading failure."
  exit 2
fi
echo "    ok, 8030 is free."

# ---- 3) run the tests -----------------------------------------------------
echo "==> [3/4] Running TestPlanSprint2 (this takes a few minutes: 1 Hz sonar, 3 s persistence, 5 s marking, 30 s window)"
cd "$SPRINT2" || { echo "ERROR: $SPRINT2 not found"; exit 1; }
# cleanTest forces re-run (defeats Gradle's UP-TO-DATE cache on the test task)
./gradlew cleanTest test --console=plain
RESULT=$?

# ---- 4) report ------------------------------------------------------------
echo "==> [4/4] Result"
echo "    HTML report: $SPRINT2/build/reports/tests/test/index.html"
if [ "$RESULT" -eq 0 ]; then
  echo "    BUILD SUCCESSFUL - all TestPlans passed."
else
  echo "    TESTS FAILED (gradle exit $RESULT). Open the report above for the failing plan(s)."
  echo "    Common causes: build not regenerated from the active .qak; broker down; 8030 busy."
fi
exit $RESULT
