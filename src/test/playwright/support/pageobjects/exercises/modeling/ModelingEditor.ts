import { Page } from '@playwright/test';
import { getExercise } from '../../../utils';

export class ModelingEditor {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Waits until the __apollonEditor property is available on the given element selector.
     * The Angular component exposes this after the ApollonEditor is initialized.
     */
    private async waitForApollonEditor(selector: string) {
        await this.page.waitForFunction(
            (sel) => {
                const el = document.querySelector(sel);
                return el && (el as any).__apollonEditor;
            },
            selector,
            { timeout: 30000 },
        );
    }

    /**
     * Adds an element to the Apollon model programmatically via the editor's public API.
     * We bypass drag-and-drop because Apollon's SVG canvas has width="0" height="0"
     * on empty diagrams (dimensions come from diagram.bounds in Redux state), and
     * Chromium's renderer crashes when Playwright tries mouse operations on a 0×0 SVG.
     *
     * The ApollonEditor instance is exposed on the host DOM element as `__apollonEditor`
     * by the Angular ModelingEditorComponent. This works in production mode where
     * `ng.getComponent()` is not available.
     */
    private async addElementViaEditorAPI(editorSelector: string, posX: number, posY: number) {
        await this.page.evaluate(
            ({ selector, x, y }) => {
                const editorEl = document.querySelector(selector);
                if (!editorEl) throw new Error(`Modeling editor element not found: ${selector}`);

                const editor = (editorEl as any).__apollonEditor;
                if (!editor) throw new Error('ApollonEditor instance not found on element.__apollonEditor. The editor may not be initialized yet.');

                const model = JSON.parse(JSON.stringify(editor.model));

                const id = 'e2e-' + Math.random().toString(36).slice(2, 11);
                const attrId = 'e2e-a-' + Math.random().toString(36).slice(2, 11);
                const methodId = 'e2e-m-' + Math.random().toString(36).slice(2, 11);

                // Apollon v4 format: nodes[] array with embedded attributes/methods in data
                if (!model.nodes) model.nodes = [];
                model.nodes.push({
                    id,
                    width: 200,
                    height: 100,
                    type: 'class',
                    position: { x, y },
                    data: {
                        name: 'TestClass',
                        attributes: [{ id: attrId, name: '+ attribute: Type' }],
                        methods: [{ id: methodId, name: '+ method(): void' }],
                    },
                    measured: { width: 200, height: 100 },
                });

                editor.model = model;
            },
            { selector: editorSelector, x: posX, y: posY },
        );
    }

    async addComponentToModel(exerciseID: number, componentNumber: number, x?: number, y?: number) {
        const exerciseElement = getExercise(this.page, exerciseID);
        const sidebar = exerciseElement.locator('jhi-modeling-editor aside');
        await sidebar.waitFor({ state: 'visible' });
        const selector = `#exercise-${exerciseID} jhi-modeling-editor`;
        await this.waitForApollonEditor(selector);
        const posX = x ?? 100 + componentNumber * 250;
        const posY = y ?? 100;
        await this.addElementViaEditorAPI(selector, posX, posY);
    }

    /**
     * Waits until the modeling editor for the given exercise is fully initialized on this page:
     * the Apollon sidebar is visible AND the ApollonEditor instance is exposed on
     * `element.__apollonEditor`. Use this before reading the live model on a page that did not
     * itself add an element (e.g. the second collaborator in a team exercise).
     */
    async waitForEditorReady(exerciseID: number) {
        const exerciseElement = getExercise(this.page, exerciseID);
        const sidebar = exerciseElement.locator('jhi-modeling-editor aside');
        await sidebar.waitFor({ state: 'visible', timeout: 30000 });
        await this.waitForApollonEditor(`#exercise-${exerciseID} jhi-modeling-editor`);
    }

    /**
     * Reads the current number of UML nodes in the live Apollon model for the given exercise.
     *
     * Mirrors the `__apollonEditor` access used by {@link addComponentToModel}: the Angular
     * ModelingEditorComponent exposes the ApollonEditor instance on the host DOM element, and the
     * Apollon v4 model keeps its elements in a `nodes[]` array. Because the editor is Yjs-backed,
     * this reflects the live, websocket-synced state — so it can be polled on a *second* page to
     * observe an element another collaborator added.
     *
     * Returns -1 when the editor is not initialized yet (so callers can distinguish "not ready"
     * from "ready with zero nodes").
     */
    async getModelNodeCount(exerciseID: number): Promise<number> {
        const selector = `#exercise-${exerciseID} jhi-modeling-editor`;
        return this.page.evaluate((sel) => {
            const editorEl = document.querySelector(sel);
            const editor = editorEl && (editorEl as any).__apollonEditor;
            if (!editor) {
                return -1;
            }
            const nodes = editor.model?.nodes;
            return Array.isArray(nodes) ? nodes.length : 0;
        }, selector);
    }

    /**
     * Polls the live Apollon model until it contains at least `expected` UML nodes (or the timeout
     * elapses). Used to assert that a change made by one team member has propagated to another
     * member's editor via the Yjs patch relay across the cluster. Deterministic — backed by
     * `waitForFunction`, not a fixed sleep.
     *
     * @param exerciseID - The exercise whose editor to observe.
     * @param expected - The minimum node count to wait for.
     * @param timeout - Maximum time to wait in milliseconds (default 30s, generous for cross-node WS propagation).
     */
    async waitForModelNodeCount(exerciseID: number, expected: number, timeout = 30000) {
        await this.page.waitForFunction(
            ({ sel, expectedCount }) => {
                const editorEl = document.querySelector(sel);
                const editor = editorEl && (editorEl as any).__apollonEditor;
                if (!editor) {
                    return false;
                }
                const nodes = editor.model?.nodes;
                return Array.isArray(nodes) && nodes.length >= expectedCount;
            },
            { sel: `#exercise-${exerciseID} jhi-modeling-editor`, expectedCount: expected },
            { timeout },
        );
    }

    getModelingCanvas() {
        return this.page.locator('jhi-modeling-editor aside').locator('..');
    }

    async waitForExampleSolutionEditor() {
        const sidebar = this.page.locator('jhi-modeling-editor aside');
        await sidebar.waitFor({ state: 'visible', timeout: 30000 });
        await this.waitForApollonEditor('jhi-modeling-editor');
    }

    async addComponentToExampleSolutionModel(componentNumber: number) {
        const sidebar = this.page.locator('jhi-modeling-editor aside');
        await sidebar.waitFor({ state: 'visible' });
        const selector = 'jhi-modeling-editor';
        await this.waitForApollonEditor(selector);
        await this.addElementViaEditorAPI(selector, 100 + componentNumber * 250, 100);
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
