import { Page } from '@playwright/test';
import { MODELING_EXERCISE_BASE } from '../../../constants';
import { Dayjs } from 'dayjs';
import { enterDate } from '../../../utils';

export class CreateModelingExercisePage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async setTitle(title: string) {
        const titleField = this.page.locator('#field_title');
        await titleField.clear();
        await titleField.fill(title);
    }

    async addCategories(categories: string[]) {
        for (const category of categories) {
            const categoriesField = this.page.locator('#field_categories');
            await categoriesField.fill(category);
            await categoriesField.press('Enter');
        }
    }

    async setPoints(points: number) {
        const pointsField = this.page.locator('#field_points');
        await pointsField.clear();
        await pointsField.fill(points.toString());
    }

    async save() {
        const responsePromise = this.page.waitForResponse(MODELING_EXERCISE_BASE);
        await this.page.click('#save-entity');
        return await responsePromise;
    }

    async import() {
        const responsePromise = this.page.waitForResponse(`${MODELING_EXERCISE_BASE}/import/*`);
        await this.page.click('#save-entity');
        return await responsePromise;
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

    async includeInOverallScore(selection: string = 'No') {
        await this.page.locator('#modeling-includeInScore-picker').locator('.btn', { hasText: selection }).click({ force: true });
    }

    async pickDifficulty(options: { hard?: boolean; medium?: boolean; easy?: boolean }) {
        const difficultyBar = this.page.locator('#modeling-difficulty-picker');
        if (options.hard) {
            await difficultyBar.locator('.btn', { hasText: 'Hard' }).click();
        } else if (options.medium) {
            await difficultyBar.locator('.btn', { hasText: 'Medium' }).click();
        } else if (options.easy) {
            await difficultyBar.locator('.btn', { hasText: 'Easy' }).click();
        } else {
            await difficultyBar.locator('.btn', { hasText: 'No Level' }).click();
        }
    }
}
