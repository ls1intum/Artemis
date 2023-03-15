import { Interception } from 'cypress/types/net-stubbing';
import dayjs from 'dayjs/esm';
import { Exercise } from 'src/test/cypress/support/pageobjects/exam/ExamParticipation';

import submission from '../../fixtures/exercise/programming/all_successful/submission.json';
import { courseManagementRequest, examExerciseGroupCreation, examNavigation, examParticipation, examStartEnd, textExerciseEditor } from '../../support/artemis';
import { EXERCISE_TYPE } from '../../support/constants';
import { CypressExamBuilder, convertCourseAfterMultiPart } from '../../support/requests/CourseManagementRequests';
import { admin, studentOne, studentThree, studentTwo } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

// Common primitives
const textFixture = 'loremIpsum.txt';
const textFixtureAlternative = 'loremIpsum-alternative.txt';
let exerciseArray: Array<Exercise> = [];

describe('Exam participation', () => {
    let course: Course;

    before('Create course', () => {
        cy.login(admin);
        courseManagementRequest.createCourse(true).then((response) => {
            course = convertCourseAfterMultiPart(response);
        });
    });

    describe('Early Hand-in', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        before('Create exam', () => {
            cy.login(admin);
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'minutes'))
                .startDate(dayjs().subtract(2, 'minutes'))
                .endDate(dayjs().add(1, 'hour'))
                .examMaxPoints(40)
                .numberOfExercises(4)
                .build();
            courseManagementRequest.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Text, { textFixture });

                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Programming, { submission });

                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Quiz, { quizExerciseID: 0 });

                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Modeling);

                courseManagementRequest.registerStudentForExam(exam, studentOne);
                courseManagementRequest.registerStudentForExam(exam, studentTwo);
                courseManagementRequest.registerStudentForExam(exam, studentThree);
                courseManagementRequest.generateMissingIndividualExams(exam);
                courseManagementRequest.prepareExerciseStartForExam(exam);
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
                examParticipation.verifyExerciseTitleOnFinalPage(exercise.id, exercise.title);
                if (exercise.type === EXERCISE_TYPE.Text) {
                    examParticipation.verifyTextExerciseOnFinalPage(exercise.additionalData!.textFixture!);
                }
            }
            examParticipation.checkExamTitle(examTitle);
        });

        it('Using save and continue to navigate within exam', () => {
            examParticipation.startParticipation(studentTwo, course, exam);
            examNavigation.openExerciseAtIndex(0);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type == EXERCISE_TYPE.Programming) {
                    examNavigation.openExerciseAtIndex(j + 1);
                } else {
                    examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
                    examParticipation.clickSaveAndContinue();
                }
            }
            examParticipation.handInEarly();
        });

        it('Using exercise overview to navigate within exam', () => {
            examParticipation.startParticipation(studentThree, course, exam);
            for (let j = 0; j < exerciseArray.length; j++) {
                const exercise = exerciseArray[j];
                // Skip programming exercise this time to save execution time
                // (we also need to use the navigation bar here, since programming  exercises do not have a "Save and continue" button)
                if (exercise.type == EXERCISE_TYPE.Programming) {
                    continue;
                } else {
                    examNavigation.openExerciseOverview();
                    examParticipation.selectExerciseOnOverview(j + 1);
                    examParticipation.makeSubmission(exercise.id, exercise.type, exercise.additionalData);
                }
            }
            examParticipation.handInEarly();
        });
    });

    describe('Early hand-in with continue and reload page', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        before('Create exam', () => {
            exerciseArray = [];

            cy.login(admin);
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'minutes'))
                .startDate(dayjs().subtract(2, 'minutes'))
                .endDate(dayjs().add(1, 'hour'))
                .examMaxPoints(10)
                .numberOfExercises(1)
                .build();
            courseManagementRequest.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Text, { textFixture });

                courseManagementRequest.registerStudentForExam(exam, studentOne);
                courseManagementRequest.registerStudentForExam(exam, studentTwo);
                courseManagementRequest.registerStudentForExam(exam, studentThree);
                courseManagementRequest.generateMissingIndividualExams(exam);
                courseManagementRequest.prepareExerciseStartForExam(exam);
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
            examParticipation.makeTextExerciseSubmission(textExercise.id, textFixtureAlternative);
            examParticipation.clickSaveAndContinue();

            examParticipation.handInEarly();
            examParticipation.verifyTextExerciseOnFinalPage(textFixtureAlternative);
            examParticipation.checkExamTitle(examTitle);
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
        });
    });

    describe('Normal Hand-in', () => {
        let exam: Exam;
        const examTitle = 'exam' + generateUUID();

        before('Create exam', () => {
            exerciseArray = [];

            cy.login(admin);
            const examContent = new CypressExamBuilder(course)
                .title(examTitle)
                .visibleDate(dayjs().subtract(3, 'minutes'))
                .startDate(dayjs().subtract(2, 'minutes'))
                .endDate(dayjs().add(20, 'seconds'))
                .examMaxPoints(10)
                .numberOfExercises(1)
                .build();
            courseManagementRequest.createExam(examContent).then((examResponse) => {
                exam = examResponse.body;
                examExerciseGroupCreation.addGroupWithExercise(exerciseArray, exam, EXERCISE_TYPE.Text, { textFixture });

                courseManagementRequest.registerStudentForExam(exam, studentOne);
                courseManagementRequest.generateMissingIndividualExams(exam);
                courseManagementRequest.prepareExerciseStartForExam(exam);
            });
        });

        it('Participates as a student in a registered exam', () => {
            examParticipation.startParticipation(studentOne, course, exam);
            const textExerciseIndex = 0;
            const textExercise = exerciseArray[textExerciseIndex];
            examNavigation.openExerciseAtIndex(textExerciseIndex);
            examParticipation.makeSubmission(textExercise.id, textExercise.type, textExercise.additionalData);
            examParticipation.clickSaveAndContinue();
            cy.get('#fullname', { timeout: 20000 }).should('be.visible');
            examStartEnd.finishExam().then((request: Interception) => {
                expect(request.response!.statusCode).to.eq(200);
            });
            examParticipation.verifyTextExerciseOnFinalPage(textExercise.additionalData!.textFixture!);
            examParticipation.checkExamTitle(examTitle);
        });
    });

    after('Delete course', () => {
        if (course) {
            cy.login(admin);
            courseManagementRequest.deleteCourse(course.id!);
        }
    });
});
