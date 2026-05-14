import { Page, expect } from '@playwright/test';
import { getExercise } from '../../../utils';
import { Fixtures } from '../../../../fixtures/fixtures';

/**
 * A class which encapsulates UI selectors and actions for the text editor page.
 */
export class TextEditorPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async typeSubmission(exerciseID: number, submission: string) {
        await getExercise(this.page, exerciseID).locator('#text-editor').fill(submission);
    }

    async clearSubmission(exerciseID: number) {
        await getExercise(this.page, exerciseID).locator('#text-editor').clear();
    }

    async checkCurrentContent(expectedContent: string) {
        const text = await Fixtures.get(expectedContent);
        await expect(this.page.locator('#text-editor')).toHaveValue(text!);
    }

    async saveAndContinue() {
        const responsePromise = this.page.waitForResponse(`api/text/exercises/*/text-submissions`);
        await this.page.click('#save');
        return await responsePromise;
    }

    async shouldShowNumberOfWords(numberOfWords: number): Promise<void> {
        const wordCountElement = this.page.locator('#word-count');
        await expect(wordCountElement).toContainText(numberOfWords.toString());
        await expect(wordCountElement).toBeVisible();
    }

    async shouldShowNumberOfCharacters(numberOfCharacters: number): Promise<void> {
        const characterCountElement = this.page.locator('#character-count');
        await expect(characterCountElement).toContainText(numberOfCharacters.toString());
        await expect(characterCountElement).toBeVisible();
    }
}
