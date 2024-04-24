import { simpleGit } from 'simple-git';
import * as fs from 'fs';

class GitClient {
    async cloneRepo(url: string, repoName: string) {
        const git = simpleGit();
        git.outputHandler((bin, stdout, stderr) => {
            stdout.pipe(process.stdout);
            stderr.pipe(process.stderr);
        });

        const repoPath = `./test-exercise-repos/${repoName}`;

        if (!fs.existsSync(repoPath)) {
            fs.mkdirSync(repoPath, { recursive: true });
        }

        try {
            console.log('Cloning the repo');
            await git.clone(url, repoPath);
            console.log('Repository cloned successfully');
        } catch (error) {
            console.error('Error cloning repository:', error);
        }
        return simpleGit(`./test-exercise-repos/${repoName}`);
    }
}

export const gitClient = new GitClient();
