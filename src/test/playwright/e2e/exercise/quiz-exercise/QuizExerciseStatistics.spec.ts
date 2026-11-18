import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { admin, instructor, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { QuizMode } from '../../../support/constants';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.quizParticipation.id } as any;

test.describe('Quiz Exercise Statistics', { tag: '@fast' }, () => {
    let quizExercise: QuizExercise;

    test.beforeEach('Create an individual-mode MC quiz and give the instructor access', async ({ login, exerciseAPIRequests, courseManagementAPIRequests }) => {
        await login(admin);
        // Individual mode so the instructor can end the quiz on demand: end-now rejects synchronized quizzes, whereas
        // for an individual quiz it ends the quiz and calculates the rated result immediately, without waiting for the
        // scheduled evaluation job. This keeps the test deterministic and fast.
        quizExercise = await exerciseAPIRequests.createQuizExercise({
            body: { course },
            quizQuestions: [multipleChoiceQuizTemplate],
            releaseDate: dayjs().subtract(1, 'hour'),
            quizMode: QuizMode.INDIVIDUAL,
        });
        await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
    });

    /**
     * Regression test for the instructor quiz-statistics view. The quiz-JSON migration folded the per-question
     * statistics counters into a JSON column and changed each counter's wire shape from a nested object
     * ({@code answer}/{@code dropLocation}/{@code spot}: {...}) to a scalar id ({@code answerId}/{@code dropLocationId}/
     * {@code spotId}). The statistics page matches each counter to its answer option and only then publishes the chart
     * data and the participant count; before the fix that matching dereferenced an undefined nested object and threw, so
     * the whole view stayed blank / stuck at 0.
     * <p>
     * A student submits, the instructor ends and evaluates the quiz, and the instructor then opens the multiple-choice
     * statistics page. Asserting the rendered participant count is 1 proves the client consumed the scalar counters,
     * matched them to the answer options, and completed {@code loadDataInDiagram} without crashing — the count is only
     * assigned after that counter-matching loop finishes. (The page also receives the same statistic live over the
     * {@code /topic/statistic} websocket; the render path exercised here is shared by both the REST and websocket feeds.)
     */
    test('Multiple choice statistics view renders the evaluated participant count', async ({ login, page, quizExerciseMultipleChoice, quizExerciseParticipation }) => {
        // A student starts an individual attempt and submits.
        await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id!}`);
        await quizExerciseParticipation.startQuizBatch();
        await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
        await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 0);
        expect((await quizExerciseMultipleChoice.submit()).status()).toBe(200);

        // The instructor ends and evaluates the quiz (via the instructor session's request context), which creates the
        // rated result and recomputes the per-question statistic. Evaluation is synchronous, so the statistic is
        // persisted by the time these calls return.
        await login(instructor, `/course-management/${course.id}`);
        expect((await page.request.put(`api/quiz/quiz-exercises/${quizExercise.id!}/end-now`)).ok()).toBeTruthy();
        expect((await page.request.post(`api/quiz/quiz-exercises/${quizExercise.id!}/evaluate`)).ok()).toBeTruthy();

        // Open the multiple-choice statistics page and assert the rendered participant count reflects the one submission.
        const questionId = quizExercise.quizQuestions![0].id!;
        await login(instructor, `/course-management/${course.id}/quiz-exercises/${quizExercise.id!}/mc-question-statistic/${questionId}`);
        const participants = page.locator('.chart-title-text', { hasText: 'Participants' });
        // The generous timeout only absorbs the cold-worker latency of the first deep-linked statistics render
        // (login -> quiz find -> chart bootstrap); the value itself is deterministic once the statistic is loaded.
        await expect(participants).toContainText('1', { timeout: 30_000 });
    });

    // Seed courses are persistent - no cleanup needed
});
