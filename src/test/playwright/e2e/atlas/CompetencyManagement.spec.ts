import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Course } from 'app/core/course/shared/entities/course.model';
import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import type { Page } from '@playwright/test';

async function selectDateInPicker(page: Page, monthsAhead: number, day: number) {
    await page.locator('.btn.position-absolute').first().click();
    for (let i = 0; i < monthsAhead; i++) {
        await page.getByRole('button', { name: 'Next month' }).click();
    }
    await page.getByText(String(day)).first().click();
}

test.describe('Competency Management', { tag: '@fast' }, () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('Creates a competency', async ({ page, competencyManagement }) => {
        const competencyData = {
            title: 'Multiplication',
            description: 'The student should learn how to multiply two integers.',
            softDueDate: dayjs().add(2, 'months').date(15),
            taxonomy: 'UNDERSTAND',
        };

        await competencyManagement.goto(course!.id!);

        // Create competency
        await page.getByRole('link', { name: 'Create competency' }).click();
        await page.getByRole('textbox', { name: 'Title' }).fill(competencyData.title);
        await page.getByRole('textbox', { name: 'Editor content' }).fill(competencyData.description);

        // Set soft due date
        await selectDateInPicker(page, 2, 15);

        // Set taxonomy
        await page.getByLabel('Taxonomy').selectOption(`2: ${competencyData.taxonomy}`);

        // Submit
        await page.getByRole('button', { name: 'Submit' }).click();

        // Verify creation
        await expect(page.getByRole('cell', { name: 'Multiplication' })).toContainText(competencyData.title);
        await expect(page.locator('.markdown-preview')).toContainText(competencyData.description);
        await expect(page.getByRole('cell', { name: competencyData.taxonomy })).toBeVisible();
    });

    test.describe('Competency editing', () => {
        const competencyData = {
            title: 'Multiplication',
            description: 'The student should learn how to multiply two integers.',
            softDueDate: dayjs().add(2, 'months').date(15),
            taxonomy: 'UNDERSTAND',
        };

        test.beforeEach('Create competency', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            // Create competency via API
            await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);

            await competencyManagement.goto(course!.id!);

            // Verify creation
            await expect(page.getByRole('cell', { name: competencyData.title })).toBeVisible();
        });

        test('Edits a competency', async ({ page }) => {
            const updatedCompetencyData = {
                title: 'Division',
                description: 'The student should learn how to divide integers and understand remainders.',
                taxonomy: 'APPLY',
            };

            // Scope edit to the row containing our competency title
            const row = page.locator('tr', { has: page.getByRole('cell', { name: competencyData.title }) });
            await row.getByRole('link', { name: 'Edit' }).click();

            // Update fields
            await page.getByRole('textbox', { name: 'Title' }).fill(updatedCompetencyData.title);
            // Ensure editor is cleared before entering new description (some editors append by default)
            const competencyEditor = page.getByRole('textbox', { name: 'Editor content' });
            await competencyEditor.press('Control+A').catch(() => {});
            await competencyEditor.press('Meta+A').catch(() => {});
            await competencyEditor.press('Backspace');
            await competencyEditor.type(updatedCompetencyData.description);

            // Update taxonomy
            await page.getByLabel('Taxonomy').selectOption(`3: ${updatedCompetencyData.taxonomy}`);

            // Submit
            await page.getByRole('button', { name: 'Submit' }).click();

            // Verify update
            await expect(page.getByRole('cell', { name: updatedCompetencyData.title })).toBeVisible();
            await expect(page.locator('.markdown-preview')).toContainText(updatedCompetencyData.description);
            await expect(page.getByText(new RegExp(updatedCompetencyData.taxonomy, 'i'))).toBeVisible();
        });
    });

    test.describe('Competency deletion', () => {
        const competencyData = {
            title: 'Temporary Competency',
            description: 'This competency will be deleted in the test.',
            taxonomy: 'UNDERSTAND',
        };

        test.beforeEach('Create competency', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            // Create competency via API
            await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);

            await competencyManagement.goto(course!.id!);
            await expect(page.getByRole('cell', { name: competencyData.title })).toBeVisible();
        });

        test('Deletes a competency', async ({ page }) => {
            const row = page.locator('tr', { has: page.getByRole('cell', { name: competencyData.title }) });
            await row.locator('button[jhideletebutton]').click();

            // Confirm delete modal
            await expect(page.locator('#delete')).toBeDisabled();
            await page.locator('#confirm-entity-name').fill(competencyData.title);
            await page.locator('#delete').click();

            // Verify removal
            await expect(page.locator('tr', { has: page.getByRole('cell', { name: competencyData.title }) })).toHaveCount(0);
        });
    });
});

test.describe('Prerequisite Management', { tag: '@fast' }, () => {
    let course: Course;
    const prerequisiteData = {
        title: 'Basic Arithmetic',
        description: 'The student should know basic arithmetic operations like addition and subtraction.',
        softDueDate: dayjs().add(1, 'month').endOf('month'),
        taxonomy: 'UNDERSTAND',
    };

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('Creates a prerequisite', async ({ page, competencyManagement }) => {
        await competencyManagement.goto(course!.id!);

        // Create prerequisite
        await page.getByRole('link', { name: 'Create prerequisite' }).click();
        await page.getByRole('textbox', { name: 'Prerequisites' }).fill(prerequisiteData.title);
        await page.getByRole('textbox', { name: 'Editor content' }).fill(prerequisiteData.description);

        // Set soft due date
        await selectDateInPicker(page, 1, 15);

        // Set taxonomy
        await page.getByLabel('Taxonomy').selectOption(`2: ${prerequisiteData.taxonomy}`);

        // Submit
        await page.getByRole('button', { name: 'Submit' }).click();

        // Verify creation
        await expect(page.getByRole('link', { name: prerequisiteData.title })).toBeVisible();
        await expect(page.locator('.markdown-preview')).toContainText(prerequisiteData.description);
        await expect(page.getByText(new RegExp(prerequisiteData.taxonomy, 'i'))).toBeVisible();
    });

    test.describe('Prerequisite editing', () => {
        test.beforeEach('Create prerequisite', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            // Create prerequisite via API
            await courseManagementAPIRequests.createPrerequisite(course, prerequisiteData.title, prerequisiteData.description);

            await competencyManagement.goto(course!.id!);

            // Verify prerequisite was created
            await expect(page.getByRole('link', { name: prerequisiteData.title })).toBeVisible();
        });

        test('Edits a prerequisite', async ({ page }) => {
            const updatedPrerequisiteData = {
                title: 'Advanced Arithmetic',
                description: 'The student should know advanced arithmetic operations like multiplication and division.',
                softDueDate: dayjs().add(2, 'months').endOf('month'),
                taxonomy: 'APPLY',
            };

            // Edit prerequisite
            await page.getByRole('link', { name: 'Edit' }).first().click();
            await page.getByRole('textbox', { name: 'Prerequisites' }).fill(updatedPrerequisiteData.title);
            // Ensure editor is cleared before entering new description (some editors append by default)
            const prereqEditor = page.getByRole('textbox', { name: 'Editor content' });
            await prereqEditor.press('Control+A').catch(() => {});
            await prereqEditor.press('Meta+A').catch(() => {});
            await prereqEditor.press('Backspace');
            await prereqEditor.type(updatedPrerequisiteData.description);

            // Set updated soft due date
            await selectDateInPicker(page, 2, 15);

            // Set updated taxonomy
            await page.getByLabel('Taxonomy').selectOption(`3: ${updatedPrerequisiteData.taxonomy}`);

            // Submit
            await page.getByRole('button', { name: 'Submit' }).click();

            // Verify update
            await expect(page.getByRole('link', { name: updatedPrerequisiteData.title })).toBeVisible();
            await expect(page.locator('.markdown-preview')).toContainText(updatedPrerequisiteData.description);
            await expect(page.getByText(new RegExp(updatedPrerequisiteData.taxonomy, 'i'))).toBeVisible();
        });
    });

    test.describe('Prerequisite deletion', () => {
        const prerequisiteData = {
            title: 'Temporary Prerequisite',
            description: 'This prerequisite will be deleted in the test.',
            taxonomy: 'UNDERSTAND',
        };

        test.beforeEach('Create prerequisite to delete', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            // Create prerequisite via API
            await courseManagementAPIRequests.createPrerequisite(course, prerequisiteData.title, prerequisiteData.description);

            await competencyManagement.goto(course!.id!);
            await expect(page.getByRole('link', { name: prerequisiteData.title })).toBeVisible();
        });

        test('Deletes a prerequisite', async ({ page }) => {
            const row = page.locator('tr', { has: page.getByRole('link', { name: prerequisiteData.title }) });
            await row.locator('button[jhideletebutton]').click();

            // Confirm delete modal
            await expect(page.locator('#delete')).toBeDisabled();
            await page.locator('#confirm-entity-name').fill(prerequisiteData.title);
            await page.locator('#delete').click();

            // Verify removal
            await expect(page.locator('tr', { has: page.getByRole('link', { name: prerequisiteData.title }) })).toHaveCount(0);
        });
    });
});
