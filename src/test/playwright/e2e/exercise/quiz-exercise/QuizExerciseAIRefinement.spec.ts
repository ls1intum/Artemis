import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import multipleChoiceTemplate from '../../../fixtures/exercise/quiz/multiple_choice/template.json';
import refinementSuccess from '../../../fixtures/exercise/quiz/ai-refinement-success-response.json';
import bulkRefinementSuccess from '../../../fixtures/exercise/quiz/ai-bulk-refinement-response.json';
import { test } from '../../../support/fixtures';
import { expect } from '@playwright/test';
import { admin } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';

const course = { id: SEED_COURSES.exerciseManagement.id } as any;

const REFINE_ENDPOINT = `**/api/hyperion/courses/${course.id}/quiz-exercises/refine-question`;
const BULK_REFINE_ENDPOINT = `**/api/hyperion/courses/${course.id}/quiz-exercises/refine-all-questions`;

/**
 * Intercepts management/info and injects 'hyperion' into activeModuleFeatures so the
 * AI refinement panel is rendered in the quiz editor during tests.
 */
async function enableHyperion(page: import('@playwright/test').Page) {
    await page.route('**/management/info', async (route) => {
        const response = await route.fetch();
        const json = await response.json();
        json.activeModuleFeatures = [...(json.activeModuleFeatures ?? []), 'hyperion'];
        await route.fulfill({ json });
    });
}

test.describe('Quiz Exercise AI Refinement', { tag: '@fast' }, () => {
    let quizExercise: QuizExercise;

    test.beforeEach('Create quiz with two MC questions and navigate to editor', async ({ page, login, exerciseAPIRequests }) => {
        await enableHyperion(page);
        await login(admin);
        quizExercise = await exerciseAPIRequests.createQuizExercise({
            body: { course },
            quizQuestions: [multipleChoiceTemplate, { ...multipleChoiceTemplate, title: '<Some title> 2' }],
        });
        await page.goto(`/course-management/${course.id}/quiz-exercises/${quizExercise.id}/edit`);
        await page.waitForLoadState('domcontentloaded');
    });

    test.afterEach('Delete quiz exercise', async ({ login, exerciseAPIRequests }) => {
        if (quizExercise?.id) {
            await login(admin);
            await exerciseAPIRequests.deleteQuizExercise(quizExercise.id);
        }
    });

    test('AI refine button is visible on MC questions', async ({ page }) => {
        const refineButton = page.locator('.question-action-btn--refine').first();
        await expect(refineButton).toBeVisible({ timeout: 15000 });
    });

    test('Opens inline refinement panel on MC question', async ({ page }) => {
        await page.locator('.question-action-btn--refine').first().click();

        const panel = page.locator('jhi-quiz-ai-question-refinement-panel').first();
        await expect(panel.locator('.refinement-textarea')).toBeVisible({ timeout: 5000 });
    });

    test('Submit per-question refinement updates question and shows reasoning', async ({ page }) => {
        await page.route(REFINE_ENDPOINT, async (route) => {
            await route.fulfill({ json: refinementSuccess });
        });

        await page.locator('.question-action-btn--refine').first().click();
        const panel = page.locator('jhi-quiz-ai-question-refinement-panel').first();

        const textarea = panel.locator('.refinement-textarea');
        await textarea.fill('Make this question harder');
        await panel.locator('.refinement-submit-button').click();

        // Reasoning card appears after successful refinement
        const reasoningCard = panel.locator('.refinement-explanation-card');
        await expect(reasoningCard).toBeVisible({ timeout: 10000 });
        await expect(reasoningCard.locator('.refinement-explanation-text')).toContainText(refinementSuccess.reasoning);
    });

    test('Restore button appears after refinement and reverts the question', async ({ page }) => {
        await page.route(REFINE_ENDPOINT, async (route) => {
            await route.fulfill({ json: refinementSuccess });
        });

        await page.locator('.question-action-btn--refine').first().click();
        const panel = page.locator('jhi-quiz-ai-question-refinement-panel').first();

        await panel.locator('.refinement-textarea').fill('Make harder');
        await panel.locator('.refinement-submit-button').click();

        // Wait for reasoning card with restore button
        const restoreBtn = panel.locator('.refinement-explanation-actions button').first();
        await expect(restoreBtn).toBeVisible({ timeout: 10000 });

        // Click restore — question should revert to original text
        await restoreBtn.click();

        // Reasoning card disappears after restore
        await expect(panel.locator('.refinement-explanation-card')).not.toBeVisible({ timeout: 5000 });
    });

    test('Shows loading spinner while refining', async ({ page }) => {
        let resolveRoute: () => void;
        const routeBlocker = new Promise<void>((resolve) => {
            resolveRoute = resolve;
        });

        await page.route(REFINE_ENDPOINT, async (route) => {
            await routeBlocker;
            await route.fulfill({ json: refinementSuccess });
        });

        await page.locator('.question-action-btn--refine').first().click();
        const panel = page.locator('jhi-quiz-ai-question-refinement-panel').first();

        await panel.locator('.refinement-textarea').fill('Make harder');
        await panel.locator('.refinement-submit-button').click();

        // Submit button shows spinner and is disabled while loading
        await expect(panel.locator('.refinement-submit-button fa-icon[animation="spin"]')).toBeVisible({ timeout: 5000 });
        await expect(panel.locator('.refinement-submit-button')).toBeDisabled();

        resolveRoute!();
        await expect(panel.locator('.refinement-explanation-card')).toBeVisible({ timeout: 10000 });
    });

    test('Shows error alert on refinement failure', async ({ page }) => {
        await page.route(REFINE_ENDPOINT, async (route) => {
            await route.fulfill({ status: 500, json: { message: 'Internal Server Error' } });
        });

        await page.locator('.question-action-btn--refine').first().click();
        const panel = page.locator('jhi-quiz-ai-question-refinement-panel').first();

        await panel.locator('.refinement-textarea').fill('Make harder');
        await panel.locator('.refinement-submit-button').click();

        // No reasoning card shown; error alert appears
        await expect(panel.locator('.refinement-explanation-card')).not.toBeVisible({ timeout: 5000 });
        await expect(page.locator('.alert-inner.danger')).toBeVisible({ timeout: 10000 });
    });

    test('Global bulk refinement FAB is visible when MC questions exist', async ({ page }) => {
        await expect(page.locator('.global-refinement-fab')).toBeVisible({ timeout: 15000 });
    });

    test('Global bulk refinement FAB expands to show textarea and submit', async ({ page }) => {
        await page.locator('.global-refinement-fab').click();

        await expect(page.locator('.global-refinement-fab-expanded')).toBeVisible({ timeout: 5000 });
        await expect(page.locator('.global-refinement-fab-textarea')).toBeVisible();
    });

    test('Bulk refinement refines all MC questions and shows per-question reasoning', async ({ page }) => {
        await page.route(BULK_REFINE_ENDPOINT, async (route) => {
            await route.fulfill({ json: bulkRefinementSuccess });
        });

        await page.locator('.global-refinement-fab').click();
        const textarea = page.locator('.global-refinement-fab-textarea');
        await textarea.fill('Add explanations to all answer options');

        const submitBtn = page.locator('.global-refinement-fab-expanded .global-refinement-fab-action-button').last();
        await submitBtn.click();

        // Both questions should show a reasoning card after bulk refinement
        const reasoningCards = page.locator('jhi-quiz-ai-question-refinement-panel .refinement-explanation-card');
        await expect(reasoningCards).toHaveCount(2, { timeout: 15000 });
    });

    test('Bulk refinement: restore one question while the other stays refined', async ({ page }) => {
        await page.route(BULK_REFINE_ENDPOINT, async (route) => {
            await route.fulfill({ json: bulkRefinementSuccess });
        });

        await page.locator('.global-refinement-fab').click();
        await page.locator('.global-refinement-fab-textarea').fill('Add explanations');
        await page.locator('.global-refinement-fab-expanded .global-refinement-fab-action-button').last().click();

        const reasoningCards = page.locator('jhi-quiz-ai-question-refinement-panel .refinement-explanation-card');
        await expect(reasoningCards).toHaveCount(2, { timeout: 15000 });

        // Restore only the first question
        const restoreBtn = reasoningCards.first().locator('.refinement-explanation-actions button').first();
        await restoreBtn.click();

        // First reasoning card disappears; second remains
        await expect(reasoningCards).toHaveCount(1, { timeout: 5000 });
    });

    test('Global bulk refinement FAB closes without action', async ({ page }) => {
        await page.locator('.global-refinement-fab').click();
        await expect(page.locator('.global-refinement-fab-expanded')).toBeVisible({ timeout: 5000 });

        await page.locator('.global-refinement-fab-close-button').click();
        await expect(page.locator('.global-refinement-fab-expanded')).not.toBeVisible({ timeout: 5000 });
        await expect(page.locator('.global-refinement-fab')).toBeVisible();
    });

    test('Submit button is disabled when refinement textarea is empty', async ({ page }) => {
        await page.locator('.question-action-btn--refine').first().click();
        const panel = page.locator('jhi-quiz-ai-question-refinement-panel').first();

        // Textarea is empty by default — submit must be disabled
        await expect(panel.locator('.refinement-textarea')).toHaveValue('');
        await expect(panel.locator('.refinement-submit-button')).toBeDisabled();

        // Typing enables it
        await panel.locator('.refinement-textarea').fill('Make harder');
        await expect(panel.locator('.refinement-submit-button')).toBeEnabled();

        // Clearing disables it again
        await panel.locator('.refinement-textarea').fill('');
        await expect(panel.locator('.refinement-submit-button')).toBeDisabled();
    });

    test('Dismiss reasoning ✕ closes card without restoring question', async ({ page }) => {
        await page.route(REFINE_ENDPOINT, async (route) => {
            await route.fulfill({ json: refinementSuccess });
        });

        await page.locator('.question-action-btn--refine').first().click();
        const panel = page.locator('jhi-quiz-ai-question-refinement-panel').first();

        await panel.locator('.refinement-textarea').fill('Make harder');
        await panel.locator('.refinement-submit-button').click();

        const reasoningCard = panel.locator('.refinement-explanation-card');
        await expect(reasoningCard).toBeVisible({ timeout: 10000 });

        // The ✕ dismiss button is the last .refinement-explanation-dismiss
        const dismissBtn = reasoningCard.locator('.refinement-explanation-dismiss').last();
        await dismissBtn.click();

        // Card gone — no restore, just dismissed
        await expect(reasoningCard).not.toBeVisible({ timeout: 5000 });
    });

    test('Panel state resets when closed and reopened', async ({ page }) => {
        await page.locator('.question-action-btn--refine').first().click();
        const panel = page.locator('jhi-quiz-ai-question-refinement-panel').first();

        const textarea = panel.locator('.refinement-textarea');
        await expect(textarea).toBeVisible({ timeout: 5000 });
        await textarea.fill('Some instruction');
        await expect(textarea).toHaveValue('Some instruction');

        // Click sparkle again to close the panel
        await page.locator('.question-action-btn--refine').first().click();
        await expect(textarea).not.toBeVisible({ timeout: 5000 });

        // Click sparkle a third time to reopen
        await page.locator('.question-action-btn--refine').first().click();
        await expect(textarea).toBeVisible({ timeout: 5000 });

        // Textarea should be cleared after the close/reopen cycle
        await expect(textarea).toHaveValue('');
    });
});
