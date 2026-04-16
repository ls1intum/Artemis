import { test } from '../../support/fixtures';
import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import type { Page } from '@playwright/test';
import { generateUUID, setMonacoEditorContent } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { admin } from '../../support/users';

const uid = generateUUID();

async function selectTaxonomy(page: Page, taxonomy: string) {
    const select = page.locator('#taxonomy');
    await expect(select).toBeVisible();

    const optionValue = await page.locator('#taxonomy option').evaluateAll((options, desired) => {
        const wanted = String(desired).trim().toLowerCase();
        for (const option of options) {
            const text = (option.textContent ?? '').trim();
            const lowerText = text.toLowerCase();

            const normalized = lowerText.replace(/^\d+\s*:\s*/, '').trim();
            if (normalized === wanted || normalized.includes(wanted) || lowerText.includes(wanted)) {
                return (option as HTMLOptionElement).value;
            }
        }
        return null;
    }, taxonomy);

    if (optionValue === null) {
        throw new Error(`Could not find taxonomy option ending with '${taxonomy}'`);
    }

    await select.selectOption(optionValue);
}

async function selectDateInPicker(page: Page, pickerId: string, monthsAhead: number, day: number) {
    const picker = page.locator(`jhi-date-time-picker#${pickerId}`);
    await expect(picker).toHaveCount(1);

    const input = picker.locator('input#date-input-field');
    await expect(input).toBeVisible();
    await input.click();

    const panel = page.locator('.owl-dt-container:visible');
    try {
        await expect(panel).toBeVisible({ timeout: 2000 });
    } catch {
        await picker.getByRole('button').first().click();
        await expect(panel).toBeVisible();
    }

    for (let i = 0; i < monthsAhead; i++) {
        await panel.getByRole('button', { name: 'Next month' }).click();
    }

    const targetDate = dayjs().add(monthsAhead, 'months').date(day);
    await panel.getByRole('cell', { name: targetDate.date().toString() }).click();
}

async function setMarkdownDescription(page: Page, text: string) {
    await setMonacoEditorContent(page, 'jhi-markdown-editor-monaco#description', text);
}

const course = { id: SEED_COURSES.atlas1.id } as any;

test.describe('Competency Management', { tag: '@fast' }, () => {
    test('Creates a competency', async ({ login, page, competencyManagement }) => {
        await login(admin);
        const competencyData = {
            title: 'Multiplication ' + uid,
            description: 'The student should learn how to multiply two integers.',
            softDueDate: dayjs().add(2, 'months').date(15),
            taxonomy: 'UNDERSTAND',
        };

        await competencyManagement.goto(course!.id!);

        // Create competency via UI
        await page.locator('a[href*="/competency-management/create"]').click();
        await page.getByRole('textbox', { name: 'Title' }).fill(competencyData.title);
        await setMarkdownDescription(page, competencyData.description);

        await selectDateInPicker(page, 'softDueDate', 2, 15);
        await selectTaxonomy(page, competencyData.taxonomy);
        await page.getByRole('button', { name: 'Submit' }).click();

        // Verify creation - wait for the page to navigate back and load competency data
        await expect(page.getByRole('link', { name: competencyData.title })).toBeVisible({ timeout: 30000 });
        const row = page.locator('tr', { has: page.getByRole('link', { name: competencyData.title }) });
        await expect(row.locator('.markdown-preview')).toContainText(competencyData.description);
        await expect(row.getByRole('cell', { name: competencyData.taxonomy })).toBeVisible();
    });

    test.describe('Competency editing', () => {
        const competencyData = {
            title: 'EditComp ' + uid,
            description: 'The student should learn how to multiply two integers.',
            softDueDate: dayjs().add(2, 'months').date(15),
            taxonomy: 'UNDERSTAND',
        };

        test.beforeEach('Create competency', async ({ login, page, courseManagementAPIRequests, competencyManagement }) => {
            await login(admin);
            await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);
            await competencyManagement.goto(course!.id!);
            await expect(page.getByRole('link', { name: competencyData.title })).toBeVisible({ timeout: 10000 });
        });

        test('Edits a competency', async ({ page }) => {
            const updatedCompetencyData = {
                title: 'Division ' + uid,
                description: 'The student should learn how to divide integers and understand remainders.',
                taxonomy: 'APPLY',
            };

            const row = page.locator('tr', { has: page.getByRole('link', { name: competencyData.title }) });
            await row.locator('a[href*="/competency-management/"][href$="/edit"]').click();

            // Wait for the edit form to load existing competency data from the server.
            // Without this, the test may start filling fields before the form is populated,
            // causing the async title uniqueness validator to run against stale/empty state.
            await expect(page.getByRole('textbox', { name: 'Title' })).toHaveValue(competencyData.title, { timeout: 15000 });

            // Update fields
            await page.getByRole('textbox', { name: 'Title' }).fill(updatedCompetencyData.title);
            await setMarkdownDescription(page, updatedCompetencyData.description);
            await selectTaxonomy(page, updatedCompetencyData.taxonomy);

            // Wait for the async title uniqueness validator to resolve before submitting
            await expect(page.getByRole('button', { name: 'Submit' })).toBeEnabled({ timeout: 10000 });
            await page.getByRole('button', { name: 'Submit' }).click();

            // Verify update
            await expect(page.getByRole('link', { name: updatedCompetencyData.title })).toBeVisible();
            const updatedRow = page.locator('tr', { has: page.getByRole('link', { name: updatedCompetencyData.title }) });
            await expect(updatedRow.locator('.markdown-preview')).toContainText(updatedCompetencyData.description);
            await expect(updatedRow.getByText(new RegExp(updatedCompetencyData.taxonomy, 'i'))).toBeVisible();
        });
    });

    test.describe('Competency deletion', () => {
        const competencyData = {
            title: 'Temporary Comp ' + uid,
            description: 'This competency will be deleted in the test.',
            taxonomy: 'UNDERSTAND',
        };

        test.beforeEach('Create competency', async ({ login, page, courseManagementAPIRequests, competencyManagement }) => {
            await login(admin);
            await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);

            await competencyManagement.goto(course!.id!);
            await expect(page.getByRole('cell', { name: competencyData.title })).toBeVisible({ timeout: 10000 });
        });

        test('Deletes a competency', async ({ page }) => {
            const row = page.locator('tr', { has: page.getByRole('link', { name: competencyData.title }) });
            await row.locator('button[jhideletebutton]').click();

            const deleteButton = page.getByTestId('delete-dialog-confirm-button');
            await expect(deleteButton).toBeDisabled();
            await page.locator('#confirm-entity-name').fill(competencyData.title);
            await deleteButton.click();

            // Verify removal
            await expect(page.locator('tr', { has: page.getByRole('link', { name: competencyData.title }) })).toHaveCount(0);
        });
    });
});

test.describe('Prerequisite Management', { tag: '@fast' }, () => {
    const prerequisiteData = {
        title: 'Basic Arithmetic ' + uid,
        description: 'The student should know basic arithmetic operations like addition and subtraction.',
        softDueDate: dayjs().add(1, 'month').endOf('month'),
        taxonomy: 'UNDERSTAND',
    };

    // Seed courses are persistent — no cleanup needed

    test('Creates a prerequisite', async ({ login, page, competencyManagement }) => {
        await login(admin);
        await competencyManagement.goto(course!.id!);

        // Create prerequisite
        await page.locator('a[href*="/prerequisite-management/create"]').click();
        await page.getByRole('textbox', { name: 'Prerequisites', exact: true }).fill(prerequisiteData.title);
        await setMarkdownDescription(page, prerequisiteData.description);

        await selectDateInPicker(page, 'softDueDate', 1, 15);
        await selectTaxonomy(page, prerequisiteData.taxonomy);
        await expect(page.getByRole('button', { name: 'Submit' })).toBeEnabled({ timeout: 10000 });
        await page.getByRole('button', { name: 'Submit' }).click();

        // Verify creation - wait for the page to navigate back and load data
        await expect(page.getByRole('link', { name: prerequisiteData.title })).toBeVisible({ timeout: 30000 });
        const row = page.locator('tr', { has: page.getByRole('link', { name: prerequisiteData.title }) });
        await expect(row.locator('.markdown-preview')).toContainText(prerequisiteData.description);
        await expect(row.getByText(new RegExp(prerequisiteData.taxonomy, 'i'))).toBeVisible();
    });

    test.describe('Prerequisite editing', () => {
        const editPrereqData = {
            title: 'EditPrereq ' + uid,
            description: 'This prerequisite will be edited in the test.',
        };

        test.beforeEach('Create prerequisite', async ({ login, page, courseManagementAPIRequests, competencyManagement }) => {
            await login(admin);
            await courseManagementAPIRequests.createPrerequisite(course, editPrereqData.title, editPrereqData.description);
            await competencyManagement.goto(course!.id!);
            await expect(page.getByRole('link', { name: editPrereqData.title })).toBeVisible({ timeout: 10000 });
        });

        test('Edits a prerequisite', async ({ page }) => {
            const updatedPrerequisiteData = {
                title: 'Advanced Arithmetic ' + uid,
                description: 'The student should know advanced arithmetic operations like multiplication and division.',
                softDueDate: dayjs().add(2, 'months').endOf('month'),
                taxonomy: 'APPLY',
            };
            // Edit prerequisite
            const row = page.locator('tr', { has: page.getByRole('link', { name: editPrereqData.title }) });
            await row.locator('a[href*="/prerequisite-management/"][href$="/edit"]').click();

            // Wait for the edit form to load existing prerequisite data from the server
            await expect(page.getByRole('textbox', { name: 'Prerequisites', exact: true })).toHaveValue(editPrereqData.title, { timeout: 15000 });

            await page.getByRole('textbox', { name: 'Prerequisites', exact: true }).fill(updatedPrerequisiteData.title);
            await setMarkdownDescription(page, updatedPrerequisiteData.description);

            await selectDateInPicker(page, 'softDueDate', 2, 15);
            await selectTaxonomy(page, updatedPrerequisiteData.taxonomy);

            // Wait for the async title uniqueness validator to resolve before submitting
            await expect(page.getByRole('button', { name: 'Submit' })).toBeEnabled({ timeout: 10000 });
            await page.getByRole('button', { name: 'Submit' }).click();

            // Verify update - wait for navigation back and data load
            await expect(page.getByRole('link', { name: updatedPrerequisiteData.title })).toBeVisible({ timeout: 30000 });
            const updatedRow = page.locator('tr', { has: page.getByRole('link', { name: updatedPrerequisiteData.title }) });
            await expect(updatedRow.locator('.markdown-preview')).toContainText(updatedPrerequisiteData.description);
            await expect(updatedRow.getByText(new RegExp(updatedPrerequisiteData.taxonomy, 'i'))).toBeVisible();
        });
    });

    test.describe('Prerequisite deletion', () => {
        const prerequisiteData = {
            title: 'Temp Prereq ' + uid,
            description: 'This prerequisite will be deleted in the test.',
            taxonomy: 'UNDERSTAND',
        };

        test.beforeEach('Create prerequisite to delete', async ({ login, page, courseManagementAPIRequests, competencyManagement }) => {
            await login(admin);
            await courseManagementAPIRequests.createPrerequisite(course, prerequisiteData.title, prerequisiteData.description);

            await competencyManagement.goto(course!.id!);
            await expect(page.getByRole('link', { name: prerequisiteData.title })).toBeVisible({ timeout: 10000 });
        });

        test('Deletes a prerequisite', async ({ page }) => {
            const row = page.locator('tr', { has: page.getByRole('link', { name: prerequisiteData.title }) });
            await row.locator('button[jhideletebutton]').click();

            // Confirm delete modal
            const deleteButton = page.getByTestId('delete-dialog-confirm-button');
            await expect(deleteButton).toBeDisabled();
            await page.locator('#confirm-entity-name').fill(prerequisiteData.title);
            await deleteButton.click();

            // Verify removal
            await expect(page.locator('tr', { has: page.getByRole('link', { name: prerequisiteData.title }) })).toHaveCount(0);
        });
    });
});
