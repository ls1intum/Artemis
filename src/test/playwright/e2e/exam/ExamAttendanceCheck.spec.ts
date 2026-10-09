import { expect, Page, Browser } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, studentOne, studentTwo, tutor } from '../../support/users';
import { asUser, newBrowserPage, prepareRunningTextExam } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { ExamStartEndPage } from '../../support/pageobjects/exam/ExamStartEndPage';
import { ModalDialogBox } from '../../support/pageobjects/exam/ModalDialogBox';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';

const course = { id: SEED_COURSES.examParticipation.id } as any;

/**
 * An exam supervisor (a tutor or above) can trigger the attendance check of a single student who is taking the exam. The student is
 * shown a live event with the instructions of the supervisor; nobody else is disturbed, and students can not trigger it.
 */
test.describe('Exam attendance check', { tag: '@slow' }, () => {
    let exam: Exam;

    /** Browser contexts opened by the test, closed again however the test ends. */
    let openedPages: Page[] = [];
    async function openPage(browser: Browser): Promise<Page> {
        const page = await newBrowserPage(browser);
        openedPages.push(page);
        return page;
    }

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        for (const openedPage of openedPages) {
            await openedPage.context().close();
        }
        openedPages = [];
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('A tutor triggers the attendance check of one student, who is shown the check', async ({ browser, login, examAPIRequests, exerciseAPIRequests }) => {
        await login(admin);
        exam = (await prepareRunningTextExam(examAPIRequests, exerciseAPIRequests, { course, students: [studentOne, studentTwo] })).exam;

        const [checkedStudentPage, otherStudentPage] = [await openPage(browser), await openPage(browser)];
        for (const [studentPage, student] of [
            [checkedStudentPage, studentOne],
            [otherStudentPage, studentTwo],
        ] as const) {
            await Commands.login(studentPage, student);
            await studentPage.goto(`/courses/${course.id}/exams/${exam.id}`);
            await new ExamStartEndPage(studentPage).startExam();
            await expect(studentPage.getByTestId('hand-in-early')).toBeVisible();
        }

        // Students may not trigger the check, not even for themselves.
        const byStudent = await new ExamAPIRequests(otherStudentPage).triggerAttendanceCheck(exam, studentOne.username);
        expect(byStudent.status()).toBe(403);

        // A tutor may.
        const triggeredFrom = dayjs();
        const response = await asUser(browser, tutor, (tutorRequests) => tutorRequests.triggerAttendanceCheck(exam, studentOne.username));
        expect(response.status()).toBe(200);

        // The student is shown the check, with its time, and can carry on afterwards.
        const dialog = new ModalDialogBox(checkedStudentPage);
        await dialog.checkDialogType('Attendance Check');
        await dialog.checkDialogTime(triggeredFrom, dayjs());
        await expect(dialog.getModalDialogContent()).toContainText(
            'An exam supervisor has triggered the attendance check for you. Please follow the instructions of the supervisor.',
        );
        await dialog.closeDialog();
        await expect(checkedStudentPage.getByTestId('hand-in-early')).toBeVisible();

        // The server kept the event for the checked student only.
        const eventTypesOf = async (studentPage: Page) => {
            const events = await studentPage.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/live-events`);
            expect(events.status()).toBe(200);
            return ((await events.json()) as { eventType: string }[]).map((event) => event.eventType);
        };
        expect(await eventTypesOf(checkedStudentPage)).toEqual(['examAttendanceCheck']);
        expect(await eventTypesOf(otherStudentPage)).toEqual([]);

        // The other student is not disturbed.
        await expect(new ModalDialogBox(otherStudentPage).getModalDialogContent()).toHaveCount(0);
    });
});
