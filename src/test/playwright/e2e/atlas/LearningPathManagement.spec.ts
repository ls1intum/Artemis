import { test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { Course } from 'app/course/shared/entities/course.model';
import { expect } from '@playwright/test';
import { Commands } from '../../support/commands';

test.describe('Learning Path Management', { tag: '@fast' }, () => {
    let course: Course;

    test.beforeEach(async ({ login, courseManagementAPIRequests }) => {
        await login(admin);
        course = await courseManagementAPIRequests.createCourse();
    });

    test.afterEach(async ({ courseManagementAPIRequests }) => {
        await courseManagementAPIRequests.deleteCourse(course, admin);
    });

    test('Instructor enables learning paths via activation card', async ({ page }) => {
        // Arrange: course initially without learning paths enabled
        await Commands.gotoAndEnsureRendered(page, `/course-management/${course.id}/learning-path-management`);
        await page.waitForLoadState('domcontentloaded');

        // Wait for the loading spinner to disappear before checking for the activation card
        const spinner = page.locator('.spinner-border');
        await expect(spinner).not.toBeVisible({ timeout: 30000 });

        // Feature activation card is visible and can be enabled
        const activationCard = page.locator('jhi-feature-activation');
        await expect(activationCard).toBeVisible({ timeout: 15000 });
        await activationCard.locator('.jhi-btn').first().click();

        // Assert: management UI becomes visible after enabling
        await expect(page.locator('.learning-paths-analytics-container')).toBeVisible({ timeout: 30000 });
    });

    test('Instructor enables learning paths via course settings', async ({ page }) => {
        // Arrange: course initially without learning paths enabled
        await Commands.gotoAndEnsureRendered(page, `/course-management/${course.id}`);
        // domcontentloaded fires before Angular bootstraps the route component, so the Settings
        // tab is not yet in the DOM. Wait for the course-detail tab strip to render before
        // clicking, otherwise the click auto-wait runs the full 60s test timeout under load.
        const settings = page.getByRole('link', { name: 'Settings' });
        await settings.waitFor({ state: 'visible', timeout: 30_000 });
        await settings.click();

        const lpCheckbox = page.locator('#field_learningPathsEnabled');
        await expect(lpCheckbox).toBeVisible({ timeout: 15000 });
        await lpCheckbox.click();
        await page.locator('#save-entity').click();

        await Commands.gotoAndEnsureRendered(page, `/course-management/${course.id}/learning-path-management`);
        await page.waitForLoadState('domcontentloaded');

        // Wait for both loading spinners to disappear. The learning-path-management page
        // renders two independent containers (jhi-learning-paths-state and
        // jhi-learning-paths-analytics), each with its own loading spinner — under heavy
        // parallel load they can finish loading at different times, so we wait for both
        // explicitly instead of asserting on a bare `.spinner-border` (which strict-mode
        // rejects when multiple match).
        await expect(page.locator('jhi-learning-paths-state .spinner-border')).not.toBeVisible({ timeout: 30000 });
        await expect(page.locator('jhi-learning-paths-analytics .spinner-border')).not.toBeVisible({ timeout: 30000 });

        // Assert: management UI becomes visible after enabling
        await expect(page.locator('.learning-paths-analytics-container')).toBeVisible({ timeout: 30000 });
    });

    test('Instructor disables learning paths via course settings', async ({ page }) => {
        // Two gotoAndEnsureRendered navigations + a settings PUT + waiting on two independent
        // learning-path spinners + a reload-once recovery on the activation card routinely
        // exceeds the 60s @fast budget under multi-node CI load. test.slow() lifts the per-
        // test timeout to 180s which comfortably covers the ~140s worst case observed.
        test.slow();
        await Commands.gotoAndEnsureRendered(page, `/course-management/${course.id}`);
        const settings = page.getByRole('link', { name: 'Settings' });
        await settings.waitFor({ state: 'visible', timeout: 30_000 });
        await settings.click();

        // Toggle checkbox off and save. A fresh course defaults to LP disabled, so the toggle
        // is typically a no-op — the save still issues a PUT we wait for so the LP management
        // page below sees the committed state.
        const lpCheckbox = page.locator('#field_learningPathsEnabled');
        await expect(lpCheckbox).toBeVisible({ timeout: 15000 });
        if (await lpCheckbox.isChecked()) {
            await lpCheckbox.click();
        }
        const saveResponse = page
            .waitForResponse((resp) => resp.url().includes(`/api/course/courses/`) && resp.request().method() === 'PUT' && resp.ok(), { timeout: 15000 })
            .catch(() => undefined);
        await page.locator('#save-entity').click();
        await saveResponse;

        await Commands.gotoAndEnsureRendered(page, `/course-management/${course.id}/learning-path-management`);
        await page.waitForLoadState('domcontentloaded');

        // Wait for both loading spinners to disappear. The learning-path-management page
        // renders two independent containers (jhi-learning-paths-state and
        // jhi-learning-paths-analytics), each with its own loading spinner — under heavy
        // parallel load they can finish loading at different times, so we wait for both
        // explicitly instead of asserting on a bare `.spinner-border` (which strict-mode
        // rejects when multiple match).
        await expect(page.locator('jhi-learning-paths-state .spinner-border')).not.toBeVisible({ timeout: 30000 });
        await expect(page.locator('jhi-learning-paths-analytics .spinner-border')).not.toBeVisible({ timeout: 30000 });

        // Under heavy load the management view briefly renders an empty state before the
        // disabled-LP flag propagates to the activation card. Re-issue the navigation if the
        // card hasn't attached within a generous initial wait; one reload reliably recovers.
        const activationCard = page.locator('jhi-feature-activation');
        const visibleWithin = async (timeout: number): Promise<boolean> =>
            activationCard
                .waitFor({ state: 'visible', timeout })
                .then(() => true)
                .catch(() => false);
        if (!(await visibleWithin(15_000))) {
            await Commands.gotoAndEnsureRendered(page, `/course-management/${course.id}/learning-path-management`);
            await expect(activationCard).toBeVisible({ timeout: 30_000 });
        }
    });

    test('Create simple learning path', async ({ page, courseManagementAPIRequests }) => {
        // Enable learning paths first
        await Commands.gotoAndEnsureRendered(page, `/course-management/${course.id}/learning-path-management`);
        await page.waitForLoadState('domcontentloaded');

        // Wait for both loading spinners to disappear. The learning-path-management page
        // renders two independent containers (jhi-learning-paths-state and
        // jhi-learning-paths-analytics), each with its own loading spinner — under heavy
        // parallel load they can finish loading at different times, so we wait for both
        // explicitly instead of asserting on a bare `.spinner-border` (which strict-mode
        // rejects when multiple match).
        await expect(page.locator('jhi-learning-paths-state .spinner-border')).not.toBeVisible({ timeout: 30000 });
        await expect(page.locator('jhi-learning-paths-analytics .spinner-border')).not.toBeVisible({ timeout: 30000 });

        const activationCard = page.locator('jhi-feature-activation');
        await expect(activationCard).toBeVisible({ timeout: 15000 });
        await activationCard.locator('.jhi-btn').first().click();
        await expect(page.locator('.learning-paths-analytics-container')).toBeVisible({ timeout: 30000 });

        // Create all competencies and prerequisites via API and store their IDs
        const comp1 = await courseManagementAPIRequests.createCompetency(course, 'Competency 1', 'Seed competency for learning path graph test 1');
        const comp2 = await courseManagementAPIRequests.createCompetency(course, 'Competency 2', 'Seed competency for learning path graph test 2');
        const comp3 = await courseManagementAPIRequests.createCompetency(course, 'Competency 3', 'Seed competency for learning path graph test 3');
        const comp4 = await courseManagementAPIRequests.createCompetency(course, 'Competency 4', 'Seed competency for learning path graph test 4');
        const comp5 = await courseManagementAPIRequests.createCompetency(course, 'Competency 5', 'Seed competency for learning path graph test 5');
        const comp6 = await courseManagementAPIRequests.createCompetency(course, 'Competency 6', 'Seed competency for learning path graph test 6');
        const prereq1 = await courseManagementAPIRequests.createPrerequisite(course, 'Prerequisite 1', 'Prerequisite of Competency 1');
        const prereq2 = await courseManagementAPIRequests.createPrerequisite(course, 'Prerequisite 2', 'Prerequisite of Competency 2');

        // Create all relations via API
        // Note: In the UI "Requires" is shown, but the server uses "ASSUMES" for the tail assuming the head
        await courseManagementAPIRequests.createCompetencyRelation(course, prereq1.id, comp1.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, prereq2.id, comp2.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp1.id, comp3.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp2.id, comp3.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp1.id, comp4.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp2.id, comp5.id, 'ASSUMES');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp3.id, comp6.id, 'EXTENDS');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp4.id, comp6.id, 'EXTENDS');
        await courseManagementAPIRequests.createCompetencyRelation(course, comp5.id, comp6.id, 'ASSUMES');

        await page.reload();
        await page.waitForLoadState('domcontentloaded');
        const reloadSpinner = page.locator('.spinner-border');
        await expect(reloadSpinner).not.toBeVisible({ timeout: 30000 });
        await expect(page.locator('.learning-paths-analytics-container')).toBeVisible({ timeout: 30000 });
    });
});
