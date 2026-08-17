import { test } from '../../support/fixtures';
import { expect } from '@playwright/test';
import { admin, studentTwo } from '../../support/users';
import { generateUUID, getExercise } from '../../support/utils';
import dayjs from 'dayjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Exercise, ExerciseType } from '../../support/constants';
import { ExamAPIRequests } from '../../support/requests/ExamAPIRequests';
import { SEED_COURSES } from '../../support/seedData';

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

test.describe('Exam submission recovery after a failed save', { tag: '@slow' }, () => {
    // Block the Angular service worker for this test. The production WAR registers ngsw-worker.js, which handles the
    // quiz exam-save fetch; Playwright's page.route does NOT intercept service-worker-handled requests, so the 503
    // outage we inject below was silently bypassed and the save reached the real server (200). Blocking the SW lets
    // page.route intercept the save directly; the answer-restore-on-reload logic under test lives in the client
    // (local storage), not the SW, so this does not change what the test verifies. serviceWorkers: 'block' is now
    // also the global default in playwright.config.ts; this test keeps its own declaration because its route-based
    // outage injection is correctness-critical, not merely flake mitigation.
    test.use({ serviceWorkers: 'block' });

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

        // The restored answer is still selected in the UI: the client read it back out of local storage.
        await examNavigation.openOrSaveExerciseByTitle(quizExercise.exerciseGroup!.title!);
        await expect(getExercise(page, quizExercise.id!).locator('#answer-option-0')).toHaveClass(/selected/, { timeout: 15000 });

        // Trigger the re-send instead of waiting for the autosave to do it. AUTOSAVE_EXERCISE_INTERVAL is 30s and the
        // timer restarts at 0 on reload, so the previous 30s `waitForResponse` was racing the very timer it depended
        // on - it started counting before the page had finished loading and lost whenever the reload was slow, which
        // is the whole of this test's flakiness. Forcing the save still covers the regression: an answer wrongly
        // marked as already synced is not re-sent by an explicit save either, so the assertion below still fails.
        await getExercise(page, quizExercise.id!).locator('#save-exam').click();
        await expect
            .poll(() => successfulResends.length, {
                message: 'the answer restored from local storage was never re-sent to the server',
                timeout: 30000,
            })
            .toBeGreaterThan(0);
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
