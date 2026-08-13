import dayjs from 'dayjs';

import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { Page, expect } from '@playwright/test';
import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';
import { BUILD_RESULT_TIMEOUT } from '../../../support/timeouts';

const course = { id: SEED_COURSES.programmingParticipation.id } as any;

// The graded/practice toggle renders as a PrimeNG select button; each option is a `.p-togglebutton`
// whose active state is expressed via the `p-togglebutton-checked` class.
const ACTIVE_MODE_CLASS = /p-togglebutton-checked/;

function modeButton(page: Page, mode: 'practice' | 'graded') {
    return page.locator(`.p-togglebutton:has(#${mode}-mode-button)`);
}

/**
 * Regression tests for the practice mode of programming exercises (issue #12780): after the due date,
 * starting practice must keep the practice mode reachable — both while switching between graded and
 * practice and across a full page reload — even though the fresh practice participation has no
 * submission yet (a programming practice participation only receives a submission on the first push).
 */
test.describe('Programming exercise practice mode', { tag: '@slow' }, () => {
    test.describe('After the due date with a graded submission', () => {
        let exercise: ProgrammingExercise;
        let dueDate: dayjs.Dayjs;

        test.beforeEach('Create exercise and make a graded submission before the due date', async ({ login, page, exerciseAPIRequests }) => {
            await login(admin);
            // The budget must also cover the exercise creation (template/solution/test repo provisioning)
            // and the graded submission, which all happen before the due date passes.
            dueDate = dayjs().add(45, 'seconds');
            exercise = await exerciseAPIRequests.createProgrammingExercise({ course, dueDate });
            await login(studentOne);
            const response = await exerciseAPIRequests.startExerciseParticipation(exercise.id!);
            const participation = await response.json();
            await exerciseAPIRequests.makeProgrammingExerciseSubmission(participation.id!, javaAllSuccessfulSubmission);
            const now = dayjs();
            if (now.isBefore(dueDate)) {
                await page.waitForTimeout(dueDate.diff(now, 'ms') + 2000);
            }
        });

        test('Keeps the practice mode selectable when switching back to graded', async ({ login, page, programmingExerciseEditor }) => {
            test.slow();
            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            const practiceParticipationId = await startPracticeFromExercisePage(page, exercise.id!, 'Practice with template repository');

            const practiceButton = modeButton(page, 'practice');
            const gradedButton = modeButton(page, 'graded');
            await expect(practiceButton).toBeVisible();
            await expect(gradedButton).toBeVisible();
            await expect(practiceButton).toHaveClass(ACTIVE_MODE_CLASS);

            // Switching back to graded must not remove the practice option
            await gradedButton.click();
            await expect(gradedButton).toHaveClass(ACTIVE_MODE_CLASS);
            await expect(practiceButton).toBeVisible();

            // ... and practice can be selected again
            await practiceButton.click();
            await expect(practiceButton).toHaveClass(ACTIVE_MODE_CLASS);

            // The toggle also survives a fresh page load, even though no practice submission exists yet
            await page.goto(`/courses/${course.id}/exercises/${exercise.id}`);
            await expect(modeButton(page, 'practice')).toBeVisible({ timeout: 15000 });
            await expect(modeButton(page, 'graded')).toBeVisible();

            // Submitting in practice mode must process the submission and update the shown result
            await modeButton(page, 'practice').click();
            // Selecting a mode re-routes the embedded editor to that mode's participation. Wait for the practice
            // repository to be the one on screen before touching the file tree: the graded repository is read-only
            // after the due date, so a file action aimed at the outgoing tree is dropped and the editor never sends
            // the repository request the submission helper waits for.
            await page.waitForURL((url) => url.pathname.endsWith(`/code-editor/${practiceParticipationId}`), { timeout: 30000 });
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, javaAllSuccessfulSubmission, async () => {
                await expect(page.locator('#exercise-headers-information')).toContainText('100%', { timeout: BUILD_RESULT_TIMEOUT });
            });
        });
    });

    test.describe('After the due date without a graded participation', () => {
        let exercise: ProgrammingExercise;

        test.beforeEach('Create exercise whose due date has already passed', async ({ login, exerciseAPIRequests }) => {
            await login(admin);
            exercise = await exerciseAPIRequests.createProgrammingExercise({
                course,
                releaseDate: dayjs().subtract(1, 'hour'),
                dueDate: dayjs().subtract(5, 'minutes'),
            });
        });

        test('Starts the practice mode and allows switching to the missed graded mode', async ({ login, page }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            await startPracticeFromExercisePage(page, exercise.id!, 'Practice');

            const practiceButton = modeButton(page, 'practice');
            const gradedButton = modeButton(page, 'graded');
            await expect(practiceButton).toBeVisible();
            await expect(practiceButton).toHaveClass(ACTIVE_MODE_CLASS);
            await expect(page.locator('.code-button')).toBeVisible();

            // The graded mode stays reachable, so the student can recognize that they missed the due date
            await gradedButton.click();
            await expect(gradedButton).toHaveClass(ACTIVE_MODE_CLASS);
            await expect(page.locator('#exercise-headers-information')).toContainText('Missed due date');

            // ... and practice can be selected again
            await practiceButton.click();
            await expect(practiceButton).toHaveClass(ACTIVE_MODE_CLASS);

            // The practice mode survives a fresh page load, even though no practice submission exists yet
            await page.goto(`/courses/${course.id}/exercises/${exercise.id}`);
            await expect(modeButton(page, 'practice')).toBeVisible({ timeout: 15000 });
            await expect(modeButton(page, 'graded')).toBeVisible();
            await expect(page.locator('.code-button')).toBeVisible();
        });

        test('Shows the submission state when submitting in the practice mode code editor', async ({ login, page, programmingExerciseEditor }) => {
            test.slow();
            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            await startPracticeFromExercisePage(page, exercise.id!, 'Practice');
            await expect(modeButton(page, 'practice')).toHaveClass(ACTIVE_MODE_CLASS);

            // The live submission state is shown in practice mode even though the due date has passed
            // (instead of a static "currently participating" text that never updates)
            await expect(page.locator('#exercise-headers-information')).toContainText('No result');

            // Submitting in practice mode must process the submission and show its result
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, javaAllSuccessfulSubmission, async () => {
                await expect(page.locator('#exercise-headers-information')).toContainText('100%', { timeout: BUILD_RESULT_TIMEOUT });
            });
        });
    });
});

/**
 * Opens the start-practice popover on the exercise details page and starts the practice mode via the
 * option with the given label ('Practice with template repository' / 'Practice with graded participation'
 * when a graded participation exists, plain 'Practice' otherwise).
 *
 * @returns the id of the created practice participation, which also identifies its editor route.
 */
async function startPracticeFromExercisePage(page: Page, exerciseId: number, optionLabel: string): Promise<number> {
    const startPracticeButton = page.locator(`#start-practice-${exerciseId} button`);
    await startPracticeButton.waitFor({ state: 'visible', timeout: 15000 });
    const popover = page.locator('.start-practice-popover');
    const responsePromise = page.waitForResponse((response) => response.url().includes(`/exercises/${exerciseId}/participations/practice`) && response.status() === 201);

    // Opening the popover and picking an option are retried as a pair, because an exercise page that already has a
    // participation routes itself to that participation's editor a moment after the details arrive. That navigation
    // re-renders the header and closes the popover, and a click that lost the race spins forever: the option passes
    // the actionability check and is gone by the time the click lands ("element is not visible", retried until the
    // test dies). Reopening after the one-shot navigation settles is what makes this deterministic.
    for (let attempt = 0; attempt < 3; attempt++) {
        try {
            await startPracticeButton.click({ timeout: 10000 });
            await popover.waitFor({ state: 'visible', timeout: 10000 });
            await popover.locator('button', { hasText: optionLabel }).first().click({ timeout: 10000 });
            break;
        } catch (error) {
            if (attempt === 2) {
                throw error;
            }
        }
    }

    const participation = await (await responsePromise).json();
    return participation.id;
}
