import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import { admin } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

test.describe('Quiz Exercise Management', () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
    });

    test.describe('Quiz Exercise Creation', () => {
        test.beforeEach('Create quiz exercise', async ({ login, courseManagement, courseManagementExercises, quizExerciseCreation }) => {
            await login(admin, '/course-management/');
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.createQuizExercise();
            await quizExerciseCreation.setTitle('Quiz Exercise ' + generateUUID());
        });

        test('Creates a Quiz with Multiple Choice', async ({ page, quizExerciseCreation }) => {
            const title = 'Multiple Choice Quiz';
            await quizExerciseCreation.addMultipleChoiceQuestion(title);
            const quizResponse = await quizExerciseCreation.saveQuiz();
            const quiz: QuizExercise = await quizResponse.json();
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/preview`);
            await expect(page.getByText(title)).toBeVisible();
        });

        test('Creates a Quiz with Short Answer', async ({ page, quizExerciseCreation }) => {
            const title = 'Short Answer Quiz';
            await quizExerciseCreation.addShortAnswerQuestion(title);
            const quizResponse = await quizExerciseCreation.saveQuiz();
            const quiz: QuizExercise = await quizResponse.json();
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/preview`);
            await expect(page.getByText(title)).toBeVisible();
        });

        test.skip('Creates a Quiz with Drag and Drop', async ({ page, quizExerciseCreation }) => {
            const quizQuestionTitle = 'Quiz Question';
            await quizExerciseCreation.addDragAndDropQuestion(quizQuestionTitle);
            const response = await quizExerciseCreation.saveQuiz();
            const quiz = await response.json();
            await page.goto(`/course-management/${course.id}/quiz-exercises/${quiz.id}/preview`);
            await expect(page.getByText(quizQuestionTitle)).toBeVisible();
        });
    });

    test.describe('Quiz Exercise Deletion', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({ course }, [multipleChoiceTemplate]);
        });

        test('Deletes a quiz exercise', async ({ login, navigationBar, courseManagement, courseManagementExercises }) => {
            await login(admin, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.deleteQuizExercise(quizExercise);
            await expect(courseManagementExercises.getExercise(quizExercise.id!)).not.toBeAttached();
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
