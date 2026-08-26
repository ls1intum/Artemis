import { test } from '../../support/fixtures';
import { expect } from '@playwright/test';
import { admin, studentTwo } from '../../support/users';
import { generateUUID, getExercise } from '../../support/utils';
import dayjs from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Exercise, ExerciseType } from '../../support/constants';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { SEED_COURSES } from '../../support/seedData';
import { POLLING_INTERVAL, RELOAD_RENDER_TIMEOUT } from '../../support/timeouts';

/**
 * Regression test for silent exam answer loss after a failed save.
 *
 * When a submission save fails (e.g. during a network/power outage) the answer stays unsynced and is written to local
 * storage. On reload the client must RESTORE the answer from local storage AND re-send it to the server, instead of
 * marking it as already synced and silently dropping it.
 *
 * This test ticks a multiple-choice answer, forces the save to fail, reloads the page, and verifies that the answer is
 * both restored in the UI and successfully re-sent to the server.
 */
const course = { id: SEED_COURSES.examParticipation.id } as any;
// Chrome DevTools Fetch interception is used instead of Playwright routing below. Playwright routing disables the HTTP
// cache for the page, which makes the production client reload unnecessarily expensive and can starve under parallel CI.

// Ceiling for the post-reload re-send. Generous on purpose, and it costs nothing when things are fast: expect.poll
// returns as soon as the re-send lands. The ceiling only matters on a loaded CI runner, where the re-send is preceded
// by a full client bootstrap, exam fetch, and local-storage restoration.
//
// A caution for whoever reads this next: the observed "CI saw zero re-sends" was NOT this budget being too small. It
// was the test reloading before the client had recorded the failed save, after which no re-send can ever happen - see
// the wait added before the reload below. Enlarging this number was tried repeatedly and never fixed it. If this poll
// expires again, the cause is upstream of the budget.
const RESEND_TIMEOUT = 4 * RELOAD_RENDER_TIMEOUT;

// Everything in the test that is not the post-reload wait: participation start, navigation, ticking the answer, the
// forced failing save, and the final UI assertion. Measured at roughly 50s in CI; doubled so the per-test timeout
// derived from it does not sit right on the measurement.
const SETUP_AND_ASSERTION_ALLOWANCE = 120_000;

test.describe('Exam submission recovery after a failed save', { tag: '@slow' }, () => {
    // Block the Angular service worker so the request reaches the page network target intercepted below. The
    // answer-restore-on-reload logic under test lives in the client (local storage), not the service worker.
    // serviceWorkers: 'block' is also the global default in playwright.config.ts; this test keeps its own declaration
    // because its outage injection is correctness-critical.
    test.use({ serviceWorkers: 'block' });

    // The slow-tests project allows 90s per test. This test needs additional room because it deliberately performs a
    // failed save and a full client reload before waiting for the automatic recovery save.
    test.setTimeout(RESEND_TIMEOUT + SETUP_AND_ASSERTION_ALLOWANCE);

    let exam: Exam;
    let quizExercise: Exercise;

    test.beforeEach('Create exam with a multiple-choice quiz', async ({ login, examAPIRequests, examExerciseGroupCreation }) => {
        await login(admin);
        exam = await createExam(course, examAPIRequests, { title: 'exam' + generateUUID() });
        quizExercise = await examExerciseGroupCreation.addGroupWithExercise(exam, ExerciseType.QUIZ, { quizExerciseID: 0 });
        await examAPIRequests.registerStudentForExam(exam, studentTwo);
        await examAPIRequests.generateMissingIndividualExams(exam);
        await examAPIRequests.prepareExerciseStartForExam(exam);
    });

    test('restores and re-sends a not-yet-saved quiz answer after a failed save and reload', async ({ page, examParticipation, examNavigation, quizExerciseMultipleChoice }) => {
        await examParticipation.startParticipation(studentTwo, course, exam);
        await examNavigation.openOrSaveExerciseByTitle(quizExercise.exerciseGroup!.title!);

        // Simulate a failed save (as during an outage) BEFORE touching the answer. CDP Fetch interception preserves the
        // HTTP cache; Playwright's page.route disables it for the page and made the production reload stall under
        // parallel CI load.
        //
        // Every matching save fails for as long as the outage lasts, not only the first one. Fetch.enable pauses every
        // request matching the pattern, so a single-shot handler answers one and leaves a second - the 30s autosave, or
        // a client retry after the 503 - paused with nothing to answer it. Lifting the outage releases that request,
        // which can then reach the server for real and mark the answer synced before the reload. The recovery under test
        // would have nothing left to restore, and the re-send awaited below could never happen.
        const cdpSession = await page.context().newCDPSession(page);
        let outageActive = true;
        const failedSave = new Promise<void>((resolveFailedSave, rejectFailedSave) => {
            cdpSession.on('Fetch.requestPaused', async ({ requestId }) => {
                try {
                    await cdpSession.send('Fetch.fulfillRequest', {
                        requestId,
                        responseCode: 503,
                        responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
                        body: 'e30=',
                    });
                    // Settles on the first failed save; a promise ignores every later call.
                    resolveFailedSave();
                } catch (error) {
                    // Lifting the outage detaches the session, and a request paused at that moment is released by
                    // Fetch.disable rather than answered here, so only a failure during the outage means the injection
                    // itself is broken.
                    if (outageActive) {
                        rejectFailedSave(error as Error);
                    }
                }
            });
        });
        await cdpSession.send('Fetch.enable', {
            patterns: [{ urlPattern: `*://*/api/quiz/exercises/${quizExercise.id}/submissions/exam`, requestStage: 'Request' }],
        });

        // Tick an answer option; the exercise becomes unsynced.
        await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 0);
        await expect(getExercise(page, quizExercise.id!).locator('#answer-option-0')).toHaveClass(/selected/);

        // Force a save attempt and wait deterministically until CDP has fulfilled it with 503.
        // The answer is written to local storage but the server submission stays empty.
        await getExercise(page, quizExercise.id!).locator('#save-exam').click();
        await failedSave;

        // Wait for the CLIENT to have recorded the failure, not merely for the 503 to appear on the wire.
        //
        // This is the difference between this test passing and failing. `waitForResponse` resolves the moment Playwright
        // sees the response; the Angular error handler that records the failure (`onSaveSubmissionError` ->
        // `setLastSaveFailed(true)`) runs afterwards. Reloading in between produces a page whose local storage says the
        // last save succeeded, so the recovery branch in `handleStudentExam` never runs: nothing is restored and nothing
        // is re-sent. The re-send poll below then waits out its whole budget for an event that can no longer happen,
        // which is why every earlier attempt to fix this by enlarging that budget failed.
        //
        // The cached exam itself is not at risk - `triggerSave` writes it synchronously before issuing the request - so
        // this flag is the only thing to wait for.
        const saveFailedKey = `artemis_student_exam_${course.id}_${exam.id}-save-failed`;
        await expect
            .poll(() => page.evaluate((key) => window.localStorage.getItem(key), saveFailedKey), {
                message: 'the client never recorded the failed save, so a reload would not attempt any recovery',
                timeout: RELOAD_RENDER_TIMEOUT,
                intervals: [POLLING_INTERVAL],
            })
            .toBe('true');
        // Record SUCCESSFUL re-sends, but only ones issued after the reload has committed.
        //
        // Both boundaries matter. The listener is attached before the outage is lifted so it cannot miss a re-send the
        // client fires during its own start-up. Attaching it that early leaves a window between disabling interception
        // and reload in which the existing page's autosave could fire a successful PUT. Gating on the main-frame
        // navigation closes that window: the reload is the next main-frame navigation after this point.
        let reloadCommitted = false;
        page.on('framenavigated', (frame) => {
            if (frame === page.mainFrame()) {
                reloadCommitted = true;
            }
        });
        // TEMPORARY DIAGNOSTICS (to be removed before merge): the CI failure shows no conduction fetch and no save
        // request after the reload, so the client is not resuming the exam. Nothing in the server or nginx logs can
        // show why, so capture what the browser itself does.
        const clientLog: string[] = [];
        const startedAt = Date.now();
        const at = () => `+${String(Date.now() - startedAt).padStart(6, ' ')}ms`;
        page.on('console', (message) => clientLog.push(`${at()} console.${message.type()}: ${message.text().slice(0, 180)}`));
        page.on('pageerror', (error) => clientLog.push(`${at()} pageerror: ${error.message.slice(0, 250)}`));
        page.on('requestfailed', (request) => clientLog.push(`${at()} requestfailed: ${request.url().slice(0, 120)} ${request.failure()?.errorText}`));
        page.on('framenavigated', (frame) => {
            if (frame === page.mainFrame()) {
                clientLog.push(`${at()} NAVIGATED -> ${frame.url().slice(0, 140)}`);
            }
        });
        page.on('response', (response) => {
            const url = response.url();
            if (url.includes('/api/') || url.endsWith('.js')) {
                clientLog.push(`${at()} ${response.status()} ${response.request().method()} ${url.slice(0, 120)}`);
            }
        });

        const successfulResends: string[] = [];
        page.on('response', (response) => {
            if (!reloadCommitted) {
                return;
            }
            if (response.url().includes(`/quiz/exercises/${quizExercise.id}/submissions/exam`) && response.request().method() === 'PUT' && response.status() === 200) {
                successfulResends.push(response.url());
            }
        });

        // Stop failing saves so the post-reload re-send can succeed.
        outageActive = false;
        await cdpSession.send('Fetch.disable');
        await cdpSession.detach();
        await page.reload();
        // TEMPORARY DIAGNOSTICS (to be removed before merge).
        clientLog.push(`${at()} RELOAD RETURNED, url = ${page.url()}`);

        // The client re-sends the restored answer by itself while starting up, so the only thing to do is wait for it.
        // Nothing is clicked here on purpose: by the time the exercise is on screen the save button is already
        // `disabled`, because the re-send has happened and there is nothing left to save. An earlier version of this
        // test clicked it anyway and hung for the whole per-test budget on "element is not enabled".
        //
        // The budget is the point: the previous version allowed exactly 30000ms for reload plus bootstrap plus exam
        // re-fetch plus re-send, and every failure was that one wait expiring. See RESEND_TIMEOUT above.
        try {
            await expect
                .poll(() => successfulResends.length, {
                    message: 'the answer restored from local storage was never re-sent to the server',
                    timeout: RESEND_TIMEOUT,
                    intervals: [POLLING_INTERVAL],
                })
                .toBeGreaterThan(0);
        } catch (pollExpired) {
            // TEMPORARY DIAGNOSTICS (to be removed before merge).
            const storage = await page.evaluate(() =>
                Object.keys(window.localStorage)
                    .filter((key) => key.startsWith('artemis_student_exam'))
                    .map((key) => `${key} = ${(window.localStorage.getItem(key) ?? '').slice(0, 120)}`),
            );
            console.log('=== DIAG url after reload ===\n' + page.url());
            console.log('=== DIAG localStorage ===\n' + storage.join('\n'));
            console.log('=== DIAG client activity (' + clientLog.length + ' entries) ===\n' + clientLog.join('\n'));
            throw pollExpired;
        }

        // The restored answer is still selected in the UI: the client read it back out of local storage.
        await examNavigation.openOrSaveExerciseByTitle(quizExercise.exerciseGroup!.title!);
        await expect(getExercise(page, quizExercise.id!).locator('#answer-option-0')).toHaveClass(/selected/);
    });

    test.afterEach('Delete exam', async ({ login, examAPIRequests }) => {
        await login(admin);
        await examAPIRequests.deleteExam(exam);
    });
});

async function createExam(course: any, examAPIRequests: ExamAPIRequests, customExamConfig?: any) {
    const defaultExamConfig = {
        course,
        title: 'exam' + generateUUID(),
        visibleDate: dayjs().subtract(3, 'minutes'),
        startDate: dayjs().subtract(2, 'minutes'),
        endDate: dayjs().add(1, 'hour'),
        examMaxPoints: 10,
        numberOfExercisesInExam: 1,
    };
    return await examAPIRequests.createExam({ ...defaultExamConfig, ...customExamConfig });
}
