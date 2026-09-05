import { UserRole } from '../users';
import { Page } from '@playwright/test';
import { APIResponse } from '@playwright/test';

/**
 * A class which encapsulates all API requests related to user management.
 */
export class UserManagementAPIRequests {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Creates a new user that can authenticate with the given password.
     *
     * `internal` matters and is not a detail: an account that is not internally managed authenticates against the
     * external directory, so the password below would never be used and the new user could not log in at all.
     *
     * @param username the username of the new user
     * @param password the password of the new user
     * @param role the role of the new user
     */
    async createUser(username: string, password: string, role: UserRole): Promise<APIResponse> {
        return await this.page.request.post(`api/account/admin/users`, {
            data: {
                login: username,
                password,
                firstName: username,
                lastName: username,
                email: username + '@example.com',
                authorities: [role],
                internal: true,
            },
        });
    }

    async getUser(username: string): Promise<APIResponse> {
        return await this.page.request.get(`api/account/admin/users/${username}`);
    }

    /**
     * Permanently deletes a user.
     *
     * Deletion confirms against the impact the administrator was shown, so the current fingerprint has to be fetched
     * first and handed back with the request. A bare `DELETE api/account/admin/users/{login}` is rejected with 400
     * because its body is required, and a stale fingerprint is rejected with 409, so both steps belong together and
     * live here rather than in each spec that has to clean up after itself.
     *
     * @param username the login of the user to delete
     * @returns the deletion response, or the impact response when that already failed
     */
    async deleteUser(username: string): Promise<APIResponse> {
        const impactResponse = await this.page.request.post('api/account/admin/users/deletion-impact', { data: { logins: [username] } });
        if (!impactResponse.ok()) {
            return impactResponse;
        }
        const impact = (await impactResponse.json()) as { users: { login: string; impactFingerprint: string }[] };
        return await this.page.request.delete('api/account/admin/users', {
            data: { users: impact.users.map((user) => ({ login: user.login, impactFingerprint: user.impactFingerprint })) },
        });
    }
}
