import dayjs from 'dayjs';

import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { Page, expect } from '@playwright/test';
import javaAllSuccessfulSubmission from '../../../fixtures/exercise/programming/java/all_successful/submission.json';
import { admin, studentOne } from '../../../support/users';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.programmingParticipation.id } as any;

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
            dueDate = dayjs().add(30, 'seconds');
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

        test('Keeps the practice mode selectable when switching back to graded', async ({ login, page }) => {
            test.slow();
            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            await startPracticeFromExercisePage(page, exercise.id!, 'Practice with template repository');

            const practiceButton = page.locator('#practice-mode-button');
            const gradedButton = page.locator('#graded-mode-button');
            await expect(practiceButton).toBeVisible();
            await expect(gradedButton).toBeVisible();
            await expect(practiceButton).toHaveClass(/segmented-control__button--active/);

            // Switching back to graded must not remove the practice option
            await gradedButton.click();
            await expect(gradedButton).toHaveClass(/segmented-control__button--active/);
            await expect(practiceButton).toBeVisible();

            // ... and practice can be selected again
            await practiceButton.click();
            await expect(practiceButton).toHaveClass(/segmented-control__button--active/);

            // The toggle also survives a fresh page load, even though no practice submission exists yet
            await page.goto(`/courses/${course.id}/exercises/${exercise.id}`);
            await expect(page.locator('#practice-mode-button')).toBeVisible({ timeout: 15000 });
            await expect(page.locator('#graded-mode-button')).toBeVisible();
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

        test('Starts the practice mode and keeps it after a page reload', async ({ login, page }) => {
            await login(studentOne, `/courses/${course.id}/exercises/${exercise.id}`);
            await startPracticeFromExercisePage(page, exercise.id!, 'Practice');

            // Without a graded participation only the practice badge is shown
            await expect(page.locator('#mode-badge')).toContainText('Practice');
            await expect(page.locator('.code-button')).toBeVisible();

            // The practice mode survives a fresh page load, even though no practice submission exists yet
            await page.goto(`/courses/${course.id}/exercises/${exercise.id}`);
            await expect(page.locator('#mode-badge')).toContainText('Practice', { timeout: 15000 });
            await expect(page.locator('.code-button')).toBeVisible();
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
    await startPracticeButton.click();
    const popover = page.locator('.start-practice-popover');
    await popover.waitFor({ state: 'visible' });
    const responsePromise = page.waitForResponse((response) => response.url().includes(`/exercises/${exerciseId}/participations/practice`) && response.status() === 201);
    await popover.locator('button', { hasText: optionLabel }).first().click();
    await responsePromise;
}
