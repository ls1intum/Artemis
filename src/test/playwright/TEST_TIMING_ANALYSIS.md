# E2E Test Performance Optimization Plan

**Current total: ~17.6 min** (fast+slow: 12 min parallel, sequential: 5.6 min serial)
**Target: <10 min**

## Where Time is Spent

| Phase | Time | Tests | Bottleneck |
|-------|------|-------|------------|
| Fast tests (3 workers) | ~3 min | 126 | Course creation overhead (~63 per run) |
| Slow tests (3 workers) | ~9 min | 83 | Java builds (~50-60s), exam setup (~10s each) |
| Sequential tests (1 worker) | ~5.6 min | 18 | Serial programming builds |
| **Total** | **~17.6 min** | **227** | |

## Top 10 Slowest Tests

| # | Time | Test | Root Cause |
|---|------|------|------------|
| 1 | 110.9s | SCA grading + submission | Java build (~50s) + SCA check |
| 2 | 89.9s | Java code editor submission | Java build (~50s) |
| 3 | 74.7s | Test exam overview navigation | Exam setup + 8 exercises + participation |
| 4 | 69.3s | Group chat write message | WebSocket delivery delays |
| 5 | 67.7s | Test exam sidebar navigation | Exam setup + 8 exercises + participation |
| 6 | 65.0s | Test exam participation | Exam setup + 8 exercises + participation |
| 7 | 64.6s | Java git HTTPS submission | Java build (~50s) |
| 8 | 64.1s | Channel write message | WebSocket delivery delays |
| 9 | 59.4s | Exam test run conduct | Exam setup + 4 exercises + run |
| 10 | 57.1s | C code editor submission | Multiple parallel C builds |

## Optimization Strategies (Priority Order)

### 1. Switch Java to C everywhere possible (saves ~2-3 min)

**Quick wins:**
- `ProgrammingExerciseParticipation.spec.ts` line 38: Java successful → C (saves ~45s)
- Keep ONE parallel Java test to verify Java works (move to @slow, not @sequential)
- `ProgrammingExerciseStaticCodeAnalysis.spec.ts`: Create C SCA fixture (saves ~90s)
  - C supports SCA via GCC warnings. Need `c/static_code_analysis/` fixture with intentional warnings.

**Current language distribution:**
| Language | Tests | Build Time | Can Switch to C? |
|----------|-------|------------|-----------------|
| Java | 2 (submission) + 1 (SCA) | 50-60s each | Yes (keep 1 Java) |
| C | ~15 tests | 2-5s each | Already C |
| Python | 1 test | ~5s | Already fast |

### 2. Seed exams in Liquibase (saves ~2-3 min)

Every exam test block creates: exam + exercise groups + exercises + registration + generation + preparation = ~10-15s.

**Seed these exam structures:**
1. Exam with 4 exercises (TEXT + PROGRAMMING + QUIZ + MODELING) - for participation tests
2. Exam with TEXT only - for continue/reload/normal hand-in tests
3. Test exam with 4 exercises + 4 more (8 total) - for test exam participation
4. Exam with 4 exercises - for assessment tests
5. Exam with 4 exercises - for results tests
6. Exam with 4 exercises - for test run tests

**Implementation:** Add Liquibase changesets with `context="e2e"`:
- `exam` table entries with configurable dates (tests update dates via API before use)
- `exercise_group` entries linked to exams
- Exercise entries (text, quiz, modeling) linked to exercise groups
- Programming exercises need special handling (template repos must exist)

**10+ describe blocks x 10s setup = ~100-150s saved**

### 3. Consolidate exam describe blocks (saves ~1 min)

**ExamParticipation.spec.ts** has 6 describe blocks, each creating separate exams:
- Early Hand-in (studentTwo, studentThree, studentFour - 4 exercises)
- Continue & Reload (studentTwo, studentThree, studentFour - 1 TEXT)
- Normal Hand-in (studentFour - 1 TEXT)

**Consolidation:** Blocks 1-3 use different students, so they could share ONE exam:
- Create 1 exam with 4 exercises
- Register all 4 students
- Each test uses a different student → no interference

Similarly, **ExamAssessment.spec.ts** has 4 serial blocks (programming, modeling, text, quiz), each creating a separate exam. These could share 1 exam with all 4 exercise types.

### 4. More seed courses for fast tests (saves ~40s)

Currently 63 courses created per run. Add seed courses for:
- Atlas tests (6 files, 24 tests) → 2-3 seed courses
- LectureManagement (6 tests) → 1 seed course
- Exercise management tests (modeling, file-upload, quiz, text) → 3-4 seed courses
- CourseManagement (8 tests) → 2 seed courses

**~40 fewer course creations x ~1s = ~40s saved**

### 5. TestExamParticipation: reduce from 8 to 4 exercises (saves ~30s)

`TestExamParticipation.spec.ts` creates 8 exercises (3 text, 2 programming, 2 quiz, 2 modeling) per test. Tests already skip some programming submissions. Reduce to 4 exercises (1 per type) like regular exam tests.

### 6. Increase workers from 3 to 5 (saves ~20-30% wall clock)

With a modern machine (8+ cores), increase parallel workers for fast/slow tests.

## Implementation Roadmap

| Phase | Strategy | Effort | Expected Savings |
|-------|----------|--------|-----------------|
| **Phase 1** (quick wins) | Switch Java→C, keep 1 Java parallel | 1 hour | ~2 min |
| **Phase 2** | Create C SCA fixture | 2 hours | ~1.5 min |
| **Phase 3** | Seed courses for fast tests | 2 hours | ~0.5 min |
| **Phase 4** | Seed exam structures | 4-6 hours | ~2-3 min |
| **Phase 5** | Consolidate exam describe blocks | 3 hours | ~1 min |
| **Phase 6** | Increase workers, reduce test exam exercises | 1 hour | ~1 min |

**Total estimated savings: ~8-9 min → Target ~9-10 min achievable**

## Notes

- Sequential tests (~5.6 min) are the hardest to optimize since builds must be serial
- Programming exercise builds (even C) have a minimum ~2-5s overhead per exercise
- Exam tests with time-based waits (exam end, grace period) cannot be eliminated
- The 2 intermittent failures in parallel mode (Java build timeout, SCA timeout) will be fixed by switching to C
