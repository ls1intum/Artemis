### Summary

When opening the global search modal, the active course and the current content tab (exercises, lectures, exams, etc.) are now automatically detected from the URL and pre-populated as filter chips — reducing manual filter setup and surfacing the most relevant results immediately.

### Checklist
#### General
- [ ] I tested **all** changes and their related features with **all** corresponding user types on a test server.
- [ ] I chose a title conforming to the [naming conventions for pull requests](https://docs.artemis.tum.de/developer/development-process#pr-naming-conventions).

#### Client
- [ ] I **strictly** followed the [client coding guidelines](https://docs.artemis.tum.de/developer/guidelines/client-development).
- [ ] I **strictly** followed the [AET UI-UX guidelines](https://ls1intum.github.io/ui-ux-guidelines/).
- [ ] I added multiple integration tests (Vitest) related to the features (with a high test coverage), while following the [test guidelines](https://docs.artemis.tum.de/developer/guidelines/client-tests).
- [ ] I documented the TypeScript code using JSDoc style.
- [ ] I added multiple screenshots/screencasts of my UI changes.
- [ ] I translated all newly inserted strings into English and German.


### Motivation and Context

Opening the global search modal while browsing a specific course or tab (e.g., `/courses/42/exercises`) previously showed an unfiltered search with no context. Users had to manually add the course filter and the type filter before their query was scoped to what they were already looking at. This PR makes the modal context-aware: it reads the current URL on open and pre-populates the appropriate filter chips automatically, so the first search is already scoped to the relevant course and content type.


### Description

- Added `applyContextFilters()` to `GlobalSearchModalComponent`, called via an `effect()` whenever the overlay opens. It parses the current URL with a regex matching `/courses/:courseId(/:tab)?`.
- `CourseStorageService` is injected to resolve the human-readable course title for the filter chip label; falls back to `"Course <id>"` if the course is not yet cached.
- A static `ROUTE_TO_FILTER_TAG` map translates URL tab segments (`exercises`, `lectures`, `exams`, `communication`, `faq`) to search filter tags (`exercise`, `lecture`, `exam`, `channel`, `faq`). Tabs with no mapping (e.g., `dashboard`) set only the course filter.
- Two new signals, `activeCourseId` and `activeCourseLabel`, track the active course context independently from the type filter chips.
- The placeholder-result cache key is updated from `typeFilter` to `typeFilter_courseId` so results cached for one course are not reused for another.
- The `globalSearch` API call is extended with an optional `courseId` parameter to scope results server-side.
- Backspace in an empty search field removes type filter chips first; a second Backspace then removes the course filter chip.
- `SearchInputComponent` receives a new `courseFilterLabel` input and `courseFilterRemoved` output. A dedicated PrimeNG chip for the course filter is rendered before the type filter chips, and `hasActiveFilters` is updated to also return `true` when a course label is set.
- All context state (course ID, course label, type filters, cache) is cleared when the modal closes.


### Steps for Testing

Prerequisites:
- 1 Instructor or Student enrolled in at least one course that has exercises, lectures, and a communication channel

1. Log in to Artemis.
2. Navigate to a course dashboard (`/courses/<id>/dashboard`).
3. Open the global search modal.
4. Verify that a course filter chip (showing the course name) is pre-populated; no type filter chip is present.
5. Close the modal and navigate to the Exercises tab (`/courses/<id>/exercises`).
6. Open the global search modal.
7. Verify that both the course chip and an "exercise" type chip are pre-populated.
8. Type a query and confirm results are scoped to that course's exercises.
9. Press Backspace with an empty search field — the type chip should be removed first; press Backspace again — the course chip should be removed.
10. Click the × on the course chip — the course filter should be cleared and the search re-triggers without course scoping.
11. Repeat steps 5–10 for `/courses/<id>/lectures`, `/courses/<id>/communication`, `/courses/<id>/exams`, and `/courses/<id>/faq`, verifying the correct type chip is pre-set each time.
12. Navigate to the global course list (`/courses`) and open the modal — no filter chips should appear.
13. Close the modal and reopen it on a different tab — verify that no stale filters from the previous session appear.


### Testserver States
You can manage test servers using [Helios](https://helios.aet.cit.tum.de/). Check environment statuses in the [environment list](https://helios.aet.cit.tum.de/repo/69562331/environment/list). To deploy to a test server, go to the [CI/CD](https://helios.aet.cit.tum.de/repo/69562331/ci-cd) page, find your PR or branch, and trigger the deployment.

### Review Progress

#### Performance Review
- [ ] I (as a reviewer) confirm that the client changes (in particular related to REST calls and UI responsiveness) are implemented with a very good performance even for very large courses with more than 2000 students.

#### Code Review
- [ ] Code Review 1
- [ ] Code Review 2

#### Manual Tests
- [ ] Test 1
- [ ] Test 2

### Test Coverage

| Class/File | Line Coverage | Confirmation (assert/expect) |
|------------|--------------:|-----------------------------:|
| global-search-modal.component.ts | 95% | ✅ |
| search-input.component.ts | 100% | ✅ |

### Screenshots
