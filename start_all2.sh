#!/usr/bin/env bash
################################################################################
# start_all2.sh  -  CargoService Sprint 2 full-stack launcher (Ubuntu / bash)
################################################################################
# Avvia l'intero stack demo Sprint 2, in ordine. A differenza dello Sprint 1
# (il cui IOPort era una console che leggeva da stdin), qui l'IOPort è una
# WEB GUI: il pulsante è un bottone nel browser, quindi questo script apre
# anche la pagina della GUI per te.
#
#   1) mosquitto            (broker MQTT, docker) - DUE listener:
#                             * 1883  MQTT nativo   (board, applicazione, device sim)
#                             * 9001  MQTT/WebSocket (la GUI nel browser)
#   2) vrWithGui26          (robot virtuale + GUI,  docker, porta 8090)
#   3) robotsmart26         (servizio robot,        gradlew run, TCP 8020) -> nuovo terminale
#   4) Sprint 2 application  (ctxcargoservice:       gradlew run, TCP 8030) -> nuovo terminale
#                             cargoservice + cargorobot + logic sonar
#   5) devices              (FIELD = flasha il Pico W a mano; SIMULATED = sonar+LED
#                            simulati girano DENTRO l'app, costruita dal modello sim)
#   6) la web GUI           (pagina statica ioport, aperta nel browser di default)
#
# USO (dalla root del repo):
#     # variante FIELD (il Pico W fisico pubblica su sonar_data / legge led_data):
#     ./start_all2.sh
#
#     # variante SIMULATED (nessun hardware: il modello sim-devices sostituisce la board):
#     ./start_all2.sh --simulated
#
# STOP:
#     ./stop_all2.sh
#
# NOTA:
#   - $gui_page : dove si trova la pagina statica della GUI (adatta se la sposti).
#   - FIELD vs SIMULATED è una scelta di MODELLO (quale .qak generi), non un flag
#     a runtime: cargoservice_sprint2.qak (field) / cargoservice_sprint2_sim.qak
#     (simulated, superset).
#   - mosquitto.yml deve esporre ENTRAMBI i listener (1883 nativo + 9001 websocket).
#   - Per "robotsmart26" e "cargoservice" viene aperto un nuovo terminale grafico se
#     disponibile (gnome-terminal / xterm / konsole); altrimenti si va in background
#     con i log salvati in /tmp.
################################################################################

set -u

SIMULATED=false
for arg in "$@"; do
    case "$arg" in
        --simulated|-Simulated) SIMULATED=true ;;
    esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"   # questo script vive nella root del repo
MOSQUITTO_YML="$ROOT/yamls/mosquitto.yml"
VR_YAML="$ROOT/yamls/vrWithGui26.yaml"
ROBOT_DIR="$ROOT/robotsmart26"
SPRINT2_DIR="$ROOT/CargoService/Sprint2"

# --- percorsi -----------------------------------------------------------------
GUI_PAGE="$SPRINT2_DIR/web/ioport.html"   # pagina statica della GUI (client MQTT-over-WebSocket)

# Colori (fallback a niente se il terminale non li supporta)
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

write_step() { printf "\n${CYAN}[%s] %s${NC}\n" "$1" "$2"; }

# Individua un docker compose utilizzabile (v1 o v2)
if command -v docker-compose >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker-compose)
elif docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker compose)
else
    echo "Errore: né 'docker-compose' né 'docker compose' sono disponibili." >&2
    exit 1
fi

# Attende che una porta TCP su localhost accetti connessioni (o va in timeout).
wait_tcp() {
    local port="$1" timeout_sec="${2:-90}" label="${3:-porta $1}"
    printf "    in attesa di %s (localhost:%s) ..." "$label" "$port"
    local deadline=$(( $(date +%s) + timeout_sec ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
            exec 3>&- 3<&-
            printf " up.\n"
            return 0
        fi
        sleep 1
        printf "."
    done
    printf " TIMEOUT.\n"
    echo "  ATTENZIONE: $label non è salita entro ${timeout_sec}s. Continuo comunque." >&2
    return 1
}

# Apre un comando in un nuovo terminale grafico se possibile, altrimenti in background con log.
run_in_new_window() {
    local title="$1" workdir="$2" cmd="$3" logfile="$4"

    if command -v gnome-terminal >/dev/null 2>&1; then
        gnome-terminal --title="$title" -- bash -c "cd '$workdir' && echo '$title - lascia aperto questo terminale' && $cmd; exec bash"
    elif command -v konsole >/dev/null 2>&1; then
        konsole --new-tab -p tabtitle="$title" -e bash -c "cd '$workdir' && echo '$title - lascia aperto questo terminale' && $cmd; exec bash" &
    elif command -v xterm >/dev/null 2>&1; then
        xterm -T "$title" -e bash -c "cd '$workdir' && echo '$title - lascia aperto questo terminale' && $cmd; exec bash" &
    else
        echo "    (nessun terminale grafico trovato: avvio '$title' in background, log -> $logfile)"
        ( cd "$workdir" && bash -c "$cmd" > "$logfile" 2>&1 & )
    fi
}

# --- controlli preliminari ------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
    echo "Errore: docker non trovato nel PATH. Avvia il servizio Docker e riprova." >&2
    exit 1
fi
for p in "$MOSQUITTO_YML" "$VR_YAML" "$ROBOT_DIR" "$SPRINT2_DIR"; do
    if [ ! -e "$p" ]; then
        echo "Errore: percorso atteso non trovato: $p" >&2
        exit 1
    fi
done

# --- 1) mosquitto (1883 nativo + 9001 websocket) ---------------------------
write_step 1 "Avvio mosquitto (broker MQTT: 1883 nativo + 9001 websocket) ..."
"${DOCKER_COMPOSE[@]}" -f "$MOSQUITTO_YML" -p cargo-mqtt up -d
wait_tcp 1883 60 "mosquitto MQTT"
wait_tcp 9001 30 "mosquitto WebSocket"

# --- 2) vrWithGui26 ---------------------------------------------------------
write_step 2 "Avvio vrWithGui26 (robot virtuale + GUI) ..."
"${DOCKER_COMPOSE[@]}" -f "$VR_YAML" -p cargo-vr up -d
wait_tcp 8090 90 "robot virtuale"

# --- 3) robotsmart26 (finestra separata, server long-running) --------------
write_step 3 "Avvio robotsmart26 (gradlew run) in un terminale separato ..."
run_in_new_window "robotsmart26" "$ROBOT_DIR" "./gradlew run --console=plain" "/tmp/robotsmart26.log"
wait_tcp 8020 120 "robotsmart26 (TCP 8020)"
sleep 3   # piccolo periodo di grazia dopo l'apertura della porta

# --- 4) Sprint 2 application (finestra separata, server; guidata dalla GUI) ---
write_step 4 "Avvio della Sprint 2 application (ctxcargoservice: cargoservice + cargorobot + logic sonar) ..."
run_in_new_window "cargoservice (Sprint 2)" "$SPRINT2_DIR" "./gradlew run --console=plain" "/tmp/cargoservice.log"
wait_tcp 8030 120 "cargoservice (TCP 8030)"

# --- 5) devices --------------------------------------------------------------
# I device sono scelti nel MODELLO, non a runtime: sonar+LED simulati fanno parte
# dell'applicazione quando è costruita da cargoservice_sprint2_sim.qak (co-locati
# in ctxcargoservice), quindi lo step 4 li ha già avviati. Questo flag serve solo
# a stampare il promemoria corretto.
if [ "$SIMULATED" = true ]; then
    write_step 5 "Device SIMULATED: girano DENTRO l'applicazione (costruita da cargoservice_sprint2_sim.qak)."
    printf "${YELLOW}    -> Nessun processo separato. Se sonar/LED simulati non girano, hai costruito il modello FIELD:${NC}\n"
    printf "${YELLOW}       dai a cargoservice_sprint2_sim.qak l'estensione .qak (quello field -> .qaktt), rigenera, ricompila.${NC}\n"
else
    write_step 5 "Variante FIELD: flasha il Pico W."
    printf "${YELLOW}    -> Flasha pico_code/pico_mqtt.py sul Pico W (imposta SSID/password Wi-Fi e l'IP del broker${NC}\n"
    printf "${YELLOW}       all'indirizzo LAN di questa macchina); la board si collega su sonar_data / led_data automaticamente.${NC}\n"
    printf "${YELLOW}    -> (Senza hardware: costruisci l'app da cargoservice_sprint2_sim.qak ed esegui con --simulated.)${NC}\n"
fi

# --- 6) apri la web GUI (IOPort) nel browser di default ---------------------
write_step 6 "Apertura della web GUI (IOPort) ..."
if [ -f "$GUI_PAGE" ]; then
    if command -v xdg-open >/dev/null 2>&1; then
        xdg-open "$GUI_PAGE" >/dev/null 2>&1 &
    elif command -v sensible-browser >/dev/null 2>&1; then
        sensible-browser "$GUI_PAGE" >/dev/null 2>&1 &
    else
        echo "  ATTENZIONE: nessun apri-browser trovato (xdg-open). Apri manualmente: $GUI_PAGE" >&2
    fi
    printf "${GREEN}    -> GUI aperta: %s${NC}\n" "$GUI_PAGE"
else
    echo "  ATTENZIONE: pagina GUI non trovata in $GUI_PAGE - aggiorna \$GUI_PAGE una volta pronta la GUI Sprint 2, poi aprila manualmente." >&2
fi

printf "\n${GREEN}Tutti i servizi sono stati avviati.${NC}\n"
printf "${GREEN}  Broker: 1883 (nativo) / 9001 (websocket) | robot: 8020 | cargoservice: 8030 | robot virtuale: http://localhost:8090${NC}\n"
printf "${GREEN}  Guida la demo dalla GUI del browser (pulsante). Per fermare tutto:  ./stop_all2.sh${NC}\n\n"
