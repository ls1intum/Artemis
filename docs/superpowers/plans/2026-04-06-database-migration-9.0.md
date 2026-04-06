# Database Migration 9.0 — Consolidate Schema for Major Release

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate all Liquibase changelogs deployed on release/8.8.x into a new initial schema for the 9.0 major release, following the established migration path pattern.

**Architecture:** The migration system requires administrators to install the last bugfix release (8.8.6) before upgrading to 9.0.0. At upgrade time, `DatabaseMigration.checkMigrationPath()` detects the version jump, nullifies the initial schema checksum in `DATABASECHANGELOG`, and Liquibase recalculates it against the new consolidated schema. The `cleanup.xml` changeset deletes all old changelog entries. For fresh installs, only the new initial schema + post-8.8.x changelogs are applied.

**Tech Stack:** Liquibase 5.0.2, PostgreSQL (Testcontainers), MySQL, Spring Boot 3.5, Gradle

---

## Context

### What gets folded into the new initial schema (66 changelog files from release/8.8.x):
- `20240513101552_changelog.xml` through `20260220093729_changelog.xml`
- Plus the existing `00000000000000_initial_schema.xml` content

### What stays as individual changelog files (7 develop-only changelogs):
- `20260205132454_changelog.xml`
- `20260301010101_changelog.xml`
- `20260304120000_e2e_seed_data.xml`
- `20260312120000_changelog.xml`
- `20260313112454_changelog.xml`
- `20260327190000_changelog.xml`
- `20260330120000_changelog.xml`

### File map:
- **Modify:** `src/main/java/de/tum/cit/aet/artemis/core/config/migration/DatabaseMigration.java:106-111` — add new migration path
- **Modify:** `src/main/resources/config/liquibase/master.xml` — remove folded changelogs
- **Modify:** `src/main/resources/config/liquibase/changelog/20240331151800_cleanup.xml` — update changeSet ID
- **Replace:** `src/main/resources/config/liquibase/changelog/00000000000000_initial_schema.xml` — new consolidated schema
- **Delete:** 66 changelog XML files that were on release/8.8.x
- **Modify:** `gradle/liquibase.gradle` — update to support PostgreSQL for schema generation

---

### Task 1: Generate the new initial schema from a fully-migrated 8.8.x database

This is the most critical and complex step. The initial schema must be generated from a real database that has all release/8.8.x migrations applied.

**Files:**
- Replace: `src/main/resources/config/liquibase/changelog/00000000000000_initial_schema.xml`

- [ ] **Step 1: Start a clean PostgreSQL database via Docker**

```bash
docker run --name artemis-migration-db -e POSTGRES_USER=root -e POSTGRES_PASSWORD=root -e POSTGRES_DB=Artemis -p 5432:5432 -d postgres:17
```

Wait a few seconds for it to start:
```bash
docker exec artemis-migration-db pg_isready -U root
```

- [ ] **Step 2: Check out release/8.8.x and apply all migrations**

```bash
# In a separate directory or worktree
git worktree add /tmp/artemis-8.8.x origin/release/8.8.x
cd /tmp/artemis-8.8.x
```

Configure application to use the PostgreSQL database. Create/edit `src/main/resources/config/application-local.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/Artemis
    username: root
    password: root
```

Start the application briefly so Liquibase applies all changelogs:
```bash
./gradlew bootRun -Pprofile=local -x webapp
# Wait until you see "Liquibase: Update has been successful" in the logs
# Then stop the app with Ctrl+C
```

- [ ] **Step 3: Create an empty reference database for diffChangeLog**

```bash
docker exec artemis-migration-db psql -U root -c "CREATE DATABASE \"ArtemisEmpty\";"
```

- [ ] **Step 4: Generate the changelog using Liquibase diffChangeLog**

The `gradle/liquibase.gradle` is configured for MySQL by default. For PostgreSQL, run the Liquibase CLI directly or modify the gradle task temporarily.

Option A — Direct Liquibase CLI via Gradle (modify `gradle/liquibase.gradle` temporarily for PostgreSQL):

```bash
cd /tmp/artemis-8.8.x
```

Edit `gradle/liquibase.gradle` temporarily to use PostgreSQL URLs:
```groovy
args = [
    "--changeLogFile=src/main/resources/config/liquibase/changelog/00000000000000_initial_schema.xml",
    "--referenceUsername=root",
    "--referencePassword=root",
    "--referenceUrl=jdbc:postgresql://localhost:5432/ArtemisEmpty",
    "--username=root",
    "--password=root",
    "--url=jdbc:postgresql://localhost:5432/Artemis",
    "--driver=org.postgresql.Driver",
    command
]
```

Then run:
```bash
./gradlew liquibaseDiffChangeLog
```

Option B — Use Liquibase CLI directly if you have it installed:
```bash
liquibase \
  --changeLogFile=initial_schema_generated.xml \
  --referenceUrl=jdbc:postgresql://localhost:5432/ArtemisEmpty \
  --referenceUsername=root \
  --referencePassword=root \
  --url=jdbc:postgresql://localhost:5432/Artemis \
  --username=root \
  --password=root \
  --driver=org.postgresql.Driver \
  diffChangeLog
```

- [ ] **Step 5: Clean up the generated initial schema XML**

The generated file needs manual cleanup:
1. Set the `changeSet` ID to `00000000000001` and author to `krusche`
2. Remove any Liquibase internal tables (`DATABASECHANGELOG`, `DATABASECHANGELOGLOCK`, `artemis_version`, `migration_changelog`) from the generated schema — these are managed by Liquibase/application code, not the initial schema

   **CRITICAL:** Keep the `artemis_version` table definition — it IS part of the initial schema (check the current initial schema to confirm). But do NOT include `DATABASECHANGELOG` or `DATABASECHANGELOGLOCK`.

3. Ensure the XML header matches the current format:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
    <changeSet author="krusche" id="00000000000001">
        <!-- all createTable, addForeignKeyConstraint, createIndex statements -->
    </changeSet>
</databaseChangeLog>
```

4. Ensure all types use Liquibase-portable types (e.g., `bigint`, `varchar(N)`, `boolean`, `datetime(3)`, `longtext`), not PostgreSQL-native types (e.g., `int8`, `text`, `bool`). The `diffChangeLog` command with Liquibase should handle this, but verify.

5. Copy the cleaned file to the main repo:
```bash
cp /tmp/artemis-8.8.x/src/main/resources/config/liquibase/changelog/00000000000000_initial_schema.xml \
   src/main/resources/config/liquibase/changelog/00000000000000_initial_schema.xml
```

- [ ] **Step 6: Clean up Docker and worktree**

```bash
docker stop artemis-migration-db && docker rm artemis-migration-db
git worktree remove /tmp/artemis-8.8.x
```

---

### Task 2: Add the new migration path in DatabaseMigration.java

**Files:**
- Modify: `src/main/java/de/tum/cit/aet/artemis/core/config/migration/DatabaseMigration.java:106-111`

- [ ] **Step 1: Add the 8.8.6 migration path**

In `DatabaseMigration.java`, add a new migration path after the existing ones (line 109):

```java
        // Initialize migration paths here in the correct order
        migrationPaths.add(new MigrationPath("5.12.9")); // required for migration to 6.0.0 until 7.0.0
        migrationPaths.add(new MigrationPath("6.9.6"));  // required for migration to 7.0.0 until 8.0.0
        migrationPaths.add(new MigrationPath("7.10.5"));  // required for migration to 8.0.0 until 9.0.0
        migrationPaths.add(new MigrationPath("8.8.6"));  // required for migration to 9.0.0 until 10.0.0
```

- [ ] **Step 2: Update the Liquibase version in `updateInitialChecksum`**

The `updateInitialChecksum` method (line 228) has a hardcoded Liquibase version `4.27.0` that must match the current version `5.0.2`:

```java
        String updateSqlStatement = """
                UPDATE DATABASECHANGELOG
                SET MD5SUM = null,
                    DATEEXECUTED = now(),
                    DESCRIPTION = ?,
                    LIQUIBASE = '5.0.2',
                    FILENAME = 'config/liquibase/changelog/00000000000000_initial_schema.xml'
                WHERE ID = '00000000000001';
                """;
```

- [ ] **Step 3: Verify the code compiles**

```bash
./gradlew compileJava -x webapp
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/de/tum/cit/aet/artemis/core/config/migration/DatabaseMigration.java
git commit -m "Development: Add migration path 8.8.6 → 9.0.0 and update Liquibase version"
```

---

### Task 3: Update the cleanup changeset

**Files:**
- Modify: `src/main/resources/config/liquibase/changelog/20240331151800_cleanup.xml`

The cleanup changeset must get a new ID so it re-executes during migration. The pattern is to use a timestamp-based ID matching the migration date.

- [ ] **Step 1: Update the changeSet ID**

Change the changeSet ID from `20250417182300` to a new timestamp (use the current date):

```xml
<?xml version="1.1" encoding="UTF-8" standalone="no"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
    <changeSet id="20260406120000" author="krusche">
        <sql>
            DELETE FROM DATABASECHANGELOG WHERE ID not like '00000000000001';
        </sql>
        <sql>
            DELETE FROM migration_changelog;
        </sql>
    </changeSet>
</databaseChangeLog>
```

The new ID `20260406120000` ensures:
- For **existing systems upgrading**: The cleanup runs because it has a new ID not yet in DATABASECHANGELOG. It deletes all old changelog entries, leaving only the initial schema entry.
- For **fresh installs**: The cleanup runs once (harmlessly — there are no old entries to delete).

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/config/liquibase/changelog/20240331151800_cleanup.xml
git commit -m "Development: Update cleanup changeset ID for 9.0 migration"
```

---

### Task 4: Update master.xml — remove folded changelogs, keep post-release ones

**Files:**
- Modify: `src/main/resources/config/liquibase/master.xml`

- [ ] **Step 1: Replace master.xml content**

Remove all 66 changelog includes that were on release/8.8.x. Keep only the initial schema, cleanup, and the 7 develop-only changelogs:

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
    <include file="classpath:config/liquibase/changelog/00000000000000_initial_schema.xml" relativeToChangelogFile="false"/>
    <include file="classpath:config/liquibase/changelog/20240331151800_cleanup.xml" relativeToChangelogFile="false"/>
    <include file="classpath:config/liquibase/changelog/20260205132454_changelog.xml" relativeToChangelogFile="false"/>
    <include file="classpath:config/liquibase/changelog/20260301010101_changelog.xml" relativeToChangelogFile="false"/>
    <include file="classpath:config/liquibase/changelog/20260304120000_e2e_seed_data.xml" relativeToChangelogFile="false"/>
    <include file="classpath:config/liquibase/changelog/20260312120000_changelog.xml" relativeToChangelogFile="false"/>
    <include file="classpath:config/liquibase/changelog/20260313112454_changelog.xml" relativeToChangelogFile="false"/>
    <include file="classpath:config/liquibase/changelog/20260327190000_changelog.xml" relativeToChangelogFile="false"/>
    <include file="classpath:config/liquibase/changelog/20260330120000_changelog.xml" relativeToChangelogFile="false"/>
    <!-- NOTE: please use the format "YYYYMMDDhhmmss_changelog.xml", i.e. year month day hour minutes seconds and not something else! -->
    <!-- we should also stay in a chronological order! -->
    <!-- you can use the command "date '+%Y%m%d%H%M%S'" to get the current date and time in the correct format -->
</databaseChangeLog>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/config/liquibase/master.xml
git commit -m "Development: Remove folded changelogs from master.xml for 9.0 migration"
```

---

### Task 5: Delete the 66 old changelog files

**Files:**
- Delete: 66 changelog XML files from `src/main/resources/config/liquibase/changelog/`

- [ ] **Step 1: Delete all changelog files that were folded into the initial schema**

```bash
cd src/main/resources/config/liquibase/changelog/
git rm \
  20240513101552_changelog.xml \
  20241218152849_changelog.xml \
  20250113154600_changelog.xml \
  20250114140000_changelog.xml \
  20250214103211_changelog.xml \
  20250309013555_changelog.xml \
  20250302174100_changelog.xml \
  20250326161400_changelog.xml \
  20250326145226_changelog.xml \
  20250327083600_changelog.xml \
  20250328114200_changelog.xml \
  20250329091700_changelog.xml \
  20250329095200_changelog.xml \
  20250329103600_changelog.xml \
  20250329172100_changelog.xml \
  20250330140400_changelog.xml \
  20250402152800_changelog.xml \
  20250409003009_changelog.xml \
  20250404204728_changelog.xml \
  20250411202800_changelog.xml \
  20250416233114_changelog.xml \
  20250417224314_changelog.xml \
  20250501145700_changelog.xml \
  20250506142300_changelog.xml \
  20250521014500_changelog.xml \
  20250523161733_changelog.xml \
  20250524100749_changelog.xml \
  20250525101905_changelog.xml \
  20250525131700_changelog.xml \
  20250527120500_changelog.xml \
  20250619010700_changelog.xml \
  20250621162700_changelog.xml \
  20250627143900_changelog.xml \
  20250630090800_changelog.xml \
  20250701095541_changelog.xml \
  20250712200001_changelog.xml \
  20250713192142_changelog.xml \
  20250805132415_changelog.xml \
  20250721212121_changelog.xml \
  20250718201805_changelog.xml \
  20250731140151_changelog.xml \
  20250802175051_changelog.xml \
  20250901221600_changelog.xml \
  20250911170100_changelog.xml \
  20250920163252_changelog.xml \
  20250930145942_changelog.xml \
  20251006234500_changelog.xml \
  20251012122800_changelog.xml \
  20251017200800_changelog.xml \
  20251020175436_changelog.xml \
  20251023184348_changelog.xml \
  20251024184500_changelog.xml \
  20251030120000_changelog.xml \
  20251106150715_changelog.xml \
  20251113185215_changelog.xml \
  20251118134547_changelog.xml \
  20251119162133_changelog.xml \
  20251129123347_changelog.xml \
  20251210170000_changelog.xml \
  20251202184601_changelog.xml \
  20251201120000_changelog.xml \
  20251219191421_changelog.xml \
  20260106120000_changelog.xml \
  20260109003547_changelog.xml \
  20260115143000_changelog.xml \
  20260220093729_changelog.xml
```

- [ ] **Step 2: Verify only the correct files remain**

```bash
ls src/main/resources/config/liquibase/changelog/
```

Expected remaining files:
- `00000000000000_initial_schema.xml`
- `20240331151800_cleanup.xml`
- `20260205132454_changelog.xml`
- `20260301010101_changelog.xml`
- `20260304120000_e2e_seed_data.xml`
- `20260312120000_changelog.xml`
- `20260313112454_changelog.xml`
- `20260327190000_changelog.xml`
- `20260330120000_changelog.xml`

- [ ] **Step 3: Commit**

```bash
git add -A src/main/resources/config/liquibase/changelog/
git commit -m "Development: Delete 66 old changelog files folded into 9.0 initial schema"
```

---

### Task 6: Verification — fresh install scenario (PostgreSQL)

This verifies that a brand-new Artemis system can start from scratch with the new initial schema.

- [ ] **Step 1: Start a clean PostgreSQL database**

```bash
docker run --name artemis-fresh-test -e POSTGRES_USER=root -e POSTGRES_PASSWORD=root -e POSTGRES_DB=Artemis -p 5432:5432 -d postgres:17
```

- [ ] **Step 2: Run the server tests (which use Testcontainers)**

```bash
./gradlew test --tests "de.tum.cit.aet.artemis.core.config.migration.*" -x webapp
```

Expected: All migration-related tests pass.

- [ ] **Step 3: Start the application against the clean database**

```bash
./gradlew bootRun -x webapp
```

Expected: Application starts successfully. Logs show:
- "Migration path check: Not necessary" (no previous version in DB)
- "Liquibase: Update has been successful"
- "Inserting latest version 9.0.0 into database"

- [ ] **Step 4: Clean up**

```bash
docker stop artemis-fresh-test && docker rm artemis-fresh-test
```

---

### Task 7: Verification — upgrade scenario (8.8.6 → 9.0.0)

This verifies that an existing Artemis 8.8.6 system can upgrade to 9.0.0.

- [ ] **Step 1: Set up a database with 8.8.6 state**

```bash
docker run --name artemis-upgrade-test -e POSTGRES_USER=root -e POSTGRES_PASSWORD=root -e POSTGRES_DB=Artemis -p 5432:5432 -d postgres:17

# Check out 8.8.x and start briefly to apply all 8.8.x migrations
git worktree add /tmp/artemis-upgrade-test origin/release/8.8.x
cd /tmp/artemis-upgrade-test
# Configure application-local.yml as in Task 1, Step 2
./gradlew bootRun -Pprofile=local -x webapp
# Wait for startup, then stop
```

Verify the version is stored:
```bash
docker exec artemis-upgrade-test psql -U root -d Artemis -c "SELECT * FROM artemis_version;"
```

Expected: `latest_version = 8.8.6` (or whatever version is in build.gradle on release/8.8.x)

- [ ] **Step 2: Switch to the 9.0.0 branch and start**

```bash
cd /path/to/main/repo  # back to the develop branch with the migration changes
# Configure application-local.yml to point to the same database
./gradlew bootRun -Pprofile=local -x webapp
```

Expected logs (in order):
1. `"Set checksum of initial schema to null so that liquibase will recalculate it"` — migration path detected
2. `"Successfully cleaned up initial schema during migration"`
3. Liquibase applies the cleanup changeset (deletes old DATABASECHANGELOG entries)
4. Liquibase applies the 7 new changelogs
5. `"Updating latest version to 9.0.0 in database"`

- [ ] **Step 3: Verify database state after upgrade**

```bash
docker exec artemis-upgrade-test psql -U root -d Artemis -c "SELECT * FROM artemis_version;"
# Expected: latest_version = 9.0.0

docker exec artemis-upgrade-test psql -U root -d Artemis -c "SELECT id, filename FROM databasechangelog ORDER BY orderexecuted;"
# Expected: Only the initial schema entry + cleanup + the 7 new changelogs
```

- [ ] **Step 4: Clean up**

```bash
docker stop artemis-upgrade-test && docker rm artemis-upgrade-test
git worktree remove /tmp/artemis-upgrade-test
```

---

### Task 8: Verification — blocked upgrade scenario (too old version)

This verifies that upgrading from a version older than 8.8.6 is correctly blocked.

- [ ] **Step 1: Set up a database with an old version**

```bash
docker run --name artemis-blocked-test -e POSTGRES_USER=root -e POSTGRES_PASSWORD=root -e POSTGRES_DB=Artemis -p 5432:5432 -d postgres:17
```

Manually insert an old version:
```bash
docker exec artemis-blocked-test psql -U root -d Artemis -c "
CREATE TABLE IF NOT EXISTS databasechangelog (ID varchar(255));
INSERT INTO databasechangelog VALUES ('00000000000001');
CREATE TABLE IF NOT EXISTS artemis_version (latest_version varchar(10) PRIMARY KEY);
INSERT INTO artemis_version VALUES ('8.7.0');
"
```

- [ ] **Step 2: Attempt to start 9.0.0**

```bash
./gradlew bootRun -Pprofile=local -x webapp
```

Expected: Application exits with code 15 and error message:
`"Cannot start Artemis because the migration path was not followed. Please deploy and start the release 8.8.6 first, otherwise the migration will fail"`

- [ ] **Step 3: Clean up**

```bash
docker stop artemis-blocked-test && docker rm artemis-blocked-test
```

---

### Task 9: Final commit and formatting check

- [ ] **Step 1: Run spotless to ensure Java formatting**

```bash
./gradlew spotlessApply -x webapp
```

- [ ] **Step 2: Run checkstyle**

```bash
./gradlew checkstyleMain -x webapp
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileJava -x webapp
```

- [ ] **Step 4: Final commit if any formatting changes**

```bash
git add -A
git commit -m "Development: Combine new initial database schema for 9.0 release"
```

---

## Migration flow summary

### Fresh install (new system):
```
Start 9.0.0 → no artemis_version table → skip migration check
  → Liquibase applies: initial_schema → cleanup (no-op) → 7 new changelogs
  → Store version 9.0.0
```

### Upgrade from 8.8.6:
```
Start 9.0.0 → read artemis_version = 8.8.6
  → MigrationPath(8.8.6): currentVersion 9.0.0 ≥ 9.0.0, < 10.0.0 ✓
  → previousVersion 8.8.6 == requiredVersion 8.8.6 ✓
  → updateInitialChecksum: set MD5SUM=null for initial schema entry
  → Liquibase recalculates checksum of initial_schema (matches new consolidated schema)
  → Liquibase runs cleanup changeset (new ID → not yet executed)
    → Deletes all DATABASECHANGELOG entries except initial schema
    → Deletes all migration_changelog entries
  → Liquibase applies 7 new changelogs
  → Store version 9.0.0
```

### Upgrade from < 8.8.6 (blocked):
```
Start 9.0.0 → read artemis_version = 8.7.x
  → MigrationPath(8.8.6): currentVersion 9.0.0 ≥ 9.0.0, < 10.0.0 ✓
  → previousVersion 8.7.x < requiredVersion 8.8.6 ✗
  → ERROR + System.exit(15)
```

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Generated initial schema has PostgreSQL-specific types | Verify with both MySQL and PostgreSQL fresh installs |
| Missing table/column in initial schema | Compare table count/column count between 8.8.x fully-migrated DB and fresh 9.0.0 DB |
| Liquibase type mapping differences between `diffChangeLog` and hand-written schemas | Review generated XML against current initial schema format |
| Cleanup changeset runs at wrong time | The new changeSet ID ensures it only runs once; existing systems see it as new; fresh systems run it harmlessly |
| `updateInitialChecksum` still references old Liquibase version | Task 2 updates the hardcoded version from `4.27.0` to `5.0.2` |
