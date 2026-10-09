import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentOne, tutor } from '../../support/users';
import { asAdmin, newBrowserPage, prepareEndedExam, startAssessing } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { ExerciseType } from '../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { ExamManagementPage } from '../../support/pageobjects/exam/ExamManagementPage';
import { CourseAssessmentDashboardPage } from '../../support/pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from '../../support/pageobjects/assessment/ExerciseAssessmentDashboardPage';
import { ExamAssessmentPage } from '../../support/pageobjects/assessment/ExamAssessmentPage';
import { EXAM_DASHBOARD_TIMEOUT, RELOAD_RENDER_TIMEOUT } from '../../support/timeouts';
import { Fixtures } from '../../fixtures/fixtures';

const course = { id: SEED_COURSES.examResults.id } as any;

/**
 * What a student sees of an assessed exam depends on two dates: the results are published at the publication date, and the student
 * review period (the time in which a complaint is possible) decides whether the student can still complain. The exam is assessed while
 * the results are unpublished, and the dates are then moved on the server clock, so the test never waits for a guessed date.
 */
test.describe.serial('Exam results publication and review period', { tag: '@slow' }, () => {
    test.describe.configure({ timeout: 240_000 });

    let exam: Exam;
    let studentExamId: number;

    test.beforeAll('Take, end and assess an exam whose results are not published', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        exam = await prepareEndedExam(course, ExerciseType.TEXT, page, 1, false);

        await Commands.login(page, tutor);
        await startAssessing(
            course.id!,
            exam.id!,
            EXAM_DASHBOARD_TIMEOUT,
            new ExamManagementPage(page),
            new CourseAssessmentDashboardPage(page),
            new ExerciseAssessmentDashboardPage(page),
        );
        const examAssessment = new ExamAssessmentPage(page);
        await examAssessment.addNewFeedback(7, 'Good job');
        const response = await examAssessment.submitTextAssessment();
        expect(response.status()).toBe(200);

        await Commands.login(page, studentOne);
        studentExamId = await new ExamAPIRequests(page).getOwnStudentExamId(exam);
        await page.close();
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        await Commands.login(page, admin);
        await new ExamAPIRequests(page).deleteExam(exam);
        await page.close();
    });

    test('The student sees the submission but neither score nor grade before the results are published', async ({ page, login }) => {
        await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
        // The exam opens on the result page, which says that the correction is not published yet ...
        await expect(page.getByText('Your result will be published here as soon as the correction is finished.')).toBeVisible({ timeout: RELOAD_RENDER_TIMEOUT });
        // ... and shows the submission itself ...
        await expect(page.getByText(exam.title!).first()).toBeVisible();
        await expect(page.locator('#text-editor')).toHaveValue((await Fixtures.get('loremIpsum-short.txt'))!);
        // ... but nothing of the assessment, for the client and for the server alike.
        await expect(page.getByTestId('achieved-percentage')).toHaveCount(0);
        await expect(page.locator('#complain')).toHaveCount(0);
        const gradeSummary = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/${studentExamId}/grade-summary`);
        expect(gradeSummary.status()).toBe(403);
    });

    test('The results and the complaint form appear at the publication date and the complaint form disappears with the review period', async ({
        browser,
        page,
        login,
        examParticipation,
    }) => {
        const publishedAt = await asAdmin(browser, (adminExamRequests) => adminExamRequests.publishResultsIn(exam, 10));
        await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
        await asAdmin(browser, (adminExamRequests) => adminExamRequests.waitUntilServerClockIsAfter(exam, publishedAt));

        // Published: the score is there (7 of 10 points), the grade summary is handed out, and a complaint is possible.
        await examParticipation.checkResultScore('70%');
        await expect(page.getByText('Your result will be published here as soon as the correction is finished.')).toHaveCount(0);
        const gradeSummary = await page.request.get(`api/exam/courses/${course.id}/exams/${exam.id}/student-exams/${studentExamId}/grade-summary`);
        expect(gradeSummary.status()).toBe(200);
        expect((await gradeSummary.json()).studentResult.overallPointsAchieved).toBe(7);
        await expect(page.locator('#complain')).toBeVisible();

        // The review period ends: the result stays, the complaint form goes.
        await asAdmin(browser, (adminExamRequests) => adminExamRequests.closeReviewPeriod(exam));
        await page.reload();
        await examParticipation.checkResultScore('70%');
        await expect(page.locator('#complain')).toHaveCount(0);
    });
});
