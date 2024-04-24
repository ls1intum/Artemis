import { simpleGit } from 'simple-git';

class GitClient {
    async cloneRepo(url: string, repoName: string) {
        const git = simpleGit();
        git.outputHandler((bin, stdout, stderr) => {
            stdout.pipe(process.stdout);
            stderr.pipe(process.stderr);
        });

        try {
            console.log('Cloning the repo');
            await git.clone(url, `./test-exercise-repos/${repoName}`);
            console.log('Repository cloned successfully');
        } catch (error) {
            console.error('Error cloning repository:', error);
        }
        return simpleGit(`./test-exercise-repos/${repoName}`);
    }
}

export const gitClient = new GitClient();
