import { Page, expect } from '@playwright/test';
import { Dayjs } from 'dayjs';

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
     * Opens the student exams page.
     */
    async openStudentExams(examId: number) {
        await this.page.locator(`#student-exams-${examId}`).click();
    }

    /**
     * Opens the exam assessment dashboard
     * @param courseID the id of the course
     * @param examID the id of the exam
     * @param timeout timeout of waiting for assessment dashboard button
     */
    async openAssessmentDashboard(courseID: number, examID: number, timeout = 60000) {
        await this.page.goto(`/course-management/${courseID}/exams`);
        const assessmentButton = this.page.locator(`#exercises-button-${examID}`);
        await assessmentButton.waitFor({ state: 'attached', timeout: timeout });
        await assessmentButton.click();
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
        await this.page.goto(`/course-management/${courseID}/exams/${examID}/student-exams`);
        await this.page.locator('#student-exam').waitFor({ state: 'visible' });
        await expect(this.page.locator('#student-exam .datatable-body-row', { hasText: username }).locator('.submitted')).toHaveText('Yes');
    }

    async checkQuizSubmission(courseID: number, examID: number, username: string, score: string) {
        await this.page.goto(`/course-management/${courseID}/exams/${examID}/student-exams`);
        await this.page.locator('#student-exam .datatable-body-row', { hasText: username }).locator('.view-submission').click();
        await this.page.locator('.summery').click();
        await expect(this.page.locator('#exercise-result-score')).toHaveText(score, { useInnerText: true });
    }

    async openAnnouncementDialog() {
        await this.page.locator('#announcement-create-button').click();
    }

    async typeAnnouncementMessage(message: string) {
        await this.page.locator('.monaco-editor textarea').fill(message);
    }

    async verifyAnnouncementContent(announcementTime: Dayjs, message: string, authorUsername: string) {
        const announcementDialog = this.page.locator('.modal-content');
        const timeFormat = 'MMM D, YYYY HH:mm';
        const announcementTimeFormatted = announcementTime.format(timeFormat);
        const announcementTimeAfterMinute = announcementTime.add(1, 'minute').format(timeFormat);
        await expect(announcementDialog.locator('.date').getByText(new RegExp(`(${announcementTimeFormatted}|${announcementTimeAfterMinute})`))).toBeVisible();
        await expect(announcementDialog.locator('.content').getByText(message)).toBeVisible();
        await expect(announcementDialog.locator('.author').getByText(authorUsername)).toBeVisible();
    }

    async sendAnnouncement() {
        await this.page.locator('button', { hasText: 'Send Announcement' }).click();
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
