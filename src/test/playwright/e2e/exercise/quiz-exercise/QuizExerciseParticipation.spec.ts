import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

test.describe('Quiz Exercise Participation', () => {
    let course: Course;
    let quizExercise: QuizExercise;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
    });

    test.describe('Quiz exercise participation', () => {
        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({ course }, [multipleChoiceQuizTemplate]);
        });

        test('Student cannot see hidden quiz', async ({ login, courseOverview }) => {
            await login(studentOne, '/courses/' + course.id);
            await expect(courseOverview.getExercises()).toHaveCount(0);
        });

        test('Student can see a visible quiz', async ({ login, exerciseAPIRequests, courseOverview }) => {
            await login(admin);
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await login(studentOne, '/courses/' + course.id);
            await courseOverview.openRunningExercise(quizExercise.id!);
        });

        test('Student can participate in MC quiz', async ({ login, exerciseAPIRequests, courseOverview, quizExerciseMultipleChoice }) => {
            await login(admin);
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
            await login(studentOne, '/courses/' + course.id);
            await courseOverview.startExercise(quizExercise.id!);
            await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 0);
            await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 2);
            await quizExerciseMultipleChoice.submit();
        });
    });

    test.describe('SA quiz participation', () => {
        test.beforeEach('Create SA quiz', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({ course }, [shortAnswerQuizTemplate]);
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
        });

        test('Student can participate in SA quiz', async ({ login, courseOverview, quizExerciseShortAnswerQuiz }) => {
            const quizQuestionId = quizExercise.quizQuestions![0].id!;
            await login(studentOne, '/courses/' + course.id);
            await courseOverview.startExercise(quizExercise.id!);
            await quizExerciseShortAnswerQuiz.typeAnswer(0, 1, quizQuestionId, 'give');
            await quizExerciseShortAnswerQuiz.typeAnswer(1, 1, quizQuestionId, 'let');
            await quizExerciseShortAnswerQuiz.typeAnswer(2, 1, quizQuestionId, 'run');
            await quizExerciseShortAnswerQuiz.typeAnswer(2, 3, quizQuestionId, 'desert');
            await quizExerciseShortAnswerQuiz.typeAnswer(3, 1, quizQuestionId, 'cry');
            await quizExerciseShortAnswerQuiz.typeAnswer(4, 1, quizQuestionId, 'goodbye');
            await quizExerciseShortAnswerQuiz.submit();
        });
    });

    test.describe.skip('DnD Quiz participation', () => {
        test.beforeEach('Create DND quiz', async ({ login, courseManagementExercises, exerciseAPIRequests, quizExerciseCreation }) => {
            await login(admin, '/course-management/' + course.id + '/exercises');
            await courseManagementExercises.createQuizExercise();
            await quizExerciseCreation.setTitle('Cypress Quiz');
            await quizExerciseCreation.addDragAndDropQuestion('DnD Quiz');
            const response = await quizExerciseCreation.saveQuiz();
            quizExercise = await response.json();
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
        });

        test('Student can participate in DnD Quiz', async ({ login, courseOverview, quizExerciseDragAndDropQuiz }) => {
            await login(studentOne, '/courses/' + course.id);
            await courseOverview.startExercise(quizExercise.id!);
            await quizExerciseDragAndDropQuiz.dragItemIntoDragArea(0);
            await quizExerciseDragAndDropQuiz.submit();
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
