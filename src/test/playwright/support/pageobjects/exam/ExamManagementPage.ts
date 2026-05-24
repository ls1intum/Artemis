import { Page, expect } from '@playwright/test';
import { Dayjs } from 'dayjs';
import { EXAM_DASHBOARD_TIMEOUT } from '../../timeouts';
import { setMonacoEditorContentByLocator } from '../../utils';

/**
 * A class which encapsulates UI selectors and actions for the exam management page.
 */
export class ExamManagementPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Searches for an exam with the provided title.
     * @param examTitle the title of the exam.
     * @returns the row element of the found exam
     */
    async getExamRowRoot(examTitle: string) {
        return this.page.locator('tr', { has: this.getExamSelector(examTitle) });
    }

    /** Opens the exam with this exam id. */
    async openExam(examId: number) {
        await this.page.locator(`#exam-${examId}-title`).click();
    }

    /**
     * Clicks the create new exam button.
     */
    async createNewExam() {
        await this.page.locator('#create-exam').click();
    }

    /**
     * Returns the title element of the exam row.
     * @param examTitle the title to search for
     * @returns the element
     */
    getExamSelector(examTitle: string) {
        return this.page.locator('#exams-table').getByText(examTitle);
    }

    /**
     * Opens the exercise groups page.
     */
    async openExerciseGroups(examId: number) {
        await this.page.locator(`#exercises-button-${examId}-groups`).click();
    }

    /**
     * Opens the student registration page.
     */
    async openStudentRegistration(examId: number) {
        await this.page.locator(`#student-button-${examId}`).click();
    }

    /**
     * Opens the exam assessment dashboard.
     *
     * Under heavy multi-node CI load the goto occasionally lands on `/courses` instead of
     * the assessment-dashboard route — the assessment dashboard's lazy chunk fails to load
     * and Angular's auth/router fall-back redirects to /courses. When that happens the
     * downstream `clickExerciseDashboardButton` reloads /courses forever and ultimately
     * times out. Verify the URL after navigation and re-issue the goto if it drifted.
     */
    async openAssessmentDashboard(courseID: number, examID: number, timeout = EXAM_DASHBOARD_TIMEOUT) {
        const expectedUrl = `/course-management/${courseID}/exams/${examID}/assessment-dashboard`;
        const expectedPattern = new RegExp(`/course-management/${courseID}/exams/${examID}/assessment-dashboard(?:[/?#].*)?$`);
        // Try up to twice — under multi-node CI load the first goto occasionally lands on
        // `/courses` (assessment-dashboard chunk fails to load and Angular's fall-back redirect
        // kicks in). Detecting the URL drift early lets us retry the navigation cleanly rather
        // than letting downstream helpers reload the wrong page until they time out.
        for (let attempt = 0; attempt < 2; attempt++) {
            await this.page.goto(expectedUrl);
            await this.page.waitForLoadState('domcontentloaded');
            if (expectedPattern.test(this.page.url())) {
                return;
            }
        }
    }

    /**
     * Opens the test run page.
     */
    async openTestRun() {
        await this.page.locator(`#testrun-button`).click();
    }

    /**
     * Opens the exam grading system page.
     */
    async openGradingKey() {
        await this.page.locator('a', { hasText: 'Grading Key' }).click();
    }

    /**
     * Opens the exam scores page.
     */
    async openScoresPage() {
        await this.page.locator('#scores-button').click();
    }

    async verifySubmitted(courseID: number, examID: number, username: string) {
        await this.page.goto(`/course-management/${courseID}/exams/${examID}/students`);
        const row = this.page.locator('tbody tr', { hasText: username }).first();
        const visibleWithin = async (timeout: number): Promise<boolean> =>
            row
                .waitFor({ state: 'visible', timeout })
                .then(() => true)
                .catch(() => false);
        // The exam-students endpoint joins across submissions; under heavy multi-node CI load
        // the row for a just-handed-in student can take >30s to surface in the first response
        // (the participation-state propagation lags behind the submit POST). Try up to four
        // reload attempts with progressively shorter per-attempt waits — totalling ~90s — so
        // the test does not give up on a slow but eventually-correct backend state.
        let visible = await visibleWithin(30_000);
        for (let attempt = 0; !visible && attempt < 3; attempt++) {
            await this.page.reload();
            await this.page.waitForLoadState('load');
            visible = await visibleWithin(20_000);
        }
        if (!visible) {
            // One last wait so the assertion error surfaces with the locator's call log.
            await row.waitFor({ state: 'visible', timeout: 10_000 });
        }
        await expect(row).toContainText('Submitted');
    }

    async checkQuizSubmission(courseID: number, examID: number, username: string, score: string) {
        await this.page.goto(`/course-management/${courseID}/exams/${examID}/students`);
        await this.page.waitForLoadState('domcontentloaded');
        const row = this.page.locator('tbody tr', { hasText: username }).first();
        await row.waitFor({ state: 'visible' });
        await row.getByRole('link', { name: 'View exam' }).click();
        await this.page.locator('.summery').click();
        await expect(this.page.locator('#exercise-result-score')).toHaveText(score, { useInnerText: true });
    }

    async openAnnouncementDialog() {
        await this.page.locator('#announcement-create-button').click();
    }

    async typeAnnouncementMessage(message: string) {
        // Match either the legacy NgbModal (.modal-content) or the migrated PrimeNG dialog (.p-dialog-content).
        const modalContent = this.page.locator('.p-dialog-content, .modal-content').first();
        await setMonacoEditorContentByLocator(this.page, modalContent, message);
    }

    async verifyAnnouncementContent(announcementTime: Dayjs, message: string, authorUsername: string) {
        const announcementDialog = this.page.locator('.p-dialog-content, .modal-content').first();
        const timeFormat = 'MMM D, YYYY HH:mm';
        const announcementTimeFormatted = announcementTime.format(timeFormat);
        const announcementTimeAfterMinute = announcementTime.add(1, 'minute').format(timeFormat);
        await expect(announcementDialog.locator('.date').getByText(new RegExp(`(${announcementTimeFormatted}|${announcementTimeAfterMinute})`))).toBeVisible();
        await expect(announcementDialog.locator('.content').getByText(message)).toBeVisible();
    }

    async sendAnnouncement() {
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes('/announcements') && resp.request().method() === 'POST' && resp.ok());
        await this.page.locator('button', { hasText: 'Send Announcement' }).click();
        await responsePromise;
    }

    async openEditWorkingTimeDialog() {
        await this.page.locator('#edit-working-time-button').click();
    }

    async changeExamWorkingTime(newWorkingTime: any) {
        if (newWorkingTime.hours) {
            await this.page.locator('#workingTimeHours').fill(newWorkingTime.hours.toString());
        }
        if (newWorkingTime.minutes) {
            await this.page.locator('#workingTimeMinutes').fill(newWorkingTime.minutes.toString());
        }
        if (newWorkingTime.seconds) {
            await this.page.locator('#workingTimeSeconds').fill(newWorkingTime.seconds.toString());
        }
    }

    async verifyExamWorkingTimeChange(previousWorkingTime: any, newWorkingTime: any) {
        await expect(this.page.locator('[data-testid="old-time"]').getByText(previousWorkingTime)).toBeVisible();
        await expect(this.page.locator('[data-testid="new-time"]').getByText(newWorkingTime)).toBeVisible();
    }

    async confirmWorkingTimeChange(examTitle: string) {
        await this.page.locator('#confirm-entity-name').fill(examTitle);
        await this.page.locator('#confirm').click();
    }

    async clickEdit() {
        await this.page.locator('#editButton').click();
    }

    /*
     * Helper methods to get information about course
     */

    getExamTitle() {
        return this.page.locator('#detail-value-artemisApp\\.exam\\.title');
    }

    getExamVisibleDate() {
        return this.page.locator('#detail-value-artemisApp\\.examManagement\\.visibleDate');
    }

    getExamStartDate() {
        return this.page.locator('#detail-value-artemisApp\\.exam\\.startDate');
    }

    getExamEndDate() {
        return this.page.locator('#detail-value-artemisApp\\.exam\\.endDate');
    }

    getExamNumberOfExercises() {
        return this.page.locator('#detail-value-artemisApp\\.examManagement\\.numberOfExercisesInExam');
    }

    getExamMaxPoints() {
        return this.page.locator('#detail-value-artemisApp\\.examManagement\\.maxPoints\\.title');
    }

    getExamStartText() {
        return this.page.locator('#detail-value-artemisApp\\.examManagement\\.startText');
    }

    getExamEndText() {
        return this.page.locator('#detail-value-artemisApp\\.examManagement\\.endText');
    }

    getExamConfirmationStartText() {
        return this.page.locator('#detail-value-artemisApp\\.examManagement\\.confirmationStartText');
    }

    getExamConfirmationEndText() {
        return this.page.locator('#detail-value-artemisApp\\.examManagement\\.confirmationEndText');
    }

    getExamWorkingTime() {
        return this.page.locator('#detail-value-artemisApp\\.exam\\.workingTime');
    }
}
