#!/usr/bin/env bash
set -euo pipefail

mkdir -p /opt/zhiyuan/backend /opt/zhiyuan/frontend /opt/zhiyuan/config /opt/zhiyuan/config/keys /opt/zhiyuan/logs /opt/zhiyuan/sql /opt/zhiyuan/uploads

if [ ! -f /opt/zhiyuan/config/keys/private.pem ] || [ ! -f /opt/zhiyuan/config/keys/public.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /opt/zhiyuan/config/keys/private.pem
  openssl rsa -pubout -in /opt/zhiyuan/config/keys/private.pem -out /opt/zhiyuan/config/keys/public.pem
  chmod 600 /opt/zhiyuan/config/keys/private.pem
  chmod 644 /opt/zhiyuan/config/keys/public.pem
fi

if [ -f /opt/zhiyuan/config/db.env ]; then
  # shellcheck disable=SC1091
  . /opt/zhiyuan/config/db.env
else
  DB_PASS="$(openssl rand -base64 24 | tr -d '=/+' | cut -c1-24)"
  umask 077
  printf 'DB_PASS=%s\n' "$DB_PASS" > /opt/zhiyuan/config/db.env
fi

mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS zhiyuan DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'zhiyuan'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER 'zhiyuan'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON zhiyuan.* TO 'zhiyuan'@'localhost';
FLUSH PRIVILEGES;
SQL

umask 077
cat > /opt/zhiyuan/config/application-prod.yml <<EOF
server:
  port: 8080

spring:
  autoconfigure:
    exclude:
      - org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration
      - org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreAutoConfiguration
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/zhiyuan?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: zhiyuan
    password: ${DB_PASS}
  data:
    redis:
      host: 127.0.0.1
      port: 6379
  kafka:
    bootstrap-servers: 127.0.0.1:9092
    listener:
      auto-startup: false

mybatis:
  mapper-locations: classpath*:mapper/*.xml

management:
  health:
    elasticsearch:
      enabled: false

zhiyuan:
  features:
    search:
      enabled: false
    ai:
      enabled: false
    rag:
      enabled: false

canal:
  enabled: false
  host: 127.0.0.1
  port: 11111
  destination: example
  username: ""
  password: ""
  filter: outbox
  batchSize: 100
  intervalMs: 1000

auth:
  jwt:
    issuer: zhiyuan
    key-id: zhiyuan-key
    private-key: file:/opt/zhiyuan/config/keys/private.pem
    public-key: file:/opt/zhiyuan/config/keys/public.pem
    access-token-ttl: 15m
    refresh-token-ttl: 7d
  verification:
    enabled: false
  wechat:
    enabled: false
    frontend-callback-url: http://47.108.184.164/login

cache:
  feed-public:
    maximum-size: 1000
    expire-seconds: 60
  feed-mine:
    maximum-size: 1000
    expire-seconds: 30

oss:
  mode: local
  folder: avatars
  local-root: /opt/zhiyuan/uploads
  local-public-base-url: /uploads
EOF

chmod 600 /opt/zhiyuan/config/application-prod.yml /opt/zhiyuan/config/db.env
