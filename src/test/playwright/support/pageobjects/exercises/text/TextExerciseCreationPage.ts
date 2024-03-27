import { Page } from '@playwright/test';
import { Dayjs } from 'dayjs';
import { enterDate } from '../../../utils';
import { TEXT_EXERCISE_BASE } from '../../../constants';

export class TextExerciseCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async typeTitle(title: string) {
        const titleField = this.page.locator('#field_title');
        await titleField.clear();
        await titleField.fill(title);
    }

    async setReleaseDate(date: Dayjs) {
        await enterDate(this.page, '#pick-releaseDate', date);
    }

    async setDueDate(date: Dayjs) {
        await enterDate(this.page, '#pick-dueDate', date);
    }

    async setAssessmentDueDate(date: Dayjs) {
        await enterDate(this.page, '#pick-assessmentDueDate', date);
    }

    async typeMaxPoints(maxPoints: number) {
        await this.page.locator('#field_points').fill(maxPoints.toString());
    }

    async typeProblemStatement(statement: string) {
        await this.typeText('#problemStatement', statement);
    }

    async typeExampleSolution(statement: string) {
        await this.typeText('#exampleSolution', statement);
    }

    async typeAssessmentInstructions(statement: string) {
        await this.typeText('#gradingInstructions', statement);
    }

    async create() {
        const responsePromise = this.page.waitForResponse(TEXT_EXERCISE_BASE);
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    async import() {
        const responsePromise = this.page.waitForResponse(`${TEXT_EXERCISE_BASE}/import/*`);
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    private async typeText(selector: string, text: string) {
        const textField = this.page.locator(selector).locator('.ace_content');
        await textField.click();
        await textField.pressSequentially(text);
    }
}
