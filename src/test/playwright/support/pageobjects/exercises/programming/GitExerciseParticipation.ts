import { BrowserContext } from '@playwright/test';
import { GitCloneMethod, ProgrammingExerciseOverviewPage } from './ProgrammingExerciseOverviewPage';
import { UserCredentials } from '../../../users';
import { gitClient, SSH_KEY_NAMES, SSH_KEYS_PATH, SshEncryptionAlgorithm } from './GitClient';
import fs from 'fs/promises';
import path from 'path';
import { BASE_API } from '../../../constants';
import { SimpleGit } from 'simple-git';
import { ProgrammingExerciseSubmission } from './OnlineEditorPage';
import { Fixtures } from '../../../../fixtures/fixtures';
import { createFileWithContent } from '../../../utils';

export class GitExerciseParticipation {
    static async makeSubmission(
        programmingExerciseOverview: ProgrammingExerciseOverviewPage,
        student: UserCredentials,
        submission: any,
        commitMessage: string,
        cloneMethod: GitCloneMethod = GitCloneMethod.https,
        sshAlgorithm: SshEncryptionAlgorithm = SshEncryptionAlgorithm.ed25519,
    ) {
        await programmingExerciseOverview.openCloneMenu(cloneMethod);
        let repoUrl = await programmingExerciseOverview.copyCloneUrl();
        await programmingExerciseOverview.getCodeButton().click();

        if (cloneMethod == GitCloneMethod.https) {
            repoUrl = repoUrl.replace(student.username!, `${student.username!}:${student.password!}`);
        }
        console.log(`Cloning repository from ${repoUrl}`);
        const urlParts = repoUrl.split('/');
        const repoName = urlParts[urlParts.length - 1];
        let exerciseRepo;
        try {
            if (cloneMethod == GitCloneMethod.ssh) {
                exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName, SSH_KEY_NAMES[sshAlgorithm]);
            } else {
                exerciseRepo = await gitClient.cloneRepo(repoUrl, repoName);
            }
            console.log(`Cloned repository successfully. Pushing files...`);
            await GitExerciseParticipation.pushGitSubmissionFiles(exerciseRepo, repoName, student, submission, commitMessage);
        } finally {
            // Remove the local directory even if cloning or pushing fails at some stage
            await fs.rm(`./test-exercise-repos/${repoName}`, { recursive: true, force: true });
        }
    }

    static async setupSSHCredentials(context: BrowserContext, sshAlgorithm: SshEncryptionAlgorithm) {
        console.log(`Setting up SSH credentials with key ${SSH_KEY_NAMES[sshAlgorithm]}`);
        const page = await context.newPage();
        const sshKeyPath = path.join(SSH_KEYS_PATH, `${SSH_KEY_NAMES[sshAlgorithm]}.pub`);
        const sshKey = await fs.readFile(sshKeyPath, 'utf8');
        await page.goto('user-settings/ssh');
        await page.getByTestId('addNewSshKeyButton').click();
        await page.getByTestId('sshKeyField').fill(sshKey!);
        const responsePromise = page.waitForResponse(`${BASE_API}/ssh-settings/public-key`);
        await page.getByTestId('saveSshKeyButton').click();
        await responsePromise;
        await page.close();
    }

    /**
     * Helper function to make a submission to a git repository.
     * @param exerciseRepo - The git repository to which the submission should be made.
     * @param exerciseRepoName - The name of the git repository.
     * @param user - The user who is making the submission.
     * @param submission - The programming exercise submission to be made.
     * @param commitMessage - The commit message for the submission.
     * @param deleteFiles - Whether to delete files from the repository directory before making the submission.
     */
    private static async pushGitSubmissionFiles(
        exerciseRepo: SimpleGit,
        exerciseRepoName: string,
        user: UserCredentials,
        submission: ProgrammingExerciseSubmission,
        commitMessage: string,
        deleteFiles: boolean = true,
    ) {
        let sourcePath = '';
        if (submission.packageName) {
            const packagePath = submission.packageName.replace(/\./g, '/');
            sourcePath = `src/${packagePath}/`;
        }

        if (deleteFiles) {
            for (const fileName of submission.deleteFiles) {
                const filePath = `./${sourcePath}${fileName}`;
                await exerciseRepo.rm(filePath);
            }
        }

        for (const file of submission.files) {
            const filePath = `./${sourcePath}${file.name}`;
            const sourceCode = await Fixtures.get(file.path);
            await createFileWithContent(`./test-exercise-repos/${exerciseRepoName}/${filePath}`, sourceCode!);
            await exerciseRepo.add(`./${filePath}`);
        }

        await exerciseRepo.addConfig('user.email', `${user.username}@example.com`);
        await exerciseRepo.addConfig('user.name', user.username);
        await exerciseRepo.commit(commitMessage);
        await exerciseRepo.push();
    }
}
