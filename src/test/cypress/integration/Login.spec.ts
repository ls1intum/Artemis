/// <reference types="cypress" />

describe('The Login Page', () => {
    it('sets authentication token after logging in via form submission', function () {
        const username = Cypress.env('username');
        const password = Cypress.env('password');

        expect(username, 'username was set').to.be.a('string').and.not.be.empty;
        expect(password, 'password was set').to.be.a('string').and.not.be.empty;

        cy.visit('/');
        // @ts-ignore
        cy.get('#username').type(username);
        // @ts-ignore
        cy.get('#password').type(password);
        cy.get('.btn').click();
        cy.url()
            .should('include', '/courses')
            .then(() => {
                expect(localStorage.getItem('jhi-authenticationtoken')).to.not.be.null;
            });
    });
});
