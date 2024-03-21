import dayjs from 'dayjs';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import { Exercise, ExerciseType } from '../../../support/constants';
import { admin, studentFour, studentThree, studentTwo, users } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';

// Common primitives
const textFixture = 'loremIpsum-short.txt';

test.describe('Test exam participation', () => {
    let course: Course;
    let exerciseArray: Array<Exercise> = [];

    test.beforeEach('Create course', async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse({ customizeGroups: true });
        await courseManagementAPIRequests.addStudentToCourse(course, studentTwo);
        await courseManagementAPIRequests.addStudentToCourse(course, studentThree);
        await courseManagementAPIRequests.addStudentToCourse(course, studentFour);
    });

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
                examMaxPoints: 100,
                numberOfExercisesInExam: 10,
                numberOfCorrectionRoundsInExam: 0,
            };
            exam = await examAPIRequests.createExam(examConfig);
            Promise.all([
                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }),
                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }),
                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }),

                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { submission: javaAllSuccessfulSubmission, expectedScore: 100 }),
                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { submission: javaBuildErrorSubmission, expectedScore: 0 }),

                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 }),
                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 }),

                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING),
                await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING),
            ]).then((responses) => {
                exerciseArray = responses;
            });
        });

        test('Participates as a student in a registered test exam', async ({ examParticipation, examNavigation }) => {
            await examParticipation.startParticipation(studentTwo, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examNavigation.openExerciseAtIndex(j);

                if (exercise.type !== ExerciseType.PROGRAMMING) {
                    await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
                }
            }
            await examParticipation.handInEarly();
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                await examParticipation.verifyExerciseTitleOnFinalPage(exercise.id!, exercise.exerciseGroup!.title!);
                if (exercise.type === ExerciseType.TEXT) {
                    await examParticipation.verifyTextExerciseOnFinalPage(exercise.id!, exercise.additionalData!.textFixture!);
                }
            }
            await examParticipation.checkExamTitle(examTitle);
        });

        test('Using save and continue to navigate within exam', async ({ examParticipation, examNavigation }) => {
            await examParticipation.startParticipation(studentThree, course, exam);
            await examNavigation.openExerciseAtIndex(0);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type === ExerciseType.PROGRAMMING) {
                    await examNavigation.openExerciseAtIndex(j + 1);
                } else {
                    await examParticipation.checkExerciseTitle(exerciseArray[j].id!, exerciseArray[j].exerciseGroup!.title!);
                    await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
                    await examParticipation.clickSaveAndContinue();
                }
            }
            await examParticipation.handInEarly();
        });

        test('Using exercise overview to navigate within exam', async ({ examParticipation, examNavigation }) => {
            await examParticipation.startParticipation(studentFour, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type != ExerciseType.PROGRAMMING) {
                    await examNavigation.openExerciseOverview();
                    await examParticipation.selectExerciseOnOverview(j + 1);
                    await examParticipation.checkExerciseTitle(exerciseArray[j].id!, exerciseArray[j].exerciseGroup!.title!);
                    await examParticipation.makeSubmission(exercise.id!, exercise.type!, exercise.additionalData);
                }
            }
            await examParticipation.handInEarly();
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
            await examNavigation.openExerciseAtIndex(textExerciseIndex);
            await examParticipation.makeSubmission(textExercise.id!, textExercise.type!, textExercise.additionalData);
            await examParticipation.clickSaveAndContinue();
            await examParticipation.checkExamFullnameInputExists();
            await examParticipation.checkYourFullname(studentFourName);
            const response = await examStartEnd.finishExam();
            expect(response.status()).toBe(200);
            await examParticipation.verifyTextExerciseOnFinalPage(textExercise.id!, textExercise.additionalData!.textFixture!);
            await examParticipation.checkExamTitle(examTitle);
        });
    });

    test.afterEach('Delete course', async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });
});
