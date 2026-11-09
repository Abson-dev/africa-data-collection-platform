variable "region" {
  description = "AWS region. af-south-1 (Cape Town) is opt-in: enable it in the AWS account first."
  type        = string
  default     = "af-south-1"
}

variable "project" {
  description = "Short name used in resource names."
  type        = string
  default     = "adc"
}

variable "environment" {
  description = "staging or prod."
  type        = string

  validation {
    condition     = contains(["staging", "prod"], var.environment)
    error_message = "environment must be staging or prod."
  }
}

variable "central_domain" {
  description = "Host name for ODK Central, e.g. central.example.org."
  type        = string
}

variable "bridge_domain" {
  description = "Host name for the Bridge service (SMS webhook), e.g. bridge.example.org."
  type        = string
}

variable "enable_https" {
  description = "Set to true after the ACM certificate has been validated (see infra/README.md, step 3)."
  type        = bool
  default     = false
}

variable "instance_type" {
  description = "EC2 instance for Central + Bridge containers."
  type        = string
  default     = "t3.large"
}

variable "db_instance_class" {
  type    = string
  default = "db.t3.medium"
}

variable "db_allocated_storage_gb" {
  type    = number
  default = 50
}

variable "db_engine_version" {
  description = "PostgreSQL major version. Check the version supported by the Central release you deploy."
  type        = string
  default     = "14"
}

variable "vpc_cidr" {
  type    = string
  default = "10.40.0.0/16"
}
