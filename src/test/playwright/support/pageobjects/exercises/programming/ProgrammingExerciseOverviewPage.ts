import { expect, Page } from '@playwright/test';
import { UserCredentials } from '../../../users';
import { Commands } from '../../../commands';
import { CourseOverviewPage } from '../../course/CourseOverviewPage';
import { BUILD_RESULT_TIMEOUT, POLLING_INTERVAL } from '../../../timeouts';

export class ProgrammingExerciseOverviewPage {
    private readonly page: Page;
    private readonly courseOverview: CourseOverviewPage;

    constructor(page: Page, courseOverview: CourseOverviewPage) {
        this.page = page;
        this.courseOverview = courseOverview;
    }

    async checkResultScore(expectedResult: string) {
        const resultScore = this.page.locator('#exercise-headers-information').locator('#result-score');
        await Commands.reloadUntilTextFound(this.page, resultScore, expectedResult, POLLING_INTERVAL, BUILD_RESULT_TIMEOUT * 2);
        await expect(resultScore).toContainText(expectedResult);
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
        return (await this.page.locator('.clone-url').innerText()).trim();
    }

    async copyCloneUrl(cloneMethod: GitCloneMethod = GitCloneMethod.https) {
        if (cloneMethod !== GitCloneMethod.httpsWithToken) {
            return await this.getCloneUrl();
        }
        await this.page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
        const button = this.getCloneUrlButton();
        // The copy button lives inside the Code popover (autoClose='outside').
        // Under load, the ngbDropdown closing after auth method selection can race
        // with the popover's outside-click detection, causing the popover to close.
        // If that happens, re-open it — the auth method is persisted in localStorage.
        if (!(await button.isVisible())) {
            await this.getCodeButton().click();
        }
        // Wait for the button to be enabled — starts disabled until VCS access token loads.
        // The token is fetched via an async HTTP call triggered by an effect() in code-button.component.ts.
        // Under parallel test load, this can take longer than 10s, so use a generous timeout.
        try {
            await expect(button).toBeEnabled({ timeout: 30000 });
        } catch {
            // For SSH cloning, the copyEnabled signal depends on doesUserHaveSSHkeys() which
            // is set asynchronously in ngOnInit(). If the Code button was opened before this
            // check completed, copyEnabled was set to false and never re-evaluated.
            // Re-opening the popover triggers onClick() → useSshUrl() which re-reads the
            // now-updated doesUserHaveSSHkeys signal.
            await this.getCodeButton().click();
            await this.page.waitForTimeout(500);
            await this.getCodeButton().click();
            await this.page.locator('.popover-body').waitFor({ state: 'visible' });
            await expect(button).toBeEnabled({ timeout: 15000 });
        }
        await button.click();
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
