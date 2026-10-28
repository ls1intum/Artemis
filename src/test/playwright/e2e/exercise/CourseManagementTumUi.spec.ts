import { Locator, Page, expect } from '@playwright/test';
import dayjs from 'dayjs';
import type { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import type { UpdateModelingExerciseDTO } from 'app/modeling/shared/entities/modeling-exercise-update-dto.model';
import { dismissPasskeyReminderIfPresent } from '../../support/dismissPasskeyReminder';
import { Course } from 'app/course/shared/entities/course.model';
import { test } from '../../support/fixtures';
import { expectInside, expectNoOverlap, expectNoHorizontalOverflow, expectNoHorizontalScrollAround, expectWithinViewport, forEachViewport } from '../../support/layout';
import { admin, studentOne, tutor, UserCredentials, UserRole } from '../../support/users';
import { dayjsToString, generateUUID } from '../../support/utils';

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

        await expectWithinViewport(viewport, ['top', 'right', 'bottom', 'left'], { name: 'course students table viewport', tolerance: 1 });
        await expectNoHorizontalOverflow(page, [], { name: 'course students page' });
        await expectNoHorizontalScrollAround(viewport, { name: 'course students table viewport' });
        await expect.poll(() => viewport.evaluate((element) => element.scrollHeight - element.clientHeight)).toBeGreaterThan(0);

        if (narrow) {
            expect(await viewport.evaluate((element) => element.scrollWidth - element.clientWidth)).toBeGreaterThan(50);
        }
        await viewport.evaluate((element) => {
            element.scrollTop = Math.min(300, element.scrollHeight - element.clientHeight);
            element.scrollLeft = element.scrollWidth - element.clientWidth;
        });
        await expect.poll(() => viewport.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
        if (narrow) {
            await expect.poll(() => viewport.evaluate((element) => element.scrollLeft)).toBeGreaterThan(0);
        }
        await expect
            .poll(() =>
                viewport.evaluate((element) => {
                    const header = element.querySelector('thead th:last-child');
                    return header ? Math.abs(header.getBoundingClientRect().top - element.getBoundingClientRect().top) : Number.POSITIVE_INFINITY;
                }),
            )
            .toBeLessThan(2);
        await expectHeaderAboveRows(actionsHeader);
        await expectNoHorizontalOverflow(page, [], { name: 'scrolled course students page' });
    }

    test('keeps course students inside a scrollable viewport with a sticky header in light, dark and narrow views', async ({
        page,
        login,
        courseManagementAPIRequests,
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

            // Exercise the real theme service through its persisted system-preference mode.
            await page.addInitScript(() => localStorage.removeItem('artemisapp.theme.preference'));
            await page.emulateMedia({ colorScheme: 'light' });
            await page.setViewportSize({ width: 1280, height: 800 });
            await page.goto(`/course-management/${course.id}/members/students`);
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
                        await attachScreenshot(page, `course-students-${name}`);
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
                        await attachScreenshot(page, 'course-students-remove-dialog');
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

    test('shows the real assessment dashboard, workspace and grading help in light, dark and narrow layouts', async ({
        page,
        login,
        courseManagementAPIRequests,
        exerciseAPIRequests,
        exerciseAssessment,
        modelingExerciseAssessment,
    }) => {
        let course: Course | undefined;
        const token = generateUUID();

        // The inherited page fixture provides navigation recovery; the login fixture verifies the account.
        await page.addInitScript(() => localStorage.removeItem('artemisapp.theme.preference'));
        await page.emulateMedia({ colorScheme: 'light' });
        await page.setViewportSize({ width: 1280, height: 800 });

        async function captureLayouts(screen: string, check: (viewport: { width: number; height: number }) => Promise<void>, afterCapture?: () => Promise<void>): Promise<void> {
            for (const theme of ['light', 'dark'] as const) {
                await page.emulateMedia({ colorScheme: theme });
                await forEachViewport(page, async (viewport) => {
                    await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
                    if (theme === 'dark') {
                        await page.waitForFunction(() => (document.getElementById('artemis-theme-override') as HTMLLinkElement | null)?.sheet != null);
                    }
                    await check(viewport);
                    if (viewport.width === 1280 || viewport.width === 480) {
                        await page.mouse.move(0, 0);
                        await attachScreenshot(page, `${screen}-${theme}-${viewport.width}x${viewport.height}`);
                    }
                    await afterCapture?.();
                });
            }
        }

        try {
            await login(admin);
            course = await courseManagementAPIRequests.createCourse({
                courseName: `TUM UI assessment ${token}`,
                allowCommunication: false,
                allowMessaging: false,
            });
            expect(course.id).toBeDefined();
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            await courseManagementAPIRequests.addTutorToCourse(course, tutor);

            const exercise = await exerciseAPIRequests.createModelingExercise({ course }, `Domain model assessment ${token}`);
            expect(exercise.id).toBeDefined();

            await login(studentOne);
            const participationResponse = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
            expect(participationResponse.status()).toBe(201);
            const participation = (await participationResponse.json()) as StudentParticipation;
            const submissionResponse = await exerciseAPIRequests.makeModelingExerciseSubmission(exercise.id!, participation, {
                submitted: true,
                explanationText: 'The aggregate root owns its entities and keeps changes to the domain model consistent.',
            });
            expect(submissionResponse.ok()).toBe(true);

            // Submit before closing the period: the real submission remains in time, with no deadline sleeps.
            await login(admin);
            const closedAt = dayjs();
            const update: UpdateModelingExerciseDTO = {
                id: exercise.id,
                courseId: course.id,
                title: exercise.title,
                channelName: exercise.channelName,
                shortName: exercise.shortName,
                difficulty: exercise.difficulty,
                maxPoints: exercise.maxPoints,
                bonusPoints: exercise.bonusPoints,
                includedInOverallScore: exercise.includedInOverallScore,
                diagramType: exercise.diagramType,
                releaseDate: dayjsToString(closedAt.subtract(2, 'hours')),
                startDate: dayjsToString(closedAt.subtract(1, 'hour')),
                dueDate: dayjsToString(closedAt),
                assessmentDueDate: dayjsToString(closedAt.add(1, 'day')),
                problemStatement: 'Model a library domain. Explain which objects own their data and how changes stay consistent.',
                gradingInstructions: 'Award points for clear ownership, meaningful relationships, and an explanation consistent with the diagram.',
                exampleSolutionModel: exercise.exampleSolutionModel,
                exampleSolutionExplanation: 'A small aggregate keeps ownership and consistency rules explicit.',
                presentationScoreEnabled: false,
                secondCorrectionEnabled: false,
                gradingCriteria: exercise.gradingCriteria ?? [],
            };
            const updateResponse = await page.request.put('api/modeling/modeling-exercises', { data: update });
            expect(updateResponse.ok(), await updateResponse.text()).toBe(true);

            const dashboardRoute = `/course-management/${course.id}/assessment-dashboard/${exercise.id}`;
            await login(tutor, dashboardRoute);
            await dismissPasskeyReminderIfPresent(page);
            await exerciseAssessment.clickHaveReadInstructionsButton();
            const startAssessment = page.getByTestId('start-new-assessment').first();
            await expect(startAssessment).toBeVisible();
            await expect(startAssessment).not.toHaveAttribute('aria-disabled', 'true');

            await captureLayouts('exercise-assessment-dashboard', async () => {
                // The native submission table may scroll inside its explicit wrapper at narrow widths.
                await expect(page.getByTestId('correction-round-0').getByRole('table')).toBeVisible();
                await expectNoHorizontalOverflow(page, [], { name: 'exercise assessment dashboard' });
                await expectNoHorizontalScrollAround(page.getByTestId('correction-round-0'), { name: 'submission panel' });
                await startAssessment.scrollIntoViewIfNeeded();
                await expectWithinViewport(startAssessment, ['left', 'right'], { name: 'start assessment action' });
                await page.evaluate(() => window.scrollTo(0, 0));
            });

            await exerciseAssessment.clickStartNewAssessment();
            // This existing method waits for the real Apollon model to initialize; it only sets the page object's cursor.
            await modelingExerciseAssessment.openAssessmentForComponent(1);
            await expect(page.getByTestId('assessmentLockedCurrentUser')).toBeVisible();
            const workspace = page.getByTestId('assessment-workspace');
            const canvas = workspace.getByTestId('assessment-workspace-canvas');
            const instructions = workspace.getByTestId('assessment-workspace-instructions');
            const details = workspace.getByTestId('assessment-workspace-details');
            const notes = workspace.getByRole('textbox', { name: 'Private tutor notes', exact: true });

            // Populate the migrated native feedback/points/notes controls; no assessment submission is needed.
            await workspace.getByRole('button', { name: 'Add new Feedback', exact: true }).click();
            const points = workspace.getByTestId('unified-feedback-points-input').locator('input');
            await points.fill('2');
            await points.blur();
            await expect(points).toHaveValue('2');
            await workspace
                .getByRole('textbox', { name: 'Feedback', exact: true })
                .fill('The domain boundaries are clear. Add explicit multiplicities to explain the relationships.');
            await notes.fill('Check relationship consistency during the final review.');

            await captureLayouts('modeling-assessment-workspace', async () => {
                await expectWithinViewport(workspace, ['top', 'right', 'bottom', 'left'], { name: 'assessment workspace', tolerance: 1 });
                await expectNoHorizontalOverflow(page, [], { name: 'assessment editor page' });
                await expectNoHorizontalScrollAround(workspace, { name: 'assessment workspace' });
                for (const [panel, name] of [
                    [canvas, 'model canvas'],
                    [instructions, 'assessment instructions'],
                    [details, 'feedback and private notes'],
                ] as const) {
                    await expectInside(panel, workspace, ['top', 'right', 'bottom', 'left'], { name, tolerance: 1 });
                }
                await expectNoOverlap([canvas, instructions, details], { name: 'assessment panels', tolerance: 1 });
                const primary = workspace.getByRole('separator').first();
                const stacked = await workspace.evaluate((element) => element.clientWidth < 840);
                await expect(primary).toHaveAttribute('aria-orientation', stacked ? 'horizontal' : 'vertical');
                await expect(workspace.getByRole('heading', { name: 'Instructions', exact: true })).toBeVisible();
                await expect(workspace.getByRole('heading', { name: 'Feedback', exact: true })).toBeVisible();
                await expect(notes).toHaveValue('Check relationship consistency during the final review.');
            });

            // Unlike the existing workspace's pointer-drag test, this checks the native keyboard path in a browser.
            // Do it after the viewport matrix, since resizing persists an absolute user-selected split.
            const primary = workspace.getByRole('separator').first();
            const previousSize = Number(await primary.getAttribute('aria-valuenow'));
            await primary.focus();
            await expect(primary).toBeFocused();
            await primary.press((await primary.getAttribute('aria-orientation')) === 'vertical' ? 'ArrowRight' : 'ArrowDown');
            await expect.poll(async () => Number(await primary.getAttribute('aria-valuenow'))).toBeGreaterThan(previousSize);

            await login(admin, `/course-management/${course.id}/grading`);
            await dismissPasskeyReminderIfPresent(page);
            const help = page.getByRole('button', { name: 'Grading Key Help', exact: true });
            await expect(help).toBeVisible();
            await page.getByRole('button', { name: 'Set Default Grading Key', exact: true }).click();
            const dialog = page.getByRole('dialog', { name: 'Grading Key Help', exact: true });

            await captureLayouts(
                'grading-key-help',
                async () => {
                    await help.click();
                    await expect(dialog).toBeVisible();
                    await expect(dialog.getByRole('table')).toContainText('General Information');
                    await expectWithinViewport(dialog, ['top', 'right', 'bottom', 'left'], { name: 'grading key help dialog', tolerance: 1 });
                    await expectInside(dialog.getByRole('table'), dialog, ['left', 'right'], { name: 'grading help table', tolerance: 1 });
                    await expectNoHorizontalScrollAround(dialog, { name: 'grading key help dialog' });

                    // Header close and footer close surround the long, scrollable help table.
                    const close = dialog.getByRole('button', { name: 'Close', exact: true });
                    await expect(close).toHaveCount(2);
                    await close.first().focus();
                    await page.keyboard.press('Shift+Tab');
                    await expect(close.last()).toBeFocused();
                    await page.keyboard.press('Tab');
                    await expect(close.first()).toBeFocused();
                },
                async () => {
                    await page.keyboard.press('Escape');
                    await expect(dialog).toBeHidden();
                    await expect(help).toBeFocused();
                },
            );
        } finally {
            await courseManagementAPIRequests.deleteCourse(course, admin);
        }
    });
});
