# Dropping the quiz statistics tables and computing statistics on demand

**Branch:** `feature/quiz-statistics-on-the-fly` (from `develop` @ `3dbb01eedf`)
**Question:** can `quiz_statistic` and `quiz_statistic_counter` be deleted and every statistics page be computed from
the submissions when an instructor opens it?
**Answer:** yes. Every page renders in single-digit to low-double-digit milliseconds at 2000 participations, on both
PostgreSQL and MySQL, using plain JPQL. The measured numbers and the parity proof are below.

---

## 1. What exists today

### Storage

| Table | Rows kept there today |
|---|---|
| `quiz_statistic` | One row per quiz (`QP`, the point statistic) and one per question (`MC` / `DD` / `SA`). Columns: `participants_rated`, `participants_unrated`, `rated_correct_counter`, `un_rated_correct_counter`, and a `counters` JSON column holding the per-answer-option / per-drop-location / per-spot counters. |
| `quiz_statistic_counter` | Only `PointCounter` rows survive (one per integer point bucket of a quiz), pointing at the quiz point statistic. The per-element counter rows were already folded into `quiz_statistic.counters` by changelog `20260707080319`. |

Two owning FKs reference them: `quiz_exercise.quiz_point_statistic_id` and `quiz_question.quiz_question_statistic_id`
(both `@OneToOne(cascade = ALL, orphanRemoval = true)`).

### Who writes them

| Call site | Trigger |
|---|---|
| `QuizResultService` | Quiz evaluation at the due date — `recalculateStatistics` on first evaluation, `updateStatistics` afterwards |
| `QuizSubmissionService:186` | Practice submission, pushed onto `quizStatisticsExecutor` off the request thread |
| `QuizSubmissionService:243` | Practice-mode recalculation |
| `ExamQuizService:129` | Exam quiz evaluation, one `updateStatistics` call per result |
| `QuizExerciseService:643/672` | Saving or re-evaluating a quiz |
| `QuizExerciseEvaluationResource` | `GET /quiz-exercises/{id}/recalculate-statistics`, the manual "the numbers drifted, fix them" endpoint |

The existence of that last endpoint is itself the strongest argument for this change: incrementally maintained counters
drift, so a repair button had to be built.

### Who reads them

Exactly two paths, both instructor-facing:

1. `GET /quiz-exercises/{quizExerciseId}` → `QuizExerciseWithStatisticsDTO`, which embeds the point statistic and every
   question statistic. This single endpoint serves **all five** statistics pages *and* the quiz detail/edit page.
2. The websocket topic `/topic/statistic/{quizId}`, which pushes the whole quiz (statistics included) after every
   evaluation. All five pages subscribe and re-fetch.

Nothing else touches them — not the data export, not exam scores, not the course dashboard, not Atlas. The removal
boundary is genuinely contained inside the `quiz` module.

### The five pages, and what each actually needs

| Route | Needs |
|---|---|
| `quiz-statistic` (overview) | Per question: rated/unrated participants and the fully-correct counters. **No per-element counters.** |
| `quiz-point-statistic` | The point-bucket histogram plus rated/unrated participants |
| `mc-question-statistic/:questionId` | One question: participants, correct counters, per-answer-option selection counts |
| `dnd-question-statistic/:questionId` | One question: participants, correct counters, per-drop-location correct counts |
| `sa-question-statistic/:questionId` | One question: participants, correct counters, per-spot correct counts |

Today every one of them downloads the statistics of the whole quiz. That is the waste this change removes.

---

## 2. What can and cannot be aggregated in SQL

Three facts decide the design.

**a) `submitted_answer.score_in_points` is persisted, and `isAnswerCorrect` is defined as "scored full points".**
`QuizQuestion#isAnswerCorrect` is literally `scoreForAnswer(answer) == getPoints()`, and
`QuizSubmission#calculateAndUpdateScores` writes exactly that score into `submitted_answer.score_in_points` at
evaluation time. So the fully-correct counters — the numbers the overview page draws — are a pure
`SUM(CASE WHEN answer.scoreInPoints >= question.points THEN 1 ELSE 0 END)`. No scoring strategy has to be re-run.

**b) The point statistic only needs `result.score`.** Bucketing is `Math.round(overallQuizPoints * score / 100)`,
which is a fold over at most a few dozen distinct scores. Pure aggregate.

**c) Per-spot short-answer correctness is persisted too.** `ShortAnswerTextSelection` carries a `correct` boolean
inside the selection JSON, written by the scoring pass. So no statistic anywhere needs `FuzzySearch.ratio`.

That flag cannot go stale: `QuizExerciseService#updateResultsOnQuizChanges` runs `calculateAndUpdateScores` on every
submission of a re-evaluated quiz, the scoring pass writes `isCorrect` through to the stored selection entry, and
`quizSubmissionRepository.saveAll` persists it.

**Nothing is recomputed, therefore.** Every number is either a scalar column, a `COUNT`, or a value already stored in
the selection JSON. Only one number is *derived* at all: whether a drag-and-drop drop location was filled correctly,
which has no persisted flag and is an integer id comparison against the question's correct mappings.

### Why it is still not one query

**The per-element counters cannot be aggregated in portable JPQL** — not because of correctness, but because of where
the values live:

- The student's selection is a JSON document in `submitted_answer.selection` (since the same changelog). Counting "how
  many students ticked answer option 7" means indexing into a JSON array — `JSON_CONTAINS` on MySQL, `@>` /
  `jsonb_array_elements` on PostgreSQL. Neither is expressible in JPQL, and they are not the same statement.
- For drag-and-drop, the question's own definition (correct mappings, drop locations, drag items) is *also* JSON, in
  `quiz_question.content`, so a SQL-side check would have to join JSON to JSON.

So the shape is a hybrid: **scalars are aggregated in the database, the JSON selections are streamed out for one
question only and folded in Java.** The projection selects nothing but the JSON column and the rated flag, so no
submitted answer, submission, participation or result entity is ever materialized.

On a PostgreSQL-only future this could collapse into a single query per page: Hibernate 7.4 ships
`PostgreSQLJsonTableFunction` (HQL `json_table`, enabled via `hibernate.query.hql.json_functions_enabled`) and
PostgreSQL 17+ has native `JSON_TABLE`. Since correctness is read rather than recomputed, even the short-answer page
would aggregate cleanly on the stored `correct` boolean. Estimated saving from the breakdown below: roughly 25–40 %
of an already single-digit-to-teens page. Not obviously worth an incubating, vendor-locked feature at these sizes.

### The "results that count"

Every query reproduces `QuizStatisticService#recalculateStatistics`: per participation, the latest rated result and
the latest unrated result. Expressed as a correlated `MAX(completionDate)` subquery, which is the only portable way to
write "top-1 per group" without window functions or derived tables.

---

## 3. The queries

Five, in `src/test/java/.../quiz/test_repository/SubmittedAnswerTestRepository.java`. All plain JPQL — no native SQL,
no views, no stored procedures, no vendor-specific function.

| Query | Serves | Returns |
|---|---|---|
| `findQuestionAggregatesForQuiz` | overview page | one row per (question, rated) |
| `findPointStatistic` | point statistic page | one row per (rated, score) |
| `findQuestionAggregate` | all three question pages | two rows (rated, unrated) |
| `findSelectionsForQuestion` | all three question pages | one JSON selection per participation |
| `findResultsForPointStatistic` | comparison baseline only | one row per result, folded in Java |

---

## 4. Measurements

Seeded: one quiz with an MC question (5 answer options), a DnD question (4 drop locations / 4 drag items) and an SA
question (4 spots, `matchLetterCase=false`, `similarityValue=85`). Each participation gets a submission, three
submitted answers and a rated result. Answers vary deterministically so the point histogram spreads over its buckets
and the short-answer texts are a realistic mix of exact hits, typos and wrong answers.

Median per page, whole page including loading the question, warm (5 warm-up + 15 measured rounds).
Machine: Apple Silicon, 18 cores. PostgreSQL 18 (Zonky embedded, tmpfs), MySQL 9.7.2 (Docker).

### PostgreSQL

| participations | overview | point statistic | MC page | DnD page | SA page |
|---:|---:|---:|---:|---:|---:|
| 100 | 0.7 ms | 2.4 ms | 5.6 ms | 5.5 ms | 4.6 ms |
| 500 | 2.0 ms | 2.2 ms | 5.5 ms | 6.4 ms | 6.3 ms |
| 1000 | 3.2 ms | 2.7 ms | 8.2 ms | 9.8 ms | 9.4 ms |
| **2000** | **6.2 ms** | **4.2 ms** | **14.1 ms** | **16.3 ms** | **17.2 ms** |
| 5000 | 16.0 ms | 8.3 ms | 32.1 ms | 40.3 ms | 38.5 ms |

### MySQL

Same queries, same test, `SPRING_PROFILES_INCLUDE=mysql`, MySQL 9.7.2 in Docker. Nothing had to change — which is the
portability claim, verified rather than argued.

| participations | overview | point statistic | MC page | DnD page | SA page |
|---:|---:|---:|---:|---:|---:|
| 100 | 1.4 ms | 3.8 ms | 6.3 ms | 7.7 ms | 6.7 ms |
| 500 | 5.9 ms | 5.9 ms | 9.3 ms | 9.3 ms | 8.7 ms |
| 1000 | 5.6 ms | 5.4 ms | 12.1 ms | 14.3 ms | 13.4 ms |
| **2000** | **19.0 ms** | **7.9 ms** | **19.2 ms** | **23.4 ms** | **24.9 ms** |
| 5000 | 53.4 ms | 15.0 ms | 45.7 ms | 54.0 ms | 50.8 ms |

MySQL is roughly 1.3–1.8× slower than PostgreSQL across the board, and its overview aggregate scales worse than
PostgreSQL's (53 ms vs 16 ms at 5000). Still comfortably inside budget at every realistic size.

### Where the time goes (PostgreSQL, 2000 participations)

| Component | Median |
|---|---:|
| point statistic aggregate query | 2.8 ms |
| per-question counts query | 4.4 ms |
| per-question selections query (2000 JSON documents) | 6.2 ms |
| whole MC page (incl. loading the question) | 14.1 ms |

Scaling is linear in participations with no cliff, and the dominant term on a question page is transferring and
deserializing one JSON selection per participation — not any computation. That is exactly the term a PostgreSQL-only
`json_table` rewrite would attack.

### Correctness, not just speed

At 100 participations the test runs the current `QuizStatisticService#recalculateStatistics` and asserts that **every**
on-the-fly number equals the stored one: point buckets, participants, correct counters, per-answer-option counters,
per-drop-location counters, per-spot counters, and the overview aggregates. The test passes on both databases. This is
what makes the timings meaningful — it is the right computation being measured, not merely a fast query.

A second assertion guards the decision to read persisted correctness rather than recompute it:
`computeShortAnswerStatisticByRederivingCorrectness` re-derives every spot with `FuzzySearch.ratio`, exactly as
`ShortAnswerQuestionStatistic` does today, and must produce identical counters to the flag-reading variant. It is
asserted and deliberately never measured — no design should run it.

### Caveats on the numbers

- Embedded PostgreSQL runs on tmpfs, so disk I/O is optimistic. The working set here (a few thousand narrow rows) is
  buffer-cache resident on a production server too, so the gap should be small — but these are not production numbers.
- The benchmark calls `ANALYZE` after each seeding step. Without it the 2000-row measurement flipped to ~98 ms while
  5000 stayed at ~40 ms — a stale-statistics plan flip, because a benchmark inserts in seconds what a real quiz
  accumulates over an exam. In production autovacuum keeps the statistics current. The lesson to carry forward is that
  the correlated-subquery formulation *is* plan-sensitive and deserves an `EXPLAIN` check on a production-sized table.
- The asynchronous `ParticipantScoreScheduleService` is shut down during the run; left on, its background tasks from
  seeding thousands of results dominated the wall clock.

---

## 5. What removing the tables would require

### Server

1. **New endpoints**, one per page, replacing the statistics embedded in `QuizExerciseWithStatisticsDTO`:
   - `GET /quiz-exercises/{id}/statistics/overview`
   - `GET /quiz-exercises/{id}/statistics/points`
   - `GET /quiz-exercises/{id}/statistics/questions/{questionId}`
   Each `@EnforceAtLeastTutorInExercise`, each running only the queries its page needs.
2. **Move the five queries** from the test repository into `SubmittedAnswerRepository` / a new
   `QuizStatisticsRepository`, and the projections into `quiz/dto` as records.
3. **Add `QuizStatisticsService`** holding the Java-side folds (the JSON selection folds and the point bucketing).
4. **Delete** `QuizStatisticService`, `QuizPointStatisticRepository`, `QuizQuestionStatisticRepository`, the six
   statistic entities (`QuizStatistic`, `QuizQuestionStatistic`, `QuizPointStatistic`, and the three concrete
   subclasses), `PointCounter`, `AnswerCounter`, `DropLocationCounter`, `ShortAnswerSpotCounter`, and
   `QuizQuestion#initializeStatistic` plus the `fixReference*` counter bookkeeping in `QuizService#save`.
5. **Remove the six write call sites** listed in section 1, and the `/recalculate-statistics` endpoint — with nothing
   stored, there is nothing to repair.
6. **Strip the statistics** out of `QuizExerciseWithStatisticsDTO` (which then becomes
   `QuizExerciseWithQuestionsDTO`), and out of `QuizExerciseImportService` / `QuizSubmissionResource`, which currently
   null them out by hand.
7. **Liquibase**: drop `quiz_exercise.quiz_point_statistic_id`, `quiz_question.quiz_question_statistic_id`, then
   `quiz_statistic_counter` and `quiz_statistic`.

### Client

8. Point the five components at their own endpoint instead of `quizExerciseService.find(exerciseId)`, and drop
   `quizQuestionStatistic` / `quizPointStatistic` from the models.

### The one genuine design decision: the live websocket push

Today `/topic/statistic/{id}` carries the whole quiz with its statistics, and every subscriber re-renders from the
payload. With on-demand computation the server cannot know which page a given subscriber is on, so the topic should
become a **notification only** ("statistics changed") and each open page re-fetches its own endpoint.

That inverts the cost: instead of one push, N subscribers each issue a query. During an exam-scale evaluation this
needs a debounce on the server side (coalesce pushes within, say, a second) — otherwise `ExamQuizService`, which
currently calls `updateStatistics` once per result, would fan out one refetch per result per viewer. This is the only
part of the change that is not a mechanical deletion, and it should be designed explicitly.

---

## 6. Recommendation

Proceed. The numbers leave a large margin: the slowest page at a realistic worst case (2000 participations) is 17 ms
on PostgreSQL and 25 ms on MySQL, against pages that instructors open a handful of times per course. Even the
deliberately unrealistic 5000-participation case stays under 55 ms.

What is gained is disproportionate to the cost: two tables and six entities disappear, six write call sites and an
asynchronous executor disappear, the statistics can no longer drift out of sync with the results (so the repair
endpoint becomes unnecessary), and re-evaluating a quiz no longer has to rewrite counters. The statistics become a
pure function of the submissions, which is what they always semantically were.

Three things to settle before implementing:

1. The websocket debounce described above.
2. An `EXPLAIN` of the correlated-subquery formulation against a production-sized `result` / `submitted_answer` table.
3. A one-off data check that the persisted values the design now relies on are actually populated:

   ```sql
   -- must be 0: a submitted answer that belongs to an evaluated submission but has no score
   SELECT COUNT(*) FROM submitted_answer sa
     JOIN result r ON r.submission_id = sa.submission_id
    WHERE sa.score_in_points IS NULL;
   ```

   The SQL version treats a `NULL` `score_in_points` as "not correct", where today's code would recompute it. Every
   submission that has a result has been through `calculateAndUpdateScores`, so this should be empty — but it is worth
   confirming rather than assuming.

---

## 7. Reproducing

```bash
# PostgreSQL (default)
./gradlew test --tests QuizStatisticsOnTheFlyBenchmarkTest -x webapp

# MySQL
SPRING_PROFILES_INCLUDE=mysql ./gradlew test --tests QuizStatisticsOnTheFlyBenchmarkTest -x webapp
```

The two summary tables are logged at the end of the run.

Files added:

- `src/test/java/de/tum/cit/aet/artemis/quiz/QuizStatisticsOnTheFlyBenchmarkTest.java` — seeding, parity assertions, measurement
- `src/test/java/de/tum/cit/aet/artemis/quiz/test_repository/SubmittedAnswerTestRepository.java` — the five candidate queries
- `src/test/java/de/tum/cit/aet/artemis/quiz/util/QuizStatisticProjections.java` — the projections
