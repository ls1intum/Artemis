/// <reference types="cypress" />

import { v4 as uuidv4 } from 'uuid';

// Environmental variables
const adminUsername = Cypress.env('adminUsername');
const adminPassword = Cypress.env('adminPassword');
const username = Cypress.env('username');
const password = Cypress.env('password');
if (Cypress.env('isCi') === 'true') {
    cy.log('REPLACED USER ID WITH 1');
    username.replace('USERID', '1');
    password.replace('USERID', '1');
}
// Common primitives
const uid = uuidv4().replace(/-/g, '');
const courseName = 'Cypress course';
const courseShortName = 'cypress' + uid;
const programmingExerciseName = 'Cypress programming exercise ' + uid;
const programmingExerciseShortName = courseShortName;
const exercisePath = '/exercises';
const longTimeout = 60000;
const beVisible = 'be.visible';

// Selectors
const fieldTitle = '#field_title';
const shortName = '#field_shortName';
const datepickerButtons = '.owl-dt-container-control-button';
const exerciseRow = '.course-exercise-row';
const modalDeleteButton = '.modal-footer > .btn-danger';

describe('Programming exercise', () => {
    before(() => {
        cy.intercept('/api/courses/course-management-overview*').as('courseManagementQuery');
    });

    it('Creates a new course, participates in it and deletes it afterwards', function () {
        cy.login(adminUsername, adminPassword);
        openCourseManagement();
        cy.get('.create-course').click();

        // Fill in the course-form
        cy.log('Filling out course information...');
        cy.get(fieldTitle).type(courseName);
        cy.get(shortName).type(courseShortName);
        cy.get('#field_testCourse').check();
        cy.get('#field_customizeGroupNamesEnabled').uncheck();
        cy.get('#save-entity').click();

        cy.log('Created course. Adding a programming exercise...');
        openExercisesFromCourseManagement();
        cy.get('#jh-create-entity').click();
        cy.url().should('include', '/programming-exercises/new');

        cy.log('Filling out programming exercise info...');
        cy.get(fieldTitle).type(programmingExerciseName);
        cy.get(shortName).type(programmingExerciseShortName);
        cy.get('#field_packageName').type('tum.exercise');
        cy.get('#field_points').type('100');
        cy.get('#field_allowOnlineEditor').check();

        cy.get('[label="artemisApp.exercise.releaseDate"] > :nth-child(1) > .btn').click();
        cy.get(datepickerButtons).wait(500).eq(1).should(beVisible).click();

        cy.get('.test-schedule-date.ng-pristine > :nth-child(1) > .btn').click();
        cy.get('.owl-dt-control-arrow-button').eq(1).click();
        cy.get('.owl-dt-day-3').eq(2).click();
        cy.get(datepickerButtons).eq(1).click();
        cy.get('[type="submit"]').click();
        // Creating a programming exercise takes a lot of time, so we increase the timeout here
        cy.url({ timeout: longTimeout }).should('include', exercisePath);
        cy.log('Successfully created a new programming exercise!');

        openCourseManagement();
        cy.get('.course-table-container').contains('0 Students').click();
        cy.get('#typeahead-basic').type(username);
        cy.contains(new RegExp(username)).should(beVisible).click();

        // Login as the student
        cy.login(username, password);

        cy.url().should('include', '/courses');
        cy.log('Participating in the programming exercise as a student...');
        cy.get('jhi-overview-course-card').click();
        cy.url().should('include', exercisePath);
        cy.get(exerciseRow).contains(programmingExerciseName).should(beVisible);
        cy.get(exerciseRow).find('.start-exercise').click();
        cy.get(exerciseRow).find('[buttonicon="folder-open"]', { timeout: 20000 }).click();

        // TODO: Actually interact with the online code editor
        // Asserts that every sub-task in the programming exercise is marked with a question mark
        cy.get('.stepwizard-row', { timeout: 10000 })
            .find('.stepwizard-step')
            .each(($el) => {
                cy.wrap($el).find('[data-icon="question"]').should(beVisible);
            });

        cy.log('Submitting default exercise for grading...');
        cy.get('#submit_button').click();
        // CI build is triggered here, so it will take quite some time
        cy.get('jhi-updating-result').contains('0 of 13 passed', { timeout: longTimeout }).should(beVisible);
        // Make sure that all sub-tasks are not marked with question marks, but with an indication that they failed
        cy.get('.stepwizard-row')
            .find('.stepwizard-step')
            .each(($el) => {
                cy.wrap($el).find('[data-icon="question"]').should('not.exist');
                cy.wrap($el).find('[data-icon="times"]').should(beVisible);
            });
        cy.log('Artemis graded our submission, so we are done here...');

        // Login is admin again
        cy.login(adminUsername, adminPassword);

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

        // Delete the course
        cy.log('Deleting the test course');
        openCourseManagement();
        cy.contains(`${courseName} (${courseShortName})`).parent().parent().click();
        cy.get('.btn-danger').click();
        cy.get(modalDeleteButton).should('be.disabled');
        cy.get('[name="confirmExerciseName"]').type(courseName);
        cy.get(modalDeleteButton).should('not.be.disabled').click();
        cy.contains(`${courseName} (${courseShortName})`).should('not.exist');
    });
});

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
