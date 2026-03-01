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
        // Instructor submissions require a build to complete after the git push.
        // On cold Docker environments, builds can be queued behind other builds,
        // so use a generous timeout (150s) instead of the default BUILD_RESULT_TIMEOUT (90s).
        await this.checkSubmissionVisible(submissionRow, 150000);
    }

    async checkStudentSubmission() {
        const submissionRow = this.getSubmissionWithText('MANUAL');
        await this.checkSubmissionVisible(submissionRow);
    }

    private async checkSubmissionVisible(submissionRow: Locator, timeout: number = BUILD_RESULT_TIMEOUT) {
        // Submissions appear after the build agent processes the git push,
        // so use the standard build result timeout instead of an arbitrary value.
        await Commands.reloadUntilFound(this.page, submissionRow, POLLING_INTERVAL, timeout);
        expect(submissionRow.locator('jhi-result')).not.toBeUndefined();
    }
}
