-- ================================================================================
-- VERIFICATION SCRIPT FOR ORPHAN CLEANUP MIGRATION (20251231100000)
-- ================================================================================
--
-- Run this script BEFORE and AFTER the migration to verify:
-- 1. Only orphan records are deleted
-- 2. No legitimate data is lost
-- 3. All NOT NULL constraints can be safely applied
--
-- Save the output from BEFORE migration and compare with AFTER migration.
-- The difference in total counts should exactly match the orphan counts.
--
-- IMPORTANT: This migration also backfills exercise_id for TPEP (template) and
-- SPEP (solution) participations from exercise.template_participation_id and
-- exercise.solution_participation_id. TPEP/SPEP participations not referenced
-- by any exercise are considered orphans and will be deleted.
--
-- This script works for both MySQL and PostgreSQL.
-- ================================================================================

-- ================================================================================
-- SECTION 1: COUNT ORPHANS (records that WILL be deleted)
-- These counts should be 0 AFTER the migration
-- ================================================================================

SELECT '=== ORPHAN COUNTS (should be 0 after migration) ===' AS section;

-- Phase 1: Orphan complaint_responses (no complaint)
SELECT 'complaint_response WHERE complaint_id IS NULL' AS entity,
       COUNT(*) AS orphan_count
FROM complaint_response
WHERE complaint_id IS NULL;

-- Phase 2: Orphan complaints (no result)
SELECT 'complaint WHERE result_id IS NULL' AS entity,
       COUNT(*) AS orphan_count
FROM complaint
WHERE result_id IS NULL;

-- Phase 3: Orphan feedback (no result)
SELECT 'feedback WHERE result_id IS NULL' AS entity,
       COUNT(*) AS orphan_count
FROM feedback
WHERE result_id IS NULL;

-- Phase 4: Orphan results (no submission)
SELECT 'result WHERE submission_id IS NULL' AS entity,
       COUNT(*) AS orphan_count
FROM result
WHERE submission_id IS NULL;

-- Phase 5: Orphan submissions (no participation)
SELECT 'submission WHERE participation_id IS NULL' AS entity,
       COUNT(*) AS orphan_count
FROM submission
WHERE participation_id IS NULL;

-- Phase 6: Orphan participations (no exercise)
SELECT 'participation WHERE exercise_id IS NULL (all types)' AS entity,
       COUNT(*) AS orphan_count
FROM participation
WHERE exercise_id IS NULL;

-- TPEP/SPEP participations with NULL exercise_id that CAN be backfilled (referenced by an exercise)
SELECT 'TPEP/SPEP with NULL exercise_id (will be backfilled)' AS entity,
       COUNT(*) AS will_backfill_count
FROM participation p
WHERE p.exercise_id IS NULL
  AND p.discriminator IN ('TPEP', 'SPEP')
  AND EXISTS (
    SELECT 1 FROM exercise e
    WHERE e.template_participation_id = p.id OR e.solution_participation_id = p.id
  );

-- TPEP/SPEP participations with NULL exercise_id that CANNOT be backfilled (orphans - will be deleted)
SELECT 'TPEP/SPEP with NULL exercise_id (orphans, will be deleted)' AS entity,
       COUNT(*) AS orphan_count
FROM participation p
WHERE p.exercise_id IS NULL
  AND p.discriminator IN ('TPEP', 'SPEP')
  AND NOT EXISTS (
    SELECT 1 FROM exercise e
    WHERE e.template_participation_id = p.id OR e.solution_participation_id = p.id
  );

-- ================================================================================
-- SECTION 2: COUNT TOTAL RECORDS
-- Compare BEFORE and AFTER - difference should equal orphan counts
-- ================================================================================

SELECT '=== TOTAL RECORD COUNTS ===' AS section;

SELECT 'complaint_response' AS table_name, COUNT(*) AS total_count FROM complaint_response;
SELECT 'complaint' AS table_name, COUNT(*) AS total_count FROM complaint;
SELECT 'feedback' AS table_name, COUNT(*) AS total_count FROM feedback;
SELECT 'long_feedback_text' AS table_name, COUNT(*) AS total_count FROM long_feedback_text;
SELECT 'result' AS table_name, COUNT(*) AS total_count FROM result;
SELECT 'result_rating' AS table_name, COUNT(*) AS total_count FROM result_rating;
SELECT 'assessment_note' AS table_name, COUNT(*) AS total_count FROM assessment_note;
SELECT 'submission' AS table_name, COUNT(*) AS total_count FROM submission;
SELECT 'build_log_entry' AS table_name, COUNT(*) AS total_count FROM build_log_entry;
SELECT 'submitted_answer' AS table_name, COUNT(*) AS total_count FROM submitted_answer;
SELECT 'coverage_report' AS table_name, COUNT(*) AS total_count FROM coverage_report;
SELECT 'coverage_file_report' AS table_name, COUNT(*) AS total_count FROM coverage_file_report;
SELECT 'testwise_coverage_report_entry' AS table_name, COUNT(*) AS total_count FROM testwise_coverage_report_entry;
SELECT 'text_block' AS table_name, COUNT(*) AS total_count FROM text_block;
SELECT 'participation' AS table_name, COUNT(*) AS total_count FROM participation;
SELECT 'participation_vcs_access_token' AS table_name, COUNT(*) AS total_count FROM participation_vcs_access_token;
SELECT 'participant_score' AS table_name, COUNT(*) AS total_count FROM participant_score;
SELECT 'example_submission' AS table_name, COUNT(*) AS total_count FROM example_submission;

-- ================================================================================
-- SECTION 3: COUNT CASCADING DELETES (child records of orphans)
-- These are records that will be deleted because their parent is an orphan
-- ================================================================================

SELECT '=== CASCADING DELETE COUNTS (children of orphans) ===' AS section;

-- Children of orphan complaints (result_id IS NULL)
SELECT 'complaint_response for orphan complaints' AS entity,
       COUNT(*) AS cascade_count
FROM complaint_response cr
JOIN complaint c ON cr.complaint_id = c.id
WHERE c.result_id IS NULL;

-- Children of orphan feedback (result_id IS NULL)
SELECT 'long_feedback_text for orphan feedback' AS entity,
       COUNT(*) AS cascade_count
FROM long_feedback_text lft
JOIN feedback f ON lft.feedback_id = f.id
WHERE f.result_id IS NULL;

-- Children of orphan results (submission_id IS NULL)
SELECT 'feedback for orphan results' AS entity,
       COUNT(*) AS cascade_count
FROM feedback f
JOIN result r ON f.result_id = r.id
WHERE r.submission_id IS NULL;

SELECT 'long_feedback_text for orphan results (via feedback)' AS entity,
       COUNT(*) AS cascade_count
FROM long_feedback_text lft
JOIN feedback f ON lft.feedback_id = f.id
JOIN result r ON f.result_id = r.id
WHERE r.submission_id IS NULL;

SELECT 'assessment_note for orphan results' AS entity,
       COUNT(*) AS cascade_count
FROM assessment_note a
JOIN result r ON a.result_id = r.id
WHERE r.submission_id IS NULL;

SELECT 'result_rating for orphan results' AS entity,
       COUNT(*) AS cascade_count
FROM result_rating rr
JOIN result r ON rr.result_id = r.id
WHERE r.submission_id IS NULL;

SELECT 'complaint for orphan results' AS entity,
       COUNT(*) AS cascade_count
FROM complaint c
JOIN result r ON c.result_id = r.id
WHERE r.submission_id IS NULL;

SELECT 'complaint_response for orphan results (via complaint)' AS entity,
       COUNT(*) AS cascade_count
FROM complaint_response cr
JOIN complaint c ON cr.complaint_id = c.id
JOIN result r ON c.result_id = r.id
WHERE r.submission_id IS NULL;

-- Children of orphan submissions (participation_id IS NULL)
SELECT 'result for orphan submissions' AS entity,
       COUNT(*) AS cascade_count
FROM result r
JOIN submission s ON r.submission_id = s.id
WHERE s.participation_id IS NULL;

SELECT 'build_log_entry for orphan submissions' AS entity,
       COUNT(*) AS cascade_count
FROM build_log_entry ble
JOIN submission s ON ble.programming_submission_id = s.id
WHERE s.participation_id IS NULL;

SELECT 'submitted_answer for orphan submissions' AS entity,
       COUNT(*) AS cascade_count
FROM submitted_answer sa
JOIN submission s ON sa.submission_id = s.id
WHERE s.participation_id IS NULL;

SELECT 'coverage_report for orphan submissions' AS entity,
       COUNT(*) AS cascade_count
FROM coverage_report cr
JOIN submission s ON cr.submission_id = s.id
WHERE s.participation_id IS NULL;

SELECT 'text_block for orphan submissions' AS entity,
       COUNT(*) AS cascade_count
FROM text_block tb
JOIN submission s ON tb.submission_id = s.id
WHERE s.participation_id IS NULL;

-- Children of orphan participations (exercise_id IS NULL)
SELECT 'submission for orphan participations' AS entity,
       COUNT(*) AS cascade_count
FROM submission s
JOIN participation p ON s.participation_id = p.id
WHERE p.exercise_id IS NULL;

SELECT 'participation_vcs_access_token for orphan participations' AS entity,
       COUNT(*) AS cascade_count
FROM participation_vcs_access_token pvat
JOIN participation p ON pvat.participation_id = p.id
WHERE p.exercise_id IS NULL;

-- ================================================================================
-- SECTION 4: CRITICAL - TEMPLATE/SOLUTION PARTICIPATION CHECK
-- If any of these return > 0, the migration WILL FAIL due to FK constraints!
-- FIX THESE ISSUES BEFORE RUNNING THE MIGRATION!
-- ================================================================================

SELECT '=== CRITICAL: TEMPLATE/SOLUTION PARTICIPATION CHECK ===' AS section;

-- Check for orphan participations still referenced as template participations
-- These would cause FK violation when trying to delete
SELECT 'BLOCKER: Orphan participations referenced as template_participation_id' AS check_name,
       COUNT(*) AS count,
       CASE WHEN COUNT(*) > 0 THEN 'MIGRATION WILL FAIL - FIX FIRST!' ELSE 'OK' END AS status
FROM participation p
JOIN exercise e ON e.template_participation_id = p.id
WHERE p.exercise_id IS NULL;

-- Check for orphan participations still referenced as solution participations
SELECT 'BLOCKER: Orphan participations referenced as solution_participation_id' AS check_name,
       COUNT(*) AS count,
       CASE WHEN COUNT(*) > 0 THEN 'MIGRATION WILL FAIL - FIX FIRST!' ELSE 'OK' END AS status
FROM participation p
JOIN exercise e ON e.solution_participation_id = p.id
WHERE p.exercise_id IS NULL;

-- If issues found, show details for manual fix
SELECT 'Problematic template participations (exercise_id IS NULL but referenced):' AS info;
SELECT p.id AS participation_id,
       p.discriminator,
       e.id AS exercise_id,
       e.title AS exercise_title,
       'FIX: UPDATE participation SET exercise_id = ' || e.id || ' WHERE id = ' || p.id AS suggested_fix
FROM participation p
JOIN exercise e ON e.template_participation_id = p.id
WHERE p.exercise_id IS NULL;

SELECT 'Problematic solution participations (exercise_id IS NULL but referenced):' AS info;
SELECT p.id AS participation_id,
       p.discriminator,
       e.id AS exercise_id,
       e.title AS exercise_title,
       'FIX: UPDATE participation SET exercise_id = ' || e.id || ' WHERE id = ' || p.id AS suggested_fix
FROM participation p
JOIN exercise e ON e.solution_participation_id = p.id
WHERE p.exercise_id IS NULL;

-- ================================================================================
-- SECTION 5: ADDITIONAL DATA INTEGRITY CHECKS
-- These counts should be 0 - if not, there may be data integrity issues
-- ================================================================================

SELECT '=== ADDITIONAL DATA INTEGRITY CHECKS (should all be 0) ===' AS section;

-- Check for complaints with valid results that somehow have result_id NULL
-- (This would indicate a bug if count > 0)
SELECT 'complaints with NULL result_id but valid complaint_response' AS check_name,
       COUNT(*) AS issue_count
FROM complaint c
WHERE c.result_id IS NULL
  AND EXISTS (SELECT 1 FROM complaint_response cr WHERE cr.complaint_id = c.id);

-- Check for results with NULL submission_id but valid participation chain
SELECT 'results with NULL submission_id but has feedback' AS check_name,
       COUNT(*) AS issue_count
FROM result r
WHERE r.submission_id IS NULL
  AND EXISTS (SELECT 1 FROM feedback f WHERE f.result_id = r.id);

-- ================================================================================
-- SECTION 6: VERIFY FOREIGN KEY REFERENCES THAT USE SET NULL
-- These will be SET to NULL, not deleted
-- ================================================================================

SELECT '=== SET NULL REFERENCES (will be nullified, not deleted) ===' AS section;

-- participant_score references to orphan results
SELECT 'participant_score.last_result_id pointing to orphan results' AS entity,
       COUNT(*) AS will_be_nullified
FROM participant_score ps
JOIN result r ON ps.last_result_id = r.id
WHERE r.submission_id IS NULL;

SELECT 'participant_score.last_rated_result_id pointing to orphan results' AS entity,
       COUNT(*) AS will_be_nullified
FROM participant_score ps
JOIN result r ON ps.last_rated_result_id = r.id
WHERE r.submission_id IS NULL;

-- example_submission references to orphan submissions
SELECT 'example_submission.submission_id pointing to orphan submissions' AS entity,
       COUNT(*) AS will_be_nullified
FROM example_submission es
JOIN submission s ON es.submission_id = s.id
WHERE s.participation_id IS NULL;

-- ================================================================================
-- SECTION 7: POST-MIGRATION VALIDATION
-- Run these AFTER migration to verify constraints can be applied
-- ================================================================================

SELECT '=== POST-MIGRATION VALIDATION (run after migration) ===' AS section;

-- These should ALL return 0 after successful migration
SELECT 'complaint_response with NULL complaint_id (should be 0)' AS validation,
       COUNT(*) AS count FROM complaint_response WHERE complaint_id IS NULL;

SELECT 'complaint with NULL result_id (should be 0)' AS validation,
       COUNT(*) AS count FROM complaint WHERE result_id IS NULL;

SELECT 'feedback with NULL result_id (should be 0)' AS validation,
       COUNT(*) AS count FROM feedback WHERE result_id IS NULL;

SELECT 'result with NULL submission_id (should be 0)' AS validation,
       COUNT(*) AS count FROM result WHERE submission_id IS NULL;

SELECT 'submission with NULL participation_id (should be 0)' AS validation,
       COUNT(*) AS count FROM submission WHERE participation_id IS NULL;

SELECT 'participation with NULL exercise_id (should be 0)' AS validation,
       COUNT(*) AS count FROM participation WHERE exercise_id IS NULL;

-- ================================================================================
-- SECTION 8: SUMMARY CALCULATION HELPER
-- Use these to calculate expected changes
-- ================================================================================

SELECT '=== SUMMARY: EXPECTED DELETIONS ===' AS section;

-- This query gives a summary of all records that will be deleted
-- Run BEFORE migration and save the output
SELECT
    'Total complaint_response to delete' AS metric,
    (SELECT COUNT(*) FROM complaint_response WHERE complaint_id IS NULL) +
    (SELECT COUNT(*) FROM complaint_response cr JOIN complaint c ON cr.complaint_id = c.id WHERE c.result_id IS NULL) +
    (SELECT COUNT(*) FROM complaint_response cr JOIN complaint c ON cr.complaint_id = c.id JOIN result r ON c.result_id = r.id WHERE r.submission_id IS NULL) AS count;

SELECT
    'Total complaint to delete' AS metric,
    (SELECT COUNT(*) FROM complaint WHERE result_id IS NULL) +
    (SELECT COUNT(*) FROM complaint c JOIN result r ON c.result_id = r.id WHERE r.submission_id IS NULL) AS count;

SELECT
    'Total feedback to delete' AS metric,
    (SELECT COUNT(*) FROM feedback WHERE result_id IS NULL) +
    (SELECT COUNT(*) FROM feedback f JOIN result r ON f.result_id = r.id WHERE r.submission_id IS NULL) AS count;

SELECT
    'Total result to delete' AS metric,
    (SELECT COUNT(*) FROM result WHERE submission_id IS NULL) +
    (SELECT COUNT(*) FROM result r JOIN submission s ON r.submission_id = s.id WHERE s.participation_id IS NULL) AS count;

SELECT
    'Total submission to delete' AS metric,
    (SELECT COUNT(*) FROM submission WHERE participation_id IS NULL) +
    (SELECT COUNT(*) FROM submission s JOIN participation p ON s.participation_id = p.id WHERE p.exercise_id IS NULL) AS count;

SELECT
    'Total participation to delete (non-TPEP/SPEP orphans)' AS metric,
    (SELECT COUNT(*) FROM participation WHERE exercise_id IS NULL AND discriminator NOT IN ('TPEP', 'SPEP')) AS count;

SELECT
    'TPEP/SPEP to backfill (referenced by exercise)' AS metric,
    (SELECT COUNT(*) FROM participation p
     WHERE p.discriminator IN ('TPEP', 'SPEP')
       AND p.exercise_id IS NULL
       AND EXISTS (SELECT 1 FROM exercise e WHERE e.template_participation_id = p.id OR e.solution_participation_id = p.id)) AS count;

SELECT
    'TPEP/SPEP orphans to delete (NOT referenced by any exercise)' AS metric,
    (SELECT COUNT(*) FROM participation p
     WHERE p.discriminator IN ('TPEP', 'SPEP')
       AND p.exercise_id IS NULL
       AND NOT EXISTS (SELECT 1 FROM exercise e WHERE e.template_participation_id = p.id OR e.solution_participation_id = p.id)) AS count;
