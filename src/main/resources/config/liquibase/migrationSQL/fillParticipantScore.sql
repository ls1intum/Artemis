-- FILL IN LAST SCORE INDIVIDUAL
INSERT INTO participant_score(user_id, exercise_id, last_result_id, last_score, discriminator, last_points)
WITH last_submission AS (
    SELECT DISTINCT e.id         as "exercise_id",
                    e.max_points as "exercise_max_points",
                    p.student_id as "student_id",
                    max(s.id)    as "id_of_last_submission"
    FROM exercise e
             JOIN participation p ON p.exercise_id = e.id
             JOIN submission s ON p.id = s.participation_id
    WHERE e.mode = 'INDIVIDUAL'
      AND p.student_id IS NOT NULL
      AND (
            p.discriminator = 'SP'
            OR p.discriminator = 'PESP'
        )
      AND EXISTS(
            SELECT *
            FROM result r
            WHERE r.submission_id = s.id
              AND r.score IS NOT NULL
              AND r.completion_date IS NOT NULL
        )
    GROUP BY e.id, p.student_id
),
     last_result AS (
         SELECT DISTINCT ls.exercise_id,
                         ls.exercise_max_points,
                         ls.student_id,
                         r.id    AS "last_result_id",
                         r.score as "last_result_score"
         FROM last_submission ls
                  JOIN result r ON ls.id_of_last_submission = r.submission_id
         WHERE r.score IS NOT NULL
           AND r.completion_date IS NOT NULL
           AND NOT EXISTS(
                 SELECT *
                 FROM result r2
                 WHERE r2.submission_id = r.submission_id
                   AND r2.id > r.id
                   AND r2.score IS NOT NULL
                   AND r2.completion_date IS NOT NULL
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
WITH last_submission AS (
    SELECT DISTINCT e.id         as "exercise_id",
                    e.max_points as "exercise_max_points",
                    p.student_id as "student_id",
                    max(s.id)    as "id_of_last_submission"
    FROM exercise e
             JOIN participation p ON p.exercise_id = e.id
             JOIN submission s ON p.id = s.participation_id
    WHERE e.mode = 'INDIVIDUAL'
      AND p.student_id IS NOT NULL
      AND (
            p.discriminator = 'SP'
            OR p.discriminator = 'PESP'
        )
      AND EXISTS(
            SELECT *
            FROM result r
            WHERE r.submission_id = s.id
              AND r.score IS NOT NULL
              AND r.completion_date IS NOT NULL
              AND r.rated IS TRUE
        )
    GROUP BY e.id, p.student_id
),
     last_rated_result AS (
         SELECT DISTINCT ls.exercise_id,
                         ls.exercise_max_points,
                         ls.student_id,
                         r.id    AS "last_rated_result_id",
                         r.score as "last_rated_result_score"
         FROM last_submission ls
                  JOIN result r ON ls.id_of_last_submission = r.submission_id
         WHERE r.score IS NOT NULL
           AND r.completion_date IS NOT NULL
           AND r.rated is TRUE
           AND NOT EXISTS(
                 SELECT *
                 FROM result r2
                 WHERE r2.submission_id = r.submission_id
                   AND r2.id > r.id
                   AND r2.score IS NOT NULL
                   AND r2.completion_date IS NOT NULL
                   AND r2.rated IS TRUE
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
WITH last_submission AS (
    SELECT DISTINCT e.id         as "exercise_id",
                    e.max_points as "exercise_max_points",
                    p.team_id    as "team_id",
                    max(s.id)    as "id_of_last_submission"
    FROM exercise e
             JOIN participation p ON p.exercise_id = e.id
             JOIN submission s ON p.id = s.participation_id
    WHERE e.mode = 'TEAM'
      AND p.team_id IS NOT NULL
      AND (
            p.discriminator = 'SP'
            OR p.discriminator = 'PESP'
        )
      AND EXISTS(
            SELECT *
            FROM result r
            WHERE r.submission_id = s.id
              AND r.score IS NOT NULL
              AND r.completion_date IS NOT NULL
        )
    GROUP BY e.id, p.team_id
),
     last_result AS (
         SELECT DISTINCT ls.exercise_id,
                         ls.exercise_max_points,
                         ls.team_id,
                         r.id    AS "last_result_id",
                         r.score as "last_result_score"
         FROM last_submission ls
                  JOIN result r ON ls.id_of_last_submission = r.submission_id
         WHERE r.score IS NOT NULL
           AND r.completion_date IS NOT NULL
           AND NOT EXISTS(
                 SELECT *
                 FROM result r2
                 WHERE r2.submission_id = r.submission_id
                   AND r2.id > r.id
                   AND r2.score IS NOT NULL
                   AND r2.completion_date IS NOT NULL
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
WITH last_submission AS (
    SELECT DISTINCT e.id         as "exercise_id",
                    e.max_points as "exercise_max_points",
                    p.team_id    as "team_id",
                    max(s.id)    as "id_of_last_submission"
    FROM exercise e
             JOIN participation p ON p.exercise_id = e.id
             JOIN submission s ON p.id = s.participation_id
    WHERE e.mode = 'TEAM'
      AND p.team_id IS NOT NULL
      AND (
            p.discriminator = 'SP'
            OR p.discriminator = 'PESP'
        )
      AND EXISTS(
            SELECT *
            FROM result r
            WHERE r.submission_id = s.id
              AND r.score IS NOT NULL
              AND r.completion_date IS NOT NULL
              AND r.rated IS TRUE
        )
    GROUP BY e.id, p.team_id
),
     last_rated_result AS (
         SELECT DISTINCT ls.exercise_id,
                         ls.exercise_max_points,
                         ls.team_id,
                         r.id    AS "last_rated_result_id",
                         r.score as "last_rated_result_score"
         FROM last_submission ls
                  JOIN result r ON ls.id_of_last_submission = r.submission_id
         WHERE r.score IS NOT NULL
           AND r.completion_date IS NOT NULL
           AND r.rated is TRUE
           AND NOT EXISTS(
                 SELECT *
                 FROM result r2
                 WHERE r2.submission_id = r.submission_id
                   AND r2.id > r.id
                   AND r2.score IS NOT NULL
                   AND r2.completion_date IS NOT NULL
                   AND r2.rated IS TRUE
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
