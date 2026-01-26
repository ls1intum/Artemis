-- =====================================================================================
-- VERIFICATION SCRIPTS FOR CORRECTION_ROUND MIGRATION (20251231100001)
-- =====================================================================================
-- Run these queries to verify the correction_round migration worked correctly.
--
-- TESTING STRATEGY:
-- 1. Run PRE-MIGRATION queries to capture baseline data
-- 2. Run the migration
-- 3. Run POST-MIGRATION queries to verify correctness
-- 4. Run VALIDATION queries to check for anomalies
-- =====================================================================================

-- =====================================================================================
-- SECTION 1: PRE-MIGRATION - Capture baseline (run BEFORE migration)
-- =====================================================================================

-- 1.1 Count results by assessment type (baseline)
SELECT
    assessment_type,
    COUNT(*) as total_count,
    SUM(CASE WHEN completion_date IS NOT NULL THEN 1 ELSE 0 END) as completed_count
FROM result
GROUP BY assessment_type
ORDER BY assessment_type;

-- 1.2 Identify participations with multiple manual assessments (these are exam second corrections)
-- These are the most important cases to verify
SELECT
    s.participation_id,
    COUNT(DISTINCT r.id) as manual_result_count,
    GROUP_CONCAT(r.id ORDER BY r.id) as result_ids
FROM result r
JOIN submission s ON s.id = r.submission_id
WHERE r.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
  AND r.completion_date IS NOT NULL
GROUP BY s.participation_id
HAVING COUNT(DISTINCT r.id) > 1
ORDER BY manual_result_count DESC
LIMIT 100;

-- 1.3 Sample of participations with their manual results (for manual verification)
-- Save this output to compare with post-migration results
SELECT
    s.participation_id,
    r.id as result_id,
    r.assessment_type,
    r.completion_date,
    r.score,
    -- This is what correction_round SHOULD be after migration:
    (SELECT COUNT(*)
     FROM result r2
     JOIN submission s2 ON s2.id = r2.submission_id
     WHERE s2.participation_id = s.participation_id
       AND r2.id < r.id
       AND r2.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
       AND r2.completion_date IS NOT NULL
    ) as expected_correction_round
FROM result r
JOIN submission s ON s.id = r.submission_id
WHERE r.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
  AND r.completion_date IS NOT NULL
ORDER BY s.participation_id, r.id
LIMIT 500;

-- =====================================================================================
-- SECTION 2: POST-MIGRATION - Verify results (run AFTER migration)
-- =====================================================================================

-- 2.1 Summary of correction_round distribution
SELECT
    assessment_type,
    correction_round,
    COUNT(*) as count
FROM result
WHERE assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
GROUP BY assessment_type, correction_round
ORDER BY assessment_type, correction_round;

-- 2.2 Verify AUTOMATIC results have NULL correction_round (as expected)
SELECT
    assessment_type,
    COUNT(*) as total,
    SUM(CASE WHEN correction_round IS NULL THEN 1 ELSE 0 END) as null_count,
    SUM(CASE WHEN correction_round IS NOT NULL THEN 1 ELSE 0 END) as non_null_count
FROM result
WHERE assessment_type IN ('AUTOMATIC', 'AUTOMATIC_ATHENA')
GROUP BY assessment_type;
-- Expected: non_null_count should be 0 for all AUTOMATIC types

-- 2.3 Verify completed MANUAL/SEMI_AUTOMATIC results have correction_round set
SELECT
    assessment_type,
    COUNT(*) as total,
    SUM(CASE WHEN correction_round IS NULL THEN 1 ELSE 0 END) as null_count,
    SUM(CASE WHEN correction_round IS NOT NULL THEN 1 ELSE 0 END) as non_null_count
FROM result
WHERE assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
  AND completion_date IS NOT NULL
GROUP BY assessment_type;
-- Expected: null_count should be 0 (all completed manual results should have correction_round)

-- 2.4 Verify incomplete MANUAL/SEMI_AUTOMATIC results have NULL correction_round
SELECT
    assessment_type,
    COUNT(*) as total,
    SUM(CASE WHEN correction_round IS NULL THEN 1 ELSE 0 END) as null_count,
    SUM(CASE WHEN correction_round IS NOT NULL THEN 1 ELSE 0 END) as non_null_count
FROM result
WHERE assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
  AND completion_date IS NULL
GROUP BY assessment_type;
-- Expected: non_null_count should be 0 (incomplete results should not have correction_round)

-- =====================================================================================
-- SECTION 3: VALIDATION - Check for anomalies (run AFTER migration)
-- =====================================================================================

-- 3.1 Find any participations where correction_round is not sequential (0, 1, 2, ...)
-- This would indicate a problem with the migration
SELECT
    s.participation_id,
    GROUP_CONCAT(r.correction_round ORDER BY r.id) as correction_rounds,
    GROUP_CONCAT(r.id ORDER BY r.id) as result_ids,
    COUNT(*) as result_count
FROM result r
JOIN submission s ON s.id = r.submission_id
WHERE r.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
  AND r.completion_date IS NOT NULL
GROUP BY s.participation_id
HAVING MAX(r.correction_round) >= COUNT(*)
   OR MIN(r.correction_round) != 0
LIMIT 100;
-- Expected: No rows (all participations should have sequential correction rounds starting at 0)

-- 3.2 Verify the correction_round calculation is correct by recalculating
-- Compare actual vs expected - should return 0 rows if migration is correct
SELECT
    r.id as result_id,
    s.participation_id,
    r.correction_round as actual_correction_round,
    (SELECT COUNT(*)
     FROM result r2
     JOIN submission s2 ON s2.id = r2.submission_id
     WHERE s2.participation_id = s.participation_id
       AND r2.id < r.id
       AND r2.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
       AND r2.completion_date IS NOT NULL
    ) as expected_correction_round
FROM result r
JOIN submission s ON s.id = r.submission_id
WHERE r.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
  AND r.completion_date IS NOT NULL
  AND r.correction_round != (
      SELECT COUNT(*)
      FROM result r2
      JOIN submission s2 ON s2.id = r2.submission_id
      WHERE s2.participation_id = s.participation_id
        AND r2.id < r.id
        AND r2.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
        AND r2.completion_date IS NOT NULL
  )
LIMIT 100;
-- Expected: No rows (actual should equal expected for all results)

-- 3.3 Check for exam participations with exactly 2 correction rounds (common case)
-- This helps verify second correction is working
SELECT
    e.title as exam_title,
    COUNT(DISTINCT s.participation_id) as participations_with_2_corrections
FROM result r
JOIN submission s ON s.id = r.submission_id
JOIN participation p ON p.id = s.participation_id
JOIN exercise ex ON ex.id = p.exercise_id
JOIN exercise_group eg ON eg.id = ex.exercise_group_id
JOIN exam e ON e.id = eg.exam_id
WHERE r.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
  AND r.completion_date IS NOT NULL
  AND r.correction_round = 1  -- Second correction
GROUP BY e.id, e.title
ORDER BY participations_with_2_corrections DESC
LIMIT 20;

-- =====================================================================================
-- SECTION 4: DETAILED INSPECTION - For manual verification
-- =====================================================================================

-- 4.1 Sample of participations with multiple corrections (for spot-checking)
SELECT
    s.participation_id,
    p.discriminator as participation_type,
    r.id as result_id,
    r.assessment_type,
    r.correction_round,
    r.completion_date,
    r.score,
    r.rated,
    u.login as assessor_login
FROM result r
JOIN submission s ON s.id = r.submission_id
JOIN participation p ON p.id = s.participation_id
LEFT JOIN jhi_user u ON u.id = r.assessor_id
WHERE s.participation_id IN (
    -- Get participations with multiple corrections
    SELECT s2.participation_id
    FROM result r2
    JOIN submission s2 ON s2.id = r2.submission_id
    WHERE r2.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
      AND r2.completion_date IS NOT NULL
    GROUP BY s2.participation_id
    HAVING COUNT(*) > 1
    LIMIT 10
)
AND r.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
AND r.completion_date IS NOT NULL
ORDER BY s.participation_id, r.correction_round;

-- 4.2 Find participations where same assessor did multiple rounds (should be rare)
SELECT
    s.participation_id,
    r.assessor_id,
    COUNT(*) as assessments_by_same_user,
    GROUP_CONCAT(r.correction_round ORDER BY r.correction_round) as rounds
FROM result r
JOIN submission s ON s.id = r.submission_id
WHERE r.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
  AND r.completion_date IS NOT NULL
  AND r.assessor_id IS NOT NULL
GROUP BY s.participation_id, r.assessor_id
HAVING COUNT(*) > 1
LIMIT 50;

-- =====================================================================================
-- SECTION 5: EDGE CASES - Check specific scenarios
-- =====================================================================================

-- 5.1 Results without submission (should not exist after NOT NULL migration)
SELECT COUNT(*) as orphan_results
FROM result
WHERE submission_id IS NULL;
-- Expected: 0

-- 5.2 Check programming exercises with multiple submissions per participation
-- correction_round should be based on participation, not submission
SELECT
    s.participation_id,
    COUNT(DISTINCT s.id) as submission_count,
    COUNT(DISTINCT r.id) as manual_result_count,
    MAX(r.correction_round) as max_correction_round
FROM submission s
JOIN result r ON r.submission_id = s.id
JOIN participation p ON p.id = s.participation_id
WHERE p.discriminator = 'PESP'  -- Programming exercise student participation
  AND r.assessment_type IN ('MANUAL', 'SEMI_AUTOMATIC')
  AND r.completion_date IS NOT NULL
GROUP BY s.participation_id
HAVING COUNT(DISTINCT s.id) > 1
ORDER BY submission_count DESC
LIMIT 20;

-- 5.3 Check for any results with correction_round > 1 (third correction - should be very rare)
SELECT
    r.id,
    s.participation_id,
    r.correction_round,
    r.assessment_type,
    r.completion_date
FROM result r
JOIN submission s ON s.id = r.submission_id
WHERE r.correction_round > 1
LIMIT 50;
