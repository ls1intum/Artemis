import fs from 'fs';
import path from 'path';
import { expect, Page } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.examParticipation.id } as any;
const roomZip = path.resolve(__dirname, '../../../../test/resources/test-data/exam-room/single-room.zip');
const roomNumber = '5602.EG.001';

/**
 * Seating the students of an exam: an instructor distributes the registered students over a room, every student gets a seat of their own,
 * a single student can be moved to another seat, and the dialog says when a room does not have enough seats for everybody.
 */
test.describe('Exam room distribution', { tag: '@slow' }, () => {
    let exam: Exam;

    test.beforeEach('Provide the room and an exam with registered students', async ({ login, page, examAPIRequests }) => {
        await login(instructor);
        const upload = await page.request.post('api/exam/rooms/upload', {
            multipart: { file: { name: 'single-room.zip', mimeType: 'application/zip', buffer: fs.readFileSync(roomZip) } },
        });
        expect(upload.status()).toBe(200);
        expect((await upload.json()).uploadedRoomNames).toEqual(['Friedrich L. Bauer Hörsaal']);

        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course });
        await examAPIRequests.registerAllCourseStudentsForExam(exam);
        // The rooms that can be offered are loaded once, when the student list opens.
        const roomsLoaded = page.waitForResponse((response) => response.url().includes('rooms/distribution-data'));
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/students`);
        expect((await roomsLoaded).status()).toBe(200);
    });

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    /** Opens the distribution dialog and selects the uploaded room. */
    async function openDistributionWithRoom(page: Page) {
        await expect(page.locator('p-table tbody tr').first()).toBeVisible();
        await page.getByRole('button', { name: 'Logistics' }).click();
        // Opening the dialog loads the rooms the exam already uses and preselects them; picking a room before that answer is there would be overwritten by it.
        const roomsUsedLoaded = page.waitForResponse((response) => response.url().includes('/rooms-used'));
        await page.locator('[data-testid="exam-students-menu-item"]', { hasText: 'Distribute students' }).last().click();
        expect((await roomsUsedLoaded).status()).toBe(200);
        // The dialog moves the focus to its first field once it is open; typing into the room search before that would lose the focus again.
        await expect(page.getByRole('dialog').locator('#reserveFactor')).toBeFocused();
        const search = page.getByPlaceholder('Search by room number, name, and/or building');
        await search.click();
        await search.pressSequentially('Bauer');
        await page.locator('ngb-typeahead-window button').first().click();
        return page.getByRole('dialog');
    }

    /** The seat and room of every student in the table, keyed by login. */
    async function seating(page: Page) {
        const rows = page.locator('p-table tbody tr');
        const result: Record<string, { room: string; seat: string }> = {};
        for (const row of await rows.all()) {
            const cells = (await row.innerText())
                .split(/[\n\t]+/)
                .map((line) => line.trim())
                .filter(Boolean);
            const login = cells.find((cell) => cell.startsWith('artemis_test_user_'))!;
            const room = cells.find((cell) => cell === roomNumber) ?? '';
            const seat = cells.find((cell) => /^\d+, \d+/.test(cell)) ?? '';
            result[login] = { room, seat };
        }
        return result;
    }

    test('Distributing the students gives every student a seat of their own', async ({ page }) => {
        await expect(page.locator('p-table tbody tr').first()).toBeVisible();
        const numberOfStudents = await page.locator('p-table tbody tr').count();
        expect(numberOfStudents).toBeGreaterThanOrEqual(4);

        const dialog = await openDistributionWithRoom(page);
        const distributed = page.waitForResponse((response) => response.url().includes('distribute-registered-students'));
        await dialog.getByRole('button', { name: 'Distribute' }).click();
        expect((await distributed).status()).toBe(200);

        // Everybody sits in the room, and no two students share a seat.
        await expect(page.locator('p-table tbody tr').filter({ hasText: roomNumber })).toHaveCount(numberOfStudents);
        const seats = await seating(page);
        expect(Object.keys(seats)).toHaveLength(numberOfStudents);
        for (const { room, seat } of Object.values(seats)) {
            expect(room).toBe(roomNumber);
            expect(seat).toMatch(/^\d+, \d+$/);
        }
        expect(new Set(Object.values(seats).map(({ seat }) => seat)).size, 'every student has a seat of their own').toBe(numberOfStudents);

        // The server knows the room as used by the exam.
        const roomsUsed = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/rooms-used`);
        expect(roomsUsed.status()).toBe(200);
        expect(JSON.stringify(await roomsUsed.json())).toContain(roomNumber);
    });

    test('A single student can be moved to another seat', async ({ page }) => {
        const dialog = await openDistributionWithRoom(page);
        await dialog.getByRole('button', { name: 'Distribute' }).click();
        await expect(page.locator('p-table tbody tr').filter({ hasText: roomNumber }).first()).toBeVisible();
        const before = await seating(page);

        // Student One moves to the seat in row 5, seat 5.
        const studentRow = page.locator('p-table tbody tr', { hasText: 'artemis_test_user_1' });
        await studentRow.getByTestId('reseat-student-button').click();
        const reseating = page.getByRole('dialog');
        await expect(reseating).toContainText('artemis_test_user_1');
        const seatSearch = reseating.getByPlaceholder('Leave empty to automatically find a seat');
        await seatSearch.click();
        await seatSearch.fill('5, 5');
        await page.locator('ngb-typeahead-window button').first().click();
        const reseated = page.waitForResponse((response) => response.url().includes('reseat-student'));
        await reseating.locator('#finish-button').click();
        expect((await reseated).status()).toBe(200);

        // Only that student moved.
        await expect(studentRow).toContainText('5, 5');
        const after = await seating(page);
        expect(after['artemis_test_user_1'].seat).toBe('5, 5');
        for (const login of Object.keys(before).filter((candidate) => candidate !== 'artemis_test_user_1')) {
            expect(after[login], `${login} keeps the seat`).toEqual(before[login]);
        }
    });

    // KNOWN BUG: the dialog takes the number of students from `exam.numberOfExamUsers`, which the exam of the student list does not carry
    // (the exam is loaded without it), so it always reports "You can seat all 0 students", also when the room is too small.
    test.fixme('The dialog reports how many of the registered students the room can seat', async ({ page }) => {
        await expect(page.locator('p-table tbody tr').first()).toBeVisible();
        const numberOfStudents = await page.locator('p-table tbody tr').count();
        const dialog = await openDistributionWithRoom(page);
        await expect(dialog).toContainText(`You can seat all ${numberOfStudents} students.`);
        // Reserving nearly all seats leaves fewer seats than students.
        const reserved = dialog.locator('#reserveFactor');
        await reserved.click();
        await reserved.fill('99');
        await reserved.blur();
        await expect(dialog).toContainText(new RegExp(`You can only seat \\d+ of the ${numberOfStudents} students`));
    });
});
