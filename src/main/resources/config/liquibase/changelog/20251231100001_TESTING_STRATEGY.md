# Testing Strategy for Correction Round Migration

## Overview

The `correction_round` column is added to the `result` table to track which assessment round a result belongs to:
- `correction_round = 0`: First assessment
- `correction_round = 1`: Second correction (exam second assessment)
- `correction_round = 2+`: Additional corrections (rare)
- `correction_round = NULL`: Automatic results or incomplete manual assessments

## Migration Logic

The migration calculates `correction_round` by counting previous completed manual/semi-automatic results within the same **participation** (not submission). This is important for programming exercises where each commit creates a new submission.

```sql
correction_round = COUNT of previous results WHERE:
  - Same participation_id
  - result.id < current result.id
  - assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
  - completion_date IS NOT NULL
```

## Testing Strategy

### Phase 1: Pre-Migration Analysis

Run on a **copy of production data** or staging environment:

1. **Capture baseline metrics** (Section 1 of verification SQL)
   - Count results by assessment type
   - Identify participations with multiple manual assessments
   - Save sample data for comparison

2. **Identify test cases**
   - Participations with exactly 1 manual result → should get `correction_round = 0`
   - Participations with exactly 2 manual results → should get `0` and `1`
   - Programming exercise participations with multiple submissions
   - Incomplete assessments (no completion_date) → should stay `NULL`

### Phase 2: Post-Migration Verification

1. **Run validation queries** (Section 3 of verification SQL)
   - Check sequential correction rounds (0, 1, 2, ...)
   - Recalculate and compare actual vs expected
   - Verify automatic results have `NULL`

2. **Expected outcomes**
   | Scenario | Expected correction_round |
   |----------|---------------------------|
   | AUTOMATIC result | NULL |
   | AUTOMATIC_ATHENA result | NULL |
   | MANUAL result, incomplete | NULL |
   | MANUAL result, 1st completed | 0 |
   | MANUAL result, 2nd completed | 1 |
   | SEMI_AUTOMATIC, 1st completed | 0 |

### Phase 3: Application Testing

After migration, verify the application works correctly:

1. **Exam Assessment**
   - Create an exam with second correction enabled
   - Complete first assessment → verify `correction_round = 0`
   - Complete second assessment → verify `correction_round = 1`
   - Check that the UI correctly shows first/second correction

2. **Normal Course Exercise**
   - Submit and assess a text/modeling exercise
   - Verify `correction_round = 0`

3. **Programming Exercise**
   - Submit multiple commits
   - Manually assess one submission
   - Verify `correction_round = 0`
   - If second correction exists, verify correct assignment

### Known Edge Cases

1. **Programming exercises**: Multiple submissions per participation
   - Migration correctly counts across ALL submissions in a participation
   - Not per-submission

2. **Incomplete assessments**: `completion_date IS NULL`
   - These get `correction_round = NULL` (correct behavior)
   - They should NOT be counted when calculating rounds for other results

3. **Same assessor multiple rounds**: Technically possible but rare
   - Migration doesn't care about assessor, only counts chronological results

## Rollback Plan

If issues are found:

```sql
-- Remove the correction_round column
ALTER TABLE result DROP COLUMN correction_round;
```

The rollback is safe because:
- The column is nullable
- No other code depends on it until it's fully migrated
- The original `results_order` column is preserved (not dropped yet)

## Verification Queries Location

See: `20251231100001_verification.sql`

## Sign-off Checklist

- [ ] Pre-migration baseline captured
- [ ] Migration ran without errors
- [ ] Validation query 3.1 returns 0 rows (sequential rounds)
- [ ] Validation query 3.2 returns 0 rows (actual = expected)
- [ ] AUTOMATIC results have NULL correction_round
- [ ] Completed MANUAL results have non-NULL correction_round
- [ ] Application exam assessment works correctly
- [ ] Application shows correct correction round in UI
