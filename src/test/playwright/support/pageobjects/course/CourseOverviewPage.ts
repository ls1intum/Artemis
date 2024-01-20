import { BASE_API } from '../../constants';
import { Locator, Page } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the course overview page (/courses/*).
 */
export class CourseOverviewPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async search(term: string) {
        await this.page.locator('#exercise-search-input').fill(term);
        await this.page.locator('#exercise-search-button').click();
    }

    async startExercise(exerciseId: number) {
        await this.page
            .locator('#start-exercise-' + exerciseId)
            // .waitFor({ state: 'visible' })
            .click();
    }

    async openRunningExercise(exerciseId: number) {
        await this.page.locator('#open-exercise-' + exerciseId).click();
    }

    getExercise(exerciseID: number): Locator {
        return this.page.locator(`#exercise-card-${exerciseID}`);
    }

    async openRunningProgrammingExercise(exerciseID: number) {
        const responsePromise = this.page.waitForRequest(BASE_API + 'programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks');
        await this.openRunningExercise(exerciseID);
        await responsePromise;
    }

    async openExamsTab() {
        await this.page.locator('#exam-tab').click();
    }

    async openExam(examId: number): Promise<void> {
        await this.page.locator('#exam-' + examId).click();
    }
}
