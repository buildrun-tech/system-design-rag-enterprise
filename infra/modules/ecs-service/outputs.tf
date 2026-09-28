output "service_name" {
  value = aws_ecs_service.this.name
}

output "task_family" {
  value = aws_ecs_task_definition.this.family
}

output "exec_role_arn" {
  value = aws_iam_role.exec.arn
}

output "task_role_arn" {
  value = aws_iam_role.task.arn
}

output "task_sg_id" {
  value = aws_security_group.task.id
}
