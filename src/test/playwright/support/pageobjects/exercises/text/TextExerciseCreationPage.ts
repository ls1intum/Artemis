import { Page } from '@playwright/test';
import { Dayjs } from 'dayjs';
import { enterDate } from '../../../utils';
import { BASE_API } from '../../../constants';

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
        const responsePromise = this.page.waitForResponse(`${BASE_API}text-exercises`);
        await this.page.locator('#submit-entity').click();
        return await responsePromise;
    }

    async import() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}text-exercises/import/*`);
        await this.page.locator('#submit-entity').click();
        await responsePromise;
    }

    private async typeText(selector: string, text: string) {
        await this.page.locator(selector).locator('.ace_content').fill(text);
    }
}
