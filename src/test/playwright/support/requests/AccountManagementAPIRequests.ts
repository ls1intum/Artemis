import { BASE_API } from '../constants';
import { Page } from '@playwright/test';

export class AccountManagementAPIRequests {
    private readonly page: Page;
    private readonly PLAYWRIGHT_SSH_LABEL = 'artemis_playwright_ssh';

    constructor(page: Page) {
        this.page = page;
    }

    /**
     * Deletes the existing test SSH public keys.
     * Currently, the API does not return the ID of the created SSH public key.
     * As a workaround, we fetch all SSH public keys and delete the test SSH key based on the label.
     * This method is designed to be safe to call even when no keys exist.
     * */
    async deleteSshPublicKey() {
        const publicKeysResponse = await this.page.request.get(`${BASE_API}/programming/ssh-settings/public-keys`);
        if (!publicKeysResponse.ok()) {
            // User might not have any SSH keys configured, which is fine
            return;
        }
        const publicKeys = await publicKeysResponse.json();
        if (!Array.isArray(publicKeys)) {
            return;
        }
        for (const publicKey of publicKeys) {
            if (publicKey.label === this.PLAYWRIGHT_SSH_LABEL) {
                await this.page.request.delete(`${BASE_API}/programming/ssh-settings/public-key/${publicKey.id}`);
            }
        }
    }
}
