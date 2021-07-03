/// <reference types="cypress" />

import { generateUUID } from '../support/utils';

// environmental variables
const username = Cypress.env('username');
const password = Cypress.env('password');
let instructorUsername = Cypress.env('instructorUsername');
let instructorPassword = Cypress.env('instructorPassword');
if (Cypress.env('isCi')) {
    instructorUsername = username.replace('USERID', '11');
    instructorPassword = password.replace('USERID', '11');
}

let testCourse: any;

//
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cy' + uid;

// FIXME: Enable tests again
describe('Modeling Exercise Spec', () => {
    before('Log in as instructor and create a course', () => {
        cy.intercept('POST', '/api/modeling-exercises').as('createModelingExercise');
        cy.login(instructorUsername, instructorPassword);
        cy.fixture('course.json').then((course) => {
            course.title = courseName;
            course.shortName = courseShortName;
            cy.createCourse(course).then((response) => {
                testCourse = response.body;
                cy.visit('/course-management').get('.card-body').should('contain', testCourse.title);
            });
        });
    });

    beforeEach('login as instructor', () => {
        cy.login(instructorUsername, instructorPassword);
    });

    after('Delete the test course', () => {
        cy.deleteCourse(testCourse.id);
    });

    describe('Create/Edit Modeling Exercise', () => {
        it('Create a new modeling exercise', () => {
            createNewModelingExercise();
            cy.contains('Cypress Modeling Exercise').should('exist');
        });

        it('Create Example Solution', () => {
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            createExampleSolutionModelingExercise();
            // TODO: right now the item gets dragged to the correct position but after mouse up it doesnt stay in the diagram
            cy.get('.sc-kstrdz > :nth-child(1) > :nth-child(1)').move({ x: -400, y: 100, force: true });
            cy.get('.card-body').contains('Save Example Solution').click();
            cy.get('.alerts').should('contain', 'Your diagram was saved successfully');
            cy.get('.col-lg-1 > .btn').click();
            cy.get('.card-body').contains('ul > .ng-star-inserted').should('contain.text', 'Example Submission').and('have.attr', 'href');
        });
    });
});

function createNewModelingExercise() {
    cy.visit(`/course-management/${testCourse.id}/exercises`);
    cy.get('#modeling-exercise-create-button').click();
    cy.get('#field_title').type('Cypress Modeling Exercise');
    cy.get('#field_categories').type('e2e-testing');
    cy.get('#field_points').type('10');
    cy.get(':nth-child(3) > .btn-primary').click();
    cy.wait('@createModelingExercise');
}

function createExampleSolutionModelingExercise() {
    cy.contains('Cypress Modeling Exercise').click();
    cy.get('.card-body').contains('Edit').click();
    cy.get('.card-body').contains('Create Example Solution').click();
}
