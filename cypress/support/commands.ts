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
// Cypress.Commands.add("login", (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add("drag", { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add("dismiss", { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite("visit", (originalFn, url, options) => { ... })

export {};

declare global {
    namespace Cypress {
        interface Chainable {
            loginWithGUI(username: String, password: String): any;

            loginWithAPI(username: String, password: String, url: String): any;
        }
    }
}

Cypress.Commands.add('loginWithGUI', (username, password) => {
    cy.visit('/');
    cy.get('#username').type(username);
    cy.get('#password').type(password);
    cy.get('.btn').click();
});

Cypress.Commands.add('loginWithAPI', (username, password, url) => {
    let token = '';
    cy.request({url: '/api/authenticate', method: 'POST', followRedirect: true, body: {
            username,
            password,
            rememberMe: true
    }}).its('body').then((res) => {
        localStorage.setItem('jhi-authenticationtoken', '"' + res.id_token + '"');
        token = res.id_token;
    });
    cy.visit({url, method: 'GET', headers: {Authorization: `Bearer ${token}`}});
});
