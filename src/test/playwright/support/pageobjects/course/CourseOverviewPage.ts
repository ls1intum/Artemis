import { BASE_API } from '../../constants';
import { Locator, Page } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the Course Overview page (/courses/*).
 */
export class CourseOverviewPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Searches for exercises using the provided search term.
     * @param term The search term to use.
     */
    async search(term: string) {
        await this.page.locator('#exercise-search-input').fill(term);
        await this.page.locator('#exercise-search-button').click();
    }

    /**
     * Initiates the start of an exercise given its ID.
     * @param exerciseId The ID of the exercise to start.
     */
    async startExercise(exerciseId: number) {
        await this.page.locator('#start-exercise-' + exerciseId).click();
    }

    /**
     * Opens an already running exercise given its ID.
     * @param exerciseId The ID of the exercise to open.
     */
    async openRunningExercise(exerciseId: number) {
        await this.page.locator('#open-exercise-' + exerciseId).click();
    }

    /**
     * Retrieves the Locator for an exercise card by its ID.
     * @param exerciseID The ID of the exercise.
     * @returns The Locator for the exercise card.
     */
    getExercise(exerciseID: number): Locator {
        return this.page.locator(`#exercise-card-${exerciseID}`);
    }

    /**
     * Opens a running programming exercise and waits for the necessary request to complete.
     * @param exerciseID The ID of the programming exercise to open.
     */
    async openRunningProgrammingExercise(exerciseID: number) {
        const responsePromise = this.page.waitForRequest(`${BASE_API}/programming-exercise-participations/*/student-participation-with-latest-result-and-feedbacks`);
        await this.openRunningExercise(exerciseID);
        await responsePromise;
    }

    /**
     * Navigates to the Exams tab on the course overview page.
     */
    async openExamsTab() {
        await this.page.locator('#exam-tab').click();
    }

    /**
     * Opens an exam given its ID.
     * @param examId The ID of the exam to open.
     */
    async openExam(examId: number): Promise<void> {
        await this.page.locator(`#exam-${examId} .clickable`).click();
    }
}
