import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor } from '../../../support/users';
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
            await page.waitForLoadState('networkidle');
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
            await expect(programmingExerciseOverview.getExerciseDetails().locator('.view-team')).toBeVisible();
            await login(studentFour, `/courses/${course.id}/exercises/${exercise.id}`);
            await expect(programmingExerciseOverview.getExerciseDetails()).toHaveText(/No team yet/);
        });
    });

    // Seed courses are persistent — no cleanup needed
});
