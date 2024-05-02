import { simpleGit } from 'simple-git';
import * as fs from 'fs';

class GitClient {
    async cloneRepo(url: string, repoName: string) {
        const git = simpleGit();
        const repoPath = `./${process.env.EXERCISE_REPO_DIRECTORY}/${repoName}`;

        if (!fs.existsSync(repoPath)) {
            fs.mkdirSync(repoPath, { recursive: true });
        }

        await git.clone(url, repoPath);
        return simpleGit(`./${process.env.EXERCISE_REPO_DIRECTORY}/${repoName}`);
    }
}

export const gitClient = new GitClient();
