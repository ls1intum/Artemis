import { Locator, Page } from '@playwright/test';
import { Dayjs } from 'dayjs';
import { enterDate, setMonacoEditorContentByLocator } from '../../utils';

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
        // Use the setMonacoEditorContentByLocator utility to clear the content
        await setMonacoEditorContentByLocator(this.page, textEditor, '');
    }

    async typeTextInMonaco(textEditor: Locator, text: string) {
        // Use the setMonacoEditorContentByLocator utility to set the content
        await setMonacoEditorContentByLocator(this.page, textEditor, text);
    }

    async waitForFormToLoad() {
        await this.page.locator('[name*="editForm"]').waitFor({ state: 'visible', timeout: 10000 });
    }
}
