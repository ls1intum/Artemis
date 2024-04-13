import { CypressCredentials } from '../users';

/**
 * A class which encapsulates UI selectors and actions for the Login Page.
 */
export class LoginPage {
    enterUsername(name: string) {
        cy.get('#username').type(name, { log: false });
    }

    enterPassword(password: string) {
        cy.get('#password').type(password, { log: false });
    }

    clickLoginButton() {
        cy.get('#login-button').click();
    }

    login(credentials: CypressCredentials) {
        this.enterUsername(credentials.username);
        this.enterPassword(credentials.password);
        this.clickLoginButton();
    }

    shouldShowFooter() {
        cy.get('#footer').should('be.visible');
    }

    shouldShowAboutUsInFooter() {
        cy.get('#about').should('be.visible').and('have.attr', 'href', '/about');
    }

    shouldShowRequestChangeInFooter() {
        cy.get('#feedback').should('be.visible').and('have.attr', 'href');
    }

    shouldShowReleaseNotesInFooter() {
        cy.get('#releases').should('be.visible').and('have.attr', 'href');
    }

    shouldShowPrivacyStatementInFooter() {
        cy.get('#privacy').should('be.visible').and('have.attr', 'href', '/privacy');
    }

    shouldShowImprintInFooter() {
        cy.get('#imprint').should('be.visible').and('have.attr', 'href', '/imprint');
    }
}
