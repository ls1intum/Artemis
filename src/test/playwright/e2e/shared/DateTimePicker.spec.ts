import { test } from '../../support/fixtures';
import { expect } from '@playwright/test';
import type { Locator, Page } from '@playwright/test';
import dayjs, { Dayjs } from 'dayjs';
import { fillDateTimePicker } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { admin } from '../../support/users';

/**
 * Behavioral E2E coverage for the TUM AET UI date picker (`tumaet-ui-date-picker`). These tests exercise the picker
 * directly in two representative forms — a date+time picker on the course-creation page and a date-only picker on the
 * competency-creation page — asserting typing, calendar selection, invalid-input handling, validity recovery and
 * clearing.
 */

/** The picker element a form gave an id of its own. */
function picker(page: Page, pickerId: string): Locator {
    return page.locator(`tumaet-ui-date-picker#${pickerId}`);
}

/** The text input of the picker, which a form labels with an id of its own. */
function pickerInput(page: Page, pickerId: string, inputId: string): Locator {
    return picker(page, pickerId).locator(`input#${inputId}`);
}

/** The open calendar dialog. The picker opens it in the overlay layer, outside its own element. */
function calendarDialog(page: Page): Locator {
    return page.locator('[role="dialog"]').filter({ has: page.locator('tumaet-ui-calendar') });
}

/** Clicks the calendar icon of the picker and waits for the dialog to appear. */
async function openDialog(page: Page, pickerId: string): Promise<Locator> {
    await picker(page, pickerId).locator('button[aria-haspopup="dialog"]').click();
    const dialog = calendarDialog(page);
    await expect(dialog).toBeVisible();
    return dialog;
}

/** Closes the dialog if it is open. */
async function closeDialog(page: Page): Promise<void> {
    const dialog = calendarDialog(page);
    if (await dialog.isVisible()) {
        await page.keyboard.press('Escape');
        await expect(dialog).toBeHidden();
    }
}

/** Types unparseable text into the picker the way a user would (replacing any existing value). */
async function typeRawText(input: Locator, text: string): Promise<void> {
    await input.click();
    await input.press('ControlOrMeta+a');
    await input.pressSequentially(text);
    await input.press('Tab');
}

/** The button of one specific date in the open dialog. Every day button carries its own ISO date. */
function dayButton(dialog: Locator, date: Dayjs): Locator {
    return dialog.locator(`button[data-date="${date.format('YYYY-MM-DD')}"]`);
}

/** Asserts that the dialog shows the given date as the selected one. */
async function expectDaySelected(dialog: Locator, date: Dayjs): Promise<void> {
    await expect(dialog.locator('td[role="gridcell"][aria-selected="true"]')).toHaveCount(1);
    await expect(dialog.locator('td[role="gridcell"][aria-selected="true"]').locator('button')).toHaveAttribute('data-date', date.format('YYYY-MM-DD'));
}

/** The hour and minute fields of the dialog's time steppers. */
function timeFields(dialog: Locator): Locator {
    return dialog.locator('input[inputmode="numeric"]');
}

test.describe('Date-time picker', { tag: '@fast' }, () => {
    test('date+time picker supports typing, calendar selection, validation recovery and clearing', async ({ login, page }) => {
        await login(admin, '/course-management/new');

        const pickerId = 'field_startDate';
        const input = pickerInput(page, pickerId, 'field_startDate_input');
        await expect(input).toBeVisible();
        await expect(input).toHaveAttribute('placeholder', 'DD.MM.YYYY HH:mm');

        // 1) Typing a valid date+time is accepted and not flagged invalid
        const typedDate = dayjs().add(1, 'year').month(11).date(20).hour(8).minute(30); // 20.12.<next year> 08:30
        await fillDateTimePicker(input, typedDate, 'DD.MM.YYYY HH:mm');
        await expect(input).toHaveValue(typedDate.format('DD.MM.YYYY HH:mm'));
        await expect(input).toHaveAttribute('aria-invalid', 'false');

        // 2) The calendar dialog reflects the typed value: correct month/year, selected day, time steppers, Monday-first week
        const dialog = await openDialog(page, pickerId);
        await expect(dialog.locator('[role="grid"]')).toHaveAttribute('aria-label', new RegExp(`${typedDate.format('MMMM')}.*${typedDate.format('YYYY')}`));
        await expectDaySelected(dialog, typedDate);
        await expect(timeFields(dialog)).toHaveCount(2);
        await expect(timeFields(dialog).first()).toHaveValue(typedDate.format('HH'));
        await expect(dialog.locator('th[scope="col"]').first()).toHaveAttribute('aria-label', 'Monday');

        // 3) Picking a different day updates the value while preserving the entered time
        await dayButton(dialog, typedDate.date(15)).click();
        await expect(input).toHaveValue(typedDate.date(15).format('DD.MM.YYYY HH:mm'));
        await closeDialog(page);

        // 4) Unparseable text is flagged invalid and kept verbatim (not silently coerced into a date)
        await typeRawText(input, 'notadate');
        await expect(input).toHaveValue('notadate');
        await expect(input).toHaveAttribute('aria-invalid', 'true');
        await expect(page.locator('#field_startDate_input-error')).toBeVisible();

        // 5) Recovery: typing a valid date again clears the invalid state
        const recoveredDate = dayjs().add(1, 'year').month(5).date(1).hour(9).minute(45); // 01.06.<next year> 09:45
        await fillDateTimePicker(input, recoveredDate, 'DD.MM.YYYY HH:mm');
        await expect(input).toHaveValue(recoveredDate.format('DD.MM.YYYY HH:mm'));
        await expect(input).toHaveAttribute('aria-invalid', 'false');
        await expect(page.locator('#field_startDate_input-error')).toHaveCount(0);

        // 6) The clear (×) button empties the field
        await picker(page, pickerId).getByRole('button', { name: 'Clear', exact: true }).click();
        await expect(input).toHaveValue('');
    });

    test('date-only picker has no time component and supports typing, calendar selection and clearing', async ({ login, page }) => {
        await login(admin, `/course-management/${SEED_COURSES.atlas1.id}/competency-management/create`);

        const pickerId = 'softDueDate';
        const input = pickerInput(page, pickerId, 'softDueDate-input');
        await expect(input).toBeVisible();
        // Date only: no time portion in the placeholder
        await expect(input).toHaveAttribute('placeholder', 'DD.MM.YYYY');

        // 1) Typing a date-only value is accepted
        const typedDate = dayjs().add(1, 'year').month(8).date(20); // 20.09.<next year>
        await fillDateTimePicker(input, typedDate, 'DD.MM.YYYY');
        await expect(input).toHaveValue(typedDate.format('DD.MM.YYYY'));

        // 2) The dialog is calendar-only (no time steppers) and reflects the typed value
        const dialog = await openDialog(page, pickerId);
        await expect(dialog.locator('[role="grid"]')).toHaveAttribute('aria-label', new RegExp(`${typedDate.format('MMMM')}.*${typedDate.format('YYYY')}`));
        await expectDaySelected(dialog, typedDate);
        await expect(timeFields(dialog)).toHaveCount(0);

        // 3) Picking a different day updates the value
        await dayButton(dialog, typedDate.date(10)).click();
        await expect(input).toHaveValue(typedDate.date(10).format('DD.MM.YYYY'));
        await closeDialog(page);

        // 4) Unparseable text is flagged invalid and kept verbatim
        await typeRawText(input, 'xx.yy.zzzz');
        await expect(input).toHaveValue('xx.yy.zzzz');
        await expect(input).toHaveAttribute('aria-invalid', 'true');
        await expect(page.locator('#softDueDate-input-error')).toBeVisible();

        // 5) Restoring a valid value and clearing it empties the field
        await fillDateTimePicker(input, typedDate, 'DD.MM.YYYY');
        await expect(input).toHaveValue(typedDate.format('DD.MM.YYYY'));
        await picker(page, pickerId).getByRole('button', { name: 'Clear', exact: true }).click();
        await expect(input).toHaveValue('');
    });
});
