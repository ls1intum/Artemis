import { Page } from '@playwright/test';

/**
 * A simulated outage of one save endpoint, see {@link startSaveOutage}.
 */
export interface SaveOutage {
    /**
     * Resolves once a save request has been answered with a 503. Call it after the save was triggered: the wait is bounded from the call, and
     * it rejects when no request reached the interception in time, because then the outage was never injected.
     */
    waitForFirstFailedSave(): Promise<void>;

    /** Lifts the outage. Requests that are paused at that moment are released to the real server. */
    end(): Promise<void>;
}

// A save the test has just triggered is answered at once, so a longer wait means the outage was never injected.
const FIRST_FAILED_SAVE_TIMEOUT_MS = 30_000;

/**
 * Answers every request that matches the URL pattern with a 503 until the outage is ended, like a connection that broke during an exam.
 * <p>
 * The interception is a Chrome DevTools Fetch interception instead of `page.route`: Playwright routing disables the HTTP cache of the page,
 * which makes the production client reload expensive and starves under parallel CI (see ExamSubmissionRecovery.spec.ts). The interception
 * belongs to the page and stays active across reloads, so the outage can span a reload.
 * <p>
 * Every matching request is answered, not only the first one: a paused request that nothing answers would be released to the real server
 * when the outage ends, and could save the very answer the test wants to stay unsaved.
 *
 * @param page the page whose requests are intercepted
 * @param urlPattern a DevTools URL pattern (scheme, host and path with wildcards) of the save endpoint to break
 */
export async function startSaveOutage(page: Page, urlPattern: string): Promise<SaveOutage> {
    const cdpSession = await page.context().newCDPSession(page);
    let outageActive = true;
    const firstFailedSave = new Promise<void>((resolveFirstFailedSave, rejectFirstFailedSave) => {
        cdpSession.on('Fetch.requestPaused', async ({ requestId }) => {
            try {
                await cdpSession.send('Fetch.fulfillRequest', {
                    requestId,
                    responseCode: 503,
                    responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
                    body: 'e30=',
                });
                // A promise ignores every call after the first, so later failed saves are harmless here.
                resolveFirstFailedSave();
            } catch (error) {
                // Ending the outage detaches the session; only a failure during the outage means the injection itself is broken.
                if (outageActive) {
                    rejectFirstFailedSave(error as Error);
                }
            }
        });
    });
    // A rejection nobody awaits (the test failed earlier) must not take down the worker as an unhandled rejection.
    firstFailedSave.catch(() => undefined);

    await cdpSession.send('Fetch.enable', { patterns: [{ urlPattern, requestStage: 'Request' }] });

    return {
        waitForFirstFailedSave: () => {
            let deadline: NodeJS.Timeout | undefined;
            const timedOut = new Promise<never>((_, reject) => {
                deadline = setTimeout(
                    () => reject(new Error(`No save request matching ${urlPattern} was intercepted within ${FIRST_FAILED_SAVE_TIMEOUT_MS}ms, so the outage was never injected`)),
                    FIRST_FAILED_SAVE_TIMEOUT_MS,
                );
            });
            return Promise.race([firstFailedSave, timedOut]).finally(() => clearTimeout(deadline));
        },
        end: async () => {
            outageActive = false;
            await cdpSession.send('Fetch.disable').catch(() => undefined);
            await cdpSession.detach().catch(() => undefined);
        },
    };
}
