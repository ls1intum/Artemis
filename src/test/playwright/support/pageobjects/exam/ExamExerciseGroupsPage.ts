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

    async clickEditGroupForTestExam() {
        await this.page.getByRole('link', { name: 'Edit' }).click();
    }

    async clickDeleteGroup(groupID: number, groupName: string) {
        await this.page.click(`#group-${groupID} .delete-group`);
        const deleteButton = this.page.locator('#delete');
        await expect(deleteButton).toBeDisabled();
        await this.page.fill('#confirm-entity-name', groupName);
        await expect(deleteButton).toBeEnabled();
        await deleteButton.click();
    }

    async shouldShowNumberOfExerciseGroups(numberOfGroups: number) {
        await expect(this.page.locator('#number-groups')).toContainText(numberOfGroups.toString());
    }

    async clickAddExerciseGroup() {
        await this.page.locator('#create-new-group').click();
    }

    async clickAddTextExercise(groupID: number) {
        const addButton = this.page.locator(`#group-${groupID} .add-text-exercise`);
        await addButton.waitFor({ state: 'visible', timeout: 30000 });
        await addButton.click();
    }

    async clickAddModelingExercise(groupID: number) {
        const addButton = this.page.locator(`#group-${groupID} .add-modeling-exercise`);
        await addButton.waitFor({ state: 'visible', timeout: 30000 });
        await addButton.click();
    }

    async clickAddQuizExercise(groupID: number) {
        const addButton = this.page.locator(`#group-${groupID} .add-quiz-exercise`);
        await addButton.waitFor({ state: 'visible', timeout: 30000 });
        await addButton.click();
    }

    async clickAddProgrammingExercise(groupID: number) {
        const addButton = this.page.locator(`#group-${groupID} .add-programming-exercise`);
        await addButton.waitFor({ state: 'visible', timeout: 30000 });
        await addButton.click();
    }

    async clickEditExercise(groupID: number, exerciseID: number) {
        await this.page.locator(`#group-${groupID} #exercise-${exerciseID}`).locator('.btn', { hasText: 'Edit' }).click();
    }

    async visitPageViaUrl(courseId: number, examId: number) {
        await this.page.goto(`course-management/${courseId}/exams/${examId}/exercise-groups`);
    }

    async shouldContainExerciseWithTitle(groupID: number, exerciseTitle: string) {
        // Wait for the exercise groups page to fully load
        await this.page.waitForLoadState('networkidle');
        const exerciseElement = this.page.locator(`#group-${groupID} #exercises`, { hasText: exerciseTitle });
        // Wait for the element to be attached to DOM first, with a longer timeout
        await exerciseElement.waitFor({ state: 'attached', timeout: 30000 });
        await exerciseElement.scrollIntoViewIfNeeded();
        await expect(exerciseElement).toBeVisible({ timeout: 10000 });
    }
}
