# PV Case Processing Platform

Spring Boot backend + React frontend for pharmacovigilance case review.

---

## Quick start (Docker)

```bash
make build    # Build Docker image (~2 min first time)
make start    # Start service, wait for healthy
make test     # Smoke tests against running service
make stop     # Stop service
```

Or directly:

```bash
./ops/run.sh build
./ops/run.sh start
```

---

## Project structure

```
.
├── backend/          # Spring Boot 3.2 (Java 17, Maven)
│   └── src/
│       ├── main/java/com/theragenx/pvcases/
│       │   ├── controller/      CaseController, QueryController, HealthController
│       │   ├── service/         CaseService (merge logic), QueryService
│       │   ├── model/           CaseDocument, MergedCase, MergedField, ReviewQuery
│       │   ├── dto/             QueryRequest
│       │   └── exception/       CaseNotFoundException, GlobalExceptionHandler
│       └── test/                6 unit tests covering merge edge cases
├── ops/
│   ├── run.sh        build | start | stop | test | logs | clean
│   ├── backup.sh     Fetch and write timestamped case backups
│   └── restore.sh    Restore cases from backup (--dry-run supported)
├── frontend/         Next.js 14 + Tailwind (Phase 2 React UI)
├── Dockerfile        Multi-stage, non-root, JRE-only runtime
├── docker-compose.yml
├── Makefile
└── case_v2_followup_payload.json   Sample follow-up for Phase 2
```

---

## API endpoints

### `GET /health`
Service liveness check. Used by Docker healthcheck and ops scripts.

```bash
curl http://localhost:8080/health
```

### `GET /cases/{caseId}`
Returns the most recent merged case view. All fields include `status`, `confidence`, and `source`.

```bash
curl http://localhost:8080/cases/PV-2026-0451 | jq .
```

### `POST /cases/{caseId}/follow-ups`
Merges a follow-up extraction onto the stored case. Returns diff-annotated merged case.

**Merge rules:**
- Same value → `status: "unchanged"`
- Changed value → `status: "overridden"`, includes `previous_value`
- New field (not in stored) → `status: "new"`
- Field in stored but absent from follow-up → retained as `"unchanged"` *(absence ≠ deletion)*

```bash
curl -X POST http://localhost:8080/cases/PV-2026-0451/follow-ups \
  -H "Content-Type: application/json" \
  -d @case_v2_followup_payload.json | jq .
```

### `POST /queries`
Raise a reviewer query against a specific field.

```bash
curl -X POST http://localhost:8080/queries \
  -H "Content-Type: application/json" \
  -d '{
    "case_id": "PV-2026-0451",
    "field_path": "sections.adverse_event.seriousness",
    "question": "Source document page 4 appears to say Serious. Please confirm."
  }' | jq .
```

### `GET /queries?caseId={id}`
List all queries for a case.

```bash
curl "http://localhost:8080/queries?caseId=PV-2026-0451" | jq .
```

---

## Running locally (without Docker)

Requires Java 17+ and Maven.

```bash
cd backend
mvn spring-boot:run
```

Service starts on port 8080.

---

## Running unit tests

```bash
cd backend
mvn test
```

Tests cover: unchanged fields, overridden fields with `previous_value`, new fields, stored-only fields retained, `missing_fields` surfaced, 404 on unknown case.

---

## Operations

### How to build and deploy

```bash
# 1. Build the Docker image
./ops/run.sh build

# 2. Start the service
./ops/run.sh start

# 3. Verify healthy
curl http://localhost:8080/health
```

### How to verify the service is healthy

```bash
curl -s http://localhost:8080/health | jq .
# Expect: { "status": "UP", "service": "pv-cases", ... }

# Or use Docker
docker ps --filter name=pv-cases --format "{{.Status}}"
# Expect: Up X minutes (healthy)
```

### How to back up and restore data

**Backup:**
```bash
./ops/backup.sh
# Writes: backups/pv-cases-YYYYMMDD-HHMMSS.json
# Safe to schedule: crontab -e → 0 2 * * * /path/to/ops/backup.sh
```

**Restore (dry-run first):**
```bash
./ops/restore.sh backups/pv-cases-20260408-120000.json --dry-run
./ops/restore.sh backups/pv-cases-20260408-120000.json
```

Restore is idempotent: replaying the same data marks all fields `"unchanged"`.

### How to debug a failed startup

```bash
# 1. Check container status
docker ps -a --filter name=pv-cases

# 2. Tail startup logs
./ops/run.sh logs
# or
docker logs pv-cases --tail 100

# 3. Common causes:
#    - Port 8080 already in use: lsof -i :8080
#    - case_v1.json missing from resources/data/: check backend/src/main/resources/data/
#    - OOM: docker stats pv-cases
```

### What to check first if requests are failing

1. **Is the service up?** `curl http://localhost:8080/health`
2. **Right port?** Service runs on 8080 (mapped in docker-compose.yml)
3. **Case exist?** `curl http://localhost:8080/cases/PV-2026-0451` — 404 means bootstrap failed
4. **Validation error?** POST bodies must include `case_id`, `field_path`, `question` — check response body for field-level error detail
5. **Recent logs?** `./ops/run.sh logs` — Spring Boot logs all incoming requests at INFO

---

## Frontend (Phase 2)

```bash
cd frontend
npm install
npm run dev
# Opens on http://localhost:3000
```

Set `NEXT_PUBLIC_API_URL=http://localhost:8080` in `frontend/.env.local` if you need to override.
