import { Page } from '@playwright/test';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { UserCredentials } from '../../users';
import { Commands } from '../../commands';
import { ExamStartEndPage } from './ExamStartEndPage';

export class ExamTestRunPage {
    private readonly page: Page;
    private readonly examStartEnd: ExamStartEndPage;

    constructor(page: Page, examStartEnd: ExamStartEndPage) {
        this.page = page;
        this.examStartEnd = examStartEnd;
    }

    async confirmTestRun() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/test-run`);
        await this.page.locator('.modal-dialog').locator('#createTestRunButton').click();
        return await responsePromise;
    }

    async startParticipation(user: UserCredentials, course: Course, exam: Exam, testRunId: number) {
        await Commands.login(this.page, user);
        await this.openTestRunPage(course, exam);
        await this.startTestRun(testRunId);
        await this.page.waitForURL(`/course-management/${course.id}/exams/${exam.id}/test-runs/${testRunId}/conduction`);
        await this.examStartEnd.startExam();
    }

    async createTestRun() {
        await this.page.locator('#createTestRunButton').click();
    }

    async saveTestRun() {
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/student-exams/*/working-time`);
        await this.page.locator('#save').click();
        return await responsePromise;
    }

    getTestRun(testRunId: number) {
        return this.page.locator(`#testrun-${testRunId}`);
    }

    getTestRunRibbon() {
        return this.page.locator('#testRunRibbon');
    }

    async openTestRunPage(course: Course, exam: Exam) {
        await this.page.goto(`/course-management/${course.id}/exams/${exam.id}/test-runs`);
        await this.page.waitForURL(`/course-management/${course.id}/exams/${exam.id}/test-runs`);
    }

    async setWorkingTimeHours(hours: number) {
        const hoursField = this.page.locator('#workingTimeHours');
        await hoursField.clear();
        await hoursField.fill(hours.toString());
    }

    async setWorkingTimeMinutes(minutes: number) {
        const minutesField = this.page.locator('#workingTimeMinutes');
        await minutesField.clear();
        await minutesField.fill(minutes.toString());
    }

    async setWorkingTimeSeconds(seconds: number) {
        const secondsField = this.page.locator('#workingTimeSeconds');
        await secondsField.clear();
        await secondsField.fill(seconds.toString());
    }

    getWorkingTime(testRunId: number) {
        return this.getTestRun(testRunId).locator('.working-time');
    }

    getStarted(testRunId: number) {
        return this.getTestRun(testRunId).locator('.started');
    }

    getSubmitted(testRunId: number) {
        return this.getTestRun(testRunId).locator('.submitted');
    }

    getTestRunIdElement(testRunId: number) {
        return this.getTestRun(testRunId).locator('.testrun-id');
    }

    async changeWorkingTime(testRunId: number) {
        await this.page.locator(`#testrun-${testRunId}`).locator('.manage-worktime').click();
        // Wait for navigation to the detail page and for the working time form to be ready
        await this.page.waitForURL(`**/test-runs/${testRunId}`);
        await this.page.locator('#workingTimeHours').waitFor({ state: 'visible', timeout: 30000 });
    }

    async startTestRun(testRunId: number) {
        const startButton = this.page.locator(`#testrun-${testRunId}`).locator('.start-testrun');
        await startButton.waitFor({ state: 'visible', timeout: 10000 });
        await startButton.click();
    }

    async deleteTestRun(testRunId: number) {
        const deleteButton = this.page.locator(`#testrun-${testRunId}`).locator('.delete-testrun');
        await deleteButton.waitFor({ state: 'visible', timeout: 10000 });
        await deleteButton.click();
        const confirmInput = this.page.locator('#confirm-entity-name');
        await confirmInput.waitFor({ state: 'visible', timeout: 10000 });
        await confirmInput.fill('Test Run');
        const responsePromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/test-run/*`);
        await this.page.getByTestId('delete-dialog-confirm-button').click();
        await responsePromise;
    }
}
