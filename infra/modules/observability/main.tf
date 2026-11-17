locals {
  lb_dims = { LoadBalancer = var.alb_arn_suffix, TargetGroup = var.target_group_arn_suffix }

  # Logs Insights: so a janela do widget; eventos de request filtram OPTIONS (preflight) pra nao inflar numeros
  q = {
    traffic      = <<-EOT
      SOURCE '${var.log_group_name}'
      | filter message = "Request end" and method != "OPTIONS"
      | stats count(*) as requests by route
      | sort requests desc
    EOT
    latency      = <<-EOT
      SOURCE '${var.log_group_name}'
      | filter message = "Request end" and method != "OPTIONS"
      | stats count(*) as requests, avg(durationMs) as avgMs, pct(durationMs, 95) as p95Ms, pct(durationMs, 99) as p99Ms by route
      | sort p95Ms desc
    EOT
    errors       = <<-EOT
      SOURCE '${var.log_group_name}'
      | filter message = "Request end" and status >= 400
      | stats count(*) as n by status, route
      | sort n desc
    EOT
    integrations = <<-EOT
      SOURCE '${var.log_group_name}'
      | filter message = "External call"
      | stats count(*) as calls, sum(outcome = "error") as errors, avg(durationMs) as avgMs, pct(durationMs, 95) as p95Ms by integration, operation
      | sort p95Ms desc
    EOT
    cognito_401  = <<-EOT
      SOURCE '${var.log_group_name}'
      | filter message = "Request end" and status = 401
      | stats count(*) as unauthorized by bin(5m)
    EOT
    ingestion    = <<-EOT
      SOURCE '${var.log_group_name}'
      | filter message in ["Consumer out", "Source ingestion failed"]
      | stats count(*) as n, avg(durationMs) as avgMs, max(durationMs) as maxMs by message
    EOT
    enrichment   = <<-EOT
      SOURCE '${var.log_group_name}'
      | filter message = "Source enrichment"
      | stats count(*) as sources, avg(chunks) as avgChunks, avg(durationMs) as avgMs, max(durationMs) as maxMs
    EOT
    sse          = <<-EOT
      SOURCE '${var.log_group_name}'
      | filter message in ["SSE stream out", "SSE stream failed"]
      | stats count(*) as streams, avg(ttfbMs) as avgTtfbMs, pct(durationMs, 95) as p95DurationMs by outcome
    EOT
    quiz         = <<-EOT
      SOURCE '${var.log_group_name}'
      | filter message in ["Quiz generation out", "Quiz generation failed"]
      | stats count(*) as n, avg(durationMs) as avgMs by message
    EOT
  }

  # w=largura (grid de 24), h=altura
  widgets = [
    # ---- linha 1: visao geral ----
    { type = "text", x = 0, y = 0, width = 24, height = 1,
    properties = { markdown = "## Visão geral" } },
    { type = "metric", x = 0, y = 1, width = 5, height = 4, properties = {
      title   = "Requests / min", view = "singleValue", region = var.aws_region, period = 60, stat = "Sum",
      metrics = [["AWS/ApplicationELB", "RequestCount", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix]]
    } },
    { type = "metric", x = 5, y = 1, width = 5, height = 4, properties = {
      title = "5xx % (5 min)", view = "singleValue", region = var.aws_region, period = 300,
      metrics = [
        [{ expression = "IF(req > 0, 100 * errors / req, 0)", label = "5xx %", id = "rate" }],
        ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix, { id = "errors", stat = "Sum", visible = false }],
        ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix, { id = "req", stat = "Sum", visible = false }],
      ]
    } },
    { type = "metric", x = 10, y = 1, width = 5, height = 4, properties = {
      title   = "Latência p95 (ALB)", view = "singleValue", region = var.aws_region, period = 300, stat = "p95",
      metrics = [["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix]]
    } },
    { type = "metric", x = 15, y = 1, width = 5, height = 4, properties = {
      title   = "Tasks saudáveis", view = "singleValue", region = var.aws_region, period = 60, stat = "Minimum",
      metrics = [["AWS/ApplicationELB", "HealthyHostCount", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix]]
    } },
    { type = "metric", x = 20, y = 1, width = 4, height = 4, properties = {
      title   = "DLQ (mensagens)", view = "singleValue", region = var.aws_region, period = 60, stat = "Maximum",
      metrics = [["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", var.dlq_name]]
    } },

    # ---- linha 2: golden signals ----
    { type = "text", x = 0, y = 5, width = 24, height = 1,
    properties = { markdown = "## Golden signals: tráfego, latência, erros, saturação" } },
    { type = "log", x = 0, y = 6, width = 12, height = 6, properties = {
    title = "Tráfego por rota", region = var.aws_region, view = "table", query = local.q.traffic } },
    { type = "log", x = 12, y = 6, width = 12, height = 6, properties = {
    title = "Latência por rota (app)", region = var.aws_region, view = "table", query = local.q.latency } },
    { type = "metric", x = 0, y = 12, width = 8, height = 6, properties = {
      title = "Tráfego e erros (ALB)", view = "timeSeries", region = var.aws_region, period = 60, stat = "Sum",
      metrics = [
        ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix],
        [".", "HTTPCode_Target_4XX_Count", ".", ".", ".", "."],
        [".", "HTTPCode_Target_5XX_Count", ".", ".", ".", "."],
      ]
    } },
    { type = "metric", x = 8, y = 12, width = 8, height = 6, properties = {
      title = "Latência ALB p50 / p95 / p99", view = "timeSeries", region = var.aws_region, period = 60,
      metrics = [
        ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix, { stat = "p50", label = "p50" }],
        ["...", { stat = "p95", label = "p95" }],
        ["...", { stat = "p99", label = "p99" }],
      ]
    } },
    { type = "log", x = 16, y = 12, width = 8, height = 6, properties = {
    title = "Erros 4xx/5xx por rota", region = var.aws_region, view = "table", query = local.q.errors } },
    { type = "metric", x = 0, y = 18, width = 8, height = 6, properties = {
      title = "ECS CPU / memória (%)", view = "timeSeries", region = var.aws_region, period = 60, stat = "Average",
      metrics = [
        ["AWS/ECS", "CPUUtilization", "ClusterName", var.cluster_name, "ServiceName", var.service_name],
        [".", "MemoryUtilization", ".", ".", ".", "."],
      ]
    } },
    { type = "metric", x = 8, y = 18, width = 8, height = 6, properties = {
      title = "RDS conexões / CPU", view = "timeSeries", region = var.aws_region, period = 60, stat = "Average",
      metrics = [
        ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", var.rds_identifier],
        [".", "CPUUtilization", ".", ".", { yAxis = "right" }],
      ]
    } },
    { type = "metric", x = 16, y = 18, width = 8, height = 6, properties = {
      title = "API Gateway (count / 4xx / 5xx)", view = "timeSeries", region = var.aws_region, period = 60, stat = "Sum",
      metrics = [
        ["AWS/ApiGateway", "Count", "ApiId", var.api_id],
        [".", "4xx", ".", "."],
        [".", "5xx", ".", "."],
      ]
    } },

    # ---- linha 3: integracoes ----
    { type = "text", x = 0, y = 24, width = 24, height = 1,
    properties = { markdown = "## Integrações: OpenRouter, SQS, S3, pgvector, Cognito" } },
    { type = "log", x = 0, y = 25, width = 12, height = 6, properties = {
    title = "Chamadas externas (latência e erros por integração)", region = var.aws_region, view = "table", query = local.q.integrations } },
    { type = "metric", x = 12, y = 25, width = 12, height = 6, properties = {
      title   = "Erros por integração (metric filter)", view = "timeSeries", region = var.aws_region, period = 300, stat = "Sum",
      metrics = [for i in local.integrations : [local.metric_namespace, "IntegrationErrors", "integration", i]]
    } },
    { type = "metric", x = 0, y = 31, width = 8, height = 6, properties = {
      title = "SQS ingestão: visíveis / idade / DLQ", view = "timeSeries", region = var.aws_region, period = 60,
      metrics = [
        ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", var.queue_name, { stat = "Maximum" }],
        [".", "ApproximateAgeOfOldestMessage", ".", ".", { stat = "Maximum", yAxis = "right" }],
        [".", "ApproximateNumberOfMessagesVisible", "QueueName", var.dlq_name, { stat = "Maximum", label = "DLQ" }],
      ]
    } },
    { type = "metric", x = 8, y = 31, width = 8, height = 6, properties = {
      title = "S3 sources: 4xx / 5xx", view = "timeSeries", region = var.aws_region, period = 300, stat = "Sum",
      metrics = [
        ["AWS/S3", "4xxErrors", "BucketName", var.bucket_name, "FilterId", "EntireBucket"],
        [".", "5xxErrors", ".", ".", ".", "."],
      ]
    } },
    { type = "log", x = 16, y = 31, width = 8, height = 6, properties = {
    title = "401 (Cognito / JWT)", region = var.aws_region, view = "timeSeries", query = local.q.cognito_401 } },

    # ---- linha 4: pipeline ----
    { type = "text", x = 0, y = 37, width = 24, height = 1,
    properties = { markdown = "## Pipeline: ingestão, chat SSE, quiz" } },
    { type = "log", x = 0, y = 38, width = 8, height = 6, properties = {
    title = "Ingestão (consumer)", region = var.aws_region, view = "table", query = local.q.ingestion } },
    { type = "log", x = 8, y = 38, width = 8, height = 6, properties = {
    title = "Enrichment (chunks e duração)", region = var.aws_region, view = "table", query = local.q.enrichment } },
    { type = "log", x = 16, y = 38, width = 8, height = 6, properties = {
    title = "Chat SSE (ttfb e duração)", region = var.aws_region, view = "table", query = local.q.sse } },
    { type = "log", x = 0, y = 44, width = 12, height = 6, properties = {
    title = "Quiz", region = var.aws_region, view = "table", query = local.q.quiz } },
  ]
}

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = "${var.name_prefix}-service-health"
  dashboard_body = jsonencode({ widgets = local.widgets })
}
