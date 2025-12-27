import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Course } from 'app/core/course/shared/entities/course.model';
import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import type { Page } from '@playwright/test';

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
    const monaco = page.locator('jhi-markdown-editor-monaco#description .monaco-editor');
    if (await monaco.count()) {
        await monaco.click();
        await monaco.press('Control+A').catch(() => {});
        await monaco.press('Meta+A').catch(() => {});
        await monaco.press('Backspace');

        const monacoWithPressSequentially = monaco as unknown as { pressSequentially?: (value: string) => Promise<void> };
        if (typeof monacoWithPressSequentially.pressSequentially === 'function') {
            await monacoWithPressSequentially.pressSequentially(text);
        } else {
            await monaco.fill(text);
        }
    } else {
        const fallback = page.getByRole('textbox', { name: 'Editor content' });
        await fallback.click();
        await fallback.fill(text);
    }
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

        // Create competency via UI
        await page.locator('a[href*="/competency-management/create"]').click();
        await page.getByRole('textbox', { name: 'Title' }).fill(competencyData.title);
        await setMarkdownDescription(page, competencyData.description);

        await selectDateInPicker(page, 'softDueDate', 2, 15);
        await selectTaxonomy(page, competencyData.taxonomy);
        await page.getByRole('button', { name: 'Submit' }).click();

        // Verify creation
        await page.waitForLoadState('networkidle');
        await expect(page.getByRole('link', { name: competencyData.title })).toBeVisible();
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
            await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);
            await competencyManagement.goto(course!.id!);
            await page.waitForLoadState('networkidle');
            await expect(page.getByRole('link', { name: competencyData.title })).toBeVisible();
        });

        test('Edits a competency', async ({ page }) => {
            const updatedCompetencyData = {
                title: 'Division',
                description: 'The student should learn how to divide integers and understand remainders.',
                taxonomy: 'APPLY',
            };

            const row = page.locator('tr', { has: page.getByRole('link', { name: competencyData.title }) });
            await row.locator('a[href*="/competency-management/"][href$="/edit"]').click();

            // Update fields
            await page.getByRole('textbox', { name: 'Title' }).fill(updatedCompetencyData.title);
            await setMarkdownDescription(page, updatedCompetencyData.description);
            await selectTaxonomy(page, updatedCompetencyData.taxonomy);
            await page.getByRole('button', { name: 'Submit' }).click();

            // Verify update
            await expect(page.getByRole('link', { name: updatedCompetencyData.title })).toBeVisible();
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
            await courseManagementAPIRequests.createCompetency(course, competencyData.title, competencyData.description);

            await competencyManagement.goto(course!.id!);
            await page.waitForLoadState('networkidle');
            await expect(page.getByRole('cell', { name: competencyData.title })).toBeVisible();
        });

        test('Deletes a competency', async ({ page }) => {
            const row = page.locator('tr', { has: page.getByRole('link', { name: competencyData.title }) });
            await row.locator('button[jhideletebutton]').click();

            await expect(page.locator('#delete')).toBeDisabled();
            await page.locator('#confirm-entity-name').fill(competencyData.title);
            await page.locator('#delete').click();

            // Verify removal
            await expect(page.locator('tr', { has: page.getByRole('link', { name: competencyData.title }) })).toHaveCount(0);
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
        await page.locator('a[href*="/prerequisite-management/create"]').click();
        await page.getByRole('textbox', { name: 'Prerequisites' }).fill(prerequisiteData.title);
        await setMarkdownDescription(page, prerequisiteData.description);

        await selectDateInPicker(page, 'softDueDate', 1, 15);
        await selectTaxonomy(page, prerequisiteData.taxonomy);
        await page.getByRole('button', { name: 'Submit' }).click();

        // Verify creation
        await page.waitForLoadState('networkidle');
        await expect(page.getByRole('link', { name: prerequisiteData.title })).toBeVisible();
        await expect(page.locator('.markdown-preview')).toContainText(prerequisiteData.description);
        await expect(page.getByText(new RegExp(prerequisiteData.taxonomy, 'i'))).toBeVisible();
    });

    test.describe('Prerequisite editing', () => {
        test.beforeEach('Create prerequisite', async ({ page, courseManagementAPIRequests, competencyManagement }) => {
            // Create prerequisite via API
            await courseManagementAPIRequests.createPrerequisite(course, prerequisiteData.title, prerequisiteData.description);
            await competencyManagement.goto(course!.id!);

            await page.waitForLoadState('networkidle');
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
            await page.locator('a[href*="/prerequisite-management/"][href$="/edit"]').first().click();
            await page.getByRole('textbox', { name: 'Prerequisites' }).fill(updatedPrerequisiteData.title);
            await setMarkdownDescription(page, updatedPrerequisiteData.description);

            await selectDateInPicker(page, 'softDueDate', 2, 15);
            await selectTaxonomy(page, updatedPrerequisiteData.taxonomy);
            await page.getByRole('button', { name: 'Submit' }).click();

            // Verify update
            await page.waitForLoadState('networkidle');
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
            await courseManagementAPIRequests.createPrerequisite(course, prerequisiteData.title, prerequisiteData.description);

            await competencyManagement.goto(course!.id!);
            await page.waitForLoadState('networkidle');
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
