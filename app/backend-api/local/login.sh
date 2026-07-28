#!/usr/bin/env bash
# Gera um AccessToken JWT pro usuário admin/123 no user pool local (floci).
# Requer: docker, aws cli, start_local.sh já rodado (usa COGNITO_USER_POOL_ID/CLIENT_ID do .env).
set -euo pipefail

LOCAL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$LOCAL_DIR/../.env"
ENDPOINT_URL="http://localhost:4566"
REGION="us-east-1"
ADMIN_USER="admin"
ADMIN_PASSWORD="123"

# shellcheck disable=SC1090
source "$ENV_FILE"

if [ -z "${COGNITO_USER_POOL_ID:-}" ] || [ -z "${COGNITO_CLIENT_ID:-}" ]; then
  echo "COGNITO_USER_POOL_ID/COGNITO_CLIENT_ID vazios em $ENV_FILE — rode start_local.sh primeiro" >&2
  exit 1
fi

aws --endpoint-url "$ENDPOINT_URL" --region "$REGION" cognito-idp admin-initiate-auth \
  --user-pool-id "$COGNITO_USER_POOL_ID" --client-id "$COGNITO_CLIENT_ID" \
  --auth-flow ADMIN_NO_SRP_AUTH --auth-parameters USERNAME="$ADMIN_USER",PASSWORD="$ADMIN_PASSWORD" \
  --query "AuthenticationResult.AccessToken" --output text
