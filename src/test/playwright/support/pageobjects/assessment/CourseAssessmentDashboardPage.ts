import { Page } from '@playwright/test';
import { COURSE_BASE } from '../../constants';
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
        await this.page.locator('#show-complaint').click();
    }

    async clickExerciseDashboardButton(exerciseIndex: number = 0) {
        // Sometimes the page does not load properly, so we reload it if the button is not found
        const openExerciseDashboardLocator = this.page.locator('#open-exercise-dashboard').nth(exerciseIndex);
        await Commands.reloadUntilFound(this.page, openExerciseDashboardLocator);
        await openExerciseDashboardLocator.click();
    }

    async clickEvaluateQuizzes() {
        const evaluateQuizzesPromise = this.page.waitForResponse(`${COURSE_BASE}/*/exams/*/student-exams/evaluate-quiz-exercises`);
        await this.page.locator('#evaluateQuizExercisesButton').click();
        return await evaluateQuizzesPromise;
    }
}
