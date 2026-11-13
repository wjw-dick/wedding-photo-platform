#!/usr/bin/env bash
# 恢复演练：恢复到指定备份目录。务必在隔离环境验证后再操作生产。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
[ -f deploy/.env ] && set -a && . deploy/.env && set +a

SRC="${1:?用法: restore.sh /backup/20260101-020000}"
[ -f "$SRC/mysql.sql.gz" ] || { echo "未找到 $SRC/mysql.sql.gz"; exit 1; }

if [ -f "$SRC/mysql.sql.gz.sha256" ]; then
  sha256sum -c "$SRC/mysql.sql.gz.sha256" || { echo "校验失败，终止恢复"; exit 1; }
fi

read -r -p "将覆盖数据库 ${MYSQL_DB}，输入 RESTORE 继续: " confirm
[ "$confirm" = "RESTORE" ] || { echo "已取消"; exit 1; }

# 1. 恢复前再备份一次
./deploy/backup/backup.sh || true

# 2. 停应用，防写入
docker compose -f deploy/docker-compose.yml stop app

# 3. 重建库并导入
docker exec wedding-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  -e "DROP DATABASE IF EXISTS ${MYSQL_DB}; CREATE DATABASE ${MYSQL_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
gunzip -c "$SRC/mysql.sql.gz" | docker exec -i wedding-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DB}"

# 4. 启动并校验
docker compose -f deploy/docker-compose.yml start app
sleep 30
curl -fsS http://127.0.0.1:8080/actuator/health && echo "恢复完成，健康检查通过" || echo "健康检查失败，请查看应用日志"
