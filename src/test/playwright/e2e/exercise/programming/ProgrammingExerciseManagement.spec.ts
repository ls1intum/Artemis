import { Course } from 'app/entities/course.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

import { admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { generateUUID } from '../../../support/utils';
import { expect } from '@playwright/test';
import { Exercise, ExerciseMode } from '../../../support/constants';

test.describe('Programming Exercise Management', () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
    });

    test.describe('Programming exercise creation', () => {
        test('Creates a new programming exercise', async ({ login, page, navigationBar, courseManagement, courseManagementExercises, programmingExerciseCreation }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.createProgrammingExercise();
            await page.waitForURL('**/programming-exercises/new**');
            const exerciseTitle = 'Programming exercise ' + generateUUID();
            await programmingExerciseCreation.setTitle(exerciseTitle);
            await programmingExerciseCreation.setShortName('programming' + generateUUID());
            await programmingExerciseCreation.setPackageName('de.test');
            await programmingExerciseCreation.setPoints(100);
            await programmingExerciseCreation.checkAllowOnlineEditor();
            const response = await programmingExerciseCreation.generate();
            const exercise: Exercise = await response.json();
            await expect(courseManagementExercises.getExerciseTitle(exerciseTitle)).toBeVisible();
            await page.waitForURL(`**/programming-exercises/${exercise.id}**`);
        });
    });

    test.describe('Programming exercise deletion', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Create programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin, '/');
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course });
        });

        test('Deletes an existing programming exercise', async ({ login, navigationBar, courseManagement, courseManagementExercises }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.deleteProgrammingExercise(exercise);
            await expect(courseManagementExercises.getExercise(exercise.id!)).not.toBeAttached();
        });
    });

    test.describe('Programming exercise team creation', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Setup team programming exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            const teamAssignmentConfig = { minTeamSize: 2, maxTeamSize: 3 };
            exercise = await exerciseAPIRequests.createProgrammingExercise({
                course,
                mode: ExerciseMode.TEAM,
                teamAssignmentConfig,
            });
        });

        test('Create an exercise team', async ({ login, page, navigationBar, courseManagement, courseManagementExercises, exerciseTeams, programmingExerciseOverview }) => {
            await login(instructor, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
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

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
