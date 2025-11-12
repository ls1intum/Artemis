import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Course } from 'app/core/course/shared/entities/course.model';
import { expect } from '@playwright/test';

test.describe('Learning Path Management', { tag: '@fast' }, () => {
    let course: Course;

    test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
    });

    test('Instructor enables learning paths via activation card', async ({ page }) => {
        // Arrange: course initially without learning paths enabled
        await page.goto(`/course-management/${course.id}/learning-path-management`);

        // Feature activation card is visible and can be enabled
        const activationCard = page.locator('jhi-feature-activation');
        await expect(activationCard).toBeVisible();
        await activationCard.locator('.jhi-btn').first().click();

        // Assert: management UI becomes visible after enabling
        await expect(page.locator('.learning-paths-analytics-container')).toBeVisible();
    });

    test('Instructor enables learning paths via course settings', async ({ page }) => {
        // Arrange: course initially without learning paths enabled
        await page.goto(`/course-management/${course.id}`);

        await page.getByRole('link', { name: 'Settings' }).click();

        const lpCheckbox = page.locator('#field_learningPathsEnabled');
        await expect(lpCheckbox).toBeVisible();
        await lpCheckbox.click();
        await page.locator('#save-entity').click();

        await page.goto(`/course-management/${course.id}/learning-path-management`);

        // Assert: management UI becomes visible after enabling
        await expect(page.locator('.learning-paths-analytics-container')).toBeVisible();
    });

    test('Instructor disables learning paths via course settings', async ({ page }) => {
        await page.goto(`/course-management/${course.id}`);

        await page.getByRole('link', { name: 'Settings' }).click();

        // Toggle checkbox off and save
        const lpCheckbox = page.locator('#field_learningPathsEnabled');
        await expect(lpCheckbox).toBeVisible();
        if (await lpCheckbox.isChecked()) {
            await lpCheckbox.click();
        }
        await page.locator('#save-entity').click();

        await page.goto(`/course-management/${course.id}/learning-path-management`);
        await expect(page.locator('jhi-feature-activation')).toBeVisible();
    });

    test('Create simple learning path', async ({ page, courseManagementAPIRequests }) => {
        // Enable learning paths first
        await page.goto(`/course-management/${course.id}/learning-path-management`);
        const activationCard = page.locator('jhi-feature-activation');
        await expect(activationCard).toBeVisible();
        await activationCard.locator('.jhi-btn').first().click();
        await expect(page.locator('.learning-paths-analytics-container')).toBeVisible();

        // Create all competencies and prerequisites via API and store their IDs
        const comp1 = await courseManagementAPIRequests.createCompetency(course, 'Competency 1', 'Seed competency for learning path graph test 1');
        const comp2 = await courseManagementAPIRequests.createCompetency(course, 'Competency 2', 'Seed competency for learning path graph test 2');
        const comp3 = await courseManagementAPIRequests.createCompetency(course, 'Competency 3', 'Seed competency for learning path graph test 3');
        const comp4 = await courseManagementAPIRequests.createCompetency(course, 'Competency 4', 'Seed competency for learning path graph test 4');
        const comp5 = await courseManagementAPIRequests.createCompetency(course, 'Competency 5', 'Seed competency for learning path graph test 5');
        const comp6 = await courseManagementAPIRequests.createCompetency(course, 'Competency 6', 'Seed competency for learning path graph test 6');
        const prereq1 = await courseManagementAPIRequests.createPrerequisite(course, 'Prerequisite 1', 'Prerequisite of Competency 1');
        const prereq2 = await courseManagementAPIRequests.createPrerequisite(course, 'Prerequisite 2', 'Prerequisite of Competency 2');

        // Create all relations via API
        // Note: In the UI "Requires" is shown, but the backend uses "ASSUMES" for the tail assuming the head
        await courseManagementAPIRequests.createCompetencyRelation(course, prereq1.id, comp1.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, prereq2.id, comp2.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp1.id, comp3.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp2.id, comp3.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp1.id, comp4.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp2.id, comp5.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp3.id, comp6.id, 'EXTENDS');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp4.id, comp6.id, 'EXTENDS');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp5.id, comp6.id, 'ASSUMES');

        await page.reload();
        await expect(page.locator('.learning-paths-analytics-container')).toBeVisible();
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
