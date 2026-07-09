import dayjs from 'dayjs';
import { Browser, expect, Page } from '@playwright/test';

import { test } from '../../../support/fixtures';
import { Commands } from '../../../support/commands';
import { admin, instructor, UserCredentials } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';
import { ExerciseMode, ProgrammingLanguage } from '../../../support/constants';
import { generateUUID } from '../../../support/utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

const course = { id: SEED_COURSES.programmingManagement.id } as any;

type GenerationRequest = {
    mode?: 'GENERATE' | 'ADAPT';
    prompt?: string;
    selectedFeedbackThreadIds?: number[];
};

test.describe('Hyperion exercise generation browser UI', { tag: '@slow' }, () => {
    test.skip(process.env.HYPERION_LLM_MODE === 'live', 'Mocked Hyperion UI tests must not send mock-control prompts to a live LLM endpoint.');
    test.describe.configure({ mode: 'serial' });
    test.use({ serviceWorkers: 'block' });

    let exercise: ProgrammingExercise | undefined;
    let runningJobId: string | undefined;

    test.beforeEach('Create unreleased Java programming exercise', async ({ login, page, exerciseAPIRequests }) => {
        await skipIfHyperionGenerationUnavailable(page);
        await login(admin);
        exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            programmingLanguage: ProgrammingLanguage.JAVA,
            mode: ExerciseMode.INDIVIDUAL,
            releaseDate: dayjs().add(2, 'days'),
            dueDate: dayjs().add(3, 'days'),
            assessmentDate: dayjs().add(4, 'days'),
            title: `hyperion-ui-${generateUUID()}`,
        });
        expect(exercise.id).toBeDefined();
        runningJobId = undefined;
    });

    test.afterEach('Cancel job and delete programming exercise', async ({ login, page, exerciseAPIRequests }) => {
        if (exercise?.id && runningJobId) {
            await page.request.delete(`api/hyperion/programming-exercises/${exercise.id}/generate-exercise/jobs/${runningJobId}`).catch(() => undefined);
            runningJobId = undefined;
        }
        if (exercise?.id) {
            await login(admin);
            await exerciseAPIRequests.deleteProgrammingExercise(exercise.id);
            exercise = undefined;
        }
    });

    test('starts generation through the real Hyperion backend, exposes admin slot usage, and cancels the running job', async ({ browser, page, login }) => {
        test.setTimeout(180_000);
        await openEditor(page, login, exercise!);

        const { jobId, request } = await startGenerationFromMenu(page, exercise!.id!);
        runningJobId = jobId;

        expect(request).toEqual({ mode: 'GENERATE' });
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible();
        await expect(activity).toContainText('Generating');
        await expect(page.getByTestId('hyperion-ai-menu')).toBeDisabled();
        await expectAdminGenerationSandboxSlots(browser, '2 / 2');

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        await expectAdminGenerationSandboxSlots(browser, '0 / 2');
        runningJobId = undefined;
    });

    test('rehydrates real retained file snapshots after reload', async ({ page, login }) => {
        test.setTimeout(180_000);
        await openEditor(page, login, exercise!);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        await page.getByLabel('Additional instructions').fill('HYPERION_E2E_WRITE_SNAPSHOT: write the deterministic preview file, then keep running.');

        const startResponsePromise = waitForGenerationStart(page, exercise!.id!);
        await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
        const { jobId } = await startResponsePromise;
        runningJobId = jobId;
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible();
        await expect(activity).toContainText('HyperionPreview.java', { timeout: 60_000 });
        await expect(activity).toContainText('retained-preview');

        await page.reload();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
        const rehydratedActivity = page.getByTestId('hyperion-generation-activity');
        await expect(rehydratedActivity).toBeVisible();
        await expect(rehydratedActivity).toContainText('Starting exercise generation');
        await expect(rehydratedActivity).toContainText('HyperionPreview.java');
        await expect(rehydratedActivity).toContainText('retained-preview');

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        runningJobId = undefined;
    });

    test('starts adaptation through the real Hyperion backend with instructor instructions', async ({ page, login }) => {
        test.setTimeout(180_000);
        await openEditor(page, login, exercise!);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        await page.getByLabel('Additional instructions').fill('Make the edge-case requirements explicit and keep the tests deterministic.');

        const startResponsePromise = waitForGenerationStart(page, exercise!.id!);
        await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
        const { jobId, request } = await startResponsePromise;
        runningJobId = jobId;

        expect(request).toEqual({
            mode: 'ADAPT',
            prompt: 'Make the edge-case requirements explicit and keep the tests deterministic.',
        });
        await expect(page.getByTestId('hyperion-generation-activity')).toContainText('Adapting');

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        runningJobId = undefined;
    });

    test('requires nonblank free-adaptation instructions and cancels without starting a job', async ({ page, login }) => {
        await openEditor(page, login, exercise!);
        const unexpectedStart = page
            .waitForRequest((request) => request.method() === 'POST' && request.url().includes(`/api/hyperion/programming-exercises/${exercise!.id}/generate-exercise`), {
                timeout: 1_000,
            })
            .then(() => true)
            .catch(() => false);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        const adaptButton = page.getByRole('button', { name: 'Adapt exercise', exact: true });
        await expect(adaptButton).toBeDisabled();

        await page.getByLabel('Additional instructions').fill('   ');
        await expect(adaptButton).toBeDisabled();

        await page.getByRole('button', { name: 'Cancel', exact: true }).click();
        await expect(page.getByRole('dialog')).toBeHidden();
        await expect(unexpectedStart).resolves.toBe(false);
    });

    test('surfaces external LLM failures through the real Hyperion backend and unlocks the UI', async ({ page, login }) => {
        test.setTimeout(240_000);
        await openEditor(page, login, exercise!);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        await page.getByLabel('Additional instructions').fill('HYPERION_E2E_FAIL_LLM: fail the external model call for this unhappy-path browser test.');

        const startResponsePromise = waitForGenerationStart(page, exercise!.id!);
        await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
        const { jobId, request } = await startResponsePromise;
        runningJobId = jobId;

        expect(request).toEqual({
            mode: 'ADAPT',
            prompt: 'HYPERION_E2E_FAIL_LLM: fail the external model call for this unhappy-path browser test.',
        });

        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toContainText('Model call failed and will not be retried', { timeout: 180_000 });
        await expect(activity).toContainText('The agent loop ended with an error.');
        await expect(page.getByTestId('hyperion-generation-cancel')).toBeHidden();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
        runningJobId = undefined;
    });
});

async function skipIfHyperionGenerationUnavailable(page: Page) {
    const response = await page.request.get('/management/info');
    test.skip(!response.ok(), 'Hyperion generation E2E needs management info to detect active features.');
    const info = await response.json();
    test.skip(!info.activeModuleFeatures?.includes('hyperion'), 'Hyperion generation is not enabled in this E2E environment.');
}

async function openEditor(page: Page, login: (credentials: UserCredentials, url?: string) => Promise<void>, programmingExercise: ProgrammingExercise) {
    const exerciseId = programmingExercise.id;
    const repositoryId = programmingExercise.templateParticipation?.id;
    expect(exerciseId).toBeDefined();
    expect(repositoryId).toBeDefined();
    await login(instructor, `/course-management/${course.id}/programming-exercises/${exerciseId}/code-editor/TEMPLATE/${repositoryId}`);
    await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
}

async function startGenerationFromMenu(page: Page, exerciseId: number) {
    await page.getByTestId('hyperion-ai-menu').click();
    const startResponsePromise = waitForGenerationStart(page, exerciseId);
    await page.getByTestId('hyperion-generate-exercise').click();
    return startResponsePromise;
}

async function waitForGenerationStart(page: Page, exerciseId: number): Promise<{ jobId: string; request: GenerationRequest }> {
    const response = await page.waitForResponse(
        (candidate) => candidate.request().method() === 'POST' && candidate.url().includes(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise`),
        { timeout: 60_000 },
    );
    expect(response.status()).toBe(202);
    const request = response.request().postDataJSON() as GenerationRequest;
    const body = await response.json();
    expect(body.jobId).toBeTruthy();
    return { jobId: body.jobId, request };
}

async function cancelRunningJobFromUi(page: Page, exerciseId: number, jobId: string) {
    const cancelResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'DELETE' && response.url().includes(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/jobs/${jobId}`),
        { timeout: 60_000 },
    );
    await page.getByTestId('hyperion-generation-cancel').click();
    const cancelResponse = await cancelResponsePromise;
    expect(cancelResponse.ok()).toBeTruthy();
    await expect(page.getByTestId('hyperion-generation-activity')).toContainText('Generation was cancelled', { timeout: 60_000 });
    await expect(page.getByTestId('hyperion-generation-cancel')).toBeHidden();
    await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
}

async function expectAdminGenerationSandboxSlots(browser: Browser, expectedSlots: string) {
    const adminPage = await browser.newPage();
    try {
        await Commands.login(adminPage, admin, '/admin/build-agents');
        await expect(adminPage.locator('td.build-agents-column').filter({ hasText: expectedSlots }).first()).toBeVisible({ timeout: 60_000 });
    } finally {
        await adminPage.close();
    }
}
