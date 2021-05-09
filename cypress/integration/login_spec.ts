describe('/', () => {
    beforeEach(() => {
        cy.visit('/');
    });

    it('fails to access protected resource without login', () => {
        cy.visit('/course-management');
        cy.location('pathname').should('eq', '/');
    });

    it('Log in using GUI', () => {
        cy.loginWithGUI('artemis_admin', 'artemis_admin');
        cy.location('pathname').should('eq', '/courses');
    });

    it('displays error messages on wrong password', () => {
        // @ts-ignore
        cy.loginWithGUI('artemis_admin', 'lorem-ipsum');
        cy.location('pathname').should('eq', '/');
        cy.get('.alert').should('exist').and('have.text',
            'Failed to sign in! Please check your username and password and try again.');
        cy.get('.btn').click();
        cy.get('.btn').click();
        cy.get('.alert-info').should('exist').and('have.text',
            '\n                                Seems like you are having issues signing in :-(' +
            'Please go to JIRA and try to sign in there.After that, try again here.\n                            ');
    });

    it('logs in correctly using api request', () => {
        // @ts-ignore
        cy.loginWithAPI(Cypress.env('username'), Cypress.env('password'), '/courses');
        cy.location('pathname').should('eq', '/courses');
    });
});
