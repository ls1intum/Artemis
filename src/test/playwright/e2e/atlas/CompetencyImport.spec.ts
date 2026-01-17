import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Course } from 'app/core/course/shared/entities/course.model';
import { expect } from '@playwright/test';

test.describe('Competency Import', { tag: '@fast' }, () => {
    let sourceCourse: Course;
    let targetCourse: Course;
    let comp1: any;
    let comp2: any;
    let prereq: any;

    test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        sourceCourse = await courseManagementAPIRequests.createCourse({ courseName: 'Source Course' });
        targetCourse = await courseManagementAPIRequests.createCourse({ courseName: 'Target Course' });

        // Create competencies and prerequisites in source course
        comp1 = await courseManagementAPIRequests.createCompetency(sourceCourse, 'Source Competency 1');
        comp2 = await courseManagementAPIRequests.createCompetency(sourceCourse, 'Source Competency 2');
        prereq = await courseManagementAPIRequests.createPrerequisite(sourceCourse, 'Source Prerequisite');

        // Create relation: comp1 EXTENDS comp2
        await courseManagementAPIRequests.createCompetencyRelation(sourceCourse, comp1.id, comp2.id, 'EXTENDS');
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(sourceCourse, admin);
        await courseManagementAPIRequests.deleteCourse(targetCourse, admin);
    });

    test('Imports all competencies, prerequisites and relations from other course', async ({ page, competencyManagement }) => {
        await competencyManagement.goto(targetCourse.id!);

        // Open Import All modal
        await page.locator('#courseCompetencyImportAllButton').click();

        // Enable relation import
        await page.locator('#importRelations-checkbox').check();

        // Search for source course and select it
        await page.locator('#import-objects-search').fill(sourceCourse.title!);

        // Click Select button in the row of the source course
        await page.getByRole('row', { name: sourceCourse.title! }).getByRole('button', { name: 'Select' }).first().click();

        // Verify success message
        await expect(page.getByText('Imported 3 competencies')).toBeVisible();
        await page.locator('jhi-close-circle svg').click(); // Close success message

        // Verify imported entities in the list
        await expect(page.getByRole('link', { name: comp1.title })).toBeVisible();
        await expect(page.getByRole('link', { name: comp2.title })).toBeVisible();
        await expect(page.getByRole('link', { name: prereq.title })).toBeVisible();

        // Verify relation
        await page.getByRole('button', { name: 'Edit relations' }).click();

        // Wait for modal to load
        await expect(page.locator('jhi-course-competencies-relation-graph')).toBeVisible();

        // Select the imported competencies to verify the relation exists
        await page.selectOption('#head', { label: comp2.title });
        await page.selectOption('#tail', { label: comp1.title });

        // If relation exists, we should see "Delete relation" button
        await expect(page.getByRole('button', { name: 'Delete relation' })).toBeVisible();

        // Check the type is EXTENDS
        await expect(page.locator('#type')).toHaveValue('EXTENDS');
    });

    test('Imports specific competencies from other course', async ({ page, competencyManagement }) => {
        await competencyManagement.goto(targetCourse.id!);

        // Open Import from other course modal
        await page.getByRole('button', { name: 'Import competencies' }).click();
        await page.getByRole('link', { name: 'Import from other course' }).click();

        // Select only comp1
        await page
            .getByRole('row', { name: '' + comp1.id + ' ' + comp1.title })
            .getByRole('button', { name: 'Select' })
            .click();

        // Click Import
        await page.getByRole('button', { name: 'Import' }).click();

        // Verify success message
        await expect(page.getByText('Imported 1 competencies')).toBeVisible();
        await page.locator('jhi-close-circle svg').click(); // Close success message (reduce wait time)

        // Verify comp1 is imported but comp2 is not
        await expect(page.getByRole('link', { name: comp1.title })).toBeVisible();
        await expect(page.getByRole('link', { name: comp2.title })).not.toBeVisible();
    });
});
