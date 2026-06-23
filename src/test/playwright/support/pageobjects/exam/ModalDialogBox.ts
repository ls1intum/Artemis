import { Page, expect } from '@playwright/test';
import { Dayjs } from 'dayjs';

export class ModalDialogBox {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getModalDialogContent() {
        // The exam live-events overlay was migrated from NgbModal (.modal-content) to
        // PrimeNG DynamicDialog (.p-dialog-content). Match either so this page object
        // works for any modal-style dialog used by the exam tests.
        return this.page.locator('.p-dialog-content, .modal-content').first();
    }

    async checkDialogTime(dialogTime: Dayjs) {
        const modalDialog = this.getModalDialogContent();
        await expect(modalDialog).toBeVisible({ timeout: 30000 });
        const timeFormat = 'MMM D, YYYY HH:mm';
        const dialogTimeFormatted = dialogTime.format(timeFormat);
        const dialogTimeAfterMinuteFormatted = dialogTime.add(1, 'minute').format(timeFormat);
        await expect(modalDialog.locator('.date').getByText(new RegExp(`(${dialogTimeFormatted}|${dialogTimeAfterMinuteFormatted})`))).toBeVisible({ timeout: 10000 });
    }

    async checkDialogMessage(message: string) {
        await expect(this.getModalDialogContent().locator('.content').getByText(message)).toBeVisible({ timeout: 10000 });
    }

    async checkDialogType(type: string) {
        const modalContent = this.getModalDialogContent();
        // Wait for modal to be visible first
        await expect(modalContent).toBeVisible({ timeout: 30000 });
        await expect(modalContent.locator('.type').getByText(type)).toBeVisible({ timeout: 10000 });
    }

    async checkExamTimeChangeDialog(previousWorkingTime: string, newWorkingTime: string) {
        const timeChangeDialog = this.getModalDialogContent();
        await expect(timeChangeDialog.getByTestId('old-time').getByText(previousWorkingTime)).toBeVisible();
        await expect(timeChangeDialog.getByTestId('new-time').getByText(newWorkingTime)).toBeVisible();
    }

    async closeDialog() {
        await this.getModalDialogContent().locator('button').click({ force: true });
    }

    async pressModalButton(buttonText: string) {
        let buttonLocator = this.getModalDialogContent().locator('button');
        if (buttonText) {
            buttonLocator = buttonLocator.filter({ hasText: buttonText });
        }
        await buttonLocator.click();
    }
}
