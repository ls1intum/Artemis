/// <reference types="cypress" />

import { generateUUID } from '../support/utils';
import { OnlineEditorPage, ProgrammingExerciseSubmission } from '../pageobjects/OnlineEditorPage';
import allSuccessful from '../fixtures/programming_exercises/all_successful/submission.json';
import partiallySuccessful from '../fixtures/programming_exercises/partially_successful/submission.json';

// Environmental variables
const adminUsername = Cypress.env('adminUsername');
const adminPassword = Cypress.env('adminPassword');
let username = Cypress.env('username');
let password = Cypress.env('password');
if (Cypress.env('isCi')) {
    username = username.replace('USERID', '1');
    password = password.replace('USERID', '1');
}

// PageObjects
var editorPage: OnlineEditorPage;

// Common primitives
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cypress' + uid;
const programmingExerciseName = 'Cypress programming exercise ' + uid;
const programmingExerciseShortName = courseShortName;
const exercisePath = '/exercises';
const longTimeout = 60000;
const beVisible = 'be.visible';
const packageName = 'de.test';

// Selectors
const fieldTitle = '#field_title';
const shortName = '#field_shortName';
const saveEntity = '#save-entity';
const datepickerButtons = '.owl-dt-container-control-button';
const exerciseRow = '.course-exercise-row';
const modalDeleteButton = '.modal-footer > .btn-danger';
const buildingAndTesting = 'Building and testing...';

describe('Programming exercise', () => {
    before(() => {
        editorPage = new OnlineEditorPage();
        registerQueries();
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
        cleanupProgrammingExerciseAndCourse();
    });
});

/**
 * Sets all the necessary cypress request hooks up.
 */
function registerQueries() {
    cy.intercept('GET', '/api/courses/course-management-overview*').as('courseManagementQuery');
    cy.intercept('GET', '/api/users/search*').as('getStudentQuery');
    cy.intercept('POST', '/api/courses/*/students/' + username).as('addStudentQuery');
    cy.intercept('DELETE', '/api/programming-exercises/*').as('deleteProgrammingExerciseQuery');
    cy.intercept('POST', '/api/courses').as('createCourseQuery');
    cy.intercept('POST', '/api/programming-exercises/setup').as('createProgrammingExerciseQuery');
    cy.intercept('POST', '/api/courses/*/exercises/*/participations').as('participateInExerciseQuery');
}

/**
 * Creates a course and a programming exercise inside that course.
 */
function setupCourseAndProgrammingExercise() {
    cy.login(adminUsername, adminPassword, '/');
    createTestCourse();
    // We sleep for 80 seconds to allow bamboo/bitbucket to synchronize the group rights, because the programming exercise creation fails otherwise
    cy.log('Created course. Sleeping before adding a programming exercise...');
    // cy.wait(80000);
    openExercisesFromCourseManagement();
    createProgrammingExercise();
    addStudentToCourse();
}

/**
 * Makes a submission, which fails the CI build and asserts that this is highlighted in the UI.
 */
function makeFailingSubmission() {
    var submission = { files: [{ name: 'BubbleSort.java', path: 'programming_exercises/build_error/BubbleSort.txt' }] };
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
    editorPage.createFileInRootPackage('Client.java');
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
    editorPage.save();
    editorPage.submit();
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
}

/**
 * Adds the test student to the test course.
 */
function addStudentToCourse() {
    openCourseManagement();
    getCourseCard().contains('0 Students').click();
    cy.get('#typeahead-basic').type(username);
    cy.wait('@getStudentQuery');
    cy.get('#ngb-typeahead-0')
        .contains(new RegExp('\\(' + username + '\\)'))
        .should(beVisible)
        .click();
    cy.wait('@addStudentQuery');
    cy.get('[deletequestion="artemisApp.course.courseGroup.removeFromGroup.modalQuestion"]').should('be.visible');
}

/**
 * Fills out the course formula and clicks the save button at the end.
 */
function createTestCourse() {
    openCourseManagement();
    cy.get('.create-course').click();
    // Fill in the course-form
    cy.log('Filling out course information...');
    cy.get(fieldTitle).type(courseName);
    cy.get(shortName).type(courseShortName);
    cy.get('#field_testCourse').check();
    cy.get('#field_customizeGroupNamesEnabled').uncheck();
    cy.get(saveEntity).click();
    cy.wait('@createCourseQuery');
}

/**
 * Opens the course management page via the menu at the top and waits until it is loaded.
 */
function openCourseManagement() {
    cy.log('Opening course-management page...');
    cy.get('#course-admin-menu').should(beVisible).click();
    cy.wait('@courseManagementQuery', { timeout: 30000 });
    cy.url({ timeout: 10000 }).should('include', '/course-management');
}

/**
 * Opens the exercises (of the first found course).
 */
function openExercisesFromCourseManagement() {
    getCourseCard().find('.card-footer').eq(0).children().eq(0).click();
    cy.url().should('include', exercisePath);
}

/**
 * @returns Returns the cypress chainable containing the root element of the course card of our created course. This can be used to find specific elements within this course card.
 */
function getCourseCard() {
    return cy.contains(`${courseName} (${courseShortName})`).parent().parent().parent();
}

/**
 * Creates a new programming exercise.
 */
function createProgrammingExercise() {
    cy.get('#jh-create-entity').click();
    cy.url().should('include', '/programming-exercises/new');
    cy.log('Filling out programming exercise info...');
    cy.get(fieldTitle).type(programmingExerciseName);
    cy.get(shortName).type(programmingExerciseShortName);
    cy.get('#field_packageName').type(packageName);
    cy.get('[label="artemisApp.exercise.releaseDate"] > :nth-child(1) > .btn').should(beVisible).click();
    cy.get(datepickerButtons).wait(500).eq(1).should(beVisible).click();

    cy.get('.test-schedule-date.ng-pristine > :nth-child(1) > .btn').click();
    cy.get('.owl-dt-control-arrow-button').eq(1).click();
    cy.get('.owl-dt-day-3').eq(2).click();
    cy.get(datepickerButtons).eq(1).should(beVisible).click();
    cy.get('#field_points').type('100');
    cy.get('#field_allowOnlineEditor').check();

    cy.get(saveEntity).click();
    cy.wait('@createProgrammingExerciseQuery');
    // Creating a programming exercise takes a lot of time, so we increase the timeout here
    cy.url().should('include', exercisePath);
    cy.log('Successfully created a new programming exercise!');
}

/**
 * Deletes the previously created programming exercise and course.
 */
function cleanupProgrammingExerciseAndCourse() {
    // Login is admin again
    cy.login(adminUsername, adminPassword, '/');
    deleteProgrammingExercise();
    deleteCourse();
}

/**
 * Navigates to the course management and deletes the test course.
 */
function deleteCourse() {
    cy.log('Deleting the test course');
    openCourseManagement();
    cy.contains(`${courseName} (${courseShortName})`).parent().parent().click();
    cy.get('.btn-danger').click();
    cy.get(modalDeleteButton).should('be.disabled');
    cy.get('[name="confirmExerciseName"]').type(courseName);
    cy.get(modalDeleteButton).should('not.be.disabled').click();
    cy.contains(`${courseName} (${courseShortName})`).should('not.exist');
}

/**
 * Navigates to the course management and deletes the programming exercise from the test course.
 */
function deleteProgrammingExercise() {
    openCourseManagement();
    openExercisesFromCourseManagement();
    cy.log('Deleting programming exercise...');
    cy.get('[deletequestion="artemisApp.programmingExercise.delete.question"]').click();
    // Check all checkboxes to get rid of the git repositories and build plans
    cy.get('.modal-body')
        .find('[type="checkbox"]')
        .each(($el) => {
            cy.wrap($el).check();
        });
    cy.get('[type="text"], [name="confirmExerciseName"]').type(programmingExerciseName).type('{enter}');
    cy.wait('@deleteProgrammingExerciseQuery');
}
