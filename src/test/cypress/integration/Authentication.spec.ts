/// <reference types="cypress" />

import { authTokenKey } from '../support/constants';

const username = Cypress.env('username');
const password = Cypress.env('password');

describe('Authentication tests', () => {
    beforeEach(() => {
        expect(username, 'username was set').to.be.a('string').and.not.be.empty;
        expect(password, 'password was set').to.be.a('string').and.not.be.empty;
        cy.logout();
    });

    it('logs in via the ui', function () {
        // @ts-ignore
        cy.get('#username').type(username);
        // @ts-ignore
        cy.get('#password').type(password).type('{enter}');
        cy.url()
            .should('include', '/courses')
            .then(() => {
                expect(localStorage.getItem(authTokenKey)).to.not.be.null;
            });
    });

    it('logs in programmatically and logs out via the ui', function () {
        cy.login(username, password);
        cy.url().should('include', '/courses');
        cy.get('#account-menu').click().get('#logout').click();
        cy.url().should('equal', Cypress.config().baseUrl + '/');
    });
});
