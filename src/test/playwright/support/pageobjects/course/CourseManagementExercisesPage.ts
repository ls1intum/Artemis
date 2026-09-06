import { Page } from '@playwright/test';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MODELING_EXERCISE_BASE, PROGRAMMING_EXERCISE_BASE, QUIZ_EXERCISE_BASE, TEXT_EXERCISE_BASE, UPLOAD_EXERCISE_BASE } from '../../constants';
import { expect } from '@playwright/test';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';

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

    /**
     * Clicks a row action, resolving it whether it is inline or collapsed into the row's ellipsis overflow menu.
     * @param exerciseID the exercise whose row to act on
     * @param actionId the action id from `exercise-actions.component.ts` (e.g. `delete`, `edit`, `participations`)
     */
    private async clickRowAction(exerciseID: number, actionId: string) {
        const row = this.getExercise(exerciseID);
        await row.waitFor({ state: 'attached' });
        const inlineAction = row.locator(`[data-testid="exercise-action-${actionId}"]`);
        if (await inlineAction.isVisible()) {
            await inlineAction.click();
            return;
        }
        // The action collapsed into the row's ellipsis overflow menu, which the kit renders in an overlay popover.
        await row.locator('.action-more').click();
        await this.page.locator('.tum-ui-popover-panel').getByTestId(`exercise-action-${actionId}`).click();
    }

    /**
     * Opens the exercise create/import modal via the toolbar button and selects the exercise-type card for the given
     * type. Mode is `create` or `import`; the type is the {@link ExerciseType} enum value (e.g. `file-upload`, `text`).
     */
    private async selectExerciseTypeCard(mode: 'create' | 'import', type: string) {
        await this.page.getByTestId(`${mode}-exercise-button`).click();
        await this.page.getByTestId(`${mode}-${type}-exercise`).click();
    }

    async clickDeleteExercise(exerciseID: number) {
        await this.clickRowAction(exerciseID, 'delete');
    }

    /**
     * Opens the "Create Variant with AI" wizard for the given exercise (the `create-variant-ai` row action,
     * shown to at-least-editors). Resolves the action whether it is inline or collapsed into the overflow menu.
     */
    async openCreateVariantWithAi(exerciseID: number) {
        await this.waitForExerciseCardAttached(exerciseID);
        await this.clickRowAction(exerciseID, 'create-variant-ai');
    }

    async clickExampleSubmissionsButton() {
        await this.page.locator('[data-testid="example-submissions-button"]').click();
    }

    getExerciseTitle(exerciseTitle: string) {
        return this.page.locator('dl', { hasText: 'Title' }).getByText(exerciseTitle);
    }

    async deleteTextExercise(exercise: Exercise) {
        await this.clickRowAction(exercise.id!, 'delete');
        await this.page.locator('#confirm-entity-name').fill(exercise.title!);
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes(TEXT_EXERCISE_BASE) && resp.request().method() === 'DELETE');
        await this.page.getByTestId('delete-dialog-confirm-button').click();
        await responsePromise;
    }

    async deleteModelingExercise(exercise: Exercise) {
        await this.clickRowAction(exercise.id!, 'delete');
        await this.page.locator('#confirm-entity-name').fill(exercise.title!);
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes(MODELING_EXERCISE_BASE) && resp.request().method() === 'DELETE');
        await this.page.getByTestId('delete-dialog-confirm-button').click();
        await responsePromise;
    }

    async deleteQuizExercise(exercise: Exercise) {
        await this.clickRowAction(exercise.id!, 'delete');
        await this.page.locator('#confirm-entity-name').fill(exercise.title!);
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes(QUIZ_EXERCISE_BASE) && resp.request().method() === 'DELETE');
        await this.page.getByTestId('delete-dialog-confirm-button').click();
        await responsePromise;
    }

    async deleteProgrammingExercise(exercise: Exercise) {
        await this.clickRowAction(exercise.id!, 'delete');
        await this.page.locator('#confirm-entity-name').fill(exercise.title!);
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes(PROGRAMMING_EXERCISE_BASE) && resp.request().method() === 'DELETE');
        await this.page.getByTestId('delete-dialog-confirm-button').click();
        await responsePromise;
        // Wait for the delete confirmation dialog to close and the exercise to be
        // removed from the DOM. The dialog close is fast, but Angular's change detection
        // might not remove the card immediately. Poll until both are done.
        await expect(this.page.getByTestId('delete-dialog-confirm-button')).not.toBeVisible({ timeout: 10000 });
        // Wait for the exercise card to disappear. If Angular doesn't remove it
        // after the dialog closes, reload to force a fresh render.
        try {
            await expect(this.getExercise(exercise.id!)).not.toBeAttached({ timeout: 5000 });
        } catch {
            await this.page.reload();
            await this.page.waitForLoadState('domcontentloaded');
            // Re-assert after the reload so a still-present card surfaces as a real test failure
            // instead of being silently swallowed by the catch.
            await expect(this.getExercise(exercise.id!)).not.toBeAttached({ timeout: 5000 });
        }
    }

    async deleteFileUploadExercise(exercise: Exercise) {
        await this.clickRowAction(exercise.id!, 'delete');
        await this.page.locator('#confirm-entity-name').fill(exercise.title!);
        const responsePromise = this.page.waitForResponse((resp) => resp.url().includes(UPLOAD_EXERCISE_BASE) && resp.request().method() === 'DELETE');
        await this.page.getByTestId('delete-dialog-confirm-button').click();
        await responsePromise;
    }

    async createProgrammingExercise() {
        await this.selectExerciseTypeCard('create', 'programming');
    }

    async createModelingExercise() {
        await this.selectExerciseTypeCard('create', 'modeling');
    }

    async createTextExercise() {
        await this.selectExerciseTypeCard('create', 'text');
    }

    async createQuizExercise() {
        await this.selectExerciseTypeCard('create', 'quiz');
    }

    async createFileUploadExercise() {
        await this.selectExerciseTypeCard('create', 'file-upload');
    }

    async importProgrammingExercise() {
        await this.selectExerciseTypeCard('import', 'programming');
    }

    async importModelingExercise() {
        await this.selectExerciseTypeCard('import', 'modeling');
    }

    async importTextExercise() {
        await this.selectExerciseTypeCard('import', 'text');
    }

    async importQuizExercise() {
        await this.selectExerciseTypeCard('import', 'quiz');
    }

    async clickImportExercise(exerciseID: number) {
        // Search by ID to handle pagination when many exercises exist
        const searchInput = this.page.locator('input[name="searchExcercise"]');
        await searchInput.fill(String(exerciseID));
        await this.page.locator(`.exercise-${exerciseID}`).locator('.import').click();
    }

    async startQuiz(quizID: number) {
        const startButton = this.page.locator(`#instructor-quiz-start-${quizID}`);
        await startButton.waitFor({ state: 'visible', timeout: 10000 });
        await startButton.click();
    }

    async endQuiz(quizExercise: QuizExercise) {
        const endButton = this.page.locator(`#quiz-set-end-${quizExercise.id}`);
        await endButton.waitFor({ state: 'visible', timeout: 10000 });
        await endButton.scrollIntoViewIfNeeded();
        await endButton.click();
        await this.page.locator('#confirm-entity-name').fill(quizExercise.title!);
        // Wait for the end-now request to actually complete before returning. Otherwise a follow-up step (e.g. a student
        // loading the exercise to practice) can race the in-flight end-now and observe the quiz as still "Waiting for
        // Start", with no Practice option — the root cause of the practice-mode flake.
        const endResponse = this.page.waitForResponse(
            (resp) => resp.url().includes(`/quiz-exercises/${quizExercise.id}/end-now`) && resp.request().method() === 'PUT' && resp.ok(),
            { timeout: 20000 },
        );
        await this.page.getByTestId('delete-dialog-confirm-button').click();
        await endResponse;
    }

    async shouldContainExerciseWithName(exerciseID: number) {
        const exerciseElement = this.getExercise(exerciseID);
        await exerciseElement.scrollIntoViewIfNeeded();
        await expect(exerciseElement).toBeVisible();
    }

    async openExerciseParticipations(exerciseId: number) {
        await this.waitForExerciseCardAttached(exerciseId);
        await this.clickRowAction(exerciseId, 'participations');
    }

    /**
     * Opens the edit form of the given exercise from the exercises list.
     * <p>
     * Robust against the multi-node "freshly-created exercise not yet visible on the routed node" race: waits for the
     * exercise's Edit link in short bursts, reloading the list (which re-issues the exercise-list GET) between attempts.
     * Without this, clicking the Edit link of a card that only a reload would surface auto-waits until the whole test
     * times out. The per-attempt timeouts are kept short so the total stays within the fast-test budget.
     *
     * @param exerciseId - The ID of the exercise to edit.
     */
    async openExerciseEditForm(exerciseId: number): Promise<void> {
        for (let attempt = 0; attempt < 3; attempt++) {
            try {
                await this.getExercise(exerciseId).waitFor({ state: 'visible', timeout: 10_000 });
                await this.clickRowAction(exerciseId, 'edit');
                return;
            } catch (error) {
                if (attempt === 2) {
                    throw error;
                }
                // The freshly-created exercise card has not surfaced on the routed node yet — re-issue the list GET.
                await this.page.reload({ waitUntil: 'domcontentloaded' });
            }
        }
    }

    async openQuizExerciseDetailsPage(exerciseId: number) {
        await Promise.all([
            this.page.waitForURL(`/course-management/*/quiz-exercises/${exerciseId}`),
            this.getExercise(exerciseId).getByTestId('exercise-row-title').getByRole('link').click(),
        ]);
    }

    getModelingExerciseTitle(exerciseID: number) {
        return this.getExercise(exerciseID).getByTestId('exercise-row-title');
    }

    getModelingExerciseMaxPoints(exerciseID: number) {
        return this.getExercise(exerciseID).getByTestId('exercise-row-points');
    }

    async openExerciseTeams(exerciseId: number) {
        await this.waitForExerciseCardAttached(exerciseId);
        await this.clickRowAction(exerciseId, 'teams');
    }

    /**
     * Wait for the given exercise card to be attached, reloading once if necessary.
     * Under heavy parallel multi-node load, the exercise-list GET that the page issues
     * on first render occasionally returns an empty list right after a sibling create —
     * the freshly-created exercise has not yet become visible through the routed node
     * (eventual visibility across the load-balancer + caches). A single reload re-issues
     * the GET and reliably surfaces the card on the second attempt.
     */
    private async waitForExerciseCardAttached(exerciseId: number): Promise<void> {
        const card = this.getExercise(exerciseId);
        const attachedWithin = async (timeout: number): Promise<boolean> =>
            card
                .waitFor({ state: 'attached', timeout })
                .then(() => true)
                .catch(() => false);
        if (await attachedWithin(15_000)) {
            return;
        }
        await this.page.reload();
        await this.page.waitForLoadState('load');
        await card.waitFor({ state: 'attached', timeout: 60_000 });
    }
}
