import { type APIRequestContext, type Page, expect } from '@playwright/test';
import { Commands } from './commands';
import { UserCredentials } from './users';

/**
 * Enables Iris for a course through the settings API. The request must be signed in as an instructor or admin.
 *
 * Asserts the response, and retries it, because the server saves the settings with a find-then-insert: when specs
 * enable the same course for the first time at the same moment, the loser of the race trips the unique `course_id`
 * constraint and gets a 500. Its retry finds the row the winner inserted and updates it. Ignoring that response
 * left the specs relying on the winner's write without ever knowing whether their own call went through.
 */
export async function enableIrisForCourse(request: APIRequestContext, courseId: number): Promise<void> {
    await setIrisEnabledForCourse(request, courseId, true);
}

/**
 * Disables Iris for a course through the settings API, with the same signed-in user and retry as {@link enableIrisForCourse}.
 *
 * A course without a settings row counts as enabled, so a spec that needs a course without Iris must say so explicitly on a server that
 * runs the Iris module (as the nightly Iris run does). The settings endpoint only exists with that module, so callers check
 * `activeModuleFeatures` of `management/info` first.
 */
export async function disableIrisForCourse(request: APIRequestContext, courseId: number): Promise<void> {
    await setIrisEnabledForCourse(request, courseId, false);
}

async function setIrisEnabledForCourse(request: APIRequestContext, courseId: number, enabled: boolean): Promise<void> {
    await expect(async () => {
        const response = await request.put(`api/iris/courses/${courseId}/iris-settings`, {
            data: { enabled, variant: 'default' },
            failOnStatusCode: false,
        });
        expect(response.ok(), `Iris must be ${enabled ? 'enabled' : 'disabled'} for course ${courseId}, but the settings request answered ${response.status()}`).toBeTruthy();
    }).toPass({ timeout: 15_000 });
}

/**
 * Signs in and opens `url` with the Iris onboarding tour already marked as completed for that user.
 *
 * The client decides whether to offer the tour only after the chat has loaded and an extra request for the user's
 * message count has answered, so on a loaded runner it opens an unpredictable time after the chat appears. While it
 * is open, its full-page backdrop swallows every click on the chat. Waiting a fixed time for it to show up and then
 * closing it (what the specs did before) is therefore a race; marking it as completed removes it.
 *
 * The client remembers completion per user in localStorage under `iris-onboarding-completed-<userId>` (see
 * IrisOnboardingService). The user id is not a constant of the e2e data, so it is read from the account the first
 * sign-in yields, and the key is written before any page script runs, on every navigation the page makes.
 */
export async function loginWithoutIrisTour(page: Page, credentials: UserCredentials, url: string): Promise<void> {
    await Commands.login(page, credentials);
    const accountResponse = await page.request.get('api/core/public/account');
    expect(accountResponse.ok(), 'The account of the signed-in user is needed to key the onboarding flag').toBeTruthy();
    const { id: userId } = await accountResponse.json();
    await page.addInitScript((id) => localStorage.setItem(`iris-onboarding-completed-${id}`, 'true'), userId);

    await Commands.login(page, credentials, url);
}
