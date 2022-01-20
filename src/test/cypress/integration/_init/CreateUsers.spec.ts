import { artemis } from 'src/test/cypress/support/ArtemisTesting';

const users = artemis.users;

it('Creates the required users for cypress', () => {
    cy.login(users.getAdmin());
    // TODO: Find a way to fail the complete test suite immediately if this fails
    users.createRequiredUsers(artemis.requests.courseManagement);
});
