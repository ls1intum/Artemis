#!/usr/bin/env node

/**
 * Access Rights DSL Performance Benchmark
 *
 * Benchmarks the two endpoints that use the new access policy DSL:
 *
 * 1. GET /api/core/courses/{courseId}/for-dashboard
 *    - Uses @EnforceAccessPolicy(CourseVisibilityPolicy) for AOP-based authorization
 *    - Uses PolicyEngine.isAllowed() for runtime course visibility check
 *    - DSL: memberOfGroup(...).and(hasStarted(Course::getStartDate))
 *
 * 2. GET /api/core/courses/for-dashboard
 *    - Uses PolicyBasedCourseSpecs.withVisibilityAccessAndActive() to generate SQL from DSL
 *    - Uses ProgrammingExerciseVisibleService for exercise-level filtering
 *    - DSL: memberOfGroup(...).and(hasStarted(ProgrammingExercise::getReleaseDate))
 *
 * Usage:
 *   node benchmark-access-policies.mjs [options]
 *
 * Options:
 *   --server-url=<url>         Server URL (default: http://localhost:8080)
 *   --admin-user=<username>    Admin username (default: artemis_admin)
 *   --admin-password=<pass>    Admin password (default: artemis_admin)
 *   --student-password=<pass>  Password for test users (default: Password123!)
 *   --course-count=<n>         Number of courses to create (default: 10)
 *   --exercises-per-course=<n> Programming exercises per course (default: 5)
 *   --iterations=<n>           Benchmark iterations per endpoint (default: 20)
 *   --warmup=<n>               Warmup iterations (not measured) (default: 3)
 *   --setup-data               Create test data before benchmarking
 *   --course-id=<id>           Benchmark a specific course (auto-discovered if omitted)
 *   --sql-analysis             SQL query analysis mode: run each endpoint once, capture
 *                              server log, count SQL queries and extract timing per request.
 *                              Defaults --server-log to "server.log" if not explicitly set.
 *                              For SQL execution timing, also enable in application-local.yml:
 *                                spring.jpa.properties.hibernate.generate_statistics: true
 *                                logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener: INFO
 *   --server-log=<path>        Path to the server log file (default: server.log with --sql-analysis).
 *                              Tip: start Artemis with  ./gradlew bootRun 2>&1 | tee server.log
 *   --output=<path>            Write results to a file (append mode) for later comparison
 *   --report=<path>            Write a formatted Markdown report of the benchmark results
 *   --label=<name>             Label for this run (e.g. "develop" or "dsl-branch")
 *   --help                     Show help
 */

// ---------------------------------------------------------------------------
// Imports – reuse the HTTP client and auth helpers from the setup-course lib
// ---------------------------------------------------------------------------
import { readFileSync, appendFileSync, writeFileSync, existsSync } from 'node:fs';
import { HttpClient } from '../course-scripts/setup-course/lib/http-client.mjs';
import { authenticate } from '../course-scripts/setup-course/lib/auth.mjs';

// ---------------------------------------------------------------------------
// CLI parsing
// ---------------------------------------------------------------------------
function parseArgs(argv) {
    const args = {};
    for (const arg of argv) {
        if (arg.startsWith('--')) {
            const [key, value] = arg.slice(2).split('=');
            args[key] = value ?? 'true';
        }
    }
    return args;
}

function printUsage() {
    console.log(`
Access Rights DSL Performance Benchmark

Usage: node benchmark-access-policies.mjs [options]

Options:
  --server-url=<url>         Server URL (default: http://localhost:8080)
  --admin-user=<username>    Admin username (default: artemis_admin)
  --admin-password=<pass>    Admin password (default: artemis_admin)
  --student-password=<pass>  Password for test users (default: Password123!)
  --course-count=<n>         Number of courses to create (default: 10)
  --exercises-per-course=<n> Programming exercises per course (default: 5)
  --iterations=<n>           Benchmark iterations per endpoint (default: 20)
  --warmup=<n>               Warmup iterations (not measured) (default: 3)
  --setup-data               Create test data before benchmarking
  --course-id=<id>           Benchmark a specific course (auto-discovered if omitted)
  --sql-analysis             SQL query analysis mode (defaults --server-log to "server.log").
                             Enable these in application-local.yml for full detail:
                               spring.jpa.show-sql: true
                               spring.jpa.properties.hibernate.format_sql: true
                               spring.jpa.properties.hibernate.generate_statistics: true
                               logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener: INFO
  --server-log=<path>        Path to server log file (default: server.log with --sql-analysis)
  --output=<path>            Append results to file for A/B comparison
  --report=<path>            Write a formatted Markdown report of the results
  --label=<name>             Label for this run (e.g. "develop", "dsl-branch")
  --help                     Show this help

Environment Variables:
  ARTEMIS_SERVER_URL         Server URL
  ARTEMIS_ADMIN_USER         Admin username
  ARTEMIS_ADMIN_PASSWORD     Admin password
  ARTEMIS_STUDENT_PASSWORD   Password for test users

Examples:
  # Set up 10 courses with 5 exercises each, then benchmark
  node benchmark-access-policies.mjs --setup-data --course-count=10

  # Benchmark only (data already exists), 50 iterations
  node benchmark-access-policies.mjs --iterations=50

  # Benchmark a specific course
  node benchmark-access-policies.mjs --course-id=42 --iterations=30

  # SQL query analysis (A/B comparison workflow):
  #   1. Start server:  ./gradlew bootRun 2>&1 | tee server.log
  #   2. On develop branch:
  node benchmark-access-policies.mjs --sql-analysis \\
      --label=develop --output=benchmark-results.txt --course-id=1
  #   3. Switch to DSL branch, restart server, then:
  node benchmark-access-policies.mjs --sql-analysis \\
      --label=dsl-branch --output=benchmark-results.txt --course-id=1
  #   4. Compare: cat benchmark-results.txt
`);
}

const args = parseArgs(process.argv.slice(2));

if (args.help) {
    printUsage();
    process.exit(0);
}

const config = {
    serverUrl: args['server-url'] || process.env.ARTEMIS_SERVER_URL || 'http://localhost:8080',
    adminUser: args['admin-user'] || process.env.ARTEMIS_ADMIN_USER || 'artemis_admin',
    adminPassword: args['admin-password'] || process.env.ARTEMIS_ADMIN_PASSWORD || 'artemis_admin',
    studentPassword: args['student-password'] || process.env.ARTEMIS_STUDENT_PASSWORD || 'Password123!',
    courseCount: parseInt(args['course-count'] || '10', 10),
    exercisesPerCourse: parseInt(args['exercises-per-course'] || '5', 10),
    iterations: parseInt(args['iterations'] || '20', 10),
    warmup: parseInt(args['warmup'] || '3', 10),
    setupData: args['setup-data'] === 'true',
    courseId: args['course-id'] ? parseInt(args['course-id'], 10) : null,
    sqlAnalysis: args['sql-analysis'] === 'true',
    serverLog: args['server-log'] || (args['sql-analysis'] === 'true' ? 'server.log' : null),
    outputFile: args['output'] || null,
    reportFile: args['report'] || null,
    label: args['label'] || new Date().toISOString(),
};

// ---------------------------------------------------------------------------
// Data setup
// ---------------------------------------------------------------------------

/**
 * Creates a course with the given index.
 * The course starts 10 minutes ago and ends in 30 days so it is "active".
 */
async function createCourse(client, index) {
    const timestamp = Date.now();
    const shortName = `bench${index}t${timestamp}`;

    const now = new Date();
    const startDate = new Date(now.getTime() - 10 * 60 * 1000);
    const endDate = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);

    const course = {
        title: `Benchmark Course ${index}`,
        shortName,
        startDate: startDate.toISOString(),
        endDate: endDate.toISOString(),
        courseInformationSharingConfiguration: 'COMMUNICATION_AND_MESSAGING',
        enrollmentEnabled: false,
        accuracyOfScores: 1,
        onlineCourse: false,
        timeZone: 'Europe/Berlin',
        // Complaint settings: disable all by setting counts and time to 0
        maxComplaints: 0,
        maxTeamComplaints: 0,
        maxComplaintTimeDays: 0,
        maxRequestMoreFeedbackTimeDays: 0,
        maxComplaintTextLimit: 0,
        maxComplaintResponseTextLimit: 0,
    };

    // The admin course creation endpoint expects multipart form data
    const boundary = '----FormBoundary' + Math.random().toString(36).substring(2);
    let body = `--${boundary}\r\n`;
    body += `Content-Disposition: form-data; name="course"\r\n`;
    body += `Content-Type: application/json\r\n\r\n`;
    body += JSON.stringify(course) + '\r\n';
    body += `--${boundary}--\r\n`;

    const response = await client.post('/api/core/admin/courses', body, {
        headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}` },
        contentType: 'multipart',
    });

    return response.data;
}

/**
 * Creates a test user and returns { login }.
 */
async function createUser(client, login, password) {
    const user = {
        activated: true,
        login,
        email: `${login}@bench.local`,
        firstName: 'Bench',
        lastName: login,
        langKey: 'en',
        password,
    };
    try {
        await client.post('/api/core/admin/users', user);
    } catch (error) {
        // User already exists — fine
        if (error.response?.status !== 400 && error.response?.status !== 409) {
            throw error;
        }
    }
    return { login };
}

async function addUserToCourse(client, courseId, group, username) {
    try {
        await client.post(`/api/core/courses/${courseId}/${group}/${username}`);
    } catch (error) {
        if (error.response?.status !== 400) throw error;
    }
}

/**
 * Creates a simple programming exercise in the given course.
 * Uses Java/Gradle, released now, due in 7 days.
 */
async function createProgrammingExercise(client, courseId, index) {
    const timestamp = Date.now();
    const exercise = {
        type: 'programming',
        title: `Bench Exercise ${index}`,
        shortName: `BenchEx${index}t${timestamp}`,
        course: { id: courseId },
        programmingLanguage: 'JAVA',
        projectType: 'PLAIN_GRADLE',
        allowOnlineEditor: true,
        allowOfflineIde: true,
        maxPoints: 100,
        assessmentType: 'AUTOMATIC',
        packageName: 'de.tum.bench',
        staticCodeAnalysisEnabled: false,
        sequentialTestRuns: false,
        releaseDate: new Date().toISOString(),
        dueDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
        problemStatement: '# Benchmark Exercise\nThis is a placeholder exercise for benchmarking.',
        buildConfig: {
            buildScript: '#!/bin/bash\necho "build"',
            checkoutSolutionRepository: false,
        },
    };

    const response = await client.post('/api/programming/programming-exercises/setup', exercise);
    return response.data;
}

/**
 * Sets up benchmark test data: multiple courses, each with programming exercises,
 * and a student enrolled in all courses.
 */
async function setupBenchmarkData(client) {
    console.log('='.repeat(60));
    console.log('Setting up benchmark data');
    console.log('='.repeat(60));
    console.log(`  Courses: ${config.courseCount}`);
    console.log(`  Programming exercises per course: ${config.exercisesPerCourse}`);

    // Create the benchmark student
    const studentLogin = 'bench_student';
    console.log(`\n  Creating benchmark user: ${studentLogin}`);
    await createUser(client, studentLogin, config.studentPassword);

    const courseIds = [];

    for (let c = 1; c <= config.courseCount; c++) {
        console.log(`\n  [${c}/${config.courseCount}] Creating course...`);
        const course = await createCourse(client, c);
        courseIds.push(course.id);
        console.log(`    Course ID: ${course.id} — "${course.title}"`);

        // Enroll student
        await addUserToCourse(client, course.id, 'students', studentLogin);
        console.log(`    Enrolled ${studentLogin}`);

        // Create programming exercises
        for (let e = 1; e <= config.exercisesPerCourse; e++) {
            try {
                const ex = await createProgrammingExercise(client, course.id, e);
                console.log(`    Exercise ${e}/${config.exercisesPerCourse}: ${ex.title} (ID: ${ex.id})`);
            } catch (error) {
                console.error(`    Failed to create exercise ${e}: ${error.message}`);
            }
        }
    }

    console.log(`\n  Data setup complete. Created ${courseIds.length} courses.`);
    console.log(`  Course IDs: [${courseIds.join(', ')}]`);

    return { studentLogin, courseIds };
}

// ---------------------------------------------------------------------------
// Entity snapshot
// ---------------------------------------------------------------------------

/**
 * Collects a snapshot of relevant entity counts from the database at the time
 * the benchmark runs. This gives context for interpreting benchmark results:
 * - Total courses tells you the SQL query scale for the all-courses endpoint
 * - Active/visible courses narrow down what the student endpoint actually processes
 * - Exercise counts reflect the work done by programming exercise visibility filtering
 *
 * Uses:
 *   GET /api/core/courses          (as admin) — all courses regardless of visibility
 *   GET /api/core/courses/for-dashboard  (as student) — courses + exercises visible to bench_student
 *
 * @param {HttpClient} adminClient   - authenticated admin HTTP client
 * @param {HttpClient} studentClient - authenticated student HTTP client
 * @returns {object} snapshot with counts
 */
async function collectEntitySnapshot(adminClient, studentClient) {
    console.log('\n' + '='.repeat(60));
    console.log('DATABASE ENTITY SNAPSHOT');
    console.log('='.repeat(60));

    let totalCourses = '?';
    let activeCourses = '?';
    let visibleCourses = '?';
    let totalVisibleExercises = '?';
    let totalVisibleProgrammingExercises = '?';

    // Admin: fetch all courses to get total and active counts
    try {
        const adminCoursesResponse = await adminClient.get('/api/core/courses');
        const allCourses = Array.isArray(adminCoursesResponse.data) ? adminCoursesResponse.data : [];
        totalCourses = allCourses.length;

        const now = new Date();
        activeCourses = allCourses.filter(c => {
            const start = c.startDate ? new Date(c.startDate) : null;
            const end = c.endDate ? new Date(c.endDate) : null;
            return (!start || start <= now) && (!end || end >= now);
        }).length;
    } catch (error) {
        console.error(`  WARNING: Could not fetch admin course list: ${error.message}`);
    }

    // Student: fetch for-dashboard to count visible courses and exercises
    try {
        const dashboardResponse = await studentClient.get('/api/core/courses/for-dashboard');
        const dashboardData = dashboardResponse.data;
        const visibleCourseDTOs = dashboardData?.courses ? [...dashboardData.courses] : [];
        visibleCourses = visibleCourseDTOs.length;

        let exerciseCount = 0;
        let programmingCount = 0;
        for (const dto of visibleCourseDTOs) {
            const exercises = dto.course?.exercises || [];
            exerciseCount += exercises.length;
            programmingCount += exercises.filter(e => e.type === 'programming').length;
        }
        totalVisibleExercises = exerciseCount;
        totalVisibleProgrammingExercises = programmingCount;
    } catch (error) {
        console.error(`  WARNING: Could not fetch student dashboard: ${error.message}`);
    }

    console.log(`  Total courses in DB                : ${totalCourses}`);
    console.log(`  Active courses (now in date range)  : ${activeCourses}`);
    console.log(`  Courses visible to bench_student    : ${visibleCourses}`);
    console.log(`  Exercises visible to bench_student  : ${totalVisibleExercises}`);
    console.log(`  └─ of which programming exercises   : ${totalVisibleProgrammingExercises}`);

    return {
        totalCourses,
        activeCourses,
        visibleCourses,
        totalVisibleExercises,
        totalVisibleProgrammingExercises,
    };
}

// ---------------------------------------------------------------------------
// Benchmark helpers
// ---------------------------------------------------------------------------

function computeStats(timings) {
    const sorted = [...timings].sort((a, b) => a - b);
    const sum = sorted.reduce((a, b) => a + b, 0);
    const mean = sum / sorted.length;
    const median = sorted.length % 2 === 0
        ? (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2
        : sorted[Math.floor(sorted.length / 2)];
    const min = sorted[0];
    const max = sorted[sorted.length - 1];
    const p90 = sorted[Math.floor(sorted.length * 0.9)];
    const p95 = sorted[Math.floor(sorted.length * 0.95)];
    const stddev = Math.sqrt(sorted.reduce((acc, v) => acc + (v - mean) ** 2, 0) / sorted.length);

    return { mean, median, min, max, p90, p95, stddev, count: sorted.length };
}

function formatMs(ms) {
    return ms.toFixed(2) + ' ms';
}

function printStats(label, stats) {
    console.log(`\n  ${label}`);
    console.log(`  ${'—'.repeat(50)}`);
    console.log(`  Iterations : ${stats.count}`);
    console.log(`  Mean       : ${formatMs(stats.mean)}`);
    console.log(`  Median     : ${formatMs(stats.median)}`);
    console.log(`  Min        : ${formatMs(stats.min)}`);
    console.log(`  Max        : ${formatMs(stats.max)}`);
    console.log(`  P90        : ${formatMs(stats.p90)}`);
    console.log(`  P95        : ${formatMs(stats.p95)}`);
    console.log(`  Std Dev    : ${formatMs(stats.stddev)}`);
}

/**
 * Run a single benchmark: call `requestFn` `warmup + iterations` times,
 * measure the last `iterations` calls.
 */
async function runBenchmark(label, requestFn, warmup, iterations) {
    console.log(`\n  Running: ${label}`);
    console.log(`  Warmup: ${warmup}, Measured iterations: ${iterations}`);

    // Warmup (not measured)
    for (let i = 0; i < warmup; i++) {
        await requestFn();
    }

    // Measured iterations
    const timings = [];
    for (let i = 0; i < iterations; i++) {
        const start = performance.now();
        await requestFn();
        const elapsed = performance.now() - start;
        timings.push(elapsed);

        // Print progress every 10 iterations
        if ((i + 1) % 10 === 0 || i === iterations - 1) {
            process.stdout.write(`  Progress: ${i + 1}/${iterations} (last: ${formatMs(elapsed)})\r`);
        }
    }
    console.log(''); // newline after progress

    return timings;
}

// ---------------------------------------------------------------------------
// SQL query analysis helpers
// ---------------------------------------------------------------------------

/**
 * Read the server log file and return its current size (byte offset).
 * We use this as a "bookmark" so we can later read only the lines appended
 * after a request was made.
 */
function getLogFileSize(logPath) {
    try {
        const content = readFileSync(logPath, 'utf-8');
        return content.length;
    } catch (error) {
        console.error(`ERROR: Server log file not found at: ${logPath}`);
        console.error('');
        console.error('Option A — Pipe server output to a file:');
        console.error('  ./gradlew bootRun 2>&1 | tee server.log');
        console.error('');
        console.error('Option B — Configure Spring Boot to write a log file.');
        console.error('  Add to application-local.yml:');
        console.error('    logging:');
        console.error('      file:');
        console.error('        name: server.log');
        console.error('      level:');
        console.error('        org.hibernate.SQL: DEBUG');
        console.error('        org.hibernate.engine.internal.StatisticalLoggingSessionEventListener: INFO');
        console.error('');
        console.error('  Note: show-sql uses System.out (not captured by logging.file.name).');
        console.error('  Option B requires org.hibernate.SQL: DEBUG instead of / in addition to show-sql.');
        console.error('');
        console.error('Then restart the server and re-run this benchmark.');
        process.exit(1);
    }
}

/**
 * Read lines appended to the log file after the given character offset.
 */
function readLogSince(logPath, offset) {
    const content = readFileSync(logPath, 'utf-8');
    return content.substring(offset);
}

/**
 * Count SQL queries in a Hibernate show-sql log snippet and extract
 * individual SQL statements and Hibernate session metrics.
 *
 * With format_sql=true, Hibernate outputs multi-line formatted SQL.
 * Each statement starts with one of: select, insert, update, delete
 * at the beginning of a line (after optional whitespace).
 *
 * With format_sql=false, each SQL statement is a single line starting
 * with "Hibernate: select/insert/update/delete".
 *
 * We count both patterns.
 *
 * If hibernate.generate_statistics=true, the log also contains a
 * "Session Metrics { ... }" block with aggregate JDBC timing that we parse.
 */
function countSqlQueries(logSnippet) {
    const lines = logSnippet.split('\n');
    let totalQueries = 0;
    let selects = 0;
    let inserts = 0;
    let updates = 0;
    let deletes = 0;
    const uniqueQueries = new Set();

    for (const line of lines) {
        const trimmed = line.trim().toLowerCase();
        // Match "Hibernate: select ..." or standalone "select ..." at start of formatted block
        const isHibernateLine = trimmed.startsWith('hibernate:');
        // Also match logging-framework format: "... org.hibernate.SQL                        : select ..."
        const loggingMatch = !isHibernateLine && /org\.hibernate\.sql\s*:\s*(.*)/.exec(trimmed);
        let sqlStart;
        if (isHibernateLine) {
            sqlStart = trimmed.substring('hibernate:'.length).trim();
        } else if (loggingMatch) {
            sqlStart = loggingMatch[1].trim();
        } else {
            sqlStart = trimmed;
        }

        let matched = false;
        if (/^select\b/.test(sqlStart)) {
            selects++;
            matched = true;
        } else if (/^insert\b/.test(sqlStart)) {
            inserts++;
            matched = true;
        } else if (/^update\b/.test(sqlStart)) {
            updates++;
            matched = true;
        } else if (/^delete\b/.test(sqlStart)) {
            deletes++;
            matched = true;
        }

        if (matched) {
            totalQueries++;
            // Normalize for uniqueness: take the first 200 chars of the SQL
            uniqueQueries.add(sqlStart.substring(0, 200));
        }
    }

    // ---------------------------------------------------------------
    // Extract individual SQL statements (full text, preserving formatting)
    // Split on "Hibernate:" markers to get each SQL block
    // ---------------------------------------------------------------
    const rawQueries = extractRawSqlStatements(logSnippet);

    // ---------------------------------------------------------------
    // Parse Hibernate Session Metrics (requires generate_statistics=true)
    // Example block:
    //   Session Metrics {
    //       12345 nanoseconds spent preparing 5 JDBC statements;
    //       67890 nanoseconds spent executing 5 JDBC statements;
    //       ...
    //   }
    // ---------------------------------------------------------------
    const sessionMetrics = parseSessionMetrics(logSnippet);

    return { totalQueries, selects, inserts, updates, deletes, uniqueQueries: uniqueQueries.size, rawQueries, sessionMetrics };
}

/**
 * Extract individual SQL statements from a Hibernate log snippet.
 * Handles both formatted (multi-line) and unformatted (single-line) output.
 * Returns an array of { type, sql } objects.
 */
function extractRawSqlStatements(logSnippet) {
    const queries = [];
    // Split on "Hibernate:" or "org.hibernate.SQL ... :" markers to isolate each SQL block
    const parts = logSnippet.split(/(?:Hibernate:\s*|org\.hibernate\.SQL\s+:\s*)/);

    for (let i = 1; i < parts.length; i++) {
        const block = parts[i];
        // Collect SQL lines: continuation lines are indented or contain SQL keywords
        const blockLines = block.split('\n');
        const sqlLines = [];

        for (const line of blockLines) {
            const trimmed = line.trim();
            if (!trimmed) continue;
            const lower = trimmed.toLowerCase();

            // SQL continuation: indented lines, SQL keywords, or looks like SQL
            const isSqlLine = /^\s{2,}/.test(line) // indented
                || /^(select|insert|update|delete|from|where|and|or|join|left|inner|right|cross|outer|on|group|order|having|limit|offset|values|set|into|case|when|then|else|end|exists|not|in|like|between|as|distinct|union|all|fetch|with|returning|using)\b/i.test(trimmed)
                || /^[a-z0-9_.,()? =<>!'"*+\-\[\]%]+$/i.test(trimmed);

            if (sqlLines.length === 0) {
                // First line must start with a SQL keyword
                if (/^(select|insert|update|delete)\b/i.test(lower)) {
                    sqlLines.push(trimmed);
                } else {
                    break; // Not a SQL block
                }
            } else if (isSqlLine) {
                sqlLines.push(trimmed);
            } else {
                break; // End of SQL block (hit a log line)
            }
        }

        if (sqlLines.length > 0) {
            const sql = sqlLines.join('\n');
            const type = sqlLines[0].trim().split(/\s/)[0].toUpperCase();
            queries.push({ type, sql });
        }
    }

    return queries;
}

/**
 * Parse Hibernate Session Metrics from a log snippet.
 * Requires hibernate.generate_statistics=true to be present in the log.
 * Returns null if no metrics block is found.
 */
function parseSessionMetrics(logSnippet) {
    const metricsMatch = logSnippet.match(/Session Metrics \{([\s\S]*?)\}/);
    if (!metricsMatch) return null;

    const metricsBlock = metricsMatch[1];
    const extract = (pattern) => {
        const m = metricsBlock.match(pattern);
        return m ? { ns: parseInt(m[1], 10), count: parseInt(m[2], 10) } : null;
    };

    return {
        acquiring: extract(/(\d+) nanoseconds spent acquiring (\d+) JDBC connections/),
        releasing: extract(/(\d+) nanoseconds spent releasing (\d+) JDBC connections/),
        preparing: extract(/(\d+) nanoseconds spent preparing (\d+) JDBC statements/),
        executing: extract(/(\d+) nanoseconds spent executing (\d+) JDBC statements/),
        batches: extract(/(\d+) nanoseconds spent executing (\d+) JDBC batches/),
        flushes: extract(/(\d+) nanoseconds spent executing (\d+) flushes/),
    };
}

function formatNsAsMs(ns) {
    return (ns / 1_000_000).toFixed(2) + ' ms';
}

/**
 * Run a single request while capturing the server log, then count SQL queries.
 */
async function runWithSqlCapture(label, requestFn, logPath) {
    // Record log position before the request
    const offsetBefore = getLogFileSize(logPath);

    // Make the request
    const start = performance.now();
    await requestFn();
    const elapsed = performance.now() - start;

    // Small delay to allow log flush
    await new Promise(resolve => setTimeout(resolve, 200));

    // Read the log lines generated during the request
    const logSnippet = readLogSince(logPath, offsetBefore);
    const sqlStats = countSqlQueries(logSnippet);

    return { elapsed, sqlStats, logSnippet };
}

/**
 * Append a result record to the output file (if configured).
 * Format: one JSON object per line for easy post-processing.
 */
function appendResult(record) {
    if (!config.outputFile) return;
    appendFileSync(config.outputFile, JSON.stringify(record) + '\n', 'utf-8');
}

/**
 * Append SQL analysis tables (query counts, Hibernate timing, raw SQL) to a Markdown lines array.
 */
function appendSqlAnalysisSection(lines, sqlResult) {
    const stats = sqlResult.sqlStats;

    // Summary table
    lines.push(`| Metric | Value |`);
    lines.push(`|--------|-------|`);
    lines.push(`| Response time | ${formatMs(sqlResult.elapsed)} |`);
    lines.push(`| Total queries | ${stats.totalQueries} |`);
    lines.push(`| SELECTs | ${stats.selects} |`);
    lines.push(`| INSERTs | ${stats.inserts} |`);
    lines.push(`| UPDATEs | ${stats.updates} |`);
    lines.push(`| DELETEs | ${stats.deletes} |`);
    lines.push(`| Unique queries | ${stats.uniqueQueries} |`);

    // Hibernate Session Metrics (if available)
    const metrics = stats.sessionMetrics;
    if (metrics) {
        lines.push('');
        lines.push(`#### Hibernate JDBC Timing`);
        lines.push('');
        lines.push(`| Phase | Statements | Time |`);
        lines.push(`|-------|-----------|------|`);
        if (metrics.preparing) {
            lines.push(`| Preparing | ${metrics.preparing.count} | ${formatNsAsMs(metrics.preparing.ns)} |`);
        }
        if (metrics.executing) {
            lines.push(`| Executing | ${metrics.executing.count} | ${formatNsAsMs(metrics.executing.ns)} |`);
        }
        if (metrics.preparing && metrics.executing) {
            const totalSqlMs = (metrics.preparing.ns + metrics.executing.ns) / 1_000_000;
            const pct = sqlResult.elapsed > 0 ? ((totalSqlMs / sqlResult.elapsed) * 100).toFixed(1) : '?';
            lines.push(`| **Total SQL** | | **${totalSqlMs.toFixed(2)} ms** (${pct}% of response time) |`);
        }
    }

    // Raw SQL queries
    const rawQueries = stats.rawQueries;
    if (rawQueries && rawQueries.length > 0) {
        lines.push('');
        lines.push(`#### Raw SQL Queries (${rawQueries.length})`);
        lines.push('');
        for (let i = 0; i < rawQueries.length; i++) {
            const q = rawQueries[i];
            lines.push(`<details>`);
            lines.push(`<summary>${i + 1}. ${q.type}</summary>`);
            lines.push('');
            lines.push('```sql');
            lines.push(q.sql);
            lines.push('```');
            lines.push('');
            lines.push(`</details>`);
            lines.push('');
        }
    }
}

/**
 * Write a formatted Markdown report of the benchmark results.
 */
function writeMarkdownReport({ mode, snapshot, allCoursesStats, singleCourseStats, courseId, allCoursesSql, singleCourseSql }) {
    if (!config.reportFile) return;

    const lines = [];
    const ts = new Date().toISOString();

    lines.push(`# Access Rights DSL — Benchmark Report`);
    lines.push('');
    lines.push(`| Property | Value |`);
    lines.push(`|----------|-------|`);
    lines.push(`| **Label** | ${config.label} |`);
    lines.push(`| **Date** | ${ts} |`);
    lines.push(`| **Server** | ${config.serverUrl} |`);
    lines.push(`| **Mode** | ${mode === 'sql-analysis' ? 'SQL Analysis' : 'Response Time'} |`);
    if (mode !== 'sql-analysis') {
        lines.push(`| **Iterations** | ${config.iterations} (+ ${config.warmup} warmup) |`);
    }
    lines.push('');

    // Entity snapshot
    lines.push(`## Database Entity Snapshot`);
    lines.push('');
    lines.push(`| Metric | Count |`);
    lines.push(`|--------|-------|`);
    lines.push(`| Total courses in DB | ${snapshot.totalCourses} |`);
    lines.push(`| Active courses (in date range) | ${snapshot.activeCourses} |`);
    lines.push(`| Courses visible to bench_student | ${snapshot.visibleCourses} |`);
    lines.push(`| Exercises visible to bench_student | ${snapshot.totalVisibleExercises} |`);
    lines.push(`| — of which programming exercises | ${snapshot.totalVisibleProgrammingExercises} |`);
    lines.push('');

    if (mode === 'sql-analysis') {
        // SQL Analysis report
        lines.push(`## SQL Query Analysis`);
        lines.push('');

        lines.push(`### GET /api/core/courses/for-dashboard`);
        lines.push('');
        if (allCoursesSql) {
            appendSqlAnalysisSection(lines, allCoursesSql);
        }
        lines.push('');

        if (singleCourseSql && courseId) {
            lines.push(`### GET /api/core/courses/${courseId}/for-dashboard`);
            lines.push('');
            appendSqlAnalysisSection(lines, singleCourseSql);
            lines.push('');
        }
    } else {
        // Response Time report
        lines.push(`## Response Time Results`);
        lines.push('');

        lines.push(`### GET /api/core/courses/for-dashboard`);
        lines.push('');
        if (allCoursesStats) {
            lines.push(`| Metric | Value |`);
            lines.push(`|--------|-------|`);
            lines.push(`| Iterations | ${allCoursesStats.count} |`);
            lines.push(`| Mean | ${formatMs(allCoursesStats.mean)} |`);
            lines.push(`| Median | ${formatMs(allCoursesStats.median)} |`);
            lines.push(`| Min | ${formatMs(allCoursesStats.min)} |`);
            lines.push(`| Max | ${formatMs(allCoursesStats.max)} |`);
            lines.push(`| P90 | ${formatMs(allCoursesStats.p90)} |`);
            lines.push(`| P95 | ${formatMs(allCoursesStats.p95)} |`);
            lines.push(`| Std Dev | ${formatMs(allCoursesStats.stddev)} |`);
        }
        lines.push('');

        if (singleCourseStats && courseId) {
            lines.push(`### GET /api/core/courses/${courseId}/for-dashboard`);
            lines.push('');
            lines.push(`| Metric | Value |`);
            lines.push(`|--------|-------|`);
            lines.push(`| Iterations | ${singleCourseStats.count} |`);
            lines.push(`| Mean | ${formatMs(singleCourseStats.mean)} |`);
            lines.push(`| Median | ${formatMs(singleCourseStats.median)} |`);
            lines.push(`| Min | ${formatMs(singleCourseStats.min)} |`);
            lines.push(`| Max | ${formatMs(singleCourseStats.max)} |`);
            lines.push(`| P90 | ${formatMs(singleCourseStats.p90)} |`);
            lines.push(`| P95 | ${formatMs(singleCourseStats.p95)} |`);
            lines.push(`| Std Dev | ${formatMs(singleCourseStats.stddev)} |`);
            lines.push('');
        }
    }

    writeFileSync(config.reportFile, lines.join('\n') + '\n', 'utf-8');
    console.log(`Markdown report written to: ${config.reportFile}`);
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
    console.log('='.repeat(60));
    console.log('Access Rights DSL — Performance Benchmark');
    console.log('='.repeat(60));
    console.log(`Server URL : ${config.serverUrl}`);
    console.log(`Mode       : ${config.sqlAnalysis ? 'SQL Analysis' : 'Response Time'}`);
    if (!config.sqlAnalysis) {
        console.log(`Iterations : ${config.iterations} (+ ${config.warmup} warmup)`);
    }
    if (args['label']) {
        console.log(`Label      : ${config.label}`);
    }
    console.log();

    // Validate SQL analysis prerequisites
    if (config.sqlAnalysis && !existsSync(config.serverLog)) {
        console.error(`ERROR: Server log file not found at: ${config.serverLog}`);
        console.error('');
        console.error('Option A — Pipe server output to a file:');
        console.error('  ./gradlew bootRun 2>&1 | tee server.log');
        console.error('');
        console.error('Option B — Configure Spring Boot to write a log file.');
        console.error('  Add to application-local.yml:');
        console.error('    logging:');
        console.error('      file:');
        console.error('        name: server.log');
        console.error('      level:');
        console.error('        org.hibernate.SQL: DEBUG');
        console.error('        org.hibernate.engine.internal.StatisticalLoggingSessionEventListener: INFO');
        console.error('');
        console.error('  Note: show-sql uses System.out (not captured by logging.file.name).');
        console.error('  Option B requires org.hibernate.SQL: DEBUG instead of / in addition to show-sql.');
        console.error('');
        console.error('Then restart the server and re-run this benchmark.');
        process.exit(1);
    }

    const adminClient = new HttpClient(config.serverUrl);

    // Authenticate as admin
    console.log('Authenticating as admin...');
    await authenticate(adminClient, config.adminUser, config.adminPassword);

    // Optional: set up test data
    let benchData = null;
    if (config.setupData) {
        benchData = await setupBenchmarkData(adminClient);
    }

    // Determine which student to use
    const studentLogin = benchData?.studentLogin || 'bench_student';

    // Authenticate as the benchmark student
    const studentClient = new HttpClient(config.serverUrl);
    console.log(`\nAuthenticating as benchmark student (${studentLogin})...`);
    try {
        await authenticate(studentClient, studentLogin, config.studentPassword);
    } catch (error) {
        console.error(`Failed to authenticate as ${studentLogin}: ${error.message}`);
        console.error('Make sure the user exists. Run with --setup-data to create test data first.');
        process.exit(1);
    }

    // Determine course ID for single-course endpoint
    let courseId = config.courseId;
    if (!courseId && benchData?.courseIds?.length > 0) {
        courseId = benchData.courseIds[0];
    }

    // Auto-discover a courseId if still not set — pick the first visible course
    if (!courseId) {
        try {
            const dashboardResp = await studentClient.get('/api/core/courses/for-dashboard');
            const courses = dashboardResp.data?.courses || [];
            if (courses.length > 0) {
                courseId = courses[0].course?.id;
                if (courseId) {
                    console.log(`Auto-discovered course ID ${courseId} ("${courses[0].course?.title}") for single-course benchmark.`);
                }
            }
        } catch {
            // Non-fatal — single-course benchmark will be skipped
        }
        if (!courseId) {
            console.log('WARNING: Could not auto-discover a course ID. Single-course benchmark will be skipped.');
            console.log('  Use --course-id=<id> or --setup-data to enable it.');
        }
    }

    // Collect entity snapshot before benchmarking
    const snapshot = await collectEntitySnapshot(adminClient, studentClient);

    // -------------------------------------------------------------------
    // SQL ANALYSIS MODE
    // -------------------------------------------------------------------
    if (config.sqlAnalysis) {
        await runSqlAnalysisMode(studentClient, courseId, snapshot);
        return;
    }

    // -------------------------------------------------------------------
    // RESPONSE TIME BENCHMARK MODE (default)
    // -------------------------------------------------------------------
    console.log('\n' + '='.repeat(60));
    console.log('RESPONSE TIME BENCHMARK');
    console.log('='.repeat(60));
    console.log();

    // -----------------------------------------------------------------------
    // Benchmark 1: GET /api/core/courses/for-dashboard (all courses)
    //
    // This endpoint uses:
    //   - PolicyBasedCourseSpecs.withVisibilityAccessAndActive() -> SQL from DSL
    //     DSL: memberOfGroup(Course::getStudentGroupName).and(hasStarted(Course::getStartDate))
    //   - ProgrammingExerciseVisibleService (exercise-level DSL filtering)
    //     DSL: memberOfGroup(...).and(hasStarted(ProgrammingExercise::getReleaseDate))
    // -----------------------------------------------------------------------
    const allCoursesTimings = await runBenchmark(
        'GET /api/core/courses/for-dashboard (all courses — policy-based SQL + exercise filtering)',
        async () => {
            await studentClient.get('/api/core/courses/for-dashboard');
        },
        config.warmup,
        config.iterations,
    );
    const allCoursesStats = computeStats(allCoursesTimings);
    printStats('Endpoint: GET /api/core/courses/for-dashboard', allCoursesStats);

    // -----------------------------------------------------------------------
    // Benchmark 2: GET /api/core/courses/{courseId}/for-dashboard (single course)
    //
    // This endpoint uses:
    //   - @EnforceAccessPolicy(CourseVisibilityPolicy) -> AOP-based authorization
    //     DSL: memberOfGroup(Course::getStudentGroupName).and(hasStarted(Course::getStartDate))
    //   - PolicyEngine.isAllowed() for runtime course visibility check
    //   - ProgrammingExerciseVisibleService (exercise-level DSL filtering)
    //     DSL: memberOfGroup(...).and(hasStarted(ProgrammingExercise::getReleaseDate))
    // -----------------------------------------------------------------------
    let singleCourseStats = null;
    if (courseId) {
        const singleCourseTimings = await runBenchmark(
            `GET /api/core/courses/${courseId}/for-dashboard (single course — AOP policy + runtime check)`,
            async () => {
                await studentClient.get(`/api/core/courses/${courseId}/for-dashboard`);
            },
            config.warmup,
            config.iterations,
        );
        singleCourseStats = computeStats(singleCourseTimings);
        printStats(`Endpoint: GET /api/core/courses/${courseId}/for-dashboard`, singleCourseStats);
    } else {
        console.log('\n  Skipping single-course benchmark (no --course-id provided and --setup-data not used).');
        console.log('  Use --course-id=<id> or --setup-data to enable this benchmark.');
    }

    // -----------------------------------------------------------------------
    // Summary
    // -----------------------------------------------------------------------
    console.log('\n' + '='.repeat(60));
    console.log('SUMMARY');
    console.log('='.repeat(60));
    console.log();
    console.log('All Courses Dashboard (policy-based SQL generation from DSL):');
    console.log(`  Mean: ${formatMs(allCoursesStats.mean)} | Median: ${formatMs(allCoursesStats.median)} | P95: ${formatMs(allCoursesStats.p95)}`);

    if (singleCourseStats) {
        console.log(`\nSingle Course Dashboard (AOP @EnforceAccessPolicy + runtime PolicyEngine):`);
        console.log(`  Mean: ${formatMs(singleCourseStats.mean)} | Median: ${formatMs(singleCourseStats.median)} | P95: ${formatMs(singleCourseStats.p95)}`);
    }

    // Write results to output file
    appendResult({
        label: config.label,
        timestamp: new Date().toISOString(),
        mode: 'response-time',
        snapshot,
        allCourses: allCoursesStats,
        singleCourse: singleCourseStats,
        config: { iterations: config.iterations, warmup: config.warmup, courseCount: config.courseCount },
    });

    // Write markdown report
    writeMarkdownReport({
        mode: 'response-time',
        snapshot,
        allCoursesStats,
        singleCourseStats,
        courseId,
    });

    console.log('\nBenchmark complete.');
    if (config.outputFile) {
        console.log(`Results appended to: ${config.outputFile}`);
    }
}

/**
 * SQL Analysis Mode: fires each endpoint exactly once (with a warmup request first),
 * captures the server log window, and counts SQL queries.
 */
async function runSqlAnalysisMode(studentClient, courseId, snapshot) {
    console.log('\n' + '='.repeat(60));
    console.log('SQL QUERY ANALYSIS');
    console.log('='.repeat(60));
    console.log(`Server log : ${config.serverLog}`);
    console.log(`Label      : ${config.label}`);
    console.log();
    console.log('Running one warmup request per endpoint, then one measured request.');
    console.log('Make sure the server was started with these settings in application-local.yml:');
    console.log('  spring.jpa.show-sql: true');
    console.log('  spring.jpa.properties.hibernate.format_sql: true          (for readable SQL)');
    console.log('  spring.jpa.properties.hibernate.generate_statistics: true (for SQL timing)');
    console.log('  logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener: INFO');
    console.log('    (required if logback-spring.xml sets org.hibernate to WARN)');
    console.log();

    // --- Endpoint 1: All courses dashboard ---
    console.log('-'.repeat(60));
    console.log('Endpoint 1: GET /api/core/courses/for-dashboard');
    console.log('-'.repeat(60));

    // Warmup (populates caches, establishes session — not measured)
    await studentClient.get('/api/core/courses/for-dashboard');

    const allCoursesResult = await runWithSqlCapture(
        'GET /api/core/courses/for-dashboard',
        () => studentClient.get('/api/core/courses/for-dashboard'),
        config.serverLog,
    );

    printSqlAnalysis('GET /api/core/courses/for-dashboard', allCoursesResult);

    // --- Endpoint 2: Single course dashboard ---
    let singleCourseResult = null;
    if (courseId) {
        console.log('\n' + '-'.repeat(60));
        console.log(`Endpoint 2: GET /api/core/courses/${courseId}/for-dashboard`);
        console.log('-'.repeat(60));

        // Warmup
        await studentClient.get(`/api/core/courses/${courseId}/for-dashboard`);

        singleCourseResult = await runWithSqlCapture(
            `GET /api/core/courses/${courseId}/for-dashboard`,
            () => studentClient.get(`/api/core/courses/${courseId}/for-dashboard`),
            config.serverLog,
        );

        printSqlAnalysis(`GET /api/core/courses/${courseId}/for-dashboard`, singleCourseResult);
    } else {
        console.log('\n  Skipping single-course endpoint (no --course-id).');
    }

    // --- Summary ---
    console.log('\n' + '='.repeat(60));
    console.log('SQL ANALYSIS SUMMARY');
    console.log('='.repeat(60));
    console.log(`Label: ${config.label}`);
    console.log();
    console.log(`  GET /api/core/courses/for-dashboard`);
    console.log(`    Response time : ${formatMs(allCoursesResult.elapsed)}`);
    console.log(`    Total queries : ${allCoursesResult.sqlStats.totalQueries}`);
    console.log(`    SELECTs       : ${allCoursesResult.sqlStats.selects}`);
    console.log(`    Unique queries: ${allCoursesResult.sqlStats.uniqueQueries}`);
    if (allCoursesResult.sqlStats.sessionMetrics?.executing) {
        const m = allCoursesResult.sqlStats.sessionMetrics;
        const totalSqlMs = ((m.preparing?.ns || 0) + m.executing.ns) / 1_000_000;
        console.log(`    SQL exec time : ${totalSqlMs.toFixed(2)} ms`);
    }

    if (singleCourseResult) {
        console.log();
        console.log(`  GET /api/core/courses/${courseId}/for-dashboard`);
        console.log(`    Response time : ${formatMs(singleCourseResult.elapsed)}`);
        console.log(`    Total queries : ${singleCourseResult.sqlStats.totalQueries}`);
        console.log(`    SELECTs       : ${singleCourseResult.sqlStats.selects}`);
        console.log(`    Unique queries: ${singleCourseResult.sqlStats.uniqueQueries}`);
        if (singleCourseResult.sqlStats.sessionMetrics?.executing) {
            const m = singleCourseResult.sqlStats.sessionMetrics;
            const totalSqlMs = ((m.preparing?.ns || 0) + m.executing.ns) / 1_000_000;
            console.log(`    SQL exec time : ${totalSqlMs.toFixed(2)} ms`);
        }
    }

    // Write results to output file (exclude raw query text from JSON for compactness)
    const stripRaw = (stats) => {
        const { rawQueries, ...rest } = stats;
        return { ...rest, rawQueryCount: rawQueries?.length || 0 };
    };
    appendResult({
        label: config.label,
        timestamp: new Date().toISOString(),
        mode: 'sql-analysis',
        snapshot,
        allCourses: {
            responseTimeMs: allCoursesResult.elapsed,
            ...stripRaw(allCoursesResult.sqlStats),
        },
        singleCourse: singleCourseResult ? {
            responseTimeMs: singleCourseResult.elapsed,
            ...stripRaw(singleCourseResult.sqlStats),
        } : null,
        courseId,
    });

    // Write markdown report
    writeMarkdownReport({
        mode: 'sql-analysis',
        snapshot,
        courseId,
        allCoursesSql: allCoursesResult,
        singleCourseSql: singleCourseResult,
    });

    if (config.outputFile) {
        console.log(`\nResults appended to: ${config.outputFile}`);
    }
    console.log('\nSQL analysis complete.');
}

function printSqlAnalysis(label, result) {
    console.log(`\n  Response time: ${formatMs(result.elapsed)}`);
    console.log(`  SQL queries executed:`);
    console.log(`    Total   : ${result.sqlStats.totalQueries}`);
    console.log(`    SELECTs : ${result.sqlStats.selects}`);
    console.log(`    INSERTs : ${result.sqlStats.inserts}`);
    console.log(`    UPDATEs : ${result.sqlStats.updates}`);
    console.log(`    DELETEs : ${result.sqlStats.deletes}`);
    console.log(`    Unique  : ${result.sqlStats.uniqueQueries}`);

    // Hibernate Session Metrics (requires generate_statistics=true)
    const metrics = result.sqlStats.sessionMetrics;
    if (metrics) {
        console.log(`\n  Hibernate Session Metrics (JDBC timing):`);
        if (metrics.preparing) {
            console.log(`    Preparing ${metrics.preparing.count} statements : ${formatNsAsMs(metrics.preparing.ns)}`);
        }
        if (metrics.executing) {
            console.log(`    Executing ${metrics.executing.count} statements : ${formatNsAsMs(metrics.executing.ns)}`);
        }
        if (metrics.preparing && metrics.executing) {
            const totalSqlMs = (metrics.preparing.ns + metrics.executing.ns) / 1_000_000;
            const pct = result.elapsed > 0 ? ((totalSqlMs / result.elapsed) * 100).toFixed(1) : '?';
            console.log(`    Total SQL time              : ${totalSqlMs.toFixed(2)} ms (${pct}% of response time)`);
        }
    }

    // Raw SQL queries
    const rawQueries = result.sqlStats.rawQueries;
    if (rawQueries && rawQueries.length > 0) {
        console.log(`\n  Individual SQL queries (${rawQueries.length}):`);
        for (let i = 0; i < rawQueries.length; i++) {
            const q = rawQueries[i];
            // Truncate display to first 3 lines for console readability
            const preview = q.sql.split('\n').slice(0, 3).join(' ').substring(0, 120);
            console.log(`    [${i + 1}] ${q.type}: ${preview}${q.sql.length > 120 ? '...' : ''}`);
        }
    }

    if (result.sqlStats.totalQueries === 0) {
        console.log();
        console.log('  WARNING: No SQL queries detected. Possible causes:');
        console.log('    - show-sql is not enabled in application-local.yml');
        console.log('    - The server log file path is incorrect');
        console.log('    - The log file is not being flushed fast enough (try adding a small delay)');
    }

    if (!metrics) {
        console.log();
        console.log('  NOTE: No Hibernate Session Metrics found. For SQL execution timing:');
        console.log('    1. Enable in application-local.yml:');
        console.log('       spring.jpa.properties.hibernate.generate_statistics: true');
        console.log('    2. The StatisticalLoggingSessionEventListener logger must be at INFO or lower.');
        console.log('       If logback-spring.xml sets org.hibernate to WARN, metrics are suppressed.');
        console.log('       Add to application-local.yml:');
        console.log('         logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener: INFO');
    }
}

main().catch(error => {
    console.error('Benchmark failed:', error.message);
    process.exit(1);
});
