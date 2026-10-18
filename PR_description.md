<!-- Suggested PR title: `Assessment`: Unify individual and team assessment dashboards -->

### Summary

The exercise assessment dashboard now uses one assessment queue for individual and team exercises, exams, and test runs. It replaces the old submission table and shows each participation's assessment status, latest result, and available assessment actions.

Team exercises show **Your teams**, including team details. Individual exercises show participation IDs instead of student names. Assessment links require a valid submission ID and update when row inputs change. The existing Scores and Participations pages remain separate; this PR does not merge their pages, navigation, exports, or filters.

### Checklist

#### General

- [ ] I tested **all** changes and their related features with **all** corresponding user types on a test server.
- [ ] Language: I followed the [guidelines for inclusive, diversity-sensitive, and appreciative language](https://docs.artemis.tum.de/developer/guidelines/language).
- [ ] I chose a title conforming to the [naming conventions for pull requests](https://docs.artemis.tum.de/developer/development-process#pr-naming-conventions).

#### Server

- [ ] **Important**: I implemented the changes with [very good performance](https://docs.artemis.tum.de/developer/guidelines/performance) and prevented unnecessary and overly complex database calls.
- [ ] I **strictly** followed the principle of **data economy** for all database calls.
- [ ] I **strictly** followed the [server coding and design guidelines](https://docs.artemis.tum.de/developer/guidelines/server-development) and the [REST API guidelines](https://docs.artemis.tum.de/developer/guidelines/rest-api).
- [ ] I added integration tests with meaningful assertions for the changed behavior.
- [ ] I verified authorization, course membership checks, team ownership, and participant anonymity.
- [ ] I documented the Java code using JavaDoc style.

#### Client

- [ ] **Important**: I prevented unnecessary REST calls and verified responsiveness with large data, including server-side pagination, filtering, and sorting.
- [ ] I **strictly** followed the principle of **data economy** for all client-server REST calls.
- [ ] I **strictly** followed the [client coding guidelines](https://docs.artemis.tum.de/developer/guidelines/client-development).
- [ ] I **strictly** followed the [AET UI-UX guidelines](https://ls1intum.github.io/ui-ux-guidelines/).
- [ ] Following the [theming guidelines](https://docs.artemis.tum.de/developer/guidelines/client-theming), I checked that the changes look consistent in light and dark themes.
- [ ] I added and updated Vitest tests while following the [test guidelines](https://docs.artemis.tum.de/developer/guidelines/client-tests).
- [ ] I verified role-dependent navigation, assessment actions, and participant anonymity.
- [ ] I documented the TypeScript code using JSDoc style.
- [ ] I added screenshots/screencasts of the changed pages.
- [ ] I translated newly inserted strings into English and German.

#### Changes affecting Programming Exercises

- [ ] **High priority**: I tested the changes on a test server configured with the **integrated lifecycle setup** (LocalVC and LocalCI).
- [ ] I tested the changes on a test server configured with **LocalVC** and **Jenkins**.

### Motivation and Context

The old submission table and separate team navigation made assessment workflows inconsistent. A common queue provides the same place to start, continue, open, and cancel assessments across exercise types while preserving team ownership, participant anonymity, correction rounds, and test-run behavior.

### Description

#### Unified assessment queue

- Replace the legacy submission table and remove its redundant loading, sorting, and display logic.
- Use the same queue for programming, text, modeling, and file-upload assessments, including individual exercises, team exercises, exams, and test runs.
- Show the participant, assessment status, latest result, and applicable assessment actions in each row.
- Distinguish no submission, not assessed, in progress, complaint, and assessed states.
- Use **Your teams** and team-specific details for team exercises; use student wording for individual exercises.
- Restrict the team queue and **Assess next** selection to teams owned by the requesting tutor.
- Show participation IDs for individual exercises, omit identity search, and sort those rows by participation ID.
- Provide programming repository and commit-history links, with exam-aware routes, and team details where applicable. Hide menus with no available actions.
- Load test runs through the existing endpoint for the current user's test-run submissions and preserve the test-run assessment parameter.
- Keep assessment availability gates, enabled correction rounds, and separate assessment actions for each correction round.

#### Assessment actions and navigation

- Render assessment links only when a submission ID is available. A submission count alone previously allowed an action with no navigation target.
- Derive assessment availability and correction-round controls reactively when inputs change.
- Preserve complaint information when converting the latest result into the assessment-action model.
- Provide accessible labels and make unavailable assessment links unreachable by keyboard.
- Add a dedicated assessment-queue endpoint that reuses the existing score retrieval, excludes practice participations, and supports team-owner filtering.
- Allow tutors to load anonymous exam queue rows without broadening access to the existing management or scores endpoints.
- Share result conversion between the queue and the existing Scores page; include team-member details with explicit anonymization.
- Return assessment editors to the exercise assessment dashboard for both individual and team exercises.
- Offer the exercise dashboard for team exercises alongside the Teams shortcut, and enable **Assess next** for team assessments.
- Update English/German translations, documentation, client/server tests, browser-test selectors, and the generated feature-usage catalogue.

### Steps for Testing

#### Prerequisites

- An instructor and a tutor assigned to a course.
- Individual programming, text, modeling, and file-upload exercises with submissions and results.
- A team exercise with teams assigned to different tutors.
- Participations covering unassessed submissions, draft assessments, completed assessments, and complaints.

#### Individual assessment queues

1. Open each exercise type's assessment dashboard and complete the required instruction/training steps.
2. Confirm there is one assessment queue and no legacy submission table above it. The heading and participant column should refer to students.
3. As an instructor and a tutor, confirm the participant column shows participation IDs, student names/logins are absent, and identity search is unavailable. Check sorting and pagination.
4. Confirm the queue loads without an authorization error. Check that the separate Scores and Participations pages and their existing permissions still work.
5. Click **Assess submission**, save a draft, return to the dashboard, and use **Continue assessment**. Submit it and use **Open assessment**.
6. Cancel an unfinished assessment and confirm the row refreshes to the appropriate state.
7. Confirm rows without submissions offer no assessment link. Check that complaints retain the correct action.
8. For programming exercises, check repository and commit-history links. For other individual exercises, confirm no empty action menu appears.

#### Team assessment queues

1. Open a team exercise from the course assessment dashboard using **Exercise Dashboard**. Confirm the separate Teams shortcut remains available.
2. As the assigned tutor, confirm **Your teams** lists only teams you own, with their names, members, assessment states, and applicable actions.
3. Start, continue, submit, and reopen an assessment. Returning from the editor should open the exercise assessment dashboard.
4. After completing an assessment, use **Assess next** and confirm it selects only a submission belonging to one of your teams.
5. Verify team details and, for programming exercises, repository and commit-history links.

#### Exam Mode Testing

Prerequisites: an exam with submissions, two correction rounds, and an instructor test run.

1. Open an exam exercise's assessment dashboard as an instructor and a tutor. Confirm the queue loads and tutors see anonymous student rows.
2. While assessment is unavailable, confirm the explanation is visible and assessment links cannot be activated, including by keyboard.
3. Complete a first-round assessment. Confirm the second-round action is unavailable until the instructor enables second correction.
4. Enable second correction and assess with a different corrector. Check that each round's action opens its corresponding assessment and the result column shows the latest result.
5. Verify programming repository/commit-history links use the exam route.
6. Open the instructor's test-run assessment dashboard. Confirm it lists the current user's test-run submission, opens the correct editor, and does not offer second correction.
7. Confirm the overall exam Scores page still works.

Repeat the changed UI flows in English/German and light/dark themes.

### Automated Validation

Validation after separating the dashboard changes from the scores/participations branch:

- 143 focused client tests passed, covering the dashboard, queue, assessment controls, existing Scores page, and participation service.
- 27 server regression tests passed using the repository's H2 test mode, including team ownership, practice exclusion, anonymous exam access, and unchanged permissions on the existing scores/management endpoints.
- Server architecture suite passed: 1,264 checks succeeded and 11 were skipped. The endpoint catalogue was regenerated for the new queue endpoint.
- Production client build and strict client test type checks passed using Node 24.21.
- Client lint, Prettier, and server/client dead-code checks passed. Lint reports existing deprecation warnings.

PostgreSQL-backed regression tests could not initialize because the temporary database container's mapped ports were unreachable, including with an explicit IPv4 host. The H2 run validates application behavior but does not replace PostgreSQL validation in CI.

The full Playwright suite has not been run. Manual test-server, theme, lifecycle, and performance checks remain to be completed.

### Testserver States

Manage deployments using [Helios](https://helios.aet.cit.tum.de/). Check the [environment list](https://helios.aet.cit.tum.de/repo/69562331/environment/list) and deploy the branch from the [CI/CD page](https://helios.aet.cit.tum.de/repo/69562331/ci-cd).

### Review Progress

#### Performance Review

- [ ] Client performance verified for courses with more than 2000 students.
- [ ] Server performance verified for courses with more than 2000 students.

#### Code Review

- [ ] Code Review 1
- [ ] Code Review 2

#### Manual Tests

- [ ] Test 1
- [ ] Test 2

#### Exam Mode Test

- [ ] Test 1
- [ ] Test 2

#### Performance Tests

- [ ] Test 1
- [ ] Test 2

### Test Coverage

<!-- Add the coverage table from CI or the local PR coverage report. Do not infer coverage percentages from test counts. -->

### Screenshots

<!-- Add screenshots of individual and team assessment queues, assessment states/actions, and exam correction-round controls. Include light and dark themes. -->
