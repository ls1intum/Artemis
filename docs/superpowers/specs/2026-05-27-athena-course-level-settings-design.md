# Athena Course-Level Settings Design

**Date:** 2026-05-27  
**Branch:** feature/assessment/athena-course-level-settings (new, based on develop)

## Problem

Athena module selection currently lives on each individual exercise. Instructors must:
1. Enable a feedback suggestion module per exercise via a dropdown
2. Separately enable preliminary feedback per exercise
3. Manually select the correct Athena module name from a list

This is repetitive, error-prone, and exposes implementation details (module names) to instructors who should not need to care about them.

## Goal

Move Athena configuration to the course level. Instructors toggle 6 options once during course creation/update. All exercises in the course automatically inherit these settings. No per-exercise Athena UI remains.

## Design

### 1. Data Model

#### Course entity — 6 new boolean columns (default `false`)

| Column | Meaning |
|---|---|
| `athena_text_grading_enabled` | Grading feedback suggestions for text exercises |
| `athena_text_preliminary_enabled` | Preliminary feedback requests for text exercises |
| `athena_modeling_grading_enabled` | Grading feedback suggestions for modeling exercises |
| `athena_modeling_preliminary_enabled` | Preliminary feedback requests for modeling exercises |
| `athena_programming_grading_enabled` | Grading feedback suggestions for programming exercises |
| `athena_programming_preliminary_enabled` | Preliminary feedback requests for programming exercises |

One Liquibase changeset adds these 6 columns. The existing `restricted_athena_modules_access` column is unchanged.

#### Exercise entity — no structural change

`feedbackSuggestionModule` (string) and `allowFeedbackRequests` (boolean) remain on `Exercise`. Existing values are preserved. Going forward the system writes them automatically on exercise creation.

#### Application properties — 3 new entries

```yaml
artemis:
  athena:
    default-text-module: module_text_llm
    default-programming-module: module_programming_llm
    default-modeling-module: module_modeling_llm
```

`AthenaModuleService` gets three `@Value`-injected fields and a `getDefaultModule(ExerciseType)` helper. If a course flag is enabled but the corresponding property is missing, an `IllegalStateException` is thrown at startup (fail-fast).

---

### 2. Backend Behavior

#### Exercise creation

When a new exercise is saved, the creation resource reads the parent course's Athena flags:

- If the grading flag is `true` for that exercise type → set `feedbackSuggestionModule` to the configured default module.
- If the preliminary flag is `true` for that exercise type → set `allowFeedbackRequests = true`.

This runs before the existing `checkHasAccessToAthenaModule` validation, which continues to act as a safety net.

Affected resources: `TextExerciseCreationUpdateResource`, `ModelingExerciseResource`, `ProgrammingExerciseCreationResource`.

#### Exercise update

Auto-population does **not** run on update. Existing `feedbackSuggestionModule` and `allowFeedbackRequests` values are preserved. The existing `checkValidAthenaModuleChange` check remains unchanged.

#### Course update — flag disabled (true → false)

When a grading flag transitions from `true` to `false`, `CourseUpdateResource` bulk-clears `feedbackSuggestionModule` (set to `null`) on all exercises of that type in the course via `ExerciseRepository` (following the pattern of `revokeAccessToRestrictedFeedbackSuggestionModulesByCourseId`).

When a preliminary flag transitions from `true` to `false`, `CourseUpdateResource` bulk-sets `allowFeedbackRequests = false` on all exercises of that type in the course.

Both bulk operations run within the course update transaction. Each flag is evaluated independently — if both grading and preliminary flags are disabled in the same save, both bulk operations run.

#### AthenaResource available-modules endpoints

The three `/courses/{courseId}/{type}-exercises/available-modules` endpoints are kept (may be used for admin tooling) but are no longer called by the frontend exercise forms.

---

### 3. Frontend UI Changes

#### Course create/edit form (`course-update.component`)

Inside the existing Athena section (where `restrictedAthenaModulesAccess` already lives), add 6 checkboxes shown only when `isAthenaEnabled` is `true`:

```
Text exercises
  ☐ Enable grading feedback suggestions
  ☐ Enable preliminary feedback requests

Modeling exercises
  ☐ Enable grading feedback suggestions
  ☐ Enable preliminary feedback requests

Programming exercises
  ☐ Enable grading feedback suggestions
  ☐ Enable preliminary feedback requests
```

Use Angular signal-based APIs (`input()`, `output()`, `signal()`) per project conventions. Use PrimeNG components where applicable; no new Bootstrap or ng-bootstrap usage.

#### Course model (TypeScript)

Add 6 optional boolean fields matching the new columns.

#### Exercise create/edit forms

- Remove `<jhi-exercise-feedback-suggestion-options>` from all three exercise type forms (text, modeling, programming update).
- Remove the `allowFeedbackRequests` checkbox from `programming-exercise-lifecycle.component`.

#### ExerciseFeedbackSuggestionOptionsComponent

Delete the component entirely (`exercise-feedback-suggestion-options.component.ts`, `.html`, `.spec.ts`) — it will have no remaining consumers.

#### AthenaService

`getAvailableModules()` is no longer called from exercise forms. The method stays because the service is still used for fetching feedback suggestions during assessment.

---

### 4. Migration Strategy

- **Existing exercises:** `feedbackSuggestionModule` and `allowFeedbackRequests` values are preserved as-is. No data migration needed for exercises.
- **New course flags:** All 6 columns default to `false`, so all existing courses start with Athena disabled at the course level. Instructors must explicitly enable the flags for courses where they want Athena going forward.
- **Liquibase:** Single changeset adding 6 `BOOLEAN NOT NULL DEFAULT FALSE` columns to the `course` table.

---

### 5. Out of Scope

- Changing how Athena routes requests internally (module URL construction in `AthenaModuleService.getAthenaModuleUrl()` is unchanged).
- Per-exercise override of course-level settings.
- Admin UI for configuring default module names (handled via application properties).
- Removing `restricted_athena_modules_access` (kept as-is).
