import { Page, expect } from '@playwright/test';
import { DELETION_COMPLETION_TIMEOUT } from '../timeouts';

/**
 * Follows the progress dialog that opens after the server accepted the deletion of an exercise, an exam or an exercise group:
 * waits until the background deletion finished, dismisses the result (the dialog has to be dismissed by the user) and fails with the
 * server's reason if the deletion failed.
 * <p>
 * The state comes from `data-status` of the dialog, not from translated text. A deletion that never started (for example a 409) never
 * opens the dialog, which shows up here as a timeout on the dialog instead of a silent pass.
 *
 * @param page the page that started the deletion
 * @param timeout how long the deletion may take
 */
export async function finishDeletionProgress(page: Page, timeout = DELETION_COMPLETION_TIMEOUT): Promise<void> {
    const dialog = page.getByTestId('entity-deletion-progress');
    await expect(dialog).toHaveAttribute('data-status', /^(COMPLETED|FAILED)$/, { timeout });
    const failed = (await dialog.getAttribute('data-status')) === 'FAILED';
    const reason = failed
        ? ((await dialog
              .getByTestId('entity-deletion-error')
              .textContent({ timeout: 1000 })
              .catch(() => null)) ?? 'no reason reported')
        : undefined;
    await dialog.getByTestId('entity-deletion-dismiss').click();
    await expect(dialog).toBeHidden();
    if (failed) {
        throw new Error(`The deletion failed on the server: ${reason}`);
    }
}
