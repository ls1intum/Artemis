import { test } from '../../support/fixtures';
import { expect } from '@playwright/test';
import type { Locator, Page } from '@playwright/test';
import dayjs from 'dayjs';
import { fillDateTimePicker } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { admin } from '../../support/users';

/**
 * Behavioral E2E coverage for the shared `jhi-date-time-picker` (the PrimeNG `p-datepicker` wrapper
 * introduced when the legacy owl picker was removed). These tests exercise the picker directly in two
 * representative forms — a DEFAULT (date+time) picker on the course-creation page and a CALENDAR
 * (date-only) picker on the competency-creation page — asserting typing, calendar selection,
 * invalid-input handling, validity recovery and clearing.
 */

function pickerInput(page: Page, pickerId: string): Locator {
    return page.locator(`jhi-date-time-picker#${pickerId} input#date-input-field`);
}

function pickerWrapper(page: Page, pickerId: string): Locator {
    return page.locator(`jhi-date-time-picker#${pickerId}`);
}

/** Clicks the in-input calendar icon and waits for the overlay panel to appear. */
async function openPanel(page: Page, pickerId: string): Promise<Locator> {
    await pickerWrapper(page, pickerId).locator('.p-datepicker-input-icon').click();
    const panel = page.locator('.p-datepicker-panel');
    await expect(panel).toBeVisible();
    return panel;
}

/** Closes the overlay panel if it is open. */
async function closePanel(page: Page): Promise<void> {
    if (await page.locator('.p-datepicker-panel').isVisible()) {
        await page.keyboard.press('Escape');
        await expect(page.locator('.p-datepicker-panel')).toBeHidden();
    }
}

/** Types unparseable text into the picker the way a user would (replacing any existing value). */
async function typeRawText(input: Locator, text: string): Promise<void> {
    await input.click();
    await input.press('ControlOrMeta+a');
    await input.pressSequentially(text);
    await input.press('Tab');
}

/** Clicks a day cell of the current month in the open panel. */
async function clickDay(panel: Locator, day: number): Promise<void> {
    await panel.locator(`.p-datepicker-calendar td:not(.p-datepicker-other-month) span.p-datepicker-day:text-is("${day}")`).click();
}

test.describe('Date-time picker', { tag: '@fast' }, () => {
    test('DEFAULT (date+time) picker supports typing, calendar selection, validation recovery and clearing', async ({ login, page }) => {
        await login(admin, '/course-management/new');

        const pickerId = 'field_startDate';
        const input = pickerInput(page, pickerId);
        const wrapper = pickerWrapper(page, pickerId);
        await expect(input).toBeVisible();
        // DEFAULT mode shows date + time
        await expect(input).toHaveAttribute('placeholder', 'dd.mm.yyyy hh:mm');

        // 1) Typing a valid date+time is accepted and not flagged invalid
        const typedDate = dayjs().add(1, 'year').month(11).date(20).hour(8).minute(30); // 20.12.<next year> 08:30
        await fillDateTimePicker(input, typedDate, 'DD.MM.YYYY HH:mm');
        await expect(input).toHaveValue(typedDate.format('DD.MM.YYYY HH:mm'));
        await expect(wrapper.locator('.p-datepicker')).not.toHaveClass(/p-invalid/);

        // 2) The calendar overlay reflects the typed value: correct month/year, selected day, a time picker, Monday-first week
        const panel = await openPanel(page, pickerId);
        await expect(panel.locator('.p-datepicker-title')).toContainText(typedDate.format('MMMM'));
        await expect(panel.locator('.p-datepicker-title')).toContainText(typedDate.format('YYYY'));
        await expect(panel.locator('.p-datepicker-day-selected')).toHaveText(String(typedDate.date()));
        await expect(panel.locator('.p-datepicker-time-picker')).toBeVisible();
        await expect(panel.locator('.p-datepicker-weekday').first()).toHaveText('Mo');

        // 3) Picking a different day updates the value while preserving the entered time
        await clickDay(panel, 15);
        await expect(input).toHaveValue(typedDate.date(15).format('DD.MM.YYYY HH:mm'));
        await closePanel(page);

        // 4) Unparseable text is flagged invalid, kept verbatim (not silently coerced into a date)
        await typeRawText(input, 'notadate');
        await expect(input).toHaveValue('notadate');
        await expect(wrapper.locator('.p-datepicker')).toHaveClass(/p-invalid/);
        await expect(wrapper.locator('[data-testid="date-picker-validation-message"]').first()).toBeVisible();

        // 5) Recovery: typing a valid date again clears the invalid state
        const recoveredDate = dayjs().add(1, 'year').month(5).date(1).hour(9).minute(45); // 01.06.<next year> 09:45
        await fillDateTimePicker(input, recoveredDate, 'DD.MM.YYYY HH:mm');
        await expect(input).toHaveValue(recoveredDate.format('DD.MM.YYYY HH:mm'));
        await expect(wrapper.locator('.p-datepicker')).not.toHaveClass(/p-invalid/);
        await expect(wrapper.locator('[data-testid="date-picker-validation-message"]')).toHaveCount(0);

        // 6) The clear (×) button empties the field
        await wrapper.locator('.p-datepicker-clear-icon').click();
        await expect(input).toHaveValue('');
    });

    test('CALENDAR (date-only) picker has no time component and supports typing, calendar selection and clearing', async ({ login, page }) => {
        await login(admin, `/course-management/${SEED_COURSES.atlas1.id}/competency-management/create`);

        const pickerId = 'softDueDate';
        const input = pickerInput(page, pickerId);
        const wrapper = pickerWrapper(page, pickerId);
        await expect(input).toBeVisible();
        // CALENDAR mode: date only, no time portion in the placeholder
        await expect(input).toHaveAttribute('placeholder', 'dd.mm.yyyy');

        // 1) Typing a date-only value is accepted
        const typedDate = dayjs().add(1, 'year').month(8).date(20); // 20.09.<next year>
        await fillDateTimePicker(input, typedDate, 'DD.MM.YYYY');
        await expect(input).toHaveValue(typedDate.format('DD.MM.YYYY'));

        // 2) The overlay is calendar-only (no time picker) and reflects the typed value
        const panel = await openPanel(page, pickerId);
        await expect(panel.locator('.p-datepicker-title')).toContainText(typedDate.format('MMMM'));
        await expect(panel.locator('.p-datepicker-title')).toContainText(typedDate.format('YYYY'));
        await expect(panel.locator('.p-datepicker-day-selected')).toHaveText(String(typedDate.date()));
        await expect(panel.locator('.p-datepicker-time-picker')).toHaveCount(0);

        // 3) Picking a different day updates the value
        await clickDay(panel, 10);
        await expect(input).toHaveValue(typedDate.date(10).format('DD.MM.YYYY'));
        await closePanel(page);

        // 4) Unparseable text is flagged invalid and kept verbatim
        await typeRawText(input, 'xx.yy.zzzz');
        await expect(input).toHaveValue('xx.yy.zzzz');
        await expect(wrapper.locator('[data-testid="date-picker-validation-message"]').first()).toBeVisible();

        // 5) Restoring a valid value and clearing it empties the field
        await fillDateTimePicker(input, typedDate, 'DD.MM.YYYY');
        await expect(input).toHaveValue(typedDate.format('DD.MM.YYYY'));
        await wrapper.locator('.p-datepicker-clear-icon').click();
        await expect(input).toHaveValue('');
    });
});
