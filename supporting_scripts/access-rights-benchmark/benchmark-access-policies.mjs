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
 *   --course-id=<id>           Benchmark a specific course (for single-course endpoint)
 *   --sql-analysis             SQL query analysis mode: run each endpoint once, capture
 *                              server log, and count SQL queries per request.
 *                              Requires --server-log=<path> to the Artemis console log file.
 *   --server-log=<path>        Path to the server log file (used with --sql-analysis).
 *                              Tip: start Artemis with  ./gradlew bootRun 2>&1 | tee server.log
 *   --output=<path>            Write results to a file (append mode) for later comparison
 *   --label=<name>             Label for this run (e.g. "develop" or "dsl-branch")
 *   --help                     Show help
 */

// ---------------------------------------------------------------------------
// Imports – reuse the HTTP client and auth helpers from the setup-course lib
// ---------------------------------------------------------------------------
import { readFileSync, appendFileSync } from 'node:fs';
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
  --course-id=<id>           Benchmark a specific course (single-course endpoint)
  --sql-analysis             SQL query analysis mode (requires --server-log)
  --server-log=<path>        Path to server log file for SQL counting
  --output=<path>            Append results to file for A/B comparison
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
  node benchmark-access-policies.mjs --sql-analysis --server-log=server.log \\
      --label=develop --output=benchmark-results.txt --course-id=1
  #   3. Switch to DSL branch, restart server, then:
  node benchmark-access-policies.mjs --sql-analysis --server-log=server.log \\
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
    serverLog: args['server-log'] || null,
    outputFile: args['output'] || null,
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
        console.error(`Cannot read server log at ${logPath}: ${error.message}`);
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
 * Count SQL queries in a Hibernate show-sql log snippet.
 *
 * With format_sql=true, Hibernate outputs multi-line formatted SQL.
 * Each statement starts with one of: select, insert, update, delete
 * at the beginning of a line (after optional whitespace).
 *
 * With format_sql=false, each SQL statement is a single line starting
 * with "Hibernate: select/insert/update/delete".
 *
 * We count both patterns.
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
        const sqlStart = isHibernateLine ? trimmed.substring('hibernate:'.length).trim() : trimmed;

        let matched = false;
        if (sqlStart.startsWith('select ') || sqlStart.startsWith('select\n')) {
            selects++;
            matched = true;
        } else if (sqlStart.startsWith('insert ')) {
            inserts++;
            matched = true;
        } else if (sqlStart.startsWith('update ')) {
            updates++;
            matched = true;
        } else if (sqlStart.startsWith('delete ')) {
            deletes++;
            matched = true;
        }

        if (matched) {
            totalQueries++;
            // Normalize for uniqueness: take the first 200 chars of the SQL
            uniqueQueries.add(sqlStart.substring(0, 200));
        }
    }

    return { totalQueries, selects, inserts, updates, deletes, uniqueQueries: uniqueQueries.size };
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
    if (config.sqlAnalysis && !config.serverLog) {
        console.error('ERROR: --sql-analysis requires --server-log=<path>');
        console.error('Start the server with:  ./gradlew bootRun 2>&1 | tee server.log');
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

    // -------------------------------------------------------------------
    // SQL ANALYSIS MODE
    // -------------------------------------------------------------------
    if (config.sqlAnalysis) {
        await runSqlAnalysisMode(studentClient, courseId);
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
        allCourses: allCoursesStats,
        singleCourse: singleCourseStats,
        config: { iterations: config.iterations, warmup: config.warmup, courseCount: config.courseCount },
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
async function runSqlAnalysisMode(studentClient, courseId) {
    console.log('\n' + '='.repeat(60));
    console.log('SQL QUERY ANALYSIS');
    console.log('='.repeat(60));
    console.log(`Server log : ${config.serverLog}`);
    console.log(`Label      : ${config.label}`);
    console.log();
    console.log('Running one warmup request per endpoint, then one measured request.');
    console.log('Make sure the server was started with show-sql: true');
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

    if (singleCourseResult) {
        console.log();
        console.log(`  GET /api/core/courses/${courseId}/for-dashboard`);
        console.log(`    Response time : ${formatMs(singleCourseResult.elapsed)}`);
        console.log(`    Total queries : ${singleCourseResult.sqlStats.totalQueries}`);
        console.log(`    SELECTs       : ${singleCourseResult.sqlStats.selects}`);
        console.log(`    Unique queries: ${singleCourseResult.sqlStats.uniqueQueries}`);
    }

    // Write results to output file
    appendResult({
        label: config.label,
        timestamp: new Date().toISOString(),
        mode: 'sql-analysis',
        allCourses: {
            responseTimeMs: allCoursesResult.elapsed,
            ...allCoursesResult.sqlStats,
        },
        singleCourse: singleCourseResult ? {
            responseTimeMs: singleCourseResult.elapsed,
            ...singleCourseResult.sqlStats,
        } : null,
        courseId,
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

    if (result.sqlStats.totalQueries === 0) {
        console.log();
        console.log('  WARNING: No SQL queries detected. Possible causes:');
        console.log('    - show-sql is not enabled in application-local.yml');
        console.log('    - The server log file path is incorrect');
        console.log('    - The log file is not being flushed fast enough (try adding a small delay)');
    }
}

main().catch(error => {
    console.error('Benchmark failed:', error.message);
    process.exit(1);
});
