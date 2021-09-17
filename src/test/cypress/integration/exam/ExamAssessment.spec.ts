import { generateUUID } from '../../support/utils';
import { artemis } from '../../support/ArtemisTesting';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import { BASE_API, GROUP_SYNCHRONIZATION, PUT } from '../../support/constants';
import dayjs from 'dayjs';
import { ProgrammingExerciseSubmission } from '../../support/pageobjects/OnlineEditorPage';
import partiallySuccessful from '../../fixtures/programming_exercise_submissions/partially_successful/submission.json';
import textSubmission from '../../fixtures/text_exercise_submission/text_exercise_submission.json';

// requests
const courseManagementRequests = artemis.requests.courseManagement;

// page objects
const examStartEnd = artemis.pageobjects.examStartEnd;
const modelingEditor = artemis.pageobjects.modelingEditor;
const modelingAssessment = artemis.pageobjects.modelingExerciseAssessmentEditor;
const editorPage = artemis.pageobjects.programmingExercise.editor;
const assessmentDashboard = artemis.pageobjects.assessmentDashboard;

// Common primitives
const admin = artemis.users.getAdmin();
const student = artemis.users.getStudentOne();
const tutor = artemis.users.getTutor();
let exam: any;
let exerciseGroup: any;
let course: any;
let examTitle: string;

// This is a workaround for uncaught athene errors. When opening a text submission athene throws an uncaught exception, which fails the test
Cypress.on('uncaught:exception', () => {
    return false;
});

describe('Exam assessment', () => {
    before('Create a course', () => {
        cy.login(admin);
        courseManagementRequests.createCourse().then((response) => {
            course = response.body;
            courseManagementRequests.addStudentToCourse(course.id, artemis.users.getStudentOne().username);
            courseManagementRequests.addTutorToCourse(course, artemis.users.getTutor());
        });
    });

    beforeEach('Generate new exam name', () => {
        examTitle = 'exam' + generateUUID();
        cy.login(admin);
    });

    afterEach('Delete exam', () => {
        cy.login(admin);
        courseManagementRequests.deleteExam(exam);
    });

    after('Delete course', () => {
        cy.login(admin);
        courseManagementRequests.deleteCourse(course.id);
    });

    describe('Exam exercise assessment', () => {
        beforeEach('Create exam', () => {
            prepareExam(dayjs().add(30, 'seconds'));
        });

        it('Assess a modeling exercise submission', () => {
            courseManagementRequests.createModelingExercise({ exerciseGroup }).then((modelingResponse) => {
                courseManagementRequests.generateMissingIndividualExams(exam);
                courseManagementRequests.prepareExerciseStartForExam(exam);
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                cy.contains(modelingResponse.body.title).should('be.visible').click();
                modelingEditor.addComponentToModel(1);
                modelingEditor.addComponentToModel(2);
                modelingEditor.addComponentToModel(3);
                cy.intercept(PUT, BASE_API + 'exercises/*/modeling-submissions').as('createModelingSubmission');
                cy.contains('Save').click();
                cy.wait('@createModelingSubmission');
                cy.get('#exam-navigation-bar').find('.btn-danger').click();
                examStartEnd.finishExam();
                cy.login(tutor, '/course-management/' + course.id + '/exams');
                cy.contains('Assessment Dashboard', { timeout: 60000 }).click();
                assessmentDashboard.startAssessing();
                modelingAssessment.addNewFeedback(2, 'Noice');
                modelingAssessment.openAssessmentForComponent(1);
                modelingAssessment.assessComponent(1, 'Good');
                modelingAssessment.openAssessmentForComponent(2);
                modelingAssessment.assessComponent(0, 'Neutral');
                modelingAssessment.openAssessmentForComponent(3);
                modelingAssessment.assessComponent(-1, 'Wrong');
                assessmentDashboard.submitModelingAssessment().then((assessmentResponse) => {
                    expect(assessmentResponse.response?.statusCode).to.equal(200);
                });
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                cy.contains('2 of 10 points').should('be.visible');
            });
        });

        it('Assess a text exercise submission', () => {
            const exerciseTitle = 'Cypress Text Exercise';
            courseManagementRequests.createTextExercise({ exerciseGroup }, exerciseTitle);
            courseManagementRequests.generateMissingIndividualExams(exam);
            courseManagementRequests.prepareExerciseStartForExam(exam);
            cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
            examStartEnd.startExam();
            cy.contains(exerciseTitle).click();
            cy.get('#text-editor-tab').type(textSubmission.text);
            cy.contains('Save').click();
            cy.get('#exam-navigation-bar').find('.btn-danger').click();
            examStartEnd.finishExam();
            cy.login(tutor, '/course-management/' + course.id + '/exams');
            cy.contains('Assessment Dashboard', { timeout: 60000 }).click();
            assessmentDashboard.startAssessing();
            assessmentDashboard.addNewFeedback(7, 'Good job');
            assessmentDashboard.submitTextAssessment().then((assessmentResponse) => {
                expect(assessmentResponse.response?.statusCode).to.equal(200);
            });
            cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
            cy.get('.question-options').contains('7 of 10 points').should('be.visible');
        });
    });

    describe('Exam programming exercise assessment', () => {
        const examEnd = (Cypress.env('isBamboo') ? GROUP_SYNCHRONIZATION : 0) + 115000;

        beforeEach('Create exam', () => {
            prepareExam(dayjs().add(examEnd, 'milliseconds'));
        });

        it('Assess a programming exercise submission (MANUAL)', () => {
            cy.login(admin);
            courseManagementRequests.createProgrammingExercise({ exerciseGroup }).then((progRespone) => {
                const programmingExercise = progRespone.body;
                courseManagementRequests.generateMissingIndividualExams(exam);
                courseManagementRequests.prepareExerciseStartForExam(exam);
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                cy.contains(programmingExercise.title).should('be.visible').click();
                makeSubmissionAndVerifyResults(partiallySuccessful, programmingExercise.packageName, () => {
                    cy.get('#exam-navigation-bar').find('.btn-danger').click();
                    examStartEnd.finishExam();
                    cy.get('.alert').should('be.visible');
                    cy.login(tutor, '/course-management/' + course.id + '/exams');
                    cy.contains('Assessment Dashboard', { timeout: examEnd }).click();
                    assessmentDashboard.startAssessing();
                    assessmentDashboard.addNewFeedback(2, 'Good job');
                    assessmentDashboard.submitAssessment();
                    cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                    cy.get('.question-options').contains('6.6 of 10 points').should('be.visible');
                });
            });
        });
    });
});

function makeSubmissionAndVerifyResults(submission: ProgrammingExerciseSubmission, packageName: string, verifyOutput: () => void) {
    // We create an empty file so that the file browser does not create an extra subfolder when all files are deleted
    editorPage.createFileInRootPackage('placeholderFile');
    // We delete all existing files, so we can create new files and don't have to delete their already existing content
    editorPage.deleteFile('Client.java');
    editorPage.deleteFile('BubbleSort.java');
    editorPage.deleteFile('MergeSort.java');
    editorPage.typeSubmission(submission, packageName);
    editorPage.submit();
    verifyOutput();
}

function prepareExam(examEnd: dayjs.Dayjs) {
    cy.log(examEnd.toString());
    cy.login(admin);
    const examContent = new CypressExamBuilder(course)
        .title(examTitle)
        .visibleDate(dayjs().subtract(1, 'day'))
        .startDate(dayjs())
        .endDate(examEnd)
        .publishResultsDate(examEnd.add(1, 'seconds'))
        .gracePeriod(0)
        .build();
    courseManagementRequests.createExam(examContent).then((examResponse) => {
        exam = examResponse.body;
        courseManagementRequests.registerStudentForExam(exam, student);
        courseManagementRequests.addExerciseGroupForExam(exam).then((groupResponse) => {
            exerciseGroup = groupResponse.body;
        });
    });
}
