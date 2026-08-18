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
// Matcher for the quiz exam-save endpoint (PUT /api/quiz/exercises/{id}/submissions/exam), used to inject the failed
// save below. A RegExp keeps the match unambiguous against the absolute request URL.
const quizSaveUrl = /\/api\/quiz\/exercises\/\d+\/submissions\/exam/;

// Ceiling for the post-reload re-send. Generous on purpose, and it costs nothing when things are fast: expect.poll
// returns as soon as the re-send lands, which locally is about two seconds. The ceiling only matters on a loaded CI
// runner, where the re-send is preceded by a full client re-bootstrap with Playwright's per-context HTTP cache
// disabled - bundle and lazy chunks re-fetched, then the exam re-fetched, then the answer restored and sent. A 30s
// ceiling (the RELOAD_RENDER_TIMEOUT default) was measurably too tight there: CI saw zero re-sends inside it while the
// same code re-sent in ~2s locally. Measured: this test takes ~4s end to end against the local dev server and
// ~113s in CI against the containerised production WAR, so the multiplier is sized for the slow environment
// rather than the fast one - every previous budget here was sized to the happy path and kept expiring.
// Paired with the test.setTimeout in the describe block below, which keeps the worst case inside the cap.
const RESEND_TIMEOUT = 4 * RELOAD_RENDER_TIMEOUT;

test.describe('Exam submission recovery after a failed save', { tag: '@slow' }, () => {
    // Block the Angular service worker for this test. The production WAR registers ngsw-worker.js, which handles the
    // quiz exam-save fetch; Playwright's page.route does NOT intercept service-worker-handled requests, so the 503
    // outage we inject below was silently bypassed and the save reached the real server (200). Blocking the SW lets
    // page.route intercept the save directly; the answer-restore-on-reload logic under test lives in the client
    // (local storage), not the SW, so this does not change what the test verifies. serviceWorkers: 'block' is now
    // also the global default in playwright.config.ts; this test keeps its own declaration because its route-based
    // outage injection is correctness-critical, not merely flake mitigation.
    test.use({ serviceWorkers: 'block' });

    // The slow-tests project allows 90s per test, which this test cannot meet in CI: it was measured at ~113s there,
    // because the setup (start participation, navigate, tick, forced failed save) runs before a reload that has to
    // re-bootstrap the client with the HTTP cache disabled, re-fetch the exam and re-send the restored answer.
    // Raised well past the measurement rather than just past it, so a slower runner does not put it back at the cap.
    test.setTimeout(240_000);

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

        // Simulate a failed save (as during an outage) BEFORE touching the answer: make the quiz exam save endpoint fail.
        // Installing it before the first answer change guarantees no save can succeed first (e.g. a coincidental 30s
        // autosave) and silently mark the answer synced, which would make the forced save below a no-op.
        await page.route(quizSaveUrl, (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{}' }));

        // Tick an answer option; the exercise becomes unsynced.
        await quizExerciseMultipleChoice.tickAnswerOption(quizExercise.id!, 0);
        await expect(getExercise(page, quizExercise.id!).locator('#answer-option-0')).toHaveClass(/selected/);

        // Force a save attempt and wait deterministically for the failed (503) save instead of a fixed timeout.
        // The answer is written to local storage but the server submission stays empty.
        const failedSave = page.waitForResponse((response) => response.url().includes(`/quiz/exercises/${quizExercise.id}/submissions/exam`) && response.status() === 503, {
            timeout: 30000,
        });
        await getExercise(page, quizExercise.id!).locator('#save-exam').click();
        await failedSave;
        // Record every SUCCESSFUL re-send from here on. Registered before the outage is lifted, so no pre-reload save
        // can be counted (every save so far was answered 503) and none can be missed either - the client may fire the
        // re-send during its own start-up, which a waitForResponse registered after the reload would never see.
        const successfulResends: string[] = [];
        page.on('response', (response) => {
            if (response.url().includes(`/quiz/exercises/${quizExercise.id}/submissions/exam`) && response.request().method() === 'PUT' && response.status() === 200) {
                successfulResends.push(response.url());
            }
        });

        // Stop failing saves so the post-reload re-send can succeed.
        await page.unroute(quizSaveUrl);
        await page.reload();

        // The client re-sends the restored answer by itself while starting up, so the only thing to do is wait for it.
        // Nothing is clicked here on purpose: by the time the exercise is on screen the save button is already
        // `disabled`, because the re-send has happened and there is nothing left to save. An earlier version of this
        // test clicked it anyway and hung for the whole per-test budget on "element is not enabled".
        //
        // The budget is the point: the previous version allowed exactly 30000ms for reload plus bootstrap plus exam
        // re-fetch plus re-send, and every failure was that one wait expiring. See RESEND_TIMEOUT above.
        await expect
            .poll(() => successfulResends.length, {
                message: 'the answer restored from local storage was never re-sent to the server',
                timeout: RESEND_TIMEOUT,
                intervals: [POLLING_INTERVAL],
            })
            .toBeGreaterThan(0);

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
