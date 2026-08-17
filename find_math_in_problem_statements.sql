-- ---------------------------------------------------------------------------------------------------------------------
-- Inventory of math notation in Artemis problem statements (MySQL 8, read-only)
--
-- Artemis renders math from two delimiter styles, both handled by MathFormulaExtractor:
--   $$...$$   display math, and inline when other text shares the line
--   $...$     inline math (a single dollar that is not part of a $$ pair)
--
-- Every step is self-contained: no temporary tables, no writes, only SELECTs. Run the whole file.
-- ---------------------------------------------------------------------------------------------------------------------
SET SESSION cte_max_recursion_depth = 100000;

-- =====================================================================================================================
-- 1. Scope: how much content contains math at all
-- =====================================================================================================================
SELECT 'exercises total'                 AS metric, COUNT(*) AS value FROM exercise
UNION ALL SELECT 'with a problem statement',       COUNT(*) FROM exercise WHERE problem_statement IS NOT NULL AND problem_statement <> ''
UNION ALL SELECT 'containing $$',                  COUNT(*) FROM exercise WHERE problem_statement LIKE '%$$%'
UNION ALL SELECT 'containing $ but never $$',      COUNT(*) FROM exercise WHERE problem_statement LIKE '%$%' AND problem_statement NOT LIKE '%$$%';

-- =====================================================================================================================
-- 2. Formula counts per delimiter style
-- =====================================================================================================================
WITH RECURSIVE n (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 300),
formula AS (
    SELECT e.id AS exercise_id, '$$' AS style, REGEXP_SUBSTR(e.problem_statement, '\\$\\$[^$]+\\$\\$', 1, n.i) AS raw
    FROM exercise e JOIN n ON e.problem_statement LIKE '%$$%'
    UNION ALL
    SELECT e.id, '$', REGEXP_SUBSTR(e.problem_statement, '(?<!\\$)\\$[^$\n]+\\$(?!\\$)', 1, n.i)
    FROM exercise e JOIN n ON e.problem_statement LIKE '%$%' AND e.problem_statement NOT LIKE '%$$%'
)
SELECT style, COUNT(*) AS formulas, COUNT(DISTINCT exercise_id) AS exercises
FROM formula WHERE raw IS NOT NULL GROUP BY style;

-- =====================================================================================================================
-- 3. THE COVERAGE ANSWER: which LaTeX commands appear in the formulas, and how often.
--    A converter has to understand exactly this list. Formulas with no command at all (x^2, a_1, [i,j]) are trivial.
-- =====================================================================================================================
WITH RECURSIVE n (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 300),
formula AS (
    SELECT REGEXP_SUBSTR(e.problem_statement, '\\$\\$[^$]+\\$\\$', 1, n.i) AS raw
    FROM exercise e JOIN n ON e.problem_statement LIKE '%$$%'
    UNION ALL
    SELECT REGEXP_SUBSTR(e.problem_statement, '(?<!\\$)\\$[^$\n]+\\$(?!\\$)', 1, n.i)
    FROM exercise e JOIN n ON e.problem_statement LIKE '%$%' AND e.problem_statement NOT LIKE '%$$%'
),
m (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM m WHERE i < 60),
command AS (
    SELECT REGEXP_SUBSTR(f.raw, '\\\\[a-zA-Z]+', 1, m.i) AS cmd
    FROM formula f JOIN m ON f.raw IS NOT NULL
)
SELECT cmd AS command, COUNT(*) AS occurrences,
       ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS pct_of_all_commands
FROM command WHERE cmd IS NOT NULL
GROUP BY cmd ORDER BY occurrences DESC;

-- =====================================================================================================================
-- 4. How many formulas need any command at all
-- =====================================================================================================================
WITH RECURSIVE n (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 300),
formula AS (
    SELECT REGEXP_SUBSTR(e.problem_statement, '\\$\\$[^$]+\\$\\$', 1, n.i) AS raw
    FROM exercise e JOIN n ON e.problem_statement LIKE '%$$%'
    UNION ALL
    SELECT REGEXP_SUBSTR(e.problem_statement, '(?<!\\$)\\$[^$\n]+\\$(?!\\$)', 1, n.i)
    FROM exercise e JOIN n ON e.problem_statement LIKE '%$%' AND e.problem_statement NOT LIKE '%$$%'
)
SELECT CASE WHEN raw REGEXP '\\\\[a-zA-Z]+' THEN 'uses a LaTeX command' ELSE 'plain notation only' END AS kind,
       COUNT(*) AS formulas,
       ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS pct
FROM formula WHERE raw IS NOT NULL GROUP BY kind;

-- =====================================================================================================================
-- 5. The tail: formulas using anything outside a candidate subset. Update the list once step 3 is known;
--    these are the formulas a subset converter would have to fall back on.
-- =====================================================================================================================
WITH RECURSIVE n (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 300),
formula AS (
    SELECT e.id AS exercise_id, REGEXP_SUBSTR(e.problem_statement, '\\$\\$[^$]+\\$\\$', 1, n.i) AS raw
    FROM exercise e JOIN n ON e.problem_statement LIKE '%$$%'
    UNION ALL
    SELECT e.id, REGEXP_SUBSTR(e.problem_statement, '(?<!\\$)\\$[^$\n]+\\$(?!\\$)', 1, n.i)
    FROM exercise e JOIN n ON e.problem_statement LIKE '%$%' AND e.problem_statement NOT LIKE '%$$%'
)
SELECT exercise_id, LEFT(raw, 200) AS formula
FROM formula
WHERE raw IS NOT NULL
  AND raw REGEXP '\\\\[a-zA-Z]+'
  AND REGEXP_REPLACE(raw,
        '\\\\(frac|sqrt|cdot|times|div|pm|le|ge|leq|geq|lt|gt|neq|approx|sum|prod|int|infty|alpha|beta|gamma|delta|epsilon|theta|lambda|mu|pi|sigma|phi|omega|Delta|Omega|Sigma|left|right|text|mathrm|mathbb|log|ln|sin|cos|tan|max|min|quad|qquad)',
        '') REGEXP '\\\\[a-zA-Z]+'
ORDER BY exercise_id
LIMIT 200;
