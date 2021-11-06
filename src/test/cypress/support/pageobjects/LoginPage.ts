import { CypressCredentials } from '../users';

/**
 * A class which encapsulates UI selectors and actions for the Login Page.
 */
export class LoginPage {
    readonly footerSelector = '.footer';

    enterUsername(name: string) {
        cy.get('#username').type(name, { log: false });
    }

    enterPassword(password: string) {
        cy.get('#password').type(password, { log: false });
    }

    clickLoginButton() {
        cy.get('.btn-primary').click();
    }

    login(credentials: CypressCredentials) {
        this.enterUsername(credentials.username);
        this.enterPassword(credentials.password);
        this.clickLoginButton();
    }

    shouldShowFooter() {
        cy.get(this.footerSelector).should('be.visible');
    }

    shouldShowAboutUsInFooter() {
        cy.get(this.footerSelector).find('[jhitranslate="aboutUs"]').should('be.visible').and('have.attr', 'href', '/about');
    }

    shouldShowRequestChangeInFooter() {
        cy.get(this.footerSelector).find('[jhitranslate="requestChange"]').should('be.visible').and('have.attr', 'href');
    }

    shouldShowReleaseNotesInFooter() {
        cy.get(this.footerSelector).find('[jhitranslate="releaseNotes"]').should('be.visible').and('have.attr', 'href');
    }

    shouldShowPrivacyStatementInFooter() {
        cy.get(this.footerSelector).find('[jhitranslate="legal.privacy.title"]').should('be.visible').and('have.attr', 'href', '/privacy');
    }

    shouldShowImprintInFooter() {
        cy.get(this.footerSelector).find('[jhitranslate="legal.imprint.title"]').should('be.visible').and('have.attr', 'href', '/imprint');
    }
}
