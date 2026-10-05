output "distribution_id" {
  value = aws_cloudfront_distribution.this.id
}

output "distribution_arn" {
  value = aws_cloudfront_distribution.this.arn
}

# aplicado via module s3-bucket (policy_documents): bucket só tem uma policy,
# aws_s3_bucket_policy aqui sobrescreveria o DenyInsecureTransport (e vice-versa)
output "bucket_policy_json" {
  value = data.aws_iam_policy_document.frontend_oac.json
}

output "domain_name" {
  value = aws_cloudfront_distribution.this.domain_name
}
