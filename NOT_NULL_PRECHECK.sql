-- Pre-flight check for the not-null constraints added by PR #13581 and PR #13589 (MySQL).
-- Read-only: nothing here changes any data.
--
-- Run it against a copy or a replica of the database you are about to upgrade.
--
-- How to read the result:
--
--   SKIPS THE CONSTRAINT  a row here means the changeset's precondition fails, the constraint is NOT applied on this
--                         database, and the column stays nullable. Artemis still starts. With onFail="CONTINUE" the
--                         constraint is retried on every later start, so cleaning the rows up later is enough.
--   MIGRATION DELETES     the changeset deletes these rows itself before adding the constraint. A number here is not a
--                         problem for the upgrade, but it tells you how much data the migration will remove, which is
--                         worth knowing before you run it.
--
-- Anything reporting 0 needs no attention at all.

SELECT severity, target, violating_rows FROM (

    -- ---------------------------------------------------------------- guard only: no rows are deleted for these
    SELECT 'SKIPS THE CONSTRAINT' AS severity, 'exam.course_id'                  AS target, COUNT(*) AS violating_rows FROM exam                  WHERE course_id IS NULL
    UNION ALL SELECT 'SKIPS THE CONSTRAINT', 'competency.course_id',             COUNT(*) FROM competency             WHERE course_id IS NULL
    UNION ALL SELECT 'SKIPS THE CONSTRAINT', 'student_exam.exam_id',             COUNT(*) FROM student_exam           WHERE exam_id IS NULL
    UNION ALL SELECT 'SKIPS THE CONSTRAINT', 'exercise_group.exam_id',           COUNT(*) FROM exercise_group         WHERE exam_id IS NULL
    UNION ALL SELECT 'SKIPS THE CONSTRAINT', 'team.exercise_id',                 COUNT(*) FROM team                   WHERE exercise_id IS NULL
    UNION ALL SELECT 'SKIPS THE CONSTRAINT', 'answer_post.author_id',            COUNT(*) FROM answer_post            WHERE author_id IS NULL
    UNION ALL SELECT 'SKIPS THE CONSTRAINT', 'plagiarism_result.exercise_id',    COUNT(*) FROM plagiarism_result      WHERE exercise_id IS NULL

    -- conversation.course_id: a channel on an exam exercise is removed along with everything written in it, any
    -- other kind has its course worked out from the exercise, lecture or exam it belongs to. Only a channel a tutorial
    -- group points at, or one that neither reaches, is left over.
    UNION ALL SELECT 'SKIPS THE CONSTRAINT', 'conversation.course_id (after cleanup)', COUNT(*) FROM conversation c
        WHERE c.course_id IS NULL
          AND NOT (c.exercise_id IN (SELECT id FROM exercise WHERE course_id IS NULL)
                   AND NOT EXISTS (SELECT 1 FROM tutorial_group tg WHERE tg.tutorial_group_channel_id = c.id))
          AND COALESCE(
                (SELECT e.course_id FROM exercise e WHERE e.id = c.exercise_id),
                (SELECT l.course_id FROM lecture l WHERE l.id = c.lecture_id),
                (SELECT ex.course_id FROM exam ex WHERE ex.id = c.exam_id)
              ) IS NULL

    -- post.author_id is backfilled from the course instructors first, so only what the backfill cannot reach matters
    UNION ALL SELECT 'SKIPS THE CONSTRAINT', 'post.author_id (after backfill)', COUNT(*) FROM post
        WHERE author_id IS NULL
          AND NOT EXISTS (SELECT 1 FROM user_course_role ucr
                              JOIN exercise e ON e.course_id = ucr.course_id
                              JOIN plagiarism_case pc ON pc.exercise_id = e.id
                          WHERE pc.id = post.plagiarism_case_id AND ucr.course_role = 'INSTRUCTOR')

    -- ---------------------------------------------------------------- the migration deletes these rows itself
    UNION ALL SELECT 'MIGRATION DELETES', 'result.submission_id',               COUNT(*) FROM result                 WHERE submission_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'complaint.result_id',                COUNT(*) FROM complaint              WHERE result_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'complaint_response.complaint_id',    COUNT(*) FROM complaint_response     WHERE complaint_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'build_log_entry.programming_submission_id', COUNT(*) FROM build_log_entry WHERE programming_submission_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'assessment_note.result_id',          COUNT(*) FROM assessment_note        WHERE result_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'grade_step.grading_scale_id',        COUNT(*) FROM grade_step             WHERE grading_scale_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'conversation_participant.conversation_id', COUNT(*) FROM conversation_participant WHERE conversation_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'conversation_participant.user_id',   COUNT(*) FROM conversation_participant WHERE user_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'answer_post.post_id',                COUNT(*) FROM answer_post            WHERE post_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'competency_relation.head_competency_id', COUNT(*) FROM competency_relation WHERE head_competency_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'competency_relation.tail_competency_id', COUNT(*) FROM competency_relation WHERE tail_competency_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'iris_message_content.message_id',    COUNT(*) FROM iris_message_content   WHERE message_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'lecture_transcription.lecture_unit_id', COUNT(*) FROM lecture_transcription WHERE lecture_unit_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'lecture_unit_processing_state.lecture_unit_id', COUNT(*) FROM lecture_unit_processing_state WHERE lecture_unit_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'participant_score.exercise_id',      COUNT(*) FROM participant_score      WHERE exercise_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'exam_session.student_exam_id',       COUNT(*) FROM exam_session           WHERE student_exam_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'reaction.user_id',                   COUNT(*) FROM reaction               WHERE user_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'plagiarism_comparison_matches.plagiarism_comparison_id', COUNT(*) FROM plagiarism_comparison_matches WHERE plagiarism_comparison_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'plagiarism_result_similarity_distribution.plagiarism_result_id', COUNT(*) FROM plagiarism_result_similarity_distribution WHERE plagiarism_result_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'example_submission.submission_id',   COUNT(*) FROM example_submission     WHERE submission_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'submitted_answer.submission_id',     COUNT(*) FROM submitted_answer       WHERE submission_id IS NULL
    UNION ALL SELECT 'MIGRATION DELETES', 'submitted_answer.quiz_question_id',  COUNT(*) FROM submitted_answer       WHERE quiz_question_id IS NULL
) checks
WHERE violating_rows > 0
ORDER BY severity, target;


-- If the first query returns nothing at all, every one of the 31 constraints applies cleanly and there is nothing to do.


-- ----------------------------------------------------------------------------------------------------------------
-- Deleting a result also removes what hangs off it. Run this if 'result.submission_id' came back non-zero, to see how
-- much goes with it. All of it belongs to results that carry no submission and are therefore unreachable in the UI.
-- ----------------------------------------------------------------------------------------------------------------

SELECT 'feedback'            AS also_deleted, COUNT(*) AS rows_affected FROM feedback            WHERE result_id IN (SELECT id FROM result WHERE submission_id IS NULL)
UNION ALL SELECT 'long_feedback_text', COUNT(*) FROM long_feedback_text WHERE feedback_id IN (SELECT id FROM feedback WHERE result_id IN (SELECT id FROM result WHERE submission_id IS NULL))
UNION ALL SELECT 'text_block',         COUNT(*) FROM text_block         WHERE feedback_id IN (SELECT id FROM feedback WHERE result_id IN (SELECT id FROM result WHERE submission_id IS NULL))
UNION ALL SELECT 'result_rating',      COUNT(*) FROM result_rating      WHERE result_id IN (SELECT id FROM result WHERE submission_id IS NULL)
UNION ALL SELECT 'assessment_note',    COUNT(*) FROM assessment_note    WHERE result_id IN (SELECT id FROM result WHERE submission_id IS NULL)
UNION ALL SELECT 'complaint',          COUNT(*) FROM complaint          WHERE result_id IN (SELECT id FROM result WHERE submission_id IS NULL)
UNION ALL SELECT 'complaint_response', COUNT(*) FROM complaint_response WHERE complaint_id IN (SELECT id FROM complaint WHERE result_id IN (SELECT id FROM result WHERE submission_id IS NULL))
UNION ALL SELECT 'participant_score (reference cleared, row kept)', COUNT(*) FROM participant_score WHERE last_result_id IN (SELECT id FROM result WHERE submission_id IS NULL) OR last_rated_result_id IN (SELECT id FROM result WHERE submission_id IS NULL)
UNION ALL SELECT 'build_job (reference cleared, row kept)', COUNT(*) FROM build_job WHERE result_id IN (SELECT id FROM result WHERE submission_id IS NULL);
