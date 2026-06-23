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

    /**
     * Opens a complaint from the course-wide complaints list.
     *
     * The list intermingles complaints from every assessment test that shares the seed course, so clicking the
     * first #show-complaint races those other tests and can open an unrelated (possibly already-handled)
     * complaint whose response editor never enables for this flow. When the exercise title is known, open that
     * exercise's own complaint row instead of the first one to keep the selection deterministic.
     * @param exerciseTitle - The title of the exercise whose complaint should be opened (optional).
     */
    async showTheComplaint(exerciseTitle?: string) {
        const showComplaintButton = exerciseTitle ? this.page.locator('tr', { hasText: exerciseTitle }).locator('#show-complaint') : this.page.locator('#show-complaint').first();
        await showComplaintButton.click();
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
