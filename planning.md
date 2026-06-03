# Exercise Management — Course Exercise Groups & Variants

## Scope & Assumptions

- **Instructor-facing only.** Student-facing behavior is out of scope for this branch.
- **Client/UI only.** No server-side changes. All data is mocked via the existing
  client mock infrastructure:
  - `src/main/webapp/app/core/course/manage/exercises/mock/intro-to-programming-java-exercises.ts`
  - `src/main/webapp/app/core/interceptor/mock-course.interceptor.ts`
- **PrimeNG over Bootstrap.** New UI uses PrimeNG components. Drag-and-drop uses
  Angular CDK (`@angular/cdk/drag-drop`), consistent with the existing lecture-unit
  management implementation (see Consistency below).

## Domain Model (mocked)

### CourseExerciseGroup
A new course-level grouping entity, distinct from the existing exam-scoped
`ExerciseGroup` (`exam/domain/ExerciseGroup.java`). Named `CourseExerciseGroup` to
avoid the naming collision.

- Linked to a set of exercises (group ↔ exercises).
- Has an explicit display order within the course (for drag-and-drop reordering).
- **Optional timeline attributes** (`releaseDate`, `startDate`, `dueDate`,
  `assessmentDueDate`): each is optional. When set on the group, it applies to **all**
  exercises in the group, and the corresponding individual exercise date is **ignored**.
  Exercises may still store their own dates, but group-level dates win when present.
- **Max points cap**: a maximum number of points the group can contribute to the
  course score. The cap applies at **grade calculation** — if the summed exercise
  points exceed the cap, the contribution is capped at the cap value.
- Exercises within a group are treated as **implicit variants** of one another.
  Strict variant relationships are **not** enforced within a group.

> Note: the "practice only" visibility setting has been **dropped** from scope.

### ExerciseRelation (mocked)
A new, simple relationship entity for adaptive learning. No additional attributes
beyond the relation itself.

- Models directed relations such as `PREREQUISITE` / `HARDER_THAN` (kept deliberately
  simple, mirroring the Atlas `CompetencyRelationType` precedent: a small enum, no
  payload).
- **Endpoints can be either an individual exercise or a whole CourseExerciseGroup.**
  A group-level relation applies to all variants in the group — e.g. when variants
  differ only thematically without changing the underlying topic, they share the same
  prerequisites rather than each declaring them individually.

## Functional Requirements — Instructor

- [ ] Create a new exercise from an existing exercise as a template; both exercises
      share the same CourseExerciseGroup (implicit variant relationship).
- [ ] Add and organize exercises within a CourseExerciseGroup via drag and drop.
- [ ] Set the timeline and points on a CourseExerciseGroup. Group-level timeline/points
      override individual exercise settings (individual dates ignored when set on the group).
- [ ] Create new and rename existing CourseExerciseGroups.
- [ ] Set a maximum number of points a group can contribute to the course score
      (capped at grade calculation).
- [ ] Define ExerciseRelations between exercises and/or whole groups (e.g. "harder than",
      "prerequisite") with no additional attributes.
- [ ] Switch between management views:
  - **Grouped** — by type, by calendar week (derived from dates), and by CourseExerciseGroup.
  - **Graph** — showing exercise/group relations (prerequisite, harder than).

## Consistency

Reuse the lecture-unit drag-and-drop pattern for group/exercise reordering:
`lecture/manage/lecture-units/management/lecture-unit-management.component.ts`
uses `cdkDropList` + `cdkDrag` + `moveItemInArray`, with a `drop(event)` handler
that reorders a signal-backed array and persists the new order. Mirror this.

## Non-Functional Requirements

- Keep changes minimal and intuitive.
- Use PrimeNG components (not Bootstrap) for new UI.
- Angular 21 signal-based APIs, standalone components, `@if`/`@for` control flow.

## Build Order

1. Extend mock data: add `CourseExerciseGroup` and `ExerciseRelation` models +
   sample instances in the mock course, so the views have data to render.
2. Build the management views against the mock data.Let
