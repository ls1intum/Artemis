import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { test } from '../../../support/fixtures';
import { admin, studentOne, studentTwo } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';
import { ExerciseType } from '../../../support/constants';
import { Exam } from 'app/exam/shared/entities/exam.model';

const course = { id: SEED_COURSES.testExam.id } as any;

/**
 * A student who has handed in an attempt of a test exam may ask for AI feedback on it, a limited number of times. The E2E environment has no Athena
 * service, so what can be tested is what the server does around the request: it refuses a request that can not be fulfilled without using up one of the
 * limited requests, it only serves the owner of the attempt, and the button for the request is not offered where no feedback can be generated.
 */
test.describe('Test exam AI feedback requests', { tag: '@slow' }, () => {
    let exam: Exam;

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });

    test('Requests that can not be fulfilled are refused without using up a request, and only the owner may ask', async ({
        page,
        login,
        examAPIRequests,
        examExerciseGroupCreation,
        examParticipation,
        examNavigation,
        examStartEnd,
    }) => {
        await login(admin);
        exam = await examAPIRequests.createExam({
            course,
            testExam: true,
            startDate: dayjs().subtract(1, 'day'),
            visibleDate: dayjs().subtract(2, 'days'),
            examMaxPoints: 10,
            numberOfExercisesInExam: 1,
            workingTime: 3600,
        });
        const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture: 'loremIpsum-short.txt' });

        // The student works on an attempt, which is not handed in yet.
        await examParticipation.startParticipation(studentOne, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
        await examParticipation.makeTextExerciseSubmission(exercise.id!, 'loremIpsum-short.txt');
        const attemptId = Math.max(...(await examAPIRequests.getOwnTestExamAttempts(exam)).map((attempt) => attempt.id!));
        const base = `api/exam/courses/${course.id}/exams/${exam.id}/student-exams/${attemptId}`;
        const requestFeedback = async () => {
            const response = await page.request.post(`${base}/request-feedback`);
            return { status: response.status(), errorKey: ((await response.json().catch(() => ({}))) as { errorKey?: string }).errorKey };
        };
        const usage = async () => (await (await page.request.get(`${base}/athena-feedback-usage`)).json()) as { used: number; limit: number };

        // An attempt that is not handed in has nothing to give feedback on.
        expect(await requestFeedback()).toEqual({ status: 400, errorKey: 'studentExamNotSubmitted' });
        const before = await usage();
        expect(before.used).toBe(0);
        expect(before.limit, 'the number of requests is limited').toBeGreaterThan(0);

        // Handed in, the request is still refused, because the environment has no Athena to generate the feedback, and it does not use up a request.
        await examParticipation.handInEarly();
        await examStartEnd.pressShowSummary();
        const refused = await requestFeedback();
        expect(refused.status).toBe(400);
        expect(['athenaNotAvailable', 'noCourseLevelAthenaFormativeEnabled']).toContain(refused.errorKey);
        expect(await usage(), 'a refused request does not use up one of the requests').toEqual(before);

        // Without Athena the summary does not offer the request.
        await expect(page.locator('#requestAIFeedbackButton')).toHaveCount(0);

        // Only the student of the attempt may ask or look at the usage.
        await login(studentTwo);
        expect((await page.request.post(`${base}/request-feedback`)).status()).toBe(403);
        expect((await page.request.get(`${base}/athena-feedback-usage`)).status()).toBe(403);
    });
});
