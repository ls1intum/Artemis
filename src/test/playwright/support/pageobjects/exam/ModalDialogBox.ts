import { Page, expect } from '@playwright/test';
import { Dayjs } from 'dayjs';

export class ModalDialogBox {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getModalDialogContent() {
        return this.page.locator('.modal-content');
    }

    async checkDialogTime(dialogTime: Dayjs) {
        const modalDialog = this.getModalDialogContent();
        const timeFormat = 'MMM D, YYYY HH:mm';
        const dialogTimeFormatted = dialogTime.format(timeFormat);
        const dialogTimeAfterMinuteFormatted = dialogTime.add(1, 'minute').format(timeFormat);
        await expect(modalDialog.locator('.date').getByText(new RegExp(`(${dialogTimeFormatted}|${dialogTimeAfterMinuteFormatted})`))).toBeVisible();
    }

    async checkDialogMessage(message: string) {
        await expect(this.getModalDialogContent().locator('.content').getByText(message)).toBeVisible();
    }

    async checkDialogAuthor(authorUsername: string) {
        await expect(this.getModalDialogContent().locator('.author').getByText(authorUsername)).toBeVisible();
    }

    async checkExamTimeChangeDialog(previousWorkingTime: string, newWorkingTime: string) {
        const timeChangeDialog = this.getModalDialogContent();
        await expect(timeChangeDialog.getByTestId('old-time').getByText(previousWorkingTime)).toBeVisible();
        await expect(timeChangeDialog.getByTestId('new-time').getByText(newWorkingTime)).toBeVisible();
    }

    async closeDialog() {
        await this.getModalDialogContent().locator('button').click({ force: true });
    }
}
