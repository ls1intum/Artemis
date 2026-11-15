import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../support/fixtures';
import { admin, instructor } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { EXAM_DASHBOARD_TIMEOUT } from '../../support/timeouts';

const course = { id: SEED_COURSES.examTestRun.id } as any;

/**
 * An instructor who tries an exam out with a test run also assesses it, to see the assessment from the tutors' side: the test run shows on the
 * assessment dashboard of the test runs, the instructor gives points for its answer, and the summary of the test run shows the result.
 */
test.describe('Exam test run assessment', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('The instructor assesses the answer of a test run and finds the result in its summary', async ({
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        courseManagementAPIRequests,
        examTestRun,
        examParticipation,
        examNavigation,
        courseAssessment,
        examAssessment,
    }) => {
        test.slow();
        await login(admin);
        exam = await examAPIRequests.createExam({
            course,
            visibleDate: dayjs().subtract(3, 'days'),
            startDate: dayjs().add(1, 'days'),
            endDate: dayjs().add(3, 'days'),
            examMaxPoints: 10,
        });
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        const exercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup });

        // The instructor conducts a test run and hands in an answer.
        await login(instructor);
        const testRun = await courseManagementAPIRequests.createExamTestRun(exam, [exercise]);
        await examTestRun.startParticipation(instructor, course, exam, testRun.id!);
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        await examParticipation.makeTextExerciseSubmission(exercise.id!, 'loremIpsum-short.txt');
        await examParticipation.handInEarly();

        // The test run can be assessed from its own dashboard.
        await page.goto(`/course-management/${course.id}/exams/${exam.id}/test-runs`);
        await expect(examTestRun.getSubmitted(testRun.id!).filter({ hasText: 'Yes' })).toBeVisible();
        await page.goto(`/course-management/${course.id}/exams/${exam.id}/test-runs/assess`);
        await courseAssessment.clickExerciseDashboardButton(0, EXAM_DASHBOARD_TIMEOUT);
        // The dashboard of a test run lists its submissions; the one of the instructor is opened for the assessment.
        await expect(page.getByRole('heading', { name: 'Test Run submissions' })).toBeVisible();
        await page.locator('#continue-assessment, [data-testid="open-assessment"]').first().click();
        await examAssessment.addNewFeedback(6, 'Fine for a test run');
        expect((await examAssessment.submitTextAssessment()).status()).toBe(200);

        // The summary of the test run shows the answer and the result of the assessment.
        await page.goto(`/course-management/${course.id}/exams/${exam.id}/test-runs/${testRun.id}/summary`);
        await expect(page.getByTestId('achieved-percentage').first()).toContainText('60', { timeout: EXAM_DASHBOARD_TIMEOUT });
        await expect(page.getByText('Fine for a test run').first()).toBeVisible();
        await expect(page.getByText('At vero eos et accusam').first()).toBeVisible();
    });
});
