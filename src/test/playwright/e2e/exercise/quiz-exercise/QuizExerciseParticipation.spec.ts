import { Course } from 'app/entities/course.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import multipleChoiceQuizTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import shortAnswerQuizTemplate from '../../../fixtures/exercise/quiz/short_answer/template.json';
import { admin, instructor, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import dayjs from 'dayjs';
import { QuizMode } from '../../../support/constants';

test.describe('Quiz Exercise Participation', { tag: '@fast' }, () => {
    let course: Course;

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
        await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
    });

    test.describe('Quiz exercise participation', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate] });
        });

        test('Student cannot see hidden quiz', async ({ login, courseOverview }) => {
            await login(studentOne, '/courses/' + course.id);
            await expect(courseOverview.getExercises()).toHaveCount(0);
        });

        test('Student can see a visible quiz', async ({ login, exerciseAPIRequests, courseOverview }) => {
            await login(admin);
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id!}`);
            await courseOverview.openRunningExercise(quizExercise.id!);
        });

        test('Student can participate in MC quiz', async ({ login, exerciseAPIRequests, courseOverview, quizExerciseMultipleChoice }) => {
            await login(admin);
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id!}`);
            await courseOverview.startExercise(quizExercise.id!);
            await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 0);
            await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 2);
            await quizExerciseMultipleChoice.submit();
        });
    });

    test.describe('Quiz exercise scheduled participation', () => {
        let quizExercise: QuizExercise;
        const timeUntilQuizStartInSeconds = 10;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            const releaseDate = dayjs();
            const startOfWorkingTime = releaseDate.add(timeUntilQuizStartInSeconds, 'seconds');
            quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [multipleChoiceQuizTemplate], releaseDate, startOfWorkingTime });
        });

        test('Student cannot participate in scheduled quiz before start of working time', async ({ login, courseOverview, quizExerciseParticipation }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await courseOverview.openRunningExercise(quizExercise.id!);
            await expect(quizExerciseParticipation.getWaitingForStartAlert()).toBeVisible();
        });

        test('Student can participate in scheduled quiz when working time arrives', async ({ page, login, courseOverview, quizExerciseParticipation }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await courseOverview.openRunningExercise(quizExercise.id!);
            await page.waitForTimeout(timeUntilQuizStartInSeconds * 1000);
            await expect(quizExerciseParticipation.getWaitingForStartAlert()).not.toBeVisible();
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
        });
    });

    test.describe('Quiz exercise batched participation', () => {
        let quizExercise: QuizExercise;
        const exerciseDuration = 60;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests, courseManagementAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceQuizTemplate],
                releaseDate: dayjs(),
                duration: exerciseDuration,
                quizMode: QuizMode.BATCHED,
            });
            await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        });

        test('Instructor creates a quiz batch and student joins it', async ({
            login,
            navigationBar,
            courseManagement,
            quizExerciseOverview,
            courseOverview,
            quizExerciseParticipation,
        }) => {
            await login(instructor);
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            const quizBatch = await quizExerciseOverview.addQuizBatch(quizExercise.id!);
            await quizExerciseOverview.startQuizBatch(quizExercise.id!, quizBatch.id!);
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await courseOverview.openRunningExercise(quizExercise.id!);
            await quizExerciseParticipation.joinQuizBatch(quizBatch.password!);
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
        });

        test('Instructor ends the quiz batch and student cannot participate anymore', async ({
            login,
            navigationBar,
            courseManagement,
            courseManagementExercises,
            courseOverview,
        }) => {
            await login(instructor, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.endQuiz(quizExercise);
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await expect(courseOverview.getOpenRunningExerciseButton(quizExercise.id!)).not.toBeVisible();
        });

        test('Instructor release ended exercise for practice and student practices', async ({
            login,
            navigationBar,
            courseManagement,
            courseManagementExercises,
            courseOverview,
            quizExerciseParticipation,
        }) => {
            await login(instructor, '/');
            await navigationBar.openCourseManagement();
            await courseManagement.openExercisesOfCourse(course.id!);
            await courseManagementExercises.endQuiz(quizExercise);
            await courseManagementExercises.getExercise(quizExercise.id!).locator('button', { hasText: 'Release For Practice' }).click();
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await courseOverview.practiceExercise();
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
        });
    });

    test.describe('Quiz exercise individual participation', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create quiz exercise', async ({ login, exerciseAPIRequests, courseManagementAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({
                body: { course },
                quizQuestions: [multipleChoiceQuizTemplate],
                releaseDate: dayjs().subtract(1, 'weeks'),
                quizMode: QuizMode.INDIVIDUAL,
            });
            await courseManagementAPIRequests.addInstructorToCourse(course, instructor);
        });

        test('Student can start a batch in an individual quiz', async ({ login, courseOverview, quizExerciseParticipation }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await courseOverview.openRunningExercise(quizExercise.id!);
            await quizExerciseParticipation.startQuizBatch();
            await expect(quizExerciseParticipation.getQuizQuestion(0)).toBeVisible();
        });
    });

    test.describe('SA quiz participation', () => {
        let quizExercise: QuizExercise;

        test.beforeEach('Create SA quiz', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            quizExercise = await exerciseAPIRequests.createQuizExercise({ body: { course }, quizQuestions: [shortAnswerQuizTemplate] });
            await exerciseAPIRequests.setQuizVisible(quizExercise.id!);
            await exerciseAPIRequests.startQuizNow(quizExercise.id!);
        });

        test('Student can participate in SA quiz', async ({ login, courseOverview, quizExerciseShortAnswerQuiz }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await courseOverview.startExercise(quizExercise.id!);
            const quizQuestionId = quizExercise.quizQuestions![0].id!;
            await quizExerciseShortAnswerQuiz.typeAnswer(0, 1, quizQuestionId, 'give');
            await quizExerciseShortAnswerQuiz.typeAnswer(1, 1, quizQuestionId, 'let');
            await quizExerciseShortAnswerQuiz.typeAnswer(2, 1, quizQuestionId, 'run');
            await quizExerciseShortAnswerQuiz.typeAnswer(2, 3, quizQuestionId, 'desert');
            await quizExerciseShortAnswerQuiz.typeAnswer(3, 1, quizQuestionId, 'cry');
            await quizExerciseShortAnswerQuiz.typeAnswer(4, 1, quizQuestionId, 'goodbye');
            await quizExerciseShortAnswerQuiz.submit();
        });
    });

    test.describe('DnD Quiz participation', () => {
        let quizExercise: QuizExercise;

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
            await login(studentOne, `/courses/${course.id}/exercises/${quizExercise.id}`);
            await courseOverview.startExercise(quizExercise.id!);
            await quizExerciseDragAndDropQuiz.dragItemIntoDragArea(0);
            await quizExerciseDragAndDropQuiz.submit();
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
