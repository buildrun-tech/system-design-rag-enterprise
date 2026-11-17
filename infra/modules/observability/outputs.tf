output "dashboard_name" {
  value = aws_cloudwatch_dashboard.this.dashboard_name
}

output "alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}
