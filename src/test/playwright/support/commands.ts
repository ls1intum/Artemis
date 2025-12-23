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
     * Logs in using API.
     * @param page - Playwright page object.
     * @param credentials - UserCredentials object containing username and password.
     * @param url - Optional URL to navigate to after successful login.
     */
    static login = async (page: Page, credentials: UserCredentials, url?: string): Promise<void> => {
        await Commands.logout(page);
        const { username, password } = credentials;

        const jwtCookie = await page
            .context()
            .cookies()
            .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
        if (!jwtCookie) {
            const response = await page.request.post(`api/core/public/authenticate`, {
                data: {
                    username,
                    password,
                    rememberMe: true,
                },
                failOnStatusCode: false,
            });

            expect(response.status()).toBe(200);

            const newJwtCookie = await page
                .context()
                .cookies()
                .then((cookies) => cookies.find((cookie) => cookie.name === 'jwt'));
            expect(newJwtCookie).not.toBeNull();
        }

        if (url) {
            await page.goto(url);
        }
    };

    static logout = async (page: Page): Promise<void> => {
        await page.request.post(`api/core/public/logout`);
    };

    static reloadUntilFound = async (page: Page, locator: Locator, interval = 2000, timeout = 20000) => {
        const startTime = Date.now();

        while (Date.now() - startTime < timeout) {
            try {
                await locator.waitFor({ state: 'visible', timeout: interval });
                return;
            } catch (error) {
                await page.reload();
            }
        }

        throw new Error(`Timed out finding an element matching the "${locator}" locator`);
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

        const numberOfBuildResults = exerciseParticipation.submissions
            ? exerciseParticipation.submissions.reduce((sum, submission) => sum + (submission.results?.length ?? 0), 0)
            : 0;

        console.log('Waiting for build of an exercise to finish...');
        while (Date.now() - startTime < timeout) {
            exerciseParticipation = await getParticipation();

            const currentBuildResultsCount = exerciseParticipation?.submissions
                ? exerciseParticipation.submissions.reduce((sum, submission) => sum + (submission.results?.length ?? 0), 0)
                : 0;

            if (currentBuildResultsCount > numberOfBuildResults) {
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
        const startTime = Date.now();

        const countResults = (participation: StudentParticipation): number => {
            return participation.submissions ? participation.submissions.reduce((sum, submission) => sum + (submission.results?.length ?? 0), 0) : 0;
        };

        let initialResultCount = 0;

        // Get initial result count
        try {
            const participation = await exerciseAPIRequests.getParticipationWithLatestResult(participationId);
            initialResultCount = countResults(participation);
        } catch {
            // Participation might not have results yet
        }

        console.log('Waiting for build of participation to finish...');
        while (Date.now() - startTime < timeout) {
            try {
                const participation = await exerciseAPIRequests.getParticipationWithLatestResult(participationId);
                const currentResultCount = countResults(participation);

                if (currentResultCount > initialResultCount) {
                    return participation;
                }
            } catch {
                // Continue polling if request fails
            }

            await new Promise((resolve) => setTimeout(resolve, interval));
        }

        throw new Error(`Timed out waiting for build to finish for participation ${participationId}`);
    };

    static toggleSidebar = async (page: Page) => {
        await page.keyboard.press('Control+m');
    };
}
