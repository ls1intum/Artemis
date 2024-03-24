import { Page, expect } from '@playwright/test';

/**
 * A class which encapsulates UI selectors and actions for the exam details page.
 */
export class ExamDetailsPage {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Deletes this exam.
     * @param examTitle the exam title to confirm the deletion
     */
    async deleteExam(examTitle: string) {
        const deleteButton = this.page.locator('#delete');
        await this.page.locator('#exam-delete').click();
        await expect(deleteButton).toBeDisabled();
        await this.page.locator('#confirm-entity-name').fill(examTitle);
        await expect(deleteButton).not.toBeDisabled();
        await deleteButton.click();
    }
}
