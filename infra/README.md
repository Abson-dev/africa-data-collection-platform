# Infrastructure

Terraform for one environment (staging or prod) in **AWS af-south-1**. It creates:

- **Network:** a VPC with 2 public subnets (load balancer, app host) and 2 private subnets (database). No NAT gateway.
- **Load balancer** with an ACM certificate. `central.<domain>` goes to Central (port 80); `bridge.<domain>` goes to the Bridge (port 8080).
- **App host:** one EC2 instance, Ubuntu 24.04, running Docker with stock ODK Central and the Bridge. There is no SSH; admins connect through SSM Session Manager.
- **Database:** RDS PostgreSQL in the private subnets. The password is kept in Secrets Manager.
- **Media storage:** an S3 bucket for Central's files, with an IAM user limited to that bucket.
- **Encryption:** one KMS key, with rotation, for the database, the bucket and the disks.

Rough cost for staging: about US$150–200/month (EC2 t3.large, RDS db.t3.medium, load balancer). Check the AWS pricing calculator for af-south-1.

## Steps

### 0. Before you start
- Enable the **af-south-1** region in the AWS account (it is opt-in).
- Install Terraform ≥ 1.6 and AWS CLI v2, with credentials for the account.

### 1. Plan and apply
```bash
cd infra/terraform
cp terraform.tfvars.example staging.tfvars      # edit the domains
terraform init
terraform apply -var-file=staging.tfvars
```

### 2. DNS
Point both domains at the `load_balancer_dns` output, as CNAMEs. Also add the `certificate_validation_records` from the output.

### 3. Turn on HTTPS
Once ACM shows the certificate as **Issued**, set `enable_https = true` in `staging.tfvars` and apply again.

### 4. Configure and start ODK Central on the app host
```bash
aws ssm start-session --target <app_instance_id>
sudo -i
cd /opt/central && git checkout <latest Central release tag> && git submodule update --init --recursive
cp .env.template .env      # then edit, see below
```

**Central `.env` settings:**
- Basic: `DOMAIN=<central domain>` and `SYSADMIN_EMAIL=...`.
- TLS: the load balancer terminates it, so use Central's setting for running behind an upstream proxy (`SSL_TYPE=upstream`).
- Database: the external database settings (`DB_HOST=<db_endpoint>`, `DB_USER=odk`, `DB_NAME=odk`, `DB_PASSWORD=` from the secret).
- Storage: the S3 settings (`S3_SERVER=https://s3.af-south-1.amazonaws.com`, `S3_BUCKET_NAME=<media_bucket>`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`).

> Variable names change between Central releases. **Check them against the Central install docs for the release you deploy** (docs.getodk.org, "Installing Central", "Using a custom database" and "Using S3-compatible storage").

```bash
# database password
aws secretsmanager get-secret-value --secret-id <db_password_secret_arn> --query SecretString --output text | jq -r .password
# S3 key for Central (do this once; store the secret only in Central's .env)
aws iam create-access-key --user-name <central_s3_iam_user>

docker compose build && docker compose up -d
docker compose exec service odk-cmd --email you@example.org user-create
docker compose exec service odk-cmd --email you@example.org user-promote
```

### 5. Start the Bridge
```bash
git clone https://github.com/Abson-dev/africa-data-collection-platform /opt/adc
cd /opt/adc && docker build -f services/bridge/Dockerfile -t adc-bridge .
cp services/bridge/.env.example /opt/adc-bridge.env    # fill in
docker run -d --name bridge --restart unless-stopped -p 8080:8080 --env-file /opt/adc-bridge.env adc-bridge
```

Then, in Central:
1. Upload `forms/templates/sms_partial.xlsx` to the project.
2. Create a web user for the Bridge with the Project Manager role.

In Africa's Talking, set the inbound SMS callback to `https://<bridge domain>/sms/inbound?token=<SMS_WEBHOOK_TOKEN>`.

## On-premise (ministries)
The same pieces run without AWS: Central's Docker stack with its built-in PostgreSQL and file storage (or a local S3-compatible store such as MinIO), plus the Bridge container. Write a separate runbook when the first ministry deployment is planned.

## Not done yet
- Pin the Central release and automate steps 4–5 (Phase 1, month 1).
- CloudWatch alarms (host down, disk, database storage), and a restore drill (month 4).
- Bridge image built by CI and pulled from GitHub Container Registry instead of built on the host.
