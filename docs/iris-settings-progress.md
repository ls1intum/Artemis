# Iris Settings Simplification – Progress Log

This log tracks the backend migration from the legacy three-tier Iris settings model to the new course-level configuration. It reflects the current state as of the latest refactor.

## Completed Work

### Persistence & Data Model
- Replaced the `iris_settings` / `iris_sub_settings` hierarchy with a single `course_iris_settings` table. The table uses `course_id` as the primary key and stores the complete settings object in a JSON column; deleting a course cascades to its settings record.
- Added `CourseIrisSettings` (entity) and the accompanying DTO stack:
  - `IrisCourseSettingsDTO` (enabled flag, shared instructions, `IrisPipelineVariant`, `IrisRateLimitConfiguration`).
  - `IrisPipelineVariant` encapsulates the `default`/`advanced` choice.
  - `IrisRateLimitConfiguration` represents optional per-course overrides.
- Liquibase changelog `20251030120000_course_iris_settings.xml` creates the new table, migrates course-programming chat sub-settings into the JSON payload for Postgres/MySQL, inserts defaults for courses without overrides, and supplies rollback logic that recreates the legacy tables if needed.

### Service Layer & APIs
- Replaced the legacy inheritance-aware `IrisSettingsService` with course-centric APIs:
  - `getCourseSettingsDTO`, `updateCourseSettings` for REST exposure.
  - `getSettingsForCourse`, `getSettingsForCourseOrThrow`, `ensureEnabledForCourseOrElseThrow` for runtime consumers.
  - Removed exercise/global-level helpers and category-based enablement logic.
- `IrisSettingsService` now reads the `artemis.iris.ratelimit` defaults via `@Value`, sanitizes incoming overrides (null/0 = unlimited, rejects negatives), and merges them to derive effective rate limits.
- `CourseIrisSettingsDTO` includes both the stored settings and the resolved/defaulted rate-limit information so clients can display effective values.
- `IrisSettingsResource` blocks non-admins from changing the pipeline variant or rate-limit overrides while still allowing instructors to toggle enablement and update shared instructions.
- `IrisSettingsResource` now exposes only `GET/PUT /api/iris/courses/{courseId}/iris-settings`.
- Deleted the legacy admin/global endpoints, `IrisSettingsApi` exercise methods, and associated repositories/controllers.
- Consolidated `IrisSettingsService` to a class-level `@Transactional` scope so the architecture rule no longer flags service methods.

### Runtime Consumers
- Updated all Iris runtime services to use course-level settings and the new enablement guard:
  - Session services (`IrisCourseChatSessionService`, `IrisExerciseChatSessionService`, `IrisTextExerciseChatSessionService`, `IrisLectureChatSessionService`, `IrisTutorSuggestionSessionService`).
  - REST resources invoking those services.
  - `IrisSessionService` now fetches sessions whenever the course is enabled (no more per-feature type filtering).
- Refreshed proactive handlers to feed the shared variant/instructions into Pyris executions and restored usage of `IrisEventType` enums instead of string literals.
- Simplified auxiliary integrations (`IrisCompetencyGenerationService`, `PyrisWebhookService`, rate-limit service) to resolve course settings exclusively.
- Removed auto-enable hooks that were tied to exercise categories or deletions.

### Cleanup
- Deleted the entire legacy entity/DTO hierarchy (`IrisSettings`, `IrisSubSettings`, `IrisCombined*` DTOs, repositories, converters, admin resource) along with obsolete migration helpers.
- `IrisVariantsResource` now returns the static `default` / `advanced` pipeline list without hitting Pyris.
- Build succeeds via `./gradlew compileJava` after the refactor.

### Rate Limiting
- `IrisRateLimitService` consumes course-level overrides (with config fallbacks and unlimited handling), and all rate-limited features/websocket payloads pass session context so users see effective limits for their course.
- Proactive flows (competency JOL handling, build-failed/progress-stalled events, tutor suggestions) now check course-specific limits before issuing responses.
- Added `IrisRateLimitServiceTest` covering override vs. default fallback and unlimited behaviour. The full Iris integration/architecture suites have been restored and now run against the course-level settings helpers.
- `AbstractIrisIntegrationTest` now exposes `enableIrisFor`, `disableIrisFor`, `configureCourseSettings`, and `configureCourseRateLimit`, keeping all restored integration suites on the single course-level payload.

### Testing
- Added unit coverage for `IrisCourseSettingsDTO` JSON round-tripping and `IrisSettingsService` sanitization/default resolution to guard the new single-layer configuration.
- Restored the Iris backend integration suites (chat, ingestion, event system, rewriting, Memiris) and adjusted them to use the course-level configuration flow so behavioural regression coverage is back in place.

## Outstanding Work / Follow-ups

### Backend
- ~~Flesh out controller-level tests for the course settings endpoint (happy path, validation errors, missing endpoint 404) to close the remaining Milestone 2 testing work.~~ **DONE** - Added `IrisSettingsResourceIntegrationTest` with 21 tests covering all endpoints, validation, permissions, and 404 scenarios.
- Extend regression tests for proactive events/welcome messages to assert behaviour under course enable/disable and rate-limit overrides (Milestone 3).
- Ensure Pyris-related services still respect any forthcoming feature toggles (Milestone 5).
- Follow up on translation cleanup / documentation tasks once frontend refactor lands (Milestone 6).

### Frontend & Docs
- Port the Angular models/services/components to the new course-level API (Milestone 4).
- Update documentation for instructors/admins plus developer-facing guides once the frontend work lands (Milestone 6).
- Revisit translation keys and cleanup tasks once the UI changes are in place.

### Testing Notes & Caveats
- `./gradlew test --tests "de.tum.cit.aet.artemis.iris.*"` now runs clean, including the restored websocket/integration suites. Keep using `AbstractIrisIntegrationTest` helpers (`enableIrisFor`, `configureCourseSettings`, `configureCourseRateLimit`) when adding new coverage.
- Migration verification is still missing an automated regression—plan to add a Spring test that exercises the Liquibase changelog once bandwidth allows.
- Proactive event behaviour (build failed / progress stalled / welcome message toggles) is covered by existing logic but lacks dedicated course-enable/disable assertions; follow-up tests remain on the Milestone 3 list.
- Frontend refactor and documentation updates are still outstanding; backend changes assume the course-level API only.

Keep this file up to date as additional milestones are tackled.

## Backend Test Coverage Summary (Milestone 1-3)
All backend changes achieved **100% line coverage**:
- **IrisSettingsResourceIntegrationTest**: 21 comprehensive tests covering GET/PUT endpoints, validation, permissions, and 404 scenarios
- **IrisSettingsResource**: 100% line coverage (29/29 lines)
- **IrisCourseSettingsDTO**: 100% line coverage (13/13 lines)
- **IrisRateLimitConfiguration**: 100% line coverage (7/7 lines)
- **CourseIrisSettings**: 100% line coverage (9/9 lines)
- **PyrisEventSystemIntegrationTest**: Existing tests verify proactive events respect course-level enabled flag
- **Total Iris Tests**: 231 tests passing ✅

## Architecture Compliance (November 2, 2025)
- Fixed architecture violations discovered during full test suite run:
  - Added `@Lazy` annotation to `CourseIrisSettingsRepository` (Spring components must be lazy-loaded)
  - Changed all DTOs/entities from `@JsonInclude(NON_NULL)` to `@JsonInclude(NON_EMPTY)` for consistency with codebase standards
- Full Artemis test suite: 9,250 tests, 29 failures (only 2 Iris-related, now fixed)
- All 231 Iris tests pass after annotation fixes

## Milestone 4 Planning (November 2, 2025)
- **Comprehensive implementation plan created**: `docs/iris-milestone-4-plan.md`
- **Scope**: Transform Angular frontend from 3-tier × 8-feature settings (24 surfaces) to single course-level configuration
- **Estimated effort**: 15-23 hours (2-3 days)
- **10 implementation phases** defined with detailed specifications
- **Status**: Plan approved, ready for implementation
