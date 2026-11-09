import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import dayjs from 'dayjs';

import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { dismissPasskeyReminderIfPresent } from '../../../support/dismissPasskeyReminder';
import { generateUUID, readResponseJson } from '../../../support/utils';
import { expect } from '@playwright/test';
import { Exercise, ExerciseMode, ProgrammingLanguage } from '../../../support/constants';
import { SEED_COURSES } from '../../../support/seedData';
import { expectInside, expectNoOverlap, forEachViewport, measure } from '../../../support/layout';

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
            // The passkey setup reminder is a CDK overlay whose backdrop swallows the next click without failing it:
            // createProgrammingExercise clicks twice, so the first click dismisses the backdrop and the second only
            // opens the create menu, leaving the page on the exercise list with no navigation and no error. That is why
            // this reads as a timeout on waitForURL rather than as an interaction failure.
            await dismissPasskeyReminderIfPresent(page);
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.createProgrammingExercise();
            await page.waitForURL('**/programming-exercises/new**');
            await expect(page.getByRole('button', { name: /generate with ai/i })).toHaveCount(0);
            const exerciseTitle = 'Programming exercise ' + generateUUID();
            await programmingExerciseCreation.changeEditMode();
            await programmingExerciseCreation.setProgrammingLanguage(ProgrammingLanguage.C);
            await programmingExerciseCreation.setTitle(exerciseTitle);
            await programmingExerciseCreation.setShortName('programming' + generateUUID());
            await programmingExerciseCreation.setPoints(100);
            await programmingExerciseCreation.checkAllowOnlineEditor();
            // Avoid the immediate-release confirmation, which is skipped when the course is marked as a test course.
            const releaseDate = page.getByLabel('Release Date', { exact: true });
            await releaseDate.fill(dayjs().add(1, 'day').format('DD.MM.YYYY HH:mm'));
            await releaseDate.press('Tab');
            const response = await programmingExerciseCreation.generate();
            const exercise: Exercise = await readResponseJson(response);
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
            // Same overlay as in the test above, and the same silent symptom: without this, the double click in
            // createProgrammingExercise is spent dismissing the backdrop and opening the menu, so the page stays on the
            // exercise list and waitForURL below times out after a minute with nothing to say why.
            await dismissPasskeyReminderIfPresent(page);
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

    test('Keeps detail buttons compact and editing actions readable at every screen width', async ({ login, page, exerciseAPIRequests }) => {
        // The layout must also hold when a deployment exposes generation. This exercises the visible,
        // configuration-disabled C variant action without requiring a generation worker or changing server settings.
        await page.route('**/management/info', async (route) => {
            const response = await route.fetch();
            const info = await response.json();
            info.activeModuleFeatures = [...new Set([...(info.activeModuleFeatures ?? []), 'hyperion', 'hyperion-exercise-generation'])];
            await route.fulfill({ response, json: info });
        });
        await login(admin);
        const exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            programmingLanguage: ProgrammingLanguage.C,
            problemStatement: '[task][Implement sorting](testSorting)',
        });
        try {
            await page.goto(`/course-management/${course.id}/programming-exercises/${exercise.id}`);
            const editing = page.getByTestId('exercise-editing-actions');
            // The toolbar holds the navigation, editing and more actions, so it is looked up by its own test id
            const toolbar = page.getByTestId('exercise-actions-toolbar');
            const controls = [
                editing.getByRole('link', { name: 'Edit', exact: true }),
                editing.getByTestId('edit-in-editor-action'),
                editing.getByRole('link', { name: 'Grading', exact: true }),
                editing.getByTestId('create-variant-ai-button').getByRole('button'),
            ];
            await expect(controls[3]).toBeVisible();
            await expect(controls[1]).toHaveAttribute('href', /\/code-editor\/TEMPLATE\/\d+$/);
            const checkActions = async () => {
                for (const name of ['Participations', 'Statistics']) {
                    const navigation = toolbar.getByRole('link', { name, exact: true });
                    await expect.poll(async () => (await measure(navigation, { name })).width, { message: `${name} remains a compact button` }).toBeLessThanOrEqual(240);
                }
                for (const [index, control] of controls.entries()) {
                    const name = ['Edit', 'Edit in editor', 'Grading', 'Create Variant'][index];
                    await expectInside(control, toolbar, ['top', 'right', 'bottom', 'left'], { name: `${name} stays inside the action toolbar` });
                    await expect.poll(async () => (await measure(control, { name })).width, { message: `${name} retains a usable width` }).toBeGreaterThanOrEqual(32);
                    await expect.poll(async () => (await measure(control, { name })).height, { message: `${name} uses at most two text lines` }).toBeLessThanOrEqual(64);
                }
                await expectNoOverlap(controls, { name: 'Edit, Edit in editor, Grading and Create Variant' });
                const rows = await toolbar.evaluate((element) => {
                    const controls = Array.from(element.querySelectorAll('a, button')).filter((control) => control.getClientRects().length);
                    return new Set(controls.map((control) => Math.round(control.getBoundingClientRect().top))).size;
                });
                expect(rows, 'navigation and editing occupy at most two rows').toBeLessThanOrEqual(2);
            };
            const tasksLabel = page.getByTestId('programming-tasks-label');
            const checkDetails = async () => {
                await checkActions();
                await expect(tasksLabel).toBeVisible();
                await expect
                    .poll(
                        () =>
                            tasksLabel.evaluate((element) => {
                                const range = document.createRange();
                                range.selectNodeContents(element);
                                return range.getClientRects().length;
                            }),
                        { message: 'the Tasks label stays on one line inside the problem details' },
                    )
                    .toBe(1);
            };
            await forEachViewport(page, checkDetails);
            await test.step('390px phone with the management sidebar', async () => {
                await page.setViewportSize({ width: 390, height: 844 });
                await checkDetails();
            });
        } finally {
            await exerciseAPIRequests.deleteProgrammingExercise(exercise.id!);
        }
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

        // Users are pre-enrolled via seed data (user_course_role.csv)

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

        test('Create an exercise team', async ({ login, page, courseManagementExercises, exerciseTeams, programmingExerciseOverview }) => {
            // The beforeEach creates a C programming exercise (triggers builds).
            // Under CI parallel load, the combined setup + team creation + verification
            // can exceed the 60s fast-test timeout.
            test.setTimeout(600000);
            await login(instructor, `/course-management/${course.id}/exercises`);
            await courseManagementExercises.openExerciseTeams(exercise.id!);
            await page.getByRole('table').waitFor({ state: 'visible' });
            await exerciseTeams.createTeam();

            const teamId = generateUUID();
            const teamName = `Team ${teamId}`;
            const teamShortName = `team${teamId}`;
            await exerciseTeams.enterTeamName(teamName);
            await exerciseTeams.enterTeamShortName(teamShortName);
            await exerciseTeams.setTeamTutor(tutor.username);

            await exerciseTeams.addStudentToTeam(studentOne.username);
            await expect(exerciseTeams.getIgnoreTeamSizeRecommendationCheckbox()).toBeVisible();
            await expect(exerciseTeams.getSaveButton()).toBeDisabled();
            await exerciseTeams.getIgnoreTeamSizeRecommendationCheckbox().check();
            await expect(exerciseTeams.getSaveButton()).toBeEnabled();
            await exerciseTeams.getIgnoreTeamSizeRecommendationCheckbox().uncheck();

            await exerciseTeams.addStudentToTeam(studentTwo.username);
            await exerciseTeams.addStudentToTeam(studentThree.username);
            await expect(exerciseTeams.getSaveButton()).toBeEnabled();
            await exerciseTeams.getSaveButton().click();
            await exerciseTeams.checkTeamOnList(teamShortName);

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
