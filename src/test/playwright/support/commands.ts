import { UserCredentials } from './users';
import { BASE_API } from './constants';
import { Locator, Page, expect } from '@playwright/test';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseAPIRequests } from './requests/ExerciseAPIRequests';

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
            const response = await page.request.post(`${BASE_API}/public/authenticate`, {
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
        await page.request.post(`${BASE_API}/public/logout`);
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
    static waitForExerciseBuildToFinish = async (page: Page, exerciseAPIRequests: ExerciseAPIRequests, exerciseId: number, interval: number = 2000, timeout: number = 60000) => {
        let exerciseParticipation: StudentParticipation;
        const startTime = Date.now();

        const numberOfBuildResults = (await exerciseAPIRequests.getExerciseParticipation(exerciseId)).results?.length;
        console.log('Waiting for build of an exercise to finish...');

        while (Date.now() - startTime < timeout) {
            exerciseParticipation = await exerciseAPIRequests.getExerciseParticipation(exerciseId);

            if (exerciseParticipation.results && exerciseParticipation.results.length > (numberOfBuildResults ?? 0)) {
                return exerciseParticipation;
            }

            await new Promise((resolve) => setTimeout(resolve, interval));
        }

        throw new Error('Timed out while waiting for build to finish.');
    };
}
