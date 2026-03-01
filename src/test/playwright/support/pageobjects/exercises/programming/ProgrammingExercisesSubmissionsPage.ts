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

    async checkInstructorSubmission() {
        const submissionRow = this.getSubmissionWithText('INSTRUCTOR');
        await this.checkSubmissionVisible(submissionRow);
    }

    async checkStudentSubmission() {
        const submissionRow = this.getSubmissionWithText('MANUAL');
        await this.checkSubmissionVisible(submissionRow);
    }

    private async checkSubmissionVisible(submissionRow: Locator) {
        // Submissions appear after the build agent processes the git push,
        // so use the standard build result timeout instead of an arbitrary value.
        await Commands.reloadUntilFound(this.page, submissionRow, POLLING_INTERVAL, BUILD_RESULT_TIMEOUT);
        expect(submissionRow.locator('jhi-result')).not.toBeUndefined();
    }
}
