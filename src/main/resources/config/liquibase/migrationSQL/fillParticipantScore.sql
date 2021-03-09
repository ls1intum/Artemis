-- FILL IN LAST SCORE INDIVIDUAL
INSERT INTO participant_score(user_id, exercise_id, last_result_id, last_score, discriminator, last_points)
WITH last_result AS (
    SELECT DISTINCT e.id         as "exercise_id",
                    e.max_points as "exercise_max_points",
                    p.student_id as "student_id",
                    r.id         as "last_result_id",
                    r.score      as "last_result_score"
    FROM exercise e
             JOIN participation p on e.id = p.exercise_id
             JOIN result r on r.participation_id = p.id
    WHERE e.mode = 'INDIVIDUAL'
      AND p.student_id IS NOT NULL
      AND (p.test_run IS NULL OR p.test_run = 0)
      AND (
            p.discriminator = 'SP'
            OR p.discriminator = 'PESP'
        )
      AND r.score IS NOT NULL
      AND r.completion_date IS NOT NULL
      AND NOT EXISTS(
            SELECT *
            FROM participation p2
            WHERE p2.student_id IS NOT NULL
              AND (p2.test_run IS NULL OR p2.test_run = 0)
              AND (
                    p2.discriminator = 'SP'
                    OR p2.discriminator = 'PESP'
                )
              AND p2.student_id = p.student_id
              AND p2.exercise_id = p.exercise_id
              AND p2.id > p.id
        )
      AND NOT EXISTS(
            SELECT *
            FROM result r2
            WHERE r2.id <> r.id
              AND r2.score IS NOT NULL
              AND r2.completion_date IS NOT NULL
              AND r2.participation_id = p.id
              AND (r2.completion_date > r.completion_date OR (r2.completion_date = r.completion_date AND r2.id > r.id))
        )
)
SELECT lr.student_id,
       lr.exercise_id,
       lr.last_result_id,
       lr.last_result_score,
       'SS',
       IF(lr.last_result_score IS NOT NULL, ROUND(lr.last_result_score * 0.01 * lr.exercise_max_points, 1), NULL)
FROM last_result lr;


-- UPDATE LAST RATED SCORE INDIVIDUAL
WITH last_rated_result AS (
    SELECT DISTINCT e.id         as "exercise_id",
                    e.max_points as "exercise_max_points",
                    p.student_id as "student_id",
                    r.id         as "last_rated_result_id",
                    r.score      as "last_rated_result_score"
    FROM exercise e
             JOIN participation p on e.id = p.exercise_id
             JOIN result r on r.participation_id = p.id
    WHERE e.mode = 'INDIVIDUAL'
      AND p.student_id IS NOT NULL
      AND (p.test_run IS NULL OR p.test_run = 0)
      AND (
            p.discriminator = 'SP'
            OR p.discriminator = 'PESP'
        )
      AND r.score IS NOT NULL
      AND r.rated = TRUE
      AND r.completion_date IS NOT NULL
      AND NOT EXISTS(
            SELECT *
            FROM participation p2
            WHERE p2.student_id IS NOT NULL
              AND (p2.test_run IS NULL OR p2.test_run = 0)
              AND (
                    p2.discriminator = 'SP'
                    OR p2.discriminator = 'PESP'
                )
              AND p2.student_id = p.student_id
              AND p2.exercise_id = p.exercise_id
              AND p2.id > p.id
        )
      AND NOT EXISTS(
            SELECT *
            FROM result r2
            WHERE r2.id <> r.id
              AND r2.score IS NOT NULL
              AND r2.rated = true
              AND r2.completion_date IS NOT NULL
              AND r2.participation_id = p.id
              AND (r2.completion_date > r.completion_date OR (r2.completion_date = r.completion_date AND r2.id > r.id))
        )
)
UPDATE participant_score ps,
        (SELECT * from last_rated_result) lr
SET ps.last_rated_result_id = lr.last_rated_result_id,
    ps.last_rated_score     = lr.last_rated_result_score,
    ps.last_rated_points    = IF(lr.last_rated_result_score IS NOT NULL,
                                 ROUND(lr.last_rated_result_score * 0.01 * lr.exercise_max_points, 1), NULL)
WHERE ps.exercise_id = lr.exercise_id
  AND ps.user_id = lr.student_id;


-- FILL IN LAST SCORE TEAM
INSERT INTO participant_score(team_id, exercise_id, last_result_id, last_score, discriminator, last_points)
WITH last_result AS (
    SELECT DISTINCT e.id         as "exercise_id",
                    e.max_points as "exercise_max_points",
                    p.team_id    as "team_id",
                    r.id         as "last_result_id",
                    r.score      as "last_result_score"
    FROM exercise e
             JOIN participation p on e.id = p.exercise_id
             JOIN result r on r.participation_id = p.id
    WHERE e.mode = 'TEAM'
      AND p.team_id IS NOT NULL
      AND (p.test_run IS NULL OR p.test_run = 0)
      AND (
            p.discriminator = 'SP'
            OR p.discriminator = 'PESP'
        )
      AND r.score IS NOT NULL
      AND r.completion_date IS NOT NULL
      AND NOT EXISTS(
            SELECT *
            FROM participation p2
            WHERE p2.team_id IS NOT NULL
              AND (p2.test_run IS NULL OR p2.test_run = 0)
              AND (
                    p2.discriminator = 'SP'
                    OR p2.discriminator = 'PESP'
                )
              AND p2.team_id = p.team_id
              AND p2.exercise_id = p.exercise_id
              AND p2.id > p.id
        )
      AND NOT EXISTS(
            SELECT *
            FROM result r2
            WHERE r2.id <> r.id
              AND r2.score IS NOT NULL
              AND r2.completion_date IS NOT NULL
              AND r2.participation_id = p.id
              AND (r2.completion_date > r.completion_date OR (r2.completion_date = r.completion_date AND r2.id > r.id))
        )
)
SELECT lr.team_id,
       lr.exercise_id,
       lr.last_result_id,
       lr.last_result_score,
       'TS',
       IF(lr.last_result_score IS NOT NULL, ROUND(lr.last_result_score * 0.01 * lr.exercise_max_points, 1), NULL)
FROM last_result lr;

-- UPDATE LAST RATED SCORE TEAM

WITH last_rated_result AS (
    SELECT DISTINCT e.id         as "exercise_id",
                    e.max_points as "exercise_max_points",
                    p.team_id    as "team_id",
                    r.id         as "last_rated_result_id",
                    r.score      as "last_rated_result_score"
    FROM exercise e
             JOIN participation p on e.id = p.exercise_id
             JOIN result r on r.participation_id = p.id
    WHERE e.mode = 'TEAM'
      AND p.team_id IS NOT NULL
      AND (p.test_run IS NULL OR p.test_run = 0)
      AND (
            p.discriminator = 'SP'
            OR p.discriminator = 'PESP'
        )
      AND r.score IS NOT NULL
      AND r.rated = TRUE
      AND r.completion_date IS NOT NULL
      AND NOT EXISTS(
            SELECT *
            FROM participation p2
            WHERE p2.team_id IS NOT NULL
              AND (p2.test_run IS NULL OR p2.test_run = 0)
              AND (
                    p2.discriminator = 'SP'
                    OR p2.discriminator = 'PESP'
                )
              AND p2.team_id = p.team_id
              AND p2.exercise_id = p.exercise_id
              AND p2.id > p.id
        )
      AND NOT EXISTS(
            SELECT *
            FROM result r2
            WHERE r2.id <> r.id
              AND r2.score IS NOT NULL
              AND r2.rated = true
              AND r2.completion_date IS NOT NULL
              AND r2.participation_id = p.id
              AND (r2.completion_date > r.completion_date OR (r2.completion_date = r.completion_date AND r2.id > r.id))
        )
)
UPDATE participant_score ps,
        (SELECT * from last_rated_result) lr
SET ps.last_rated_result_id = lr.last_rated_result_id,
    ps.last_rated_score     = lr.last_rated_result_score,
    ps.last_rated_points    = IF(lr.last_rated_result_score IS NOT NULL,
                                 ROUND(lr.last_rated_result_score * 0.01 * lr.exercise_max_points, 1), NULL)
WHERE ps.exercise_id = lr.exercise_id
  AND ps.team_id = lr.team_id;
