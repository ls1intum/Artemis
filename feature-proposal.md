---
name: 📝 Feature Proposal (Developer)
about: Software Engineering Process for a new feature
title: "[Feature Proposal] Proof Exercise"
labels: feature-proposal
assignees: ''

---

<!-- Feature Proposal Marker -->

# Feature Proposal
> Spec Version 0.2.0

## Context

### Problem
Artemis currently supports programming, quiz, modeling, text, and file-upload exercise types. There is no exercise type designed to let students submit a written proof or argument backed by a binary self-assessment checkbox (e.g., confirming that the proof is complete or follows a required structure). Instructors cannot define a simple boolean correctness criterion that is evaluated automatically against a student's explicit confirmation.

### Motivation
Courses in mathematics, logic, and theoretical computer science frequently require students to submit free-form proofs. Without a dedicated exercise type, instructors must either fall back to text exercises (which have no binary confirmation mechanism and no automatic scoring based on that confirmation) or resort to programming exercises (which are heavyweight and inappropriate for this use case). A lightweight Proof Exercise type enables automatic, zero-latency grading for simple proof-completion tasks while still supporting manual assessment for more nuanced evaluation. The affected roles are students submitting proofs and instructors/tutors designing and grading proof-based assessments.

## Requirements Engineering

### Existing (Problematic) Solution / System
Instructors currently use **Text Exercises** for free-form written submissions. Text exercises support only manual assessment — there is no mechanism to define an expected binary state that is matched against a student's self-reported confirmation. A score can only be assigned after a tutor manually reviews the submission.

### Proposed System
A new **Proof Exercise** type that:
- Allows instructors to compose a problem statement and an optional internal description with instructions.
- Lets instructors configure a `predefinedCheckboxState` (the expected checkbox value for 100 % score).
- Lets students submit a free-text proof together with a `studentCheckboxState` checkbox ("I confirm this proof is complete").
- Automatically scores a submission 100 % when the student's checkbox matches the instructor-configured expected value, and 0 % otherwise, as soon as the student submits.
- Supports import from existing proof exercises (course/exam copy workflows).
- Integrates into all existing exercise dashboards, course overviews, exam exercise groups, and plagiarism-control pipelines the same way as every other exercise type.

### Requirements
1. FR: Create Proof Exercise: Instructors with at least Editor role can create a Proof Exercise with a title, problem statement, example solution, difficulty, point configuration, internal description, and a predefined checkbox state.
2. FR: Update Proof Exercise: Instructors with at least Editor role can update all fields of an existing Proof Exercise.
3. FR: Delete Proof Exercise: Instructors with at least Editor role can delete a Proof Exercise.
4. FR: View Proof Exercise: Users with at least Tutor role can retrieve a single Proof Exercise or list all exercises for a course.
5. FR: Submit Proof: Students enrolled in a course can save a draft or submit a `ProofSubmission` containing free-text and a boolean checkbox state.
6. FR: Automatic Scoring: Upon final submission, the system automatically produces a `Result` with `AssessmentType.AUTOMATIC` — score 100 % if `studentCheckboxState == predefinedCheckboxState`, otherwise 0 %.
7. FR: Tutor Assessment View: Tutors can retrieve a submitted `ProofSubmission` for manual review/override via the assessment dashboard.
8. FR: Import Proof Exercise: Editors can import a Proof Exercise from an existing one (course/exam import workflows).
9. FR: Exercise List Integration: Proof Exercises appear in course exercise lists, exam exercise groups, and the exercise import picker alongside all other exercise types.
10. NFR: Performance: Proof submission and automatic result generation must complete within one HTTP round-trip (no asynchronous pipeline needed for automatic assessment).
11. NFR: Security: Submission endpoints are restricted to at least Student; exercise management endpoints are restricted to at least Editor; assessment endpoints are restricted to at least Tutor.
12. NFR: Reliability: The discriminator column for the `submission` table must be extended to include the `R` value without breaking existing rows.

## Analysis

### Analysis Object Model
- **ProofExercise** (extends `Exercise`): `description: String`, `predefinedCheckboxState: Boolean`, `exampleSolution: String`
- **ProofSubmission** (extends `Submission`): `text: String`, `studentCheckboxState: Boolean`
- **StudentParticipation** — links a student to a ProofExercise (reuses existing entity)
- **Result** — auto-generated upon submission; `assessmentType = AUTOMATIC`, `score = 0 or 100`

### Dynamic Behavior
**Student submission flow (Activity Diagram):**
1. Student opens Proof Exercise participation page.
2. Student writes proof text and optionally ticks the checkbox.
3. Student clicks **Save** → draft `ProofSubmission` persisted (not submitted).
4. Student clicks **Submit** → `ProofSubmission.submitted = true`; system evaluates `studentCheckboxState == predefinedCheckboxState`; `Result` with score (0 or 100 %) is persisted and returned in the response.
5. UI displays the result score.

## System Architecture

### Subsystem Decomposition
**New `proof` feature module** (mirrors existing modules such as `text`, `fileupload`):

| Layer | Artifact | Notes |
|---|---|---|
| Domain | `ProofExercise`, `ProofSubmission` | JPA entities, discriminator value `"R"` |
| Repository | `ProofExerciseRepository`, `ProofSubmissionRepository` | Spring Data JPA |
| Service | `ProofExerciseImportService` | Handles course/exam copy |
| REST | `ProofExerciseResource`, `ProofSubmissionResource` | See API table below |

**REST API additions:**

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/proof/proof-exercises` | Editor | Create exercise |
| `PUT` | `/api/proof/proof-exercises` | Editor | Update exercise |
| `GET` | `/api/proof/courses/{courseId}/proof-exercises` | Tutor | List by course |
| `GET` | `/api/proof/proof-exercises/{id}` | Tutor | Get by ID |
| `DELETE` | `/api/proof/proof-exercises/{id}` | Editor | Delete |
| `GET` | `/api/proof/proof-exercises` | Editor | Paginated search |
| `POST` | `/api/proof/proof-exercises/import/{sourceId}` | Editor | Import |
| `POST` | `/api/proof/exercises/{exerciseId}/proof-submissions` | Student | Create/update submission |
| `PUT` | `/api/proof/exercises/{exerciseId}/proof-submissions` | Student | Update submission |
| `GET` | `/api/proof/proof-submissions/{id}` | Student | Get submission |
| `GET` | `/api/proof/proof-submissions/{id}/for-assessment` | Tutor | Get for assessment |

**Frontend Angular routes** (new `proof` feature area):
- `/proof/proof-exercises/:exerciseId` — student participation + submission
- `/proof/manage/:courseId/proof-exercises/new` — create
- `/proof/manage/:courseId/proof-exercises/:exerciseId/edit` — edit
- `/proof/manage/:courseId/proof-exercises/:exerciseId` — detail view
- `/proof/manage/:courseId/proof-exercises/:exerciseId/submissions/:submissionId/assessment` — tutor assessment

### Persistent Data Management
**New table:** `proof_exercise_details`

| Column | Type | Notes |
|---|---|---|
| `id` | `bigint PK` | FK → `exercise.id` ON DELETE CASCADE |
| `description` | `longtext` | Instructor instructions |
| `predefined_checkbox_state` | `boolean` | Expected student checkbox value |

**Existing table modifications (`submission`):**
- Add column `text longtext` — stores the proof text for `ProofSubmission` rows.
- Add column `student_checkbox_state boolean default false` — stores the student's checkbox answer.
- Extend `discriminator` enum to include `'R'` (alongside `P`, `Q`, `M`, `T`, `F`).

**`exercise` table:** `example_solution` column already exists (shared with other exercise types); discriminator value `"R"` added to the `ExerciseType` enum.

Liquibase changelog: `20260428103000_changelog.xml`.

### Access Control / Security Aspects
- All proof submission endpoints (`POST`/`PUT`/`GET` on `proof-submissions`) require `@EnforceAtLeastStudent`.
- Assessment view (`for-assessment`) requires `@EnforceAtLeastTutor`.
- Exercise management (create/update/delete/import) requires `@EnforceAtLeastEditor`.
- Exercise read endpoints require `@EnforceAtLeastTutor`.
- No sensitive data is exposed beyond what is already available to each role in other exercise types.

### Other Design Decisions
- **Automatic scoring is synchronous** — the result is computed within the same HTTP request as the submission, avoiding the overhead of the Athena assessment pipeline. This is appropriate because scoring is a simple boolean comparison.
- **No dedicated participation creation endpoint** — participation is expected to exist before submission (consistent with other exercise types; the existing participation-start flow handles this).
- **Plagiarism control** — `ContinuousPlagiarismControlService` already iterates over exercise types; `PROOF` is excluded (free-text proofs are not subject to JPlag-style analysis at this time).
- **Testing strategy** — server integration tests follow the existing pattern (JUnit + Testcontainers/PostgreSQL). Client unit tests use Vitest.

## UI/UX Design
> Screenshots of the final UI mockups (mandatory): Please include screenshots to provide a clear and persistent visual reference of the design.
> Link to the design mockup (optional): Additionally, you may include a link to the live design mockup (e.g., Figma, Sketch) for a more interactive view.

**Student participation view (split-panel):**
- Left panel: problem statement (Markdown-rendered) + optional internal description.
- Right panel: `<textarea>` for the proof text + confirmation checkbox + Save / Submit buttons.
- Below panels: result score card (visible after submission).

**Instructor create/edit view:**
- Standard exercise fields: title, categories, problem statement (Monaco Markdown editor), example solution, difficulty, max/bonus points.
- "Proof Configuration" section: internal description textarea + "Expected Checkbox State for 100 % Score" checkbox.

**Exercise management pages:**
- Proof Exercise appears in course exercise list with a "proof" badge.
- Proof Exercise appears in exam exercise group picker.
- Proof Exercise is searchable in the exercise import dialog.
