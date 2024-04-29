import { simpleGit } from 'simple-git';
import * as fs from 'fs';

class GitClient {
    async cloneRepo(url: string, repoName: string) {
        const git = simpleGit();
        const repoPath = `./test-exercise-repos/${repoName}`;

        if (!fs.existsSync(repoPath)) {
            fs.mkdirSync(repoPath, { recursive: true });
        }

        await git.clone(url, repoPath);
        return simpleGit(`./test-exercise-repos/${repoName}`);
    }
}

export const gitClient = new GitClient();
