import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { admin, studentOne } from '../../support/users';
import { asAdmin, getExercise } from '../../support/utils';
import { SEED_COURSES } from '../../support/seedData';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { RELOAD_RENDER_TIMEOUT } from '../../support/timeouts';

const course = { id: SEED_COURSES.examParticipation.id } as any;
const textFixture = 'loremIpsum-short.txt';
// The text of the example solution of the exercise template.
const exampleSolution = 'Example Solution';

/**
 * Students who took an exam get to see the example solutions of its exercises from the example solution publication date on. Until then
 * the summary does not offer them, and the server does not hand them out.
 */
test.describe('Exam example solution', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('The example solution is hidden until its publication date, then the student can show and hide it', async ({
        browser,
        page,
        login,
        examAPIRequests,
        exerciseAPIRequests,
        examParticipation,
        examNavigation,
    }) => {
        await login(admin);
        exam = await examAPIRequests.createRunningExam({ course, gracePeriod: 5 });
        const exerciseGroup = await examAPIRequests.addExerciseGroupForExam(exam);
        const exercise = await exerciseAPIRequests.createTextExercise({ exerciseGroup });
        await examAPIRequests.registerStudentForExam(exam, studentOne);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);

        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(exerciseGroup.title!);
        await examParticipation.makeTextExerciseSubmission(exercise.id!, textFixture);
        await examParticipation.handInEarly();

        // The exam is over and its results are published, but no example solution publication date is set.
        await asAdmin(browser, (adminExamRequests) => adminExamRequests.concludeExam(exam));
        await login(studentOne, `/courses/${course.id}/exams/${exam.id}`);
        await expect(page.getByRole('heading', { name: /Exam Results/ })).toBeVisible({ timeout: RELOAD_RENDER_TIMEOUT });
        await expect(page.locator(`#show-sample-solution-button-${exercise.id}`)).toHaveCount(0);
        const early = await page.request.get(`api/exercise/exercises/${exercise.id}/example-solution`);
        expect(early.status()).toBe(403);

        // The publication date arrives.
        const publishedAt = await asAdmin(browser, (adminExamRequests) => adminExamRequests.publishExampleSolutionIn(exam, 8));
        await asAdmin(browser, (adminExamRequests) => adminExamRequests.waitUntilServerClockIsAfter(exam, publishedAt));
        await page.reload();
        const toggle = page.locator(`#show-sample-solution-button-${exercise.id}`);
        await expect(toggle).toBeVisible({ timeout: RELOAD_RENDER_TIMEOUT });
        const published = await page.request.get(`api/exercise/exercises/${exercise.id}/example-solution`);
        expect(published.status()).toBe(200);
        expect((await published.json()).exampleSolution).toBe(exampleSolution);

        // The student can show the example solution next to their answer, and hide it again.
        await expect(page.getByText('You are viewing the example solution')).toHaveCount(0);
        await toggle.click();
        await expect(page.getByText('You are viewing the example solution')).toBeVisible();
        await expect(getExercise(page, exercise.id!)).toContainText(exampleSolution);
        await expect(toggle).toContainText('Hide Example Solution');
        await toggle.click();
        await expect(page.getByText('You are viewing the example solution')).toHaveCount(0);
        await expect(toggle).toContainText('Show Example Solution');
    });
});
