import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { admin } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

/**
 * Comprehensive E2E tests for quiz exercise creation and editing lifecycle.
 * These tests verify that quiz content is correctly saved, rendered in
 * edit view, preview, and solution views.
 */
test.describe('Quiz Exercise Lifecycle', { tag: '@fast' }, () => {
    test.describe('Comprehensive Quiz Creation', () => {
        let createdQuizId: number | undefined;

        test.afterEach('Delete created quiz', async ({ login, exerciseAPIRequests }) => {
            if (createdQuizId) {
                await login(admin);
                await exerciseAPIRequests.deleteQuizExercise(createdQuizId);
                createdQuizId = undefined;
            }
        });

        test('Creates quiz with MC + SA, verifies preview and solution', async ({ page, login, courseManagement, courseManagementExercises, quizExerciseCreation }) => {
            const quizTitle = 'LCQ' + generateUUID().substring(0, 5);
            const mcTitle = 'MC Lifecycle';
            const saTitle = 'SA Lifecycle';
            const answerOptions = ['Correct A', 'Correct B', 'Wrong C', 'Wrong D'];

            // Create quiz with MC + SA questions via UI
            await login(admin, '/course-management/');
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.createQuizExercise();
            await quizExerciseCreation.setTitle(quizTitle);
            await quizExerciseCreation.createAndEditMultipleChoiceQuestionInVisualMode(mcTitle, answerOptions);
            await quizExerciseCreation.addShortAnswerQuestion(saTitle);

            // Save and verify response
            const quizResponse = await quizExerciseCreation.saveQuiz();
            const quiz: QuizExercise = await quizResponse.json();
            createdQuizId = quiz.id;
            expect(quiz.id).toBeDefined();
            expect(quiz.quizQuestions).toHaveLength(2);

            // Verify preview renders both questions with full content
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/preview`);
            await expect(page.getByText(mcTitle)).toBeVisible({ timeout: 15000 });
            for (const option of answerOptions) {
                await expect(page.getByText(option, { exact: true })).toBeVisible();
            }
            await expect(page.getByText(saTitle)).toBeVisible();
            await expect(page.getByText('Never gonna').first()).toBeVisible();

            // Verify solution view renders both questions with correct answers
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/solution`);
            await expect(page.getByText(mcTitle)).toBeVisible({ timeout: 15000 });
            await expect(page.getByText('Correct A', { exact: true })).toBeVisible();
            await expect(page.getByText(saTitle)).toBeVisible();
        });
    });

    test.describe('Quiz Edit View and Content Verification', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create quiz via API', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceTemplate],
                title: 'EQ' + generateUUID().substring(0, 5),
            });
        });

        test.afterEach('Delete quiz', async ({ login, exerciseAPIRequests }) => {
            if (quizExercise?.id) {
                await login(admin);
                await exerciseAPIRequests.deleteQuizExercise(quizExercise.id);
            }
        });

        test('Loads existing quiz in edit view and verifies content in all views', async ({ page, login }) => {
            // Step 1: Navigate to edit page and verify quiz data loads
            await login(admin, `/course-management/${course.id}/quiz-exercises/${quizExercise.id}/edit`);
            const titleField = page.locator('#field_title');
            await expect(titleField).toHaveValue(quizExercise.title!, { timeout: 30000 });

            // Step 2: Verify MC question renders in the edit view
            const mcQuestionTitle = page.locator('#mc-question-title');
            await expect(mcQuestionTitle).toBeVisible({ timeout: 10000 });
            await expect(mcQuestionTitle).toHaveValue(multipleChoiceTemplate.title);

            // Step 3: Verify score field is populated (confirms question data loaded)
            const scoreField = page.locator('#score');
            await expect(scoreField).toHaveValue(multipleChoiceTemplate.points.toString());

            // Step 4: Verify preview renders the quiz question with answer options
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quizExercise.id}/preview`);
            await expect(page.getByText(multipleChoiceTemplate.title)).toBeVisible({ timeout: 15000 });
            await expect(page.getByText(multipleChoiceTemplate.answerOptions[0].text)).toBeVisible();
            await expect(page.getByText(multipleChoiceTemplate.answerOptions[1].text)).toBeVisible();
            await expect(page.getByText(multipleChoiceTemplate.answerOptions[2].text)).toBeVisible();
            await expect(page.getByText(multipleChoiceTemplate.answerOptions[3].text)).toBeVisible();

            // Step 5: Verify solution view renders correctly with correctness indicators
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quizExercise.id}/solution`);
            await expect(page.getByText(multipleChoiceTemplate.title)).toBeVisible({ timeout: 15000 });
            // All answer options should be visible in solution view
            for (const option of multipleChoiceTemplate.answerOptions) {
                await expect(page.getByText(option.text)).toBeVisible();
            }
        });
    });
});
