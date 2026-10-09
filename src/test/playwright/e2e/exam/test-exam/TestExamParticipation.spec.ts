import dayjs from 'dayjs';

import { Exam } from 'app/exam/shared/entities/exam.model';

import { Exercise, ExerciseType } from '../../../support/constants';
import { admin, studentFour, studentThree, studentTwo, users } from '../../../support/users';
import { asAdmin, generateUUID } from '../../../support/utils';
import { expectStoredAnswers } from '../../../support/examAnswerAssertions';
import { ModalDialogBox } from '../../../support/pageobjects/exam/ModalDialogBox';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../../support/seedData';

// Common primitives
const textFixture = 'loremIpsum-short.txt';

const course = { id: SEED_COURSES.testExam.id } as any;

test.describe('Test exam participation', { tag: '@slow' }, () => {
    let exerciseArray: Array<Exercise> = [];

    test.describe('Early Hand-in', () => {
        let exam: Exam;
        const examTitle = 'test-exam' + generateUUID();

        test.beforeEach('Create test exam', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
            await login(admin);
            const examConfig = {
                course,
                title: examTitle,
                testExam: true,
                startDate: dayjs().subtract(1, 'day'),
                visibleDate: dayjs().subtract(2, 'days'),
                examMaxPoints: 20,
                numberOfExercisesInExam: 2,
                numberOfCorrectionRoundsInExam: 0,
            };
            exam = await examAPIRequests.createExam(examConfig);
            const textExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
            const quizExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 });
            exerciseArray = [textExercise, quizExercise];
        });

        test('Participates as a student in a registered test exam', async ({ examAPIRequests, examParticipation, examNavigation, examStartEnd }) => {
            await examParticipation.startParticipation(studentTwo, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
                await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
            }
            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();

            // A test exam shows the result of the attempt right away, and the server stored every answer.
            await examParticipation.checkExamTitle(exam.title!);
            const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
            expect(summary.submitted).toBe(true);
            expect(summary.testRun).toBe(false);
            expect(summary.exercises).toHaveLength(exerciseArray.length);
            await expectStoredAnswers(summary, exerciseArray, { [ExerciseType.TEXT]: true, [ExerciseType.QUIZ]: true });
        });

        test('Using exercise sidebar to navigate within exam', async ({ examAPIRequests, examParticipation, examNavigation, examStartEnd }) => {
            await examParticipation.startParticipation(studentThree, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
                await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
                await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            }
            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();

            const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
            expect(summary.submitted).toBe(true);
            expect(summary.exercises).toHaveLength(exerciseArray.length);
            await expectStoredAnswers(summary, exerciseArray, { [ExerciseType.TEXT]: true, [ExerciseType.QUIZ]: true });
        });

        test('Using exercise overview to navigate within exam', async ({ examAPIRequests, examParticipation, examNavigation, examStartEnd }) => {
            await examParticipation.startParticipation(studentFour, course, exam);

            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openFromOverviewByTitle(exercise.exerciseGroup!.title!);
                await examNavigation.openOverview();
            }
            await examParticipation.handInEarly();
            await examStartEnd.pressShowSummary();

            // Only browsing through the exercises must neither lose nor invent answers.
            const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
            expect(summary.submitted).toBe(true);
            expect(summary.exercises).toHaveLength(exerciseArray.length);
            await expectStoredAnswers(summary, exerciseArray, { [ExerciseType.TEXT]: false, [ExerciseType.QUIZ]: false });
        });

        test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
            // The test ends as a student, who is not allowed to delete the exam; without the login the exam leaked.
            await login(admin);
            await examAPIRequests.deleteExam(exam);
        });
    });

    test.describe('Normal Hand-in', () => {
        let exam: Exam;
        let studentFourName: string;
        const examTitle = 'exam' + generateUUID();

        test.beforeEach('Create exam', async ({ login, page, examAPIRequests, examExerciseGroupCreation }) => {
            exerciseArray = [];

            await login(admin);

            const studentFourInfo = await users.getUserInfo(studentFour.username, page);
            studentFourName = studentFourInfo.name!;

            const examConfig = {
                course,
                title: examTitle,
                testExam: true,
                startDate: dayjs().subtract(1, 'day'),
                visibleDate: dayjs().subtract(2, 'days'),
                examMaxPoints: 10,
                numberOfCorrectionRoundsInExam: 1,
            };
            exam = await examAPIRequests.createExam(examConfig);
            const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
            exerciseArray = [exercise];
        });

        test('Participates as a student in a registered exam', async ({ browser, page, examAPIRequests, examParticipation, examNavigation, examStartEnd }) => {
            await examParticipation.startParticipation(studentFour, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.makeSubmission(textExercise.id!, textExercise.type!, textExercise.additionalData);
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);

            // An attempt runs for its working time from the moment the student started it. Its end is set now that the student is
            // working, so a slow setup cannot cut the attempt short.
            await asAdmin(browser, async (adminExamRequests) => {
                const studentExams = await adminExamRequests.getAllStudentExams(exam);
                expect(studentExams, 'the student started exactly one attempt').toHaveLength(1);
                await adminExamRequests.endStudentExamIn(exam, studentExams[0].id, 20);
            });
            await expect(new ModalDialogBox(page).getModalDialogContent()).toBeVisible({ timeout: 30_000 });
            await new ModalDialogBox(page).closeDialog();
            await examParticipation.checkExamFullnameInputExists();
            await examParticipation.checkYourFullname(studentFourName);
            const response = await examStartEnd.finishExam();
            expect(response.status()).toBe(200);
            await examStartEnd.pressShowSummary();
            await examParticipation.verifyTextExerciseOnFinalPage(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.checkExamTitle(examTitle);

            const summary = await examAPIRequests.getOwnStudentExamSummary(exam);
            expect(summary.submitted).toBe(true);
            await expectStoredAnswers(summary, exerciseArray, { [ExerciseType.TEXT]: true });
        });

        test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
            // The test ends as a student, who is not allowed to delete the exam; without the login the exam leaked.
            await login(admin);
            await examAPIRequests.deleteExam(exam);
        });
    });
});
