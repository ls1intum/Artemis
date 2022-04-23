-- CREATE TEMPORARY TABLE WITH NEW PLAGIARISM CASES TO CREATE
CREATE TEMPORARY TABLE new_plagiarism_case
SELECT
    exercise_id,
    student_id,
    GROUP_CONCAT(instructor_statement SEPARATOR ' ') as post,
    GROUP_CONCAT(student_statement SEPARATOR ' ') as answer,
    CASE
        WHEN MAX(status) > 0 THEN 'PLAGIARISM'
        WHEN MAX(status) = 0 THEN 'WARNING'
        END as verdict
    CASE
        WHEN MAX(status) >= 0 THEN CURRENT_DATE
    END as verdict_date
    CASE
        WHEN MAX(status) >= 0 THEN 0
    END as verdict_point_deduction
FROM
    (
        SELECT
            exercise_id,
            student_statement_a as student_statement,
            instructor_statement_a as instructor_statement,
            status_a as status,
            us.id as student_id
        FROM plagiarism_comparison
                 LEFT JOIN plagiarism_result pr on plagiarism_comparison.plagiarism_result_id = pr.id
                 LEFT JOIN plagiarism_submission ps on plagiarism_comparison.submission_a_id = ps.id
                 LEFT JOIN jhi_user us on ps.student_login = us.login
        WHERE status = 0
        UNION ALL
        SELECT
            exercise_id,
            student_statement_b as student_statement,
            instructor_statement_b as instructor_statement,
            status_b as status,
            us.id as student_id
        FROM plagiarism_comparison
                 LEFT JOIN plagiarism_result pr on plagiarism_comparison.plagiarism_result_id = pr.id
                 LEFT JOIN plagiarism_submission ps on plagiarism_comparison.submission_b_id = ps.id
                 LEFT JOIN jhi_user us on ps.student_login = us.login
        WHERE status = 0
    ) as temp
group by student_id, exercise_id;

-- CREATE PLAGIARISM CASES
INSERT INTO plagiarism_case (exercise_id, student_id, verdict, created_by,  verdict_date, verdict_point_deduction)
SELECT exercise_id, student_id, verdict, 'artemis_admin' as created_by, verdict_date, verdict_point_deduction
FROM new_plagiarism_case;

-- UPDATE PLAGIARISM SUBMISSIONS WITH RELATION TO PLAGIARISM CASE
UPDATE plagiarism_submission as dest,
    (
    SELECT DISTINCT ps.id, plagiarism_case_id, plagiarism_comparison_id
    FROM plagiarism_submission ps
    LEFT JOIN plagiarism_comparison pc on ps.id = pc.submission_a_id OR ps.id = pc.submission_b_id
    LEFT JOIN plagiarism_result pr on pc.plagiarism_result_id = pr.id
    LEFT JOIN jhi_user us on student_login = us.login
    LEFT JOIN plagiarism_case pp on us.id = pp.student_id
    ) as src
SET dest.plagiarism_case_id = src.plagiarism_case_id, dest.plagiarism_comparison_id = src.plagiarism_comparison_id
WHERE dest.id = src.id;

-- MIGRATE OLD INSTRUCTOR STATEMENTS TO POSTS
INSERT INTO post (title, plagiarism_case_id, content, author_id, visible_for_students, display_priority, creation_date)
SELECT 'Plagiarism Case' as title, pc.id as plagiarism_case_id, npc.post as content, 1 as author_id, true as visibile_for_students, 'NONE' as display_priotity, current_date as creation_date
FROM plagiarism_case pc
         LEFT JOIN new_plagiarism_case npc on pc.exercise_id = npc.exercise_id AND pc.student_id = npc.student_id
WHERE npc.post IS NOT NULL;

-- UPDATE PLAGIARISM CASES WITH RELATION TO POSTS
UPDATE plagiarism_case
    INNER JOIN post p on plagiarism_case.id = p.plagiarism_case_id
    SET post_id = p.id
WHERE plagiarism_case_id IS NOT NULL;

-- MIGRATE OLD STUDENT STATEMENTS TO ANSWER POSTS
INSERT INTO answer_post (post_id, author_id, content, creation_date, resolves_post)
SELECT p.id as post_id, pc.student_id as author_id, npc.answer as content, current_date as creation_date, false as resolves_post
FROM plagiarism_case pc
         JOIN new_plagiarism_case npc on pc.exercise_id = npc.exercise_id AND pc.student_id = npc.student_id
         JOIN post p on pc.id = p.plagiarism_case_id
WHERE answer IS NOT NULL;

