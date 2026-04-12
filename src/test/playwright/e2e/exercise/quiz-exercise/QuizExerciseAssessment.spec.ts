import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.quizAssessment.id } as any;

test.describe('Quiz Exercise Assessment', { tag: '@fast' }, () => {
    test.describe('MC Quiz assessment', () => {
        test.describe.configure({ retries: 2 });
        test('Assesses a mc quiz submission automatically', async ({ login, page, exerciseAPIRequests, exerciseResult }) => {
            await login(admin);
            const quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate], duration: 10 });
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
            await login(studentOne);
            await exerciseAPIRequests.startExerciseParticipation(quizExercise.id!);
            await exerciseAPIRequests.createMultipleChoiceSubmission(quizExercise, [0, 2]);
            await page.goto(`/courses/${course.id}/exercises/${quizExercise.id}`);
            await page.waitForLoadState('networkidle');
            await exerciseResult.shouldShowScore(50);
        });
    });

    test.describe('SA Quiz assessment', () => {
        test.describe.configure({ retries: 2 });
        test('Assesses a sa quiz submission automatically', async ({ login, page, exerciseAPIRequests, exerciseResult }) => {
            await login(admin);
            const quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [shortAnswerQuizTemplate], duration: 10 });
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
            await login(studentOne);
            await exerciseAPIRequests.startExerciseParticipation(quizExercise.id!);
            await exerciseAPIRequests.createShortAnswerSubmission(quizExercise, ['give', 'let', 'run', 'desert']);
            await page.goto(`/courses/${course.id}/exercises/${quizExercise.id}`);
            await page.waitForLoadState('networkidle');
            await exerciseResult.shouldShowScore(66.7);
        });
    });

    // Seed courses are persistent — no cleanup needed
});
