import { Interception } from 'cypress/types/net-stubbing';
import dayjs from 'dayjs/esm';

import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';

import javaBuildErrorSubmission from '../../fixtures/exercise/programming/java/build_error/submission.json';
import { courseManagementAPIRequest, examAPIRequests, examExerciseGroupCreation, examManagement, examNavigation, examParticipation, examTestRun } from '../../support/artemis';
import { Exercise, ExerciseType } from '../../support/constants';
import { admin, instructor } from '../../support/users';
import { convertModelAfterMultiPart, generateUUID } from '../../support/utils';

// Common primitives
const textFixture = 'loremIpsum-short.txt';
const examTitle = 'exam' + generateUUID();

describe('Exam test run', () => {
    let course: Course;
    let exam: Exam;
    let testRun: any;
    let exerciseArray: Array<Exercise> = [];

    before('Create course', () => {
        cy.login(admin);
        courseManagementAPIRequest.createCourse({ customizeGroups: true }).then((response) => {
            course = convertModelAfterMultiPart(response);
            const examConfig: Exam = {
                course,
                title: examTitle,
                visibleDate: dayjs().subtract(3, 'days'),
                startDate: dayjs().add(1, 'days'),
                endDate: dayjs().add(3, 'days'),
                examMaxPoints: 40,
                numberOfExercisesInExam: 4,
            };
            examAPIRequests.createExam(examConfig).then((examResponse) => {
                exam = examResponse.body;
                Promise.all([
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.TEXT, { textFixture }),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.PROGRAMMING, { submission: javaBuildErrorSubmission, practiceMode: true }),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 }),
                    examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.MODELING),
                ]).then((responses) => {
                    exerciseArray = exerciseArray.concat(responses);
                });
            });
        });
    });

    beforeEach('Create test run instance', () => {
        cy.login(instructor);
        // TODO: API call does not work yet, for now we use the UI to create the test run
        // courseManagementAPIRequests.createExamTestRun(exam, exerciseArray).then((response: any) => {
        //     testRun = response.body;
        // });
        cy.visit(`/course-management/${course.id}/exams/${exam.id}`);
        examManagement.openTestRun();
        examTestRun.createTestRun();
        examTestRun.setWorkingTimeMinutes(2);
        examTestRun.confirmTestRun().then((testRunResponse: Interception) => {
            testRun = testRunResponse.response!.body;
        });
    });

    it('Create a test run', () => {
        cy.login(instructor);

        let testRunID: number;
        const minutes = 40;
        const seconds = 30;

        cy.visit(`/course-management/${course.id}/exams/${exam.id}`);
        examManagement.openTestRun();
        examTestRun.createTestRun();
        examTestRun.setWorkingTimeMinutes(minutes);
        examTestRun.setWorkingTimeSeconds(seconds);
        examTestRun.confirmTestRun().then((testRunResponse: Interception) => {
            const testRun = testRunResponse.response!.body;
            testRunID = testRun.id;

            expect(testRunResponse.response!.statusCode).to.eq(200);
            expect(testRun.testRun).to.be.true;
            expect(testRun.submitted).to.be.false;
            expect(testRun.workingTime).to.eq(minutes * 60 + seconds);

            examTestRun.getWorkingTime(testRunID).contains('40min 30s');
            examTestRun.getStarted(testRunID).contains('No');
            examTestRun.getSubmitted(testRunID).contains('No');
        });
    });

    it('Change test run working time', () => {
        const hour = 1;
        const minutes = 20;
        const seconds = 45;

        cy.login(instructor);
        examTestRun.openTestRunPage(course, exam);
        examTestRun.changeWorkingTime(testRun.id);
        examTestRun.setWorkingTimeHours(hour);
        examTestRun.setWorkingTimeMinutes(minutes);
        examTestRun.setWorkingTimeSeconds(seconds);
        examTestRun.saveTestRun().then((testRunResponse: Interception) => {
            const testRun = testRunResponse.response!.body;

            expect(testRun.id).to.eq(testRun.id);
            expect(testRunResponse.response!.statusCode).to.eq(200);
            expect(testRun.workingTime).to.eq(hour * 3600 + minutes * 60 + seconds);

            examTestRun.openTestRunPage(course, exam);
            examTestRun.getWorkingTime(testRun.id).contains(`${hour}h ${minutes}min ${seconds}s`);
            examTestRun.getStarted(testRun.id).contains('No');
            examTestRun.getSubmitted(testRun.id).contains('No');
        });
    });

    it('Conducts a test run', () => {
        examTestRun.startParticipation(instructor, course, exam, testRun.id);
        examTestRun.getTestRunRibbon().contains('Test Run');

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
        examTestRun.openTestRunPage(course, exam);
        examTestRun.getStarted(testRun.id).contains('Yes');
        examTestRun.getSubmitted(testRun.id).contains('Yes');
    });

    it('Deletes a test run', () => {
        cy.login(instructor);
        examTestRun.openTestRunPage(course, exam);
        examTestRun.getTestRunIdElement(testRun.id).should('exist');
        examTestRun.deleteTestRun(testRun.id);
        examTestRun.getTestRun(testRun.id).should('not.exist');
    });

    after('Delete course', () => {
        courseManagementAPIRequest.deleteCourse(course, admin);
    });
});
