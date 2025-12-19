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
            const competency = await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);

            await courseManagementAPIRequests.enableLearningPaths(course);

            // Create text unit with competency link directly
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 1', 'Content for text unit', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            await competencyManagement.goto(course.id!);

            await page.getByRole('link', { name: competencyData.title }).click();

            await expect(page.getByText('Text Unit 1')).toBeVisible();
        });
    });

    test.describe('Link multiple lecture units to the same competency', () => {
        test('Links multiple text units to a competency via api and verifies all appear in competency detail', async ({
            page,
            courseManagementAPIRequests,
            competencyManagement,
        }) => {
            const competency = await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);

            await courseManagementAPIRequests.enableLearningPaths(course);

            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 1', 'Content for text unit 1', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 2', 'Content for text unit 2', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);
            await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit 3', 'Content for text unit 3', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            await competencyManagement.goto(course.id!);

            await page.getByRole('link', { name: competencyData.title }).click();

            await expect(page.getByText('Text Unit 1')).toBeVisible();
            await expect(page.getByText('Text Unit 2')).toBeVisible();
            await expect(page.getByText('Text Unit 3')).toBeVisible();
        });
    });

    test.describe('Link a lecture unit to competency through UI', () => {
        test('Links a lecture unit to competency through lecture unit creation page', async ({ page, courseManagementAPIRequests, lectureManagement, competencyManagement }) => {
            await courseManagementAPIRequests.createCompetency(course, 'UI Link Competency', 'Competency for UI linking test');
            await courseManagementAPIRequests.enableLearningPaths(course);

            await page.goto(`/course-management/${course.id}/lectures/${lecture.id}/unit-management`);
            await page.waitForLoadState('networkidle');

            await lectureManagement.openCreateUnit(UnitType.TEXT);
            await page.fill('#name', 'UI Created Text Unit');
            await page.locator('.monaco-editor').click();
            await page.locator('.monaco-editor').pressSequentially('Content created through UI');

            await page.getByRole('checkbox', { name: 'UI Link Competency' }).check();

            await page.click('#submitButton');
            await page.waitForLoadState('networkidle');

            await expect(page.getByText('UI Created Text Unit')).toBeVisible();

            await competencyManagement.goto(course.id!);

            await page.getByRole('link', { name: 'UI Link Competency' }).click();
            await expect(page.getByText('UI Created Text Unit')).toBeVisible();
        });
    });

    test.describe('Update/change the competency linked to a lecture unit', () => {
        test('Changes the competency linked to a lecture unit via UI', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            const compA = await courseManagementAPIRequests.createCompetency(course, 'Comp A', 'First competency');
            await courseManagementAPIRequests.createCompetency(course, 'Comp B', 'Second competency');

            await courseManagementAPIRequests.enableLearningPaths(course);

            const textUnit = await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit', 'Content for text unit', [
                { competency: { id: compA.id, type: 'competency' }, weight: 1 },
            ]);

            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: 'Comp A' }).click();
            await expect(page.getByText('Text Unit')).toBeVisible();

            await page.goto(`/course-management/${course.id}/lectures/${lecture.id}/unit-management/text-units/${textUnit.id}/edit`);
            await page.waitForLoadState('networkidle');

            await page.getByRole('checkbox', { name: 'Comp A' }).uncheck();
            await page.getByRole('checkbox', { name: 'Comp B' }).check();

            await page.click('#submitButton');
            await page.waitForLoadState('networkidle');

            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: 'Comp A' }).click();
            await page.waitForLoadState('networkidle');
            await expect(page.getByText('Text Unit')).not.toBeVisible();

            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: 'Comp B' }).click();
            await expect(page.getByText('Text Unit')).toBeVisible();
        });
    });

    test.describe('Remove competency from a lecture unit', () => {
        test('Unlinks a lecture unit from a competency via UI', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            const competency = await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);

            await courseManagementAPIRequests.enableLearningPaths(course);

            const textUnit = await courseManagementAPIRequests.createTextUnit(lecture, 'Text Unit', 'Content for text unit', [
                { competency: { id: competency.id, type: 'competency' }, weight: 1 },
            ]);

            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: competencyData.title }).click();
            await expect(page.getByText('Text Unit')).toBeVisible();

            await page.goto(`/course-management/${course.id}/lectures/${lecture.id}/unit-management/text-units/${textUnit.id}/edit`);
            await page.waitForLoadState('networkidle');

            await page.getByRole('checkbox', { name: competencyData.title }).uncheck();

            await page.click('#submitButton');
            await page.waitForLoadState('networkidle');

            await competencyManagement.goto(course.id!);
            await page.getByRole('link', { name: competencyData.title }).click();
            await page.waitForLoadState('networkidle');
            await expect(page.getByText('Text Unit')).not.toBeVisible();

            await page.reload();
            await expect(page.getByText('Text Unit')).not.toBeVisible();
        });
    });
});
