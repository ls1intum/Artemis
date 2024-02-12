import { Page } from 'playwright';
import { BASE_API } from '../../../constants';
import { Dayjs } from 'dayjs';
import { enterDate } from '../../../utils';

export class FileUploadExerciseCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async typeTitle(title: string) {
        await this.page.locator('#field_title').fill(title);
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

    async setFilePattern(pattern: string) {
        await this.typeText('#field_filePattern', pattern);
    }

    async typeProblemStatement(statement: string) {
        await this.typeText('#field_problemStatement', statement);
    }

    async typeExampleSolution(statement: string) {
        await this.typeText('#field_exampleSolution', statement);
    }

    async typeAssessmentInstructions(statement: string) {
        await this.typeText('#gradingInstructions', statement);
    }

    async create() {
        const responsePromise = this.page.waitForResponse(BASE_API + 'file-upload-exercises');
        await this.page.locator('#save-entity').click();
        return await responsePromise;
    }

    private async typeText(selector: string, text: string) {
        await this.page.locator(selector).locator('.ace_content').fill(text);
    }
}
