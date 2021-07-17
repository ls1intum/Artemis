/// <reference types="cypress" />

import { beVisible } from '../support/constants';
import { CourseManagementPage } from '../support/pageobjects/CourseManagementPage';
import { NavigationBar } from '../support/pageobjects/NavigationBar';
import { ArtemisRequests } from '../support/requests/ArtemisRequests';
import { generateUUID } from '../support/utils';

// Environmental variables
const adminUsername = Cypress.env('adminUsername');
const adminPassword = Cypress.env('adminPassword');
let username = Cypress.env('username');
if (Cypress.env('isCi')) {
    username = username.replace('USERID', '5');
}

// Requests
let artemisRequests: ArtemisRequests;

// PagegObjects
let courseManagementPage: CourseManagementPage;
let navigationBar: NavigationBar;

// Common primitives
let uid: string;
let courseName: string;
let courseShortName: string;

// Selectors
const fieldTitle = '#field_title';
const shortName = '#field_shortName';
const saveEntity = '#save-entity';
const modalDeleteButton = '.modal-footer > .btn-danger';

describe('Course management', () => {
    beforeEach(() => {
        courseManagementPage = new CourseManagementPage();
        navigationBar = new NavigationBar();
        registerQueries();
        uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        artemisRequests = new ArtemisRequests();
        cy.login(adminUsername, adminPassword, '/');
    });

    describe('Course creation', () => {
        let courseId: number;

        it('Creates a new course', function () {
            navigationBar.openCourseManagement();
            courseManagementPage.openCourseCreation();
            cy.get(fieldTitle).type(courseName);
            cy.get(shortName).type(courseShortName);
            cy.get('#field_testCourse').check();
            cy.get('#field_customizeGroupNamesEnabled').uncheck();
            cy.get(saveEntity).click();
            cy.wait('@createCourseQuery')
                .its('response.body')
                .then((body) => {
                    expect(body).property('id').to.be.a('number');
                    courseId = body.id;
                });
            courseManagementPage.getCourseCard(courseName, courseShortName).should('be.visible');
        });

        after(() => {
            if (!!courseId) {
                artemisRequests.courseManagement.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });

    describe('Course deletion', () => {
        beforeEach(() => {
            artemisRequests.courseManagement.createCourse(courseName, courseShortName).its('status').should('eq', 201);
        });

        it('Deletes an existing course', function () {
            navigationBar.openCourseManagement();
            courseManagementPage.openCourse(courseName, courseShortName);
            cy.get('.btn-danger').click();
            cy.get(modalDeleteButton).should('be.disabled');
            cy.get('[name="confirmExerciseName"]').type(courseName);
            cy.get(modalDeleteButton).should('not.be.disabled').click();
            cy.contains(courseManagementPage.courseSelector(courseName, courseShortName)).should('not.exist');
        });
    });

    describe('Manual student selection', () => {
        let courseId: number;

        beforeEach(() => {
            artemisRequests.courseManagement
                .createCourse(courseName, courseShortName)
                .its('body')
                .then((body) => {
                    expect(body).property('id').to.be.a('number');
                    courseId = body.id;
                });
        });

        it('Adds a student manually to the course', function () {
            cy.login(adminUsername, adminPassword);
            navigationBar.openCourseManagement();
            courseManagementPage.openStudentOverviewOfCourse(courseName, courseShortName);
            cy.get('#typeahead-basic').type(username);
            cy.wait('@getStudentQuery');
            cy.get('#ngb-typeahead-0')
                .contains(new RegExp('\\(' + username + '\\)'))
                .should(beVisible)
                .click();
            cy.wait('@addStudentQuery');
            cy.get('[deletequestion="artemisApp.course.courseGroup.removeFromGroup.modalQuestion"]').should(beVisible);
        });

        after(() => {
            if (!!courseId) {
                artemisRequests.courseManagement.deleteCourse(courseId).its('status').should('eq', 200);
            }
        });
    });
});

/**
 * Sets all the necessary cypress request hooks.
 */
function registerQueries() {
    cy.intercept('GET', '/api/users/search*').as('getStudentQuery');
    cy.intercept('POST', '/api/courses/*/students/' + username).as('addStudentQuery');
    cy.intercept('POST', '/api/courses').as('createCourseQuery');
}
