import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { admin } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect, Page } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

/**
 * Helper to verify that the MC question from the template renders correctly in preview/solution views.
 * Asserts: question title, question text, and all answer option texts.
 */
async function assertMCQuestionInView(page: Page, title: string) {
    await expect(page.getByText(title)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(multipleChoiceTemplate.text)).toBeVisible();
    for (const option of multipleChoiceTemplate.answerOptions) {
        await expect(page.getByText(option.text)).toBeVisible();
    }
}

/**
 * Helper to verify that the SA question from the template renders correctly in preview/solution views.
 * Asserts: question title, question body text fragments, and input spots are present.
 */
async function assertSAQuestionInView(page: Page) {
    await expect(page.getByText(shortAnswerTemplate.title)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('Never gonna').first()).toBeVisible();
    await expect(page.getByText('you up').first()).toBeVisible();
    await expect(page.getByText('you down').first()).toBeVisible();
    const spotInputs = page.locator('input.short-answer-question-container__input');
    await expect(spotInputs.first()).toBeVisible({ timeout: 5000 });
    const spotCount = await spotInputs.count();
    expect(spotCount).toBe(shortAnswerTemplate.spots.length);
}

/**
 * Helper to verify SA solution view renders the question with answers filled in.
 */
async function assertSASolutionView(page: Page) {
    await expect(page.getByText(shortAnswerTemplate.title)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText('Never gonna').first()).toBeVisible();
    await expect(page.getByText('you up').first()).toBeVisible();
    const solutionInputs = page.locator('input.short-answer-question-container__input');
    await expect(solutionInputs.first()).toBeVisible({ timeout: 5000 });
    const inputCount = await solutionInputs.count();
    expect(inputCount).toBe(shortAnswerTemplate.spots.length);
    await expect(solutionInputs.first()).toHaveValue(shortAnswerTemplate.solutions[0].text);
}

/**
 * Comprehensive E2E tests for quiz exercise creation and editing lifecycle.
 * Verifies that quiz content is correctly saved and rendered in edit, preview, and solution views.
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

            await login(admin, '/course-management/');
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.createQuizExercise();
            await quizExerciseCreation.setTitle(quizTitle);
            await quizExerciseCreation.createAndEditMultipleChoiceQuestionInVisualMode(mcTitle, answerOptions);
            await quizExerciseCreation.addShortAnswerQuestion(saTitle);

            const quizResponse = await quizExerciseCreation.saveQuiz();
            const quiz: QuizExercise = await quizResponse.json();
            createdQuizId = quiz.id;
            expect(quiz.id).toBeDefined();
            expect(quiz.title).toBe(quizTitle);
            expect(quiz.quizQuestions).toHaveLength(2);
            expect(quiz.quizQuestions![0].type).toBe('multiple-choice');
            expect(quiz.quizQuestions![1].type).toBe('short-answer');

            // Preview: verify both questions render fully
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/preview`);
            await expect(page.getByText(mcTitle)).toBeVisible({ timeout: 15000 });
            for (const option of answerOptions) {
                await expect(page.getByText(option, { exact: true })).toBeVisible();
            }
            await expect(page.getByText(saTitle)).toBeVisible();
            await expect(page.getByText('Never gonna').first()).toBeVisible();

            // Solution: verify both questions
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/solution`);
            await expect(page.getByText(mcTitle)).toBeVisible({ timeout: 15000 });
            for (const option of answerOptions) {
                await expect(page.getByText(option, { exact: true })).toBeVisible();
            }
            await expect(page.getByText(saTitle)).toBeVisible();
        });
    });

    test.describe('Quiz with Competency Links', () => {
        let quizExercise: QuizExercise;
        let competencyId: number;

        test.beforeEach('Create competency and quiz with links', async ({ login, exerciseAPIRequests, courseManagementAPIRequests }) => {
            await login(admin);
            // Create a competency in the course
            const competency = await courseManagementAPIRequests.createCompetency(course, 'Quiz Competency ' + generateUUID().substring(0, 5));
            competencyId = competency.id!;

            // Create quiz WITH competency link
            quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceTemplate, shortAnswerTemplate],
                title: 'CQ' + generateUUID().substring(0, 5),
                competencyLinks: [{ competency: { id: competencyId }, weight: 1 }],
            });
        });

        test.afterEach('Delete quiz', async ({ login, exerciseAPIRequests }) => {
            if (quizExercise?.id) {
                await login(admin);
                await exerciseAPIRequests.deleteQuizExercise(quizExercise.id);
            }
        });

        test('Quiz created with competency has questions persisted and renderable', async ({ page, login }) => {
            // Verify quiz was created with questions (catches exercise_id = NULL bug)
            expect(quizExercise.id).toBeDefined();
            expect(quizExercise.quizQuestions).toHaveLength(2);

            // Edit view: verify quiz data loads (questions must be in DB)
            await login(admin, `/course-management/${course.id}/quiz-exercises/${quizExercise.id}/edit`);
            const titleField = page.locator('#field_title');
            await expect(titleField).toHaveValue(quizExercise.title!, { timeout: 30000 });
            const mcQuestionTitle = page.locator('#mc-question-title');
            await expect(mcQuestionTitle).toBeVisible({ timeout: 10000 });
            await expect(mcQuestionTitle).toHaveValue(multipleChoiceTemplate.title);

            // Preview: verify BOTH questions render (proves exercise_id FK is set)
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quizExercise.id}/preview`);
            await assertMCQuestionInView(page, multipleChoiceTemplate.title);
            await assertSAQuestionInView(page);

            // Solution: verify both questions
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quizExercise.id}/solution`);
            await assertMCQuestionInView(page, multipleChoiceTemplate.title);
            await assertSASolutionView(page);
        });
    });

    test.describe('Quiz Edit View and Content Verification', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create quiz with MC + SA via API', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceTemplate, shortAnswerTemplate],
                title: 'EQ' + generateUUID().substring(0, 5),
            });
        });

        test.afterEach('Delete quiz', async ({ login, exerciseAPIRequests }) => {
            if (quizExercise?.id) {
                await login(admin);
                await exerciseAPIRequests.deleteQuizExercise(quizExercise.id);
            }
        });

        test('Loads quiz with MC + SA in edit view, verifies all views render correctly', async ({ page, login }) => {
            await login(admin, `/course-management/${course.id}/quiz-exercises/${quizExercise.id}/edit`);
            const titleField = page.locator('#field_title');
            await expect(titleField).toHaveValue(quizExercise.title!, { timeout: 30000 });

            const mcQuestionTitle = page.locator('#mc-question-title');
            await expect(mcQuestionTitle).toBeVisible({ timeout: 10000 });
            await expect(mcQuestionTitle).toHaveValue(multipleChoiceTemplate.title);
            const scoreField = page.locator('#score').first();
            await expect(scoreField).toHaveValue(multipleChoiceTemplate.points.toString());

            // Preview
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quizExercise.id}/preview`);
            await assertMCQuestionInView(page, multipleChoiceTemplate.title);
            await assertSAQuestionInView(page);

            // Solution
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quizExercise.id}/solution`);
            await assertMCQuestionInView(page, multipleChoiceTemplate.title);
            await assertSASolutionView(page);
        });
    });
});
