# Access Rights DSL Performance Benchmark

## Overview

This benchmark measures the performance impact of the newly introduced access policy Domain Specific Language (DSL) on two dashboard endpoints. It measures both **endpoint response time** (client-side) and provides instructions for analyzing the **database query impact** (server-side SQL output).

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

2. **Benchmark execution**:
   - Authenticates as the benchmark student
   - Runs warmup iterations (not measured) to eliminate cold-start effects
   - Runs measured iterations for each endpoint
   - Computes statistics: mean, median, min, max, P90, P95, standard deviation

3. **Output**:
   - Per-endpoint timing statistics
   - Summary comparison
   - Instructions for database query analysis

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

### 3. Analyze database queries

After running with `--iterations=1`, check the server console output:

1. Look for Hibernate SQL statements (lines starting with `select`, `insert`, etc.)
2. Count the number of SQL queries executed during a single request to each endpoint
3. Key queries to identify:
   - **Course visibility query** (policy-generated): `SELECT ... FROM course WHERE ...` — this is the SQL automatically generated from `CourseVisibilityPolicy` via `PolicyBasedCourseSpecs`
   - **Exercise loading queries**: `SELECT ... FROM exercise WHERE ...`
   - **User/group resolution**: `SELECT ... FROM jhi_user ... groups ...`

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

Environment variables `ARTEMIS_SERVER_URL`, `ARTEMIS_ADMIN_USER`, `ARTEMIS_ADMIN_PASSWORD`, `ARTEMIS_STUDENT_PASSWORD` can be used instead of CLI flags.

## Example output

```
============================================================
Access Rights DSL -- Performance Benchmark
============================================================
Server URL : http://localhost:8080
Iterations : 20 (+ 3 warmup)

Authenticating as admin...
  Authenticated as artemis_admin

Authenticating as benchmark student (bench_student)...
  Authenticated as bench_student

============================================================
BENCHMARK RESULTS
============================================================

  Running: GET /api/core/courses/for-dashboard (all courses)
  Warmup: 3, Measured iterations: 20

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

  Running: GET /api/core/courses/1/for-dashboard (single course)
  Warmup: 3, Measured iterations: 20

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

============================================================
SUMMARY
============================================================

All Courses Dashboard (policy-based SQL generation from DSL):
  Mean: 145.32 ms | Median: 138.50 ms | P95: 198.30 ms

Single Course Dashboard (AOP @EnforceAccessPolicy + runtime PolicyEngine):
  Mean: 42.18 ms | Median: 39.50 ms | P95: 62.40 ms
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
