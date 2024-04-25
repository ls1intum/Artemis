import { UserRole } from '../users';
import { BASE_API } from '../constants';
import { Page } from '@playwright/test';
import { APIResponse } from 'playwright-core';

/**
 * A class which encapsulates all API requests related to user management.
 */
export class UserManagementAPIRequests {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Creates a new user
     * @param username the username of the new user
     * @param password the password of the new user
     * @param role the role of the new user
     */
    async createUser(username: string, password: string, role: UserRole): Promise<APIResponse> {
        return await this.page.request.post(`${BASE_API}/admin/users`, {
            data: {
                login: username,
                password,
                firstName: username,
                lastName: username,
                email: username + '@example.com',
                authorities: [role],
            },
        });
    }

    async getUser(username: string): Promise<APIResponse> {
        return await this.page.request.get(`${BASE_API}/admin/users/${username}`);
    }
}
