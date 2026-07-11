import dayjs from 'dayjs';
import { Browser, expect, Locator, Page, Request } from '@playwright/test';

import { test } from '../../../support/fixtures';
import { Commands } from '../../../support/commands';
import { admin, instructor, UserCredentials } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';
import { ExerciseMode, ProgrammingLanguage } from '../../../support/constants';
import { generateUUID } from '../../../support/utils';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import javaProgrammingExerciseTemplate from '../../../fixtures/exercise/programming/java/template.json';

const course = { id: SEED_COURSES.programmingManagement.id } as any;

type GenerationRequest = {
    mode?: 'GENERATE' | 'ADAPT';
    prompt?: string;
    selectedFeedbackThreadIds?: number[];
};

type GenerationStatus = {
    jobId: string;
    running: boolean;
    revertAvailable: boolean;
    mode?: 'GENERATE' | 'ADAPT';
    events: {
        type: 'STARTED' | 'PROGRESS' | 'DONE' | 'CANCELLED' | 'ERROR';
        message?: string;
        completionStatus?: 'SUCCESS' | 'NEEDS_REVIEW' | 'PARTIAL';
        verdict?: { accepted?: boolean; solutionPassed?: boolean; templateFailed?: boolean; testCount?: number };
        liveExerciseChanged?: boolean;
    }[];
    fileSnapshots?: { path: string; repo: string; action: string; content: string }[];
};

type BuildAgentSlots = {
    reservedGenerationSandboxSlots?: number;
    maxGenerationSandboxSlots?: number;
};

type LlmMockRequestSummary = {
    model?: string;
    messageCount?: number;
    roles?: string[];
    promptText?: string;
    toolNames?: string[];
    hasWriteFileTool?: boolean;
    hasBashTool?: boolean;
    hasSubmitTool?: boolean;
};

const correctedSeedStatementMarker = 'Use merge sort for big lists';
const solutionMarkerPath = 'hyperion-e2e-solution-marker.txt';
const templateMarkerPath = 'hyperion-e2e-template-marker.txt';
const testsMarkerPath = 'hyperion-e2e-tests-marker.txt';
const solutionMarkerText = 'hyperion-e2e-solution-marker';
const templateMarkerText = 'hyperion-e2e-template-marker';
const testsMarkerText = 'hyperion-e2e-tests-marker';

const verifierSafeJavaProblemStatement = javaProgrammingExerciseTemplate.problemStatement
    .replace('(testClass[MergeSort],testUseMergeSortForBigList)', '(testUseMergeSortForBigList)')
    .replace('(testClass[BubbleSort],testUseBubbleSortForSmallList)', '(testUseBubbleSortForSmallList)');

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
            problemStatement: verifierSafeJavaProblemStatement,
            title: `hyperion-ui-${generateUUID()}`,
        });
        expect(exercise.id).toBeDefined();
        await exerciseAPIRequests.waitForSolutionBuild(exercise.id!);
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
        await page.setViewportSize({ width: 1024, height: 768 });
        const initialLlmRequests = await getHyperionLlmMockRequestCount(page);
        await openEditor(page, login, exercise!);

        const { jobId, request } = await startGenerationFromMenu(page, exercise!.id!);
        runningJobId = jobId;

        expect(request).toEqual({ mode: 'GENERATE' });
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible();
        await expect(activity.getByTestId('hyperion-generation-persistence-state')).toContainText('Agent working copy — not saved');
        await expectNativeEditorIntegration(page);
        await expectHyperionTabSelected(page);
        await exerciseBottomPanelTabsWithKeyboard(page);
        await expectBottomPanelReachableAtNarrowWidth(page);
        await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
        await expectEditorActionsLockedDuringGeneration(page);
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
        await expectSnapshotNavigationDisabled(page, 'HyperionPreview.java');
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);
        await expectFileSnapshotStatus(page, exercise!.id!, jobId);
        await expectRehydratedActivityOnFreshPage(browser, exercise!);

        await page.reload();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
        await openHyperionTabWithKeyboard(page);
        await expectHyperionTabSelected(page);
        const rehydratedActivity = page.getByTestId('hyperion-generation-activity');
        await expect(rehydratedActivity).toBeVisible();
        await expect(rehydratedActivity).toContainText('Starting exercise generation');
        await expect(rehydratedActivity).toContainText('HyperionPreview.java');
        await expectSnapshotNavigationDisabled(page, 'HyperionPreview.java');
        await expectFileSnapshotStatus(page, exercise!.id!, jobId);

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        await expectSnapshotNavigationDisabled(page, 'HyperionPreview.java');
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
        await expect(page.getByTestId('hyperion-generation-activity')).toContainText('Adaptation activity');
        await expect(page.getByTestId('hyperion-generation-persistence-state')).toContainText('Agent working copy — not saved');
        await expectRunningGenerationStatus(page, exercise!.id!, jobId, 'ADAPT');
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);
        await expectLlmMockSawPrompt(page, 'Make the edge-case requirements explicit and keep the tests deterministic.');

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        runningJobId = undefined;
    });

    test('completes a mocked accepted adaptation through the browser and real verifier', async ({ browser, page, login }) => {
        test.setTimeout(300_000);
        const initialLlmRequests = await getHyperionLlmMockRequestCount(page);
        await openEditor(page, login, exercise!);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        await page.getByLabel('Additional instructions').fill('HYPERION_E2E_SUBMIT_SEEDED_EXERCISE: fix task bindings in the seeded Java exercise and submit it.');

        const startResponsePromise = waitForGenerationStart(page, exercise!.id!);
        await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
        const { jobId, request } = await startResponsePromise;
        runningJobId = jobId;

        expect(request).toEqual({
            mode: 'ADAPT',
            prompt: 'HYPERION_E2E_SUBMIT_SEEDED_EXERCISE: fix task bindings in the seeded Java exercise and submit it.',
        });
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toContainText('Checking the exercise builds and grades', { timeout: 180_000 });
        const buildOutputTab = page.getByRole('tab', { name: 'Build Output' });
        await selectTabWithKeyboard(page.getByRole('tab', { name: 'AI activity' }), buildOutputTab, 'ArrowLeft');
        await expectSuccessfulGenerationStatus(page, exercise!.id!, jobId, 'ADAPT');
        await expect(buildOutputTab).toHaveAttribute('aria-selected', 'true');
        await expect(buildOutputTab).toBeFocused();

        const hyperionTab = page.getByRole('tab', { name: 'AI activity' });
        await selectTabWithKeyboard(buildOutputTab, hyperionTab, 'ArrowRight');
        await expect(activity).toContainText('The exercise was adapted and saved', { timeout: 60_000 });
        await expect(page.getByTestId('hyperion-generation-verdict')).toBeVisible();
        await expect(page.getByTestId('hyperion-generation-cancel')).toBeHidden();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);
        await openPersistedChangedFileInNativeEditor(page, solutionMarkerPath, solutionMarkerText);
        await expectExerciseProblemStatement(page, exercise!.id!, correctedSeedStatementMarker);
        await expectAdaptationRepositoryMarkers(page, exercise!.id!, true);
        await expectLlmMockSawPrompt(page, 'HYPERION_E2E_SUBMIT_SEEDED_EXERCISE');
        await expectAdminGenerationSandboxSlots(browser, '0 / 2');

        await revertAcceptedAdaptationFromUi(page, exercise!.id!);
        await expectExerciseProblemStatement(page, exercise!.id!, 'testUseMergeSortForBigList');
        await expectAdaptationRepositoryMarkers(page, exercise!.id!, false);
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
        await expect(activity).toContainText('The agent loop ended with an error.', { timeout: 180_000 });
        await expect(activity.getByTestId('hyperion-generation-persistence-state')).toContainText('Not saved — failed');
        await expect(activity.getByTestId('hyperion-generation-file').getByRole('button')).toHaveCount(0);
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
    await expect(page.locator('.editor-wrapper')).toBeVisible();
}

async function expectNativeEditorIntegration(page: Page) {
    const workspace = page.locator('.editor-wrapper');
    const center = workspace.locator('.editor-center');
    const bottom = workspace.locator('.editor-bottom');
    await expect(center).toBeVisible();
    await expect(bottom).toBeVisible();
    await expect(workspace.locator('jhi-code-editor-monaco:visible')).toHaveCount(1);
    await expect(page.getByTestId('hyperion-generation-activity').locator('jhi-monaco-editor')).toHaveCount(0);

    const centerBox = await center.boundingBox();
    const bottomBox = await bottom.boundingBox();
    expect(centerBox).not.toBeNull();
    expect(bottomBox).not.toBeNull();
    expect(centerBox!.y + centerBox!.height).toBeLessThanOrEqual(bottomBox!.y + 1);
    expect(bottomBox!.y + bottomBox!.height).toBeLessThanOrEqual(page.viewportSize()!.height);
}

async function expectHyperionTabSelected(page: Page) {
    await expect(page.getByTestId('editor-bottom-panel-tab')).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByTestId('editor-bottom-panel')).toBeVisible();
}

async function exerciseBottomPanelTabsWithKeyboard(page: Page) {
    const hyperionTab = page.getByRole('tab', { name: 'AI activity' });
    const buildOutputTab = page.getByRole('tab', { name: 'Build Output' });
    await selectTabWithKeyboard(hyperionTab, buildOutputTab, 'ArrowLeft');
    await selectTabWithKeyboard(buildOutputTab, hyperionTab, 'ArrowRight');

    const collapseButton = page.getByTestId('code-editor-bottom-collapse');
    await collapseButton.click();
    await expect(collapseButton).toHaveAttribute('aria-expanded', 'false');
    await hyperionTab.click();
    await expect(collapseButton).toHaveAttribute('aria-expanded', 'true');

    const resizeHandle = page.getByRole('slider', { name: 'Resize bottom panel' });
    await resizeHandle.focus();
    await expect(resizeHandle).toBeFocused();
    await resizeHandle.press('Home');
    const minimumHeight = await resizeHandle.getAttribute('aria-valuemin');
    const maximumHeight = await resizeHandle.getAttribute('aria-valuemax');
    expect(minimumHeight).not.toBeNull();
    expect(maximumHeight).not.toBeNull();
    expect(Number(maximumHeight)).toBeGreaterThan(Number(minimumHeight));
    await expect(resizeHandle).toHaveAttribute('aria-valuenow', minimumHeight!);

    const bottomPanel = page.locator('.editor-bottom');
    const minimumBox = await bottomPanel.boundingBox();
    await resizeHandle.press('ArrowUp');
    const enlargedBox = await bottomPanel.boundingBox();
    expect(enlargedBox!.height).toBeGreaterThan(minimumBox!.height);
}

async function expectBottomPanelReachableAtNarrowWidth(page: Page) {
    await page.setViewportSize({ width: 320, height: 768 });
    const controls = [page.getByRole('tab', { name: 'AI activity' }), page.getByTestId('code-editor-bottom-collapse'), page.getByTestId('hyperion-generation-live-status')];
    for (const control of controls) {
        await expect(control).toBeVisible();
        const box = await control.boundingBox();
        expect(box!.x).toBeGreaterThanOrEqual(0);
        expect(box!.x + box!.width).toBeLessThanOrEqual(320);
    }
    const pageWidths = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, client: document.documentElement.clientWidth }));
    expect(pageWidths.scroll).toBe(pageWidths.client);
    await page.setViewportSize({ width: 1024, height: 768 });
}

async function openHyperionTabWithKeyboard(page: Page) {
    await selectTabWithKeyboard(page.getByRole('tab', { name: 'Build Output' }), page.getByRole('tab', { name: 'AI activity' }), 'ArrowRight');
}

async function selectTabWithKeyboard(currentTab: Locator, targetTab: Locator, arrow: 'ArrowLeft' | 'ArrowRight') {
    await currentTab.focus();
    await currentTab.press(arrow);
    await expect(targetTab).toBeFocused();
    await targetTab.press('Enter');
    await expect(targetTab).toHaveAttribute('aria-selected', 'true');
}

async function expectSnapshotNavigationDisabled(page: Page, fileName: string) {
    const activity = page.getByTestId('hyperion-generation-activity');
    const fileRow = activity.getByTestId('hyperion-generation-file-static').filter({ hasText: fileName });
    if (!(await fileRow.isVisible())) {
        const detailsToggle = activity.getByTestId('hyperion-generation-details-toggle');
        if (await detailsToggle.isVisible()) {
            await detailsToggle.click();
        }
    }
    await expect(fileRow).toBeVisible();
    await expect(activity.getByRole('button', { name: fileName })).toHaveCount(0);
}

async function openPersistedChangedFileInNativeEditor(page: Page, fileName: string, expectedContent: string) {
    const activity = page.getByTestId('hyperion-generation-activity');
    const fileButton = activity.getByRole('button', { name: fileName });
    if (!(await fileButton.isVisible())) {
        await activity.getByTestId('hyperion-generation-details-toggle').click();
    }
    await expect(fileButton).toBeEnabled();
    await fileButton.click();
    await expect(page).toHaveURL(/\/code-editor\/SOLUTION\//);
    await expect(page.locator('jhi-code-editor-monaco jhi-code-editor-header')).toContainText(fileName);
    await expect(page.locator('jhi-code-editor-monaco:visible')).toHaveCount(1);
    await expect(page.locator('jhi-code-editor-monaco .view-lines')).toContainText(expectedContent);
    await expect(page.getByText('Loading file failed.')).toHaveCount(0);
    await expect(page.getByText('The repository status could not be retrieved.')).toHaveCount(0);
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

async function expectSuccessfulGenerationStatus(page: Page, exerciseId: number, jobId: string, mode: 'GENERATE' | 'ADAPT') {
    await expect
        .poll(
            async () => {
                const status = await getGenerationStatus(page, exerciseId);
                const terminal = [...status.events].reverse().find((event) => event.type === 'DONE');
                return {
                    jobId: status.jobId,
                    mode: status.mode,
                    running: status.running,
                    completionStatus: terminal?.completionStatus,
                    accepted: terminal?.verdict?.accepted,
                    solutionPassed: terminal?.verdict?.solutionPassed,
                    templateFailed: terminal?.verdict?.templateFailed,
                    testCount: terminal?.verdict?.testCount,
                    revertAvailable: status.revertAvailable,
                };
            },
            { timeout: 60_000 },
        )
        .toEqual({
            jobId,
            mode,
            running: false,
            completionStatus: 'SUCCESS',
            accepted: true,
            solutionPassed: true,
            templateFailed: true,
            testCount: 13,
            revertAvailable: mode === 'ADAPT',
        });
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

async function getHyperionLlmMockRequests(page: Page): Promise<LlmMockRequestSummary[]> {
    const port = process.env.HYPERION_LLM_MOCK_PORT ?? '1234';
    const response = await page.request.get(`http://127.0.0.1:${port}/requests`);
    expect(response.ok()).toBeTruthy();
    const body = (await response.json()) as { requests?: LlmMockRequestSummary[] };
    expect(Array.isArray(body.requests)).toBeTruthy();
    return body.requests!;
}

async function expectLlmMockSawPrompt(page: Page, expectedPromptSnippet: string) {
    await expect
        .poll(async () => {
            const requests = await getHyperionLlmMockRequests(page);
            const matching = requests.find((request) => request.promptText?.includes(expectedPromptSnippet));
            return matching
                ? {
                      hasPrompt: true,
                      hasMessages: (matching.messageCount ?? 0) > 0,
                      hasUserRole: matching.roles?.includes('user') ?? false,
                      hasWriteFileTool: matching.hasWriteFileTool,
                      hasBashTool: matching.hasBashTool,
                      hasSubmitTool: matching.hasSubmitTool,
                  }
                : { hasPrompt: false };
        })
        .toEqual({ hasPrompt: true, hasMessages: true, hasUserRole: true, hasWriteFileTool: true, hasBashTool: true, hasSubmitTool: true });
}

async function expectEditorActionsLockedDuringGeneration(page: Page) {
    await expect(page.locator('#submit_button')).toBeDisabled();
    await expect(page.locator('#refresh_button')).toBeDisabled();
}

async function expectExerciseProblemStatement(page: Page, exerciseId: number, expectedSnippet: string) {
    await expect
        .poll(
            async () => {
                const response = await page.request.get(`api/programming/programming-exercises/${exerciseId}`);
                if (!response.ok()) {
                    return false;
                }
                const body = (await response.json()) as { problemStatement?: string };
                return body.problemStatement?.includes(expectedSnippet) ?? false;
            },
            { timeout: 60_000 },
        )
        .toBeTruthy();
}

async function expectAdaptationRepositoryMarkers(page: Page, exerciseId: number, shouldExist: boolean) {
    await expect
        .poll(
            async () => ({
                solution: await repositoryFileContains(
                    page,
                    `api/programming/programming-exercises/${exerciseId}/solution-files-content?omitBinaries=true`,
                    solutionMarkerPath,
                    solutionMarkerText,
                ),
                template: await repositoryFileContains(
                    page,
                    `api/programming/programming-exercises/${exerciseId}/template-files-content?omitBinaries=true`,
                    templateMarkerPath,
                    templateMarkerText,
                ),
                tests: await testRepositoryFileContains(page, exerciseId, testsMarkerPath, testsMarkerText),
            }),
            { timeout: 90_000 },
        )
        .toEqual({ solution: shouldExist, template: shouldExist, tests: shouldExist });
}

async function repositoryFileContains(page: Page, endpoint: string, path: string, expectedContent: string): Promise<boolean> {
    const response = await page.request.get(endpoint);
    if (!response.ok()) {
        return false;
    }
    const files = (await response.json()) as Record<string, string>;
    return files[path]?.includes(expectedContent) ?? false;
}

async function testRepositoryFileContains(page: Page, exerciseId: number, path: string, expectedContent: string): Promise<boolean> {
    const response = await page.request.get(`api/programming/programming-exercises/${exerciseId}/test-repository/file?file=${encodeURIComponent(path)}`);
    if (!response.ok()) {
        return false;
    }
    return (await response.text()).includes(expectedContent);
}

async function revertAcceptedAdaptationFromUi(page: Page, exerciseId: number) {
    await expect(page.getByTestId('hyperion-generation-revert')).toBeVisible({ timeout: 60_000 });
    let revertRequests = 0;
    const countRevertRequest = (request: Request) => {
        if (request.method() === 'POST' && request.url().includes(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/revert-adaptation`)) {
            revertRequests++;
        }
    };
    page.on('request', countRevertRequest);
    await page.getByTestId('hyperion-generation-revert').click();
    const initialDialog = page.getByRole('alertdialog', { name: 'Undo the most recent successful adaptation?' });
    const cancel = initialDialog.getByRole('button', { name: 'Cancel', exact: true });
    await expect(cancel).toBeFocused();
    await cancel.click();
    await expect(initialDialog).toBeHidden();
    expect(revertRequests).toBe(0);
    page.off('request', countRevertRequest);

    const revertResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().includes(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/revert-adaptation`),
        { timeout: 120_000 },
    );
    await page.getByTestId('hyperion-generation-revert').click();
    await page.getByRole('alertdialog', { name: 'Undo the most recent successful adaptation?' }).getByRole('button', { name: 'Undo adaptation', exact: true }).click();
    const revertResponse = await revertResponsePromise;
    expect(revertResponse.ok()).toBeTruthy();
    await expect(page.getByTestId('hyperion-generation-reverted')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('hyperion-generation-revert')).toBeHidden();
    await expectNoRetainedGenerationStatus(page, exerciseId);
    await page.reload();
    await expect(page.getByTestId('hyperion-generation-activity')).toBeHidden();
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
        await expect(freshPage.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
        await openHyperionTabWithKeyboard(freshPage);
        await expectHyperionTabSelected(freshPage);
        const activity = freshPage.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible({ timeout: 60_000 });
        await expect(activity).toContainText('HyperionPreview.java');
        await expectNativeEditorIntegration(freshPage);
        await expectSnapshotNavigationDisabled(freshPage, 'HyperionPreview.java');
        await expect(freshPage.getByTestId('hyperion-ai-menu')).toBeEnabled();
        await expect(freshPage.getByTestId('hyperion-generation-cancel')).toBeVisible();
    } finally {
        await freshPage.close();
    }
}
