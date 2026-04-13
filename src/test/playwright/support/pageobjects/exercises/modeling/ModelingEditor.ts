import { Page } from '@playwright/test';
import { MODELING_EDITOR_CANVAS } from '../../../constants';
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

                model.elements[id] = {
                    id,
                    name: 'TestClass',
                    type: 'Class',
                    owner: null,
                    bounds: { x, y, width: 200, height: 100 },
                    attributes: [attrId],
                    methods: [methodId],
                };
                model.elements[attrId] = {
                    id: attrId,
                    name: '+ attribute: Type',
                    type: 'ClassAttribute',
                    owner: id,
                    bounds: { x: 0, y: 40, width: 200, height: 30 },
                };
                model.elements[methodId] = {
                    id: methodId,
                    name: '+ method(): void',
                    type: 'ClassMethod',
                    owner: id,
                    bounds: { x: 0, y: 70, width: 200, height: 30 },
                };
                model.interactive.elements[id] = true;
                model.interactive.elements[attrId] = true;
                model.interactive.elements[methodId] = true;

                editor.model = model;
            },
            { selector: editorSelector, x: posX, y: posY },
        );
    }

    async addComponentToModel(exerciseID: number, componentNumber: number, x?: number, y?: number) {
        const exerciseElement = getExercise(this.page, exerciseID);
        const sidebar = exerciseElement.locator('#modeling-editor-sidebar');
        await sidebar.waitFor({ state: 'visible' });
        const canvas = exerciseElement.locator(MODELING_EDITOR_CANVAS);
        await canvas.waitFor({ state: 'visible' });
        const selector = `#exercise-${exerciseID} jhi-modeling-editor`;
        await this.waitForApollonEditor(selector);
        const posX = x ?? 100 + componentNumber * 250;
        const posY = y ?? 100;
        await this.addElementViaEditorAPI(selector, posX, posY);
    }

    getModelingCanvas() {
        return this.page.locator('#modeling-editor-canvas');
    }

    async waitForExampleSolutionEditor() {
        const sidebar = this.page.locator('#modeling-editor-sidebar');
        await sidebar.waitFor({ state: 'visible', timeout: 30000 });
        const canvas = this.page.locator(MODELING_EDITOR_CANVAS);
        await canvas.waitFor({ state: 'visible', timeout: 30000 });
        await this.waitForApollonEditor('jhi-modeling-editor');
    }

    async addComponentToExampleSolutionModel(componentNumber: number) {
        const sidebar = this.page.locator('#modeling-editor-sidebar');
        await sidebar.waitFor({ state: 'visible' });
        const canvas = this.page.locator(MODELING_EDITOR_CANVAS);
        await canvas.waitFor({ state: 'visible' });
        const selector = 'jhi-modeling-editor';
        await this.waitForApollonEditor(selector);
        await this.addElementViaEditorAPI(selector, 100 + componentNumber * 250, 100);
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
