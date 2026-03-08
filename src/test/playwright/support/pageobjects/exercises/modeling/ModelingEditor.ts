import { Page } from '@playwright/test';
import { MODELING_EDITOR_CANVAS } from '../../../constants';
import { getExercise } from '../../../utils';

export class ModelingEditor {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async addComponentToModel(exerciseID: number, componentNumber: number, x?: number, y?: number) {
        const exerciseElement = getExercise(this.page, exerciseID);
        const sidebar = exerciseElement.locator('#modeling-editor-sidebar');
        await sidebar.waitFor({ state: 'visible' });
        const canvas = exerciseElement.locator(MODELING_EDITOR_CANVAS);
        await canvas.waitFor({ state: 'visible' });
        // Wait for Apollon to render the SVG canvas with non-zero dimensions
        await this.page.waitForFunction(
            (selector: string) => {
                const el = document.querySelector(selector);
                return el != null && el.getBoundingClientRect().width > 0 && el.getBoundingClientRect().height > 0;
            },
            `#exercise-${exerciseID} ${MODELING_EDITOR_CANVAS}`,
            { timeout: 15000 },
        );
        const component = sidebar.locator('div').nth(componentNumber);
        const targetPosition = x && y ? { x, y } : undefined;
        await component.dragTo(canvas, { targetPosition, force: true });
        await canvas.dispatchEvent('pointerup');
    }

    getModelingCanvas() {
        return this.page.locator('#modeling-editor-canvas');
    }

    async addComponentToExampleSolutionModel(componentNumber: number) {
        const sidebar = this.page.locator('#modeling-editor-sidebar');
        await sidebar.waitFor({ state: 'visible' });
        const canvas = this.page.locator(MODELING_EDITOR_CANVAS);
        await canvas.waitFor({ state: 'visible' });
        await this.page.waitForFunction(
            (selector: string) => {
                const el = document.querySelector(selector);
                return el != null && el.getBoundingClientRect().width > 0 && el.getBoundingClientRect().height > 0;
            },
            MODELING_EDITOR_CANVAS,
            { timeout: 15000 },
        );
        const sidebarComponent = sidebar.locator('div').nth(componentNumber);
        await sidebarComponent.dragTo(canvas, { force: true });
        await canvas.dispatchEvent('pointerup');
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`api/modeling/exercises/*/modeling-submissions`);
        await this.page.locator('#submit-modeling-submission').first().click();
        return await responsePromise;
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
