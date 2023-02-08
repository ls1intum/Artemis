import { admin, instructor, studentOne, studentThree, studentTwo, tutor } from '../support/users';

it('Logs in once with all required users', () => {
    // If Artemis hasn't imported the required users from Jira we have to force this by logging in with these users once
    cy.login(admin);
    cy.login(instructor);
    cy.login(tutor);
    cy.login(studentOne);
    cy.login(studentTwo);
    cy.login(studentThree);
});
