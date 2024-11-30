import { BASE_API } from '../constants';
import { Page } from '@playwright/test';

export class AccountManagementAPIRequests {
    private readonly page: Page;

    constructor(page: Page) {
        this.page = page;
    }

    async deleteSshPublicKey() {
        return await this.page.request.delete(`${BASE_API}/account/ssh-public-key`);
    }
}
