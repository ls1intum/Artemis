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

    /**
     * Checks that the event of the dialog happened between the two given moments, to the minute. The displayed minute may also be the one after
     * the later moment, because the server stamps the event a little later than the test notes the time.
     */
    async checkDialogTime(from: Dayjs, to: Dayjs = from) {
        const modalDialog = this.getModalDialogContent();
        await expect(modalDialog).toBeVisible({ timeout: 30000 });
        const timeFormat = 'MMM D, YYYY HH:mm';
        const accepted: string[] = [];
        for (let minute = from.startOf('minute'); !minute.isAfter(to.add(1, 'minute')); minute = minute.add(1, 'minute')) {
            accepted.push(minute.format(timeFormat));
        }
        await expect(modalDialog.getByTestId('live-event-date').getByText(new RegExp(`(${accepted.join('|')})`))).toBeVisible({ timeout: 10000 });
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
