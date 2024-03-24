import { Page } from 'playwright';
import { expect } from '@playwright/test';
import { EXERCISE_BASE } from '../../../constants';

/**
 * A class which encapsulates UI selectors and actions for the text exercise example submission creation page.
 */
export class TextExerciseExampleSubmissionCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getInstructionsRootElement() {
        return this.page.locator('#instructions');
    }

    async typeExampleSubmission(example: string) {
        await this.page.locator('#example-text-submission-input').fill(example);
    }

    async clickCreateNewExampleSubmission() {
        const responsePromise = this.page.waitForResponse(`${EXERCISE_BASE}/*/example-submissions`);
        await this.page.locator('#create-example-submission').click();
        return responsePromise;
    }

    async showsExerciseTitle(exerciseTitle: string) {
        await expect(this.getInstructionsRootElement()).toContainText(exerciseTitle);
    }

    async showsProblemStatement(problemStatement: string) {
        await expect(this.getInstructionsRootElement()).toContainText(problemStatement);
    }

    async showsExampleSolution(exampleSolution: string) {
        await expect(this.getInstructionsRootElement()).toContainText(exampleSolution);
    }
}
