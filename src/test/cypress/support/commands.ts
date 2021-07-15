// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add('login', (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add('drag', { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add('dismiss', { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite('visit', (originalFn, url, options) => { ... })

import { authTokenKey } from './constants';

export {};

declare global {
    namespace Cypress {
        interface Chainable {
            login(username: String, password: String, url?: String): any;
            logout(): any;
            loginWithGUI(username: String, password: String): any;
            createCourse(course: String): Chainable<Cypress.Response>;
            deleteCourse(courseID: number): Chainable<Cypress.Response>;
            deleteModelingExercise(courseID: number): Chainable<Cypress.Response>;
            createModelingExercise(modelingExercise: String): Chainable<Cypress.Response>;
            getSettled(selector: String, options?: {}): Chainable<Cypress>;
        }
    }
}

/**
 * Logs in using API and sets authToken in Cypress.env
 * */
Cypress.Commands.add('login', (username, password, url) => {
    cy.request({
        url: '/api/authenticate',
        method: 'POST',
        followRedirect: true,
        body: {
            username,
            password,
            rememberMe: true,
        },
    })
        .its('body')
        .then((res) => {
            localStorage.setItem(authTokenKey, '"' + res.id_token + '"');
            Cypress.env(authTokenKey, res.id_token);
        });
    if (url) {
        cy.visit(url);
    }
});

/**
 * Log out and removes all references to authToken
 * */
Cypress.Commands.add('logout', () => {
    localStorage.removeItem(authTokenKey);
    // The 'jhi-previousurl' can cause issues when it is not cleared
    sessionStorage.clear();
    Cypress.env(authTokenKey, '');
    cy.visit('/');
    cy.location('pathname').should('eq', '/');
    cy.log('Logged out');
});

/**
 * Logs in using GUI and sets authToken in Cypress.env
 * */
Cypress.Commands.add('loginWithGUI', (username, password) => {
    cy.visit('/');
    cy.get('#username').type(username);
    cy.get('#password').type(password).type('{enter}');
    Cypress.env(authTokenKey, localStorage.getItem(authTokenKey));
});

/**
 * Creates a course with API request
 * @param course is a course object in json format
 * @return Chainable<Cypress.Response> the http response of the POST request
 * */
Cypress.Commands.add('createCourse', (course: string) => {
    cy.request({
        url: '/api/courses',
        method: 'POST',
        body: course,
        headers: {
            Authorization: 'Bearer ' + Cypress.env(authTokenKey),
        },
    }).then((response) => {
        return response;
    });
});

/**
 * Deletes course with courseID
 * @param courseID id of the course that is to be deleted
 * @return Chainable<Cypress.Response> the http response of the DELETE request
 * */
Cypress.Commands.add('deleteCourse', (courseID: number) => {
    cy.request({
        url: `/api/courses/${courseID}`,
        method: 'DELETE',
        headers: { Authorization: `Bearer ${Cypress.env(authTokenKey)}` },
    }).then((response) => {
        return response;
    });
});

/**
 * Creates a modelingExercise with API request
 * @param modelingExercise is a modeling exercise object in json format
 * @return Chainable<Cypress.Response> the http response of the POST request
 * */
Cypress.Commands.add('createModelingExercise', (modelingExercise: string) => {
    cy.request({
        url: '/api/modeling-exercises',
        method: 'POST',
        body: modelingExercise,
        headers: {
            Authorization: 'Bearer ' + Cypress.env(authTokenKey),
        },
    }).then((response) => {
        return response;
    });
});

/**
 * Deletes modeling exercise with exerciseID
 * @param exerciseID id of the exercise that is to be deleted
 * @return Chainable<Cypress.Response> the http response of the DELETE request
 * */
Cypress.Commands.add('deleteModelingExercise', (exerciseID: number) => {
    cy.request({
        url: `/api/modeling-exercises/${exerciseID}`,
        method: 'DELETE',
        headers: { Authorization: `Bearer ${Cypress.env(authTokenKey)}` },
    }).then((response) => {
        return response;
    });
});

/** recursively gets an element, returning only after it's determined to be attached to the DOM for good
 *  this prevents the "Element is detached from DOM" issue in some cases
 */
Cypress.Commands.add('getSettled', (selector, opts = {}) => {
    const retries = opts.retries || 3;
    const delay = opts.delay || 100;

    const isAttached = (resolve: any, count = 0) => {
        const el = Cypress.$(selector);

        // is element attached to the DOM?
        count = Cypress.dom.isAttached(el) ? count + 1 : 0;

        // hit our base case, return the element
        if (count >= retries) {
            return resolve(el);
        }

        // retry after a bit of a delay
        setTimeout(() => isAttached(resolve, count), delay);
    };

    // wrap, so we can chain cypress commands off the result
    return cy.wrap(null).then(() => {
        return new Cypress.Promise((resolve) => {
            return isAttached(resolve, 0);
        }).then((el) => {
            return cy.wrap(el);
        });
    });
});
