#!/usr/bin/env bash
# Stops Smart Rent Hub local infrastructure containers.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "============================================================"
echo " [Smart Rent Hub] Stopping Local Infrastructure             "
echo "============================================================"

COMPOSE_ARGS=("-f" "${ROOT_DIR}/docker-compose.yml" "--profile" "core" "--profile" "obs" "down")

while [[ $# -gt 0 ]]; do
    case "$1" in
        -v|--volumes)
            COMPOSE_ARGS+=("-v")
            shift
            ;;
        *)
            shift
            ;;
    esac
done

COMPOSE_ARGS+=("--remove-orphans")

echo "Executing: docker compose ${COMPOSE_ARGS[*]}"
docker compose "${COMPOSE_ARGS[@]}"

echo "[OK] Local infrastructure stopped."
