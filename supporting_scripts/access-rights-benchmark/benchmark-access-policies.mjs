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
 *   --help                     Show help
 */

// ---------------------------------------------------------------------------
// Imports – reuse the HTTP client and auth helpers from the setup-course lib
// ---------------------------------------------------------------------------
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
        complaintsEnabled: false,
        requestMoreFeedbackEnabled: false,
        courseInformationSharingConfiguration: 'COMMUNICATION_AND_MESSAGING',
        enrollmentEnabled: false,
        accuracyOfScores: 1,
        onlineCourse: false,
        timeZone: 'Europe/Berlin',
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
// Main
// ---------------------------------------------------------------------------

async function main() {
    console.log('='.repeat(60));
    console.log('Access Rights DSL — Performance Benchmark');
    console.log('='.repeat(60));
    console.log(`Server URL : ${config.serverUrl}`);
    console.log(`Iterations : ${config.iterations} (+ ${config.warmup} warmup)`);
    console.log();

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

    console.log('\n' + '='.repeat(60));
    console.log('BENCHMARK RESULTS');
    console.log('='.repeat(60));
    console.log();
    console.log('NOTE: Check server console for SQL query output (show-sql: true).');
    console.log('Count the SQL queries between ">>> BENCHMARK START" and ">>> BENCHMARK END" markers');
    console.log('in the server logs by searching for Hibernate query output.');
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

    console.log('\n' + '='.repeat(60));
    console.log('DATABASE QUERY ANALYSIS');
    console.log('='.repeat(60));
    console.log(`
To analyze the number of SQL queries per request:

1. Ensure your application-local.yml has:
     spring:
       jpa:
         show-sql: true
         properties:
           hibernate:
             format_sql: true

2. Run ONE request manually (e.g., with curl or this script with --iterations=1)
   and count the SQL statements printed in the server console.

3. Key queries to look for:
   - Course visibility query (policy-generated): SELECT ... FROM course WHERE ...
     This is generated by PolicyBasedCourseSpecs from the CourseVisibilityPolicy DSL.
   - Exercise loading queries: SELECT ... FROM exercise WHERE ...
   - User/group resolution: SELECT ... FROM jhi_user ... groups ...

4. Compare the generated SQL with the old hand-written JPQL queries to verify
   they are equivalent in terms of the WHERE clause conditions.
`);

    console.log('Benchmark complete.');
}

main().catch(error => {
    console.error('Benchmark failed:', error.message);
    process.exit(1);
});
