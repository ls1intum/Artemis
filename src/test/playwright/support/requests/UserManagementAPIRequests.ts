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
     * because its body is required, and a stale fingerprint with 409, so both steps belong together and live here
     * rather than in each spec that has to clean up after itself.
     *
     * The single-user endpoint is used on purpose: it answers 200 only when the account is really gone, and 409 or
     * 403 when the plan changed or retention refused it. The bulk endpoint reports the same outcomes per user inside
     * a body it always returns with 200, so a caller checking the status alone would read a refusal as a success.
     *
     * @param username the login of the user to delete
     * @returns the deletion response, or the impact response when that already failed
     */
    async deleteUser(username: string): Promise<APIResponse> {
        const impactResponse = await this.page.request.get(`api/account/admin/users/${username}/deletion-impact`);
        if (!impactResponse.ok()) {
            return impactResponse;
        }
        const impact = (await impactResponse.json()) as { impactFingerprint: string };
        return await this.page.request.delete(`api/account/admin/users/${username}`, { data: { impactFingerprint: impact.impactFingerprint } });
    }
}
