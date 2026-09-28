output "ecs_cluster_name" {
  value = module.ecs_cluster.cluster_name
}

output "ecs_service_name" {
  value = module.ecs_service.service_name
}

output "ecs_task_family" {
  value = module.ecs_service.task_family
}

output "ecs_exec_role_arn" {
  value = module.ecs_service.exec_role_arn
}

output "ecs_task_role_arn" {
  value = module.ecs_service.task_role_arn
}

output "frontend_bucket_name" {
  value = module.frontend_bucket.bucket_name
}

output "cloudfront_distribution_id" {
  value = module.cloudfront_spa.distribution_id
}

output "cloudfront_domain" {
  value = module.cloudfront_spa.domain_name
}

output "api_url" {
  value = module.api_gateway_http.api_url
}

output "cognito_authority" {
  value = module.cognito_user_pool.issuer_url
}

output "cognito_client_id" {
  value = module.cognito_user_pool.client_id
}

output "cognito_user_pool_id" {
  value = module.cognito_user_pool.user_pool_id
}
