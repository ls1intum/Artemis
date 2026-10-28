import { readFile } from 'node:fs/promises';
import { expect } from '@playwright/test';
import { Course } from 'app/course/shared/entities/course.model';
import { TutorialGroupExportData } from 'app/openapi/model/tutorial-group-export-data';
import { test } from '../../support/fixtures';
import { admin, tutor } from '../../support/users';
import { parseCsv } from '../../support/csv';

test.describe('Tutorial group import and export dialogs', { tag: '@fast' }, () => {
    let course: Course | undefined;

    test.beforeEach(async ({ login, page, courseManagementAPIRequests, userManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ timeZone: 'Europe/Berlin' });
        await courseManagementAPIRequests.createTutorialGroupsConfiguration(course);
        const tutorResponse = await userManagementAPIRequests.getUser(tutor.username);
        expect(tutorResponse.ok()).toBeTruthy();
        await courseManagementAPIRequests.createTutorialGroup(course, 'Existing Group', (await tutorResponse.json()).id);
        await page.goto(`/course-management/${course.id}/tutorial-groups`);
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
        course = undefined;
    });

    test('parses only confirmed rows, imports the selected group and refreshes after the completion warning', async ({ page }) => {
        const trigger = page.getByTestId('tutorial-groups-import-open');
        await trigger.click();
        const dialog = page.getByRole('dialog', { name: 'Import Tutorial Group Registrations', exact: true });
        const parse = dialog.getByTestId('tutorial-groups-import-parse');
        await expect(parse).toBeDisabled();
        await dialog.getByTestId('tutorial-groups-import-file').setInputFiles({
            name: 'groups.csv',
            mimeType: 'text/csv',
            buffer: Buffer.from('Title,Place\nConfirmed Group,confirmed\nWaiting Group,waiting'),
        });
        await dialog.getByTestId('tutorial-groups-import-fixed-place').getByRole('checkbox').check();
        await expect(parse).toBeDisabled();
        await dialog.getByTestId('tutorial-groups-import-status-header').fill('Place');
        await dialog.getByTestId('tutorial-groups-import-fixed-value').fill('confirmed');
        await parse.click();
        const preview = dialog.getByTestId('tutorial-groups-import-preview');
        await expect(preview.locator('tbody tr')).toHaveCount(1);
        await expect(preview).toContainText('Confirmed Group');
        await expect(preview).not.toContainText('Waiting Group');
        const imported = page.waitForResponse((response) => response.url().endsWith(`/courses/${course!.id}/tutorial-groups/import`) && response.request().method() === 'POST');
        await dialog.getByTestId('tutorial-groups-import-submit').click();
        expect((await imported).ok()).toBeTruthy();
        await expect(dialog).toContainText('Imported: 1');
        await dialog.getByTestId('tutorial-groups-import-filter').getByRole('button', { name: 'Imported registrations', exact: true }).click();
        await expect(preview.locator('tbody tr')).toHaveCount(1);
        await dialog.getByTestId('tutorial-groups-import-finish').click();
        const warning = page.getByRole('dialog', { name: 'Warning', exact: true });
        await expect(warning).toContainText('Artemis has finished importing');
        await warning.getByTestId('tutorial-groups-import-warning-close').click();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        await expect(page.getByRole('row', { name: /Confirmed Group/ })).toBeVisible();
        await expect(trigger).toBeFocused();
        await expect(page.getByRole('row', { name: /Waiting Group/ })).toHaveCount(0);
    });

    test('exports chosen fields as CSV and JSON, resets selection and restores focus on Escape', async ({ page }) => {
        const trigger = page.getByTestId('tutorial-groups-export-open');
        await trigger.click();
        let dialog = page.getByRole('dialog', { name: 'Select fields for CSV Export', exact: true });
        await expect(dialog.getByTestId('tutorial-groups-export-csv')).toBeDisabled();
        await dialog.getByTestId('tutorial-groups-export-select-all').getByRole('checkbox').check();
        await expect(dialog.getByTestId('tutorial-groups-export-field-0').getByRole('checkbox')).toBeChecked();
        await dialog.getByTestId('tutorial-groups-export-select-all').getByRole('checkbox').uncheck();
        await dialog.getByTestId('tutorial-groups-export-field-1').getByRole('checkbox').check();
        const csvDownload = page.waitForEvent('download');
        await dialog.getByTestId('tutorial-groups-export-csv').click();
        const csvFile = await csvDownload;
        expect(csvFile.suggestedFilename()).toBe('tutorial-groups.csv');
        const [header, ...rows] = parseCsv(await readFile(await csvFile.path(), 'utf8'));
        expect(header).toEqual(['Title']);
        expect(rows).toEqual([['Existing Group']]);

        await trigger.click();
        dialog = page.getByRole('dialog', { name: 'Select fields for CSV Export', exact: true });
        await expect(dialog.getByTestId('tutorial-groups-export-json')).toBeDisabled();
        await dialog.getByTestId('tutorial-groups-export-field-1').getByRole('checkbox').check();
        const jsonDownload = page.waitForEvent('download');
        await dialog.getByTestId('tutorial-groups-export-json').click();
        const jsonFile = await jsonDownload;
        expect(jsonFile.suggestedFilename()).toBe('tutorial_groups.json');
        const groups: TutorialGroupExportData[] = JSON.parse(await readFile(await jsonFile.path(), 'utf8'));
        expect(groups).toHaveLength(1);
        expect(groups[0].title).toBe('Existing Group');
        expect(groups[0].capacity).toBeUndefined();

        await trigger.click();
        await page.getByRole('dialog').press('Escape');
        await expect(page.getByRole('dialog')).toHaveCount(0);
        await expect(trigger).toBeFocused();
    });
});
