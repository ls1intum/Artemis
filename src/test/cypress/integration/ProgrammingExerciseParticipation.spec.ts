/// <reference types="cypress" />

import { generateUUID } from '../support/utils';
import { OnlineEditorPage, ProgrammingExerciseSubmission } from '../support/pageobjects/OnlineEditorPage';
import allSuccessful from '../fixtures/programming_exercise_submissions/all_successful/submission.json';
import partiallySuccessful from '../fixtures/programming_exercise_submissions/partially_successful/submission.json';
import { CourseManagementPage } from '../support/pageobjects/CourseManagementPage';
import { NavigationBar } from '../support/pageobjects/NavigationBar';
import { beVisible } from '../support/constants';
import { ArtemisRequests } from './../support/requests/ArtemisRequests';

// Environmental variables
const adminUsername = Cypress.env('adminUsername');
const adminPassword = Cypress.env('adminPassword');
let username = Cypress.env('username');
let password = Cypress.env('password');
if (Cypress.env('isCi')) {
    username = username.replace('USERID', '5');
    password = password.replace('USERID', '5');
}

// Requests
var artemisRequests: ArtemisRequests;

// PageObjects
var editorPage: OnlineEditorPage;
var courseManagementPage: CourseManagementPage;
var navigationBar: NavigationBar;
var course: any;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;
const programmingExerciseName = 'Cypress programming exercise ' + uid;
const programmingExerciseShortName = courseShortName;
const exercisePath = '/exercises';
const longTimeout = 80000;
const packageName = 'de.test';

// Selectors
const exerciseRow = '.course-exercise-row';
const buildingAndTesting = 'Building and testing...';

describe('Programming exercise', () => {
    before(() => {
        editorPage = new OnlineEditorPage();
        courseManagementPage = new CourseManagementPage();
        navigationBar = new NavigationBar();
        registerQueries();
        artemisRequests = new ArtemisRequests();
        setupCourseAndProgrammingExercise();
    });

    it('Creates a new course, participates in it and deletes it afterwards', function () {
        cy.login(username, password, '/');
        startParticipationInProgrammingExercise();
        makeFailingSubmission();
        makePartiallySuccessfulSubmission();
        makeSuccessfulSubmission();
    });

    after(() => {
        if (course != null) {
            cy.login(adminUsername, adminPassword);
            artemisRequests.courseManagement.deleteCourse(course.id);
        }
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
        artemisRequests.courseManagement.addStudentToCourse(course.id, username);
        //  We sleep for 80 seconds to allow bamboo/bitbucket to synchronize the group rights, because the programming exercise creation fails otherwise
        cy.log('Created course. Sleeping before adding a programming exercise...');
        cy.wait(65000);
        artemisRequests.courseManagement.createProgrammingExercise(course, programmingExerciseName, programmingExerciseShortName, packageName);
    });
}

/**
 * Makes a submission, which fails the CI build and asserts that this is highlighted in the UI.
 */
function makeFailingSubmission() {
    var submission = { files: [{ name: 'BubbleSort.java', path: 'programming_exercise_submissions/build_error/BubbleSort.txt' }] };
    makeSubmissionAndVerifyResults(submission, () => {
        editorPage.getResultPanel().contains('Build Failed').should(beVisible);
        editorPage.getResultPanel().contains('0%').should(beVisible);
        editorPage.getInstructionSymbols().each(($el) => {
            cy.wrap($el).find('[data-icon="question"]').should(beVisible);
        });
        editorPage.getBuildOutput().contains('[ERROR] COMPILATION ERROR').should(beVisible);
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
    editorPage.submit(true);
    editorPage.getResultPanel().contains(buildingAndTesting, { timeout: 15000 }).should(beVisible);
    editorPage.getBuildOutput().contains(buildingAndTesting).should(beVisible);
    editorPage.getResultPanel().contains('GRADED', { timeout: longTimeout }).should(beVisible);
    verifyOutput();
}

/**
 * Starts the participation in the test programming exercise.
 */
function startParticipationInProgrammingExercise() {
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
