import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { admin } from '../../../support/users';
import { generateUUID, readResponseJson } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { promises as fs } from 'fs';
import { SEED_COURSES } from '../../../support/seedData';
import dayjs from 'dayjs';
import { expectInside, expectNoOverlap, forEachViewport, measure } from '../../../support/layout';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

test.describe('Quiz Exercise Management', { tag: '@fast' }, () => {
    test.describe('Quiz Exercise Creation', () => {
        let createdQuizId: number | undefined;
        // Kept in scope so a test can look its quiz up by title when the create response body is gone
        // (see the drag-and-drop test below).
        let quizTitle: string;

        test.beforeEach('Create quiz exercise', async ({ login, courseManagement, courseManagementExercises, quizExerciseCreation }) => {
            createdQuizId = undefined;
            quizTitle = 'Quiz Exercise ' + generateUUID();
            await login(admin, '/courses');
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.createQuizExercise();
            await quizExerciseCreation.setTitle(quizTitle);
        });

        test.afterEach('Delete created quiz', async ({ login, exerciseAPIRequests }) => {
            if (createdQuizId) {
                await login(admin);
                await exerciseAPIRequests.deleteQuizExercise(createdQuizId);
                createdQuizId = undefined;
            }
        });

        test('Creates a Quiz with Multiple Choice', async ({ page, quizExerciseCreation }) => {
            const title = 'Multiple Choice Quiz';
            await quizExerciseCreation.addMultipleChoiceQuestion(title);
            const quizResponse = await quizExerciseCreation.saveQuiz();
            const quiz: QuizExercise = await readResponseJson(quizResponse);
            createdQuizId = quiz.id;
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/preview`);
            await page.waitForLoadState('domcontentloaded');
            await expect(page.getByText(title)).toBeVisible();
        });

        test('Creates a Quiz with Multiple Choice (Visual)', async ({ page, quizExerciseCreation }) => {
            // Visual-mode MC creation with 6 answer options + saveQuiz response wait can
            // exceed the 60s @fast budget under multi-node CI load. Also the preview page
            // lazy-mounts the question component — first paint of the title can take >10s
            // (default expect timeout). Lift the test budget via test.slow() and extend
            // per-element timeouts to 30s.
            test.slow();
            const title = 'Multiple Choice Quiz Visual';
            const answerOptions = [0, 1, 2, 3, 4, 5].map((answerOptionNumber) => {
                return `Answer Option ${answerOptionNumber}`;
            });
            await quizExerciseCreation.createAndEditMultipleChoiceQuestionInVisualMode(title, answerOptions);
            const quizResponse = await quizExerciseCreation.saveQuiz();
            const quiz: QuizExercise = await readResponseJson(quizResponse);
            createdQuizId = quiz.id;
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/preview`);
            await page.waitForLoadState('domcontentloaded');
            await expect(page.getByText(title)).toBeVisible({ timeout: 30000 });
            for (const answerOption of answerOptions) {
                await expect(page.getByText(answerOption)).toBeVisible({ timeout: 30000 });
            }
        });

        test('Creates a Quiz with Short Answer', async ({ page, quizExerciseCreation }) => {
            const title = 'Short Answer Quiz';
            await quizExerciseCreation.addShortAnswerQuestion(title);
            const quizResponse = await quizExerciseCreation.saveQuiz();
            const quiz: QuizExercise = await readResponseJson(quizResponse);
            createdQuizId = quiz.id;
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/preview`);
            await page.waitForLoadState('domcontentloaded');
            // The preview page lazy-mounts the question component; extend timeout to match
            // the DnD/MC-Visual tests which already use 30s for the same reason.
            await expect(page.getByText(title)).toBeVisible({ timeout: 30000 });
        });

        test('Creates a Quiz with Drag and Drop', async ({ page, quizExerciseCreation, exerciseAPIRequests }) => {
            // DnD question creation involves a background image upload, two mouse drag
            // sequences, a Monaco editor mount, and a quiz save (with its own 60s response
            // wait). Cumulative cost routinely overruns the 60s @fast budget under multi-node
            // CI load; observed worst case ~130s including the preview-page render. Lift the
            // per-test timeout to 180s via test.slow().
            test.slow();
            const quizQuestionTitle = 'Quiz Question';
            await quizExerciseCreation.addDragAndDropQuestion(quizQuestionTitle);
            const response = await quizExerciseCreation.saveQuiz();
            // The drag-and-drop background image is a disk-backed file, so this create response cannot be held
            // in Node and Chrome discards it when the editor navigates away on save. Fall back to an idempotent
            // lookup instead of failing the test on a body that no longer exists.
            const quiz = await readResponseJson<QuizExercise>(response, () => exerciseAPIRequests.getQuizExerciseByTitle(course.id!, quizTitle));
            createdQuizId = quiz.id;
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/preview`);
            await page.waitForLoadState('domcontentloaded');
            // The quiz-preview page mounts its question component lazily; under load the
            // visible text can take >5s default-expect-timeout to appear. Extend to 30s.
            await expect(page.getByText(quizQuestionTitle)).toBeVisible({ timeout: 30000 });
        });
    });

    test.describe('Quiz Exercise Deletion', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceTemplate] });
        });

        test('Deletes a quiz exercise', async ({ login, navigationBar, courseManagement, courseManagementExercises }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.deleteQuizExercise(quizExercise);
            await expect(courseManagementExercises.getExercise(quizExercise.id!)).not.toBeAttached();
        });
    });

    test.describe('Quiz Exercise Export', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceTemplate] });
        });

        test('Export quiz exercise questions', async ({ page, login, navigationBar, courseManagement, courseManagementExercises, quizExerciseOverview }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.openQuizExerciseDetailsPage(quizExercise.id!);
            const downloadPromise = new Promise<void>((resolve, reject) => {
                page.on('download', async (download) => {
                    const fileName = download.suggestedFilename();
                    expect(fileName).toBe(`${quizExercise.title}.json`);
                    const filePath = await download.path();
                    const fileContent = await fs.readFile(filePath, 'utf8');
                    const exportedExercise = JSON.parse(fileContent);
                    const exercise = [multipleChoiceTemplate];
                    expect(exportedExercise).toMatchObject(exercise);
                    resolve();
                });

                setTimeout(() => reject('Quiz questions export did not happen within the expected time frame'), 30000);
            });
            await quizExerciseOverview.exportQuizExercise();
            await expect(downloadPromise).resolves.toBeUndefined();
        });
    });

    test('Keeps German evaluation and variant actions readable on narrow screens', async ({ login, page, exerciseAPIRequests }) => {
        await page.route('**/management/info', async (route) => {
            const response = await route.fetch();
            const info = await response.json();
            info.activeModuleFeatures = [...new Set([...(info.activeModuleFeatures ?? []), 'hyperion'])];
            await route.fulfill({ response, json: info });
        });
        // Localize this browser without changing the seeded administrator's preference for other workers.
        await page.route('**/api/core/public/account', async (route) => {
            const response = await route.fetch();
            const account = await response.json();
            account.langKey = 'de';
            await route.fulfill({ response, json: account });
        });
        await login(admin);
        const quiz = await exerciseAPIRequests.createQuizExercise({
            body: { course },
            quizQuestions: [multipleChoiceTemplate],
            releaseDate: dayjs().subtract(3, 'hour'),
            startOfWorkingTime: dayjs().subtract(2, 'hour'),
            dueDate: dayjs().subtract(1, 'hour'),
        });
        try {
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}`);
            await expect(page.locator('html')).toHaveAttribute('lang', 'de');
            const editing = page.getByTestId('exercise-editing-actions');
            const toolbar = editing.locator('..');
            const controls = [
                editing.getByRole('link', { name: 'Überarbeiten', exact: true }),
                editing.getByTestId('quiz-evaluate-button').getByRole('button'),
                editing.getByTestId('create-variant-ai-button').getByRole('button'),
            ];
            await expect(controls[1]).toHaveAccessibleName('Quiz auswerten');
            const checkActions = async () => {
                for (const name of ['Teilnahmen', 'Statistiken', 'Vorschau', 'Lösung']) {
                    const navigation = toolbar.getByRole('link', { name, exact: true });
                    await expect.poll(async () => (await measure(navigation, { name })).width, { message: `${name} remains a compact button` }).toBeLessThanOrEqual(240);
                }
                for (const [index, control] of controls.entries()) {
                    const name = ['Re-evaluate', 'Evaluate quiz', 'Create Variant'][index];
                    await expectInside(control, toolbar, ['top', 'right', 'bottom', 'left'], { name: `${name} stays inside the quiz toolbar` });
                    await expect.poll(async () => (await measure(control, { name })).width, { message: `${name} retains a usable width` }).toBeGreaterThanOrEqual(32);
                    await expect.poll(async () => (await measure(control, { name })).height, { message: `${name} uses at most two text lines` }).toBeLessThanOrEqual(64);
                }
                await expectNoOverlap(controls, { name: 'Re-evaluate, Evaluate quiz and Create Variant' });
            };
            await forEachViewport(page, checkActions);
            await test.step('390px phone with the management sidebar', async () => {
                await page.setViewportSize({ width: 390, height: 844 });
                await checkActions();
            });
        } finally {
            await exerciseAPIRequests.deleteQuizExercise(quiz.id!);
        }
    });

    // Seed courses are persistent — no cleanup needed
});
