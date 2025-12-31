import { simpleGit } from 'simple-git';
import * as fs from 'fs';
import path from 'path';

class GitClient {
    async cloneRepo(url: string, repoName: string, sshKeyName?: string) {
        const git = simpleGit();
        const repoPath = `./${process.env.EXERCISE_REPO_DIRECTORY}/${repoName}`;
        let gitSshCommand;

        if (sshKeyName) {
            const privateKeyPath = path.join(SSH_KEYS_PATH, sshKeyName);
            // Use /dev/null for known_hosts to avoid "host key changed" errors when Docker containers restart
            // StrictHostKeyChecking=no alone doesn't help when the key has CHANGED (vs being unknown)
            gitSshCommand = `ssh -i ${privateKeyPath} -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no`;
            git.env({ GIT_SSH_COMMAND: gitSshCommand });
        }

        if (!fs.existsSync(repoPath)) {
            fs.mkdirSync(repoPath, { recursive: true });
        }

        await git.clone(url, repoPath);
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
