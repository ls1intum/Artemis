import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../support/seedData';

const course = { id: SEED_COURSES.general.id } as any;

test.describe('Course exercise', { tag: '@fast' }, () => {
    test.describe('Search Exercise', () => {
        let anchorExercise: TextExercise;
        let exercise1: QuizExercise;
        let exercise2: QuizExercise;
        let exercise3: QuizExercise;

        test.beforeEach('Create Exercises', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            anchorExercise = await exerciseAPIRequests.createTextExercise({ course }, 'Sidebar filter anchor');
            exercise1 = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate], title: 'Course Exercise Quiz 1' });
            exercise2 = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate], title: 'Course Exercise Quiz 2' });
            exercise3 = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate], title: 'Course Exercise 3' });
        });

        test('Filters exercises based on title', async ({ page, courseOverview }) => {
            // An explicit, accessible exercise keeps automatic selection from redirecting to an unstarted quiz.
            await page.goto(`/courses/${course.id}/exercises/${anchorExercise.id}`);
            await page.waitForLoadState('domcontentloaded');
            // Automatic exercise selection can open an accordion during navigation. Establish a broad search first
            // so narrowing the query tests filtering independently of that transient collapse state.
            const searchInput = page.locator('jhi-sidebar jhi-search-filter input');
            await searchInput.fill('Course Exercise');
            await expect(courseOverview.getExercise(exercise1.title!)).toBeVisible();
            await expect(courseOverview.getExercise(exercise2.title!)).toBeVisible();
            await expect(courseOverview.getExercise(exercise3.title!)).toBeVisible();
            await searchInput.fill('Course Exercise Quiz');
            await expect(courseOverview.getExercise(exercise1.title!)).toBeVisible();
            await expect(courseOverview.getExercise(exercise2.title!)).toBeVisible();
            await expect(courseOverview.getExercise(exercise3.title!)).toBeHidden();
        });

        test.afterEach('Delete Exercises', async ({ exerciseAPIRequests }) => {
            await exerciseAPIRequests.deleteQuizExercise(exercise1.id!);
            await exerciseAPIRequests.deleteQuizExercise(exercise2.id!);
            await exerciseAPIRequests.deleteQuizExercise(exercise3.id!);
            await exerciseAPIRequests.deleteTextExercise(anchorExercise.id!);
        });
    });

    // Seed courses are persistent — no cleanup needed
});
