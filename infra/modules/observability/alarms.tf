locals {
  metric_namespace = "${var.name_prefix}/App"
  integrations     = ["openrouter", "s3", "sqs", "pgvector"]
  alarm_defaults = {
    alarm_actions = [aws_sns_topic.alerts.arn]
    ok_actions    = [aws_sns_topic.alerts.arn]
  }
}

resource "aws_sns_topic" "alerts" {
  name = "${var.name_prefix}-alerts"
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.alert_email == "" ? 0 : 1
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# ---- metric filters sobre o log JSON ----

resource "aws_cloudwatch_log_metric_filter" "errors" {
  name           = "${var.name_prefix}-error-count"
  log_group_name = var.log_group_name
  pattern        = "{ $.level = \"ERROR\" }"

  metric_transformation {
    name          = "ErrorCount"
    namespace     = local.metric_namespace
    value         = "1"
    default_value = "0"
  }
}

# so eventos "External call" (tem $.integration); erro de SSE nao entra
resource "aws_cloudwatch_log_metric_filter" "integration_errors" {
  name           = "${var.name_prefix}-integration-errors"
  log_group_name = var.log_group_name
  pattern        = "{ $.outcome = \"error\" && $.integration = * }"

  metric_transformation {
    name      = "IntegrationErrors"
    namespace = local.metric_namespace
    value     = "1"
    dimensions = {
      integration = "$.integration"
    }
  }
}

# ---- alarmes ----

resource "aws_cloudwatch_metric_alarm" "dlq" {
  alarm_name          = "${var.name_prefix}-ingest-dlq-not-empty"
  alarm_description   = "Mensagem na DLQ da ingestão"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = var.dlq_name }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_defaults.alarm_actions
  ok_actions          = local.alarm_defaults.ok_actions
}

resource "aws_cloudwatch_metric_alarm" "queue_age" {
  alarm_name          = "${var.name_prefix}-ingest-queue-age"
  alarm_description   = "Mensagem mais antiga da fila de ingestão acima de 300 s"
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateAgeOfOldestMessage"
  dimensions          = { QueueName = var.queue_name }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 300
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_defaults.alarm_actions
  ok_actions          = local.alarm_defaults.ok_actions
}

resource "aws_cloudwatch_metric_alarm" "alb_5xx_rate" {
  alarm_name          = "${var.name_prefix}-alb-5xx-rate"
  alarm_description   = "5xx do target acima de 1% em 5 min"
  comparison_operator = "GreaterThanThreshold"
  threshold           = 1
  evaluation_periods  = 1
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_defaults.alarm_actions
  ok_actions          = local.alarm_defaults.ok_actions

  metric_query {
    id          = "rate"
    expression  = "IF(req > 0, 100 * errors / req, 0)"
    label       = "5xx %"
    return_data = true
  }

  metric_query {
    id = "errors"
    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "HTTPCode_Target_5XX_Count"
      dimensions  = { LoadBalancer = var.alb_arn_suffix, TargetGroup = var.target_group_arn_suffix }
      stat        = "Sum"
      period      = 300
    }
  }

  metric_query {
    id = "req"
    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "RequestCount"
      dimensions  = { LoadBalancer = var.alb_arn_suffix, TargetGroup = var.target_group_arn_suffix }
      stat        = "Sum"
      period      = 300
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "healthy_hosts" {
  alarm_name          = "${var.name_prefix}-healthy-hosts"
  alarm_description   = "Tasks saudáveis abaixo de desired_count"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HealthyHostCount"
  dimensions          = { LoadBalancer = var.alb_arn_suffix, TargetGroup = var.target_group_arn_suffix }
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 2
  threshold           = var.desired_count
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = local.alarm_defaults.alarm_actions
  ok_actions          = local.alarm_defaults.ok_actions
}

resource "aws_cloudwatch_metric_alarm" "app_errors" {
  alarm_name          = "${var.name_prefix}-app-error-logs"
  alarm_description   = "Log level=ERROR no backend"
  namespace           = local.metric_namespace
  metric_name         = "ErrorCount"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_defaults.alarm_actions
  ok_actions          = local.alarm_defaults.ok_actions

  depends_on = [aws_cloudwatch_log_metric_filter.errors]
}

resource "aws_cloudwatch_metric_alarm" "integration_errors" {
  for_each = toset(local.integrations)

  alarm_name          = "${var.name_prefix}-integration-errors-${each.key}"
  alarm_description   = "Chamadas com falha para ${each.key}"
  namespace           = local.metric_namespace
  metric_name         = "IntegrationErrors"
  dimensions          = { integration = each.key }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_defaults.alarm_actions
  ok_actions          = local.alarm_defaults.ok_actions

  depends_on = [aws_cloudwatch_log_metric_filter.integration_errors]
}
