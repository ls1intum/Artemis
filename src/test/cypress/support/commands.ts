import { CypressCredentials } from './users';
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

import { authTokenKey, GROUP_SYNCHRONIZATION } from './constants';

export {};

declare global {
    namespace Cypress {
        interface Chainable {
            login(credentials: CypressCredentials, url?: string): any;
            logout(): any;
            loginWithGUI(credentials: CypressCredentials): any;
            getSettled(selector: string, options?: {}): Chainable<Cypress>;
            waitForGroupSynchronization(): void;
        }
    }
}

/**
 * Overwrite the normal cypress request to always add the authorization token.
 */
Cypress.Commands.overwrite('request', (originalFn, options) => {
    const token = Cypress.env(authTokenKey);

    if (!!token) {
        const authHeader = 'Bearer ' + token;
        if (!!options.headers) {
            options.headers.Authorization = authHeader;
        } else {
            options.headers = { Authorization: authHeader };
        }
        return originalFn(options);
    }

    return originalFn(options);
});

/**
 * Logs in using API and sets authToken in Cypress.env
 * */
Cypress.Commands.add('login', (credentials: CypressCredentials, url) => {
    const username = credentials.username;
    const password = credentials.password;
    cy.request({
        url: '/api/authenticate',
        method: 'POST',
        followRedirect: true,
        retryOnStatusCodeFailure: true,
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
Cypress.Commands.add('loginWithGUI', (credentials) => {
    cy.visit('/');
    cy.get('#username').type(credentials.username);
    cy.get('#password').type(credentials.password).type('{enter}');
    Cypress.env(authTokenKey, localStorage.getItem(authTokenKey));
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

/**
 * Servers that use bamboo and bitbucket need a sleep between creating a course and creating a programming exercise for group synchronization.
 * */
Cypress.Commands.add('waitForGroupSynchronization', () => {
    cy.log('Sleeping for group synchronization...');
    cy.wait(GROUP_SYNCHRONIZATION);
});
