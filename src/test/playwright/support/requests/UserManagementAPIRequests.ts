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
}
