import { expect, Page } from '@playwright/test';
import { UserCredentials } from '../../../users';
import { Commands } from '../../../commands';
import { CourseOverviewPage } from '../../course/CourseOverviewPage';

export class ProgrammingExerciseOverviewPage {
    private readonly page: Page;
    private readonly courseOverview: CourseOverviewPage;

    constructor(page: Page, courseOverview: CourseOverviewPage) {
        this.page = page;
        this.courseOverview = courseOverview;
    }

    async checkResultScore(expectedResult: string) {
        const resultScore = this.page.locator('#exercise-headers-information').locator('#result-score');
        await Commands.reloadUntilFound(this.page, resultScore, 5000, 120000);
        await expect(resultScore.getByText(expectedResult)).toBeVisible({ timeout: 30000 });
    }

    async startParticipation(courseId: number, exerciseId: number, credentials: UserCredentials) {
        await Commands.login(this.page, credentials, `/courses/${courseId}/exercises/${exerciseId}`);
        const startButton = this.courseOverview.getStartExerciseButton(exerciseId);
        await Commands.reloadUntilFound(this.page, startButton);
        await startButton.click();
    }

    async openCodeEditor(exerciseId: number) {
        await Commands.reloadUntilFound(this.page, this.page.locator(`#open-exercise-${exerciseId}`));
        await this.courseOverview.openRunningProgrammingExercise(exerciseId);
    }

    async openCloneMenu(cloneMethod: GitCloneMethod) {
        const gitCloneMethodSelector = {
            [GitCloneMethod.https]: '#useHTTPSButton',
            [GitCloneMethod.httpsWithToken]: '#useHTTPSWithTokenButton',
            [GitCloneMethod.ssh]: '#useSSHButton',
        };

        const codeButtonLocator = this.getCodeButton();
        await Commands.reloadUntilFound(this.page, codeButtonLocator, 10000, 40000);
        await codeButtonLocator.click();
        await this.page.locator('.popover-body').waitFor({ state: 'visible' });
        await this.page.locator('.https-or-ssh-button').click();
        await this.page.locator(gitCloneMethodSelector[cloneMethod]).click();
    }

    async getCloneUrl() {
        return await this.page.locator('.clone-url').innerText();
    }

    async copyCloneUrl() {
        await this.page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
        await this.getCloneUrlButton().click();
        return await this.page.evaluate(async () => {
            return await navigator.clipboard.readText();
        });
    }

    getCodeButton() {
        return this.page.locator('.code-button');
    }

    getExerciseDetails() {
        return this.page.locator('#course-exercise-details');
    }

    getCloneUrlButton() {
        return this.page.getByTestId('copyRepoUrlButton');
    }
}

export enum GitCloneMethod {
    https = 'https',
    httpsWithToken = 'https with token',
    ssh = 'ssh',
}
