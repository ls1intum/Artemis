import { Page } from '@playwright/test';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the text exercise example submission creation page.
 */
export class TextExerciseExampleSubmissionCreationPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getInstructionsRootElement() {
        return this.page.locator('[data-testid="instructions"]');
    }

    async typeExampleSubmission(example: string) {
        await this.page.locator('[data-testid="example-text-submission-input"]').fill(example);
    }

    async clickCreateNewExampleSubmission() {
        const responsePromise = this.page.waitForResponse(`api/assessment/exercises/*/example-submissions`);
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
