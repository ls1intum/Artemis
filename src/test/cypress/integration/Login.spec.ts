import { artemis } from '../support/ArtemisTesting';

const user = artemis.users.getStudentOne();
const loginPage = artemis.pageobjects.login;

describe('Login page tests', () => {
    it('Logs in via the UI', () => {
        cy.visit('/');
        loginPage.login(user);
        cy.url().should('include', '/courses');
        cy.getCookie('jwt').should('exist');
        cy.getCookie('jwt').should('have.property', 'value');
        cy.getCookie('jwt').should('have.property', 'httpOnly', true);
        cy.getCookie('jwt').should('have.property', 'sameSite', 'lax');
        // TODO: Uncomment once cypress is using https - cy.getCookie('jwt').should('have.property', 'secure', true);
    });

    it('Logs in programmatically and logs out via the UI', () => {
        cy.login(user, '/courses');
        cy.url().should('include', '/courses');
        cy.get('#account-menu').click().get('#logout').click();
        cy.url().should('equal', Cypress.config().baseUrl + '/');
        cy.getCookie('jwt').should('not.exist');
    });

    it('Displays error messages on wrong password', () => {
        loginPage.login({ username: 'some_user_name', password: 'lorem-ipsum' });
        cy.location('pathname').should('eq', '/');
        cy.get('.alert').should('exist').and('have.text', 'Failed to sign in! Please check your username and password and try again.');
        cy.get('.btn').click();
        cy.get('.btn').click();
    });

    it('Fails to access protected resource without login', () => {
        cy.visit('/course-management');
        cy.location('pathname').should('eq', '/');
    });

    it('Verify footer content', () => {
        cy.visit('/');
        loginPage.shouldShowFooter();
        loginPage.shouldShowAboutUsInFooter();
        loginPage.shouldShowRequestChangeInFooter();
        loginPage.shouldShowReleaseNotesInFooter();
        loginPage.shouldShowPrivacyStatementInFooter();
        loginPage.shouldShowImprintInFooter();
    });
});
