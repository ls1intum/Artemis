import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Course } from 'app/core/course/shared/entities/course.model';
import { expect } from '@playwright/test';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';

test.describe('Competency Exercise Linking', { tag: '@fast' }, () => {
    let course: Course;
    let exercise: TextExercise;
    const exerciseTitle = 'Text Exercise';
    const competenciesData = [
        { title: 'Problem Solving', description: 'Ability to solve complex problems' },
        { title: 'Code Quality', description: 'Writing clean and maintainable code' },
    ];

    test.beforeEach('Setup course with competencies and exercise', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();

        for (const competency of competenciesData) {
            await courseManagementAPIRequests.createCompetency(course, competency.title, competency.description);
        }
        exercise = await exerciseAPIRequests.createTextExercise({ course }, exerciseTitle);
    });

    test.afterEach('Cleanup', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('Links exercise to single competency', async ({ page, courseManagementExercises, competencyManagement }) => {
        await page.goto(`/course-management/${course.id}/exercises`);
        await courseManagementExercises.getExercise(exercise.id!).getByRole('link', { name: 'Edit' }).click();

        // Link competency
        await page.getByRole('checkbox', { name: competenciesData[0].title }).check();
        await page.getByRole('button', { name: 'Save' }).click();
        await expect(page.getByText(competenciesData[0].title)).toBeVisible();

        // Verify exercise is linked
        await competencyManagement.goto(course.id!);
        await page.getByRole('link', { name: competenciesData[0].title }).click();
        await expect(page.getByRole('button', { name: 'Start exercise' })).toBeVisible();
    });

    test('Updates competency-exercise linking', async ({ page, courseManagementExercises, competencyManagement }) => {
        // Link to first competency
        await page.goto(`/course-management/${course.id}/exercises`);
        await courseManagementExercises.getExercise(exercise.id!).getByRole('link', { name: 'Edit' }).click();
        await page.getByRole('checkbox', { name: competenciesData[0].title }).check();
        await page.getByRole('button', { name: 'Save' }).click();
        await expect(page.getByText(competenciesData[0].title)).toBeVisible();

        // Link to second competency
        await page.goto(`/course-management/${course.id}/exercises`);
        await courseManagementExercises.getExercise(exercise.id!).getByRole('link', { name: 'Edit' }).click();

        // Remove first competency
        await page.getByRole('checkbox', { name: competenciesData[0].title }).uncheck();
        // Add second competency
        await page.getByRole('checkbox', { name: competenciesData[1].title }).check();
        await page.getByRole('button', { name: 'Save' }).click();

        // Verify first competency is unlinked and second is linked
        await expect(page.getByText(competenciesData[0].title)).not.toBeVisible();
        await expect(page.getByText(competenciesData[1].title)).toBeVisible();

        await competencyManagement.goto(course.id!);

        // Verify first competency no longer shows exercise
        await page.getByRole('link', { name: competenciesData[0].title }).click();
        await page.waitForLoadState('networkidle');
        await expect(page.getByRole('button', { name: 'Start exercise' })).toHaveCount(0);
        await competencyManagement.goto(course.id!);

        // Verify second competency shows the exercise
        await page.getByRole('link', { name: competenciesData[1].title }).click();
        await expect(page.getByRole('button', { name: 'Start exercise' })).toBeVisible();
    });

    test('Removes competency from exercise when unlinking', async ({ page, courseManagementExercises }) => {
        // Link exercise first
        await page.goto(`/course-management/${course.id}/exercises`);
        await courseManagementExercises.getExercise(exercise.id!).getByRole('link', { name: 'Edit' }).click();
        await page.getByRole('checkbox', { name: competenciesData[0].title }).check();
        await page.getByRole('button', { name: 'Save' }).click();
        await expect(page.getByText(competenciesData[0].title)).toBeVisible();

        // Unlink exercise
        await page.goto(`/course-management/${course.id}/exercises`);
        await courseManagementExercises.getExercise(exercise.id!).getByRole('link', { name: 'Edit' }).click();
        await page.getByRole('checkbox', { name: competenciesData[0].title }).uncheck();
        await page.getByRole('button', { name: 'Save' }).click();

        await expect(page.getByText(competenciesData[0].title)).not.toBeVisible();
    });
});
