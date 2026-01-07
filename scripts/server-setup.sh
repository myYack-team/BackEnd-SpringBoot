#!/bin/bash

# MyYak 서버 초기 설정 스크립트
# AWS EC2 Ubuntu 22.04 기준 (RDS 사용으로 MySQL 로컬 설치 제거)
#
# 사용법:
# 1. 서버에 SSH 접속
# 2. chmod +x server-setup.sh
# 3. ./server-setup.sh

set -e  # 에러 발생 시 중단

echo "========================================="
echo "MyYak Server Setup Script (AWS)"
echo "========================================="

# 1. 시스템 업데이트
echo "[1/6] Updating system packages..."
sudo apt update && sudo apt upgrade -y

# 2. JDK 21 설치
echo "[2/6] Installing JDK 21..."
sudo apt install -y openjdk-21-jdk
java -version

# 3. Nginx 설치
echo "[3/6] Installing Nginx..."
sudo apt install -y nginx
sudo systemctl start nginx
sudo systemctl enable nginx

# 4. Certbot (Let's Encrypt) 설치
echo "[4/6] Installing Certbot..."
sudo apt install -y certbot python3-certbot-nginx

# 5. 방화벽 설정 (Ubuntu UFW)
echo "[5/6] Configuring firewall..."
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw --force enable
sudo ufw status

# 6. 애플리케이션 디렉토리 생성
echo "[6/6] Creating application directory..."
mkdir -p /home/ubuntu/logs

echo "
========================================
AWS 서버 기본 설정 완료!

다음 단계:
1. Nginx 설정 파일 배포:
   sudo cp nginx-myyak.conf /etc/nginx/sites-available/myyak
   sudo ln -s /etc/nginx/sites-available/myyak /etc/nginx/sites-enabled/
   sudo rm /etc/nginx/sites-enabled/default  # 기본 설정 제거
   sudo nginx -t
   sudo systemctl reload nginx

2. SSL 인증서 발급:
   sudo certbot --nginx -d api.myyak.xyz

3. systemd 서비스 등록:
   sudo cp myyak.service /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable myyak

4. RDS 연결 테스트:
   mysql -h [RDS_ENDPOINT] -u myyak_admin -p

5. GitHub Secrets 설정 후 배포 (main 브랜치 push)

========================================"
