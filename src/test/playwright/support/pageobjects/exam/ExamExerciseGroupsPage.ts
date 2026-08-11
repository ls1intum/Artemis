import { Page, expect } from '@playwright/test';

export class ExamExerciseGroupsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async clickCreateNewExerciseGroup() {
        await this.page.click('#create-new-group');
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
     * Opens the per-group "Add Exercise" / "Import Exercise" type-picker modal and selects the exercise-type card.
     * Mode is `create` or `import`; the type is the exercise-type route segment (e.g. `file-upload`, `text`).
     */
    private async selectExerciseTypeCard(groupID: number, mode: 'create' | 'import', type: string) {
        await this.page.locator(`#group-${groupID}`).getByTestId(`${mode}-exercise-button`).click();
        await this.page.getByTestId(`${mode}-${type}-exercise`).click();
    }

    async clickEditGroupForTestExam() {
        await this.page.getByRole('link', { name: 'Edit' }).click();
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
        const titleLocator = this.page.locator('#exercise-groups-title');
        await titleLocator.waitFor({ state: 'visible', timeout: 30000 });
        await expect(titleLocator).toContainText(`(${numberOfGroups})`, { timeout: 30000 });
    }

    async clickAddExerciseGroup() {
        const createButton = this.page.locator('#create-new-group');
        await createButton.waitFor({ state: 'visible', timeout: 30000 });
        await createButton.click();
    }

    async clickAddTextExercise(groupID: number) {
        await this.selectExerciseTypeCard(groupID, 'create', 'text');
    }

    async clickAddModelingExercise(groupID: number) {
        await this.selectExerciseTypeCard(groupID, 'create', 'modeling');
    }

    async clickAddQuizExercise(groupID: number) {
        await this.selectExerciseTypeCard(groupID, 'create', 'quiz');
    }

    async clickAddProgrammingExercise(groupID: number) {
        await this.selectExerciseTypeCard(groupID, 'create', 'programming');
    }

    async clickEditExercise(groupID: number, exerciseID: number) {
        await this.clickRowAction(groupID, exerciseID, 'edit');
    }

    async visitPageViaUrl(courseId: number, examId: number) {
        // Reload once if the exercise-groups lazy chunk fails to render `#exercise-groups-title`
        // within 30s under multi-node CI load (same pattern as other navigateToXxxPage
        // helpers in this codebase).
        const url = `/course-management/${courseId}/exams/${examId}/exercise-groups`;
        const marker = this.page.locator('#exercise-groups-title');
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
