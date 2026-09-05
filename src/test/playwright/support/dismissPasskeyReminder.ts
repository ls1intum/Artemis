import { errors, type Page } from '@playwright/test';

export async function dismissPasskeyReminderIfPresent(page: Page): Promise<void> {
    const reminderButton = page.getByRole('button', { name: 'Remind Me in 30 Days' });
    try {
        await reminderButton.waitFor({ state: 'visible', timeout: 2000 });
    } catch (error) {
        if (error instanceof errors.TimeoutError) {
            return;
        }
        throw error;
    }
    await reminderButton.click();
    await reminderButton.waitFor({ state: 'hidden' });
}
