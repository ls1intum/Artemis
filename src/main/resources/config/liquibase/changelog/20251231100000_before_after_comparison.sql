-- ================================================================================
-- BEFORE/AFTER COMPARISON SCRIPT FOR ORPHAN CLEANUP MIGRATION (20251231100000)
-- ================================================================================
--
-- INSTRUCTIONS:
-- 1. Run this script BEFORE migration and save output to: before_migration.txt
-- 2. Run the Liquibase migration
-- 3. Run this script AFTER migration and save output to: after_migration.txt
-- 4. Compare the two files - use: diff before_migration.txt after_migration.txt
--
-- EXPECTED RESULTS:
-- - "orphan_count" values should change from >0 to 0
-- - "total_count" should decrease by exactly the number of orphans + cascades
-- - "valid_count" should remain UNCHANGED for most tables
-- - EXCEPTION: participation "valid (has exercise)" count will INCREASE because
--   TPEP/SPEP participations will have their exercise_id backfilled
--
-- IMPORTANT: This migration backfills exercise_id for TPEP (template) and SPEP
-- (solution) participations from exercise.template_participation_id and
-- exercise.solution_participation_id. TPEP/SPEP participations not referenced
-- by any exercise are considered orphans and will be deleted.
-- ================================================================================

-- Create a timestamp marker
SELECT '=== MIGRATION VERIFICATION REPORT ===' AS report;
SELECT NOW() AS report_timestamp;

-- ================================================================================
-- PART A: ORPHAN COUNTS (should be 0 AFTER migration)
-- ================================================================================

SELECT '--- ORPHAN COUNTS ---' AS section;

SELECT 'complaint_response' AS table_name,
       'orphan (complaint_id IS NULL)' AS type,
       COUNT(*) AS count
FROM complaint_response WHERE complaint_id IS NULL
UNION ALL
SELECT 'complaint', 'orphan (result_id IS NULL)', COUNT(*) FROM complaint WHERE result_id IS NULL
UNION ALL
SELECT 'feedback', 'orphan (result_id IS NULL)', COUNT(*) FROM feedback WHERE result_id IS NULL
UNION ALL
SELECT 'result', 'orphan (submission_id IS NULL)', COUNT(*) FROM result WHERE submission_id IS NULL
UNION ALL
SELECT 'submission', 'orphan (participation_id IS NULL)', COUNT(*) FROM submission WHERE participation_id IS NULL
UNION ALL
SELECT 'participation', 'orphan (exercise_id IS NULL, non-TPEP/SPEP)', COUNT(*) FROM participation WHERE exercise_id IS NULL AND discriminator NOT IN ('TPEP', 'SPEP')
UNION ALL
SELECT 'participation', 'TPEP/SPEP to backfill (referenced by exercise)', COUNT(*)
FROM participation p
WHERE p.exercise_id IS NULL
  AND p.discriminator IN ('TPEP', 'SPEP')
  AND EXISTS (SELECT 1 FROM exercise e WHERE e.template_participation_id = p.id OR e.solution_participation_id = p.id)
UNION ALL
SELECT 'participation', 'TPEP/SPEP orphans (NOT referenced, will be deleted)', COUNT(*)
FROM participation p
WHERE p.exercise_id IS NULL
  AND p.discriminator IN ('TPEP', 'SPEP')
  AND NOT EXISTS (SELECT 1 FROM exercise e WHERE e.template_participation_id = p.id OR e.solution_participation_id = p.id);

-- ================================================================================
-- PART B: TOTAL COUNTS (will decrease after migration)
-- ================================================================================

SELECT '--- TOTAL COUNTS ---' AS section;

SELECT 'complaint_response' AS table_name, COUNT(*) AS total_count FROM complaint_response
UNION ALL SELECT 'complaint', COUNT(*) FROM complaint
UNION ALL SELECT 'feedback', COUNT(*) FROM feedback
UNION ALL SELECT 'long_feedback_text', COUNT(*) FROM long_feedback_text
UNION ALL SELECT 'result', COUNT(*) FROM result
UNION ALL SELECT 'result_rating', COUNT(*) FROM result_rating
UNION ALL SELECT 'assessment_note', COUNT(*) FROM assessment_note
UNION ALL SELECT 'submission', COUNT(*) FROM submission
UNION ALL SELECT 'build_log_entry', COUNT(*) FROM build_log_entry
UNION ALL SELECT 'submitted_answer', COUNT(*) FROM submitted_answer
UNION ALL SELECT 'coverage_report', COUNT(*) FROM coverage_report
UNION ALL SELECT 'coverage_file_report', COUNT(*) FROM coverage_file_report
UNION ALL SELECT 'testwise_coverage_report_entry', COUNT(*) FROM testwise_coverage_report_entry
UNION ALL SELECT 'text_block', COUNT(*) FROM text_block
UNION ALL SELECT 'participation', COUNT(*) FROM participation
UNION ALL SELECT 'participation_vcs_access_token', COUNT(*) FROM participation_vcs_access_token
UNION ALL SELECT 'participant_score', COUNT(*) FROM participant_score
UNION ALL SELECT 'example_submission', COUNT(*) FROM example_submission;

-- ================================================================================
-- PART C: VALID RECORD COUNTS
-- These counts should remain UNCHANGED for most tables after migration.
-- EXCEPTION: participation valid count will INCREASE because TPEP/SPEP
-- participations will have their exercise_id backfilled from the exercise table.
-- ================================================================================

SELECT '--- VALID RECORD COUNTS ---' AS section;

-- Valid records = records with valid parent references
-- After migration, all records should have valid parent references (NOT NULL)
-- Note: participation valid count will increase due to TPEP/SPEP backfill
SELECT 'complaint_response' AS table_name,
       'valid (has complaint)' AS type,
       COUNT(*) AS count
FROM complaint_response WHERE complaint_id IS NOT NULL
UNION ALL
SELECT 'complaint', 'valid (has result)', COUNT(*)
FROM complaint WHERE result_id IS NOT NULL
UNION ALL
SELECT 'feedback', 'valid (has result)', COUNT(*)
FROM feedback WHERE result_id IS NOT NULL
UNION ALL
SELECT 'result', 'valid (has submission)', COUNT(*)
FROM result WHERE submission_id IS NOT NULL
UNION ALL
SELECT 'submission', 'valid (has participation)', COUNT(*)
FROM submission WHERE participation_id IS NOT NULL
UNION ALL
SELECT 'participation', 'valid (has exercise)', COUNT(*)
FROM participation WHERE exercise_id IS NOT NULL;

-- ================================================================================
-- PART D: RECORDS WITH FULL VALID CHAIN (ultimate integrity check)
-- These counts should remain UNCHANGED after migration
-- ================================================================================

SELECT '--- RECORDS WITH COMPLETE VALID CHAIN (MUST NOT CHANGE!) ---' AS section;

-- Complaints with full valid chain: complaint -> result -> submission -> participation -> exercise
SELECT 'complaints with full valid chain' AS metric,
       COUNT(*) AS count
FROM complaint c
JOIN result r ON c.result_id = r.id
JOIN submission s ON r.submission_id = s.id
JOIN participation p ON s.participation_id = p.id
WHERE p.exercise_id IS NOT NULL;

-- Feedback with full valid chain: feedback -> result -> submission -> participation -> exercise
SELECT 'feedback with full valid chain' AS metric,
       COUNT(*) AS count
FROM feedback f
JOIN result r ON f.result_id = r.id
JOIN submission s ON r.submission_id = s.id
JOIN participation p ON s.participation_id = p.id
WHERE p.exercise_id IS NOT NULL;

-- Results with full valid chain: result -> submission -> participation -> exercise
SELECT 'results with full valid chain' AS metric,
       COUNT(*) AS count
FROM result r
JOIN submission s ON r.submission_id = s.id
JOIN participation p ON s.participation_id = p.id
WHERE p.exercise_id IS NOT NULL;

-- Submissions with full valid chain: submission -> participation -> exercise
SELECT 'submissions with full valid chain' AS metric,
       COUNT(*) AS count
FROM submission s
JOIN participation p ON s.participation_id = p.id
WHERE p.exercise_id IS NOT NULL;

-- ================================================================================
-- PART E: CRITICAL CHECK - TEMPLATE/SOLUTION PARTICIPATION INTEGRITY
-- If any of these return rows > 0, DO NOT RUN THE MIGRATION until fixed!
-- ================================================================================

SELECT '--- CRITICAL: TEMPLATE/SOLUTION PARTICIPATION CHECK ---' AS section;

-- Check for orphan participations that are still referenced as template participations
SELECT 'CRITICAL: Orphan participations referenced as template_participation' AS check_name,
       COUNT(*) AS count
FROM participation p
JOIN exercise e ON e.template_participation_id = p.id
WHERE p.exercise_id IS NULL;

-- Check for orphan participations that are still referenced as solution participations
SELECT 'CRITICAL: Orphan participations referenced as solution_participation' AS check_name,
       COUNT(*) AS count
FROM participation p
JOIN exercise e ON e.solution_participation_id = p.id
WHERE p.exercise_id IS NULL;

-- Show details of problematic template participations (if any)
SELECT 'Details of orphan template participations still referenced by exercises:' AS info;
SELECT p.id AS participation_id,
       p.exercise_id AS participation_exercise_id,
       e.id AS referencing_exercise_id,
       e.title AS exercise_title
FROM participation p
JOIN exercise e ON e.template_participation_id = p.id
WHERE p.exercise_id IS NULL
LIMIT 10;

-- Show details of problematic solution participations (if any)
SELECT 'Details of orphan solution participations still referenced by exercises:' AS info;
SELECT p.id AS participation_id,
       p.exercise_id AS participation_exercise_id,
       e.id AS referencing_exercise_id,
       e.title AS exercise_title
FROM participation p
JOIN exercise e ON e.solution_participation_id = p.id
WHERE p.exercise_id IS NULL
LIMIT 10;

-- ================================================================================
-- PART F: SAMPLE ORPHAN DATA (for manual review before deletion)
-- Shows a few example orphan records so you can verify they should be deleted
-- ================================================================================

SELECT '--- SAMPLE ORPHAN RECORDS (first 5 of each type) ---' AS section;

SELECT 'Sample orphan complaint_responses:' AS info;
SELECT id, complaint_id, submitted_time
FROM complaint_response
WHERE complaint_id IS NULL
LIMIT 5;

SELECT 'Sample orphan complaints:' AS info;
SELECT id, result_id, complaint_type, submitted_time
FROM complaint
WHERE result_id IS NULL
LIMIT 5;

SELECT 'Sample orphan feedback:' AS info;
SELECT id, result_id, type, text
FROM feedback
WHERE result_id IS NULL
LIMIT 5;

SELECT 'Sample orphan results:' AS info;
SELECT id, submission_id, score, completion_date
FROM result
WHERE submission_id IS NULL
LIMIT 5;

SELECT 'Sample orphan submissions:' AS info;
SELECT id, participation_id, submitted, submission_date
FROM submission
WHERE participation_id IS NULL
LIMIT 5;

SELECT 'Sample orphan participations (non-TPEP/SPEP, will be deleted):' AS info;
SELECT id, exercise_id, discriminator, student_id, initialization_state
FROM participation
WHERE exercise_id IS NULL
  AND discriminator NOT IN ('TPEP', 'SPEP')
LIMIT 5;

SELECT 'Sample TPEP/SPEP participations to backfill (referenced by exercise):' AS info;
SELECT p.id, p.exercise_id, p.discriminator, p.repository_url,
       e.id AS referencing_exercise_id
FROM participation p
JOIN exercise e ON e.template_participation_id = p.id OR e.solution_participation_id = p.id
WHERE p.exercise_id IS NULL
  AND p.discriminator IN ('TPEP', 'SPEP')
LIMIT 5;

SELECT 'Sample TPEP/SPEP orphans (NOT referenced by any exercise, will be deleted):' AS info;
SELECT id, exercise_id, discriminator, repository_url
FROM participation p
WHERE p.exercise_id IS NULL
  AND p.discriminator IN ('TPEP', 'SPEP')
  AND NOT EXISTS (SELECT 1 FROM exercise e WHERE e.template_participation_id = p.id OR e.solution_participation_id = p.id)
LIMIT 5;

SELECT '=== END OF REPORT ===' AS report;
