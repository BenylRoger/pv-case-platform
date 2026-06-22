#!/usr/bin/env bash
# ops/backup.sh — Backup all cases from the running service
#
# Usage: ./ops/backup.sh [--dry-run]
# Output: backups/pv-cases-YYYYMMDD-HHMMSS.json
# Safe for cron scheduling — no interactive prompts, non-zero exit on failure.

set -euo pipefail

# ── Config ────────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVICE_URL="${PV_SERVICE_URL:-http://localhost:8080}"
BACKUP_DIR="${PROJECT_ROOT}/backups"
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/pv-cases-${TIMESTAMP}.json"

# Well-known case IDs. In production this would be driven by a /cases index endpoint.
# For now, maintain this list or extend ops/run.sh to expose it.
KNOWN_CASES=("PV-2026-0451")

# ── Logging (stderr so it doesn't pollute stdout / file content) ───────────────
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [backup.sh] $*" >&2; }
err() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [backup.sh] ERROR: $*" >&2; }

# ── Guards ────────────────────────────────────────────────────────────────────
require_cmd() {
    command -v "$1" &>/dev/null || { err "Required command '$1' not found. Install it and retry."; exit 1; }
}
require_cmd curl
require_cmd jq

# ── Health check ──────────────────────────────────────────────────────────────
log "Checking service health at ${SERVICE_URL}/health…"
if ! curl -sf --max-time 5 "${SERVICE_URL}/health" | jq -e '.status == "UP"' &>/dev/null; then
    err "Service is not healthy. Aborting backup."
    exit 1
fi

# ── Create backup directory ───────────────────────────────────────────────────
mkdir -p "${BACKUP_DIR}"

# ── Fetch cases ───────────────────────────────────────────────────────────────
log "Starting backup of ${#KNOWN_CASES[@]} case(s) to ${BACKUP_FILE}"

all_cases="[]"

for case_id in "${KNOWN_CASES[@]}"; do
    log "  Fetching case ${case_id}…"
    case_data=$(curl -sf --max-time 10 "${SERVICE_URL}/cases/${case_id}") || {
        err "Failed to fetch case ${case_id}. Aborting."
        exit 1
    }

    # Validate it looks like a case (has case_id field)
    echo "${case_data}" | jq -e '.case_id' &>/dev/null || {
        err "Response for ${case_id} does not contain case_id. Aborting."
        exit 1
    }

    all_cases=$(echo "${all_cases}" | jq --argjson c "${case_data}" '. + [$c]')
done

# ── Write backup file ─────────────────────────────────────────────────────────
backup_payload=$(jq -n \
    --arg ts "${TIMESTAMP}" \
    --arg url "${SERVICE_URL}" \
    --argjson cases "${all_cases}" \
    '{
        "backup_timestamp": $ts,
        "service_url": $url,
        "case_count": ($cases | length),
        "cases": $cases
    }')

echo "${backup_payload}" > "${BACKUP_FILE}"

# Sanity-check the written file
actual_count=$(jq '.case_count' "${BACKUP_FILE}")
log "Backup complete. ${actual_count} case(s) written to ${BACKUP_FILE}"

# ── Retention: keep last 30 backups ───────────────────────────────────────────
backup_count=$(find "${BACKUP_DIR}" -name "pv-cases-*.json" | wc -l | tr -d ' ')
if [ "${backup_count}" -gt 30 ]; then
    log "Pruning old backups (keeping 30 most recent)…"
    find "${BACKUP_DIR}" -name "pv-cases-*.json" -printf '%T+ %p\n' \
        | sort | head -n "$((backup_count - 30))" | awk '{print $2}' \
        | xargs rm -f
fi
