import { Page, expect } from '@playwright/test';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import { dismissPasskeyReminderIfPresent } from '../../../support/dismissPasskeyReminder';
import { test } from '../../../support/fixtures';
import { SEED_COURSES } from '../../../support/seedData';
import { admin, instructor } from '../../../support/users';

const course = { id: SEED_COURSES.programmingManagement.id } as any;

/** what only matters with several containers, which a plain build plan with one container does not show */
const CONTAINER_SETTINGS = ['container-name-input', 'container-timeout-input', 'container-scoping-checkbox', 'container-docker-flags-checkbox'];

test.describe('Build plan editor', { tag: '@fast' }, () => {
    let exercise: ProgrammingExercise;

    test.beforeEach('Create a programming exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        exercise = await exerciseAPIRequests.createProgrammingExercise({ course });
    });

    test.afterEach('Delete the programming exercise', async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        await exerciseAPIRequests.deleteProgrammingExercise(exercise.id!);
    });

    const containerEditors = (page: Page) => page.getByTestId('build-container-editor');

    const expectContainerSettingsShown = async (page: Page, containerIndex: number, shown: boolean) => {
        const container = containerEditors(page).nth(containerIndex);
        for (const setting of CONTAINER_SETTINGS) {
            await expect(container.getByTestId(setting), setting).toHaveCount(shown ? 1 : 0);
        }
    };

    test('Shows the settings of a build container only once the build plan needs them', async ({ login, page }) => {
        // saving triggers the template and solution builds of both containers, on top of the setup's builds
        test.setTimeout(600000);
        await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}/edit-build-plan`);
        await dismissPasskeyReminderIfPresent(page);

        // a plain build plan: its only container shows its image and phases, and nothing the exercise's settings cover
        await expect(containerEditors(page)).toHaveCount(1);
        await expect(containerEditors(page).first().getByTestId('container-image-input')).toBeVisible();
        await expect(containerEditors(page).first().getByTestId('phase-name-input').first()).toBeVisible();
        await expectContainerSettingsShown(page, 0, false);

        // a second container brings the settings of both containers in
        await page.getByTestId('add-container-button').click();
        await expect(containerEditors(page)).toHaveCount(2);
        await expectContainerSettingsShown(page, 0, true);
        await expectContainerSettingsShown(page, 1, true);

        // the two containers are stored and come back from the server
        const secondContainer = containerEditors(page).nth(1);
        await secondContainer.getByTestId('container-name-input').fill('student_tests');
        await secondContainer.getByTestId('add-phase-button').click();
        await secondContainer.getByTestId('phase-name-input').first().fill('checks');
        const saved = page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().includes(`programming-exercises/${exercise.id}/build`));
        await page.getByTestId('submit-build-plan-button').getByRole('button').click();
        expect((await saved).ok()).toBe(true);

        await page.reload();
        await expect(containerEditors(page)).toHaveCount(2);
        await expect(containerEditors(page).first().getByTestId('container-name-input')).toHaveValue('default');
        await expect(containerEditors(page).nth(1).getByTestId('container-name-input')).toHaveValue('student_tests');
        await expect(containerEditors(page).nth(1).getByTestId('phase-name-input').first()).toHaveValue('checks');

        // back to one container without settings of its own, the settings give way again
        await containerEditors(page).nth(1).getByTestId('remove-container-button').getByRole('button').click();
        await expect(containerEditors(page)).toHaveCount(1);
        await expectContainerSettingsShown(page, 0, false);
    });

    test('Keeps the settings of an only container visible while one of them is set', async ({ login, page }) => {
        test.setTimeout(600000);
        await login(instructor, `/course-management/${course.id}/programming-exercises/${exercise.id}/edit-build-plan`);
        await dismissPasskeyReminderIfPresent(page);

        // a timeout set while there were two containers still takes effect after the second one is gone, so it has to stay in sight
        await page.getByTestId('add-container-button').click();
        await containerEditors(page).first().getByTestId('container-timeout-input').fill('90');
        await containerEditors(page).nth(1).getByTestId('remove-container-button').getByRole('button').click();
        await expect(containerEditors(page)).toHaveCount(1);
        await expectContainerSettingsShown(page, 0, true);
        await expect(containerEditors(page).first().getByTestId('container-timeout-input')).toHaveValue('90');

        // clearing it leaves nothing to show
        await containerEditors(page).first().getByTestId('container-timeout-input').fill('');
        await expectContainerSettingsShown(page, 0, false);
    });
});
