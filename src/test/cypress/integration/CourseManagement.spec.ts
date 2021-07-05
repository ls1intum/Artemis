/// <reference types="cypress" />

import { CourseManagementPage } from '../support/pageobjects/CourseManagementPage';
import { NavigationBar } from '../support/pageobjects/NavigationBar';
import { ArtemisRequests } from '../support/requests/ArtemisRequests';
import { generateUUID } from '../support/utils';

// Environmental variables
const adminUsername = Cypress.env('adminUsername');
const adminPassword = Cypress.env('adminPassword');
let username = Cypress.env('username');
let password = Cypress.env('password');
if (Cypress.env('isCi')) {
    username = username.replace('USERID', '1');
    password = password.replace('USERID', '1');
}

// Requests
var artemisRequests: ArtemisRequests;

// PagegObjects
var courseManagementPage: CourseManagementPage;
var navigationBar: NavigationBar;

// Common primitives
var uid: string;
var courseName: string;
var courseShortName: string;

// Selectors
const fieldTitle = '#field_title';
const shortName = '#field_shortName';
const saveEntity = '#save-entity';

describe('Course management', () => {
    before(() => {
        courseManagementPage = new CourseManagementPage();
        navigationBar = new NavigationBar();
        registerQueries();
        // We want a uuid for every test in this spec
        uid = generateUUID();
        courseName = 'Cypress course' + uid;
        courseShortName = 'cypress' + uid;
        artemisRequests = new ArtemisRequests();
        cy.login(adminUsername, adminPassword, '/');
    });

    describe('Creates a new course', () => {
        var courseId: number;

        it('Creates a new course', function () {
            navigationBar.openCourseManagement();
            courseManagementPage.openCourseCreation();
            // Fill in the course-form
            cy.log('Filling out course information...');
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
            if (courseId != null) artemisRequests.course_management.deleteCourse(courseId).its('status').should('eq', 200);
        });
    });

    after(() => {});
});

/**
 * Sets all the necessary cypress request hooks up.
 */
function registerQueries() {
    cy.intercept('GET', '/api/courses/course-management-overview*').as('courseManagementQuery');
    cy.intercept('GET', '/api/users/search*').as('getStudentQuery');
    cy.intercept('POST', '/api/courses/*/students/' + username).as('addStudentQuery');
    cy.intercept('POST', '/api/courses').as('createCourseQuery');
}
