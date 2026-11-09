output "load_balancer_dns" {
  description = "Point both domains at this (CNAME, or Route 53 alias)."
  value       = aws_lb.main.dns_name
}

output "certificate_validation_records" {
  description = "Add these DNS records, wait for the certificate to be issued, then set enable_https = true."
  value = [
    for o in aws_acm_certificate.main.domain_validation_options : {
      name  = o.resource_record_name
      type  = o.resource_record_type
      value = o.resource_record_value
    }
  ]
}

output "app_instance_id" {
  description = "Connect with: aws ssm start-session --target <id>"
  value       = aws_instance.app.id
}

output "db_endpoint" {
  value = aws_db_instance.central.address
}

output "db_password_secret_arn" {
  description = "Secrets Manager secret holding the database password."
  value       = aws_db_instance.central.master_user_secret[0].secret_arn
}

output "media_bucket" {
  value = aws_s3_bucket.media.bucket
}

output "central_s3_iam_user" {
  description = "Create its access key by hand: aws iam create-access-key --user-name <name>"
  value       = aws_iam_user.central_s3.name
}
