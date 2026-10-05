# Production Environment Configuration
environment = "prod"
project     = "rag"
vpc_cidr    = "10.1.0.0/16"

rds_instance_class    = "db.t4g.small"
rds_allocated_storage = 50
rds_engine_version    = "18"
rds_multi_az          = false

task_cpu       = "1024"
task_memory    = "2048"
desired_count  = 0
container_port = 8080

deletion_protection         = true
skip_final_snapshot         = false
force_destroy               = false
secret_recovery_window_days = 7

cognito_allow_signup = true

container_insights_enabled = true
integration_timeout_ms     = 30000
