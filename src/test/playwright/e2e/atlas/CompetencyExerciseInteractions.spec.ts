import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Course } from 'app/core/course/shared/entities/course.model';
import { expect } from '@playwright/test';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import type { Page } from '@playwright/test';

async function navigateToCompetencyManagement(page: Page, courseId?: number) {
    if (courseId === undefined) {
        throw new Error('navigateToCompetencyManagement called without courseId');
    }
    await page.goto(`/course-management/${courseId}/competency-management`);
    const closeButton = page.locator('#close-button');
    await page.waitForLoadState('networkidle');
    if (await closeButton.isVisible()) {
        await closeButton.click();
    }
}

test.describe('Competency Exercise Linking', { tag: '@fast' }, () => {
    let course: Course;
    let exercise: TextExercise;
    const exerciseTitle = 'Text Exercise';
    const competencyData = [
        { title: 'Problem Solving', description: 'Ability to solve complex problems' },
        { title: 'Code Quality', description: 'Writing clean and maintainable code' },
    ];

    test.beforeEach('Setup course with competencies and exercise', async ({ login, courseManagementAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();

        for (const comp of competencyData) {
            await courseManagementAPIRequests.createCompetency(course, comp.title, comp.description);
        }
        exercise = await exerciseAPIRequests.createTextExercise({ course }, exerciseTitle);
    });

    test('Links exercise to single competency', async ({ page, courseManagementExercises }) => {
        await page.goto(`/course-management/${course.id}/exercises`);
        await courseManagementExercises.getExercise(exercise.id!).getByRole('link', { name: 'Edit' }).click();

        // Link competency
        await page.getByRole('checkbox', { name: competencyData[0].title }).check();
        await page.getByRole('button', { name: 'Save' }).click();
        await expect(page.getByText(competencyData[0].title)).toBeVisible();

        // Verify exercise is linked
        await navigateToCompetencyManagement(page, course.id);
        await page.getByRole('link', { name: competencyData[0].title }).click();
        await expect(page.getByRole('button', { name: 'Start exercise' })).toBeVisible();
    });

    test('Updates competency-exercise linking', async ({ page, courseManagementExercises }) => {
        // Link to first competency
        await page.goto(`/course-management/${course.id}/exercises`);
        await courseManagementExercises.getExercise(exercise.id!).getByRole('link', { name: 'Edit' }).click();
        await page.getByRole('checkbox', { name: competencyData[0].title }).check();
        await page.getByRole('button', { name: 'Save' }).click();
        await expect(page.getByText(competencyData[0].title)).toBeVisible();

        // Link to second competency
        await page.goto(`/course-management/${course.id}/exercises`);
        await courseManagementExercises.getExercise(exercise.id!).getByRole('link', { name: 'Edit' }).click();

        // Remove first competency
        await page.getByRole('checkbox', { name: competencyData[0].title }).uncheck();
        // Add second competency
        await page.getByRole('checkbox', { name: competencyData[1].title }).check();
        await page.getByRole('button', { name: 'Save' }).click();

        // Verify first competency is unlinked and second is linked
        await expect(page.getByText(competencyData[0].title)).not.toBeVisible();
        await expect(page.getByText(competencyData[1].title)).toBeVisible();

        await navigateToCompetencyManagement(page, course.id);

        // Verify first competency no longer shows exercise
        await page.getByRole('link', { name: competencyData[0].title }).click();
        await page.waitForLoadState('networkidle');
        await expect(page.getByRole('button', { name: 'Start exercise' })).not.toBeVisible();

        await navigateToCompetencyManagement(page, course.id);

        // Verify second competency shows the exercise
        await page.getByRole('link', { name: competencyData[1].title }).click();
        await expect(page.getByRole('button', { name: 'Start exercise' })).toBeVisible();
    });

    test('Removes competency from exercise when unlinking', async ({ page, courseManagementExercises }) => {
        // Link exercise first
        await page.goto(`/course-management/${course.id}/exercises`);
        await courseManagementExercises.getExercise(exercise.id!).getByRole('link', { name: 'Edit' }).click();
        await page.getByRole('checkbox', { name: competencyData[0].title }).check();
        await page.getByRole('button', { name: 'Save' }).click();
        await expect(page.getByText(competencyData[0].title)).toBeVisible();

        // Unlink exercise
        await page.goto(`/course-management/${course.id}/exercises`);
        await courseManagementExercises.getExercise(exercise.id!).getByRole('link', { name: 'Edit' }).click();
        await page.getByRole('checkbox', { name: competencyData[0].title }).uncheck();
        await page.getByRole('button', { name: 'Save' }).click();

        await expect(page.getByText(competencyData[0].title)).not.toBeVisible();
    });

    test.afterEach('Cleanup', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
