import { userManagementAPIRequest } from '../support/artemis';
import { USER_ID, USER_ROLE, admin, instructor, studentFour, studentOne, studentThree, studentTwo, tutor, users } from '../support/users';

describe('Setup users', () => {
    if (Cypress.env('createUsers')) {
        before('Creates all required users', () => {
            cy.login(admin);
            for (const userKey in USER_ID) {
                const user = users.getUserWithId(USER_ID[userKey]);
                userManagementAPIRequest.getUser(user.username).then((response) => {
                    if (!response.isOkStatusCode) {
                        userManagementAPIRequest.createUser(user.username, user.password, USER_ROLE[userKey]);
                    }
                });
            }
        });
    }

    it('Logs in once with all required users', () => {
        // If Artemis hasn't imported the required users from Jira we have to force this by logging in with these users once
        cy.login(admin);
        cy.login(instructor);
        cy.login(tutor);
        cy.login(studentOne);
        cy.login(studentTwo);
        cy.login(studentThree);
        cy.login(studentFour);
    });
});
