import { admin } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

test.describe('Quiz Exercise Drop Location Spec', { tag: '@slow' }, () => {
    test.describe('DnD Quiz drop locations', () => {
        test.beforeEach('Create DND quiz', async ({ login, courseManagementExercises, quizExerciseCreation, quizExerciseDragAndDropQuiz }) => {
            await login(admin, '/course-management/' + course.id + '/exercises');
            await courseManagementExercises.createQuizExercise();
            await quizExerciseCreation.setTitle('Quiz Exercise ' + generateUUID());
            await quizExerciseDragAndDropQuiz.createDnDQuiz('DnD Quiz Test');
        });

        // TODO: Enable test again after fixing https://github.com/ls1intum/Artemis/issues/12418
        test.skip('Checks drop locations', async ({ page, quizExerciseDragAndDropQuiz }) => {
            await quizExerciseDragAndDropQuiz.dragUsingCoordinates(0, 100);
            await quizExerciseDragAndDropQuiz.dragUsingCoordinates(410, 240);
            await quizExerciseDragAndDropQuiz.dragUsingCoordinates(420, 90);

            const exerciseId = await quizExerciseDragAndDropQuiz.generateQuizExercise();
            await quizExerciseDragAndDropQuiz.waitForQuizExerciseToBeGenerated();

            // Navigate directly to the exercise detail to preview, avoiding slow exercises list
            await page.goto(`/course-management/${course.id}/quiz-exercises/${exerciseId}/preview`);
            await quizExerciseDragAndDropQuiz.waitForQuizPreviewToLoad();

            const containerBounds = await page.locator('.click-layer').first().boundingBox();

            const { minX, maxX } = await quizExerciseDragAndDropQuiz.getXAxis(page.locator('.drop-location'));
            // Verify drop locations are within the container bounds (not overflowing)
            expect(containerBounds!.x + containerBounds!.width - maxX).toBeGreaterThanOrEqual(0);
            expect(minX - containerBounds!.x).toBeGreaterThanOrEqual(0);
        });
    });

    // Seed courses are persistent — no cleanup needed
});
