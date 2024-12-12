import { simpleGit } from 'simple-git';
import * as fs from 'fs';

class GitClient {
    async cloneRepo(url: string, repoName: string, sshKeyName?: string) {
        const git = simpleGit();
        const repoPath = `./${process.env.EXERCISE_REPO_DIRECTORY}/${repoName}`;
        const gitSshCommand = sshKeyName ? `ssh -i ~/.ssh/${sshKeyName} -o StrictHostKeyChecking=no` : undefined;

        if (gitSshCommand) {
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
