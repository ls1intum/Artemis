-- CREATE TEMPORARY TABLE WITH NEW PLAGIARISM CASES TO CREATE
CREATE TEMPORARY TABLE new_plagiarism_case
SELECT
    exercise_id,
    student_id,
    team_id,
    GROUP_CONCAT(instructor_statement SEPARATOR ' ') as post,
    GROUP_CONCAT(student_statement SEPARATOR ' ') as answer,
    CASE
        WHEN MAX(status) > 0 THEN 'PLAGIARISM'
        WHEN MAX(status) = 0 THEN 'WARNING'
        END as verdict,
    CASE
        WHEN MAX(status) >= 0 THEN CURRENT_DATE
    END as verdict_date,
    0 as verdict_point_deduction
FROM
    (
        SELECT
            pr.exercise_id,
            student_statement_a as student_statement,
            instructor_statement_a as instructor_statement,
            status_a as status,
            us.id as student_id,
            t.id as team_id
        FROM plagiarism_comparison
                 LEFT JOIN plagiarism_result pr on plagiarism_comparison.plagiarism_result_id = pr.id
                 LEFT JOIN plagiarism_submission ps on plagiarism_comparison.submission_a_id = ps.id
                 LEFT JOIN submission s on ps.submission_id = s.id
                 LEFT JOIN participation p on s.participation_id = p.id
                 LEFT JOIN jhi_user us on p.student_id = us.id
                 LEFT JOIN team t on p.team_id = t.id
        WHERE status = 0
        UNION ALL
        SELECT
            pr.exercise_id,
            student_statement_b as student_statement,
            instructor_statement_b as instructor_statement,
            status_b as status,
            us.id as student_id,
            t.id as team_id
        FROM plagiarism_comparison
                 LEFT JOIN plagiarism_result pr on plagiarism_comparison.plagiarism_result_id = pr.id
                 LEFT JOIN plagiarism_submission ps on plagiarism_comparison.submission_b_id = ps.id
                 LEFT JOIN submission s on ps.submission_id = s.id
                 LEFT JOIN participation p on s.participation_id = p.id
                 LEFT JOIN jhi_user us on p.student_id = us.id
                 LEFT JOIN team t on p.team_id = t.id
        WHERE status = 0
    ) as temp
group by student_id, team_id, exercise_id;

-- CREATE PLAGIARISM CASES
INSERT INTO plagiarism_case (exercise_id, student_id, team_id, verdict, created_by, verdict_date, verdict_point_deduction, verdict_by_id)
WITH instructor AS (
    (
        SELECT e.id as exercise_id, min(ju.login) as instructor_login
        FROM exercise e
                 LEFT JOIN exercise_group eg on e.exercise_group_id = eg.id
                 LEFT JOIN exam on eg.exam_id = exam.id
                 LEFT JOIN course c on e.course_id = c.id or exam.course_id = c.id
                 LEFT JOIN user_groups ug on c.instructor_group_name = ug.groups
                 LEFT JOIN jhi_user ju on ug.user_id = ju.id
        group by e.id
    )
)
SELECT npc.exercise_id, student_id, team_id, verdict, instructor_login as created_by, verdict_date, verdict_point_deduction, u.id as verdict_by_id
FROM new_plagiarism_case npc
    LEFT JOIN instructor i on i.exercise_id = npc.exercise_id
    LEFT JOIN jhi_user u on u.login = instructor_login
WHERE student_id IS NOT NULL OR team_id IS NOT NULL;

-- UPDATE PLAGIARISM SUBMISSIONS WITH RELATION TO PLAGIARISM CASE
UPDATE plagiarism_submission as dest,
    (
        SELECT DISTINCT
            ps.id,
            pp.id as plagiarism_case_id,
            pc.id as plagiarism_comparison_id
        FROM plagiarism_submission ps
                 LEFT JOIN plagiarism_comparison pc on ps.id = pc.submission_a_id or ps.id = pc.submission_b_id
                 LEFT JOIN jhi_user us on student_login = us.login
                 LEFT JOIN plagiarism_result pr on pc.plagiarism_result_id = pr.id
                 LEFT JOIN plagiarism_case pp on us.id = pp.student_id AND pr.exercise_id = pp.exercise_id
        WHERE pc.id IS NOT NULL
    ) as src
SET dest.plagiarism_case_id = src.plagiarism_case_id, dest.plagiarism_comparison_id = src.plagiarism_comparison_id
WHERE dest.id = src.id;

-- MIGRATE OLD INSTRUCTOR STATEMENTS TO POSTS
INSERT INTO post (title, plagiarism_case_id, content, author_id, visible_for_students, display_priority, creation_date)
SELECT 'Plagiarism Case' as title, pc.id as plagiarism_case_id, npc.post as content, u.id as author_id, true as visibile_for_students, 'NONE' as display_priotity, current_date as creation_date
FROM plagiarism_case pc
         LEFT JOIN new_plagiarism_case npc on pc.exercise_id = npc.exercise_id AND pc.student_id = npc.student_id
         LEFT JOIN jhi_user u on pc.created_by = u.login
WHERE npc.post IS NOT NULL;

-- MIGRATE OLD STUDENT STATEMENTS TO ANSWER POSTS
INSERT INTO answer_post (post_id, author_id, content, creation_date, resolves_post)
SELECT p.id as post_id, pc.student_id as author_id, npc.answer as content, current_date as creation_date, false as resolves_post
FROM plagiarism_case pc
         JOIN new_plagiarism_case npc on pc.exercise_id = npc.exercise_id AND pc.student_id = npc.student_id
         JOIN post p on pc.id = p.plagiarism_case_id
WHERE answer IS NOT NULL;
