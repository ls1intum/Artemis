import { artemis } from '../support/ArtemisTesting';
import { authTokenKey } from '../support/constants';

const user = artemis.users.getStudentOne();
const loginPage = artemis.pageobjects.login;

describe('Logout tests', () => {
    it('Logs in via the UI and logs out', () => {
        cy.visit('/');
        loginPage.login(user);
        cy.url()
            .should('include', '/courses')
            .then(() => {
                expect(localStorage.getItem(authTokenKey)).to.not.be.null;
            });

        cy.get('#account-menu').click().get('#logout').click();
        cy.url()
            .should('equal', Cypress.config().baseUrl + '/')
            .then(() => {
                expect(localStorage.getItem(authTokenKey)).to.be.null;
            });
    });

    it('Logs out by pressing OK when unsaved changes on exercise mode', () => {
        /**
         * TODO: 1 - Login
         * TODO: 2 - Create a course and modeling exercise
         * TODO: 3 - Open modeling exercise and make changes
         * TODO: 4 - Click logout button and confirms the popup
         * TODO: 5 - It should log out
         */
    });

    it('Stays logged in by pressing cancel when trying to logout during unsaved changes on exercise mode', () => {
        /**
         * TODO: 1 - Login
         * TODO: 2 - Create a course and modeling exercise
         * TODO: 3 - Open modeling exercise and make changes
         * TODO: 4 - Click logout button and cancel the popup
         * TODO: 5 - It should stay logged in
         */
    });

    it('Logs out by pressing OK when unsaved changes on exam mode', () => {
        /**
         * TODO: 1 - Login
         * TODO: 2 - Create a course and one exam
         * TODO: 3 - Open exam and make changes
         * TODO: 4 - Click logout button and cancel the popup
         * TODO: 5 - It should stay logged in
         */
    });

    it('Stays logged in by pressing cancel when trying to logout during unsaved changes on exam mode', () => {
        /**
         * TODO: 1 - Login
         * TODO: 2 - Create a course and one exam
         * TODO: 3 - Open exam and make changes
         * TODO: 4 - Click logout button and confirm the popup
         * TODO: 5 - It should log out
         */
    });
});
