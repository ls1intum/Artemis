import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { newBrowserPage, prepareEndedExam } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { ExerciseType } from '../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';

const course = { id: SEED_COURSES.examManagement.id } as any;

/**
 * What an instructor sees and can do with the student exam of a student who has taken and handed in an exam that is over: the
 * participation status, the possibility to change the submission state, and the sessions that were recorded while the student took the exam.
 */
test.describe.serial('Student exam management of an ended exam', { tag: '@slow' }, () => {
    test.describe.configure({ timeout: 180_000 });

    let exam: Exam;
    let studentExamId: number;

    test.beforeAll('Take and end an exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        exam = await prepareEndedExam(course, ExerciseType.TEXT, page);
        const examAPIRequests = new ExamAPIRequests(page);
        [{ id: studentExamId }] = await examAPIRequests.getAllStudentExams(exam);
        await page.close();
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        await Commands.login(page, admin);
        await new ExamAPIRequests(page).deleteExam(exam);
        await page.close();
    });

    test.beforeEach(async ({ login, page }) => {
        await login(instructor, `/course-management/${course.id}/exams/${exam.id}/student-exams/${studentExamId}`);
        await expect(page.locator('#adjust-submitted-state-button')).toBeVisible({ timeout: 30_000 });
    });

    /** The line "<label>: yes/no" of the participation status. */
    const statusLine = (page: import('@playwright/test').Page, label: string) => page.locator('li', { has: page.locator('strong', { hasText: new RegExp(`^${label}:`) }) });

    test('An instructor can change a submitted student exam to unsubmitted and back', async ({ page, examAPIRequests }) => {
        await expect(statusLine(page, 'Started')).toContainText('Yes');
        await expect(statusLine(page, 'Submitted')).toContainText('Yes');
        await expect(page.locator('#adjust-submitted-state-button')).toContainText('Change Student Exam to Unsubmitted');

        // Un-submitting asks for a confirmation that names the consequence, and only then changes anything.
        await page.locator('#adjust-submitted-state-button').click();
        const dialog = page.getByRole('dialog');
        await expect(dialog).toContainText('This will delete the submission date and set the submission state to');
        await dialog.getByRole('button', { name: 'Cancel' }).click();
        await expect(statusLine(page, 'Submitted')).toContainText('Yes');
        expect((await examAPIRequests.getAllStudentExams(exam))[0].submitted).toBe(true);

        await page.locator('#adjust-submitted-state-button').click();
        await page.getByRole('dialog').getByRole('button', { name: 'Unsubmit', exact: true }).click();
        await expect(statusLine(page, 'Submitted')).toContainText('No');
        await expect(page.locator('#adjust-submitted-state-button')).toContainText('Change Student Exam to Submitted');
        const unsubmitted = (await examAPIRequests.getAllStudentExams(exam))[0];
        expect(unsubmitted.submitted).toBe(false);
        expect(unsubmitted.submissionDate ?? null, 'the submission date is deleted with the submission').toBeNull();

        // Submitting again sets the submission state and a new submission date.
        await page.locator('#adjust-submitted-state-button').click();
        await page.getByRole('dialog').getByRole('button', { name: 'Submit', exact: true }).click();
        await expect(statusLine(page, 'Submitted')).toContainText('Yes');
        const resubmitted = (await examAPIRequests.getAllStudentExams(exam))[0];
        expect(resubmitted.submitted).toBe(true);
        expect(resubmitted.submissionDate).toBeTruthy();
    });

    test('The sessions recorded while the student took the exam are listed', async ({ page }) => {
        // Every time the student loads the exam a session is recorded, so a recovery reload adds one; at least the first one is there.
        const sessions = page.getByTestId('exam-session-row');
        await expect(sessions.first()).toBeVisible();
        for (const session of await sessions.all()) {
            await expect(session.getByTestId('exam-session-token')).not.toBeEmpty();
            await expect(session.getByTestId('exam-session-user-agent')).toContainText('Chrome');
            await expect(session.getByTestId('exam-session-ip-address')).not.toBeEmpty();
            await expect(session.getByTestId('exam-session-created-date')).not.toBeEmpty();
        }
    });
});
