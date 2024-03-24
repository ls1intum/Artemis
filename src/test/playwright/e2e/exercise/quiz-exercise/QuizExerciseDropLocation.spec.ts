import { Course } from 'app/entities/course.model';

import { admin } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

let course: Course;

test.describe('Quiz Exercise Drop Location Spec', () => {
    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
    });

    test.describe('DnD Quiz drop locations', () => {
        test.beforeEach('Create DND quiz', async ({ login, courseManagementExercises, quizExerciseCreation, quizExerciseDragAndDropQuiz }) => {
            await login(admin, '/course-management/' + course.id + '/exercises');
            await courseManagementExercises.createQuizExercise();
            await quizExerciseCreation.setTitle('Quiz Exercise ' + generateUUID());
            await quizExerciseDragAndDropQuiz.createDnDQuiz('DnD Quiz Test');
        });

        test('Checks drop locations', async ({ page, quizExerciseDragAndDropQuiz }) => {
            await quizExerciseDragAndDropQuiz.dragUsingCoordinates(0, 100);
            await quizExerciseDragAndDropQuiz.dragUsingCoordinates(410, 240);
            await quizExerciseDragAndDropQuiz.dragUsingCoordinates(420, 90);

            await quizExerciseDragAndDropQuiz.activateInteractiveMode();

            await quizExerciseDragAndDropQuiz.markElementAsInteractive(0, 2);
            await quizExerciseDragAndDropQuiz.markElementAsInteractive(1, 1);
            await quizExerciseDragAndDropQuiz.markElementAsInteractive(2, 1);
            await quizExerciseDragAndDropQuiz.markElementAsInteractive(2, 2);

            await quizExerciseDragAndDropQuiz.generateQuizExercise();
            await quizExerciseDragAndDropQuiz.waitForQuizExerciseToBeGenerated();

            await page.goto(`/course-management/${course.id}/exercises`);
            await quizExerciseDragAndDropQuiz.previewQuiz();
            await quizExerciseDragAndDropQuiz.waitForQuizPreviewToLoad();

            const containerBounds = await page.locator('.click-layer').first().boundingBox();

            const { minX, maxX } = await quizExerciseDragAndDropQuiz.getXAxis(page.locator('.drop-location'));
            expect(containerBounds!.x + containerBounds!.width - maxX).toBeGreaterThan(0);
            expect(minX - containerBounds!.x).toBeGreaterThan(0);
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
