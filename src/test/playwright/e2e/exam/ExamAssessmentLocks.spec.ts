import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { asAdmin, newBrowserPage, prepareEndedExam, startAssessing } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { ExerciseType } from '../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExamManagementPage } from '../../support/pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from '../../support/pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from '../../support/pageobjects/assessment/ExerciseAssessmentDashboardPage';
import { EXAM_DASHBOARD_TIMEOUT } from '../../support/timeouts';

const course = { id: SEED_COURSES.examAssessment.id } as any;

/**
 * Whoever starts to assess a submission locks it, so nobody else assesses it at the same time. The assessment locks page of the exam lists the
 * submissions a person has locked with their exercise, and lets them cancel an assessment, which unlocks the submission again.
 */
test.describe.serial('Exam assessment locks', { tag: '@slow' }, () => {
    test.describe.configure({ timeout: 240_000 });

    let exam: Exam;
    let exerciseTitle: string;

    test.beforeAll('Take and end an exam whose answer is not assessed yet', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        exam = await prepareEndedExam(course, ExerciseType.TEXT, page, 1, false);
        await Commands.login(page, admin);
        const groups = await new ExamAPIRequests(page).getExerciseGroups(exam);
        exerciseTitle = groups[0].exercises![0].title!;
        await page.close();
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        await asAdmin(browser, (examAPIRequests) => examAPIRequests.deleteExam(exam));
    });

    test('A started assessment locks the submission, the locks page lists it, and cancelling unlocks it', async ({ page, login }) => {
        const locksUrl = `api/exam/courses/${course.id}/exams/${exam.id}/locked-submissions`;
        await login(instructor);
        expect(await (await page.request.get(locksUrl)).json(), 'nothing is locked before anybody assesses').toEqual([]);

        // The instructor starts to assess the only submission, which locks it.
        await startAssessing(
            course.id!,
            exam.id!,
            EXAM_DASHBOARD_TIMEOUT,
            new ExamManagementPage(page),
            new CourseAssessmentDashboardPage(page),
            new ExerciseAssessmentDashboardPage(page),
        );
        const lockedSubmissions = async () => (await (await page.request.get(locksUrl)).json()) as { participation: { exercise: { title: string } } }[];
        await expect.poll(async () => (await lockedSubmissions()).length, { message: 'the submission is locked once the assessment is open' }).toBe(1);
        expect((await lockedSubmissions())[0].participation.exercise.title).toBe(exerciseTitle);

        // The locks page lists it with the exercise.
        await page.goto(`/course-management/${course.id}/exams/${exam.id}/assessment-locks`);
        const rows = page.locator('table.exercise-table tbody tr');
        await expect(rows).toHaveCount(1);
        await expect(rows.first()).toContainText(exerciseTitle);

        // Cancelling the assessment unlocks the submission: the row and the lock are gone.
        // The page asks for a confirmation with the dialog of the browser.
        page.once('dialog', (dialog) => dialog.accept());
        const cancelled = page.waitForResponse((response) => response.url().includes('/cancel-assessment') && response.request().method() === 'POST');
        await page.getByRole('button', { name: 'Cancel' }).click();
        expect((await cancelled).status()).toBe(200);
        await expect(rows).toHaveCount(0);
        expect(await lockedSubmissions(), 'the lock is released').toEqual([]);
    });
});
