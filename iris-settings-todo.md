## Iris Settings Simplification Plan

This document is the authoritative TODO list for the Iris configuration redesign. It captures the agreed-on target state, implementation milestones, and detailed tasks. Treat it as a living artifact: update checkboxes as work progresses, add clarifications when questions arise, and extend the plan if new requirements surface.

### Overall Goal

Artemis currently configures Iris through a three-tier inheritance tree (global → course → exercise) with numerous per-feature sub-settings. This causes confusion for instructors, complicates maintenance, and makes runtime decisions hard to follow. The goal is to replace this system with a single, course-level settings object that contains:

- A boolean flag determining whether Iris is enabled for the course.
- One shared custom-instructions text field applied to every Iris chat context.
- A course-level variant choice (`default` or `advanced`), editable only by administrators.
- Optional course-specific rate-limit overrides (requests + timeframe), falling back to application-level defaults when not set.

While simplifying the configuration, we must:  
1. Preserve existing course overrides where meaningful.  
2. Remove obsolete layers/entities and associated UI.  
3. Ensure runtime services (chat sessions, proactive messages, ingestion jobs) honor the new setting structure.  
4. Introduce global feature toggles for coarse-grained operational control.  
5. Cover all changes with automated tests and update documentation accordingly.

---

## Milestone 1 – Data Model & Migration

**Objective:** Replace the inheritance-based persistence model with a single course-level JSON payload and migrate existing data. After this milestone, each course should have exactly one settings record containing the new structure; no global/exercise-specific records should remain.

### Tasks
- [x] **Understand the legacy schema**
  - [x] Review entities in `src/main/java/de/tum/cit/aet/artemis/iris/domain/settings/` including `IrisSettings` subclasses and every `Iris*SubSettings`.
  - [x] Inventory foreign keys and indices linking `iris_settings` to `iris_sub_settings`, `course`, and `exercise`.
  - [x] Note all places where legacy tables are queried (repositories, services, tests).
- [x] **Design the new persistence model**
  - [x] Define a new `CourseIrisSettings` entity with a JSON column containing:
      ```json
      {
        "enabled": boolean,
        "customInstructions": string | null,
        "variant": "default" | "advanced",
        "rateLimit": {
          "requests": number | null,
          "timeframeHours": number | null
        }
      }
      ```
  - [x] Choose a JSON persistence strategy (Hibernate JSON type or attribute converter) and document the rationale in code comments.
  - [x] Use `course_id` as the primary key so that each course owns exactly one settings row.
- [x] **Implement the Liquibase migration**
  - [x] Create a timestamped changelog that:
    - [x] Adds the JSON column (or new table) representing the new payload.
    - [x] Populates each course’s record using course-level programming chat sub-settings:
      - `enabled` = course programming chat `enabled`, default `true` if unset.
      - `customInstructions` = course programming chat custom instructions (null/omit if blank).
      - `variant` = course programming chat selected variant or `'default'` if missing.
      - `rateLimit.requests` = course programming chat rate limit (nullable).
      - `rateLimit.timeframeHours` = course programming chat timeframe (nullable).
    - [x] Insert default records (`enabled: true`, `variant: "default"`, others null) for courses lacking overrides.
    - [x] Drop or mark for removal the legacy foreign keys and columns not needed anymore.
  - [x] Provide rollback steps where possible (e.g., recreate dropped columns).
- [x] **Remove legacy entities and repositories**
  - [x] Delete obsolete classes/sub-settings, converters, and repositories no longer referenced.
- [x] **Testing**
  - [x] Add unit test confirming JSON serialization/deserialization works with the chosen mapping strategy.

---

## Milestone 2 – Server Configuration & Service Refactor ✅

**Objective:** Introduce configuration defaults for rate limiting, expose a simplified course-level API, and remove the remnants of global/exercise endpoints. After this milestone, the server should expose a coherent `CourseIrisSettingsDTO` and no longer refer to legacy structures.

**Status:** COMPLETED

### Tasks
- [x] **Configuration defaults**
  - [x] Add properties under `artemis.iris.ratelimit` in configuration files:
    - `defaultLimit` (Integer, default `0`, meaning unlimited requests).
    - `defaultTimeframeHours` (Integer, default `0`, meaning unlimited timeframe).
  - [x] Bind these properties via `@Value` injection in `IrisSettingsService` so they are available to consumers.
- [x] **Service layer refactor**
  - [x] Expose course-scoped APIs in `IrisSettingsService` (`getCourseSettingsDTO`, `updateCourseSettings`, `getSettingsForCourse`, `getSettingsForCourseOrThrow`,
        `ensureEnabledForCourseOrElseThrow`, `deleteSettingsFor`).
  - [x] Remove global/exercise inheritance code paths, category synchronisation, and sub-setting merging helpers.
  - [x] Enforce role restrictions for variant/rate-limit overrides (e.g. admin-only if required).
- [ ] **REST controllers & DTOs**
  - [x] Replace `IrisCombinedSettingsDTO` with the course-level settings DTO (`IrisCourseSettingsDTO`).
  - [x] Update `IrisSettingsResource` to expose only course-level endpoints:
    - `GET /api/iris/courses/{courseId}/settings`
    - `PUT /api/iris/courses/{courseId}/settings`
  - [x] Remove endpoints for global or exercise settings and update the public API surface accordingly.
  - [x] Ensure response payloads include resolved/defaulted rate-limit information where needed.
- [x] **Validation & security**
  - [x] Validate rate-limit inputs (`>= 0`), treating `null` or `0` as “use defaults/unlimited”.
  - [x] Keep custom-instruction length checks (max 2048 characters).
  - [x] Ensure instructors can toggle enable/disable and edit instructions; only admins can change variant or rate-limit override if required.
- [x] **Testing**
  - [x] Controller tests covering happy path, validation errors, and permission checks.
  - [x] Service tests verifying fallback to application defaults when JSON fields are absent.
  - [x] Negative tests ensuring attempts to access removed endpoints return 404.

---

## Milestone 3 – Runtime Feature Integration & Rate Limiting ✅

**Objective:** Update every runtime component to consume the new course-level settings. This includes chat services, ingestion jobs, proactive messaging, and any logic previously relying on per-feature flags or inheritance. Rate limiting must draw from the course override when present, otherwise from config defaults.

**Status:** COMPLETED

### Tasks
- [x] **Identify all consumers of Iris settings**
  - [x] `IrisCourseChatSessionService`
  - [x] `IrisExerciseChatSessionService`
  - [x] `IrisTextExerciseChatSessionService`
  - [x] `IrisLectureChatSessionService`
  - [x] `IrisCompetencyGenerationService`
  - [x] `IrisTutorSuggestionSessionService`
  - [x] `PyrisWebhookService`
  - [x] Proactive listeners (build failed, progress stalled, JOL, etc.)
  - [x] Auxiliary services (e.g., `ProgrammingExerciseAtlasIrisService`, `TextExerciseCreationUpdateResource`).
- [x] **Simplify enablement logic**
  - Legacy enablement checks removed; all consumers resolve the course settings and bail out when `enabled` is `false`.
- [ ] **Rate limiting**
  - [x] Refactor `IrisRateLimitService` (and any caller) to:
    - Use course-level `rateLimit.requests/timeframeHours` when present.
    - Fall back to config defaults when either value is `null`.
    - Interpret `0` (requests or timeframe) as “unlimited”.
  - [ ] Verify rate-limit resets and enforcement still function after changes.
- [ ] **Shared custom instructions & variant**
  - [x] Pass shared `customInstructions` to all pipelines (course chat, programming chat, text chat, lecture chat, etc.).
  - [x] Ensure variant value is provided to every `execute*Pipeline` call that currently expects a feature-specific variant.
- [ ] **Remove legacy helpers**
  - [x] Delete methods that automatically enabled exercises by categories or synchronized settings across layers.
  - [x] Remove unused repositories/services tied to exercise-level overrides.
- [x] **Testing**
  - [x] Update service-level unit/integration tests to assert the new enablement behavior.
  - [x] Add tests covering rate-limit override vs. default fallback.
  - [x] Verify proactive events and welcome messages still behave correctly when Iris is enabled/disabled.

---

## Milestone 4 – Frontend Simplification

**Objective:** Align the Angular client with the new server contract. The UI should offer a straightforward course-level settings form and display Iris availability consistently across the application. All legacy global/exercise views, feature-specific toggles, and category controls must be removed.

### Tasks
- [ ] **Client models & services**
  - [ ] Replace `IrisSettings` hierarchy in `app/iris/shared/entities/settings` with a single `CourseIrisSettings` model.
  - [ ] Update `iris-settings.service.ts` to use the new endpoints and remove caching logic for global/exercise requests.
  - [ ] Ensure service methods expose both the raw settings and resolved defaults so components can display effective rate limits.
- [ ] **Settings UI overhaul**
  - [ ] Redesign `IrisSettingsUpdateComponent` to include:
    - Toggle switch (enable/disable).
    - Textarea for custom instructions (single shared context).
    - Numeric inputs for rate-limit requests/timeframe with helper text (“0 or blank = unlimited / use default”).
    - Variant selector (radio buttons or dropdown) visible only to admins.
  - [ ] Remove `IrisCommonSubSettingsUpdateComponent` and related templates/scripts for per-feature management.
  - [ ] Update dirty-checking, warning dialogs, and form validation to match the new structure.
- [ ] **Quick toggles & indicators**
  - [ ] Simplify `IrisEnabledComponent` to reflect only a binary state; remove “custom” indicator logic.
  - [ ] Update course control center card to use the new payload and text.
  - [ ] Adjust `redirect-to-iris-button` and editor components to rely on `enabled` flag only.
  - [ ] Ensure variant-specific UI (if any) references the shared field.
- [ ] **Translations & help text**
  - [ ] Remove unused translation keys and add new ones for the simplified UI (rate limits, shared instructions).
  - [ ] Update tooltips and context help to describe the new behavior.
- [ ] **Testing**
  - [ ] Angular unit tests covering the service, form validation, and admin-only controls.
  - [ ] Component tests ensuring toggles render correctly for instructors vs. admins.
  - [ ] E2E tests (e.g., Cypress/Playwright) verifying instructors can update settings and students see the expected state.

---

## Milestone 5 – Feature Toggle Expansion

**Objective:** Introduce granular global feature toggles so administrators can disable specific Iris subsystems without altering course-level data. The toggles must be respected by backend services and reflected in frontend UI.

### Tasks
- [ ] **Define new toggles**
  - [ ] Add constants/enums for:
    - `FeatureToggle.Iris` (master switch for all Iris functionality).
    - `FeatureToggle.IrisLectureIngestion`.
    - `FeatureToggle.IrisProactivity` (controls proactive events).
    - `FeatureToggle.IrisWelcomeMessage` (daily student greeting).
  - [ ] Make toggles configurable via application properties and existing feature toggle infrastructure.
- [ ] **Backend wiring**
  - [ ] Guard Iris entry points (REST controllers, services) with the master toggle.
  - [ ] Wrap lecture-ingestion pipelines behind `IrisLectureIngestion`.
  - [ ] Ensure proactive event handlers check `IrisProactivity` before scheduling or sending messages.
  - [ ] Prevent welcome message dispatch when `IrisWelcomeMessage` is disabled.
- [ ] **Frontend wiring**
  - [ ] Update `ProfileService` or equivalent to expose new toggle states.
  - [ ] Hide or disable UI elements (buttons, cards) when toggles are off.
  - [ ] Ensure admin feature-toggle page surfaces these toggles with descriptions and default values.
- [ ] **Testing**
  - [ ] Backend tests confirming toggles disable functionality.
  - [ ] Frontend tests verifying UI reacts to toggled-off states (unit + E2E).
  - [ ] Documentation updates for administrators explaining how to use these toggles.

---

## Milestone 6 – Cleanup, Documentation & QA

**Objective:** Remove dead code, update all relevant documentation, and confirm through automated and manual testing that the new system works end-to-end.

### Tasks
- [ ] **Code cleanup**
  - [x] Delete orphaned classes, configuration beans, routes, and assets tied to the old settings system.
  - [ ] Remove unused translation keys from `messages*.properties`.
  - [ ] Check dependency injection graphs for unused beans related to legacy services.
- [ ] **Documentation**
  - [ ] Update developer docs (README, architecture notes) describing the new single-layer settings and rate-limit defaults.
  - [ ] Revise instructor/admin documentation to explain the new UI and the meaning of each field.
  - [ ] Document the new feature toggles and provide configuration examples.
- [ ] **QA & regression**
  - [ ] Run the full backend test suite (unit + integration) ensuring Iris-related tests pass.
  - [ ] Run frontend unit tests and E2E suites covering Iris workflows.
  - [ ] Perform manual smoke tests on a staging environment:
    - Enable/disable Iris in a course and verify chat availability.
    - Test proactive notifications and welcome messages.
    - Verify lecture ingestion still works when toggled on.
  - [ ] Address any regressions discovered during testing and update this checklist accordingly.
- [ ] **Sign-off**
  - [ ] Ensure CI pipelines pass after all changes.
  - [ ] Capture QA approval and note any follow-up tasks or known limitations.

---

## Appendix – Tracking & Notes

- [ ] Keep a list of removed classes/files for future reference (e.g., if someone has custom integrations relying on old APIs).
- [ ] Capture open questions or follow-up tasks discovered mid-implementation and add them as new checklist items.
- [ ] When a milestone is complete, add a short summary beneath it documenting key decisions and version numbers involved.
