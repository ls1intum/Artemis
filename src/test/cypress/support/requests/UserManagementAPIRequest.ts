import { BASE_API, GET, POST } from '../constants';
import { UserRole } from '../users';

/**
 * A class which encapsulates all API requests related to user management.
 */
export class UserManagementAPIRequests {
    /**
     * Creates a new user
     * @param username the username of the new user
     * @param password the password of the new user
     * @param role the role of the new user
     */
    createUser(username: string, password: string, role: UserRole) {
        const user = {
            login: username,
            password,
            firstName: username,
            lastName: username,
            email: username + '@example.com',
            authorities: [role],
        };
        return cy.request({
            url: `${BASE_API}/admin/users`,
            method: POST,
            body: user,
        });
    }

    getUser(username: string) {
        return cy.request({
            url: `${BASE_API}/admin/users/${username}`,
            method: GET,
            failOnStatusCode: false,
        });
    }
}
