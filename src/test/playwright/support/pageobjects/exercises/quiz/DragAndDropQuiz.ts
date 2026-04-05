import { Page } from 'playwright';
import { drag } from '../../../utils';
import { Locator } from '@playwright/test';

export class DragAndDropQuiz {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Waits until the __apollonEditor property is available on the quiz detail component.
     */
    private async waitForApollonEditor() {
        await this.page.waitForFunction(
            () => {
                const el = document.querySelector('jhi-apollon-diagram-detail');
                return el && (el as any).__apollonEditor;
            },
            undefined,
            { timeout: 30000 },
        );
    }

    async createDnDQuiz(title: string) {
        await this.page.locator('#quiz-import-apollon-dnd-question').click();
        await this.page.locator('#create-apollon-diagram').click();
        await this.page.locator('#field_diagram_title').fill(title);
        await this.page.locator('#save-dnd-quiz').click();
    }

    async dragItemIntoDragArea(itemIndex: number) {
        const dragLocation = this.page.locator(`#drag-item-${itemIndex}`);
        const dropLocation = this.page.locator('#drop-location');
        await drag(this.page, dragLocation, dropLocation);
    }

    async setTitle(title: string) {
        await this.page.locator('#field_title').fill(title);
    }

    /**
     * Adds a class element to the Apollon model at the given coordinates using the editor API.
     * Apollon v4 uses React Flow with a different DOM structure, so drag-and-drop from the
     * sidebar is unreliable. Instead, we programmatically add nodes via the editor instance.
     *
     * The quiz DnD editor is rendered in a modal (apollon-diagram-detail), not inside
     * jhi-modeling-editor. The ApollonEditor is mounted on a container div inside the modal body.
     * We locate it by finding the container that has the Apollon React root.
     */
    async dragUsingCoordinates(x: number, y: number) {
        // The quiz editor renders an <aside> inside the modal body's editor container
        const sidebar = this.page.locator('.modal-body aside');
        await sidebar.waitFor({ state: 'visible', timeout: 30000 });
        await this.waitForApollonEditor();

        await this.page.evaluate(
            ({ posX, posY }) => {
                const el = document.querySelector('jhi-apollon-diagram-detail');
                if (!el) throw new Error('jhi-apollon-diagram-detail not found');
                const editor = (el as any).__apollonEditor;
                if (!editor) throw new Error('ApollonEditor not found on jhi-apollon-diagram-detail.__apollonEditor');

                const model = JSON.parse(JSON.stringify(editor.model));
                const id = 'e2e-dnd-' + Math.random().toString(36).slice(2, 11);

                if (!model.nodes) model.nodes = [];
                model.nodes.push({
                    id,
                    width: 150,
                    height: 80,
                    type: 'class',
                    position: { x: posX + 50, y: posY + 20 },
                    data: { name: 'DnDClass', attributes: [], methods: [] },
                    measured: { width: 150, height: 80 },
                });

                editor.model = model;
            },
            { posX: x, posY: y },
        );
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

    /**
     * In Apollon v4, all elements are automatically interactive — there is no separate
     * "exporting" mode. This method is a no-op retained for test compatibility.
     */
    async activateInteractiveMode() {
        // No-op: Apollon v4 treats all nodes as interactive by default
    }

    /**
     * In Apollon v4, all elements are automatically interactive — there is no need to
     * mark individual elements. This method is a no-op retained for test compatibility.
     */
    async markElementAsInteractive(_nthElementOnCanvas: number, _nthChildOfElement: number) {
        // No-op: Apollon v4 treats all nodes as interactive by default
    }

    async generateQuizExercise(): Promise<number> {
        await this.page.locator('#generate-quiz-exercise').click();
        await this.page.locator('#quiz-save').waitFor({ state: 'visible' });
        const responsePromise = this.page.waitForResponse(/api\/quiz\/(courses|exercise-groups)\/\d+\/quiz-exercises$/);
        await this.page.locator('#quiz-save').click();
        const response = await responsePromise;
        const exercise = await response.json();
        return exercise.id;
    }

    async waitForQuizExerciseToBeGenerated() {
        await this.page.locator('#jhi-text-exercise-heading-edit').waitFor({ state: 'visible' });
    }

    async previewQuiz() {
        await this.page.locator('#preview-quiz').first().click();
    }

    async waitForQuizPreviewToLoad() {
        await this.page.locator('.drag-and-drop-area').waitFor({ state: 'visible' });
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`api/quiz/exercises/*/submissions/live?submit=true`);
        await this.page.locator('#submit-quiz').click();
        await responsePromise;
    }
}
