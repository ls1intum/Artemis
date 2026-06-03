import { Dayjs } from 'dayjs';
import { MODELING_EXERCISE_BASE } from '../../../constants';
import { AbstractExerciseCreationPage } from '../AbstractExerciseCreationPage';

// Date format expected by ExerciseTimelineComponent#handleManualInput.
const TIMELINE_DATE_FORMAT = 'DD.MM.YYYY HH:mm';

export class ModelingExerciseCreationPage extends AbstractExerciseCreationPage {
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
        const responsePromise = this.page.waitForResponse((response) => response.url().includes(`${MODELING_EXERCISE_BASE}/import?sourceExerciseId=`));
        await this.page.click('#save-entity');
        return await responsePromise;
    }

    async includeInOverallScore(selection: string = 'No') {
        await this.page.locator('#modeling-includeInScore-picker').locator('.btn', { hasText: selection }).click({ force: true });
    }

    async setReleaseDate(date: Dayjs) {
        await this.setTimelineDate('Release Date', date);
    }

    async setDueDate(date: Dayjs) {
        await this.setTimelineDate('Due Date', date);
    }

    async setAssessmentDueDate(date: Dayjs) {
        await this.setTimelineDate('Assessment Due Date', date);
    }

    private async setTimelineDate(label: string, date: Dayjs) {
        const dateInput = this.page.getByLabel(label, { exact: true });
        await dateInput.waitFor({ state: 'visible' });
        await dateInput.fill(date.format(TIMELINE_DATE_FORMAT));
        await dateInput.press('Tab');
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
