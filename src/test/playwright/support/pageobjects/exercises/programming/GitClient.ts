import { simpleGit } from 'simple-git';
import * as fs from 'fs';
import path from 'path';

const MAX_CLONE_RETRIES = 3;
const INITIAL_RETRY_DELAY_MS = 2000;

class GitClient {
    async cloneRepo(url: string, repoName: string, sshKeyName?: string) {
        const repoPath = `./${process.env.EXERCISE_REPO_DIRECTORY}/${repoName}`;
        let gitSshCommand;

        if (sshKeyName) {
            const privateKeyPath = path.join(SSH_KEYS_PATH, sshKeyName);
            // Use /dev/null for known_hosts to avoid "host key changed" errors when Docker containers restart
            // StrictHostKeyChecking=no alone doesn't help when the key has CHANGED (vs being unknown)
            gitSshCommand = `ssh -i ${privateKeyPath} -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no`;
        }

        if (!fs.existsSync(repoPath)) {
            fs.mkdirSync(repoPath, { recursive: true });
        }

        for (let attempt = 1; attempt <= MAX_CLONE_RETRIES; attempt++) {
            try {
                const git = simpleGit();
                if (gitSshCommand) {
                    git.env({ GIT_SSH_COMMAND: gitSshCommand });
                }
                await git.clone(url, repoPath);
                break;
            } catch (error) {
                console.error(`Git clone attempt ${attempt}/${MAX_CLONE_RETRIES} failed: ${error}`);
                if (attempt === MAX_CLONE_RETRIES) {
                    throw error;
                }
                // Clean up partial clone before retrying
                if (fs.existsSync(repoPath)) {
                    fs.rmSync(repoPath, { recursive: true, force: true });
                    fs.mkdirSync(repoPath, { recursive: true });
                }
                const delay = INITIAL_RETRY_DELAY_MS * Math.pow(2, attempt - 1);
                console.log(`Retrying clone in ${delay}ms...`);
                await new Promise((resolve) => setTimeout(resolve, delay));
            }
        }

        const clonedRepo = simpleGit(repoPath);

        if (gitSshCommand) {
            clonedRepo.env({ GIT_SSH_COMMAND: gitSshCommand });
        }

        return clonedRepo;
    }
}

export const gitClient = new GitClient();

export enum SshEncryptionAlgorithm {
    rsa = 'RSA',
    ed25519 = 'ED25519',
}

export const SSH_KEY_NAMES = {
    [SshEncryptionAlgorithm.rsa]: 'artemis_playwright_rsa',
    [SshEncryptionAlgorithm.ed25519]: 'artemis_playwright_ed25519',
};

export const SSH_KEYS_PATH = path.join(process.cwd(), 'ssh-keys');
