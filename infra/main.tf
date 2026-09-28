locals {
  name_prefix         = "${var.project}-${var.environment}"
  account_id          = data.aws_caller_identity.current.account_id
  cloudfront_origin   = "https://${module.cloudfront_spa.domain_name}"
  cloudfront_redirect = "https://${module.cloudfront_spa.domain_name}/"
  openrouter_base_url = "https://openrouter.ai/api/v1"
}

resource "random_password" "rds_master" {
  length  = 24
  special = false # evita caracteres que quebram URL JDBC
}

module "network" {
  source = "./modules/network"

  name_prefix = local.name_prefix
  vpc_cidr    = var.vpc_cidr
}

module "sources_bucket" {
  source = "./modules/s3-bucket"

  bucket_name   = "${local.name_prefix}-sources-${local.account_id}"
  force_destroy = var.force_destroy
}

module "frontend_bucket" {
  source = "./modules/s3-bucket"

  bucket_name   = "${local.name_prefix}-frontend-${local.account_id}"
  force_destroy = var.force_destroy
}

module "ingest_queue" {
  source = "./modules/sqs-queue"

  name = "${local.name_prefix}-ingest-queue"
}

module "cloudfront_spa" {
  source = "./modules/cloudfront-spa"

  name_prefix                 = local.name_prefix
  bucket_name                 = module.frontend_bucket.bucket_name
  bucket_arn                  = module.frontend_bucket.bucket_arn
  bucket_regional_domain_name = module.frontend_bucket.bucket_regional_domain_name
}

module "cognito_user_pool" {
  source = "./modules/cognito-user-pool"

  name_prefix                  = local.name_prefix
  callback_urls                = [local.cloudfront_redirect]
  logout_urls                  = [local.cloudfront_redirect]
  allow_admin_create_user_only = !var.cognito_allow_signup
  google_client_id             = var.google_client_id
  google_client_secret         = var.google_client_secret
}

module "rds_postgres" {
  source = "./modules/rds-postgres"

  name_prefix       = local.name_prefix
  vpc_id            = module.network.vpc_id
  db_subnet_ids     = module.network.db_subnet_ids
  engine_version    = var.rds_engine_version
  instance_class    = var.rds_instance_class
  allocated_storage = var.rds_allocated_storage
  master_username   = var.rds_master_username
  master_password   = random_password.rds_master.result
  multi_az          = var.rds_multi_az

  deletion_protection = var.deletion_protection
  skip_final_snapshot = var.skip_final_snapshot
}

module "app_secret" {
  source = "./modules/secret"

  name = "${var.project}/${var.environment}/app"

  secret_json = {
    username           = var.rds_master_username
    password           = random_password.rds_master.result
    host               = module.rds_postgres.endpoint
    port               = tostring(module.rds_postgres.port)
    dbname             = module.rds_postgres.db_name
    openrouter_api_key = var.openrouter_api_key
  }

  recovery_window_in_days = var.secret_recovery_window_days
}

module "alb" {
  source = "./modules/alb"

  name_prefix = local.name_prefix
  vpc_id      = module.network.vpc_id
  subnet_ids  = module.network.public_subnet_ids
}

module "ecs_cluster" {
  source = "./modules/ecs-cluster"

  name_prefix                = local.name_prefix
  container_insights_enabled = var.container_insights_enabled
}

data "aws_iam_policy_document" "task_policy" {
  statement {
    sid    = "SourcesBucketObjects"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    resources = ["${module.sources_bucket.bucket_arn}/*"]
  }

  statement {
    sid       = "SourcesBucketList"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [module.sources_bucket.bucket_arn]
  }

  statement {
    sid    = "IngestQueue"
    effect = "Allow"
    actions = [
      "sqs:SendMessage",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueAttributes",
    ]
    resources = [module.ingest_queue.queue_arn]
  }
}

module "ecs_service" {
  source = "./modules/ecs-service"

  name_prefix = local.name_prefix
  account_id  = local.account_id
  aws_region  = var.aws_region

  vpc_id      = module.network.vpc_id
  subnet_ids  = module.network.public_subnet_ids
  cluster_arn = module.ecs_cluster.cluster_arn

  listener_arn = module.alb.listener_arn
  alb_sg_id    = module.alb.alb_sg_id
  db_sg_id     = module.rds_postgres.sg_id

  container_port = var.container_port
  task_cpu       = var.task_cpu
  task_memory    = var.task_memory
  desired_count  = var.desired_count
  app_image      = var.app_image

  secret_arn       = module.app_secret.secret_arn
  task_policy_json = data.aws_iam_policy_document.task_policy.json

  environment_variables = {
    SPRING_DATASOURCE_URL     = "jdbc:postgresql://${module.rds_postgres.endpoint}:${module.rds_postgres.port}/${module.rds_postgres.db_name}"
    AWS_REGION                = var.aws_region
    S3_BUCKET_NAME            = module.sources_bucket.bucket_name
    SQS_INGESTION_QUEUE_URL   = module.ingest_queue.queue_url
    COGNITO_USER_POOL_ID      = module.cognito_user_pool.user_pool_id
    COGNITO_CLIENT_ID         = module.cognito_user_pool.client_id
    APP_CORS_ALLOWED_ORIGINS  = local.cloudfront_origin
    SPRING_AI_OPENAI_BASE_URL = local.openrouter_base_url
  }
}

module "api_gateway_http" {
  source = "./modules/api-gateway-http"

  name_prefix = local.name_prefix
  vpc_id      = module.network.vpc_id
  subnet_ids  = module.network.public_subnet_ids
  alb_sg_id   = module.alb.alb_sg_id

  listener_arn           = module.alb.listener_arn
  cors_allowed_origins   = [local.cloudfront_origin]
  integration_timeout_ms = var.integration_timeout_ms
}
