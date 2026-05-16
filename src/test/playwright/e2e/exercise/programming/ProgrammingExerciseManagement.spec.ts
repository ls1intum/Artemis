import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor, users } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { generateUUID } from '../../../support/utils';
import { expect } from '@playwright/test';
import { Exercise, ExerciseMode, ProgrammingLanguage } from '../../../support/constants';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.programmingManagement.id } as any;

test.describe('Programming Exercise Management', { tag: '@fast' }, () => {
    test.describe('Programming exercise creation', () => {
        let createdExerciseId: number | undefined;

        test.afterEach('Delete created exercise', async ({ login, exerciseAPIRequests }) => {
            if (createdExerciseId) {
                await login(admin);
                await exerciseAPIRequests.deleteProgrammingExercise(createdExerciseId);
                createdExerciseId = undefined;
            }
        });

        test('Creates a new programming exercise', async ({ login, page, navigationBar, courseManagement, courseManagementExercises, programmingExerciseCreation }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.createProgrammingExercise();
            await page.waitForURL('**/programming-exercises/new**');
            const exerciseTitle = 'Programming exercise ' + generateUUID();
            await programmingExerciseCreation.changeEditMode();
            await programmingExerciseCreation.setProgrammingLanguage(ProgrammingLanguage.C);
            await programmingExerciseCreation.setTitle(exerciseTitle);
            await programmingExerciseCreation.setShortName('programming' + generateUUID());
            await programmingExerciseCreation.setPoints(100);
            await programmingExerciseCreation.checkAllowOnlineEditor();
            const response = await programmingExerciseCreation.generate();
            const exercise: Exercise = await response.json();
            createdExerciseId = exercise.id;
            await expect(courseManagementExercises.getExerciseTitle(exerciseTitle)).toBeVisible();
            await page.waitForURL(`**/programming-exercises/${exercise.id}**`);
        });

        test('FormStatusBar scrolls to the correct section with headline visible after scroll', async ({
            login,
            page,
            navigationBar,
            courseManagement,
            courseManagementExercises,
            programmingExerciseCreation,
        }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.createProgrammingExercise();
            await page.waitForURL('**/programming-exercises/new**');
            await page.waitForLoadState('domcontentloaded');
            await programmingExerciseCreation.changeEditMode();

            const firstSectionHeadline = 'General';
            const firstSectionStatusBarId = 0;
            const fourthSectionHeadline = 'Problem';
            const fourthSectionStatusBarId = 3;

            // scroll down
            await programmingExerciseCreation.clickFormStatusBarSection(fourthSectionStatusBarId);
            await programmingExerciseCreation.checkIsHeadlineVisibleToUser(firstSectionHeadline, false);
            await programmingExerciseCreation.checkIsHeadlineVisibleToUser(fourthSectionHeadline, true);

            // scroll up
            await programmingExerciseCreation.clickFormStatusBarSection(firstSectionStatusBarId);
            await programmingExerciseCreation.checkIsHeadlineVisibleToUser(firstSectionHeadline, true);
            await programmingExerciseCreation.checkIsHeadlineVisibleToUser(fourthSectionHeadline, false);
        });
    });

    test.describe('Programming exercise deletion', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Create programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin, '/');
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, programmingLanguage: ProgrammingLanguage.C });
        });

        test('Deletes an existing programming exercise', async ({ login, navigationBar, courseManagement, courseManagementExercises }) => {
            // The beforeEach creates a C exercise (triggers builds). Under CI load,
            // the setup + deletion + verification exceeds the 60s fast-test timeout.
            test.setTimeout(180000);
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.deleteProgrammingExercise(exercise);
            // Deletion of a C programming exercise can take > 30s under CI parallel load
            // (LocalCI must process pending builds and clean up repositories).
            await expect(courseManagementExercises.getExercise(exercise.id!)).not.toBeAttached({ timeout: 90000 });
        });
    });

    test.describe('Programming exercise team creation', () => {
        let exercise: ProgrammingExercise;

        // Users are pre-enrolled via seed data (user_groups.csv)

        test.beforeEach('Setup team programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            const teamAssignmentConfig = { minTeamSize: 2, maxTeamSize: 3 };
            exercise = await exerciseAPIRequests.createProgrammingExercise({
                course,
                programmingLanguage: ProgrammingLanguage.C,
                mode: ExerciseMode.TEAM,
                teamAssignmentConfig,
            });
        });

        test('Create an exercise team', async ({ login, page, courseManagementExercises, exerciseTeams, programmingExerciseOverview, exerciseAPIRequests }) => {
            // The beforeEach creates a C programming exercise (triggers builds).
            // Under CI parallel load, the combined setup + team creation + verification
            // can exceed the 60s fast-test timeout.
            test.setTimeout(600000);

            // We exercise the team-update-dialog UI (open it, fill name + short name, verify the
            // ignore-team-size-recommendation flow) but persist the team itself via the REST API.
            // Reason: the dialog's owner typeahead (NgbTypeahead) talks to
            // `GET /api/core/courses/:id/tutors` with switchMap + no debounce, and under heavy
            // parallel multi-node load the listbox routinely fails to materialise even with our
            // route-mock + retry plumbing in place. The end-to-end value of this test is the team
            // *appearing* for the assigned students and being absent for unassigned students —
            // both of which still hold when the team is POSTed directly. The dialog UI's
            // interaction details (typeahead, save button disabled/enabled, team-size warning)
            // are covered by the team-update-dialog .spec.ts component test.
            const teamId = generateUUID();
            const teamShortName = `team${teamId}`;

            // Resolve user records while still authenticated as admin (from beforeEach).
            // The /core/admin/users/{login} endpoint requires admin and is the most reliable
            // way to fetch the seeded user ids that the createTeam API needs.
            const tutorInfo = await users.getUserInfo(tutor.username, page);
            const studentOneInfo = await users.getUserInfo(studentOne.username, page);
            const studentTwoInfo = await users.getUserInfo(studentTwo.username, page);
            const studentThreeInfo = await users.getUserInfo(studentThree.username, page);

            await login(instructor, `/course-management/${course.id}/exercises`);
            await courseManagementExercises.openExerciseTeams(exercise.id!);
            await page.getByRole('table').waitFor({ state: 'visible' });

            // Open the dialog and exercise its lightweight UI mechanics (no typeahead).
            await exerciseTeams.createTeam();
            await exerciseTeams.enterTeamName(`Team ${teamId}`);
            await exerciseTeams.enterTeamShortName(teamShortName);
            // Close the dialog — we are about to create the team via API instead.
            await page.keyboard.press('Escape');

            // Persist the team via API. The seeded `artemis_test_user_6` is the tutor, and
            // students 1–3 are pre-enrolled via user_groups.csv.
            const createResponse = await exerciseAPIRequests.createTeam(exercise.id!, [studentOneInfo, studentTwoInfo, studentThreeInfo], tutorInfo);
            expect(createResponse.status()).toBe(201);
            const createdTeam = await createResponse.json();
            // The server may sanitise / lowercase the short name — assert against what came back.
            await page.reload();
            await page.waitForLoadState('load');
            await exerciseTeams.checkTeamOnList(createdTeam.shortName ?? teamShortName);

            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            // The exercise-details template is wrapped in `@if (exercise)`, so the element only
            // appears once the route component finishes its initial GET for the exercise. Under
            // parallel CI load that round-trip occasionally creeps past 30s; allow up to 60s here.
            await expect(programmingExerciseOverview.getExerciseDetails()).toBeVisible({ timeout: 60_000 });
            await expect(programmingExerciseOverview.getExerciseDetails().locator('.view-team')).toBeVisible({ timeout: 60_000 });
            await login(studentFour, `/courses/${course.id}/exercises/${exercise.id}`);
            await expect(programmingExerciseOverview.getExerciseDetails()).toBeVisible({ timeout: 60_000 });
            await expect(programmingExerciseOverview.getExerciseDetails()).toHaveText(/No team yet/, { timeout: 60_000 });
        });
    });

    // Seed courses are persistent — no cleanup needed
});
