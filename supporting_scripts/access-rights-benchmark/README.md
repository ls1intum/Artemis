# Access Rights DSL Performance Benchmark

## Overview

This benchmark measures the performance impact of the newly introduced access policy Domain Specific Language (DSL) on two dashboard endpoints. It supports two modes:

1. **Response Time Mode** (default) — measures endpoint latency over many iterations with statistics
2. **SQL Analysis Mode** (`--sql-analysis`) — fires each endpoint once, captures the server log, and counts the exact number of SQL queries executed per request

Both modes are designed for **A/B comparison**: run the benchmark on the `develop` branch (baseline), then on the DSL branch, and compare the results side by side.

### What is being benchmarked

The DSL introduces a declarative way to define access control policies that are used in two ways:

1. **Runtime policy evaluation** (`PolicyEngine.isAllowed()`) — checks access in-memory at the controller/service level
2. **SQL query generation** (`AccessPolicy.toSpecification()`) — converts the same policy into a JPA Specification for database-level filtering

Both mechanisms are derived from the **same policy definition**, ensuring consistency between authorization checks and database queries.

### Endpoints under test

| # | Endpoint | DSL Usage | Policy |
|---|----------|-----------|--------|
| 1 | `GET /api/core/courses/for-dashboard` | `PolicyBasedCourseSpecs.withVisibilityAccessAndActive()` generates SQL WHERE clause from the DSL; `ProgrammingExerciseVisibleService` filters exercises at runtime | `CourseVisibilityPolicy`: `memberOfGroup(Course::getStudentGroupName).and(hasStarted(Course::getStartDate))` |
| 2 | `GET /api/core/courses/{courseId}/for-dashboard` | `@EnforceAccessPolicy(CourseVisibilityPolicy)` AOP aspect loads the course and evaluates the policy via `PolicyEngine`; exercise filtering same as above | `ProgrammingExerciseAccessPolicies`: `memberOfGroup(...).and(hasStarted(ProgrammingExercise::getReleaseDate))` |

### What the script does

1. **Data setup** (optional, via `--setup-data`):
   - Creates a configurable number of courses (default: 10)
   - Each course gets a configurable number of programming exercises (default: 5)
   - Creates a benchmark student user enrolled in all courses
   - All courses have a start date in the past (active) and end date in the future

2. **Response Time Mode** (default):
   - Authenticates as the benchmark student
   - Runs warmup iterations (not measured) to eliminate cold-start effects
   - Runs measured iterations for each endpoint
   - Computes statistics: mean, median, min, max, P90, P95, standard deviation

3. **SQL Analysis Mode** (`--sql-analysis`):
   - Reads the Artemis server log file (requires `--server-log=<path>`)
   - Fires one warmup request (not measured), then one measured request per endpoint
   - Captures the log window during the measured request
   - Counts SQL queries by type (SELECT, INSERT, UPDATE, DELETE) and unique query patterns
   - Reports the exact query count per endpoint

4. **Output file** (`--output=<path>`):
   - Appends one JSON record per run (NDJSON format)
   - Includes all stats, SQL counts, and the `--label` for easy diffing

## Prerequisites

- Artemis server running locally (default: `http://localhost:8080`)
- Node.js 18+ (uses native `fetch` and `performance.now()`)
- Admin credentials for data setup

## How to run

### 1. Enable SQL logging on the server

In your `application-local.yml`, add:

```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

This prints all Hibernate SQL queries to the server console. For the **database query benchmark**, you will count these queries during a single request.

Restart the server after changing this configuration.

### 2. Run the benchmark

From the repository root:

```bash
# Full run: create 10 courses with 5 exercises each, then benchmark
node supporting_scripts/access-rights-benchmark/benchmark-access-policies.mjs \
  --setup-data \
  --course-count=10 \
  --exercises-per-course=5 \
  --iterations=20

# Benchmark only (data already exists from a previous run)
node supporting_scripts/access-rights-benchmark/benchmark-access-policies.mjs \
  --iterations=50

# Benchmark a specific existing course
node supporting_scripts/access-rights-benchmark/benchmark-access-policies.mjs \
  --course-id=42 \
  --iterations=30

# Custom server URL and credentials
node supporting_scripts/access-rights-benchmark/benchmark-access-policies.mjs \
  --server-url=http://localhost:8080 \
  --admin-user=artemis_admin \
  --admin-password=artemis_admin \
  --setup-data
```

### 3. SQL query analysis (automated)

The `--sql-analysis` mode automatically counts SQL queries per request by reading the server log file.

```bash
# Start the server, teeing output to a log file:
./gradlew bootRun 2>&1 | tee server.log

# In another terminal, run SQL analysis:
node supporting_scripts/access-rights-benchmark/benchmark-access-policies.mjs \
  --sql-analysis \
  --server-log=server.log \
  --course-id=1 \
  --label=dsl-branch \
  --output=benchmark-results.txt
```

This will output something like:

```
  SQL queries executed:
    Total   : 12
    SELECTs : 11
    INSERTs : 0
    UPDATEs : 0
    DELETEs : 0
    Unique  : 8
```

### 4. A/B comparison workflow (develop vs. DSL branch)

This is the recommended workflow for comparing database performance between branches:

```bash
# --- Step 1: Set up test data (only needed once) ---
git checkout develop
# Start server, then:
node supporting_scripts/access-rights-benchmark/benchmark-access-policies.mjs \
  --setup-data --course-count=10

# Note the course ID from the output (e.g., 1)

# --- Step 2: Benchmark the baseline (develop branch) ---
# Start server with:  ./gradlew bootRun 2>&1 | tee server.log
node supporting_scripts/access-rights-benchmark/benchmark-access-policies.mjs \
  --sql-analysis --server-log=server.log \
  --course-id=1 --label=develop --output=results.txt

node supporting_scripts/access-rights-benchmark/benchmark-access-policies.mjs \
  --iterations=30 --course-id=1 --label=develop --output=results.txt

# --- Step 3: Benchmark the DSL branch ---
git checkout chore/development/access-rights/example-for-domain-specific-language
# Restart server with:  ./gradlew bootRun 2>&1 | tee server.log
node supporting_scripts/access-rights-benchmark/benchmark-access-policies.mjs \
  --sql-analysis --server-log=server.log \
  --course-id=1 --label=dsl-branch --output=results.txt

node supporting_scripts/access-rights-benchmark/benchmark-access-policies.mjs \
  --iterations=30 --course-id=1 --label=dsl-branch --output=results.txt

# --- Step 4: Compare ---
cat results.txt
# Each line is a JSON object with "label", "mode", and all stats.
# Compare "develop" vs "dsl-branch" entries.
```

**What to look for:**
- **SQL query count**: same or fewer queries = no regression
- **Response time**: similar or better median/P95 = no regression
- **Query shape**: inspect the server log to verify the policy-generated SQL WHERE clause matches the old hand-written JPQL

## CLI Options

| Option | Default | Description |
|--------|---------|-------------|
| `--server-url=<url>` | `http://localhost:8080` | Artemis server URL |
| `--admin-user=<username>` | `artemis_admin` | Admin username (for data setup) |
| `--admin-password=<pass>` | `artemis_admin` | Admin password |
| `--student-password=<pass>` | `Password123!` | Password for the benchmark student |
| `--course-count=<n>` | `10` | Number of courses to create |
| `--exercises-per-course=<n>` | `5` | Programming exercises per course |
| `--iterations=<n>` | `20` | Measured benchmark iterations per endpoint |
| `--warmup=<n>` | `3` | Warmup iterations (not measured) |
| `--setup-data` | off | Create test data before benchmarking |
| `--course-id=<id>` | (auto) | Course ID for the single-course endpoint |
| `--sql-analysis` | off | SQL query counting mode (requires `--server-log`) |
| `--server-log=<path>` | — | Path to server log file (used with `--sql-analysis`) |
| `--output=<path>` | — | Append results as NDJSON for later comparison |
| `--label=<name>` | timestamp | Label for this run (e.g. `develop`, `dsl-branch`) |

Environment variables `ARTEMIS_SERVER_URL`, `ARTEMIS_ADMIN_USER`, `ARTEMIS_ADMIN_PASSWORD`, `ARTEMIS_STUDENT_PASSWORD` can be used instead of CLI flags.

## Example output

### Response Time Mode

```
============================================================
Access Rights DSL -- Performance Benchmark
============================================================
Server URL : http://localhost:8080
Mode       : Response Time
Iterations : 20 (+ 3 warmup)

  Endpoint: GET /api/core/courses/for-dashboard
  --------------------------------------------------
  Iterations : 20
  Mean       : 145.32 ms
  Median     : 138.50 ms
  Min        : 112.10 ms
  Max        : 210.45 ms
  P90        : 185.20 ms
  P95        : 198.30 ms
  Std Dev    : 28.15 ms

  Endpoint: GET /api/core/courses/1/for-dashboard
  --------------------------------------------------
  Iterations : 20
  Mean       : 42.18 ms
  Median     : 39.50 ms
  Min        : 31.20 ms
  Max        : 68.90 ms
  P90        : 55.10 ms
  P95        : 62.40 ms
  Std Dev    : 10.25 ms
```

### SQL Analysis Mode

```
============================================================
Access Rights DSL -- Performance Benchmark
============================================================
Server URL : http://localhost:8080
Mode       : SQL Analysis
Label      : dsl-branch

------------------------------------------------------------
Endpoint 1: GET /api/core/courses/for-dashboard
------------------------------------------------------------

  Response time: 152.45 ms
  SQL queries executed:
    Total   : 14
    SELECTs : 13
    INSERTs : 0
    UPDATEs : 0
    DELETEs : 0
    Unique  : 9

------------------------------------------------------------
Endpoint 2: GET /api/core/courses/1/for-dashboard
------------------------------------------------------------

  Response time: 38.72 ms
  SQL queries executed:
    Total   : 8
    SELECTs : 8
    INSERTs : 0
    UPDATEs : 0
    DELETEs : 0
    Unique  : 6

============================================================
SQL ANALYSIS SUMMARY
============================================================
Label: dsl-branch

  GET /api/core/courses/for-dashboard
    Response time : 152.45 ms
    Total queries : 14
    SELECTs       : 13
    Unique queries: 9

  GET /api/core/courses/1/for-dashboard
    Response time : 38.72 ms
    Total queries : 8
    SELECTs       : 8
    Unique queries: 6
```

## Architecture context

### DSL policy definitions

- **`CourseVisibilityPolicy`** (`CourseVisibilityPolicy.java`):
  ```java
  memberOfGroup(Course::getStudentGroupName, Course_.studentGroupName)
      .and(hasStarted(Course::getStartDate, Course_.startDate))
  ```
  Used for both runtime checks and SQL generation.

- **`ProgrammingExerciseAccessPolicies`** (`ProgrammingExerciseAccessPolicies.java`):
  ```java
  memberOfGroup(courseGroup(Course::getStudentGroupName))
      .and(hasStarted(ProgrammingExercise::getReleaseDate))
  ```
  Used for runtime exercise visibility checks.

### How policies become SQL

```
Policy DSL definition
    --> AccessPolicy.toSpecification(userGroups, isAdmin)
        --> JPA Specification<Course>
            --> Hibernate generates SQL WHERE clause
```

This is done in `PolicyBasedCourseSpecs`, which wraps each policy bean and exposes convenience methods like `withVisibilityAccessAndActive()`.

### How policies enforce access at runtime

```
@EnforceAccessPolicy(CourseVisibilityPolicy.class)
    --> EnforceAccessPolicyAspect (AOP)
        --> PolicyEngine.checkAllowedElseThrow(policy, user, resource)
            --> Evaluates each rule's Condition against user + resource
```
