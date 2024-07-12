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
        const searchInput = this.page.locator('input[formcontrolname="searchFilter"]');
        await searchInput.pressSequentially(term, { delay: 20 });
    }

    /**
     * Initiates the start of an exercise given its ID.
     * @param exerciseId The ID of the exercise to start.
     */
    async startExercise(exerciseId: number) {
        await this.getStartExerciseButton(exerciseId).click();
    }

    /**
     * Opens an already running exercise given its ID.
     * @param exerciseId The ID of the exercise to open.
     */
    async openRunningExercise(exerciseId: number) {
        await this.getOpenRunningExerciseButton(exerciseId).click();
    }

    /**
     * Initiates the practice of an exercise.
     */
    async practiceExercise() {
        await this.page.locator('button', { hasText: 'Practice' }).click();
    }

    /**
     * Retrieves the Locator for an exercise card by its ID.
     * @param exerciseName title of the exercise.
     * @returns The Locator for the exercise card.
     */
    getExercise(exerciseName: string): Locator {
        return this.page.locator('#test-sidebar-card-medium').getByText(exerciseName);
    }

    /**
     * Retrieves the Locator for all exercises.
     * @returns The Locator for all exercises.
     */
    getExercises(): Locator {
        return this.page.locator('#test-sidebar-card-medium');
    }

    /**
     * Retrieves the Locator for the button opening running exercise with the given ID.
     * @param exerciseId The ID of the exercise.
     * @returns The Locator for the button opening running exercise.
     */
    getOpenRunningExerciseButton(exerciseId: number) {
        return this.page.locator(`#open-exercise-${exerciseId}`);
    }
    /**
     * Retrieves the Locator for the start exercise button by its ID.
     * @param exerciseId The ID of the exercise.
     * @returns The Locator for the start exercise button.
     */
    getStartExerciseButton(exerciseId: number) {
        return this.page.locator(`#start-exercise-${exerciseId}`);
    }

    /**
     * Opens an exercise given its name.
     * @param exerciseName The title of the exercise to open.
     */
    async openExercise(exerciseName: string) {
        await this.page.locator('jhi-course-exercise-details').waitFor({ state: 'visible' });
        await this.getExercise(exerciseName).click();
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
     * Opens an exam given its title.
     */
    async openExam(examTitle: string): Promise<void> {
        await this.page.locator('span').filter({ hasText: examTitle }).click();
    }

    /**
     * Opens the team info for the exercise.
     */
    async openTeam() {
        await this.page.locator('.view-team').click();
    }
}
