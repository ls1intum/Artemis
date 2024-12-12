import fs from 'fs';
import path from 'path';
import os from 'os';

export enum SshEncryptionAlgorithm {
    rsa,
    ed25519,
}

export const SSH_KEY_NAMES = {
    [SshEncryptionAlgorithm.rsa]: 'artemis_playwright_rsa',
    [SshEncryptionAlgorithm.ed25519]: 'artemis_playwright_ed25519',
};

async function globalSetup() {
    console.log('Running global setup...');

    const sshDir = path.join(os.homedir(), '.ssh');

    for (const keyName of Object.values(SSH_KEY_NAMES)) {
        const sourceKey = path.join(process.cwd(), 'ssh-keys', keyName);
        const sourceKeyPub = path.join(process.cwd(), 'ssh-keys', `${keyName}.pub`);
        const destKey = path.join(sshDir, keyName);
        const destKeyPub = path.join(sshDir, `${keyName}.pub`);

        if (!fs.existsSync(destKey)) {
            fs.copyFileSync(sourceKey, destKey);
            fs.chmodSync(destKey, 0o600);
            console.log(`Private SSH key ${keyName} copied.`);
        } else {
            console.log(`Private SSH key ${keyName} already exists, skipping.`);
        }

        if (!fs.existsSync(destKeyPub)) {
            fs.copyFileSync(sourceKeyPub, destKeyPub);
            fs.chmodSync(destKeyPub, 0o644);
            console.log(`Public SSH key ${keyName} copied.`);
        } else {
            console.log(`Public SSH key ${keyName} already exists, skipping.`);
        }
    }

    console.log('Global setup completed.');
}

export default globalSetup;
