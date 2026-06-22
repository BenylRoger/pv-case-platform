#!/usr/bin/env bash
# ops/restore.sh — Restore cases from a backup file
#
# Usage: ./ops/restore.sh <backup-file> [--dry-run]
#
# Idempotent: POSTing a follow-up with the same data will mark all fields
# "unchanged" so repeated restores are safe.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVICE_URL="${PV_SERVICE_URL:-http://localhost:8080}"
DRY_RUN=false
BACKUP_FILE=""

# ── Arg parsing ───────────────────────────────────────────────────────────────
usage() {
    cat <<EOF
Usage: $(basename "$0") <backup-file> [--dry-run]

  backup-file   Path to a backup JSON file produced by backup.sh
  --dry-run     Print what would be restored without making any HTTP calls

Examples:
  ./ops/restore.sh backups/pv-cases-20260408-120000.json
  ./ops/restore.sh backups/pv-cases-20260408-120000.json --dry-run
EOF
}

for arg in "$@"; do
    case "${arg}" in
        --dry-run) DRY_RUN=true ;;
        --help|-h) usage; exit 0 ;;
        -*) echo "Unknown option: ${arg}"; usage; exit 1 ;;
        *)  BACKUP_FILE="${arg}" ;;
    esac
done

if [ -z "${BACKUP_FILE}" ]; then
    echo "ERROR: backup-file argument is required." >&2
    usage
    exit 1
fi

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "ERROR: File not found: ${BACKUP_FILE}" >&2
    exit 1
fi

# ── Dependency checks ─────────────────────────────────────────────────────────
require_cmd() { command -v "$1" &>/dev/null || { echo "ERROR: '$1' not found" >&2; exit 1; }; }
require_cmd curl
require_cmd jq

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [restore.sh] $*" >&2; }
err() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [restore.sh] ERROR: $*" >&2; }

# ── Validate backup file ──────────────────────────────────────────────────────
case_count=$(jq '.case_count // 0' "${BACKUP_FILE}")
backup_ts=$(jq -r '.backup_timestamp // "unknown"' "${BACKUP_FILE}")
log "Backup from ${backup_ts} contains ${case_count} case(s)."

if [ "${case_count}" -eq 0 ]; then
    log "Nothing to restore. Exiting."
    exit 0
fi

# ── Health check (skip in dry-run) ────────────────────────────────────────────
if [ "${DRY_RUN}" = "false" ]; then
    log "Checking service health at ${SERVICE_URL}/health…"
    if ! curl -sf --max-time 5 "${SERVICE_URL}/health" | jq -e '.status == "UP"' &>/dev/null; then
        err "Service is not healthy. Start it with: ./ops/run.sh start"
        exit 1
    fi
fi

# ── Restore each case ─────────────────────────────────────────────────────────
failed=0
restored=0

while IFS= read -r case_json; do
    case_id=$(echo "${case_json}" | jq -r '.case_id')

    if [ "${DRY_RUN}" = "true" ]; then
        log "[DRY-RUN] Would POST case ${case_id} to ${SERVICE_URL}/cases/${case_id}/follow-ups"
        continue
    fi

    log "Restoring case ${case_id}…"
    response=$(echo "${case_json}" | curl -sf --max-time 10 \
        -X POST "${SERVICE_URL}/cases/${case_id}/follow-ups" \
        -H "Content-Type: application/json" \
        -d @- 2>&1) || {
        err "Failed to restore case ${case_id}"
        failed=$((failed + 1))
        continue
    }

    echo "${response}" | jq -e '.case_id' &>/dev/null || {
        err "Unexpected response for case ${case_id}: ${response}"
        failed=$((failed + 1))
        continue
    }

    restored=$((restored + 1))
    log "  Case ${case_id} restored (version: $(echo "${response}" | jq '.version'))"

done < <(jq -c '.cases[]' "${BACKUP_FILE}")

# ── Summary ───────────────────────────────────────────────────────────────────
if [ "${DRY_RUN}" = "true" ]; then
    log "Dry-run complete. ${case_count} case(s) would be restored."
    exit 0
fi

if [ "${failed}" -gt 0 ]; then
    err "${failed} case(s) failed to restore. Check logs above."
    exit 1
fi

log "Restore complete. ${restored} case(s) restored successfully."
