import { artemis } from 'src/test/cypress/support/ArtemisTesting';

const users = artemis.users;

it('Logs in once with all required users', () => {
    // If Artemis hasn't imported the required users from Jira we have to force this by loggin in with these users once
    cy.login(users.getInstructor());
    cy.login(users.getTutor());
    cy.login(users.getStudentOne());
    cy.login(users.getStudentTwo());
    cy.login(users.getStudentThree());
});
