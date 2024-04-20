import { Page } from 'playwright';
import { Exercise } from 'app/entities/exercise.model';
import { MODELING_EXERCISE_BASE, PROGRAMMING_EXERCISE_BASE, QUIZ_EXERCISE_BASE, TEXT_EXERCISE_BASE, UPLOAD_EXERCISE_BASE } from '../../constants';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the course management exercises page.
 */
export class CourseManagementExercisesPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getExercise(exerciseID: number) {
        return this.page.locator(`#exercise-card-${exerciseID}`);
    }

    async clickDeleteExercise(exerciseID: number) {
        const exerciseElement = this.getExercise(exerciseID);
        await exerciseElement.locator('#delete-exercise').click();
    }

    async clickExampleSubmissionsButton() {
        await this.page.locator('#example-submissions-button').click();
    }

    getExerciseTitle(exerciseTitle: string) {
        return this.page.locator('dl', { hasText: 'Title' }).getByText(exerciseTitle);
    }

    async deleteTextExercise(exercise: Exercise) {
        const exerciseElement = this.getExercise(exercise.id!);
        await exerciseElement.locator('#delete-exercise').click();
        await this.page.locator('#confirm-entity-name').fill(exercise.title!);
        const responsePromise = this.page.waitForResponse(`${TEXT_EXERCISE_BASE}/*`);
        await this.page.locator('#delete').click();
        await responsePromise;
    }

    async deleteModelingExercise(exercise: Exercise) {
        const exerciseElement = this.getExercise(exercise.id!);
        await exerciseElement.locator('#delete-exercise').click();
        await this.page.locator('#confirm-entity-name').fill(exercise.title!);
        const responsePromise = this.page.waitForResponse(`${MODELING_EXERCISE_BASE}/*`);
        await this.page.locator('#delete').click();
        await responsePromise;
    }

    async deleteQuizExercise(exercise: Exercise) {
        const exerciseElement = this.getExercise(exercise.id!);
        await exerciseElement.locator(`#delete-quiz-${exercise.id}`).click();
        await this.page.locator('#confirm-entity-name').fill(exercise.title!);
        const responsePromise = this.page.waitForResponse(`${QUIZ_EXERCISE_BASE}/*`);
        await this.page.locator('#delete').click();
        await responsePromise;
    }

    async deleteProgrammingExercise(exercise: Exercise) {
        const exerciseElement = this.getExercise(exercise.id!);
        await exerciseElement.locator('#delete-exercise').click();
        await this.page.locator('#additional-check-0').check();
        await this.page.locator('#additional-check-1').check();
        await this.page.locator('#confirm-entity-name').fill(exercise.title!);
        const responsePromise = this.page.waitForResponse(`${PROGRAMMING_EXERCISE_BASE}/*`);
        await this.page.locator('#delete').click();
        await responsePromise;
    }

    async deleteFileUploadExercise(exercise: Exercise) {
        const exerciseElement = this.getExercise(exercise.id!);
        await exerciseElement.locator('#delete-exercise').click();
        await this.page.locator('#confirm-entity-name').fill(exercise.title!);
        const responsePromise = this.page.waitForResponse(`${UPLOAD_EXERCISE_BASE}/*`);
        await this.page.locator('#delete').click();
        await responsePromise;
    }

    async createProgrammingExercise() {
        await this.page.locator('#create-programming-exercise').click();
    }

    async createModelingExercise() {
        await this.page.locator('#create-modeling-exercise').click();
    }

    async createTextExercise() {
        await this.page.locator('#create-text-exercise').click();
    }

    async createQuizExercise() {
        await this.page.locator('#create-quiz-button').click();
    }

    async createFileUploadExercise() {
        await this.page.locator('#create-file-upload-exercise').click();
    }

    async importProgrammingExercise() {
        await this.page.locator('#import-programming-exercise').click();
    }

    async importModelingExercise() {
        await this.page.locator('#import-modeling-exercise').click();
    }

    async importTextExercise() {
        await this.page.locator('#import-text-exercise').click();
    }

    async importQuizExercise() {
        await this.page.locator('#import-quiz-exercise').click();
    }

    async exportQuizExercise(exerciseID: number) {
        await this.getExercise(exerciseID).locator('button', { hasText: 'Export' }).click();
    }

    async clickImportExercise(exerciseID: number) {
        await this.page.locator(`.exercise-${exerciseID}`).locator('.import').click();
    }

    async startQuiz(quizID: number) {
        await this.page.locator(`#instructor-quiz-start-${quizID}`).click();
    }

    async shouldContainExerciseWithName(exerciseID: number) {
        const exerciseElement = this.getExercise(exerciseID);
        await exerciseElement.scrollIntoViewIfNeeded();
        await expect(exerciseElement).toBeVisible();
    }

    getModelingExerciseTitle(exerciseID: number) {
        return this.page.locator(`#exercise-card-${exerciseID}`).locator(`#modeling-exercise-${exerciseID}-title`);
    }

    getModelingExerciseMaxPoints(exerciseID: number) {
        return this.page.locator(`#exercise-card-${exerciseID}`).locator(`#modeling-exercise-${exerciseID}-maxPoints`);
    }
}
