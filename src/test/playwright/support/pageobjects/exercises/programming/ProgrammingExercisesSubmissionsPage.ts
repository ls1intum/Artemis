import { expect, Locator, Page } from '@playwright/test';
import { Commands } from '../../../commands';
import { BUILD_RESULT_TIMEOUT, POLLING_INTERVAL } from '../../../timeouts';

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

    async checkInstructorSubmission(timeout?: number) {
        const submissionRow = this.getSubmissionWithText('INSTRUCTOR');
        await this.checkSubmissionVisible(submissionRow, timeout);
    }

    async checkStudentSubmission(timeout?: number) {
        const submissionRow = this.getSubmissionWithText('MANUAL');
        await this.checkSubmissionVisible(submissionRow, timeout);
    }

    private async checkSubmissionVisible(submissionRow: Locator, timeout?: number) {
        // Submissions appear after the build agent processes the git push,
        // so use the standard build result timeout instead of an arbitrary value.
        await Commands.reloadUntilFound(this.page, submissionRow, POLLING_INTERVAL, timeout ?? BUILD_RESULT_TIMEOUT);
        expect(submissionRow.locator('jhi-result')).not.toBeUndefined();
    }
}
