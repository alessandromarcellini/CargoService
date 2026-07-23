#!/usr/bin/env bash
################################################################################
# stop_all2.sh  -  CargoService Sprint 2 full-stack teardown (Ubuntu / bash)
################################################################################
# Ripulisce tutto ciò che è stato avviato da start_all2.sh, in ordine inverso:
#
#   1) le app qak           (cargoservice TCP 8030, robotsmart26 TCP 8020)
#   2) i daemon Gradle      (gradlew --stop nelle dir Sprint 2 e robotsmart26)
#   3) gli stack docker     (vrWithGui26, poi mosquitto)
#
# Il modello sim-devices (se avviato con --simulated) gira come task Gradle nel
# progetto Sprint 2, quindi 'gradlew --stop' in quella dir ferma anche il suo
# daemon; chiudi la sua finestra/terminale se è ancora aperto.
#
# USO (dalla root del repo, in un terminale SEPARATO da start_all2.sh):
#     ./stop_all2.sh
#
# NOTA: rileva automaticamente se usare 'docker-compose' (v1) o 'docker compose' (v2).
################################################################################

# La pulizia deve essere best-effort: continua anche se uno step fallisce.
set +e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOSQUITTO_YML="$ROOT/yamls/mosquitto.yml"
VR_YAML="$ROOT/yamls/vrWithGui26.yaml"
ROBOT_DIR="$ROOT/robotsmart26"
SPRINT2_DIR="$ROOT/CargoService/Sprint2"

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

write_step() { printf "\n${CYAN}[%s] %s${NC}\n" "$1" "$2"; }

if command -v docker-compose >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker-compose)
elif docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker compose)
else
    DOCKER_COMPOSE=()
fi

# Ferma qualunque processo in ASCOLTO su una porta TCP locale (i contesti qak).
stop_port() {
    local port="$1" label="${2:-porta $1}"
    local pids=""

    if command -v ss >/dev/null 2>&1; then
        pids=$(ss -ltnp "sport = :$port" 2>/dev/null | grep -oP 'pid=\K[0-9]+' | sort -u)
    fi
    if [ -z "$pids" ] && command -v fuser >/dev/null 2>&1; then
        pids=$(fuser -n tcp "$port" 2>/dev/null)
    fi
    if [ -z "$pids" ] && command -v lsof >/dev/null 2>&1; then
        pids=$(lsof -ti tcp:"$port" 2>/dev/null)
    fi

    if [ -z "$pids" ]; then
        echo "    $label : nessun processo in ascolto."
        return
    fi

    for pid in $pids; do
        local pname
        pname=$(ps -p "$pid" -o comm= 2>/dev/null)
        printf "${YELLOW}    stop %s -> PID %s (%s)${NC}\n" "$label" "$pid" "${pname:-sconosciuto}"
        kill -9 "$pid" 2>/dev/null
    done
}

# --- 1) ferma le app qak --------------------------------------------------
write_step 1 "Arresto dei servizi qak (cargoservice TCP 8030, robotsmart26 TCP 8020) ..."
stop_port 8030 "cargoservice (8030)"
stop_port 8020 "robotsmart26 (8020)"

# --- 2) ferma i daemon Gradle (ferma anche il task sim-devices, se presente) ---
write_step 2 "Arresto dei daemon Gradle ..."
if [ -d "$SPRINT2_DIR" ]; then ( cd "$SPRINT2_DIR" && ./gradlew --stop ); fi
if [ -d "$ROBOT_DIR" ]; then ( cd "$ROBOT_DIR" && ./gradlew --stop ); fi

# --- 3) ferma gli stack docker (ordine inverso rispetto all'avvio) ---------
write_step 3 "Arresto dei container docker ..."
if [ ${#DOCKER_COMPOSE[@]} -eq 0 ]; then
    echo "  ATTENZIONE: né 'docker-compose' né 'docker compose' trovati; salto lo stop dei container." >&2
else
    if [ -f "$VR_YAML" ]; then "${DOCKER_COMPOSE[@]}" -f "$VR_YAML" -p cargo-vr down; fi
    if [ -f "$MOSQUITTO_YML" ]; then "${DOCKER_COMPOSE[@]}" -f "$MOSQUITTO_YML" -p cargo-mqtt down; fi
fi

printf "\n${GREEN}Pulizia completata. Se i terminali di 'robotsmart26', 'cargoservice' o 'sim devices' sono ancora aperti, puoi chiuderli.${NC}\n\n"
