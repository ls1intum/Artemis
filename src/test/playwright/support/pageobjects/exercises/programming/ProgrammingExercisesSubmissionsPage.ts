import { expect, Locator, Page } from '@playwright/test';

export class ProgrammingExerciseSubmissionsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getSubmissionWithText(text: string) {
        return this.page
            .getByRole('table')
            .getByRole('row')
            .filter({ hasText: `${text}` });
    }

    async checkInstructorSubmission() {
        let submissionRow = this.getSubmissionWithText('INSTRUCTOR');
        await this.checkSubmissionVisible(submissionRow);
    }

    async checkStudentSubmission() {
        let submissionRow = this.getSubmissionWithText('MANUAL');
        await this.checkSubmissionVisible(submissionRow);
    }

    private async checkSubmissionVisible(submissionRow: Locator) {
        await submissionRow.waitFor({ state: 'visible' });
        expect(submissionRow).not.toBeUndefined();
        expect(submissionRow.locator('jhi-result')).not.toBeUndefined();
    }
}
