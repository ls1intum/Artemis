import dayjs from 'dayjs';

import { Exam } from 'app/exam/shared/entities/exam.model';

import { Exercise, ExerciseType } from '../../../support/constants';
import { admin, studentFour, studentThree, studentTwo, users } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
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

        test('Participates as a student in a registered test exam', async ({ examParticipation, examNavigation }) => {
            await examParticipation.startParticipation(studentTwo, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
                await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
            }
            await examParticipation.handInEarly();
        });

        test('Using exercise sidebar to navigate within exam', async ({ examParticipation, examNavigation }) => {
            await examParticipation.startParticipation(studentThree, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
                await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
                await examNavigation.openOrSaveExerciseByTitle(exercise.exerciseGroup!.title!);
            }
            await examParticipation.handInEarly();
        });

        test('Using exercise overview to navigate within exam', async ({ examParticipation, examNavigation }) => {
            await examParticipation.startParticipation(studentFour, course, exam);

            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openFromOverviewByTitle(exercise.exerciseGroup!.title!);
                await examNavigation.openOverview();
            }
            await examParticipation.handInEarly();
        });

        test.afterEach('Delete exam', async ({ examAPIRequests }) => {
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
                workingTime: 15,
                examMaxPoints: 10,
                numberOfCorrectionRoundsInExam: 1,
            };
            exam = await examAPIRequests.createExam(examConfig);
            const exercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture });
            exerciseArray = [exercise];
        });

        test('Participates as a student in a registered exam', async ({ examParticipation, examNavigation, examStartEnd }) => {
            await examParticipation.startParticipation(studentFour, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.makeSubmission(textExercise.id!, textExercise.type!, textExercise.additionalData);
            await examNavigation.openOrSaveExerciseByTitle(textExercise.exerciseGroup!.title!);
            await examParticipation.checkExamFullnameInputExists();
            await examParticipation.checkYourFullname(studentFourName);
            const response = await examStartEnd.finishExam();
            expect(response.status()).toBe(200);
            await examStartEnd.pressShowSummary();
            await examParticipation.verifyTextExerciseOnFinalPage(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.checkExamTitle(examTitle);
        });

        test.afterEach('Delete exam', async ({ examAPIRequests }) => {
            await examAPIRequests.deleteExam(exam);
        });
    });
});
