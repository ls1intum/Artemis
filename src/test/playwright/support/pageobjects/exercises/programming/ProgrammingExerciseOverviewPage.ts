import { expect, Locator, Page } from '@playwright/test';
import { StudentParticipationDTO } from 'app/exercise/shared/entities/participation/student-participation.dto';
import { UserCredentials } from '../../../users';
import { Commands } from '../../../commands';
import { CourseOverviewPage } from '../../course/CourseOverviewPage';
import { readResponseJson } from '../../../utils';
import { BUILD_RESULT_TIMEOUT, POLLING_INTERVAL } from '../../../timeouts';

export class ProgrammingExerciseOverviewPage {
    private static readonly NON_ZERO_RESULT_SCORE_PATTERN = /(?:[1-9]\d*(?:\.\d+)?|0\.\d*[1-9]\d*)%/;

    private readonly page: Page;
    private readonly courseOverview: CourseOverviewPage;

    constructor(page: Page, courseOverview: CourseOverviewPage) {
        this.page = page;
        this.courseOverview = courseOverview;
    }

    async checkResultScore(expectedResult: string) {
        const resultScore = this.page.locator('[data-testid="exercise-status"]').locator('#result-score');
        // Use > semantics: accept any non-zero score rather than an exact string match,
        // consistent with verifyResultScore. A '0%' expectation is matched literally.
        const textPattern = ProgrammingExerciseOverviewPage.buildResultScorePattern(expectedResult);
        await Commands.reloadUntilTextFound(this.page, resultScore, textPattern, POLLING_INTERVAL, BUILD_RESULT_TIMEOUT * 2);
        await expect(resultScore).toContainText(textPattern);
    }

    /**
     * Checks the result score after the build has been confirmed complete via API.
     * Repeatedly navigates to the exercise page (full page.goto, not just reload)
     * to ensure the Angular component re-initializes with fresh data each time.
     * This is more reliable than page.reload() which can preserve stale component state.
     */
    async checkResultScoreAfterBuild(courseId: number, exerciseId: number, expectedResult: string) {
        const url = `/courses/${courseId}/exercises/${exerciseId}`;
        const resultScore = this.page.locator('[data-testid="exercise-status"]').locator('#result-score');
        // Use > semantics: accept any non-zero score rather than an exact string match,
        // consistent with verifyResultScore. A '0%' expectation is matched literally.
        const textPattern = ProgrammingExerciseOverviewPage.buildResultScorePattern(expectedResult);

        // Try up to 6 full navigations over ~90s (each with 15s wait for score to appear)
        for (let attempt = 0; attempt < 6; attempt++) {
            await this.page.goto(url);
            await this.page.waitForLoadState('domcontentloaded');
            try {
                await expect(resultScore).toContainText(textPattern, { timeout: 15000 });
                return; // Success
            } catch {
                console.log(`[checkResultScoreAfterBuild] Attempt ${attempt + 1}/6: score not found, retrying with fresh navigation...`);
            }
        }

        // Final attempt with longer timeout
        await this.page.goto(url);
        await this.page.waitForLoadState('domcontentloaded');
        await expect(resultScore).toContainText(textPattern, { timeout: 30000 });
    }

    static buildResultScorePattern(expectedResult: string): string | RegExp {
        const isZeroExpected = expectedResult === '0%' || expectedResult === '0';
        return isZeroExpected ? '0%' : ProgrammingExerciseOverviewPage.NON_ZERO_RESULT_SCORE_PATTERN;
    }

    async startParticipation(courseId: number, exerciseId: number, credentials: UserCredentials): Promise<number> {
        await Commands.login(this.page, credentials, `/courses/${courseId}/exercises/${exerciseId}`);
        const startButton = this.courseOverview.getStartExerciseButton(exerciseId);
        await Commands.reloadUntilFound(this.page, startButton);
        const responsePromise = this.page.waitForResponse(
            (resp) => resp.url().includes(`/exercises/${exerciseId}/participations`) && resp.request().method() === 'POST' && resp.status() === 201,
        );
        await startButton.click();
        const response = await responsePromise;
        const participation = await readResponseJson(response);
        if (!participation?.id) {
            throw new Error(`[startParticipation] Participation response missing id for exercise ${exerciseId}. Response: ${JSON.stringify(participation)}`);
        }
        return participation.id;
    }

    async openCloneMenu(cloneMethod: GitCloneMethod, codeButton?: Locator) {
        const authenticationMethodTestId = {
            [GitCloneMethod.https]: 'authentication-method-password',
            [GitCloneMethod.httpsWithToken]: 'authentication-method-token',
            [GitCloneMethod.ssh]: 'authentication-method-ssh',
        };

        const codeButtonLocator = codeButton ?? this.getCodeButton();
        await Commands.reloadUntilFound(this.page, codeButtonLocator, 10000, 40000);

        const popover = this.getRepositoryActionsDialog();
        const menuTrigger = popover.getByTestId('authentication-method-menu-trigger');
        const cloneMethodOption = this.page.getByTestId(authenticationMethodTestId[cloneMethod]);
        const maxAttempts = 5;
        for (let attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                if (!(await popover.isVisible())) {
                    await codeButtonLocator.click();
                    await popover.waitFor({ state: 'visible', timeout: 15_000 });
                }
                if (!(await cloneMethodOption.isVisible())) {
                    await menuTrigger.click({ timeout: 10_000 });
                }
                await cloneMethodOption.waitFor({ state: 'visible', timeout: 10_000 });
                await cloneMethodOption.click({ timeout: 10_000 });
                return;
            } catch (error) {
                if (attempt === maxAttempts - 1) {
                    throw error;
                }
                await this.page.waitForTimeout(1_000);
            }
        }
    }

    async closeCloneMenu() {
        await this.page.keyboard.press('Escape');
        await expect(this.getRepositoryActionsDialog()).toBeHidden();
    }

    /**
     * Reads the clone URL from the code popover, waiting until it belongs to the selected clone method.
     * <p>
     * The popover keeps showing the previously selected URL until the switch has propagated through its async
     * re-render, so a URL read too early is the wrong one. An ssh test then cloned the https URL, which carries no
     * credentials, and the server rejected it as invalid credentials - a failure that reads like a broken token but
     * is only a stale read.
     *
     * @param cloneMethod the clone method whose URL is expected to be on screen.
     */
    async getCloneUrl(cloneMethod: GitCloneMethod = GitCloneMethod.https) {
        const cloneUrl = this.getRepositoryActionsDialog().getByTestId('clone-url');
        await expect.poll(async () => this.cloneUrlBelongsTo(cloneMethod, (await cloneUrl.innerText()).trim()), { timeout: 15000 }).toBeTruthy();
        return (await cloneUrl.innerText()).trim();
    }

    /**
     * Whether the displayed clone URL is the one of the given method. The two HTTPS variants share a scheme and differ
     * only in their credentials, so the token has to be part of the check: waiting for the scheme alone accepts the
     * tokenized URL left over from a previous selection and clones with the wrong credential mode.
     */
    private cloneUrlBelongsTo(cloneMethod: GitCloneMethod, url: string): boolean {
        if (cloneMethod === GitCloneMethod.ssh) {
            return url.startsWith('ssh://');
        }
        if (!url.startsWith('http')) {
            return false;
        }
        // `//login:token@host` - the plain HTTPS URL carries at most the login, never a password.
        const carriesToken = /\/\/[^/@]+:[^/@]+@/.test(url);
        return cloneMethod === GitCloneMethod.httpsWithToken ? carriesToken : !carriesToken;
    }

    async copyCloneUrl(cloneMethod: GitCloneMethod = GitCloneMethod.https, codeButton?: Locator) {
        if (cloneMethod !== GitCloneMethod.httpsWithToken) {
            return await this.getCloneUrl(cloneMethod);
        }
        const codeButtonLocator = codeButton ?? this.getCodeButton();
        await this.page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
        const button = this.getCloneUrlButton();
        if (!(await button.isVisible())) {
            await codeButtonLocator.click();
        }
        try {
            await expect(button).toBeEnabled({ timeout: 30000 });
        } catch {
            await this.closeCloneMenu();
            await codeButtonLocator.click();
            await this.getRepositoryActionsDialog().waitFor({ state: 'visible' });
            await expect(button).toBeEnabled({ timeout: 15000 });
        }
        await button.click();
        return await this.page.evaluate(async () => {
            return await navigator.clipboard.readText();
        });
    }

    getCodeButton() {
        return this.page.getByTestId('code-button');
    }

    getExerciseDetails() {
        return this.page.locator('#course-exercise-details');
    }

    getCloneUrlButton() {
        return this.getRepositoryActionsDialog().getByTestId('copy-repository-url');
    }

    private getRepositoryActionsDialog() {
        return this.page.getByRole('dialog', { name: /repository actions/i });
    }

    /**
     * Verifies the build result score from the participation data returned by API.
     * For "successful" submissions (expectedResult contains a non-zero %), verifies
     * that the score is > 0. For "build error" submissions (expectedResult is "0%"),
     * verifies score is 0. Exact percentages can vary between CI environments due to
     * sanitizer test behavior (e.g., TestOutputLSan fails on ARM64 Docker).
     */
    static verifyResultScore(participation: StudentParticipationDTO, expectedResult: string) {
        const submissions = participation.submissions ?? [];
        const allResults = submissions.flatMap((submission) => submission.results ?? []).sort((a, b) => b.id - a.id);
        const latestResult = allResults[0];
        if (!latestResult) {
            throw new Error(`No result found in participation ${participation.id}`);
        }
        const score = latestResult.score;
        if (score === undefined || score === null) {
            throw new Error(`Result score is ${score} for participation ${participation.id}`);
        }
        const expectedZero = expectedResult === '0%' || expectedResult === '0';
        if (expectedZero && score !== 0) {
            throw new Error(`Expected build failure (0%) but got score ${score}% for participation ${participation.id}`);
        }
        if (!expectedZero && score === 0) {
            throw new Error(`Expected non-zero score but got 0% for participation ${participation.id}. Build may have failed.`);
        }
        console.log(`[verifyResultScore] Score verified: ${score}% (expected pattern: "${expectedResult}") for participation ${participation.id}`);
    }
}

export enum GitCloneMethod {
    https = 'https',
    httpsWithToken = 'https with token',
    ssh = 'ssh',
}
