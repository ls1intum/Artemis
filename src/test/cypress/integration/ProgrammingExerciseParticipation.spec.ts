/// <reference types="cypress" />

import { generateUUID } from '../support/utils';
import { OnlineEditorPage, ProgrammingExerciseSubmission } from '../support/pageobjects/OnlineEditorPage';
import allSuccessful from '../fixtures/programming_exercise_submissions/all_successful/submission.json';
import partiallySuccessful from '../fixtures/programming_exercise_submissions/partially_successful/submission.json';
import { beVisible } from '../support/constants';
import { ArtemisRequests } from './../support/requests/ArtemisRequests';

// Environmental variables
const adminUsername = Cypress.env('adminUsername');
const adminPassword = Cypress.env('adminPassword');
const usernameTemplate = Cypress.env('username');
const passwordTemplate = Cypress.env('password');
const student1 = usernameTemplate.replace('USERID', '3');
const passwordStudent1 = passwordTemplate.replace('USERID', '3');
const student2 = usernameTemplate.replace('USERID', '4');
const passwordStudent2 = passwordTemplate.replace('USERID', '4');
const student3 = usernameTemplate.replace('USERID', '5');
const passwordStudent3 = passwordTemplate.replace('USERID', '5');

// Requests
let artemisRequests: ArtemisRequests;

// PageObjects
let editorPage: OnlineEditorPage;

// Container for a course dto
let course: any;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;
const programmingExerciseName = 'Cypress programming exercise ' + uid;
const programmingExerciseShortName = courseShortName;
const exercisePath = '/exercises';
const packageName = 'de.test';

// Selectors
const exerciseRow = '.course-exercise-row';

describe('Programming exercise participations', () => {
    before(() => {
        artemisRequests = new ArtemisRequests();
        setupCourseAndProgrammingExercise();
    });

    beforeEach(() => {
        editorPage = new OnlineEditorPage();
        registerQueries();
    });

    it('Makes a partially successful submission', function () {
        startParticipationInProgrammingExercise(student2, passwordStudent2);
        makePartiallySuccessfulSubmission();
    });

    it('Makes a successful submission', function () {
        startParticipationInProgrammingExercise(student3, passwordStudent3);
        makeSuccessfulSubmission();
    });

    it('Makes a failing submission', function () {
        startParticipationInProgrammingExercise(student1, passwordStudent1);
        makeFailingSubmission();
    });

    after(() => {
        // if (!!course) {
        //     cy.login(adminUsername, adminPassword);
        //     artemisRequests.courseManagement.deleteCourse(course.id);
        // }
    });
});

/**
 * Sets all the necessary cypress request hooks up.
 */
function registerQueries() {
    cy.intercept('POST', '/api/courses/*/exercises/*/participations').as('participateInExerciseQuery');
}

/**
 * Creates a course and a programming exercise inside that course.
 */
function setupCourseAndProgrammingExercise() {
    cy.login(adminUsername, adminPassword, '/');
    artemisRequests.courseManagement.createCourse(courseName, courseShortName).then((response) => {
        course = response.body;
        artemisRequests.courseManagement.addStudentToCourse(course.id, student1);
        artemisRequests.courseManagement.addStudentToCourse(course.id, student2);
        artemisRequests.courseManagement.addStudentToCourse(course.id, student3);
        //  We sleep to allow bamboo/bitbucket to synchronize the group rights, because the programming exercise creation fails otherwise
        cy.log('Created course. Sleeping before adding a programming exercise...');
        cy.wait(65000);
        artemisRequests.courseManagement.createProgrammingExercise(course, programmingExerciseName, programmingExerciseShortName, packageName);
    });
}

/**
 * Makes a submission, which fails the CI build and asserts that this is highlighted in the UI.
 */
function makeFailingSubmission() {
    const submission = { files: [{ name: 'BubbleSort.java', path: 'programming_exercise_submissions/build_error/BubbleSort.txt' }] };
    makeSubmissionAndVerifyResults(submission, () => {
        editorPage.getResultPanel().contains('Build Failed').should(beVisible);
        editorPage.getResultPanel().contains('0%').should(beVisible);
        editorPage.getBuildOutput().contains('[ERROR] COMPILATION ERROR').should(beVisible);
        editorPage.getInstructionSymbols().each(($el) => {
            cy.wrap($el).find('[data-icon="question"]').should(beVisible);
        });
    });
}

/**
 * Makes a submission, which passes and fails some tests, and asserts the outcome in the UI.
 */
function makePartiallySuccessfulSubmission() {
    editorPage.createFileInRootPackage('SortStrategy.java');
    makeSubmissionAndVerifyResults(partiallySuccessful, () => {
        editorPage.getResultPanel().contains('46%').should(beVisible);
        editorPage.getResultPanel().contains('6 of 13 passed').should(beVisible);
        editorPage.getBuildOutput().contains('No build results available').should(beVisible);
        editorPage.getInstructionSymbols().each(($el, $index) => {
            if ($index < 3) {
                cy.wrap($el).find('[data-icon="check"]').should(beVisible);
            } else {
                cy.wrap($el).find('[data-icon="times"]').should(beVisible);
            }
        });
    });
}

/**
 * Makes a submission, which passes all tests, and asserts the outcome in the UI.
 */
function makeSuccessfulSubmission() {
    editorPage.createFileInRootPackage('SortStrategy.java');
    editorPage.createFileInRootPackage('Context.java');
    editorPage.createFileInRootPackage('Policy.java');
    makeSubmissionAndVerifyResults(allSuccessful, () => {
        editorPage.getResultPanel().contains('100%').should(beVisible);
        editorPage.getResultPanel().contains('13 of 13 passed').should(beVisible);
        editorPage.getBuildOutput().contains('No build results available').should(beVisible);
        editorPage.getInstructionSymbols().each(($el) => {
            cy.wrap($el).find('[data-icon="check"]').should(beVisible);
        });
    });
}

/**
 * General method for entering, submitting and verifying something in the online editor.
 */
function makeSubmissionAndVerifyResults(submission: ProgrammingExerciseSubmission, verifyOutput: () => void) {
    editorPage.typeSubmission(submission, packageName);
    editorPage.submit();
    verifyOutput();
}

/**
 * Starts the participation in the test programming exercise.
 */
function startParticipationInProgrammingExercise(username: string, password: string) {
    cy.login(username, password, '/');
    cy.url().should('include', '/courses');
    cy.log('Participating in the programming exercise as a student...');
    cy.contains(courseName).parents('.card-header').click();
    cy.url().should('include', exercisePath);
    cy.get(exerciseRow).contains(programmingExerciseName).should(beVisible);
    cy.get(exerciseRow).find('.start-exercise').click();
    cy.wait('@participateInExerciseQuery');
    cy.get(exerciseRow).find('[buttonicon="folder-open"]').click();
    editorPage.waitForPageLoad();
}
