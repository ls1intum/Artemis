### Summary
Harden Weaviate upsert error handling so that embedding model failures are caught and logged gracefully without disrupting the main application flow (e.g. exercise creation/update, exam imports).

### Checklist
#### General
- [ ] I tested **all** changes and their related features with **all** corresponding user types on a test server.
- [ ] Language: I followed the [guidelines for inclusive, diversity-sensitive, and appreciative language](https://docs.artemis.tum.de/developer/guidelines/language).
- [x] I chose a title conforming to the [naming conventions for pull requests](https://docs.artemis.tum.de/developer/development-process#pr-naming-conventions).

#### Server
- [x] **Important**: I implemented the changes with a [very good performance](https://docs.artemis.tum.de/developer/guidelines/performance) and prevented too many (unnecessary) and too complex database calls.
- [x] I **strictly** followed the principle of **data economy** for all database calls.
- [x] I **strictly** followed the [server coding and design guidelines](https://docs.artemis.tum.de/developer/guidelines/server-development) and the [REST API guidelines](https://docs.artemis.tum.de/developer/guidelines/rest-api).
- [ ] I added multiple integration tests (Spring) related to the features (with a high test coverage).
- [x] I documented the Java code using JavaDoc style.


### Motivation and Context
The Weaviate embedding model can be unstable and temporarily unavailable. When Weaviate upserts exercise data, it calls the embedding model to generate vectors. If the embedding model is down, the Weaviate API throws a `WeaviateApiException` (a RuntimeException). Previously, `upsertExerciseInWeaviate` only caught `IOException`, so embedding model failures bypassed the method-level error logging. While the outer async callers already caught `Exception`, the inner method's specific error context was lost. Additionally, the `onExerciseVersionCreated` event listener had no safety wrapper.

### Description

**`ExerciseWeaviateService.upsertExerciseInWeaviate()`:**
- Widened the catch block from `IOException` to `Exception` so that `WeaviateApiException` (thrown on embedding model failures) is also caught and logged with context at the right level before being wrapped in `WeaviateException`
- Removed the now-unused `java.io.IOException` import

**`ExerciseWeaviateService.onExerciseVersionCreated()`:**
- Added a defensive try-catch wrapper around the event listener body. Since this method calls `upsertExerciseAsync` as a self-invocation (bypassing Spring's `@Async` proxy), the inner method's catch already handles errors — but the extra safety net ensures no exception can escape the `@EventListener @Async` thread

**No changes needed for:**
- `upsertExerciseAsync()`, `deleteExerciseAsync()`, `updateExamExercisesAsync()` — these already catch `Exception`, log, and swallow
- All callers in `ExerciseDeletionService`, `ExamService`, `ExamImportService` — these use `Optional.ifPresent()` and delegate to the async methods above
- `WeaviateService.initializeCollections()` — startup should still fail fast if Weaviate itself is unavailable (only the embedding model is unstable)

### Steps for Testing
Prerequisites:
- 1 Instructor
- 1 Course with a Programming Exercise
- Weaviate configured with an embedding model (text2vec-openai or text2vec-transformers)

1. **Embedding model available:** Create or update an exercise. Verify the exercise is upserted into Weaviate successfully (check server logs for `Successfully upserted exercise`).
2. **Embedding model unavailable:** Stop/break the embedding model, then create or update an exercise. Verify:
   - The exercise creation/update itself succeeds normally
   - Server logs show an error like `Failed to upsert exercise ... in Weaviate (possible embedding model issue)` but no stack trace propagates to the user
3. **Exam exercise sync:** With the embedding model down, update exam dates. Verify the exam date update succeeds and server logs show per-exercise errors for the Weaviate sync without affecting the exam operation.
4. **Exercise deletion:** Delete an exercise while the embedding model is down. Verify the deletion completes successfully.

### Testserver States
You can manage test servers using [Helios](https://helios.aet.cit.tum.de/). Check environment statuses in the [environment list](https://helios.aet.cit.tum.de/repo/69562331/environment/list). To deploy to a test server, go to the [CI/CD](https://helios.aet.cit.tum.de/repo/69562331/ci-cd) page, find your PR or branch, and trigger the deployment.

### Review Progress
#### Code Review
- [ ] Code Review 1
- [ ] Code Review 2
#### Manual Tests
- [ ] Test 1
- [ ] Test 2

### Test Coverage

| Class/File | Line Coverage | Confirmation (assert/expect) |
|------------|--------------:|-----------------------------:|
| ExerciseWeaviateService.java | | ✅ |
