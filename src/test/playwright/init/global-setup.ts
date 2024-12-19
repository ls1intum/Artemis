import fs from 'fs';
import path from 'path';
import { SSH_KEYS_PATH, SSH_KEY_NAMES } from '../support/pageobjects/exercises/programming/GitClient';

async function globalSetup() {
    console.log('Running global setup...');

    // Set correct permissions to the SSH keys
    try {
        for (const keyName of Object.values(SSH_KEY_NAMES)) {
            const privateKeyPath = path.join(SSH_KEYS_PATH, keyName);
            const publicKeyPath = `${privateKeyPath}.pub`;

            fs.chmodSync(privateKeyPath, 0o600);
            fs.chmodSync(publicKeyPath, 0o644);
        }
    } catch (error) {
        console.error('Error during SSH key setup:', error);
    }

    console.log('Global setup completed.');
}

export default globalSetup;
