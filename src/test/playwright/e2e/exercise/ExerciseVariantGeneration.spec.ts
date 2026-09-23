import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import multipleChoiceTemplate from '../../fixtures/exercise/quiz/multiple_choice/template.json';
import { admin } from '../../support/users';
import { generateUUID } from '../../support/utils';
import { test } from '../../support/fixtures';
import { expect } from '@playwright/test';
import { SEED_COURSES } from '../../support/seedData';
import { ExerciseVariantAiWizard } from '../../support/pageobjects/exercises/ExerciseVariantAiWizard';

/**
 * E2E tests for AI exercise-variant generation.
 *
 * The whole stack is real (server, client, Hazelcast job map, quiz adapters/toolset, WebSocket) except the
 * LLM: Hyperion's Spring AI OpenAI client is pointed at a deterministic mock LLM
 * (src/test/playwright/support/hyperion-mock-llm/) that returns a canned ChangePlan for PLANNING, a single
 * `finish` tool call for TRANSFORMING (the provisioned clone is already a valid copy, so an unmodified-but-
 * consistent variant passes verification), and an empty-findings critique. The mock names every variant
 * `MOCK_VARIANT_TITLE`, so the test can assert the exact exercise surfaces in the course list.
 *
 * These tests require Hyperion to be enabled on the server. Run them with:
 *     RUN_HYPERION=true ./run-e2e-tests-local-fast.sh --filter "Variant"
 * When Hyperion is NOT enabled (the default), the suite skips itself, so it is a no-op in normal CI runs.
 *
 * Note: one manual multi-node sanity run before the PR (the job map is Hazelcast-backed and the WebSocket
 * event must reach the user regardless of which node runs the job):
 *     RUN_HYPERION=true ./run-e2e-tests-local-multinode-fast.sh --filter "Variant"
 */

const course = { id: SEED_COURSES.exerciseManagement.id } as any;
// Must equal MOCK_LLM_VARIANT_TITLE in src/test/playwright/support/hyperion-mock-llm/mock_llm.py.
const MOCK_VARIANT_TITLE = 'AI Variant - Cargo Bay Inventory';
// Marker that makes the mock delay PLANNING (MOCK_LLM_SLOW_MARKER) so the job stays visibly running.
const SLOW_MARKER = '[slow-e2e]';

interface VariantJobEntry {
    jobId: string;
    sourceExerciseId: number;
    phase: string;
    variantExerciseId?: number;
    variantExerciseTitle?: string;
}

test.describe('Exercise variant generation with AI', { tag: '@fast' }, () => {
    test.beforeAll(async ({ browser }) => {
        // Probe the server's module features; skip the whole suite if Hyperion is not active.
        const probeContext = await browser.newContext();
        const info = await probeContext.request.get('management/info');
        const features: string[] = info.ok() ? ((await info.json())?.activeModuleFeatures ?? []) : [];
        await probeContext.close();
        test.skip(!features.includes('hyperion'), 'Hyperion module feature is not active on the server (run with RUN_HYPERION=true)');
    });

    let sourceQuiz: QuizExercise;
    const createdExerciseIds: number[] = [];

    test.beforeEach(async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        // The API helper derives the channel name from the title and caps it at 20 chars (titleLowercase);
        // keep the prefix short so the unique UUID suffix survives the cap and the two parallel tests can't collide.
        sourceQuiz = await exerciseAPIRequests.createQuizExercise({
            body: { course },
            quizQuestions: [multipleChoiceTemplate],
            title: 'Variant Src ' + generateUUID(),
        });
        expect(sourceQuiz?.id, `source quiz creation failed: ${JSON.stringify(sourceQuiz)}`).toBeTruthy();
        createdExerciseIds.push(sourceQuiz.id!);
    });

    test.afterEach(async ({ login, exerciseAPIRequests }) => {
        await login(admin);
        for (const id of createdExerciseIds.splice(0)) {
            // The generated variant may already be gone on failure paths; ignore best-effort deletions.
            await exerciseAPIRequests.deleteQuizExercise(id).catch(() => undefined);
        }
    });

    /** GET the current user's variant jobs and return the one generated from the given source exercise. */
    async function jobForSource(request: any, sourceExerciseId: number): Promise<VariantJobEntry | undefined> {
        const response = await request.get('api/hyperion/variant-jobs');
        if (!response.ok()) {
            return undefined;
        }
        const jobs: VariantJobEntry[] = await response.json();
        return jobs.find((job) => job.sourceExerciseId === sourceExerciseId);
    }

    /** Poll the variant-jobs endpoint until the job for the source exercise reaches COMPLETED, then return it. */
    async function awaitCompletedJob(request: any, sourceExerciseId: number, timeout = 90_000): Promise<VariantJobEntry> {
        let job: VariantJobEntry | undefined;
        await expect
            .poll(
                async () => {
                    job = await jobForSource(request, sourceExerciseId);
                    return job?.phase;
                },
                { timeout, message: 'variant job did not reach COMPLETED' },
            )
            .toBe('COMPLETED');
        return job!;
    }

    test('generates a standalone quiz variant end to end and lists it in the course', async ({ page, courseManagementExercises }) => {
        await page.goto(`/course-management/${course.id}/exercises`);
        await page.waitForLoadState('domcontentloaded');
        await courseManagementExercises.openCreateVariantWithAi(sourceQuiz.id!);

        const wizard = new ExerciseVariantAiWizard(page);
        await wizard.expectOpen();
        await wizard.selectDomainAdaptationAndContinue();
        await wizard.fillDomainAndContinue('space station cargo bay');
        await wizard.chooseStandaloneAndGenerate();

        await wizard.expectGenerating();
        await wizard.expectResultWithTitle(MOCK_VARIANT_TITLE);

        // The variant was persisted server-side in FINALIZING; find it and assert its row shows in the list.
        const job = await awaitCompletedJob(page.request, sourceQuiz.id!);
        expect(job.variantExerciseId).toBeTruthy();
        expect(job.variantExerciseTitle).toBe(MOCK_VARIANT_TITLE);
        createdExerciseIds.push(job.variantExerciseId!);

        await wizard.close();
        await page.reload();
        await page.waitForLoadState('domcontentloaded');
        const variantCard = courseManagementExercises.getExercise(job.variantExerciseId!);
        await variantCard.scrollIntoViewIfNeeded();
        await expect(variantCard).toBeVisible();
        await expect(variantCard).toContainText(MOCK_VARIANT_TITLE);
    });

    test('keeps the job running after the wizard is closed and tracks it in the navbar tray', async ({ page, courseManagementExercises }) => {
        await page.goto(`/course-management/${course.id}/exercises`);
        await page.waitForLoadState('domcontentloaded');
        await courseManagementExercises.openCreateVariantWithAi(sourceQuiz.id!);

        const wizard = new ExerciseVariantAiWizard(page);
        await wizard.expectOpen();
        await wizard.selectDomainAdaptationAndContinue();
        // The slow marker holds the PLANNING response, so the job is still running when the wizard closes.
        await wizard.fillDomainAndContinue(`space station cargo bay ${SLOW_MARKER}`);
        await wizard.chooseStandaloneAndGenerate();
        await wizard.expectGenerating();

        // Closing the wizard detaches the UI but must not cancel the @Async job.
        await wizard.runInBackground();
        await expect(page.getByTestId('variant-tray-button')).toBeVisible();
        await expect(page.getByTestId('variant-tray-spinner')).toBeVisible();

        // The background job runs to completion on the server; the variant lands in the course list.
        const job = await awaitCompletedJob(page.request, sourceQuiz.id!);
        expect(job.variantExerciseId).toBeTruthy();
        createdExerciseIds.push(job.variantExerciseId!);

        await page.reload();
        await page.waitForLoadState('domcontentloaded');
        const variantCard = courseManagementExercises.getExercise(job.variantExerciseId!);
        await variantCard.scrollIntoViewIfNeeded();
        await expect(variantCard).toBeVisible();
    });
});
