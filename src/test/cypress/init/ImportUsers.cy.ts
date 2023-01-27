import { artemis } from 'src/test/cypress/support/ArtemisTesting';

// Users
const users = artemis.users;
const admin = users.getAdmin();
const instructor = users.getInstructor();
const tutor = users.getTutor();
const studentOne = users.getStudentOne();
const studentTwo = users.getStudentTwo();
const studentThree = users.getStudentThree();

it('Logs in once with all required users', () => {
    // If Artemis hasn't imported the required users from Jira we have to force this by logging in with these users once
    cy.login(admin);
    cy.login(instructor);
    cy.login(tutor);
    cy.login(studentOne);
    cy.login(studentTwo);
    cy.login(studentThree);
});
