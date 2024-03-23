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
        await this.page.locator(`#group-${groupID} .add-text-exercise`).click();
    }

    async clickAddModelingExercise(groupID: number) {
        await this.page.locator(`#group-${groupID} .add-modeling-exercise`).click();
    }

    async clickAddQuizExercise(groupID: number) {
        await this.page.locator(`#group-${groupID} .add-quiz-exercise`).click();
    }

    async clickAddProgrammingExercise(groupID: number) {
        await this.page.locator(`#group-${groupID} .add-programming-exercise`).click();
    }

    async visitPageViaUrl(courseId: number, examId: number) {
        await this.page.goto(`course-management/${courseId}/exams/${examId}/exercise-groups`);
    }

    async shouldContainExerciseWithTitle(groupID: number, exerciseTitle: string) {
        const exerciseElement = this.page.locator(`#group-${groupID} #exercises`, { hasText: exerciseTitle });
        await exerciseElement.scrollIntoViewIfNeeded();
        await expect(exerciseElement).toBeVisible();
    }
}
