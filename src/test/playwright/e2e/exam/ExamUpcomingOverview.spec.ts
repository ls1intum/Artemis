import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Course } from 'app/course/shared/entities/course.model';

const testCourse = { id: SEED_COURSES.examManagement.id } as any;

/**
 * The admin overview of upcoming exams lists the exams of all courses that are held today or later, ordered by their start, with the exam mode, the
 * course and the dates of each. Exams that are over do not appear, and only an admin may see the list.
 */
test.describe('Exam upcoming overview', { tag: '@slow' }, () => {
    let exams: Exam[] = [];
    let realCourse: Course | undefined;

    test.afterEach('Delete exams and the course', async ({ login, examAPIRequests, courseManagementAPIRequests }) => {
        await login(admin);
        for (const exam of exams) {
            await examAPIRequests.deleteExam(exam);
        }
        exams = [];
        // Deleting the course deletes its exams with it.
        await courseManagementAPIRequests.deleteCourse(realCourse, admin);
        realCourse = undefined;
    });

    test('The admin sees the exams that are held today or later in the order of their start, but not the ones that are over', async ({
        page,
        login,
        examAPIRequests,
        courseManagementAPIRequests,
    }) => {
        await login(admin);
        const uid = generateUUID();
        // The exams of test courses are left out of the overview, so the exams are held in a course that is none.
        const course = (realCourse = await courseManagementAPIRequests.createCourse({ testCourse: false }));
        const later = await examAPIRequests.createExam({ course, title: `Later ${uid}`, visibleDate: dayjs(), startDate: dayjs().add(3, 'days'), endDate: dayjs().add(4, 'days') });
        const sooner = await examAPIRequests.createExam({
            course,
            title: `Sooner ${uid}`,
            visibleDate: dayjs(),
            startDate: dayjs().add(1, 'day'),
            endDate: dayjs().add(2, 'days'),
        });
        const testExam = await examAPIRequests.createExam({
            course,
            title: `Test exam ${uid}`,
            testExam: true,
            visibleDate: dayjs(),
            startDate: dayjs().add(2, 'days'),
            endDate: dayjs().add(5, 'days'),
        });
        const over = await examAPIRequests.createExam({
            course,
            title: `Over ${uid}`,
            visibleDate: dayjs().subtract(4, 'days'),
            startDate: dayjs().subtract(3, 'days'),
            endDate: dayjs().subtract(2, 'days'),
            gracePeriod: 0,
        });
        // An upcoming exam of a test course is not listed.
        const inTestCourse = await examAPIRequests.createExam({
            course: testCourse,
            title: `In test course ${uid}`,
            visibleDate: dayjs(),
            startDate: dayjs().add(1, 'day'),
            endDate: dayjs().add(2, 'days'),
        });
        exams.push(later, sooner, testExam, over, inTestCourse);

        // The server lists the current and upcoming exams ordered by their start, without the exam that is over.
        const listed = await page.request.get('api/exam/admin/courses/upcoming-exams');
        expect(listed.status()).toBe(200);
        const titles = ((await listed.json()) as { title: string }[]).map((exam) => exam.title).filter((title) => title.endsWith(uid));
        expect(titles, 'the exams that are over or of a test course are not listed, the others are ordered by their start').toEqual([
            `Sooner ${uid}`,
            `Test exam ${uid}`,
            `Later ${uid}`,
        ]);

        await page.goto('/admin/upcoming-exams-and-exercises');
        const table = page.getByTestId('upcoming-exams-table');
        await expect(table).toBeVisible();
        const rowOf = (exam: Exam) => table.locator('tbody tr', { hasText: exam.title! });
        await expect(rowOf(over), 'an exam that is over is not shown').toHaveCount(0);
        await expect(rowOf(inTestCourse), 'an exam of a test course is not shown').toHaveCount(0);
        for (const exam of [sooner, testExam, later]) {
            await expect(rowOf(exam)).toHaveCount(1);
        }

        // The page shows them in the same order.
        const shownTitles = (await table.locator('tbody tr td:first-child a').allInnerTexts()).filter((title) => title.endsWith(uid));
        expect(shownTitles).toEqual([`Sooner ${uid}`, `Test exam ${uid}`, `Later ${uid}`]);

        // Mode, course and dates of a row.
        await expect(rowOf(sooner).getByTestId('exam-mode-tag')).toHaveText(/^\s*Exam\s*$/);
        await expect(rowOf(testExam).getByTestId('exam-mode-tag')).toHaveText(/^\s*Test Exam\s*$/);
        await expect(rowOf(sooner)).toContainText(course.title!);
        await expect(rowOf(sooner)).toContainText(dayjs(sooner.startDate as any).format('MMM D, YYYY'));
        await expect(rowOf(sooner)).toContainText(dayjs(sooner.endDate as any).format('MMM D, YYYY'));

        // The title leads to the exam.
        await rowOf(later).getByRole('link', { name: later.title! }).click();
        await page.waitForURL(`**/course-management/${course.id}/exams/${later.id}`);
    });

    test('Only an admin may list the upcoming exams', async ({ page, login }) => {
        await login(instructor);
        const response = await page.request.get('api/exam/admin/courses/upcoming-exams');
        expect(response.status()).toBe(403);
    });
});
