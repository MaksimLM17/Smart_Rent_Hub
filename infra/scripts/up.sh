#!/usr/bin/env bash
# Starts Smart Rent Hub local infrastructure with Docker Compose.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "============================================================"
echo " [Smart Rent Hub] Starting Local Infrastructure             "
echo "============================================================"

if ! command -v docker >/dev/null 2>&1; then
    echo "[ERROR] Docker CLI not found. Please install Docker." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "[ERROR] Docker daemon is not running! Please start Docker." >&2
    exit 1
fi

ENV_FILE="${ROOT_DIR}/.env"
ENV_EXAMPLE="${ROOT_DIR}/.env.example"

if [ ! -f "${ENV_FILE}" ] && [ -f "${ENV_EXAMPLE}" ]; then
    echo "Creating .env from .env.example..."
    cp "${ENV_EXAMPLE}" "${ENV_FILE}"
fi

PROFILES=()
DETACH="-d"
BUILD=""
REGISTER_CONNECTORS=true

while [[ $# -gt 0 ]]; do
    case "$1" in
        --obs|-obs)
            PROFILES+=("obs")
            shift
            ;;
        --all|-all)
            PROFILES+=("core" "obs")
            shift
            ;;
        --profile|-p)
            PROFILES+=("$2")
            shift 2
            ;;
        --build)
            BUILD="--build"
            shift
            ;;
        --foreground|-f)
            DETACH=""
            shift
            ;;
        --no-connectors)
            REGISTER_CONNECTORS=false
            shift
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

if [ ${#PROFILES[@]} -eq 0 ]; then
    PROFILES+=("core")
fi

# Deduplicate profiles
UNIQUE_PROFILES=($(echo "${PROFILES[@]}" | tr ' ' '\n' | sort -u | tr '\n' ' '))

COMPOSE_ARGS=("-f" "${ROOT_DIR}/docker-compose.yml")
for p in "${UNIQUE_PROFILES[@]}"; do
    COMPOSE_ARGS+=("--profile" "$p")
done
COMPOSE_ARGS+=("up")

if [ -n "$DETACH" ]; then
    COMPOSE_ARGS+=("$DETACH")
fi

if [ -n "$BUILD" ]; then
    COMPOSE_ARGS+=("$BUILD")
fi

echo "Active Profiles: ${UNIQUE_PROFILES[*]}"
echo "Executing: docker compose ${COMPOSE_ARGS[*]}"
docker compose "${COMPOSE_ARGS[@]}"

if [[ " ${UNIQUE_PROFILES[*]} " =~ " core " ]] && [ "$REGISTER_CONNECTORS" = true ] && [ -n "$DETACH" ]; then
    if [ -f "${ROOT_DIR}/infra/debezium/register-connectors.sh" ]; then
        echo "Registering Debezium outbox connectors..."
        bash "${ROOT_DIR}/infra/debezium/register-connectors.sh" || true
    fi
fi

echo "============================================================"
echo " [OK] Local infrastructure started successfully.            "
echo "============================================================"
