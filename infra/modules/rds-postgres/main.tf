# Resolve a minor real disponível na região a partir do major/major.minor
# pedido em var.engine_version (ex: "18" -> "18.1"). Evita hardcodar uma
# minor que a AWS descontinuou ("Cannot find version X.Y for postgres").
# Todas as minors de PG >= 16 na AWS têm pgvector >= 0.5.0 disponível.
data "aws_rds_engine_version" "this" {
  engine  = "postgres"
  version = var.engine_version
  latest  = true
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db"
  subnet_ids = var.db_subnet_ids
}

resource "aws_security_group" "db" {
  name        = "${var.name_prefix}-db-sg"
  description = "RDS SG, no inline rules; rules created by the consumer"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-db-sg"
  }
}

resource "aws_db_parameter_group" "this" {
  name   = "${var.name_prefix}-pg"
  family = data.aws_rds_engine_version.this.parameter_group_family
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name_prefix}-db"
  engine         = "postgres"
  engine_version = data.aws_rds_engine_version.this.version_actual
  instance_class = var.instance_class

  allocated_storage = var.allocated_storage
  storage_encrypted = true

  db_name  = var.db_name
  username = var.master_username
  password = var.master_password
  port     = 5432

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.this.name

  publicly_accessible = false
  multi_az            = var.multi_az

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.name_prefix}-db-final"
}
