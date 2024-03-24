import { Page } from 'playwright';
import { EXERCISE_BASE, MODELING_EDITOR_CANVAS } from '../../../constants';
import { Locator } from '@playwright/test';

export class DragAndDropQuiz {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async createDnDQuiz(title: string) {
        await this.page.locator('#quiz-import-apollon-dnd-question').click();
        await this.page.locator('#create-apollon-diagram').click();
        await this.page.locator('#field_diagram_title').fill(title);
        await this.page.locator('#save-dnd-quiz').click();
        await this.page.locator('#open-diagram').click();
    }

    async dragItemIntoDragArea(itemIndex: number) {
        const dragLocation = this.page.locator(`#drag-item-${itemIndex}`);
        const dropLocation = this.page.locator('#drop-location');
        await dragLocation.dragTo(dropLocation);
    }

    async setTitle(title: string) {
        await this.page.locator('#field_title').fill(title);
    }

    async dragUsingCoordinates(x: number, y: number) {
        const classElement = this.page.locator('#modeling-editor-sidebar').locator('div').nth(2);
        const modelingEditorCanvas = this.page.locator(MODELING_EDITOR_CANVAS);
        await classElement.dragTo(modelingEditorCanvas, { targetPosition: { x: x, y: y } });
    }

    async getXAxis(elements: Locator) {
        let minX = Number.MAX_SAFE_INTEGER;
        let maxX = Number.MIN_SAFE_INTEGER;

        const elementsCount = await elements.count();

        for (let index = 0; index < elementsCount; index++) {
            const element = elements.nth(index);
            const rect = await element.boundingBox();
            if (rect) {
                if (rect.x < minX) {
                    minX = rect.x;
                }
                if (rect.x + rect.width > maxX) {
                    maxX = rect.x + rect.width;
                }
            }
        }

        return { minX, maxX };
    }

    async activateInteractiveMode() {
        const modelingEditorSidebar = this.page.locator('#modeling-editor-sidebar');
        const container = modelingEditorSidebar.locator('div').nth(0);
        const interactiveButton = container.locator('button').nth(1);
        await interactiveButton.click();
    }

    async markElementAsInteractive(nthElementOnCanvas: number, nthChildOfElement: number) {
        const modelingEditorCanvas = this.page.locator(MODELING_EDITOR_CANVAS);
        const canvasElements = modelingEditorCanvas.locator('g').locator('svg').nth(0);
        const nthElement = canvasElements.locator('svg', { has: this.page.locator('svg') }).nth(nthElementOnCanvas);
        const nthChild = nthElement.locator('g').locator('svg').nth(nthChildOfElement);
        await nthChild.click({ force: true });
    }

    async generateQuizExercise() {
        await this.page.locator('#generate-quiz-exercise').click();
        await this.page.locator('#quiz-save').isVisible();
        await this.page.locator('#quiz-save').click();
    }

    async waitForQuizExerciseToBeGenerated() {
        await this.page.locator('#jhi-text-exercise-heading-edit').waitFor({ state: 'visible' });
    }

    async previewQuiz() {
        await this.page.locator('#preview-quiz').click();
    }

    async waitForQuizPreviewToLoad() {
        await this.page.locator('.drag-and-drop-area').waitFor({ state: 'visible' });
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`${EXERCISE_BASE}/*/submissions/live`);
        await this.page.locator('#submit-quiz').click();
        await responsePromise;
    }
}
