import { Page, expect } from '@playwright/test';
import { Dayjs } from 'dayjs';

export class ModalDialogBox {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    getModalDialogContent() {
        return this.page.getByRole('dialog').first();
    }

    async checkDialogTime(dialogTime: Dayjs) {
        const modalDialog = this.getModalDialogContent();
        await expect(modalDialog).toBeVisible({ timeout: 30000 });
        const timeFormat = 'MMM D, YYYY HH:mm';
        const dialogTimeFormatted = dialogTime.format(timeFormat);
        const dialogTimeAfterMinuteFormatted = dialogTime.add(1, 'minute').format(timeFormat);
        await expect(modalDialog.getByTestId('live-event-date').getByText(new RegExp(`(${dialogTimeFormatted}|${dialogTimeAfterMinuteFormatted})`))).toBeVisible({
            timeout: 10000,
        });
    }

    async checkDialogMessage(message: string) {
        await expect(this.getModalDialogContent().getByTestId('live-event-content').getByText(message)).toBeVisible({ timeout: 10000 });
    }

    async checkDialogType(type: string) {
        const modalContent = this.getModalDialogContent();
        // Wait for modal to be visible first
        await expect(modalContent).toBeVisible({ timeout: 30000 });
        await expect(modalContent.getByTestId('live-event-type').getByText(type)).toBeVisible({ timeout: 10000 });
    }

    async checkExamTimeChangeDialog(previousWorkingTime: string, newWorkingTime: string) {
        const timeChangeDialog = this.getModalDialogContent();
        await expect(timeChangeDialog.getByTestId('old-time').getByText(previousWorkingTime)).toBeVisible();
        await expect(timeChangeDialog.getByTestId('new-time').getByText(newWorkingTime)).toBeVisible();
    }

    async closeDialog() {
        await this.getModalDialogContent().getByTestId('live-event-action-button').click({ force: true });
    }

    async pressModalButton(buttonText: string) {
        let buttonLocator = this.getModalDialogContent().getByTestId('live-event-action-button');
        if (buttonText) {
            buttonLocator = buttonLocator.filter({ hasText: buttonText });
        }
        await buttonLocator.click();
    }
}
