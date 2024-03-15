import { simpleGit } from 'simple-git';

class GitClient {
    async cloneRepo(url: string, repoName: string) {
        const git = simpleGit();
        console.log('Cloning the repo');
        await git.clone(url, `./test-exercise-repos/${repoName}`);
        return simpleGit(`./test-exercise-repos/${repoName}`);
    }
}

export const gitClient = new GitClient();
