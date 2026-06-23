import { simpleGit } from 'simple-git';
import * as fs from 'fs';
import path from 'path';

// Disable any configured git credential helper for every clone/push. Without this, a system-level
// helper (notably macOS `osxkeychain`, configured in /opt/homebrew/etc/gitconfig) caches credentials
// keyed by host. Because every e2e repository is served from the SAME host (localhost on the LocalVC
// port) but by DIFFERENT users, the helper hands a previously cached user's credentials to a clone
// for another user, so git sends the wrong password and LocalVC rejects it ("Authentication failed" /
// server-side "Invalid password for user ..."). This was the real cause of the intermittent git-auth
// failures — it is purely client-side (the server code is identical to develop) and gets worse as the
// keychain accumulates conflicting localhost entries across runs. Forcing an empty credential.helper
// makes git use ONLY the credentials embedded in the clone URL, which is deterministic and correct.
const NO_CREDENTIAL_HELPER = ['credential.helper='];

// Frequent, jittered retries remain for genuinely transient failures the credential-helper fix does
// NOT cover: an SSH key registered via the API but not yet visible to the serving node, momentary
// network blips, and a rare ~20s window in which LocalVC rejects an otherwise-valid credential while
// the serving node is saturated by the C builds it also hosts. The window can outlast a few retries,
// so the budget spans ~45s (≈12 attempts) to ride it out within a single test attempt. Jitter avoids
// parallel workers retrying in lockstep and re-spiking the node.
const MAX_CLONE_RETRIES = 12;
const RETRY_DELAY_BASE_MS = 3000;
const RETRY_DELAY_JITTER_MS = 1500;

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
                const git = simpleGit({ unsafe: { allowUnsafeSshCommand: true, allowUnsafeCredentialHelper: true }, config: NO_CREDENTIAL_HELPER });
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
                const delay = RETRY_DELAY_BASE_MS + Math.floor(Math.random() * RETRY_DELAY_JITTER_MS);
                console.log(`Retrying clone in ${delay}ms...`);
                await new Promise((resolve) => setTimeout(resolve, delay));
            }
        }

        const clonedRepo = simpleGit(repoPath, { unsafe: { allowUnsafeSshCommand: true, allowUnsafeCredentialHelper: true }, config: NO_CREDENTIAL_HELPER });

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
