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

Cypress.Commands.add('login', (email, password) => {
    cy.request('POST', '/api/authenticate', { username: email, password: password, rememberMe: false })
        .its('body')
        .then((body) => {
            localStorage.setItem(authTokenKey, '"' + body.id_token + '"');
        });
    cy.visit('/');
    cy.log(`Logged in as '${email}'`);
});

Cypress.Commands.add('logout', () => {
    localStorage.removeItem(authTokenKey);
    cy.visit('/');
    cy.log('Logged out');
});
