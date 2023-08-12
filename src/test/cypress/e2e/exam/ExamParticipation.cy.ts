import { Interception } from 'cypress/types/net-stubbing';
import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

import javaAllSuccessfulSubmission from '../../fixtures/exercise/programming/java/all_successful/submission.json';
import {
    courseManagementAPIRequest,
    examAPIRequests,
    examExerciseGroupCreation,
    examManagement,
    examNavigation,
    examParticipation,
    examStartEnd,
    textExerciseEditor,
} from '../../support/artemis';
import { Exercise, ExerciseType } from '../../support/constants';
import { admin, instructor, studentOne, studentThree, studentTwo, tutor, users } from '../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../support/utils';

// Common primitives
const textFixture = 'loremIpsum.txt';
const textFixtureShort = 'loremIpsum-short.txt';

describe('Exam participation', () => {
    let course: Course;
    let exerciseArray: Array<Exercise> = [];
    let studentOneName: string;
    let studentTwoName: string;
    let studentThreeName: string;

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse({ customizeGroups: true }).then((response) => {
            course = convertModelAfterMultiPart(response);
            courseManagementAPIRequest.addStudentToCourse(course, studentOne);
            courseManagementAPIRequest.addStudentToCourse(course, studentTwo);
            courseManagementAPIRequest.addStudentToCourse(course, studentThree);
            courseManagementAPIRequest.addTutorToCourse(course, tutor);
            courseManagementAPIRequest.addInstructorToCourse(course, instructor);
        });
        users.getUserInfo(studentOne.username, (userInfo) => {
            studentOneName = userInfo.name;
        });
        users.getUserInfo(studentTwo.username, (userInfo) => {
            studentTwoName = userInfo.name;
        });
        users.getUserInfo(studentThree.username, (userInfo) => {
            studentThreeName = userInfo.name;
        });
    });

    describe('Early Hand-in', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        before('Create exam', () => {
            cy.login(admin);
            const examConfig: Exam = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(3, 'minutes'),
                startDate: dayjs().subtract(2, 'minutes'),
                endDate: dayjs().add(1, 'hour'),
                examMaxPoints: 40,
                numberOfExercisesInExam: 4,
            };
            examAPIRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
                Promise.all([
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { submission: javaAllSuccessfulSubmission }),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 }),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING),
                ]).then((responses) => {
                    exerciseArray = exerciseArray.concat(responses);
                });

                examAPIRequests.registerStudentForExam(exam, studentOne);
                examAPIRequests.registerStudentForExam(exam, studentTwo);
                examAPIRequests.registerStudentForExam(exam, studentThree);
                examAPIRequests.generateMissingIndividualExams(exam);
                examAPIRequests.prepareExerciseStartForExam(exam);
            });
        });

        it('Participates as a student in a registered exam', () => {
            examParticipation.startParticipation(studentOne, course, exam);
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

            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
        });

        it('Using save and continue to navigate within exam', () => {
            examParticipation.startParticipation(studentTwo, course, exam);
            examNavigation.openExerciseAtIndex(0);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type == ExerciseType.PROGRAMMING) {
                    examNavigation.openExerciseAtIndex(j + 1);
                } else {
                    examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
                    examParticipation.clickSaveAndContinue();
                }
            }
            examParticipation.handInEarly();

            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentTwoName);
        });

        it('Using exercise overview to navigate within exam', () => {
            examParticipation.startParticipation(studentThree, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type == ExerciseType.PROGRAMMING) {
                    continue;
                } else {
                    examNavigation.openExerciseOverview();
                    examParticipation.selectExerciseOnOverview(j + 1);
                    examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
                }
            }
            examParticipation.handInEarly();

            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentThreeName);
        });
    });

    describe('Early hand-in with continue and reload page', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        before('Create exam', () => {
            exerciseArray = [];

            cy.login(admin);

            const examConfig: Exam = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(3, 'minutes'),
                startDate: dayjs().subtract(2, 'minutes'),
                endDate: dayjs().add(1, 'hour'),
                examMaxPoints: 10,
                numberOfExercisesInExam: 1,
            };
            examAPIRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
                examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }).then((response) => {
                    exerciseArray.push(response);
                });

                examAPIRequests.registerStudentForExam(exam, studentOne);
                examAPIRequests.registerStudentForExam(exam, studentTwo);
                examAPIRequests.registerStudentForExam(exam, studentThree);
                examAPIRequests.generateMissingIndividualExams(exam);
                examAPIRequests.prepareExerciseStartForExam(exam);
            });
        });

        it('Participates in the exam, hand-in early, but instead continues', () => {
            examParticipation.startParticipation(studentOne, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            examNavigation.openExerciseAtIndex(textExerciseIndex);
            examParticipation.makeTextExerciseSubmission(textExercise.id, textExercise.additionalData!.textFixture!);
            examParticipation.clickSaveAndContinue();
            examNavigation.handInEarly();

            examStartEnd.clickContinue();
            examNavigation.openExerciseAtIndex(textExerciseIndex);
            textExerciseEditor.clearSubmission(textExercise.id);
            examParticipation.makeTextExerciseSubmission(textExercise.id, textFixtureShort);
            examParticipation.clickSaveAndContinue();

            examParticipation.handInEarly();
            examParticipation.verifyTextExerciseOnFinalPage(textFixtureShort);
            examParticipation.checkExamTitle(examTitle);

            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
        });

        it('Reloads exam page during participation and ensures that everything is as expected', () => {
            examParticipation.startParticipation(studentTwo, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            examNavigation.openExerciseAtIndex(textExerciseIndex);
            examParticipation.makeTextExerciseSubmission(textExercise.id, textExercise.additionalData!.textFixture!);
            examParticipation.clickSaveAndContinue();

            cy.reload();
            examParticipation.startParticipation(studentTwo, course, exam);
            examNavigation.openExerciseAtIndex(textExerciseIndex);
            textExerciseEditor.checkCurrentContent(textExercise.id, textExercise.additionalData!.textFixture!);
            examParticipation.clickSaveAndContinue();
            examParticipation.handInEarly();

            examParticipation.verifyTextExerciseOnFinalPage(textExercise.additionalData!.textFixture!);
            examParticipation.checkExamTitle(examTitle);

            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentTwoName);
        });

        it('Reloads exam result page and ensures that everything is as expected', () => {
            examParticipation.startParticipation(studentThree, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            examNavigation.openExerciseAtIndex(textExerciseIndex);
            examParticipation.makeTextExerciseSubmission(textExercise.id, textExercise.additionalData!.textFixture!);
            examParticipation.clickSaveAndContinue();
            examParticipation.handInEarly();

            examParticipation.verifyTextExerciseOnFinalPage(textExercise.additionalData!.textFixture!);
            examParticipation.checkExamTitle(examTitle);

            cy.reload();

            examParticipation.verifyTextExerciseOnFinalPage(textExercise.additionalData!.textFixture!);
            examParticipation.checkExamTitle(examTitle);

            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentThreeName);
        });
    });

    describe('Normal Hand-in', () => {
        let exam: Exam;
        let studentOneName: string;
        const examTitle = 'exam' + generateUUID();

        before('Create exam', () => {
            exerciseArray = [];

            cy.login(admin);
            users.getUserInfo(studentOne.username, (userInfo) => {
                studentOneName = userInfo.name;
            });

            const examConfig: Exam = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(3, 'minutes'),
                startDate: dayjs().subtract(2, 'minutes'),
                endDate: dayjs().add(20, 'seconds'),
                examMaxPoints: 10,
                numberOfExercisesInExam: 1,
            };
            examAPIRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
                examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }).then((response) => {
                    exerciseArray.push(response);
                });

                examAPIRequests.registerStudentForExam(exam, studentOne);
                examAPIRequests.generateMissingIndividualExams(exam);
                examAPIRequests.prepareExerciseStartForExam(exam);
            });
        });

        it('Participates as a student in a registered exam', () => {
            examParticipation.startParticipation(studentOne, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            examNavigation.openExerciseAtIndex(textExerciseIndex);
            examParticipation.makeSubmission(textExercise.id, textExercise.type, textExercise.additionalData);
            examParticipation.clickSaveAndContinue();
            examParticipation.checkExamFullnameInputExists();
            examParticipation.checkYourFullname(studentOneName);
            examStartEnd.finishExam().then((request: Interception) => {
                expect(request.response!.statusCode).to.eq(200);
            });
            examParticipation.verifyTextExerciseOnFinalPage(textExercise.additionalData!.textFixture!);
            examParticipation.checkExamTitle(examTitle);

            cy.login(instructor);
            examManagement.verifySubmitted(course.id!, exam.id!, studentOneName);
        });
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
