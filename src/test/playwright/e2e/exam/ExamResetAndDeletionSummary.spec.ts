import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { newBrowserPage, prepareExam } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { ExerciseType } from '../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';

const course = { id: SEED_COURSES.examManagement.id } as any;

/**
 * The two destructive actions on an exam that a student has already taken. The deletion dialog tells an instructor what would be deleted
 * before anything is, and a reset throws away what students did and unregisters them while keeping the exam and its exercise configuration.
 */
test.describe.serial('Exam reset and deletion summary', { tag: '@slow' }, () => {
    let exam: Exam;

    test.beforeAll('Have a student take the exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        exam = await prepareExam(course, dayjs().add(30, 'minutes'), ExerciseType.TEXT, page);
        await page.close();
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        await Commands.login(page, admin);
        await new ExamAPIRequests(page).deleteExam(exam);
        await page.close();
    });

    test('The deletion dialog says what would be deleted and deletes nothing when it is cancelled', async ({ page, login, examAPIRequests }) => {
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}`);
        await page.getByTestId('exam-delete').click();
        const dialog = page.getByRole('dialog');
        await expect(dialog).toContainText('Exam Summary');
        await expect(dialog).toContainText(/Number of Exercise Groups\D*1\b/);
        await expect(dialog).toContainText(/Number of Text Exercises\D*1\b/);
        await expect(dialog).toContainText(/Number of Registered Students\D*1\b/);
        await expect(dialog).toContainText(/Number of Exams \(submitted\)\D*1\b/);
        await dialog.getByRole('button', { name: 'Cancel' }).click();

        // Cancelling changed nothing.
        await login(admin);
        expect((await examAPIRequests.getExam(exam)).id).toBe(exam.id);
        expect(await examAPIRequests.getAllStudentExams(exam)).toHaveLength(1);
    });

    test('Resetting the exam deletes what the students did, unregisters them and keeps the exam and its exercises', async ({ page, login, examAPIRequests }) => {
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}`);
        // The exam page shows the one registered student before the reset.
        await expect(page.getByText('Current number of students registered: 1').first()).toBeVisible();
        await page.getByRole('button', { name: 'Reset' }).click();
        const dialog = page.getByRole('dialog');
        // The dialog tells the instructor what the reset does to the registrations, instead of promising to keep them.
        await expect(dialog).toContainText('all registered students will be removed from the exam');
        await dialog.locator('#confirm-entity-name').fill(exam.title!);
        const reset = page.waitForResponse((response) => response.url().endsWith(`/exams/${exam.id}/reset`) && response.request().method() === 'DELETE');
        await dialog.getByTestId('delete-dialog-confirm-button').click();
        expect((await reset).status()).toBe(200);
        // The page now shows no registered student, without a reload.
        await expect(page.getByText('Current number of students registered: 0').first()).toBeVisible();

        // The work of the students is gone, and so are their registrations ...
        await login(admin);
        expect(await examAPIRequests.getAllStudentExams(exam)).toHaveLength(0);
        // ... while the exam and its exercises remain.
        expect((await examAPIRequests.getExam(exam)).id).toBe(exam.id);
        const groups = await examAPIRequests.getExerciseGroups(exam);
        expect(groups).toHaveLength(1);
        expect(groups[0].exercises).toHaveLength(1);
        const summary = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/deletion-summary`);
        const counts = await summary.json();
        expect(counts.numberSubmittedExams).toBe(0);
        expect(counts.numberStartedExams).toBe(0);
        expect(counts.numberRegisteredStudents).toBe(0);
    });
});
