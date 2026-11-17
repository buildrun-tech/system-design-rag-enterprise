output "alb_arn" {
  value = aws_lb.this.arn
}

output "alb_sg_id" {
  value = aws_security_group.alb.id
}

output "listener_arn" {
  value = aws_lb_listener.http.arn
}

output "alb_arn_suffix" {
  value = aws_lb.this.arn_suffix
}
