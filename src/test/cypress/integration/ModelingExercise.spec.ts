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
let modelingExercise: any;
//
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cy' + uid;

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
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            cy.get('#modeling-exercise-create-button').click();
            cy.get('#field_title').type('Cypress Modeling Exercise');
            cy.get('#field_categories').type('e2e-testing');
            cy.get('#field_points').type('10');
            cy.get(':nth-child(3) > .btn-primary').click();
            cy.wait('@createModelingExercise').then((interception) => {
                modelingExercise = interception?.response?.body;
            });
            cy.contains('Cypress Modeling Exercise').should('exist');
        });

        it('Create Example Solution', () => {
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            cy.contains('Cypress Modeling Exercise').click();
            cy.get('.card-body').contains('Edit').click();
            cy.get('.card-body').contains('Create Example Solution').click();
            cy.get('.sc-kstrdz > :nth-child(1) > :nth-child(1) > :nth-child(1)').drag('.sc-fubCfw', { position: 'bottomLeft', force: true });
            cy.get('.card-body').contains('Save Example Solution').click();
            cy.get('.alerts').should('contain', 'Your diagram was saved successfully');
            cy.get('.col-lg-1 > .btn').click();
            cy.get('ul > .ng-star-inserted > .btn').should('contain.text', 'Example Solution');
        });

        it('Creates Example Submission', () => {
            cy.visit(`/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            cy.get('.card-body').contains('Create Example Submission').click();
            cy.get('.sc-kstrdz > :nth-child(2) > :nth-child(1) > :nth-child(1)').drag('.sc-fubCfw', { position: 'bottomLeft', force: true });
            cy.get('.sc-kstrdz > :nth-child(1) > :nth-child(1) > :nth-child(1)').drag('.sc-fubCfw', { position: 'bottomLeft', force: true });
            cy.get('.sc-kstrdz > :nth-child(3) > :nth-child(1) > :nth-child(1)').drag('.sc-fubCfw', { position: 'bottomLeft', force: true });
            cy.get('.card-body').contains('Create new Example Submission').click();
            cy.get('.alerts').should('contain', 'Your diagram was saved successfully');
            cy.get('.col-lg-1 > .btn').click();
            cy.get(':nth-child(2) > :nth-child(18)').should('contain.text', 'Example Submission 1');
            cy.log('Assess Example Submission');
            cy.get(':nth-child(2) > :nth-child(18)').contains('Example Submission 1').click();
            cy.get('.col-lg-4 > :nth-child(2) > :nth-child(1)').click();
            cy.wait(500);
            cy.get('.sc-fubCfw > :nth-child(1) > :nth-child(1) > :nth-child(1)').dblclick('top');
            cy.get('.sc-iBaPrD > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type('-1');
            cy.get('.sc-iBaPrD > :nth-child(1) > :nth-child(3) ').type('Wrong');
            cy.get('.sc-iBaPrD > :nth-child(1) > :nth-child(13)').click();
            cy.get('.sc-iBaPrD > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type('1');
            cy.get('.sc-iBaPrD > :nth-child(1) > :nth-child(3) ').type('Good');
            cy.get('.sc-iBaPrD > :nth-child(1) > :nth-child(5)').click();
            cy.get('.sc-iBaPrD > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type('0');
            cy.get('.sc-iBaPrD > :nth-child(1) > :nth-child(3)').type('Unnecessary');
            cy.get('.card-body').click('top');
            cy.get('.sc-fubCfw > :nth-child(1) > :nth-child(1) > :nth-child(2) > :nth-child(1)').should('exist');
            cy.get('.col-lg-4 > :nth-child(1)').click();
        });

        it('Edit Existing Modeling Exercise', () => {
            cy.intercept('PUT', '/api/modeling-exercises').as('editModelingExercise');
            cy.visit(`/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            cy.get('#field_title')
                .clear()
                .type('Cypress EDITED ME' + uid);
            cy.get('#field_categories >>>>>>>:nth-child(2)>').click();
            cy.get('jhi-difficulty-picker > :nth-child(1) > :nth-child(4)').click({ force: true });
            cy.get(':nth-child(1) > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').type('01.01.2030', { force: true });
            cy.get('.ml-3 > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').type('02.01.2030', { force: true });
            cy.get(':nth-child(9) > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').type('03.01.2030', { force: true });
            cy.get('jhi-included-in-overall-score-picker > .btn-group > :nth-child(3)').click({ force: true });
            cy.get('#field_points').clear().type('100');
            cy.get(':nth-child(3) > .btn-primary').click();
            cy.wait('@editModelingExercise');
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            cy.get('tbody > tr > :nth-child(2)').should('contain.text', 'Cypress EDITED ME');
            cy.get('tbody > tr > :nth-child(3)').should('contain.text', 'Jan 1, 2030');
            cy.get('tbody > tr > :nth-child(6)').should('contain.text', '100');
        });
    });
});
