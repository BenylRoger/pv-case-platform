.PHONY: build start stop test logs clean backup restore help

# Default target
.DEFAULT_GOAL := help

OPS := ./ops/run.sh
BACKUP := ./ops/backup.sh
RESTORE := ./ops/restore.sh

## Build the Docker image
build:
	$(OPS) build

## Start the service (waits for healthy)
start:
	$(OPS) start

## Stop the service
stop:
	$(OPS) stop

## Run smoke tests against the running service
test:
	$(OPS) test

## Tail service logs (Ctrl-C to exit)
logs:
	$(OPS) logs

## Remove containers, images, and backups
clean:
	$(OPS) clean

## Backup all cases to backups/ directory
backup:
	$(BACKUP)

## Restore from a backup (usage: make restore FILE=backups/pv-cases-YYYYMMDD-HHMMSS.json)
restore:
ifndef FILE
	$(error FILE is required. Usage: make restore FILE=backups/pv-cases-20260408-120000.json)
endif
	$(RESTORE) $(FILE)

## Dry-run restore (usage: make restore-dry FILE=backups/...)
restore-dry:
ifndef FILE
	$(error FILE is required. Usage: make restore-dry FILE=backups/pv-cases-20260408-120000.json)
endif
	$(RESTORE) $(FILE) --dry-run

## Run unit tests locally (requires Java + Maven)
unit-test:
	cd backend && mvn test

## Print this help
help:
	@echo ""
	@echo "PV Case Processing Platform — Make targets"
	@echo "──────────────────────────────────────────"
	@grep -E '^## ' $(MAKEFILE_LIST) | sed 's/## /  /'
	@echo ""
	@echo "Quick start:"
	@echo "  make build && make start && make test"
	@echo ""
