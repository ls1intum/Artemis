import { Page, expect } from '@playwright/test';
import { Fixtures } from '../../../fixtures/fixtures';
import { Commands } from '../../commands';
import { getExercise } from '../../utils';
import { Dayjs } from 'dayjs';

export class ExamParticipationActions {
    protected readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async selectExerciseOnOverview(index: number) {
        await this.page.locator(`.exercise-table tr:nth-child(${index}) a`).click();
    }

    async clickSaveAndContinue() {
        await this.page.click('#save');
    }

    async checkExerciseTitle(exerciseID: number, title: string) {
        const exercise = getExercise(this.page, exerciseID);
        await expect(exercise.locator('.exercise-title')).toContainText(title);
    }

    async checkExamTitle(title: string) {
        await expect(this.page.locator('#exam-title')).toContainText(title);
    }

    async getResultScore(exerciseID?: number) {
        const parentComponent = exerciseID ? getExercise(this.page, exerciseID) : this.page;
        const resultScoreLocator = parentComponent.getByTestId('achieved-percentage');
        await Commands.reloadUntilFound(this.page, resultScoreLocator, 4000, 60000);
        return resultScoreLocator;
    }

    async checkResultScore(scoreText: string, exerciseID?: number) {
        const scoreElement = await this.getResultScore(exerciseID);
        await expect(scoreElement.getByText(new RegExp(scoreText))).toBeVisible();
    }

    async checkExamFinishedTitle(title: string) {
        await expect(this.page.locator('#exam-finished-title')).toContainText(title, { timeout: 40000 });
    }

    async checkExamFullnameInputExists() {
        await expect(this.page.locator('#fullname')).toBeVisible({ timeout: 30000 });
    }

    async checkYourFullname(name: string) {
        await expect(this.page.locator('#your-name')).toContainText(name, { timeout: 30000 });
    }

    async checkExamTimeLeft(timeLeft: string) {
        await expect(this.page.locator('#displayTime').getByText(timeLeft)).toBeVisible();
    }

    async checkExamTimeChangeDialog(previousWorkingTime: string, newWorkingTime: string, announcementTime: Dayjs, authorUsername: string, message: string) {
        const timeChangeDialog = this.page.locator('.modal-content');
        await expect(timeChangeDialog.getByTestId('old-time').getByText(previousWorkingTime)).toBeVisible();
        await expect(timeChangeDialog.getByTestId('new-time').getByText(newWorkingTime)).toBeVisible();
        const timeFormat = 'MMM D, YYYY HH:mm';
        const announcementTimeFormatted = announcementTime.format(timeFormat);
        const announcementTimeAfterMinute = announcementTime.add(1, 'minute').format(timeFormat);
        await expect(timeChangeDialog.locator('.date').getByText(new RegExp(`(${announcementTimeFormatted}|${announcementTimeAfterMinute})`))).toBeVisible();
        await expect(timeChangeDialog.locator('.content').getByText(message)).toBeVisible();
        await expect(timeChangeDialog.locator('.author').getByText(authorUsername)).toBeVisible();
    }

    async closeDialog() {
        await this.page.locator('button', { hasText: 'Acknowledge' }).nth(0).click({ force: true, timeout: 5000 });
    }

    async verifyExerciseTitleOnFinalPage(exerciseID: number, exerciseTitle: string): Promise<void> {
        const exercise = getExercise(this.page, exerciseID);
        await expect(exercise.locator(`#exercise-group-title-${exerciseID}`).getByText(exerciseTitle)).toBeVisible();
    }

    async verifyTextExerciseOnFinalPage(exerciseID: number, textFixture: string): Promise<void> {
        const exercise = getExercise(this.page, exerciseID);
        const submissionText = await Fixtures.get(textFixture);
        await expect(exercise.locator('#text-editor')).toHaveValue(submissionText!);
    }

    async verifyGradingKeyOnFinalPage(gradeName: string) {
        const gradingKeyCard = this.page.locator('jhi-collapsible-card').filter({ hasText: 'Grading Key Grade Interval' });
        await gradingKeyCard.locator('button.rotate-icon').click();
        await expect(gradingKeyCard.locator('tr.highlighted').locator('td', { hasText: gradeName })).toBeVisible();
    }
}
