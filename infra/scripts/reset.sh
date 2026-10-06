#!/usr/bin/env bash
# Resets Smart Rent Hub local infrastructure from scratch (down -v -> up).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "============================================================"
echo " [Smart Rent Hub] Resetting Local Infrastructure            "
echo "============================================================"

echo "Stopping containers and deleting all persistent volumes..."
bash "${SCRIPT_DIR}/down.sh" -v

echo "Starting fresh infrastructure instances..."
bash "${SCRIPT_DIR}/up.sh" "$@"

echo "[OK] Infrastructure successfully reset."
