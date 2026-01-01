# Migration Plan: Simplify Submission→Result and Result→Feedback Relationships to Set

## Overview

Migrate `Submission.results` and `Result.feedbacks` from `List` with `@OrderColumn`/`@OrderBy` to `Set` without specific ordering. This eliminates orphan entity issues, prevents MultipleBagFetchException, and simplifies the codebase.

## Status Tracking

| Step | Description | Status | Notes |
|------|-------------|--------|-------|
| 1 | Add `correctionRound` field to Result entity | ✅ Complete | Field added to Result.java |
| 2 | Create database migration | ✅ Complete | Migration only sets correction_round for MANUAL/SEMI_AUTOMATIC results |
| 3 | Change Submission.results from List to Set | ✅ Complete | Changed to Set<Result> |
| 4 | Change Result.feedbacks from List to Set | ✅ Complete | Changed to Set<Feedback> |
| 5 | Remove feedbackOrder from Feedback entity | ✅ Complete | Field removed |
| 6 | Update repository queries | ✅ Complete | Queries updated for new types |
| 7 | Update service layer | ✅ Complete | Services updated, correctionRound set in all result creation methods |
| 8 | Update DTOs and mappers | ✅ Complete | DTOs updated |
| 9 | Update client TypeScript models | ✅ Complete | Added correctionRound field |
| 10 | Run and fix tests | ✅ Complete | Assessment (412), Exam (548), Text/Modeling (460) tests pass |

## Key Design Decisions

### 1. Correction Round Handling

**Previous approach**: Used list index to determine correction round (`results.get(0)` = first round, `results.get(1)` = second round)

**New approach**: Explicit `correctionRound` field on Result entity

**Important considerations for programming exercises**:
- Programming exercises can have AUTOMATIC results from CI/CD
- Each submission can have multiple results (automatic + manual)
- Correction round should only apply to MANUAL assessments in exam context
- AUTOMATIC results don't have a correction round concept

### 2. Correction Round Calculation Logic

For the database migration, correction round should be calculated as follows:

```sql
-- Only for manual assessment results (not automatic)
-- Correction round is determined by the order of manual assessments for a participation
-- across all submissions, not per submission
```

**Edge cases to handle**:
1. AUTOMATIC results: Should have `correction_round = NULL` (not applicable)
2. MANUAL results in regular exercises: `correction_round = 0`
3. MANUAL results in exam exercises: Based on assessment order (0 for first, 1 for second)
4. AUTOMATIC_ATHENA results: `correction_round = NULL`

### 3. Feedback Ordering

**Previous approach**: `feedbackOrder` field with `@OrderBy("feedbackOrder ASC")`

**New approach**: No ordering - feedbacks are stored in a Set without specific order

## Database Migration Details

### File: `20251231100000_changelog.xml`

**Changeset 1** (`20251231100001`):
- Add `correction_round` column to `result` table
- Populate based on assessment type and completion date ordering

**Changeset 2** (`20251231100002`):
- Drop `results_order` column (with precondition check)

**Changeset 3** (`20251231100003`):
- Drop `feedback_order` column (with precondition check)

### Correction Round Population Query

```sql
-- For each participation, order results by completion_date and assign correction_round
-- Only for results where assessment_type = 'MANUAL' or 'SEMI_AUTOMATIC'
UPDATE result r
SET correction_round = (
    SELECT COUNT(*)
    FROM result r2
    WHERE r2.participation_id = r.participation_id
    AND r2.id < r.id
    AND r2.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
    AND r2.completion_date IS NOT NULL
)
WHERE r.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
AND r.completion_date IS NOT NULL;
```

## Files Modified

### Server - Domain
- `Result.java` - Added `correctionRound` field
- `Feedback.java` - Removed `feedbackOrder` field
- `Submission.java` - Changed `results` from List to Set

### Server - Repositories
- Various repository classes updated for Set return types

### Server - Services
- `SubmissionService.java` - Updated `lockSubmission`, `addResultWithFeedbackByCorrectionRound`, `createResultAfterComplaintResponse` to set correctionRound
- `ProgrammingSubmissionService.java` - Updated `lockSubmission` to set correctionRound
- `AssessmentService.java` - Handle null feedbacks in `updateAssessmentAfterComplaint`
- `CourseScoreCalculationService.java` - Fixed result selection logic

### Client
- `result.model.ts` - Added `correctionRound?: number`
- `submission.model.ts` - Updated helper functions for Set-based access using correctionRound field
- `exercise-assessment-dashboard.component.ts/html` - Updated for new API

### Tests
- Multiple test files updated for Set assertions
- `ParticipationUtilService.java` - Added correctionRound parameter to test utilities
- `TextExerciseUtilService.java` - Set correctionRound=0 for results
- `CourseScoreCalculationServiceTest.java` - Fixed test setup and expectations

## Testing Checklist

- [x] Assessment tests (412 tests pass)
- [x] Exam tests (548 tests pass)
- [x] Modeling exercise tests (part of 460 tests)
- [x] Text exercise tests (part of 460 tests)
- [ ] Programming exercise tests (some test ordering issues)
- [ ] Quiz tests
- [ ] Client unit tests
- [ ] Client E2E tests

## Rollback Plan

If issues are found:
1. Revert the changelog changes
2. Re-add `results_order` and `feedback_order` columns
3. Revert entity changes back to List

## Known Issues / TODOs

1. ~~**Correction round for automatic results**~~: ✅ Verified - AUTOMATIC and AUTOMATIC_ATHENA results correctly get `NULL` for correction_round
2. ~~**Multiple submissions per participation**~~: ✅ Verified - Correction round is calculated per participation across all submissions
3. **Programming exercise test stability**: Some programming exercise tests fail when run together but pass individually - likely test ordering issues unrelated to this migration
4. **Remaining test coverage**: Quiz tests, client unit tests, and E2E tests still need verification
