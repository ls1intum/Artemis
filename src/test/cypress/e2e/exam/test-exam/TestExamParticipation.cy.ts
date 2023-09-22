import { Interception } from 'cypress/types/net-stubbing';
import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import javaBuildErrorSubmission from '../../../fixtures/exercise/programming/java/build_error/submission.json';
import { courseManagementAPIRequest, examAPIRequests, examExerciseGroupCreation, examNavigation, examParticipation, examStartEnd } from '../../../support/artemis';
import { Exercise, ExerciseType } from '../../../support/constants';
import { admin, studentFour, studentThree, studentTwo, users } from '../../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../../support/utils';

// Common primitives
const textFixture = 'loremIpsum-short.txt';

describe('Test exam participation', () => {
    let course: Course;
    let exerciseArray: Array<Exercise> = [];

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse({ customizeGroups: true }).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentTwo);
            courseManagementAPIRequest.addStudentToCourse(course, studentThree);
            courseManagementAPIRequest.addStudentToCourse(course, studentFour);
        });
    });

    describe('Early Hand-in', () => {
        let exam: Exam;
        const examTitle = 'test-exam' + generateUUID();

        before('Create test exam', () => {
            cy.login(admin);
            const examConfig: Exam = {
                course,
                title: examTitle,
                testExam: true,
                startDate: dayjs().subtract(1, 'day'),
                visibleDate: dayjs().subtract(2, 'days'),
                examMaxPoints: 100,
                numberOfExercisesInExam: 10,
                numberOfCorrectionRoundsInExam: 0,
            };
            examAPIRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
                Promise.all([
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }),

                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { submission: javaAllSuccessfulSubmission, expectedScore: 100 }),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { submission: javaBuildErrorSubmission, expectedScore: 0 }),

                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 }),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 }),

                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING),
                ]).then((responses) => {
                    exerciseArray = exerciseArray.concat(responses);
                });
            });
        });

        it('Participates as a student in a registered test exam', () => {
            examParticipation.startParticipation(studentTwo, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                examNavigation.openExerciseAtIndex(j);
                examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
            }
            examParticipation.handInEarly();
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                examParticipation.verifyExerciseTitleOnFinalPage(exercise.id, exercise.exerciseGroup!.title!);
                if (exercise.type === ExerciseType.TEXT) {
                    examParticipation.verifyTextExerciseOnFinalPage(exercise.additionalData!.textFixture!);
                }
            }
            examParticipation.checkExamTitle(examTitle);
        });

        it('Using save and continue to navigate within exam', () => {
            examParticipation.startParticipation(studentThree, course, exam);
            examNavigation.openExerciseAtIndex(0);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type == ExerciseType.PROGRAMMING) {
                    examNavigation.openExerciseAtIndex(j + 1);
                } else {
                    examParticipation.checkExerciseTitle(exerciseArray[j].id, exerciseArray[j].exerciseGroup!.title!);
                    examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
                    examParticipation.clickSaveAndContinue();
                }
            }
            examParticipation.handInEarly();
        });

        it('Using exercise overview to navigate within exam', () => {
            examParticipation.startParticipation(studentFour, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type === ExerciseType.PROGRAMMING) {
                    continue;
                } else {
                    examNavigation.openExerciseOverview();
                    examParticipation.selectExerciseOnOverview(j + 1);
                    examParticipation.checkExerciseTitle(exerciseArray[j].id, exerciseArray[j].exerciseGroup!.title!);
                    examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
                }
            }
            examParticipation.handInEarly();
        });
    });

    describe('Normal Hand-in', () => {
        let exam: Exam;
        let studentFourName: string;
        const examTitle = 'exam' + generateUUID();

        before('Create exam', () => {
            exerciseArray = [];

            cy.login(admin);

            users.getUserInfo(studentFour.username, (userInfo) => {
                studentFourName = userInfo.name;
            });

            const examConfig: Exam = {
                course,
                title: examTitle,
                testExam: true,
                startDate: dayjs().subtract(1, 'day'),
                visibleDate: dayjs().subtract(2, 'days'),
                workingTime: 15,
                examMaxPoints: 10,
                numberOfCorrectionRoundsInExam: 1,
            };
            examAPIRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
                examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }).then((response) => {
                    exerciseArray.push(response);
                });
            });
        });

        it('Participates as a student in a registered exam', () => {
            examParticipation.startParticipation(studentFour, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            examNavigation.openExerciseAtIndex(textExerciseIndex);
            examParticipation.makeSubmission(textExercise.id, textExercise.type, textExercise.additionalData);
            examParticipation.clickSaveAndContinue();
            examParticipation.checkExamFullnameInputExists();
            examParticipation.checkYourFullname(studentFourName);
            examStartEnd.finishExam().then((request: Interception) => {
                expect(request.response!.statusCode).to.eq(200);
            });
            examParticipation.verifyTextExerciseOnFinalPage(textExercise.additionalData!.textFixture!);
            examParticipation.checkExamTitle(examTitle);
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
