import { Page, expect } from '@playwright/test';
import { Commands } from '../../commands';

export class CourseAssessmentDashboardPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async openComplaints() {
        await this.page.locator('#open-complaints').click();
    }

    async showTheComplaint() {
        await this.page.locator('#show-complaint').first().click();
    }

    async clickExerciseDashboardButton(exerciseIndex: number = 0, timeout?: number) {
        // Sometimes the page does not load properly, so we reload it if the button is not found
        const openExerciseDashboardLocator = this.page.locator('#open-exercise-dashboard').nth(exerciseIndex);
        await Commands.reloadUntilFound(this.page, openExerciseDashboardLocator, 5000, timeout);
        await openExerciseDashboardLocator.click();
    }

    async clickEvaluateQuizzes() {
        const button = this.page.locator('#evaluateQuizExercisesButton');
        await expect(button).toBeEnabled({ timeout: 30000 });
        const evaluateQuizzesPromise = this.page.waitForResponse(`api/exam/courses/*/exams/*/student-exams/evaluate-quiz-exercises`);
        await button.click();
        return await evaluateQuizzesPromise;
    }
}
