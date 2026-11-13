#!/usr/bin/env bash
# 每日全量备份：数据库 + 对象存储（MinIO）。保留 30 天，每周一归档 12 周。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
[ -f deploy/.env ] && set -a && . deploy/.env && set +a

STAMP="$(date +%Y%m%d-%H%M%S)"
DIR="${BACKUP_DIR:-/backup}/$STAMP"
mkdir -p "$DIR"

echo "[$(date)] backup start -> $DIR"

# 1. MySQL 全量（单事务，保证一致性）
docker exec wedding-mysql mysqldump \
  -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  --single-transaction --quick --routines --triggers --events \
  --default-character-set=utf8mb4 "${MYSQL_DB}" | gzip > "$DIR/mysql.sql.gz"

# 2. 对象存储（MinIO 别名需提前配置：mc alias set local http://minio:9000 key secret）
if docker exec wedding-minio sh -c 'command -v mc >/dev/null 2>&1'; then
  docker exec wedding-minio mc mirror --overwrite local/wedding "/data-export/$STAMP" || true
fi

# 3. 版本与校验
docker exec wedding-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e \
  "SELECT version(); SELECT COUNT(*) FROM ${MYSQL_DB}.flyway_schema_history;" > "$DIR/version.txt" 2>/dev/null || true
sha256sum "$DIR/mysql.sql.gz" > "$DIR/mysql.sql.gz.sha256"

# 4. 保留策略
find "${BACKUP_DIR:-/backup}" -maxdepth 1 -type d -mtime +30 -name '20*' -exec rm -rf {} +
if [ "$(date +%u)" = "1" ]; then
  mkdir -p "${BACKUP_DIR:-/backup}/weekly"
  cp "$DIR/mysql.sql.gz" "${BACKUP_DIR:-/backup}/weekly/mysql-$STAMP.sql.gz"
  find "${BACKUP_DIR:-/backup}/weekly" -type f -mtime +84 -delete
fi

echo "[$(date)] backup done: $(du -h "$DIR/mysql.sql.gz" | cut -f1)"
