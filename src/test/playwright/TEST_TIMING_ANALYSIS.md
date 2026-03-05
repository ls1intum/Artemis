# Playwright E2E Test Timing Analysis

* **Date:** 2026-03-04
* **Source:** JUnit XML results from `test-reports/results.xml` and monocart reports
* **Playwright version:** 1.58.2
* **Default workers:** 3 (parallel), 1 (sequential)
* **Platform:** macOS arm64, 10 CPUs, ~27 GB RAM

---

## Summary Statistics

| Metric | Value |
|--------|-------|
| Total test cases | 230 |
| Total cumulative execution time | 3,333.8s (55.6 min) |
| Average time per test | 14.5s |
| Tests > 60s (very slow) | 8 |
| Tests > 30s (slow) | 24 |
| Tests > 15s (moderate) | 65 |
| Tests < 5s (fast) | 50 |
| Retries configured | 2 |
| Fast test timeout | 45s |
| Slow/Sequential test timeout | 180s |

---

## Execution Architecture

Tests are organized into three Playwright projects:

1. **fast-tests** -- Tests tagged `@fast` or with no tags. Timeout: 45s. Run in parallel (3 workers).
2. **slow-tests** -- Tests tagged `@slow`. Timeout: 180s. Run in parallel (3 workers).
3. **sequential-tests** -- Tests tagged `@sequential`. Timeout: 180s. Run with 1 worker.

Execution order:
1. `playwright:setup` -- installs browsers and runs `init/importUsers.spec.ts`
2. `playwright:test:parallel` -- runs `fast-tests` and `slow-tests` projects concurrently
3. `playwright:test:sequential` -- runs `sequential-tests` project with a single worker

---

## Per-File Timing Breakdown

### @fast / Untagged Tests (fast-tests project)

| File | Tests | Time (s) | Avg (s) | Slow? |
|------|------:|----------:|--------:|:-----:|
| e2e/course/CourseMessages.spec.ts | 28 | 398.4 | 14.2 | YES |
| e2e/exam/ExamTestRun.spec.ts | 4 | 165.7 | 41.4 | YES |
| e2e/exam/ExamChecklists.spec.ts | 8 | 90.2 | 11.3 | YES |
| e2e/exam/ExamManagement.spec.ts | 9 | 83.3 | 9.3 | YES |
| e2e/course/CourseManagement.spec.ts | 8 | 76.1 | 9.5 | YES |
| e2e/exercise/ExerciseImport.spec.ts | 4 | 67.6 | 16.9 | YES |
| e2e/exercise/quiz-exercise/QuizExerciseParticipation.spec.ts | 11 | 55.5 | 5.0 | -- |
| e2e/exercise/modeling/ModelingExerciseManagement.spec.ts | 5 | 46.7 | 9.3 | -- |
| e2e/atlas/StudentCompetencyProgressView.spec.ts | 4 | 44.0 | 11.0 | -- |
| e2e/exercise/quiz-exercise/QuizExerciseManagement.spec.ts | 6 | 41.7 | 6.9 | -- |
| e2e/exercise/programming/ProgrammingExerciseManagement.spec.ts | 4 | 40.6 | 10.2 | -- |
| e2e/exercise/quiz-exercise/QuizExerciseAssessment.spec.ts | 2 | 40.5 | 20.2 | -- |
| e2e/atlas/CompetencyManagement.spec.ts | 6 | 40.4 | 6.7 | -- |
| e2e/atlas/CompetencyLectureUnitInteraction.spec.ts | 5 | 38.9 | 7.8 | -- |
| e2e/atlas/CompetencyExerciseInteractions.spec.ts | 3 | 35.5 | 11.8 | -- |
| e2e/atlas/CompetencyImport.spec.ts | 2 | 30.4 | 15.2 | -- |
| e2e/lecture/LectureManagement.spec.ts | 6 | 29.4 | 4.9 | -- |
| e2e/exam/ExamCreationDeletion.spec.ts | 3 | 25.3 | 8.4 | -- |
| e2e/atlas/LearningPathManagement.spec.ts | 4 | 25.0 | 6.2 | -- |
| e2e/exercise/modeling/ModelingExerciseAssessment.spec.ts | 3 | 22.3 | 7.4 | -- |
| e2e/exam/ExamDateVerification.spec.ts | 4 | 21.4 | 5.4 | -- |
| e2e/exercise/file-upload/FileUploadExerciseManagement.spec.ts | 2 | 21.2 | 10.6 | -- |
| e2e/exam/test-exam/TestExamCreationDeletion.spec.ts | 2 | 19.2 | 9.6 | -- |
| e2e/exercise/file-upload/FileUploadExerciseAssessment.spec.ts | 3 | 19.1 | 6.4 | -- |
| e2e/exercise/text/TextExerciseManagement.spec.ts | 2 | 14.9 | 7.4 | -- |
| e2e/exercise/text/TextExerciseAssessment.spec.ts | 3 | 12.6 | 4.2 | -- |
| e2e/exercise/quiz-exercise/QuizExerciseDropLocation.spec.ts | 1 | 10.4 | 10.4 | -- |
| e2e/Logout.spec.ts | 2 | 7.7 | 3.9 | -- |
| e2e/Login.spec.ts | 5 | 7.1 | 1.4 | -- |
| e2e/exercise/modeling/ModelingExerciseParticipation.spec.ts | 1 | 6.6 | 6.6 | -- |
| e2e/exercise/file-upload/FileUploadExerciseParticipation.spec.ts | 1 | 5.4 | 5.4 | -- |
| e2e/course/CourseExercise.spec.ts | 1 | 4.8 | 4.8 | -- |
| e2e/exercise/text/TextExerciseParticipation.spec.ts | 1 | 4.1 | 4.1 | -- |
| e2e/SystemHealth.spec.ts | 7 | 2.5 | 0.4 | -- |
| e2e/exam/ExamParticipation.spec.ts (fast subset) | 1 | 17.2 | 17.2 | -- |
| e2e/exam/test-exam/TestExamManagement.spec.ts | 7 | 90.7 | 13.0 | YES |
| **Subtotal (fast-tests)** | **~151** | **~1,700** | | |

### @slow Tests (slow-tests project)

| File | Tests | Time (s) | Avg (s) | Slow? |
|------|------:|----------:|--------:|:-----:|
| e2e/exam/ExamParticipation.spec.ts (slow subset) | 9 | 245.3 | 27.3 | YES |
| e2e/exam/test-exam/TestExamParticipation.spec.ts | 4 | 239.6 | 59.9 | YES |
| e2e/exam/ExamAssessment.spec.ts (slow subset) | 9 | 170.4 | 18.9 | YES |
| e2e/exercise/programming/ProgrammingExerciseStaticCodeAnalysis.spec.ts | 1 | 110.9 | 110.9 | YES |
| e2e/exam/test-exam/TestExamTestRun.spec.ts | 3 | 102.0 | 34.0 | YES |
| e2e/exercise/ExerciseImport.spec.ts (slow subset) | 1 | 42.7 | 42.7 | YES |
| e2e/exam/test-exam/TestExamStudentExams.spec.ts | 2 | 37.7 | 18.9 | -- |
| e2e/exam/ExamChecklists.spec.ts (slow subset) | 3 | 33.5 | 11.2 | -- |
| e2e/exam/ExamPlantUmlDiagramIsolation.spec.ts | 1 | 22.0 | 22.0 | -- |
| **Subtotal (slow-tests)** | **~33** | **~1,004** | | |

### @sequential Tests (sequential-tests project)

| File | Tests | Time (s) | Avg (s) | Slow? |
|------|------:|----------:|--------:|:-----:|
| e2e/exercise/programming/ProgrammingExerciseParticipation.spec.ts | 18 | 533.5 | 29.6 | YES |
| e2e/exam/ExamParticipation.spec.ts (sequential subset) | 3 | 92.6 | 30.9 | YES |
| e2e/exam/ExamAssessment.spec.ts (sequential subset) | 2 | 31.9 | 16.0 | -- |
| e2e/exam/ExamResults.spec.ts | 5 | 9.5 | 1.9 | -- |
| e2e/exercise/programming/ProgrammingExerciseAssessment.spec.ts | 1 | 0.0 | 0.0 | -- |
| **Subtotal (sequential-tests)** | **~29** | **~668** | | |

---

## Top 15 Slowest Individual Tests

| # | Time (s) | Test Name | File |
|---|----------:|-----------|------|
| 1 | 110.9 | Configures SCA grading and makes a successful submission with SCA errors | ProgrammingExerciseStaticCodeAnalysis.spec.ts |
| 2 | 89.9 | Makes a submission using code editor (Java) | ProgrammingExerciseParticipation.spec.ts |
| 3 | 74.7 | Using exercise overview to navigate within exam (test exam) | TestExamParticipation.spec.ts |
| 4 | 69.3 | Students should be able to write a message in group chat | CourseMessages.spec.ts |
| 5 | 67.7 | Using exercise sidebar to navigate within exam (test exam) | TestExamParticipation.spec.ts |
| 6 | 65.0 | Participates as a student in a registered test exam | TestExamParticipation.spec.ts |
| 7 | 64.6 | Makes a git submission through HTTPS (Java) | ProgrammingExerciseParticipation.spec.ts |
| 8 | 64.1 | Student should be able to write message in channel | CourseMessages.spec.ts |
| 9 | 59.4 | Conducts a test run | ExamTestRun.spec.ts |
| 10 | 57.1 | Makes a submission using code editor (C) | ProgrammingExerciseParticipation.spec.ts |
| 11 | 53.2 | Deletes a test run | ExamTestRun.spec.ts |
| 12 | 51.0 | Participates as a student in a registered exam (early) | ExamParticipation.spec.ts |
| 13 | 50.5 | Makes a git submission through HTTPS (C) | ProgrammingExerciseParticipation.spec.ts |
| 14 | 46.6 | Conducts a test run (test exam) | TestExamTestRun.spec.ts |
| 15 | 45.0 | Participates as a student in a registered exam (normal) | ExamParticipation.spec.ts |

---

## Bottleneck Analysis

### 1. Programming Exercise Tests are the Dominant Bottleneck

`ProgrammingExerciseParticipation.spec.ts` alone accounts for **533.5s** (16% of total cumulative time) with 18 tests averaging ~30s each. These tests involve:

- Compiling and building student submissions via CI/CD (LocalCI)
- Git operations (clone, push via HTTPS and SSH)
- Waiting for build results

These are forced sequential due to resource contention on the CI system. Each programming exercise submission triggers a real build pipeline, making parallelization risky.

### 2. Explicit Time-Based Waits

Several tests use `page.waitForTimeout()` for hard-coded delays:

- **ExamAssessment.spec.ts** -- waits for grace period end and result publication date (lines 177, 183). The monocart report shows a **51.2s wait** in the "Assesses quiz automatically" test alone.
- **ExamDateVerification.spec.ts** -- waits for exam end time (line 125)
- **ProgrammingExerciseAssessment.spec.ts** -- waits for due date and assessment due date (lines 47, 77)
- **TextExerciseAssessment.spec.ts** -- waits for due date and assessment due date (lines 45, 72)
- **QuizExerciseParticipation.spec.ts** -- waits for quiz start time (line 72)

These waits are structurally necessary because tests set up exams/exercises with real future timestamps and must wait for those times to pass. This is a fundamental design issue.

### 3. Per-Test Course Creation and Deletion Overhead

Every test file creates its own course in `beforeEach` and deletes it in `afterEach`. Looking at the monocart report for a single test:

- Course creation: ~71ms (API call) + ~350ms (student/tutor/instructor registration)
- Course deletion: ~800ms (includes cleanup delay + API call)
- Total setup/teardown overhead per test: ~1.5-5s

For 230 tests, this adds approximately **350-1,150s** of pure setup/teardown time.

### 4. CourseMessages Complexity

`CourseMessages.spec.ts` has 28 tests totaling 398.4s. Two tests that involve writing messages take 64-69s each, likely due to WebSocket message delivery and UI rendering delays.

### 5. Exam Participation Tests Require Multi-Step Workflows

Exam tests follow a complex workflow: create exam -> create exercise groups -> add exercises -> register students -> generate student exams -> start exercises -> wait for exam to begin -> participate -> hand in -> wait for grace period -> assess. Each step is a real API call or UI interaction.

---

## Tag Distribution Analysis

Most tests (>70%) have **no explicit tag** and fall into `fast-tests` by default (matched by `^[^@]*$` regex). This means the `fast-tests` project is the largest and most heterogeneous:

| Tag | Tests | Cumulative Time |
|-----|------:|----------------:|
| (none) / @fast | ~151 | ~1,700s |
| @slow | ~33 | ~1,004s |
| @sequential | ~29 | ~668s |

Some files contain tests that span multiple tags (e.g., `ExamAssessment.spec.ts` has `@sequential`, `@slow` describes). Some tests within `ExamChecklists.spec.ts` are explicitly tagged `@fast` while the rest in that file have no tag.

---

## Comparison with thesis-management Seed Data Approach

### thesis-management Architecture

The [thesis-management](https://github.com/ls1intum/thesis-management) project achieves dramatically faster E2E test execution through a fundamentally different architecture:

| Aspect | thesis-management | Artemis |
|--------|-------------------|---------|
| **Total E2E tests** | ~158 | 230 |
| **Workers** | 8 | 3 (parallel) + 1 (sequential) |
| **Approximate wall-clock time** | ~30s | 15-25+ minutes |
| **Test data setup** | SQL seed at application startup | API calls in `beforeEach` per test |
| **Database** | PostgreSQL | MySQL |
| **Data isolation** | Shared pre-seeded data, tests read or make isolated changes | Full create/teardown per test |
| **Auth setup** | Playwright auth setup project with stored state | Login API calls per test |

### How thesis-management's Seed Data Works

1. **Liquibase SQL seed file** (`seed_dev_test_data.sql`, ~1,572 lines): A comprehensive SQL script runs during application startup (via Liquibase `context="dev"`). It creates:
   - 11 users with roles, groups, and full profiles
   - Topics, theses in various states, applications, proposals, assessments, comments, feedback
   - All using deterministic UUIDs (`00000000-0000-4000-xxxx-yyyyyyyyyy`) for stable references

2. **Idempotent design**: Uses `ON CONFLICT DO NOTHING` / `DO UPDATE` so the seed can run repeatedly without errors.

3. **File seeding**: A `DevSeedFileInitializer` Spring component creates minimal PDF files on startup for tests that need file attachments.

4. **Auth state reuse**: A Playwright `setup` project logs in once and stores the auth state to `e2e/.auth/student.json`. All test projects depend on `setup` and reuse the stored session.

5. **No per-test teardown**: Tests do not delete data after each run. The seed data is the baseline, and the application state is reset between full test runs by restarting the application (or re-running the seed).

### Key Differences Enabling Speed

| Factor | Impact |
|--------|--------|
| **Zero per-test API setup** | Eliminates 1.5-5s setup per test (saves ~350-1,150s total) |
| **No real-time waits** | thesis-management has no exam timing, no CI builds, no quiz scheduling. Tests operate on pre-existing data states |
| **8 parallel workers** | 2.7x more parallelism than Artemis's 3 parallel workers |
| **Simpler domain** | No programming exercises (no CI/CD), no real-time exams, no WebSocket-dependent features |
| **Auth state caching** | Login happens once; Artemis logs in per test via API calls |

### Applicability to Artemis

The seed data approach could partially apply to Artemis, but with significant caveats:

**What could be adopted:**

1. **Pre-seeded users and courses**: Instead of creating courses and registering users in `beforeEach`, seed a set of standard test courses with pre-registered students/tutors/instructors via Liquibase.
2. **Auth state caching**: Use Playwright's `storageState` to log in once per role and reuse the session across tests.
3. **Pre-created exercises for read-only tests**: Tests that only verify display/navigation (e.g., exam checklists, exercise listings) could use pre-seeded exercises.
4. **Deterministic IDs**: Use known entity IDs in seed data so tests can directly navigate to URLs without querying.

**What cannot be adopted:**

1. **Programming exercises**: Submissions trigger real CI builds. There is no way to "seed" a completed build result without the actual build infrastructure. These tests are inherently slow.
2. **Exam timing tests**: Tests that verify exam start/end behavior must wait for real time to pass. Seeding cannot eliminate this.
3. **WebSocket-dependent tests**: Message delivery, live updates, and real-time announcements require actual running infrastructure.
4. **State-mutating tests**: Tests that create, modify, and delete entities cannot easily share seeded data without risking interference between parallel tests.

---

## Optimization Recommendations

### High Impact

1. **Reduce hard-coded time waits**: The single largest waste is the ~51s `waitForTimeout` in `ExamAssessment.spec.ts` (quiz assessment). Set exam/exercise timestamps closer to "now" to minimize wait times. This pattern appears in at least 5 test files.

2. **Implement auth state caching**: Create a Playwright setup project that logs in as each role once, saves `storageState`, and reuses it. This would save ~0.5-1s per test (cumulative: 115-230s).

3. **Pre-seed common test data**: Create courses, users, and basic exercises via SQL seed data loaded at application startup. Tests that only need read access to these entities can skip setup entirely.

4. **Increase parallel workers**: With 10 CPUs available, increase from 3 to 5-6 parallel workers for fast/slow tests. The sequential project must stay at 1 worker.

### Medium Impact

5. **Batch course creation**: For test files with many tests sharing the same course (e.g., CourseMessages with 28 tests), use `beforeAll` instead of `beforeEach` to create the course once.

6. **Split large test files**: `CourseMessages.spec.ts` (28 tests, 398s) and `ProgrammingExerciseParticipation.spec.ts` (18 tests, 534s) are bottleneck files. Splitting them allows better parallelization across workers.

7. **Optimize message tests**: The two slowest CourseMessages tests (64-69s each for writing a message) likely have unnecessary waits or polling. Investigate WebSocket connection delays.

### Lower Impact

8. **Tag audit**: Many tests in `ExamTestRun.spec.ts` (4 tests, 166s) have no tag and run as fast-tests despite averaging 41s each. They should be tagged `@slow`.

9. **Reduce retries in development**: The default 2 retries triple the wall-clock time for flaky tests. Consider 1 retry for local development.

10. **Parallel exam assessment**: The `@sequential` tag on `ExamAssessment` programming exercise tests could be reconsidered if the CI system can handle concurrent builds.

---

## Estimated Time Savings

| Optimization | Estimated Savings |
|-------------|-------------------|
| Reduce exam time waits | 50-100s cumulative |
| Auth state caching | 115-230s cumulative |
| Pre-seed common data | 350-600s cumulative |
| Increase workers (3->5) | 30-40% wall-clock reduction |
| Split large test files | 10-20% wall-clock reduction |
| **Total potential** | **40-60% reduction in wall-clock time** |

Note: These are cumulative time savings. Wall-clock improvement depends on parallelization effectiveness.
