/// <reference types="cypress" />

import { generateUUID } from '../support/utils';
// https://day.js.org/docs is a tool for date/time
import * as dayjs from 'dayjs';

// environmental variables
let studentUsername = Cypress.env('username');
let studentPassword = Cypress.env('password');
let instructorUsername = Cypress.env('instructorUsername');
let instructorPassword = Cypress.env('instructorPassword');
const adminUsername = Cypress.env('adminUsername');
const adminPassword = Cypress.env('adminPassword');

// in case we are running the tests in a CI plan we need to get our usernames/passwords like this
if (Cypress.env('isCi')) {
    const baseUsername = Cypress.env('username');
    const basePassword = Cypress.env('password');
    studentUsername = baseUsername.replace('USERID', '100');
    studentPassword = baseUsername.replace('USERID', '100');
    instructorUsername = baseUsername.replace('USERID', '101');
    instructorPassword = basePassword.replace('USERID', '101');
}

let testCourse: any;
let modelingExercise: any;
//
const uid = generateUUID();
const courseName = 'Cypress course' + uid;
const courseShortName = 'cy' + uid;

describe('Modeling Exercise Spec', () => {
    before('Log in as admin and create a course', () => {
        cy.intercept('POST', '/api/modeling-exercises').as('createModelingExercise');
        cy.login(adminUsername, adminPassword);
        cy.fixture('course.json').then((course) => {
            course.title = courseName;
            course.shortName = courseShortName;
            cy.createCourse(course).then((courseResp) => {
                testCourse = courseResp.body;
                cy.visit(`/course-management/${testCourse.id}`).get('.row-md > :nth-child(2)').should('contain.text', testCourse.title);
                // set instructor group
                cy.get('.row-md > :nth-child(5) > :nth-child(8) >').click();
                cy.get('#typeahead-basic ').type(instructorUsername).type('{enter}');
                cy.get('#ngb-typeahead-0-0 >').contains(instructorUsername).click();
                cy.get('.breadcrumb > :nth-child(2)').click();
                // set student group
                cy.get('.row-md > :nth-child(5) > :nth-child(2) >').click();
                cy.get('#typeahead-basic ').type(studentUsername).type('{enter}');
                cy.get('#ngb-typeahead-1-0 >').contains(studentUsername).click();
                cy.get('.breadcrumb > :nth-child(2)').click();
            });
        });
    });

    after('Delete the test course', () => {
        cy.login(adminUsername, adminPassword);
        cy.deleteCourse(testCourse.id);
    });

    describe('Create/Edit Modeling Exercise', () => {
        beforeEach('login as instructor', () => {
            cy.login(instructorUsername, instructorPassword);
        });

        after('delete Modeling Exercise', () => {
            cy.login(adminUsername, adminPassword);
            cy.deleteModelingExercise(modelingExercise.id);
        });

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

    describe('Modeling Exercise Flow', () => {
        before('create Modeling Exercise with future release date', () => {
            cy.fixture('modeling-exercise.json').then((exercise) => {
                exercise.course = testCourse;
                exercise.releaseDate = dayjs().add(1, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS[Z]');
                exercise.dueDate = dayjs().add(2, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS[Z]');
                exercise.assessmentDueDate = dayjs().add(3, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS[Z]');
                cy.createModelingExercise(exercise).then((resp) => {
                    modelingExercise = resp.body;
                });
            });
        });

        it('Does not show unreleased Modeling Exercise', () => {
            cy.login(studentUsername, studentPassword, '/courses');
            cy.get('.card-body').contains(testCourse.title).click({ force: true });
        });

        it('Release a Modeling Exercise', () => {
            cy.login(instructorUsername, instructorPassword, `/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
        });
    });
});
