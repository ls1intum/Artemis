import fs from 'fs';
import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor, studentOne, studentTwo } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examManagement.id } as any;
const unknownLogin = 'no_such_user_for_the_import';

/**
 * Registering students from a CSV file and exporting them again: the import reports how many students it found and registers exactly
 * those with the room and seat given in the file, and the export hands out the same students with their room and seat.
 */
test.describe('Exam students CSV import and export', { tag: '@fast' }, () => {
    let exam: Exam;

    test.beforeEach('Create an exam without students', async ({ login, examAPIRequests }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({ course });
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
    });

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('Students are registered from a CSV file with their room and seat, and exported again', async ({ page }) => {
        const csv = ['login,room,seat', `${studentOne.username},HS1,A1`, `${studentTwo.username},HS1,A2`, `${unknownLogin},HS1,A3`].join('\n');

        // Import: the file is read, the report says two of three were found, and only those two are registered.
        await page.getByRole('button', { name: 'Students' }).click();
        await page.locator('[data-testid="exam-students-menu-item"]', { hasText: 'Import from CSV' }).last().click();
        const dialog = page.getByRole('dialog');
        await dialog.locator('#importCSV').setInputFiles({ name: 'students.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
        // The file was read: all three entries are offered for the import, whether the users exist or not.
        for (const login of [studentOne.username, studentTwo.username, unknownLogin]) {
            await expect(dialog.locator('tbody tr', { hasText: login })).toHaveCount(1);
        }
        await expect(dialog).toContainText(/Number of users:\s*3/);
        const imported = page.waitForResponse((response) => /\/exams\/\d+\/students$/.test(response.url()) && response.request().method() === 'POST');
        await dialog.locator('#import').click();
        expect((await imported).status()).toBe(200);
        await expect(dialog).toContainText(/Imported:\s*2/);
        await expect(dialog).toContainText(/Not found:\s*1/);
        await expect(dialog).toContainText(unknownLogin);
        await dialog.locator('#finish-button').click();

        const rows = page.locator('p-table tbody tr');
        await expect(rows).toHaveCount(2);
        await expect(rows.filter({ hasText: studentOne.username })).toContainText('HS1');
        await expect(rows.filter({ hasText: studentOne.username })).toContainText('A1');
        await expect(rows.filter({ hasText: studentTwo.username })).toContainText('A2');
        await expect(rows.filter({ hasText: unknownLogin })).toHaveCount(0);

        // Export: the file holds the same two students with the room and seat that were imported.
        await page.getByRole('button', { name: 'Students' }).click();
        await page.locator('[data-testid="exam-students-menu-item"]', { hasText: 'Export to CSV' }).last().click();
        const exportDialog = page.getByRole('dialog');
        const download = page.waitForEvent('download');
        await exportDialog.getByRole('button', { name: 'Export', exact: true }).click();
        const exported = fs.readFileSync(await (await download).path(), 'utf-8');
        const lines = exported.split(/\r?\n/).filter(Boolean);
        expect(lines, `exported file:\n${exported}`).toHaveLength(3);
        expect(lines[0].toLowerCase()).toContain('login');
        const studentOneLine = lines.find((line) => line.includes(studentOne.username))!;
        const studentTwoLine = lines.find((line) => line.includes(studentTwo.username))!;
        expect(studentOneLine).toContain('HS1');
        expect(studentOneLine).toContain('A1');
        expect(studentTwoLine).toContain('A2');
        expect(exported).not.toContain(unknownLogin);
    });
});
