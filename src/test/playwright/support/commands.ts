import { UserCredentials } from './users';
import { Locator, Page, expect } from '@playwright/test';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseAPIRequests } from './requests/ExerciseAPIRequests';
import { BUILD_FINISH_TIMEOUT, POLLING_INTERVAL } from './timeouts';

/**
 * A class that encapsulates static helper command methods.
 */
export class Commands {
    /**
     * Logs in via API authentication.
     * @param page - Playwright page object.
     * @param credentials - UserCredentials object containing username and password.
     * @param url - Optional URL to navigate to after successful login.
     */
    static login = async (page: Page, credentials: UserCredentials, url?: string): Promise<void> => {
        await Commands.logout(page);
        await page.context().clearCookies();
        const { username, password } = credentials;
        const response = await page.request.post(`api/core/public/authenticate`, {
            data: {
                username,
                password,
                rememberMe: true,
            },
            failOnStatusCode: false,
        });

        expect(response.status()).toBe(200);

        await expect
            .poll(
                async () =>
                    page
                        .context()
                        .cookies()
                        .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt')?.value),
                { timeout: 10000 },
            )
            .toBeTruthy();

        if (url) {
            await page.goto(url);
            await page.waitForLoadState('networkidle');
        }
    };

    static logout = async (page: Page): Promise<void> => {
        await page.request.post('api/core/public/logout');
    };

    static reloadUntilFound = async (page: Page, locator: Locator, interval = 10000, timeout = 60000) => {
        const startTime = Date.now();

        while (Date.now() - startTime < timeout) {
            try {
                await locator.waitFor({ state: 'visible', timeout: interval });
                return;
            } catch {
                // waitFor can fail even when the element is visible (Playwright
                // timing issue with cookie propagation from page.request). Check
                // isVisible() as a fallback before reloading.
                if (await locator.isVisible()) {
                    return;
                }
                if (page.isClosed()) {
                    throw new Error(`Page was closed while waiting for element matching "${locator}"`);
                }
                try {
                    await page.reload();
                } catch (reloadError) {
                    throw new Error(`Failed to reload page while waiting for element: ${reloadError}`);
                }
            }
        }

        throw new Error(`Timed out finding an element matching the "${locator}" locator (URL: ${page.url()})`);
    };

    static reloadUntilTextFound = async (page: Page, locator: Locator, expectedText: string, interval = 5000, timeout = 60000) => {
        const startTime = Date.now();
        let lastSeenText: string | null = null;

        while (Date.now() - startTime < timeout) {
            try {
                await locator.waitFor({ state: 'visible', timeout: interval });
                const text = await locator.textContent();
                lastSeenText = text;
                if (text?.includes(expectedText)) {
                    return;
                }
            } catch {
                // Ignore and retry with a page reload below.
            }

            if (page.isClosed()) {
                throw new Error(`Page was closed while waiting for text "${expectedText}" in locator "${locator}"`);
            }

            try {
                await page.reload();
            } catch (reloadError) {
                throw new Error(`Failed to reload page while waiting for text "${expectedText}": ${reloadError}`);
            }
        }

        throw new Error(`Timed out waiting for text "${expectedText}" in locator "${locator}" (URL: ${page.url()}). Last seen text: "${lastSeenText}"`);
    };

    /**
     * Waits for the build of an exercise to finish.
     * Throws an error if the build does not finish within the timeout.
     * @param page - Playwright page object.
     * @param exerciseAPIRequests - ExerciseAPIRequests object.
     * @param exerciseId - ID of the exercise to wait for.
     * @param interval - Interval in milliseconds between checks for the build to finish.
     * @param timeout - Timeout in milliseconds to wait for the build to finish.
     */
    static waitForExerciseBuildToFinish = async (
        page: Page,
        exerciseAPIRequests: ExerciseAPIRequests,
        exerciseId: number,
        interval: number = POLLING_INTERVAL,
        timeout: number = BUILD_FINISH_TIMEOUT,
        minResults?: number,
    ) => {
        let exerciseParticipation: StudentParticipation | undefined;
        const startTime = Date.now();

        const getParticipation = async (): Promise<StudentParticipation | undefined> => {
            try {
                return await exerciseAPIRequests.getProgrammingExerciseParticipation(exerciseId);
            } catch {
                return undefined;
            }
        };

        // Wait for participation to be available
        while (Date.now() - startTime < timeout) {
            exerciseParticipation = await getParticipation();
            if (exerciseParticipation) {
                break;
            }
            await new Promise((resolve) => setTimeout(resolve, interval));
        }

        if (!exerciseParticipation) {
            throw new Error(`Timed out waiting for participation for exercise ${exerciseId}`);
        }

        const countResults = (participation: StudentParticipation | undefined): number => {
            return participation?.submissions ? participation.submissions.reduce((sum, submission) => sum + (submission.results?.length ?? 0), 0) : 0;
        };

        const numberOfBuildResults = countResults(exerciseParticipation);
        // If minResults is specified, wait until total results reach that count.
        // Otherwise, wait for the result count to increase by at least 1.
        const targetCount = minResults ?? numberOfBuildResults + 1;

        console.log(`Waiting for build results to reach ${targetCount} (currently ${numberOfBuildResults})...`);
        while (Date.now() - startTime < timeout) {
            exerciseParticipation = await getParticipation();
            const currentBuildResultsCount = countResults(exerciseParticipation);

            if (currentBuildResultsCount >= targetCount) {
                return exerciseParticipation;
            }

            await new Promise((resolve) => setTimeout(resolve, interval));
        }

        throw new Error('Timed out while waiting for build to finish.');
    };

    /**
     * Waits for the build of a specific participation to finish.
     * This method uses a student-accessible endpoint (by participation ID).
     * Use this when logged in as a student who owns the participation.
     *
     * @param exerciseAPIRequests - ExerciseAPIRequests object.
     * @param participationId - ID of the participation to wait for.
     * @param interval - Interval in milliseconds between checks.
     * @param timeout - Timeout in milliseconds to wait for the build to finish.
     */
    static waitForParticipationBuildToFinish = async (
        exerciseAPIRequests: ExerciseAPIRequests,
        participationId: number,
        interval: number = POLLING_INTERVAL,
        timeout: number = BUILD_FINISH_TIMEOUT,
    ) => {
        if (participationId == null || isNaN(participationId)) {
            throw new Error(`[waitForParticipationBuildToFinish] Invalid participationId: ${participationId}. Cannot poll for build result.`);
        }
        const startTime = Date.now();

        const countResults = (participation: StudentParticipation): number => {
            return participation.submissions ? participation.submissions.reduce((sum, submission) => sum + (submission.results?.length ?? 0), 0) : 0;
        };

        let initialResultCount = 0;

        // Get initial result count
        try {
            const participation = await exerciseAPIRequests.getParticipationWithLatestResult(participationId);
            initialResultCount = countResults(participation);
            console.log(`[waitForParticipationBuildToFinish] Initial result count for participation ${participationId}: ${initialResultCount}`);
        } catch (e) {
            console.log(`[waitForParticipationBuildToFinish] Could not get initial results for participation ${participationId}: ${e}`);
        }

        console.log(`[waitForParticipationBuildToFinish] Waiting for build of participation ${participationId} to finish (timeout: ${timeout}ms)...`);
        let pollCount = 0;
        while (Date.now() - startTime < timeout) {
            try {
                const participation = await exerciseAPIRequests.getParticipationWithLatestResult(participationId);
                const currentResultCount = countResults(participation);
                pollCount++;

                if (pollCount % 5 === 0) {
                    console.log(`[waitForParticipationBuildToFinish] Poll #${pollCount}: current result count = ${currentResultCount}, waiting for > ${initialResultCount}`);
                }

                if (currentResultCount > initialResultCount) {
                    console.log(`[waitForParticipationBuildToFinish] Build finished! Result count increased from ${initialResultCount} to ${currentResultCount}`);
                    return participation;
                }
            } catch (e) {
                console.log(`[waitForParticipationBuildToFinish] Poll failed: ${e}`);
            }

            await new Promise((resolve) => setTimeout(resolve, interval));
        }

        throw new Error(`Timed out waiting for build to finish for participation ${participationId}. Initial results: ${initialResultCount}, timeout: ${timeout}ms`);
    };

    static toggleSidebar = async (page: Page) => {
        await page.keyboard.press('Control+m');
    };
}

