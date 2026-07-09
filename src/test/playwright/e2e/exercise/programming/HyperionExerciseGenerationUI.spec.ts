import dayjs from 'dayjs';
import { Browser, expect, Page, Request } from '@playwright/test';

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

type GenerationStatus = {
    jobId: string;
    running: boolean;
    mode?: 'GENERATE' | 'ADAPT';
    events: { type: 'STARTED' | 'PROGRESS' | 'DONE' | 'CANCELLED' | 'ERROR'; message?: string }[];
    fileSnapshots?: { path: string; repo: string; action: string; content: string }[];
};

type BuildAgentSlots = {
    reservedGenerationSandboxSlots?: number;
    maxGenerationSandboxSlots?: number;
};

test.describe('Hyperion exercise generation browser UI', { tag: '@slow' }, () => {
    test.skip(process.env.HYPERION_LLM_MODE !== 'mock', 'Mocked Hyperion UI tests require HYPERION_LLM_MODE=mock.');
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
        const initialLlmRequests = await getHyperionLlmMockRequestCount(page);
        await openEditor(page, login, exercise!);

        const { jobId, request } = await startGenerationFromMenu(page, exercise!.id!);
        runningJobId = jobId;

        expect(request).toEqual({ mode: 'GENERATE' });
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible();
        await expect(activity).toContainText('Generating');
        await expect(page.getByTestId('hyperion-ai-menu')).toBeDisabled();
        await expectRunningGenerationStatus(page, exercise!.id!, jobId, 'GENERATE');
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);
        const requestsAfterFirstStart = await getHyperionLlmMockRequestCount(page);
        await expectDuplicateGenerationStartRejectedWithoutNewLlmRequest(page, exercise!.id!, jobId, requestsAfterFirstStart);
        await expectAdminGenerationSandboxSlots(browser, '2 / 2');

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        await expectCancelRejected(page, exercise!.id!, jobId);
        await expectAdminGenerationSandboxSlots(browser, '0 / 2');
        runningJobId = undefined;
    });

    test('rehydrates real retained file snapshots after reload and from a fresh page', async ({ browser, page, login }) => {
        test.setTimeout(180_000);
        const initialLlmRequests = await getHyperionLlmMockRequestCount(page);
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
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);
        await expectFileSnapshotStatus(page, exercise!.id!, jobId);
        await expectRehydratedActivityOnFreshPage(browser, exercise!);

        await page.reload();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
        const rehydratedActivity = page.getByTestId('hyperion-generation-activity');
        await expect(rehydratedActivity).toBeVisible();
        await expect(rehydratedActivity).toContainText('Starting exercise generation');
        await expect(rehydratedActivity).toContainText('HyperionPreview.java');
        await expect(rehydratedActivity).toContainText('retained-preview');
        await expectFileSnapshotStatus(page, exercise!.id!, jobId);

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        runningJobId = undefined;
    });

    test('starts adaptation through the real Hyperion backend with instructor instructions', async ({ page, login }) => {
        test.setTimeout(180_000);
        const initialLlmRequests = await getHyperionLlmMockRequestCount(page);
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
        await expectRunningGenerationStatus(page, exercise!.id!, jobId, 'ADAPT');
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        runningJobId = undefined;
    });

    test('requires nonblank free-adaptation instructions and cancels without starting a job', async ({ page, login }) => {
        const initialLlmRequests = await getHyperionLlmMockRequestCount(page);
        await openEditor(page, login, exercise!);
        const startRequests: string[] = [];
        const recordStartRequest = (request: Request) => {
            if (request.method() === 'POST' && request.url().includes(`/api/hyperion/programming-exercises/${exercise!.id}/generate-exercise`)) {
                startRequests.push(request.url());
            }
        };
        page.on('request', recordStartRequest);

        try {
            await page.getByTestId('hyperion-ai-menu').click();
            await page.getByTestId('hyperion-adapt-with-feedback').click();
            const adaptButton = page.getByRole('button', { name: 'Adapt exercise', exact: true });
            await expect(adaptButton).toBeDisabled();

            await page.getByLabel('Additional instructions').fill('   ');
            await expect(adaptButton).toBeDisabled();

            await page.getByRole('button', { name: 'Cancel', exact: true }).click();
            await expect(page.getByRole('dialog')).toBeHidden();
            expect(startRequests).toHaveLength(0);
            await expectNoRetainedGenerationStatus(page, exercise!.id!);
            await expectHyperionLlmMockRequestCount(page, initialLlmRequests);
        } finally {
            page.off('request', recordStartRequest);
        }
    });

    test('surfaces external LLM failures through the real Hyperion backend and unlocks the UI', async ({ browser, page, login }) => {
        test.setTimeout(240_000);
        const initialLlmRequests = await getHyperionLlmMockRequestCount(page);
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
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);
        await expectTerminalGenerationStatus(page, exercise!.id!, jobId, 'ERROR');
        await expectAdminGenerationSandboxSlots(browser, '0 / 2');
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

async function getGenerationStatus(page: Page, exerciseId: number): Promise<GenerationStatus> {
    const response = await page.request.get(`api/hyperion/programming-exercises/${exerciseId}/generate-exercise/status`);
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as GenerationStatus;
}

async function expectNoRetainedGenerationStatus(page: Page, exerciseId: number) {
    const response = await page.request.get(`api/hyperion/programming-exercises/${exerciseId}/generate-exercise/status`);
    expect(response.status()).toBe(204);
}

async function expectRunningGenerationStatus(page: Page, exerciseId: number, jobId: string, mode: 'GENERATE' | 'ADAPT') {
    await expect
        .poll(async () => {
            const status = await getGenerationStatus(page, exerciseId);
            return {
                jobId: status.jobId,
                mode: status.mode,
                running: status.running,
                hasStarted: status.events.some((event) => event.type === 'STARTED'),
            };
        })
        .toEqual({ jobId, mode, running: true, hasStarted: true });
}

async function expectFileSnapshotStatus(page: Page, exerciseId: number, jobId: string) {
    await expect
        .poll(async () => {
            const status = await getGenerationStatus(page, exerciseId);
            const snapshot = status.fileSnapshots?.find((candidate) => candidate.path.endsWith('HyperionPreview.java'));
            return {
                jobId: status.jobId,
                mode: status.mode,
                path: snapshot?.path,
                repo: snapshot?.repo,
                action: snapshot?.action,
                hasContent: snapshot?.content.includes('retained-preview') ?? false,
            };
        })
        .toEqual({
            jobId,
            mode: 'ADAPT',
            path: 'solution/src/de/test/HyperionPreview.java',
            repo: 'solution',
            action: 'create',
            hasContent: true,
        });
}

async function expectTerminalGenerationStatus(page: Page, exerciseId: number, jobId: string, terminalType: 'CANCELLED' | 'ERROR') {
    await expect
        .poll(async () => {
            const status = await getGenerationStatus(page, exerciseId);
            return {
                jobId: status.jobId,
                running: status.running,
                terminalType: [...status.events].reverse().find((event) => event.type === terminalType)?.type,
            };
        })
        .toEqual({ jobId, running: false, terminalType });
}

async function expectDuplicateGenerationStartRejectedWithoutNewLlmRequest(page: Page, exerciseId: number, runningJobId: string, expectedLlmRequestCount: number) {
    const duplicateStart = await page.request.post(`api/hyperion/programming-exercises/${exerciseId}/generate-exercise`, {
        data: { mode: 'GENERATE' },
    });

    expect(duplicateStart.status()).toBe(409);
    await expectRunningGenerationStatus(page, exerciseId, runningJobId, 'GENERATE');
    await expectHyperionLlmMockRequestCount(page, expectedLlmRequestCount);
}

async function getHyperionLlmMockRequestCount(page: Page): Promise<number> {
    const port = process.env.HYPERION_LLM_MOCK_PORT ?? '1234';
    const response = await page.request.get(`http://127.0.0.1:${port}/health`);
    expect(response.ok()).toBeTruthy();
    const health = (await response.json()) as { requestCount?: number };
    expect(typeof health.requestCount).toBe('number');
    return health.requestCount!;
}

async function expectHyperionLlmMockRequestCount(page: Page, expectedCount: number) {
    expect(await getHyperionLlmMockRequestCount(page)).toBe(expectedCount);
}

async function expectHyperionLlmMockRequestsIncreased(page: Page, previousCount: number) {
    await expect.poll(async () => getHyperionLlmMockRequestCount(page), { timeout: 60_000 }).toBeGreaterThan(previousCount);
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
    await expectTerminalGenerationStatus(page, exerciseId, jobId, 'CANCELLED');
}

async function expectCancelRejected(page: Page, exerciseId: number, jobId: string) {
    await expect
        .poll(
            async () => {
                const secondCancel = await page.request.delete(`api/hyperion/programming-exercises/${exerciseId}/generate-exercise/jobs/${jobId}`);
                return secondCancel.status();
            },
            { timeout: 60_000 },
        )
        .toBe(404);
}

async function expectAdminGenerationSandboxSlots(browser: Browser, expectedSlots: string) {
    const adminPage = await browser.newPage();
    const [expectedReservedSlots, expectedMaxSlots] = expectedSlots.split(' / ').map(Number);
    try {
        await Commands.login(adminPage, admin, '/admin/build-agents');
        await expect
            .poll(
                async () => {
                    const response = await adminPage.request.get('api/admin/build-agents');
                    if (!response.ok()) {
                        return false;
                    }
                    const agents = (await response.json()) as BuildAgentSlots[];
                    return agents.some(
                        (agent) => (agent.reservedGenerationSandboxSlots ?? 0) === expectedReservedSlots && (agent.maxGenerationSandboxSlots ?? 0) === expectedMaxSlots,
                    );
                },
                { timeout: 60_000 },
            )
            .toBeTruthy();
        await expect(adminPage.getByRole('columnheader', { name: /Hyperion sandbox slots/i })).toBeVisible({ timeout: 60_000 });
        await expect(adminPage.locator('td.build-agents-column').filter({ hasText: expectedSlots }).first()).toBeVisible({ timeout: 60_000 });
    } finally {
        await adminPage.close();
    }
}

async function expectRehydratedActivityOnFreshPage(browser: Browser, programmingExercise: ProgrammingExercise) {
    const exerciseId = programmingExercise.id;
    const repositoryId = programmingExercise.templateParticipation?.id;
    expect(exerciseId).toBeDefined();
    expect(repositoryId).toBeDefined();
    const freshPage = await browser.newPage();
    try {
        await Commands.login(freshPage, instructor, `/course-management/${course.id}/programming-exercises/${exerciseId}/code-editor/TEMPLATE/${repositoryId}`);
        const activity = freshPage.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible({ timeout: 60_000 });
        await expect(activity).toContainText('HyperionPreview.java');
        await expect(activity).toContainText('retained-preview');
        await expect(freshPage.getByTestId('hyperion-ai-menu')).toBeDisabled();
        await expect(freshPage.getByTestId('hyperion-generation-cancel')).toBeVisible();
    } finally {
        await freshPage.close();
    }
}
