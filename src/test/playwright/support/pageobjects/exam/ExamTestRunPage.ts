import { Page } from '@playwright/test';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { UserCredentials } from '../../users';
import { Commands } from '../../commands';
import { ExamStartEndPage } from './ExamStartEndPage';
import { COURSE_BASE } from '../../constants';

export class ExamTestRunPage {
    private readonly page: Page;
    private readonly examStartEnd: ExamStartEndPage;

    constructor(page: Page, examStartEnd: ExamStartEndPage) {
        this.page = page;
        this.examStartEnd = examStartEnd;
    }

    async confirmTestRun() {
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams/*/test-run`);
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
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams/*/student-exams/*/working-time`);
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
    }

    async startTestRun(testRunId: number) {
        await this.page.locator(`#testrun-${testRunId}`).locator('.start-testrun').click();
    }

    async deleteTestRun(testRunId: number) {
        await this.page.locator(`#testrun-${testRunId}`).locator('.delete-testrun').click();
        await this.page.locator('#confirm-entity-name').fill('Test Run');
        const responsePromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams/*/test-run/*`);
        await this.page.locator('#delete').click();
        await responsePromise;
    }
}
