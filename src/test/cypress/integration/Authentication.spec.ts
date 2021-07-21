import { artemis } from '../support/ArtemisTesting';
import { authTokenKey } from '../support/constants';

describe('Authentication tests', () => {
    const user = artemis.users.getStudentOne();

    beforeEach(() => {
        cy.logout();
    });

    it('fails to access protected resource without login', () => {
        cy.visit('/course-management');
        cy.location('pathname').should('eq', '/');
    });

    it('logs in via the ui', function () {
        cy.loginWithGUI(user);
        cy.url()
            .should('include', '/courses')
            .then(() => {
                expect(localStorage.getItem(authTokenKey)).to.not.be.null;
            });
    });

    it('logs in programmatically and logs out via the ui', function () {
        cy.login(user, '/courses');
        cy.url().should('include', '/courses');
        cy.get('#account-menu').click().get('#logout').click();
        cy.url().should('equal', Cypress.config().baseUrl + '/');
    });

    it('displays error messages on wrong password', () => {
        cy.loginWithGUI({ username: 'artemis_admin', password: 'lorem-ipsum' });
        cy.location('pathname').should('eq', '/');
        cy.get('.alert').should('exist').and('have.text', 'Failed to sign in! Please check your username and password and try again.');
        cy.get('.btn').click();
        cy.get('.btn').click();
        cy.get('.alert-info')
            .should('exist')
            .and(
                'have.text',
                '\n                                Seems like you are having issues signing in :-(' +
                    'Please go to JIRA and try to sign in there.After that, try again here.\n                            ',
            );
    });
});
