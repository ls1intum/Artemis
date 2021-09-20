import { artemis } from '../../support/ArtemisTesting';
import { CypressExamBuilder } from '../../support/requests/CourseManagementRequests';
import modelingExerciseTemplate from '../../fixtures/requests/modelingExercise_template.json';
import { GROUP_SYNCHRONIZATION } from '../../support/constants';
import dayjs from 'dayjs';
import { ProgrammingExerciseSubmission } from '../../support/pageobjects/OnlineEditorPage';
import partiallySuccessful from '../../fixtures/programming_exercise_submissions/partially_successful/submission.json';
import textSubmission from '../../fixtures/text_exercise_submission/text_exercise_submission.json';
import dayjs from 'dayjs';
import textSubmission from '../../fixtures/text_exercise_submission/text_exercise_submission.json';
import { makeSubmissionAndVerifyResults } from '../../support/pageobjects/OnlineEditorPage';

// requests
const courseManagementRequests = artemis.requests.courseManagement;

// page objects
const examStartEnd = artemis.pageobjects.examStartEnd;
const modelingEditor = artemis.pageobjects.modelingExercise.editor;
const modelingAssessment = artemis.pageobjects.modelingExercise.assessmentEditor;
const editorPage = artemis.pageobjects.programmingExercise.editor;
const assessmentDashboard = artemis.pageobjects.assessmentDashboard;
const examNavigation = artemis.pageobjects.examNavigationBar;
const textEditor = artemis.pageobjects.textExercise.editor;

// Common primitives
const admin = artemis.users.getAdmin();
const student = artemis.users.getStudentOne();
const tutor = artemis.users.getTutor();
let exam: any;
let exerciseGroup: any;
let course: any;

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
        beforeEach('Generate new exam name', () => {
            prepareExam(dayjs().add(30, 'seconds'));
        });

        describe('Modeling exercise assessment', () => {
            beforeEach('Create exercise and submission', () => {
                courseManagementRequests.createModelingExercise({ exerciseGroup }).then((modelingResponse) => {
                    courseManagementRequests.generateMissingIndividualExams(exam);
                    courseManagementRequests.prepareExerciseStartForExam(exam);
                    cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                    examStartEnd.startExam();
                    cy.contains(modelingResponse.body.title).should('be.visible').click();
                    modelingEditor.addComponentToModel(1);
                    modelingEditor.addComponentToModel(2);
                    modelingEditor.addComponentToModel(3);
                    modelingEditor.save().then((modelResponse) => {
                        expect(modelResponse.response?.statusCode).to.equal(200);
                    });
                    examNavigation.handInEarly();
                    examStartEnd.finishExam();
                });
            });

            it('Assess a modeling exercise submission', () => {
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

        describe('Text exercise assessment', () => {
            beforeEach('Create exercise and submission', () => {
                const exerciseTitle = 'Cypress Text Exercise';
                courseManagementRequests.createTextExercise({ exerciseGroup }, exerciseTitle);
                courseManagementRequests.generateMissingIndividualExams(exam);
                courseManagementRequests.prepareExerciseStartForExam(exam);
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                cy.contains(exerciseTitle).click();
                textEditor.typeSubmission(textSubmission.text);
                textEditor.submit().then((submissionResponse) => {
                    expect(submissionResponse.response?.statusCode).to.equal(200);
                });
                examNavigation.handInEarly();
                examStartEnd.finishExam();
            });

            it('Assess a text exercise submission', () => {
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
    
    it.only('Assess a quiz exercise submission', () => {
            courseManagementRequests.createQuizExercise({exerciseGroup}, 'Cypress Quiz', dayjs(), 30).then((quizResponse) => {
                courseManagementRequests.generateMissingIndividualExams(course, exam);
                courseManagementRequests.prepareExerciseStartForExam(course, exam);
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                // courseManagementRequests.createMultipleChoiceSubmission(quizResponse.body, [0, 2]);
                cy.contains('Cypress Quiz').click();
                cy.get('#answer-option-0 > .selection > .ng-fa-icon > .svg-inline--fa').click();
                cy.get('#answer-option-2 > .selection > .ng-fa-icon > .svg-inline--fa').click();
                cy.get('#exam-navigation-bar').find('.btn-danger').click();
                examStartEnd.finishExam();
                cy.login(tutor, '/course-management/' + course.id + '/exams');
                cy.contains('Assessment Dashboard', { timeout: 60000 }).click();
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                cy.get('.question-options').contains('points').should('be.visible');
            });
        });
    });

    describe('Exam programming exercise assessment', () => {
        const examEnd = (Cypress.env('isBamboo') ? GROUP_SYNCHRONIZATION : 0) + 115000;

        before('Prepare exam', () => {
            prepareExam(dayjs().add(examEnd, 'milliseconds'));
        });

        beforeEach('Create exam, exercise and submission', () => {
            courseManagementRequests.createProgrammingExercise({ exerciseGroup }).then((progRespone) => {
                const programmingExercise = progRespone.body;
                courseManagementRequests.generateMissingIndividualExams(exam);
                courseManagementRequests.prepareExerciseStartForExam(exam);
                cy.login(student, '/courses/' + course.id + '/exams/' + exam.id);
                examStartEnd.startExam();
                cy.contains(programmingExercise.title).should('be.visible').click();
                makeSubmissionAndVerifyResults(editorPage, programmingExercise.packageName, partiallySuccessful, () => {
                    examNavigation.handInEarly();
                    examStartEnd.finishExam();
                });
            });
        });

        it('Assess a programming exercise submission (MANUAL)', () => {
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

function prepareExam(examEnd: dayjs.Dayjs) {
    cy.login(admin);
    const examContent = new CypressExamBuilder(course)
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
