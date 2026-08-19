import { Page, expect } from '@playwright/test';

export class ExamGradingPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async addGradeStep(gradeInterval: number, gradeName: string) {
        const addButton = this.page.locator('button', { hasText: 'Add Grade Step' });
        // Make sure the grading editor has rendered before counting rows (the button sits below the table).
        await addButton.waitFor({ state: 'visible' });
        const gradeSteps = this.page.locator('table').locator('tbody').locator('tr');
        const numberOfGradeStepsBefore = await gradeSteps.count();
        await addButton.click();
        // Under Angular's zoneless change detection the new grade-step row renders on the next change-detection tick
        // (a macrotask) rather than synchronously after the click, so wait for the row count to grow instead of
        // reading it immediately (a one-shot read would race the tick and target the wrong row).
        await expect.poll(() => gradeSteps.count()).toBeGreaterThan(numberOfGradeStepsBefore);
        const numberOfGradeSteps = await gradeSteps.count();
        const newGradeStep = gradeSteps.nth(numberOfGradeSteps - 2);
        await newGradeStep.locator('input').first().fill(`${gradeInterval}`);
        await newGradeStep.locator('input').nth(1).fill(gradeName);
    }

    async enterLastGradeName(gradeName: string) {
        const gradeSteps = this.page.locator('table').locator('tbody').locator('tr');
        const lastGrade = gradeSteps.last();
        await lastGrade.locator('input').first().fill(gradeName);
    }

    async selectFirstPassingGrade(gradeName: string) {
        await this.page.locator('select[title="first passing grade"]').selectOption(gradeName);
    }

    async generateDefaultGrading() {
        await this.page.locator('button', { hasText: 'Generate Default Grading Key' }).click();
    }

    async saveGradingKey() {
        await this.page.locator('button', { hasText: 'Save' }).click();
    }
}
