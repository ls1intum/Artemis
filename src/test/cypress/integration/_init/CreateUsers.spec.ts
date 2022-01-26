import { artemis } from 'src/test/cypress/support/ArtemisTesting';

const users = artemis.users;

it('Creates the required users for cypress', () => {
    // cy.login(users.getAdmin());
    // TODO: Find a way to fail the complete test suite immediately if this fails
    // users.createRequiredUsers(artemis.requests.courseManagement);
    cy.login(users.getInstructor());
    cy.login(users.getTutor());
    cy.login(users.getStudentOne());
    cy.login(users.getStudentTwo());
    cy.login(users.getStudentThree());
});
