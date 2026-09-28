resource "aws_security_group" "vpc_link" {
  name        = "${var.name_prefix}-vpclink-sg"
  description = "VPC Link SG, no ingress; egress to alb-sg created by this module"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-vpclink-sg"
  }
}

resource "aws_vpc_security_group_egress_rule" "vpclink_to_alb" {
  security_group_id            = aws_security_group.vpc_link.id
  referenced_security_group_id = var.alb_sg_id
  from_port                    = 80
  to_port                      = 80
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_vpclink" {
  security_group_id            = var.alb_sg_id
  referenced_security_group_id = aws_security_group.vpc_link.id
  from_port                    = 80
  to_port                      = 80
  ip_protocol                  = "tcp"
}

resource "aws_apigatewayv2_vpc_link" "this" {
  name               = "${var.name_prefix}-vpclink"
  subnet_ids         = var.subnet_ids
  security_group_ids = [aws_security_group.vpc_link.id]
}

resource "aws_apigatewayv2_api" "this" {
  name          = "${var.name_prefix}-http-api"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = var.cors_allowed_origins
    allow_methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
    allow_headers = ["Authorization", "Content-Type"]
  }
}

resource "aws_apigatewayv2_integration" "alb" {
  api_id           = aws_apigatewayv2_api.this.id
  integration_type = "HTTP_PROXY"
  integration_uri  = var.listener_arn
  connection_type  = "VPC_LINK"
  connection_id    = aws_apigatewayv2_vpc_link.this.id

  integration_method   = "ANY"
  timeout_milliseconds = var.integration_timeout_ms
}

resource "aws_apigatewayv2_route" "proxy" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.alb.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true
}
