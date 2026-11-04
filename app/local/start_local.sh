#!/usr/bin/env bash
# Sobe postgres+floci e provisiona Cognito local (user pool, client, admin/123) + bucket S3.
# Requer: docker, aws cli.
set -euo pipefail

LOCAL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ENV_FILE="$LOCAL_DIR/../backend-api/.env"
FRONTEND_ENV_FILE="$LOCAL_DIR/../frontend/.env"
ENDPOINT_URL="http://localhost:4566"
REGION="us-east-1"
POOL_NAME="notebooklm-local"
ADMIN_USER="admin"
ADMIN_PASSWORD="123"
BUCKET_NAME="$(grep -m1 '^S3_BUCKET_NAME=' "$BACKEND_ENV_FILE" 2>/dev/null | cut -d= -f2-)"
BUCKET_NAME="${BUCKET_NAME:-notebooklm-sources-local}"
QUEUE_NAME="notebooklm-ingestion-local"
# Espelha infra/main.tf (ingest_queue): visibility 180s e DLQ após 3 recebimentos.
QUEUE_VISIBILITY_TIMEOUT="180"
QUEUE_MAX_RECEIVE_COUNT="3"

if [ ! -f "$FRONTEND_ENV_FILE" ]; then
  echo "$FRONTEND_ENV_FILE não existe — copie app/frontend/.env.example antes de rodar este script" >&2
  exit 1
fi

aws_local() {
  aws --endpoint-url "$ENDPOINT_URL" --region "$REGION" "$@"
}

echo "==> Subindo docker compose (postgres + floci)"
docker compose -f "$LOCAL_DIR/docker-compose.yml" up -d

echo "==> Aguardando floci responder em $ENDPOINT_URL"
for i in $(seq 1 30); do
  if aws_local cognito-idp list-user-pools --max-results 1 >/dev/null 2>&1; then
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "floci não respondeu a tempo" >&2
    exit 1
  fi
  sleep 1
done

echo "==> Verificando user pool existente ($POOL_NAME)"
POOL_ID=$(aws_local cognito-idp list-user-pools --max-results 60 \
  --query "UserPools[?Name=='$POOL_NAME'].Id | [0]" --output text)

if [ "$POOL_ID" = "None" ] || [ -z "$POOL_ID" ]; then
  echo "==> Criando user pool"
  POOL_ID=$(aws_local cognito-idp create-user-pool --pool-name "$POOL_NAME" \
    --query "UserPool.Id" --output text)
else
  echo "==> User pool já existe: $POOL_ID"
fi

CLIENT_ID=$(aws_local cognito-idp list-user-pool-clients --user-pool-id "$POOL_ID" --max-results 60 \
  --query "UserPoolClients[?ClientName=='notebooklm-local-client'].ClientId | [0]" --output text)

CLIENT_AUTH_FLOWS="ADMIN_NO_SRP_AUTH ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH"

if [ "$CLIENT_ID" = "None" ] || [ -z "$CLIENT_ID" ]; then
  echo "==> Criando app client"
  CLIENT_ID=$(aws_local cognito-idp create-user-pool-client --user-pool-id "$POOL_ID" \
    --client-name "notebooklm-local-client" --explicit-auth-flows $CLIENT_AUTH_FLOWS \
    --query "UserPoolClient.ClientId" --output text)
else
  echo "==> App client já existe: $CLIENT_ID (garantindo auth flows)"
  aws_local cognito-idp update-user-pool-client --user-pool-id "$POOL_ID" --client-id "$CLIENT_ID" \
    --explicit-auth-flows $CLIENT_AUTH_FLOWS >/dev/null
fi

if aws_local cognito-idp admin-get-user --user-pool-id "$POOL_ID" --username "$ADMIN_USER" >/dev/null 2>&1; then
  echo "==> Usuário $ADMIN_USER já existe"
else
  echo "==> Criando usuário $ADMIN_USER"
  aws_local cognito-idp admin-create-user --user-pool-id "$POOL_ID" \
    --username "$ADMIN_USER" --message-action SUPPRESS >/dev/null
fi

aws_local cognito-idp admin-set-user-password --user-pool-id "$POOL_ID" \
  --username "$ADMIN_USER" --password "$ADMIN_PASSWORD" --permanent

echo "==> Verificando bucket S3 ($BUCKET_NAME)"
if aws_local s3api head-bucket --bucket "$BUCKET_NAME" >/dev/null 2>&1; then
  echo "==> Bucket já existe: $BUCKET_NAME"
else
  echo "==> Criando bucket $BUCKET_NAME"
  aws_local s3 mb "s3://$BUCKET_NAME" >/dev/null
fi

echo "==> Verificando fila SQS ($QUEUE_NAME) e DLQ ($QUEUE_NAME-dlq)"
DLQ_URL=$(aws_local sqs create-queue --queue-name "$QUEUE_NAME-dlq" --query "QueueUrl" --output text)
DLQ_ARN=$(aws_local sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn \
  --query "Attributes.QueueArn" --output text)
QUEUE_URL=$(aws_local sqs create-queue --queue-name "$QUEUE_NAME" --query "QueueUrl" --output text)
# set-queue-attributes (e não create-queue --attributes): idempotente em fila que já existe com outros atributos
REDRIVE_POLICY="{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":\"$QUEUE_MAX_RECEIVE_COUNT\"}"
QUEUE_ATTRIBUTES=$(python3 -c 'import json,sys; print(json.dumps({"VisibilityTimeout": sys.argv[1], "RedrivePolicy": sys.argv[2]}))' \
  "$QUEUE_VISIBILITY_TIMEOUT" "$REDRIVE_POLICY")
aws_local sqs set-queue-attributes --queue-url "$QUEUE_URL" --attributes "$QUEUE_ATTRIBUTES"

echo "==> Atualizando $BACKEND_ENV_FILE"
sed -i \
  -e "s|^COGNITO_USER_POOL_ID=.*|COGNITO_USER_POOL_ID=$POOL_ID|" \
  -e "s|^COGNITO_CLIENT_ID=.*|COGNITO_CLIENT_ID=$CLIENT_ID|" \
  -e "s|^S3_BUCKET_NAME=.*|S3_BUCKET_NAME=$BUCKET_NAME|" \
  -e "s|^SQS_INGESTION_QUEUE_URL=.*|SQS_INGESTION_QUEUE_URL=$QUEUE_URL|" \
  "$BACKEND_ENV_FILE"

echo "==> Atualizando $FRONTEND_ENV_FILE"
sed -i \
  -e "s|^VITE_COGNITO_USER_POOL_ID=.*|VITE_COGNITO_USER_POOL_ID=$POOL_ID|" \
  -e "s|^VITE_COGNITO_CLIENT_ID=.*|VITE_COGNITO_CLIENT_ID=$CLIENT_ID|" \
  -e "s|^VITE_COGNITO_AUTHORITY=.*|VITE_COGNITO_AUTHORITY=$ENDPOINT_URL/$POOL_ID|" \
  "$FRONTEND_ENV_FILE"

echo "==> Pronto. Pool: $POOL_ID | Client: $CLIENT_ID | Bucket: $BUCKET_NAME | Fila: $QUEUE_URL | login: $ADMIN_USER / $ADMIN_PASSWORD"
echo "==> .env atualizados: $BACKEND_ENV_FILE e $FRONTEND_ENV_FILE"
