#!/usr/bin/env bash
# Para postgres+floci mantendo volumes (dados persistem).
set -euo pipefail

LOCAL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Parando docker compose (volumes preservados)"
docker compose -f "$LOCAL_DIR/docker-compose.yml" stop
