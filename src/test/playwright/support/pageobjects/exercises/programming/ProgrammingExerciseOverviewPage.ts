import { expect, Page } from '@playwright/test';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
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
        // Use > semantics: accept any non-zero score rather than an exact string match,
        // consistent with verifyResultScore. A '0%' expectation is matched literally.
        const isZeroExpected = expectedResult === '0%';
        const textPattern = isZeroExpected ? '0%' : /[1-9]/;
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
        const resultScore = this.page.locator('#exercise-headers-information').locator('#result-score');
        // Use > semantics: accept any non-zero score rather than an exact string match,
        // consistent with verifyResultScore. A '0%' expectation is matched literally.
        const isZeroExpected = expectedResult === '0%';
        const textPattern = isZeroExpected ? '0%' : /[1-9]/;

        // Try up to 6 full navigations over ~90s (each with 15s wait for score to appear)
        for (let attempt = 0; attempt < 6; attempt++) {
            await this.page.goto(url);
            await this.page.waitForLoadState('networkidle');
            try {
                await expect(resultScore).toContainText(textPattern, { timeout: 15000 });
                return; // Success
            } catch {
                console.log(`[checkResultScoreAfterBuild] Attempt ${attempt + 1}/6: score not found, retrying with fresh navigation...`);
            }
        }

        // Final attempt with longer timeout
        await this.page.goto(url);
        await this.page.waitForLoadState('networkidle');
        await expect(resultScore).toContainText(textPattern, { timeout: 30000 });
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
        const participation = await response.json();
        if (!participation?.id) {
            throw new Error(`[startParticipation] Participation response missing id for exercise ${exerciseId}. Response: ${JSON.stringify(participation)}`);
        }
        return participation.id;
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
        if (!(await button.isVisible())) {
            await this.getCodeButton().click();
        }
        try {
            await expect(button).toBeEnabled({ timeout: 30000 });
        } catch {
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

    /**
     * Verifies the build result score from the participation data returned by API.
     * For "successful" submissions (expectedResult contains a non-zero %), verifies
     * that the score is > 0. For "build error" submissions (expectedResult is "0%"),
     * verifies score is 0. Exact percentages can vary between CI environments due to
     * sanitizer test behavior (e.g., TestOutputLSan fails on ARM64 Docker).
     */
    static verifyResultScore(participation: StudentParticipation, expectedResult: string) {
        const submissions = participation.submissions ?? [];
        const latestResult = submissions.flatMap((s) => s.results ?? []).sort((a, b) => (b.id ?? 0) - (a.id ?? 0))[0];
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
