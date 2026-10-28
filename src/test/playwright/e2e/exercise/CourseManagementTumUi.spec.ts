import { Locator, Page, expect } from '@playwright/test';
import { Course } from 'app/course/shared/entities/course.model';
import { test } from '../../support/fixtures';
import { expectNoHorizontalOverflow, expectNoHorizontalScrollAround, expectWithinViewport, forEachViewport } from '../../support/layout';
import { admin, UserCredentials, UserRole } from '../../support/users';
import { generateUUID } from '../../support/utils';

/** Checks browser layout and stacking that cannot be verified by the table's unit tests. */
test.describe('Course management TUM UI tables', { tag: '@slow' }, () => {
    async function attachScreenshot(page: Page, name: string): Promise<void> {
        await page.evaluate(() => document.fonts.ready);
        const path = test.info().outputPath(`${name}.png`);
        await page.screenshot({ path, fullPage: true, animations: 'disabled' });
        await test.info().attach(name, { path, contentType: 'image/png' });
    }

    async function expectHeaderAboveRows(header: Locator): Promise<void> {
        await expect
            .poll(() =>
                header.evaluate((element) => {
                    const bounds = element.getBoundingClientRect();
                    const hit = document.elementFromPoint(Math.min(bounds.right - 8, window.innerWidth - 8), bounds.top + bounds.height / 2);
                    return hit !== null && element.contains(hit);
                }),
            )
            .toBe(true);
    }

    async function verifyScrolling(page: Page, viewport: Locator, narrow: boolean): Promise<void> {
        await page.mouse.move(0, 0);
        const table = viewport.getByRole('table');
        const actionsHeader = table.getByRole('columnheader').last();
        await viewport.evaluate((element) => {
            element.scrollTop = 0;
            element.scrollLeft = 0;
        });

        await expectWithinViewport(viewport, ['top', 'right', 'bottom', 'left'], { name: 'exam students table viewport', tolerance: 1 });
        await expectNoHorizontalOverflow(page, [], { name: 'exam students page' });
        await expectNoHorizontalScrollAround(viewport, { name: 'exam students table viewport' });
        await expect.poll(() => viewport.evaluate((element) => element.scrollHeight - element.clientHeight)).toBeGreaterThan(0);

        if (narrow) {
            expect(await viewport.evaluate((element) => element.scrollWidth - element.clientWidth)).toBeGreaterThan(50);
        }
        const headerRight = await actionsHeader.evaluate((element) => element.getBoundingClientRect().right);
        await viewport.evaluate((element) => {
            element.scrollTop = Math.min(300, element.scrollHeight - element.clientHeight);
            element.scrollLeft = element.scrollWidth - element.clientWidth;
        });
        await expect.poll(() => viewport.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
        if (narrow) {
            await expect.poll(() => viewport.evaluate((element) => element.scrollLeft)).toBeGreaterThan(0);
        }
        await expect.poll(() => actionsHeader.evaluate((element, initialRight) => Math.abs(element.getBoundingClientRect().right - initialRight), headerRight)).toBeLessThan(2);
        await expect
            .poll(() =>
                viewport.evaluate((element) => {
                    const header = element.querySelector('thead th:last-child');
                    return header ? Math.abs(header.getBoundingClientRect().top - element.getBoundingClientRect().top) : Number.POSITIVE_INFINITY;
                }),
            )
            .toBeLessThan(2);
        await expectHeaderAboveRows(actionsHeader);
        await expectNoHorizontalOverflow(page, [], { name: 'scrolled exam students page' });
    }

    test('keeps exam students inside a scrollable viewport with a frozen action header in light, dark and narrow views', async ({
        page,
        login,
        courseManagementAPIRequests,
        examAPIRequests,
        userManagementAPIRequests,
    }) => {
        let course: Course | undefined;
        const students: UserCredentials[] = [];
        const token = generateUUID();
        await login(admin);
        try {
            course = await courseManagementAPIRequests.createCourse({ courseName: `TUM UI table ${token}`, allowCommunication: false, allowMessaging: false });
            expect(course.id).toBeDefined();
            for (let index = 0; index < 24; index++) {
                const student = { username: `tumuitable${token}${String(index).padStart(2, '0')}`, password: `Table-${token}-${index}!` };
                const response = await userManagementAPIRequests.createUser(student.username, student.password, UserRole.Student);
                expect(response.status(), `creating table student ${student.username}`).toBe(201);
                students.push(student);
                await courseManagementAPIRequests.addStudentToCourse(course, student);
            }
            const exam = await examAPIRequests.createExam({ course, title: `TUM UI students ${token}` });
            expect(exam.id).toBeDefined();
            await examAPIRequests.registerAllCourseStudentsForExam(exam);

            // Exercise the real theme service through its persisted system-preference mode.
            await page.addInitScript(() => localStorage.removeItem('artemisapp.theme.preference'));
            await page.emulateMedia({ colorScheme: 'light' });
            await page.setViewportSize({ width: 1280, height: 800 });
            await page.goto(`/course-management/${course.id}/exams/${exam.id}/students`);
            const viewport = page.getByTestId('table-viewport');
            const table = viewport.getByRole('table');
            const rows = table.getByRole('rowgroup').last().getByRole('row');
            await expect(table).toBeVisible();
            await page.getByRole('combobox', { name: 'Rows per page' }).selectOption({ label: '50' });
            await expect(rows).toHaveCount(students.length);

            for (const theme of ['light', 'dark'] as const) {
                await page.emulateMedia({ colorScheme: theme });
                await forEachViewport(page, async ({ width, height }) => {
                    const name = `${theme}-${width}x${height}`;
                    await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
                    if (theme === 'dark') {
                        await page.waitForFunction(() => (document.getElementById('artemis-theme-override') as HTMLLinkElement | null)?.sheet != null);
                    }
                    await verifyScrolling(page, viewport, width < 600);
                    if (width === 1280 || width === 480) {
                        await attachScreenshot(page, `exam-students-${name}`);
                    }

                    const targetStudent = students[17];
                    const search = page.getByTestId('search-filter').getByRole('searchbox');
                    await search.fill(targetStudent.username);
                    await expect(rows).toHaveCount(1);
                    await expect(rows.first()).toContainText(targetStudent.username);
                    if (theme === 'light' && width === 1280) {
                        const remove = rows.first().getByRole('button', { name: 'Remove', exact: true });
                        await remove.click();
                        const dialog = page.getByRole('dialog');
                        await expect(dialog).toBeVisible();
                        await expect(dialog).toContainText(targetStudent.username);
                        await attachScreenshot(page, 'exam-students-remove-dialog');
                        await page.keyboard.press('Escape');
                        await expect(dialog).toBeHidden();
                        await expect(remove).toBeFocused();
                    }
                    await search.fill('');
                    await expect(rows).toHaveCount(students.length);
                });
            }
        } finally {
            try {
                await courseManagementAPIRequests.deleteCourse(course, admin);
            } finally {
                const deletions = await Promise.allSettled(
                    students.map(async (student) => {
                        const response = await userManagementAPIRequests.deleteUser(student.username);
                        expect([200, 404], `cleaning up table student ${student.username}`).toContain(response.status());
                    }),
                );
                const failures = deletions.filter((result) => result.status === 'rejected');
                expect(failures, 'Failed to clean up table students').toHaveLength(0);
            }
        }
    });
});
