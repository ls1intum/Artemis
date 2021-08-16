import {
  usernameLoginSelector,
  forgetYourPasswordSelector,
  emailResetPasswordSelector,
  submitInitResetPasswordSelector,
  classInvalid,
  classValid,
} from '../../support/commands';

describe('forgot your password', () => {
  const username = Cypress.env('E2E_USERNAME') ?? 'admin';

  before(() => {
    cy.window().then(win => {
      win.sessionStorage.clear();
    });
    cy.clearCookies();
    cy.visit('');
    cy.clickOnLoginItem();
    cy.get(usernameLoginSelector).type(username);
    cy.get(forgetYourPasswordSelector).click();
  });

  beforeEach(() => {
    cy.intercept('POST', '/api/account/reset-password/init').as('initResetPassword');
  });

  it('requires email', () => {
    cy.get(emailResetPasswordSelector).should('have.class', classInvalid).type('user@gmail.com');
    cy.get(emailResetPasswordSelector).should('have.class', classValid);
    cy.get(emailResetPasswordSelector).clear();
  });

  it('should be able to init reset password', () => {
    cy.get(emailResetPasswordSelector).type('user@gmail.com');
    cy.get(submitInitResetPasswordSelector).click({ force: true });
    cy.wait('@initResetPassword').then(({ request, response }) => expect(response.statusCode).to.equal(200));
  });
});
