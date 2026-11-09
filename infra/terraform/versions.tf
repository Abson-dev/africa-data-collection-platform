terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Recommended once the AWS account exists: keep state in S3 with locking.
  # backend "s3" {
  #   bucket       = "adc-terraform-state"
  #   key          = "phase1/staging.tfstate"
  #   region       = "af-south-1"
  #   use_lockfile = true
  #   encrypt      = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
