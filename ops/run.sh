#!/usr/bin/env bash
# ops/run.sh — Service lifecycle management

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.yml"
BACKEND_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:3000"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log()  { echo -e "${GREEN}[run.sh]${NC} $*"; }
warn() { echo -e "${YELLOW}[run.sh]${NC} $*"; }
err()  { echo -e "${RED}[run.sh] ERROR:${NC} $*" >&2; }
info() { echo -e "${CYAN}[run.sh]${NC} $*"; }

require_docker() {
    if ! docker info &>/dev/null; then
        err "Docker is not running. Start Docker Desktop and retry."
        exit 1
    fi
}

cmd_build() {
    require_docker
    log "Building images (backend + frontend)..."
    docker compose -f "${COMPOSE_FILE}" build
    log "Build complete."
}

cmd_start() {
    require_docker
    log "Starting all services..."
    docker compose -f "${COMPOSE_FILE}" up -d

    log "Waiting for backend to become healthy..."
    local i=0
    until curl -sf "${BACKEND_URL}/health" | grep -q 'UP' 2>/dev/null; do
        i=$((i + 1))
        if [ "${i}" -ge 24 ]; then
            err "Backend did not start in 120s. Run: ./ops/run.sh logs"
            exit 1
        fi
        sleep 5
        printf '.'
    done
    echo

    log "Waiting for frontend..."
    i=0
    until curl -sf "${FRONTEND_URL}" &>/dev/null; do
        i=$((i + 1))
        if [ "${i}" -ge 12 ]; then
            warn "Frontend taking longer than expected. Check: ./ops/run.sh logs frontend"
            break
        fi
        sleep 5
        printf '.'
    done
    echo

    echo ""
    info "Backend  -> ${BACKEND_URL}"
    info "Frontend -> ${FRONTEND_URL}"
}

cmd_stop() {
    require_docker
    log "Stopping all services..."
    docker compose -f "${COMPOSE_FILE}" down
    log "Stopped."
}

cmd_test() {
    log "Running smoke tests against ${BACKEND_URL}..."
    echo ""

    info "1. GET /health"
    RESULT=$(curl -sf "${BACKEND_URL}/health") || { err "Health check failed. Is the backend running?"; exit 1; }
    echo "   ${RESULT}"

    info "2. GET /cases/PV-2026-0451"
    RESULT=$(curl -sf "${BACKEND_URL}/cases/PV-2026-0451") || { err "Case fetch failed"; exit 1; }
    echo "${RESULT}" | grep -q '"case_id"' || { err "Unexpected response"; exit 1; }
    echo "   OK - case loaded"

    info "3. POST /cases/PV-2026-0451/follow-ups"
    PAYLOAD_FILE="${PROJECT_ROOT}/case_v2_followup_payload.json"
    if [ -f "${PAYLOAD_FILE}" ]; then
        RESULT=$(curl -sf -X POST "${BACKEND_URL}/cases/PV-2026-0451/follow-ups" \
            -H "Content-Type: application/json" -d @"${PAYLOAD_FILE}") || { err "Follow-up POST failed"; exit 1; }
        echo "   OK - follow-up merged"
    else
        warn "   case_v2_followup_payload.json not found, skipping"
    fi

    info "4. POST /queries"
    RESULT=$(curl -sf -X POST "${BACKEND_URL}/queries" \
        -H "Content-Type: application/json" \
        -d '{"case_id":"PV-2026-0451","field_path":"sections.adverse_event.seriousness","question":"Confirm seriousness from page 4."}') \
        || { err "Query POST failed"; exit 1; }
    echo "   OK - query created"

    info "5. GET /queries?caseId=PV-2026-0451"
    RESULT=$(curl -sf "${BACKEND_URL}/queries?caseId=PV-2026-0451") || { err "Query list failed"; exit 1; }
    echo "   OK - queries listed"

    echo ""
    log "All smoke tests passed"
}

cmd_logs() {
    require_docker
    SVC="${2:-}"
    if [ -n "${SVC}" ]; then
        docker compose -f "${COMPOSE_FILE}" logs -f "${SVC}"
    else
        docker compose -f "${COMPOSE_FILE}" logs -f
    fi
}

cmd_clean() {
    require_docker
    warn "Removes all containers, images, and the backups/ directory."
    read -r -p "Continue? [y/N] " confirm
    case "${confirm}" in
        [Yy]*) ;;
        *) log "Aborted."; exit 0 ;;
    esac
    docker compose -f "${COMPOSE_FILE}" down --rmi all --volumes --remove-orphans
    rm -rf "${PROJECT_ROOT}/backups"
    log "Clean done."
}

usage() {
    echo ""
    echo "Usage: $(basename "$0") <command>"
    echo ""
    echo "Commands:"
    echo "  build    Build backend + frontend Docker images"
    echo "  start    Start all services and wait for healthy"
    echo "  stop     Stop all services"
    echo "  test     Run smoke tests"
    echo "  logs     Tail all logs (or: logs backend / logs frontend)"
    echo "  clean    Remove containers, images, and backups"
    echo ""
}

CMD="${1:-}"
case "${CMD}" in
    build) cmd_build ;;
    start) cmd_start ;;
    stop)  cmd_stop  ;;
    test)  cmd_test  ;;
    logs)  cmd_logs "$@" ;;
    clean) cmd_clean ;;
    --help|help|-h) usage; exit 0 ;;
    "") err "No command."; usage; exit 1 ;;
    *)  err "Unknown: '${CMD}'"; usage; exit 1 ;;
esac
