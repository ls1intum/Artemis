/// <reference types="cypress" />

import { generateUUID } from '../support/utils';
// https://day.js.org/docs is a tool for date/time
import * as dayjs from 'dayjs';

// environmental variables
let studentUsername = Cypress.env('username');
let studentPassword = Cypress.env('password');
let tutorUsername = Cypress.env('taUsername');
let tutorPassword = Cypress.env('taPassword');
let instructorUsername = Cypress.env('instructorUsername');
let instructorPassword = Cypress.env('instructorPassword');
const adminUsername = Cypress.env('adminUsername');
const adminPassword = Cypress.env('adminPassword');

// in case we are running the tests in a CI plan we need to get our usernames/passwords like this
if (Cypress.env('isCi')) {
    const baseUsername = Cypress.env('username');
    const basePassword = Cypress.env('password');
    studentUsername = baseUsername.replace('USERID', '105');
    studentPassword = basePassword.replace('USERID', '105');
    tutorUsername = baseUsername.replace('USERID', '101');
    tutorPassword = basePassword.replace('USERID', '101');
    instructorUsername = baseUsername.replace('USERID', '103');
    instructorPassword = basePassword.replace('USERID', '103');
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
                // set tutor group
                cy.get('.row-md > :nth-child(5) > :nth-child(6) >').click();
                cy.get('#typeahead-basic ').type(tutorUsername).type('{enter}');
                cy.get('#ngb-typeahead-1-0 >').contains(tutorUsername).click();
                cy.get('.breadcrumb > :nth-child(2)').click();
                // set student group
                cy.get('.row-md > :nth-child(5) > :nth-child(2) >').click();
                cy.get('#typeahead-basic ').type(studentUsername).type('{enter}');
                cy.get('#ngb-typeahead-2-0 >').contains(studentUsername).click();
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
            cy.contains('Save').click();
            cy.wait('@createModelingExercise').then((interception) => {
                modelingExercise = interception?.response?.body;
            });
            cy.contains('Cypress Modeling Exercise').should('exist');
        });

        it('Create Example Solution', () => {
            cy.visit(`/course-management/${testCourse.id}/exercises`);
            cy.contains('Cypress Modeling Exercise').click();
            cy.get('.card-body').contains('Edit').click();
            cy.get('.sc-ksdxAp > :nth-child(1) > :nth-child(1) > :nth-child(1)').drag('.sc-furvIG', { position: 'bottomLeft', force: true });
            cy.contains('Save').click();
            cy.get('.row-md > :nth-child(4)').should('contain.text', 'Export');
            cy.get('.sc-furvIG > :nth-child(1)').should('exist');
        });

        it('Creates Example Submission', () => {
            cy.visit(`/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/example-submissions`);
            cy.contains('Create Example Submission').click();
            cy.get('.sc-ksdxAp > :nth-child(2) > :nth-child(1) > :nth-child(1)').drag('.sc-furvIG', { position: 'bottomLeft', force: true });
            cy.get('.sc-ksdxAp > :nth-child(1) > :nth-child(1) > :nth-child(1)').drag('.sc-furvIG', { position: 'bottomLeft', force: true });
            cy.get('.sc-ksdxAp > :nth-child(3) > :nth-child(1) > :nth-child(1)').drag('.sc-furvIG', { position: 'bottomLeft', force: true });
            cy.contains('Create new Example Submission').click();
            cy.get('.alerts').should('contain', 'Your diagram was saved successfully');
            cy.contains('Show Assessment').click();
            // cy.get('.sc-furvIG >> :nth-child(1)').should('contain.text', 'Class');
            // cy.get('.sc-furvIG').contains('Class').dblclick('top');
            cy.getSettled(`.sc-furvIG >> :nth-child(1)`).dblclick('top');
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type('-1');
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(3) ').type('Wrong');
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(13)').click();
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type('1');
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(3) ').type('Good');
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(5)').click();
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type('0');
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(3)').type('Unnecessary');
            cy.get('.card-body').click('top');
            cy.get('.sc-furvIG > :nth-child(1) > :nth-child(1) > :nth-child(2) > :nth-child(1)').should('exist');
            cy.contains('Save Example Assessment').click();
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
            cy.get('.ms-3 > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').type('02.01.2030', { force: true });
            cy.get(':nth-child(9) > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').type('03.01.2030', { force: true });
            cy.get('jhi-included-in-overall-score-picker > .btn-group > :nth-child(3)').click({ force: true });
            cy.get('#field_points').clear().type('100');
            cy.contains('Save').click();
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
                exercise.title = 'Cypress Modeling Exercise ' + uid;
                exercise.course = testCourse;
                exercise.releaseDate = dayjs().add(1, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS[Z]');
                exercise.dueDate = dayjs().add(2, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS[Z]');
                exercise.assessmentDueDate = dayjs().add(3, 'day').format('YYYY-MM-DDTHH:mm:ss.SSS[Z]');
                cy.createModelingExercise(exercise).then((resp) => {
                    modelingExercise = resp.body;
                });
            });
        });

        it('Student can not see unreleased Modeling Exercise', () => {
            cy.login(studentUsername, studentPassword, '/courses');
            cy.get('.card-body').contains(testCourse.title).click({ force: true });
            cy.get('.col-lg-8').should('contain.text', 'No exercises available for the course.');
        });

        it('Release a Modeling Exercise', () => {
            cy.login(instructorUsername, instructorPassword, `/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            cy.get(':nth-child(1) > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').clear().type(dayjs().subtract(1, 'hour').toString(), { force: true });
            cy.contains('Save').click();
        });

        it('Student can start and submit their model', () => {
            cy.intercept('/api/courses/*/exercises/*/participations').as('createModelingParticipation');
            cy.login(studentUsername, studentPassword, `/courses/${testCourse.id}`);
            cy.get('.col-lg-8').contains(`Cypress Modeling Exercise ${uid}`).click();
            cy.get('jhi-exercise-details-student-actions.col > >').contains('Start exercise').click();
            cy.wait('@createModelingParticipation');
            cy.get('.btn').should('contain.text', 'Open modelling editor');
            cy.get('.btn').click();
            cy.get('.sc-ksdxAp > :nth-child(2) > :nth-child(1) > :nth-child(1)').drag('.sc-furvIG', { position: 'bottomLeft', force: true });
            cy.get('.sc-ksdxAp > :nth-child(1) > :nth-child(1) > :nth-child(1)').drag('.sc-furvIG', { position: 'bottomLeft', force: true });
            cy.get('.sc-ksdxAp > :nth-child(3) > :nth-child(1) > :nth-child(1)').drag('.sc-furvIG', { position: 'bottomLeft', force: true });
            cy.get('.jhi-btn').click();
            cy.get('.alerts').should('contain.text', 'Your submission was successful! You can change your submission or wait for your feedback.');
            cy.get('.col-auto').should('contain.text', 'No graded result');
        });

        it('Close exercise for submissions', () => {
            cy.login(instructorUsername, instructorPassword, `/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            cy.get(':nth-child(2) > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').clear().type(dayjs().toString(), { force: true });
            cy.contains('Save').click();
        });

        it('Tutor can assess the submission', () => {
            cy.login(tutorUsername, tutorPassword, '/course-management');
            cy.get(`[href="/course-management/${testCourse.id}/assessment-dashboard"]`).click();
            cy.get('#field_showFinishedExercise').click();
            cy.get('tbody > tr > :nth-child(6) >').click();
            cy.get('.btn').click();
            cy.wait(10000);
            cy.get('.btn').click();
            cy.get('#assessmentLockedCurrentUser').should('contain.text', 'You have the lock for this assessment');
            cy.get('jhi-unreferenced-feedback > .btn').click();
            cy.get('jhi-assessment-detail > .card > .card-body > :nth-child(1) > :nth-child(2)').clear().type('1');
            cy.get('jhi-assessment-detail > .card > .card-body > :nth-child(2) > :nth-child(2)').clear().type('thanks, i hate it');
            cy.get('.sc-furvIG >> :nth-child(1)').dblclick('top');
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type('-1');
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(3) > ').type('Wrong', { force: true });
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(13) >').click('right', { force: true });
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type('1');
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(3) >').type('Good', { force: true });
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(5)').click();
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(2) > :nth-child(1) > :nth-child(2)').type('0');
            cy.get('.sc-nVjpj > :nth-child(1) > :nth-child(3) >').type('Unnecessary', { force: true });
            cy.get('.top-container > :nth-child(3) > :nth-child(4)').click();
            cy.get('.alerts').should('contain.text', 'Your assessment was submitted successfully!');
        });

        it('Close assessment period', () => {
            cy.login(instructorUsername, instructorPassword, `/course-management/${testCourse.id}/modeling-exercises/${modelingExercise.id}/edit`);
            cy.get(':nth-child(9) > jhi-date-time-picker.ng-untouched > .d-flex > .form-control').clear().type(dayjs().toString(), { force: true });
            cy.contains('Save').click();
        });

        it('Student can view the assessment and complain', () => {
            cy.intercept('POST', '/api/complaints').as('complaintCreated');
            cy.login(studentUsername, studentPassword, `/courses/${testCourse.id}/exercises/${modelingExercise.id}`);
            cy.get('jhi-submission-result-status > .col-auto').should('contain.text', 'Score').and('contain.text', '1 of 100 points');
            cy.get('jhi-exercise-details-student-actions.col > > :nth-child(2)').click();
            cy.url().should('contain', `/courses/${testCourse.id}/modeling-exercises/${modelingExercise.id}/participate/`);
            cy.get('.col-xl-8').should('contain.text', 'thanks, i hate it');
            cy.get('jhi-complaint-interactions > :nth-child(1) > .mt-4 > :nth-child(1)').click();
            cy.get('#complainTextArea').type('Thanks i hate you :^)');
            cy.get('.col-6 > .btn').click();
            cy.wait('@complaintCreated');
        });

        it('Instructor can see complaint and reject it', () => {
            cy.login(instructorUsername, instructorPassword, `/course-management/${testCourse.id}/assessment-dashboard`);
            cy.get('.col-md-4 > .card > .card-body').contains('Total complaints: 1').click();
            cy.get('tr > .text-center >').click();
            cy.get('#responseTextArea').type('lorem ipsum...');
            cy.get('#rejectComplaintButton').click();
            cy.get('.alerts').should('contain.text', 'Response to complaint has been submitted');
        });
    });
});
