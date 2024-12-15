import { simpleGit } from 'simple-git';
import * as fs from 'fs';

class GitClient {
    async cloneRepo(url: string, repoName: string) {
        const git = simpleGit();
        const repoPath = `./${process.env.EXERCISE_REPO_DIRECTORY}/${repoName}`;
        // Disable host key checking to avoid interactive prompt. Adds server to known_hosts.
        git.env({ GIT_SSH_COMMAND: 'ssh -o StrictHostKeyChecking=no' });

        if (!fs.existsSync(repoPath)) {
            fs.mkdirSync(repoPath, { recursive: true });
        }

        await git.clone(url, repoPath);
        return simpleGit(`./${process.env.EXERCISE_REPO_DIRECTORY}/${repoName}`);
    }
}

export const gitClient = new GitClient();
