import { Page } from '@playwright/test';

export class ExamGradingPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async addGradeStep(gradeInterval: number, gradeName: string) {
        await this.page.locator('button', { hasText: 'Add Grade Step' }).click();
        const gradeSteps = this.page.locator('table').locator('tbody').locator('tr');
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
