import dayjs from 'dayjs';

import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { Locator, Page, expect } from '@playwright/test';
import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';
import { getExercise } from '../../../support/utils';
import { BUILD_RESULT_TIMEOUT } from '../../../support/timeouts';

const course = { id: SEED_COURSES.programmingParticipation.id } as any;

function modeButton(page: Page, mode: 'practice' | 'graded') {
    return page.getByTestId(`${mode}-mode-button`);
}

/** Asserts that the given mode is the one the toggle currently reports as selected. */
async function expectModeSelected(button: Locator) {
    await expect(button).toHaveAttribute('data-selected', 'true');
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
            await startPracticeFromExercisePage(page, exercise.id!, 'Practice with template repository');

            const practiceButton = modeButton(page, 'practice');
            const gradedButton = modeButton(page, 'graded');
            await expect(practiceButton).toBeVisible();
            await expect(gradedButton).toBeVisible();
            await expectModeSelected(practiceButton);

            // Switching back to graded must not remove the practice option
            await gradedButton.click();
            await expectModeSelected(gradedButton);
            await expect(practiceButton).toBeVisible();

            // ... and practice can be selected again
            await practiceButton.click();
            await expectModeSelected(practiceButton);

            // The toggle also survives a fresh page load, even though no practice submission exists yet
            await page.goto(`/courses/${course.id}/exercises/${exercise.id}`);
            await expect(modeButton(page, 'practice')).toBeVisible({ timeout: 15000 });
            await expect(modeButton(page, 'graded')).toBeVisible();

            // Submitting in practice mode must process the submission and update the shown result
            await modeButton(page, 'practice').click();
            // Selecting a mode re-routes the embedded editor to that mode's participation, and the file tree must not be
            // touched before that swap: the graded repository is read-only after the due date, so a file action aimed at
            // it is dropped and the editor never sends the request the submission helper waits for. Its create controls
            // being enabled is what says the writable practice repository is the one on screen.
            await expect(getExercise(page, exercise.id!).locator('[data-testid="file-browser-folder-create-file"]').first()).toBeEnabled({ timeout: 30000 });
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, javaAllSuccessfulSubmission, async () => {
                await expect(page.locator('[data-testid="exercise-headers-information"]')).toContainText('100%', { timeout: BUILD_RESULT_TIMEOUT });
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
            await expectModeSelected(practiceButton);
            await expect(page.locator('.code-button')).toBeVisible();

            // The graded mode stays reachable, so the student can recognize that they missed the due date
            await gradedButton.click();
            await expectModeSelected(gradedButton);
            await expect(page.locator('[data-testid="exercise-headers-information"]')).toContainText('Missed due date');

            // ... and practice can be selected again
            await practiceButton.click();
            await expectModeSelected(practiceButton);

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
            await expectModeSelected(modeButton(page, 'practice'));

            // The live submission state is shown in practice mode even though the due date has passed
            // (instead of a static "currently participating" text that never updates)
            await expect(page.locator('[data-testid="exercise-headers-information"]')).toContainText('No result');

            // Submitting in practice mode must process the submission and show its result
            await programmingExerciseEditor.makeSubmissionAndVerifyResults(exercise.id!, javaAllSuccessfulSubmission, async () => {
                await expect(page.locator('[data-testid="exercise-headers-information"]')).toContainText('100%', { timeout: BUILD_RESULT_TIMEOUT });
            });
        });
    });
});

/**
 * Opens the start-practice popover on the exercise details page and starts the practice mode via the
 * option with the given label ('Practice with template repository' / 'Practice with graded participation'
 * when a graded participation exists, plain 'Practice' otherwise).
 */
async function startPracticeFromExercisePage(page: Page, exerciseId: number, optionLabel: string): Promise<void> {
    const startPracticeButton = page.locator(`#start-practice-${exerciseId} button`);
    await startPracticeButton.waitFor({ state: 'visible', timeout: 15000 });
    const popover = page.locator('.start-practice-popover');
    // The deadline has to outlast the retry loop below, which may spend three attempts of up to 30 s before the click
    // that finally starts the practice mode. Playwright's 30 s default would expire during those retries and report a
    // missing response for a request that was still to come.
    const responsePromise = page.waitForResponse((response) => response.url().includes(`/exercises/${exerciseId}/participations/practice`) && response.status() === 201, {
        timeout: 150000,
    });

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

    await responsePromise;
}
