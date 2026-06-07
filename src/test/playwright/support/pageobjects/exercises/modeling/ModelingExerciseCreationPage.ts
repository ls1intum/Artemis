import { MODELING_EXERCISE_BASE } from '../../../constants';
import { AbstractExerciseCreationPage } from '../AbstractExerciseCreationPage';

export class ModelingExerciseCreationPage extends AbstractExerciseCreationPage {
    async addCategories(categories: string[]) {
        for (const category of categories) {
            const categoriesField = this.page.locator('#field_categories');
            await categoriesField.fill(category);
            await categoriesField.press('Enter');
        }
    }

    async setPoints(points: number) {
        // The points field is a PrimeNG p-inputnumber (masked input): a plain fill() does not commit its
        // value to ngModel, so we type and blur to drive its keystroke handling.
        const pointsField = this.page.locator('#field_points');
        await pointsField.click();
        await pointsField.press('ControlOrMeta+a');
        await pointsField.press('Delete');
        await pointsField.pressSequentially(points.toString());
        await pointsField.blur();
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
        // The picker is now PrimeNG p-buttons (was a Bootstrap .btn-group).
        await this.page.locator('#modeling-includeInScore-picker').locator('button', { hasText: selection }).click({ force: true });
    }

    async pickDifficulty(options: { hard?: boolean; medium?: boolean; easy?: boolean }) {
        const difficultyBar = this.page.locator('#modeling-difficulty-picker');
        if (options.hard) {
            await difficultyBar.locator('button', { hasText: 'Hard' }).click();
        } else if (options.medium) {
            await difficultyBar.locator('button', { hasText: 'Medium' }).click();
        } else if (options.easy) {
            await difficultyBar.locator('button', { hasText: 'Easy' }).click();
        } else {
            await difficultyBar.locator('button', { hasText: 'No Level' }).click();
        }
    }
}
