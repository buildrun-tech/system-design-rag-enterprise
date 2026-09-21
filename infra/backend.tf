# Backend S3 parcial: bucket, region e key entram no `init` via -backend-config
# (backend block não aceita variables). No CI vêm do env do workflow infra.yml.
# Local:
#   terraform init \
#     -backend-config="bucket=<TF_STATE_BUCKET>" \
#     -backend-config="region=<TF_STATE_REGION>" \
#     -backend-config="key=rag-enterprise/<env>/terraform.tfstate"
terraform {
  backend "s3" {
    use_lockfile = true # lock nativo em S3 (Terraform >= 1.10), sem DynamoDB
  }
}
