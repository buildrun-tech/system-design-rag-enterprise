# Development Environment Configuration
environment = "dev"
project     = "rag"
vpc_cidr    = "10.0.0.0/16"

rds_instance_class    = "db.t4g.micro"
rds_allocated_storage = 20
rds_engine_version    = "18"
rds_multi_az          = false

task_cpu       = "512"
task_memory    = "1024"
desired_count  = 1 # sem imagem publicada no primeiro apply
container_port = 8080

deletion_protection         = false
skip_final_snapshot         = true
force_destroy               = true
secret_recovery_window_days = 0

cognito_allow_signup = true

container_insights_enabled = false
integration_timeout_ms     = 30000
