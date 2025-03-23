import { expect } from '@playwright/test';
import { test } from '../support/fixtures';
import { Course } from 'app/core/shared/entities/course.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { admin, studentOne, studentTwo } from '../support/users';

test.describe('Logout tests', () => {
    let course: Course;
    let modelingExercise: ModelingExercise;

    test.beforeEach('Login as admin and create a course with a modeling exercise', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);

        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
        modelingExercise = await exerciseAPIRequests.createModelingExercise({ course });
    });

    test('Logs out after confirmation of dialog for unsaved changes on exercise mode', async ({ page, login, courseOverview, modelingExerciseEditor, navigationBar }) => {
        await login(studentOne);

        const exerciseID = modelingExercise.id!;
        await page.goto(`/courses/${course.id}/exercises`);
        await courseOverview.startExercise(exerciseID);
        await courseOverview.openRunningExercise(exerciseID);
        await modelingExerciseEditor.addComponentToModel(exerciseID, 1);
        await modelingExerciseEditor.addComponentToModel(exerciseID, 2);

        page.on('dialog', async (dialog) => {
            expect(dialog.message()).toContain('You have unsaved changes');
            await dialog.accept();
        });

        await navigationBar.logout();

        await page.waitForURL(process.env.BASE_URL!);
    });

    test('Stays logged in after dismissal of dialog for unsaved changes on exercise mode', async ({ login, page, courseOverview, modelingExerciseEditor, navigationBar }) => {
        await login(studentTwo);

        const exerciseID = modelingExercise.id!;
        await page.goto(`/courses/${course.id}/exercises`);
        await courseOverview.startExercise(exerciseID);
        await courseOverview.openRunningExercise(exerciseID);
        await modelingExerciseEditor.addComponentToModel(exerciseID, 1);
        await modelingExerciseEditor.addComponentToModel(exerciseID, 2);

        page.on('dialog', async (dialog) => {
            expect(dialog.message()).toContain('You have unsaved changes');
            await dialog.dismiss();
        });

        await navigationBar.logout();

        await page.waitForLoadState('load');
        expect(page.url()).not.toEqual(process.env.BASE_URL!);
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
