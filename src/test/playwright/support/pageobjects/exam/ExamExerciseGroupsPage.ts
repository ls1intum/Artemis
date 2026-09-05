import { Page, expect } from '@playwright/test';

export class ExamExerciseGroupsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async shouldHaveTitle(groupID: number, groupTitle: string) {
        await expect(this.page.locator(`#group-${groupID} .group-title`).filter({ hasText: groupTitle })).toBeVisible();
    }

    async shouldNotExist(groupID: number) {
        await expect(this.page.locator(`#group-${groupID}`)).not.toBeVisible();
    }

    async clickEditGroup(groupID: number) {
        await this.page.click(`#group-${groupID} .edit-group`);
    }

    /**
     * Clicks a row action for the given exercise. Exercise actions live in the shared `jhi-exercise-action-bar`
     * component: each action is rendered inline in the row, but narrow rows collapse the leftmost ones into an
     * ellipsis overflow menu (appended to the body). This resolves the action whether it is visible inline or
     * hidden in that menu.
     */
    private async clickRowAction(groupID: number, exerciseID: number, actionId: string) {
        const row = this.page.locator(`#group-${groupID} #exercise-${exerciseID}`);
        const inlineAction = row.locator(`[data-testid="exercise-action-${actionId}"]`);
        const overflowTrigger = row.locator('.action-more');
        // The bar only decides what collapses once its ResizeObserver has measured the row, so right after the row
        // attaches neither the inline action nor the (hidden until needed) ellipsis trigger is visible yet. The
        // `:visible` filter is what makes this wait correct: the inline action always precedes the trigger in the DOM,
        // so an unfiltered `.first()` would latch onto it even once it has collapsed and then time out.
        await row.locator(`[data-testid="exercise-action-${actionId}"]:visible, .action-more:visible`).first().waitFor({ state: 'visible', timeout: 30000 });
        if (await inlineAction.isVisible()) {
            await inlineAction.click();
            return;
        }
        // The action collapsed into the row's ellipsis overflow menu, which the kit renders in an overlay popover.
        await overflowTrigger.click();
        await this.page.locator('.tum-ui-popover-panel').getByTestId(`exercise-action-${actionId}`).click();
    }

    /**
     * Opens the group's "Add Exercise" type-picker modal and selects the exercise-type card on its create tab.
     * The type is the exercise-type route segment (e.g. `file-upload`, `text`).
     */
    private async selectExerciseTypeCard(groupID: number, type: string) {
        const addButton = this.page.locator(`#group-${groupID}`).getByTestId('add-exercise-button');
        await addButton.waitFor({ state: 'visible', timeout: 30000 });
        await addButton.click();
        await this.page.getByTestId(`create-${type}-exercise`).click();
    }

    async clickDeleteGroup(groupID: number, groupName: string) {
        await this.page.click(`#group-${groupID} .delete-group`);
        const deleteButton = this.page.getByTestId('delete-dialog-confirm-button');
        await expect(deleteButton).toBeDisabled();
        await this.page.fill('#confirm-entity-name', groupName);
        await expect(deleteButton).toBeEnabled();
        await deleteButton.click();
    }

    async shouldShowNumberOfExerciseGroups(numberOfGroups: number) {
        // The count is now part of the page's title-bar heading ("Exercise Groups (N)") rather than a separate line.
        const titleLocator = this.page.locator('[data-testid="exercise-groups-title"]');
        await titleLocator.waitFor({ state: 'visible', timeout: 30000 });
        await expect(titleLocator).toContainText(`(${numberOfGroups})`, { timeout: 30000 });
    }

    async clickAddExerciseGroup() {
        const createButton = this.page.locator('#create-new-group');
        await createButton.waitFor({ state: 'visible', timeout: 30000 });
        await createButton.click();
    }

    async clickAddTextExercise(groupID: number) {
        await this.selectExerciseTypeCard(groupID, 'text');
    }

    async clickAddModelingExercise(groupID: number) {
        await this.selectExerciseTypeCard(groupID, 'modeling');
    }

    async clickAddQuizExercise(groupID: number) {
        await this.selectExerciseTypeCard(groupID, 'quiz');
    }

    async clickAddProgrammingExercise(groupID: number) {
        await this.selectExerciseTypeCard(groupID, 'programming');
    }

    async clickEditExercise(groupID: number, exerciseID: number) {
        await this.clickRowAction(groupID, exerciseID, 'edit');
    }

    async visitPageViaUrl(courseId: number, examId: number) {
        // Reload once if the exercise-groups lazy chunk fails to render `[data-testid="exercise-groups-title"]`
        // within 30s under multi-node CI load (same pattern as other navigateToXxxPage
        // helpers in this codebase).
        const url = `/course-management/${courseId}/exams/${examId}/exercise-groups`;
        const marker = this.page.locator('[data-testid="exercise-groups-title"]');
        await this.page.goto(url);
        const visible = await marker
            .waitFor({ state: 'visible', timeout: 30000 })
            .then(() => true)
            .catch(() => false);
        if (!visible) {
            await this.page.goto(url);
            await marker.waitFor({ state: 'visible', timeout: 30000 });
        }
    }

    async shouldContainExerciseWithTitle(groupID: number, exerciseTitle: string) {
        // Wait for DOM content to load but NOT networkidle, because programming exercise
        // creation triggers async builds that produce ongoing network traffic, causing
        // networkidle to block indefinitely and the test to time out.
        await this.page.waitForLoadState('domcontentloaded');
        const exerciseElement = this.page.locator(`#group-${groupID} #exercises`, { hasText: exerciseTitle });
        await exerciseElement.waitFor({ state: 'attached', timeout: 30000 });
        await exerciseElement.scrollIntoViewIfNeeded();
        await expect(exerciseElement).toBeVisible({ timeout: 10000 });
    }
}
