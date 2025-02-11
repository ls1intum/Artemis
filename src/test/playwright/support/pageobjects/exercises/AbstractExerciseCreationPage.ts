import { Locator, Page } from '@playwright/test';
import { Dayjs } from 'dayjs';
import { enterDate } from '../../utils';

export class AbstractExerciseCreationPage {
    protected readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async setTitle(title: string) {
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

    async clearText(textEditor: Locator) {
        await textEditor.click();
        await textEditor.press('Control+a');
        await textEditor.press('Delete');
    }

    async waitForFormToLoad() {
        await this.page.locator('[name*="editForm"]').waitFor({ state: 'visible', timeout: 10000 });
    }
}
