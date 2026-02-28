import { expect, Locator, Page } from '@playwright/test';
import { Commands } from '../../../commands';

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
        const submissionRow = this.getSubmissionWithText('INSTRUCTOR');
        await this.checkSubmissionVisible(submissionRow);
    }

    async checkStudentSubmission() {
        const submissionRow = this.getSubmissionWithText('MANUAL');
        await this.checkSubmissionVisible(submissionRow);
    }

    private async checkSubmissionVisible(submissionRow: Locator) {
        await Commands.reloadUntilFound(this.page, submissionRow, 3000, 60000);
        expect(submissionRow.locator('jhi-result')).not.toBeUndefined();
    }
}
