import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { admin } from '../../../support/users';
import { generateUUID } from '../../../support/utils';
import { SEED_COURSES } from '../../../support/seedData';
import generationResponse from '../../../fixtures/exercise/quiz/ai-generation-response.json';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

const GENERATE_ENDPOINT = `**/api/hyperion/courses/${course.id}/quiz-exercises/generate-questions`;

/**
 * Intercepts management/info and injects 'hyperion' into activeModuleFeatures so the
 * AI generation button and modal are rendered in the quiz editor during tests.
 */
async function enableHyperion(page: import('@playwright/test').Page) {
    await page.route('**/management/info', async (route) => {
        const response = await route.fetch();
        const json = await response.json();
        json.activeModuleFeatures = [...(json.activeModuleFeatures ?? []), 'hyperion'];
        await route.fulfill({ json });
    });
}

test.describe('Quiz Exercise AI Generation', { tag: '@fast' }, () => {
    // Block the Angular service worker: the production WAR registers ngsw-worker.js, which handles fetches before
    // Playwright's page.route can intercept them (default serviceWorkers: 'allow'), so the mocked Hyperion responses
    // below would be silently bypassed and the request would reach the real (non-existent) server endpoint instead.
    test.use({ serviceWorkers: 'block' });

    // Competencies created via API during a test, deleted in afterEach to avoid accumulating in the shared seed course.
    let createdCompetencyIds: number[] = [];

    test.beforeEach('Navigate to quiz creation with Hyperion enabled', async ({ page, login, courseManagement, courseManagementExercises, quizExerciseCreation }) => {
        createdCompetencyIds = [];
        await enableHyperion(page);
        await login(admin, '/courses');
        await courseManagement.openExercisesOfCourse(course.id!);
        await courseManagementExercises.createQuizExercise();
        await quizExerciseCreation.setTitle('AI Gen Quiz ' + generateUUID());
        // The quiz is never saved (tests only open the creation form), so there is no persisted quiz to clean up.
    });

    test.afterEach('Delete competencies created during the test', async ({ login, courseManagementAPIRequests }) => {
        if (createdCompetencyIds.length) {
            await login(admin);
            for (const competencyId of createdCompetencyIds) {
                await courseManagementAPIRequests.deleteCompetency(course, competencyId);
            }
            createdCompetencyIds = [];
        }
    });

    test('AI generation button is visible when Hyperion is enabled', async ({ page }) => {
        await expect(page.locator('.section-header-action').filter({ hasText: /AI|Generate/i })).toBeVisible({ timeout: 15000 });
    });

    test('Opens AI generation modal with custom close button in preview panel', async ({ page }) => {
        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();

        const modal = page.getByTestId('quiz-ai-generation-modal');
        await expect(modal).toBeVisible({ timeout: 10000 });

        // The modal hides the dialog header, so its only close button is the one in the preview panel
        await expect(modal.getByTestId('quiz-ai-generation-modal-header')).toHaveCount(0);

        // Close button is inside the preview panel
        const previewCloseBtn = modal.getByTestId('quiz-ai-close-button');
        await expect(previewCloseBtn).toBeVisible();
    });

    test('Closes modal via preview-panel close button without adding questions', async ({ page }) => {
        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();
        await expect(page.getByTestId('quiz-ai-generation-modal')).toBeVisible({ timeout: 10000 });

        await page.getByTestId('quiz-ai-close-button').click();

        await expect(page.getByTestId('quiz-ai-generation-modal')).not.toBeVisible({ timeout: 5000 });
        // No questions were added to the editor
        await expect(page.locator('.question')).toHaveCount(0);
    });

    test('Free-topic mode: generates questions and renders Markdown in preview cards', async ({ page }) => {
        await page.route(GENERATE_ENDPOINT, async (route) => {
            await route.fulfill({ json: generationResponse });
        });

        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();
        const modal = page.getByTestId('quiz-ai-generation-modal');
        await expect(modal).toBeVisible({ timeout: 10000 });

        // Fill in topic
        await modal.locator('#quiz-ai-topic').fill('Data Structures');

        await modal.getByTestId('quiz-ai-generate-button').click();

        // Wait for all 3 question cards to appear
        const cards = modal.locator('jhi-quiz-ai-generated-question-card');
        await expect(cards).toHaveCount(3, { timeout: 15000 });

        // Assert Markdown is rendered as HTML, not raw characters
        // The first question contains **time complexity** and `O(log n)`
        const firstCard = cards.nth(0);
        await expect(firstCard.getByTestId('question-text').locator('strong')).toBeVisible();
        await expect(firstCard.getByTestId('question-text').locator('code')).toBeVisible();
    });

    test('Free-topic mode: adds generated questions to the quiz editor', async ({ page }) => {
        await page.route(GENERATE_ENDPOINT, async (route) => {
            await route.fulfill({ json: generationResponse });
        });

        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();
        const modal = page.getByTestId('quiz-ai-generation-modal');
        await expect(modal).toBeVisible({ timeout: 10000 });

        await modal.locator('#quiz-ai-topic').fill('Algorithms');
        await modal.getByTestId('quiz-ai-generate-button').click();

        await expect(modal.locator('jhi-quiz-ai-generated-question-card')).toHaveCount(3, { timeout: 15000 });

        await modal.getByTestId('quiz-ai-add-to-quiz').click();

        // Modal closes and 3 questions appear in the editor
        await expect(modal).not.toBeVisible({ timeout: 5000 });
        await expect(page.locator('.question')).toHaveCount(3, { timeout: 10000 });
    });

    test('Competency mode: toggle shows competency multi-select and hides topic input', async ({ page, courseManagementAPIRequests }) => {
        // Create competencies so the mode toggle appears
        const sorting = await courseManagementAPIRequests.createCompetency(course, 'Sorting Algorithms ' + generateUUID());
        const graph = await courseManagementAPIRequests.createCompetency(course, 'Graph Theory ' + generateUUID());
        createdCompetencyIds.push(sorting.id, graph.id);

        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();
        const modal = page.getByTestId('quiz-ai-generation-modal');
        await expect(modal).toBeVisible({ timeout: 10000 });

        // Wait for competency mode toggle to appear
        await expect(modal.getByTestId('quiz-ai-mode-competency-graph')).toBeVisible({ timeout: 15000 });

        // Click the second mode button (competency-graph)
        await modal.getByTestId('quiz-ai-mode-competency-graph').click();

        // Topic input gone, competency multi-select shown.
        await expect(modal.locator('#quiz-ai-topic')).not.toBeVisible();
        await expect(modal.getByTestId('quiz-ai-competencies')).toBeVisible();
    });

    test('Competency mode: sends competencyIds in generation request', async ({ page, courseManagementAPIRequests }) => {
        const competencyTitle = 'Recursion ' + generateUUID();
        const comp = await courseManagementAPIRequests.createCompetency(course, competencyTitle);
        const compId: number = comp.id;
        createdCompetencyIds.push(compId);

        let capturedBody: any;
        await page.route(GENERATE_ENDPOINT, async (route) => {
            capturedBody = JSON.parse(route.request().postData() ?? '{}');
            await route.fulfill({ json: generationResponse });
        });

        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();
        const modal = page.getByTestId('quiz-ai-generation-modal');
        await expect(modal).toBeVisible({ timeout: 10000 });

        // Switch to competency mode
        await expect(modal.getByTestId('quiz-ai-mode-competency-graph')).toBeVisible({ timeout: 15000 });
        await modal.getByTestId('quiz-ai-mode-competency-graph').click();

        // Select the just-created competency via the multi-select. The course already has other
        // competencies, so pick the option by its unique title rather than the first option.
        // The options render in an overlay outside the modal, so they are looked up on the page.
        const multiSelectTrigger = modal.locator('#quiz-ai-competencies');
        await multiSelectTrigger.click();
        const option = page.getByTestId('quiz-ai-competency-option').filter({ hasText: competencyTitle });
        await expect(option).toBeVisible({ timeout: 5000 });
        await option.click();
        // Close the overlay: its backdrop covers the modal, so Escape on the trigger is the way out
        await multiSelectTrigger.press('Escape');

        await modal.getByTestId('quiz-ai-generate-button').click();

        await expect(modal.locator('jhi-quiz-ai-generated-question-card')).toHaveCount(3, { timeout: 15000 });

        // Verify the request contained competencyIds, not a topic
        expect(capturedBody).toHaveProperty('competencyIds');
        expect(Array.isArray(capturedBody.competencyIds)).toBeTruthy();
        expect(capturedBody.competencyIds).toContain(compId);
        expect(capturedBody).not.toHaveProperty('topic');
    });

    test('Shows loading state while generating', async ({ page }) => {
        let resolveRoute: () => void;
        const routeBlocker = new Promise<void>((resolve) => {
            resolveRoute = resolve;
        });

        await page.route(GENERATE_ENDPOINT, async (route) => {
            await routeBlocker;
            await route.fulfill({ json: generationResponse });
        });

        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();
        const modal = page.getByTestId('quiz-ai-generation-modal');
        await expect(modal).toBeVisible({ timeout: 10000 });

        await modal.locator('#quiz-ai-topic').fill('Algorithms');
        await modal.getByTestId('quiz-ai-generate-button').click();

        // Loading phrase shown while generating
        await expect(modal.getByTestId('quiz-ai-generate-loading')).toBeVisible({ timeout: 5000 });
        await expect(modal.getByTestId('quiz-ai-generate-button')).toBeDisabled();

        resolveRoute!();
        await expect(modal.locator('jhi-quiz-ai-generated-question-card')).toHaveCount(3, { timeout: 15000 });
    });

    test('Generate button disabled when topic field is empty', async ({ page }) => {
        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();
        const modal = page.getByTestId('quiz-ai-generation-modal');
        await expect(modal).toBeVisible({ timeout: 10000 });

        // Topic field is empty by default — generate button must be disabled
        await expect(modal.locator('#quiz-ai-topic')).toHaveValue('');
        await expect(modal.getByTestId('quiz-ai-generate-button')).toBeDisabled();

        // Typing a topic enables it
        await modal.locator('#quiz-ai-topic').fill('Sorting');
        await expect(modal.getByTestId('quiz-ai-generate-button')).toBeEnabled();

        // Clearing it disables it again
        await modal.locator('#quiz-ai-topic').fill('');
        await expect(modal.getByTestId('quiz-ai-generate-button')).toBeDisabled();
    });

    test('API error on generation shows error alert', async ({ page }) => {
        await page.route(GENERATE_ENDPOINT, async (route) => {
            await route.fulfill({ status: 500, json: { message: 'Internal Server Error' } });
        });

        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();
        const modal = page.getByTestId('quiz-ai-generation-modal');
        await expect(modal).toBeVisible({ timeout: 10000 });

        await modal.locator('#quiz-ai-topic').fill('Algorithms');
        await modal.getByTestId('quiz-ai-generate-button').click();

        // Error alert shown; no question cards appear
        await expect(page.locator('[data-testid="alert"][data-alert-type="danger"]')).toBeVisible({ timeout: 10000 });
        await expect(modal.locator('jhi-quiz-ai-generated-question-card')).toHaveCount(0);
    });

    test('Question type toggles: deselecting all types disables generate button', async ({ page }) => {
        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();
        const modal = page.getByTestId('quiz-ai-generation-modal');
        await expect(modal).toBeVisible({ timeout: 10000 });

        await modal.locator('#quiz-ai-topic').fill('Sorting');
        // Single Choice is selected by default — deselect it
        await modal.getByText('Single Choice').click();

        // With no question types selected, generate is disabled
        await expect(modal.getByTestId('quiz-ai-generate-button')).toBeDisabled();

        // Re-selecting a type re-enables it
        await modal.getByText('Multiple Choice').click();
        await expect(modal.getByTestId('quiz-ai-generate-button')).toBeEnabled();
    });

    test('Question type toggles: selected types are sent in request body', async ({ page }) => {
        let capturedBody: any;
        await page.route(GENERATE_ENDPOINT, async (route) => {
            capturedBody = JSON.parse(route.request().postData() ?? '{}');
            await route.fulfill({ json: generationResponse });
        });

        await page
            .locator('.section-header-action')
            .filter({ hasText: /AI|Generate/i })
            .click();
        const modal = page.getByTestId('quiz-ai-generation-modal');
        await expect(modal).toBeVisible({ timeout: 10000 });

        await modal.locator('#quiz-ai-topic').fill('Sorting');
        // Add True/False to the default Single Choice selection
        await modal.getByText('True/False').click();

        await modal.getByTestId('quiz-ai-generate-button').click();
        await expect(modal.locator('jhi-quiz-ai-generated-question-card')).toHaveCount(3, { timeout: 15000 });

        expect(capturedBody.questionTypes).toContain('single-choice');
        expect(capturedBody.questionTypes).toContain('true-false');
        expect(capturedBody.questionTypes).not.toContain('multiple-choice');
    });
});
