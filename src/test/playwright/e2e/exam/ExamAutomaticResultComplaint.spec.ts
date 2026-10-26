import { expect } from '@playwright/test';

import { Exam } from 'app/exam/shared/entities/exam.model';

import { ExerciseType, ProgrammingExerciseAssessmentType } from '../../support/constants';
import { admin, instructor, studentOne } from '../../support/users';
import { test } from '../../support/fixtures';
import { Commands } from '../../support/commands';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { SEED_COURSES } from '../../support/seedData';
import { newBrowserPage, prepareEndedExam, waitForExamBuildAndTestAfterDueDate } from '../../support/utils';
import { EXAM_DASHBOARD_TIMEOUT } from '../../support/timeouts';

const course = { id: SEED_COURSES.examAssessment.id } as any;
const complaintText = 'The tests are wrong!';

// An exam programming exercise that is graded automatically, with one correction round and complaints allowed. A complaint about the automatic result
// turns it into the manual result of the first correction round. The instructor opens it through the lock endpoint, which used to hand out the
// submission without any result, so the page showed nothing but the notes box.
test.describe('Exam complaint about an automatic result', { tag: '@slow' }, () => {
    // Preparing the exam and the participation of the student can exceed 90s
    test.describe.configure({ timeout: 180_000 });
    let exam: Exam;

    test.beforeAll('Prepare exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        exam = await prepareEndedExam(course, ExerciseType.PROGRAMMING, page, 1, true, {
            assessmentType: ProgrammingExerciseAssessmentType.AUTOMATIC,
            allowComplaintsForAutomaticAssessments: true,
        });
    });

    test('Shows the code editor and the complaint form when an instructor opens the complaint', async ({
        page,
        login,
        studentAssessment,
        examAssessment,
        examManagement,
        courseAssessment,
        exerciseAssessment,
    }) => {
        test.slow();
        // The score-producing build runs after the exam, and its result is the automatic result of the student
        await waitForExamBuildAndTestAfterDueDate(exam, page);

        await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
        await studentAssessment.startComplaint();
        await studentAssessment.enterComplaint(complaintText);
        await studentAssessment.submitComplaint();
        await examAssessment.checkComplaintMessage('Your complaint has been submitted');

        await login(instructor, `/course-management/${course.id}/exams`);
        await examManagement.openAssessmentDashboard(course.id!, exam.id!, EXAM_DASHBOARD_TIMEOUT);
        await courseAssessment.clickExerciseDashboardButton(0, EXAM_DASHBOARD_TIMEOUT);
        await exerciseAssessment.clickHaveReadInstructionsButton();
        // Preserve the first state-changing lock GET in Node before the browser can evict its body.
        // Re-reading the endpoint could create a result and hide the missing-result regression below.
        let originalLockBody: { results?: unknown[] } | undefined;
        let originalLockStatus: number | undefined;
        let lockForwards = 0;
        await page.route(
            '**/api/programming/programming-submissions/*/lock*',
            async (route) => {
                try {
                    expect(route.request().method()).toBe('GET');
                    lockForwards++;
                    const response = await route.fetch({ maxRedirects: 0 });
                    const body = await response.body();
                    originalLockStatus = response.status();
                    originalLockBody = JSON.parse(body.toString('utf-8')) as { results?: unknown[] };
                    await route.fulfill({ response, body });
                } catch (error) {
                    // Dispatch may already have changed the lock/result; never continue or replay it.
                    await route.abort('failed').catch(() => {});
                    throw error;
                }
            },
            { times: 1 },
        );
        const lockResponsePromise = page.waitForResponse((response) => /\/programming-submissions\/\d+\/lock/.test(response.url()));
        await exerciseAssessment.clickEvaluateComplaint();

        // The submission comes with the result the complaint is about
        const lockResponse = await lockResponsePromise;
        expect(lockResponse.status()).toBe(200);
        expect(lockForwards).toBe(1);
        expect(originalLockStatus).toBe(200);
        expect(originalLockBody?.results).toHaveLength(1);

        // The assessment page is complete: the code editor, and the complaint form to answer the complaint with
        await expect(page.locator('[data-testid="programming-toolbar"]')).toBeVisible();
        await exerciseAssessment.checkComplaintText(complaintText);
        await expect(page.locator('#responseTextArea')).toBeEnabled();
        await expect(page.locator('#acceptComplaintButton')).toBeVisible();
        await expect(page.locator('#rejectComplaintButton')).toBeVisible();
        await expect(page.locator('[data-testid="assessment-result-not-found"]')).toHaveCount(0);
    });

    test.afterAll('Delete exam', async ({ browser }) => {
        const page = await newBrowserPage(browser);
        await Commands.login(page, admin);
        await new ExamAPIRequests(page).deleteExam(exam);
        await page.close();
    });
});
