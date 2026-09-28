resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.name_prefix}-app"
  retention_in_days = var.log_retention_days
}

resource "aws_security_group" "task" {
  name        = "${var.name_prefix}-task-sg"
  description = "SG das tasks ECS, sem regras inline; regras criadas por este módulo (consumidor de alb-sg e db-sg)"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-task-sg"
  }
}

resource "aws_lb_target_group" "this" {
  name        = "${var.name_prefix}-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    path                = var.health_check_path
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener_rule" "this" {
  listener_arn = var.listener_arn
  priority     = var.listener_rule_priority

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this.arn
  }

  condition {
    path_pattern {
      values = ["/*"]
    }
  }
}

# --- Cadeia de security groups (regras criadas pelo consumidor) ---

resource "aws_vpc_security_group_ingress_rule" "task_from_alb" {
  security_group_id            = aws_security_group.task.id
  referenced_security_group_id = var.alb_sg_id
  from_port                    = var.container_port
  to_port                      = var.container_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_task" {
  security_group_id            = var.alb_sg_id
  referenced_security_group_id = aws_security_group.task.id
  from_port                    = var.container_port
  to_port                      = var.container_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "task_to_db" {
  security_group_id            = aws_security_group.task.id
  referenced_security_group_id = var.db_sg_id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "db_from_task" {
  security_group_id            = var.db_sg_id
  referenced_security_group_id = aws_security_group.task.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "task_https_egress" {
  security_group_id = aws_security_group.task.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

# --- IAM ---

data "aws_iam_policy_document" "ecs_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [var.account_id]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = ["arn:aws:ecs:${var.aws_region}:${var.account_id}:*"]
    }
  }
}

resource "aws_iam_role" "exec" {
  name               = "${var.name_prefix}-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_trust.json
}

resource "aws_iam_role_policy_attachment" "exec_managed" {
  role       = aws_iam_role.exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "exec_secret" {
  statement {
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [var.secret_arn]
  }
}

resource "aws_iam_role_policy" "exec_secret" {
  name   = "${var.name_prefix}-exec-secret"
  role   = aws_iam_role.exec.id
  policy = data.aws_iam_policy_document.exec_secret.json
}

resource "aws_iam_role" "task" {
  name               = "${var.name_prefix}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_trust.json
}

resource "aws_iam_role_policy" "task" {
  name   = "${var.name_prefix}-task"
  role   = aws_iam_role.task.id
  policy = var.task_policy_json
}

# --- Task definition bootstrap ---

resource "aws_ecs_task_definition" "this" {
  family                   = "${var.name_prefix}-app"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.exec.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "app"
      image     = var.app_image
      essential = true

      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]

      environment = [
        for k, v in var.environment_variables : { name = k, value = v }
      ]

      secrets = [
        { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${var.secret_arn}:username::" },
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${var.secret_arn}:password::" },
        { name = "SPRING_AI_OPENAI_API_KEY", valueFrom = "${var.secret_arn}:openrouter_api_key::" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.this.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "app"
        }
      }
    }
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }
}

resource "aws_ecs_service" "this" {
  name            = "${var.name_prefix}-app"
  cluster         = var.cluster_arn
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = [aws_security_group.task.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.this.arn
    container_name   = "app"
    container_port   = var.container_port
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }

  depends_on = [aws_lb_listener_rule.this]
}
