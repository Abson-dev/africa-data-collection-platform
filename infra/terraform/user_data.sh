#!/bin/bash
# First boot of the app host: install Docker and fetch ODK Central.
# Central itself is configured and started by hand (infra/README.md, step 4).
set -euo pipefail

apt-get update
apt-get install -y ca-certificates curl git jq unzip

# Docker Engine + Compose plugin from Docker's repository
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# AWS CLI v2 (to read the database password from Secrets Manager)
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp && /tmp/aws/install && rm -rf /tmp/aws /tmp/awscliv2.zip

# ODK Central (stock). Pin to a release tag when deploying: git -C /opt/central checkout <tag>
git clone https://github.com/getodk/central /opt/central
git -C /opt/central submodule update --init --recursive
