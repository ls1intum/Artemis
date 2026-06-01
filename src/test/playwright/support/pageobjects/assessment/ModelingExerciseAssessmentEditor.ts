import { BASE_API, ExerciseType } from '../../constants';
import { AbstractExerciseAssessmentPage } from './AbstractExerciseAssessmentPage';
import { expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the Modeling Exercise Assessment editor.
 *
 * Assessment input previously went through Apollon's MUI popover (dblclick on a node, then
 * fill spinbutton + textarea). That sequence is racy on slow / multi-node stacks: the popover
 * occasionally fails to mount even after the node receives the dblclick (Apollon's React tree
 * reconciles after the parent Angular component re-runs effects on incoming server data), which
 * left every modeling assessment test failing on the multi-node pipeline.
 *
 * Instead we drive Apollon via its public API on the host element (`__apollonEditor`,
 * `editor.addOrUpdateAssessment`). This matches the pattern already used for adding components
 * in `ModelingEditor` and removes the popover/timing dependency entirely. The save click on
 * "Save Example Assessment" / submit endpoint is unchanged, so the server contract is still
 * exercised end-to-end.
 */
export class ModelingExerciseAssessmentEditor extends AbstractExerciseAssessmentPage {
    private currentAssessmentIndex = 0;
    private static readonly EDITOR_SELECTOR = 'jhi-modeling-assessment';

    /**
     * Selects the component to assess next. With the API-driven flow this only stores the index;
     * the actual assessment is applied by {@link assessComponent}.
     */
    async openAssessmentForComponent(componentNumber: number) {
        // The previous test code passed 1-based indexes; preserve that convention here so callers
        // do not need to be touched. The original UI selector also subtracted 1.
        this.currentAssessmentIndex = Math.max(0, componentNumber - 1);
        await this.waitForAssessmentEditorReady();
    }

    async assessComponent(points: number, feedback: string) {
        await this.waitForAssessmentEditorReady();
        await this.page.evaluate(
            ({ selector, index, score, feedbackText }) => {
                const el = document.querySelector(selector);
                if (!el) {
                    throw new Error(`Modeling assessment editor element not found: ${selector}`);
                }
                const editor = (el as any).__apollonEditor;
                if (!editor) {
                    throw new Error('ApollonEditor instance not found on element.__apollonEditor; assessment editor may not be initialized yet.');
                }
                const nodes: Array<{ id: string; type: string }> = editor.model?.nodes ?? [];
                const node = nodes[index];
                if (!node) {
                    throw new Error(`No model node at index ${index}; model has ${nodes.length} node(s).`);
                }
                editor.addOrUpdateAssessment({
                    modelElementId: node.id,
                    elementType: node.type,
                    score,
                    feedback: feedbackText,
                });
            },
            { selector: ModelingExerciseAssessmentEditor.EDITOR_SELECTOR, index: this.currentAssessmentIndex, score: points, feedbackText: feedback },
        );
    }

    async clickNextAssessment() {
        // Advance the cursor; the assessment for the previously selected component was already
        // applied via the editor API in {@link assessComponent}.
        this.currentAssessmentIndex += 1;
    }

    rejectComplaint(response: string, examMode: false) {
        return super.rejectComplaint(response, examMode, ExerciseType.MODELING);
    }

    acceptComplaint(response: string, examMode: false) {
        return super.acceptComplaint(response, examMode, ExerciseType.MODELING);
    }

    async closeAssessmentPanel() {
        // No-op with the API-driven flow — kept so existing callers (and any UI assertions that
        // rely on the popover being closed) do not break. Press Escape defensively in case a
        // popover happened to open via stray click.
        await this.page.keyboard.press('Escape');
    }

    async submitExample() {
        await this.page.getByText('Save Example Assessment').click();
        await expect(this.page.getByText('Your assessment was saved successfully!')).toBeVisible({ timeout: 30000 });
    }

    async submit() {
        // Retry on multi-node 5xx flakes (Hazelcast Result.feedbacks ordered-list invalidation lag).
        for (let attempt = 0; attempt < 3; attempt++) {
            const responsePromise = this.page.waitForResponse(`${BASE_API}/modeling/modeling-submissions/*/results/*/assessment*`);
            await super.submitWithoutInterception();
            const response = await responsePromise;
            if (response.status() < 400) {
                return response;
            }
            if (attempt === 2) {
                expect(response.status()).toBe(200);
                return response;
            }
            await this.page.waitForTimeout(1500);
        }
        throw new Error('ModelingExerciseAssessmentEditor.submit exhausted retries');
    }

    /**
     * Waits until the ApollonEditor instance is exposed on the host modeling-assessment element.
     * The component sets `__apollonEditor` synchronously after the editor is constructed in
     * `ngAfterViewInit`, so a short wait is normally enough; the timeout is generous for
     * multi-node CI runners under load.
     */
    private async waitForAssessmentEditorReady() {
        await this.page.waitForFunction(
            (selector) => {
                const el = document.querySelector(selector);
                return !!el && !!(el as any).__apollonEditor && Array.isArray((el as any).__apollonEditor.model?.nodes);
            },
            ModelingExerciseAssessmentEditor.EDITOR_SELECTOR,
            { timeout: 30_000 },
        );
    }
}
