#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# Schema Equivalence Verification Script
#
# Verifies that the consolidated initial schema produces identical database
# structures on MySQL and PostgreSQL compared to the develop branch.
#
# Prerequisites:
#   - Docker installed and running
#   - Ports 3310, 3311, 5440, 5441 available (not used by local databases)
#   - Run from the Artemis project root directory
#
# Usage:
#   bash src/main/resources/config/liquibase/consolidate-changelogs.sh
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../../../../.." && pwd)"
cd "$PROJECT_DIR"

MYSQL_DEV_PORT=3310; MYSQL_NEW_PORT=3311
PG_DEV_PORT=5440;    PG_NEW_PORT=5441
WORKTREE_DIR="/tmp/artemis-develop-verify"
DUMP_DIR="/tmp/artemis-schema-dumps"

echo "=== Artemis Schema Equivalence Verification ==="
echo "Project: $PROJECT_DIR"
echo ""

# --- Cleanup ---
cleanup() {
    echo "=== Cleaning up ==="
    docker rm -f av-mysql-dev av-mysql-new av-pg-dev av-pg-new 2>/dev/null || true
    git worktree remove "$WORKTREE_DIR" --force 2>/dev/null || true
    rm -f /tmp/lb-verify-init.gradle
}
trap cleanup EXIT

# --- Step 1: Start containers ---
echo "=== Step 1: Starting database containers ==="
cleanup 2>/dev/null || true
mkdir -p "$DUMP_DIR"

docker run -d --name av-mysql-dev -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=Artemis \
    -p $MYSQL_DEV_PORT:3306 mysql:9 \
    --lower_case_table_names=1 --character_set_server=utf8mb4 --collation-server=utf8mb4_unicode_ci --explicit_defaults_for_timestamp
docker run -d --name av-mysql-new -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=Artemis \
    -p $MYSQL_NEW_PORT:3306 mysql:9 \
    --lower_case_table_names=1 --character_set_server=utf8mb4 --collation-server=utf8mb4_unicode_ci --explicit_defaults_for_timestamp
docker run -d --name av-pg-dev -e POSTGRES_USER=artemis -e POSTGRES_PASSWORD=artemis -e POSTGRES_DB=Artemis \
    -p $PG_DEV_PORT:5432 postgres:18
docker run -d --name av-pg-new -e POSTGRES_USER=artemis -e POSTGRES_PASSWORD=artemis -e POSTGRES_DB=Artemis \
    -p $PG_NEW_PORT:5432 postgres:18

echo "  Waiting for databases..."
for i in $(seq 1 60); do
    ALL=true
    docker exec av-mysql-dev mysqladmin ping -h localhost --silent 2>/dev/null || ALL=false
    docker exec av-mysql-new mysqladmin ping -h localhost --silent 2>/dev/null || ALL=false
    docker exec av-pg-dev pg_isready -U artemis -q 2>/dev/null || ALL=false
    docker exec av-pg-new pg_isready -U artemis -q 2>/dev/null || ALL=false
    if $ALL; then echo "  All databases ready!"; break; fi
    if [ "$i" -eq 60 ]; then
        echo "  ERROR: Databases did not become ready in time. Aborting."
        exit 1
    fi
    sleep 2
done

echo "  MySQL: $(docker exec av-mysql-dev mysql -u root -N -e 'SELECT VERSION();' 2>/dev/null)"
echo "  PostgreSQL: $(docker exec av-pg-dev psql -U artemis -t -A -c 'SELECT version();' Artemis 2>/dev/null | head -1)"

# --- Step 2: Create develop worktree ---
echo "=== Step 2: Creating develop worktree ==="
if ! git worktree add "$WORKTREE_DIR" develop --quiet 2>/dev/null; then
    echo "  Worktree already exists, reusing..."
    if [ ! -d "$WORKTREE_DIR" ]; then
        echo "  ERROR: Cannot create worktree at $WORKTREE_DIR. Aborting."
        exit 1
    fi
fi

# --- Step 3: Create Gradle init script ---
cat > /tmp/lb-verify-init.gradle << 'EOF'
allprojects {
    afterEvaluate {
        if (configurations.findByName('liquibaseRuntime') != null) {
            tasks.register("prepareLiquibase") {
                dependsOn tasks.named("processResources")
                doLast {
                    def f = file("build/resources/main/config/liquibase/master.xml")
                    if (f.exists()) {
                        f.text = f.text.replace('classpath:', '').replaceAll(/(?m).*e2e_seed_data.*\n/, '')
                    }
                }
            }
            tasks.register("liquibaseUpdate", JavaExec) {
                dependsOn tasks.named("prepareLiquibase")
                classpath = configurations.named("liquibaseRuntime").get()
                mainClass = "liquibase.integration.commandline.LiquibaseCommandLine"
                args = [
                    "--search-path=build/resources/main",
                    "--changeLogFile=config/liquibase/master.xml",
                    "--url=${project.findProperty('lbUrl') ?: ''}",
                    "--username=${project.findProperty('lbUser') ?: ''}",
                    "--password=${project.findProperty('lbPass') ?: ''}",
                    "update"
                ]
            }
        }
    }
}
EOF

# --- Step 4: Apply Liquibase ---
apply_lb() {
    local dir="$1" url="$2" user="$3" pass="$4" label="$5"
    echo "  Applying: $label"
    cd "$dir"
    ./gradlew --init-script /tmp/lb-verify-init.gradle clean processResources liquibaseUpdate \
        -PlbUrl="$url" -PlbUser="$user" -PlbPass="$pass" -x webapp --quiet 2>&1 | tail -1
    cd "$PROJECT_DIR"
}

MYSQL_OPTS="allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"

echo "=== Step 3: Applying Liquibase migrations ==="
apply_lb "$WORKTREE_DIR" "jdbc:mysql://localhost:$MYSQL_DEV_PORT/artemis?$MYSQL_OPTS" root "" "develop → MySQL"
apply_lb "$PROJECT_DIR"  "jdbc:mysql://localhost:$MYSQL_NEW_PORT/artemis?$MYSQL_OPTS" root "" "new branch → MySQL"
apply_lb "$WORKTREE_DIR" "jdbc:postgresql://localhost:$PG_DEV_PORT/Artemis" artemis artemis "develop → PostgreSQL"
apply_lb "$PROJECT_DIR"  "jdbc:postgresql://localhost:$PG_NEW_PORT/Artemis" artemis artemis "new branch → PostgreSQL"

# --- Step 5: Dump schemas ---
echo "=== Step 4: Dumping schemas ==="

MYSQL_EXCLUDE="--ignore-table=artemis.DATABASECHANGELOG --ignore-table=artemis.DATABASECHANGELOGLOCK --ignore-table=artemis.migration_changelog --ignore-table=artemis.artemis_version"
PG_EXCLUDE="-T databasechangelog -T databasechangeloglock -T migration_changelog -T artemis_version"

docker exec av-mysql-dev mysqldump -u root --no-data --skip-comments --skip-add-drop-table --skip-add-locks --skip-disable-keys $MYSQL_EXCLUDE artemis 2>/dev/null | \
    sed 's/ AUTO_INCREMENT=[0-9]*//' > "$DUMP_DIR/mysql-develop-schema.sql"
docker exec av-mysql-new mysqldump -u root --no-data --skip-comments --skip-add-drop-table --skip-add-locks --skip-disable-keys $MYSQL_EXCLUDE artemis 2>/dev/null | \
    sed 's/ AUTO_INCREMENT=[0-9]*//' > "$DUMP_DIR/mysql-new-schema.sql"

docker exec av-pg-dev pg_dump -U artemis -d Artemis --schema-only --no-owner --no-privileges --no-comments $PG_EXCLUDE 2>/dev/null > "$DUMP_DIR/pg-develop-schema.sql"
docker exec av-pg-new pg_dump -U artemis -d Artemis --schema-only --no-owner --no-privileges --no-comments $PG_EXCLUDE 2>/dev/null > "$DUMP_DIR/pg-new-schema.sql"

# --- Step 6: Compare ---
echo ""
echo "================================================================"
echo "=== SCHEMA COMPARISON RESULTS ==="
echo "================================================================"

MYSQL_OK=true
PG_OK=true

echo ""
echo "--- MySQL ---"
diff "$DUMP_DIR/mysql-develop-schema.sql" "$DUMP_DIR/mysql-new-schema.sql" > "$DUMP_DIR/mysql-diff.txt" 2>&1 || true
MYSQL_DIFF_LINES=$(grep -v 'GTID_PURGED' "$DUMP_DIR/mysql-diff.txt" | grep -c '^[<>]' 2>/dev/null || echo 0)
if [ "$MYSQL_DIFF_LINES" -eq 0 ]; then
    echo "  IDENTICAL ✓"
else
    echo "  $MYSQL_DIFF_LINES differences (excluding GTID):"
    grep -v 'GTID_PURGED' "$DUMP_DIR/mysql-diff.txt" | head -40
    MYSQL_OK=false
fi

echo ""
echo "--- PostgreSQL ---"
diff "$DUMP_DIR/pg-develop-schema.sql" "$DUMP_DIR/pg-new-schema.sql" > "$DUMP_DIR/pg-diff.txt" 2>&1 || true
PG_DIFF_LINES=$(grep -v 'restrict\|unrestrict' "$DUMP_DIR/pg-diff.txt" | grep -c '^[<>]' 2>/dev/null || echo 0)
if [ "$PG_DIFF_LINES" -eq 0 ]; then
    echo "  IDENTICAL ✓"
else
    echo "  $PG_DIFF_LINES differences (excluding per-instance session identifiers):"
    grep -v 'restrict\|unrestrict' "$DUMP_DIR/pg-diff.txt" | head -40
    PG_OK=false
fi

echo ""
echo "Schema dumps saved to: $DUMP_DIR/"
echo "  mysql-develop-schema.sql, mysql-new-schema.sql, mysql-diff.txt"
echo "  pg-develop-schema.sql, pg-new-schema.sql, pg-diff.txt"

if $MYSQL_OK && $PG_OK; then
    echo ""
    echo "✓ All schemas are identical!"
    exit 0
else
    echo ""
    echo "✗ Schema differences detected — review the diff files above."
    exit 1
fi
