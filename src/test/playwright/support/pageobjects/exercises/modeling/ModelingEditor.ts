import { Page } from '@playwright/test';
import { BASE_API, MODELING_EDITOR_CANVAS } from '../../../constants';
import { getExercise } from '../../../utils';

export class ModelingEditor {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async addComponentToModel(exerciseID: number, componentNumber: number, x?: number, y?: number) {
        const exerciseElement = getExercise(this.page, exerciseID);
        const component = exerciseElement.locator('#modeling-editor-sidebar').locator('div').nth(componentNumber);
        const targetPosition = x && y ? { x, y } : undefined;
        await component.dragTo(exerciseElement.locator(MODELING_EDITOR_CANVAS), { targetPosition });
        await exerciseElement.locator(MODELING_EDITOR_CANVAS).dispatchEvent('pointerup');
    }

    getModelingCanvas() {
        return this.page.locator('#modeling-editor-canvas');
    }

    async addComponentToExampleSolutionModel(componentNumber: number) {
        const sidebarComponent = this.page.locator('#modeling-editor-sidebar').nth(componentNumber);
        await sidebarComponent.dragTo(this.page.locator(MODELING_EDITOR_CANVAS));
        await this.page.locator(MODELING_EDITOR_CANVAS).dispatchEvent('pointerup');
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`${BASE_API}exercises/*/modeling-submissions`);
        await this.page.locator('#submit-modeling-submission').first().click();
        await responsePromise;
    }

    async clickCreateNewExampleSubmission() {
        await this.page.locator('#new-modeling-example-submission').click();
    }

    async clickCreateExampleSubmission() {
        await this.page.locator('#create-example-submission').click();
    }

    async showExampleAssessment() {
        await this.page.locator('#show-modeling-example-assessment').click();
    }
}
