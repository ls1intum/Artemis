-- Run read-only against a production snapshot before the PointCounter JSON cutover.
-- Archive the complete psql output as the migration preflight artifact.

-- Structural ownership errors must be repaired before the migration can run.
SELECT c.id AS source_counter_id, c.quiz_point_statistic_id, qs.discriminator AS owner_discriminator, e.id AS exercise_id
FROM quiz_statistic_counter c
LEFT JOIN quiz_statistic qs ON qs.id = c.quiz_point_statistic_id
LEFT JOIN exercise e ON e.quiz_point_statistic_id = qs.id
WHERE c.discriminator = 'PC'
  AND (c.quiz_point_statistic_id IS NULL OR qs.id IS NULL OR qs.discriminator <> 'QP' OR e.id IS NULL)
ORDER BY c.id;

-- Rows whose numeric values will be normalized. The target id is the valid target bucket's id when present, otherwise the smallest merged source id.
WITH totals AS (
    SELECT qs.id AS statistic_id, e.id AS exercise_id, e.max_points, COALESCE(SUM(q.points), 0.0) AS overall_points
    FROM quiz_statistic qs
    JOIN exercise e ON e.quiz_point_statistic_id = qs.id
    LEFT JOIN quiz_question q ON q.exercise_id = e.id
    WHERE qs.discriminator = 'QP'
    GROUP BY qs.id, e.id, e.max_points
), normalized AS (
    SELECT t.exercise_id,
           t.statistic_id,
           t.max_points,
           t.overall_points,
           c.id AS source_id,
           c.points AS source_points,
           c.rated_counter AS source_rated_counter,
           c.un_rated_counter AS source_unrated_counter,
           CASE
               WHEN c.points IS NULL
                    OR c.points::text IN ('NaN', 'Infinity', '-Infinity')
                    OR c.points < 0
                    OR c.points <> FLOOR(c.points)
                    OR c.points > FLOOR(t.overall_points + 0.5)
                   THEN 0.0
               ELSE c.points
           END AS normalized_points,
           GREATEST(COALESCE(c.rated_counter, 0), 0)::bigint AS normalized_rated_counter,
           GREATEST(COALESCE(c.un_rated_counter, 0), 0)::bigint AS normalized_unrated_counter
    FROM quiz_statistic_counter c
    JOIN totals t ON t.statistic_id = c.quiz_point_statistic_id
    WHERE c.discriminator = 'PC'
), with_target_id AS (
    SELECT n.*,
           COALESCE(
               MIN(source_id) FILTER (WHERE source_points = normalized_points) OVER (PARTITION BY statistic_id, normalized_points),
               MIN(source_id) OVER (PARTITION BY statistic_id, normalized_points)
           ) AS target_id
    FROM normalized n
)
SELECT exercise_id,
       statistic_id,
       source_id,
       target_id,
       source_points,
       normalized_points,
       source_rated_counter,
       normalized_rated_counter,
       source_unrated_counter,
       normalized_unrated_counter,
       max_points,
       overall_points
FROM with_target_id
WHERE source_points IS DISTINCT FROM normalized_points
   OR source_rated_counter IS DISTINCT FROM normalized_rated_counter
   OR source_unrated_counter IS DISTINCT FROM normalized_unrated_counter
   OR source_id <> target_id
   OR max_points IS DISTINCT FROM overall_points
ORDER BY statistic_id, normalized_points, source_id;

-- Owner-level participant parity after normalization and merging. These relational summary columns are reported but never changed by the migration.
WITH normalized AS (
    SELECT qs.id AS statistic_id,
           qs.participants_rated,
           qs.participants_unrated,
           GREATEST(COALESCE(c.rated_counter, 0), 0)::bigint AS rated_counter,
           GREATEST(COALESCE(c.un_rated_counter, 0), 0)::bigint AS unrated_counter
    FROM quiz_statistic qs
    LEFT JOIN quiz_statistic_counter c ON c.quiz_point_statistic_id = qs.id AND c.discriminator = 'PC'
    WHERE qs.discriminator = 'QP'
)
SELECT statistic_id,
       participants_rated,
       participants_unrated,
       COALESCE(SUM(rated_counter), 0) AS bucket_rated_total,
       COALESCE(SUM(unrated_counter), 0) AS bucket_unrated_total
FROM normalized
GROUP BY statistic_id, participants_rated, participants_unrated
HAVING participants_rated IS DISTINCT FROM COALESCE(SUM(rated_counter), 0)
    OR participants_unrated IS DISTINCT FROM COALESCE(SUM(unrated_counter), 0)
ORDER BY statistic_id;
