import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { expect } from '@playwright/test';
import { UnitType } from '../../support/pageobjects/lecture/LectureManagementPage';

test.describe('Competency Lecture Unit Linking', { tag: '@fast' }, () => {
    let course: Course;
    let lecture: Lecture;
    const competencyData = { title: 'Competency 1', description: 'Test competency for lecture unit linking' };

    test.beforeEach('Setup course with lecture', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        lecture = await courseManagementAPIRequests.createLecture(course, 'Test Lecture');
    });

    test.afterEach('Cleanup', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test.describe('Link a lecture unit to a single competency', () => {
        test('Links a text unit to a competency via api and verifies it in competency detail', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            // Preconditions via API
            const competency = await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);

            // Enable learning paths
            await courseManagementAPIRequests.enableLearningPaths(course);

            // Create text unit with competency link directly
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 1', 'Content for text unit', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            // Navigate to competency management page
            await competencyManagement.goto(course.id!);

            // Open the competency details
            await page.getByRole('link', { name: competencyData.title }).click();

            // Assert: The lecture unit appears in the competency's lecture units list
            await expect(page.getByText('Text Unit 1')).toBeVisible();
        });
    });

    test.describe('Link multiple lecture units to the same competency', () => {
        test('Links multiple text units to a competency via api and verifies all appear in competency detail', async ({
            page,
            courseManagementAPIRequests,
            competencyManagement,
        }) => {
            // Preconditions via API - create competency first
            const competency = await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);

            // Enable learning paths
            await courseManagementAPIRequests.enableLearningPaths(course);

            // Create 3 lecture units directly linked to the competency
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 1', 'Content for text unit 1', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 2', 'Content for text unit 2', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 3', 'Content for text unit 3', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            // Navigate to competency management page
            await competencyManagement.goto(course.id!);

            // Open the competency details
            await page.getByRole('link', { name: competencyData.title }).click();

            // Assert: All lecture units appear in the competency's lecture units list
            await expect(page.getByText('Text Unit 1')).toBeVisible();
            await expect(page.getByText('Text Unit 2')).toBeVisible();
            await expect(page.getByText('Text Unit 3')).toBeVisible();
        });
    });

    test.describe('Link a lecture unit to competency through UI', () => {
        test('Links a lecture unit to competency through lecture unit creation page', async ({ page, courseManagementAPIRequests, lectureManagement, competencyManagement }) => {
            // Preconditions via API
            await courseManagementAPIRequests.createCompetency(course, 'UI Link Competency', 'Competency for UI linking test');
            await courseManagementAPIRequests.enableLearningPaths(course);

            // Navigate to lecture units page
            await page.goto(`/course-management/${course.id}/lectures/${lecture.id}/unit-management`);
            await page.waitForLoadState('networkidle');

            // Create a text unit through UI
            await lectureManagement.openCreateUnit(UnitType.TEXT);
            await page.fill('#name', 'UI Created Text Unit');
            await page.locator('.monaco-editor').click();
            await page.locator('.monaco-editor').pressSequentially('Content created through UI');

            // Link to competency in the creation form
            await page.getByRole('checkbox', { name: 'UI Link Competency' }).check();

            // Submit
            await page.click('#submitButton');
            await page.waitForLoadState('networkidle');

            // Verify the text unit was created with competency link
            await expect(page.getByText('UI Created Text Unit')).toBeVisible();

            // Navigate to competency management to verify the link
            await competencyManagement.goto(course.id!);

            await page.getByRole('link', { name: 'UI Link Competency' }).click();
            await expect(page.getByText('UI Created Text Unit')).toBeVisible();
        });
    });

    test.describe('Update/change the competency linked to a lecture unit', () => {
        test('Changes the competency linked to a lecture unit via UI', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            // Preconditions via API - create two competencies
            const compA = await courseManagementAPIRequests.createCompetency(course, 'Comp A', 'First competency');
            await courseManagementAPIRequests.createCompetency(course, 'Comp B', 'Second competency');

            // Enable learning paths
            await courseManagementAPIRequests.enableLearningPaths(course);

            // Create text unit linked to Comp A
            const textUnit = await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit', 'Content for text unit', [
                { competency: { id: compA.id, type: 'competency' }, weight: 1 },
            ]);

            // Verify initial state - text unit is linked to Comp A
            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: 'Comp A' }).click();
            await expect(page.getByText('Text Unit')).toBeVisible();

            // Change the competency link from Comp A to Comp B via UI
            await page.goto(`/course-management/${course.id}/lectures/${lecture.id}/unit-management/text-units/${textUnit.id}/edit`);
            await page.waitForLoadState('networkidle');

            // Uncheck Comp A and check Comp B
            await page.getByRole('checkbox', { name: 'Comp A' }).uncheck();
            await page.getByRole('checkbox', { name: 'Comp B' }).check();

            // Save changes
            await page.click('#submitButton');
            await page.waitForLoadState('networkidle');

            // Assert: On Comp A page, the lecture unit is no longer listed
            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: 'Comp A' }).click();
            await page.waitForLoadState('networkidle');
            await expect(page.getByText('Text Unit')).not.toBeVisible();

            // Assert: On Comp B page, the lecture unit appears in the list
            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: 'Comp B' }).click();
            await expect(page.getByText('Text Unit')).toBeVisible();
        });
    });

    test.describe('Remove competency from a lecture unit', () => {
        test('Unlinks a lecture unit from a competency via UI', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            // Preconditions via API
            const competency = await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);

            await courseManagementAPIRequests.enableLearningPaths(course);

            // Create text unit linked to competency
            const textUnit = await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit', 'Content for text unit', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            // Verify initial state - text unit is linked
            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: competencyData.title }).click();
            await expect(page.getByText('Text Unit')).toBeVisible();

            // Remove the competency link via UI
            await page.goto(`/course-management/${course.id}/lectures/${lecture.id}/unit-management/text-units/${textUnit.id}/edit`);
            await page.waitForLoadState('networkidle');

            // Uncheck the competency
            await page.getByRole('checkbox', { name: competencyData.title }).uncheck();

            // Save changes
            await page.click('#submitButton');
            await page.waitForLoadState('networkidle');

            // Assert: The lecture unit disappears from the list
            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: competencyData.title }).click();
            await page.waitForLoadState('networkidle');
            await expect(page.getByText('Text Unit')).not.toBeVisible();

            // Refresh page and confirm it still isn't listed (persistence)
            await page.reload();
            await expect(page.getByText('Text Unit')).not.toBeVisible();
        });
    });
});
