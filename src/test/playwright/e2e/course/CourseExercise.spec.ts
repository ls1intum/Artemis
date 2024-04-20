import { test } from '../../support/fixtures';
import { Course } from 'app/entities/course.model';
import { admin } from '../../support/users';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import multipleChoiceQuizTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import { expect } from '@playwright/test';

test.describe('Course exercise', () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
    });

    test.describe('Search Exercise', () => {
        let exercise1: QuizExercise;
        let exercise2: QuizExercise;
        let exercise3: QuizExercise;

        test.beforeEach('Create Exercises', async ({ exerciseAPIRequests }) => {
            exercise1 = await exerciseAPIRequests.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise Quiz 1');
            exercise2 = await exerciseAPIRequests.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise Quiz 2');
            exercise3 = await exerciseAPIRequests.createQuizExercise({ course }, [multipleChoiceQuizTemplate], 'Course Exercise 3');
        });

        test('Filters exercises based on title', async ({ page, courseOverview }) => {
            await page.goto(`/courses/${course.id}/exercises`);
            await expect(courseOverview.getExercise(exercise1.title!)).toBeVisible();
            await expect(courseOverview.getExercise(exercise2.title!)).toBeVisible();
            await expect(courseOverview.getExercise(exercise3.title!)).toBeVisible();
            await courseOverview.search('Course Exercise Quiz');
            await expect(courseOverview.getExercise(exercise1.title!)).toBeVisible();
            await expect(courseOverview.getExercise(exercise2.title!)).toBeVisible();
            await expect(courseOverview.getExercise(exercise3.title!)).toBeHidden();
        });

        test.afterEach('Delete Exercises', async ({ exerciseAPIRequests }) => {
            await exerciseAPIRequests.deleteQuizExercise(exercise1.id!);
            await exerciseAPIRequests.deleteQuizExercise(exercise2.id!);
            await exerciseAPIRequests.deleteQuizExercise(exercise3.id!);
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
