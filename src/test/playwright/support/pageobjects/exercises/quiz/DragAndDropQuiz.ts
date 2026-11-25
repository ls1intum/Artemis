import { Page } from '@playwright/test';
import { drag, readResponseJson } from '../../../utils';
import { Locator } from '@playwright/test';

export class DragAndDropQuiz {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async createDnDQuiz(title: string) {
        await this.page.locator('[data-testid="quiz-import-apollon-dnd-question"]').click();
        await this.page.locator('[data-testid="create-apollon-diagram"]').click();
        await this.page.locator('#field_diagram_title').fill(title);
        await this.page.locator('[data-testid="save-dnd-quiz"]').click();
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
     * Adds a class node to the Apollon diagram at the given position using the editor API.
     * In Apollon v4, drag-and-drop from the sidebar is replaced by direct API manipulation.
     */
    async dragUsingCoordinates(x: number, y: number) {
        await this.page.waitForFunction(
            () => {
                const el = document.querySelector('jhi-apollon-diagram-detail');
                return el && (el as any).__apollonEditor;
            },
            { timeout: 30000 },
        );

        await this.page.evaluate(
            ({ posX, posY }) => {
                const el = document.querySelector('jhi-apollon-diagram-detail');
                const editor = (el as any).__apollonEditor;
                const model = JSON.parse(JSON.stringify(editor.model));
                if (!model.nodes) model.nodes = [];
                model.nodes.push({
                    id: 'e2e-' + Math.random().toString(36).slice(2, 11),
                    width: 200,
                    height: 100,
                    type: 'class',
                    position: { x: posX, y: posY },
                    data: {
                        name: 'TestClass',
                        attributes: [],
                        methods: [],
                    },
                    measured: { width: 200, height: 100 },
                });
                editor.model = model;
            },
            { posX: x, posY: y },
        );
    }

    /** The Apollon editor in the diagram dialog; its popovers render inside it while the dialog is open. */
    get apollonEditor(): Locator {
        return this.page.getByTestId('apollon-diagram-editor');
    }

    /**
     * Adds a class through the palette, the way an instructor does: a tap places it in the centre of
     * the canvas and selects it, so its edit and delete actions show.
     */
    async addClassFromPalette() {
        await this.apollonEditor.getByRole('button', { name: 'Add element: Class' }).click();
    }

    /**
     * Opens the edit popover of the selected class through its pen action and returns the class's name field.
     * The popover lists the class name first, followed by a name field for each attribute and method.
     */
    async openEditPopoverOfSelectedElement(): Promise<Locator> {
        await this.apollonEditor.getByRole('button', { name: 'Edit element' }).click();
        return this.apollonEditor.getByRole('textbox', { name: 'Name', exact: true }).first();
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

    async generateQuizExercise(): Promise<number> {
        await this.page.locator('[data-testid="generate-quiz-exercise"]').click();
        await this.page.locator('[data-testid="quiz-save"]').waitFor({ state: 'visible' });
        const responsePromise = this.page.waitForResponse(/api\/quiz\/(courses|exercise-groups)\/\d+\/quiz-exercises$/);
        await this.page.locator('[data-testid="quiz-save"]').click();
        const response = await responsePromise;
        const exercise = await readResponseJson(response);
        return exercise.id;
    }

    async previewQuiz() {
        await this.page.locator('[data-testid="preview-quiz"]').first().click();
    }

    async waitForQuizPreviewToLoad() {
        await this.page.locator('.drag-and-drop-area').waitFor({ state: 'visible' });
    }

    async submit() {
        const responsePromise = this.page.waitForResponse(`api/quiz/exercises/*/submissions/live?submit=true`);
        await this.page.locator('#submit-exercise, [data-testid="submit-exercise-popover"], [data-testid="submit-quiz"]').first().click();
        await responsePromise;
    }
}
