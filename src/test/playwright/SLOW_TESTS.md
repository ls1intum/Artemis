# Slow E2E Tests Analysis

Tests that take longer than 30s when run individually (single test, not batch).
These are candidates for optimization.

## Sequential Tests (require --workers=1)

| Test | Time | Notes |
|------|------|-------|
| ExamAssessment: Programming exercise assessment (MANUAL) | ~36s | Exam creation + participation + build + assessment |
| ExamResults: Full suite (5 tests serial) | ~60s total | Exam creation + participation + build + grading + assessment + results |
| ExamParticipation: Git submissions (2 tests) | ~27s each | Exam + programming exercise + git clone/push |
| ProgrammingExerciseParticipation: SSH tests | ~27s each | Exercise creation + SSH key setup + git clone/push |
| ProgrammingExerciseParticipation: HTTPS token | ~15s | Exercise creation + git clone/push |
| ProgrammingExerciseParticipation: Team tests | ~20s each | Team creation + git clone/push |

## Slow Tests (parallel capable)

| Test | Time | Notes |
|------|------|-------|
| ProgrammingExerciseStaticCodeAnalysis | ~96s | SCA config + C exercise + submission + build |
| ExamStatistics | ~37s | Full exam lifecycle + statistics check |
| ExamAssessment: Quiz assessment | ~29s | Exam + quiz + auto assessment |
| ExamParticipation: Normal hand-in | ~28s | Full exam participation flow |
| TestExamTestRun: Conducts test run | ~25s | Test exam + test run execution |
| ExamAssessment: Modeling/Text assessment | ~23s each | Exam + exercise + assessment |
| ExamTestRun: Creates/Conducts test run | ~22-29s | Exam test run lifecycle |
| ProgrammingExerciseParticipation: Java submission | ~50-60s | Java build is slower than C |

## Key Observations

1. **Java builds (~15-30s) vs C builds (~2-5s)**: Most exercises use C for speed, but Java is still needed for some tests
2. **Exam lifecycle overhead**: Creating exam + exercises + student registration + generating exams adds ~10-15s
3. **Sequential tests MUST use --workers=1**: Running with multiple workers causes server overload and timeouts
4. **Seed courses accumulate data**: Programming exercises from previous runs accumulate in seed courses, requiring direct URL navigation (not UI navigation through course list)
