import dayjs from 'dayjs';
import { Browser, expect, Locator, Page, Request } from '@playwright/test';

import { test } from '../../../support/fixtures';
import { Commands } from '../../../support/commands';
import { admin, instructor, UserCredentials } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';
import { ExerciseMode, ProgrammingLanguage } from '../../../support/constants';
import { fillDateTimePicker, generateUUID, newBrowserPage, readResponseJson } from '../../../support/utils';
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
    ownedByCaller: boolean;
    cancellable: boolean;
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
    buildAgent?: {
        name?: string;
        memberAddress?: string;
    };
    reservedGenerationSandboxSlots?: number;
    maxGenerationSandboxSlots?: number;
};

type LlmMockRequestSummary = {
    messageCount?: number;
    roles?: string[];
    promptText?: string;
    toolNames?: string[];
    acknowledgedToolNames?: string[];
    responseToolName?: string;
};

const correctedSeedStatementMarker = 'more than 5 dates';
const policyPath = 'src/de/test/Policy.java';
const policyThresholdBeforeAdaptation = 'DATES_SIZE_THRESHOLD = 10';
const policyThresholdAfterAdaptation = 'DATES_SIZE_THRESHOLD = 5';
const sortingTestPath = 'test/de/test/SortingExampleBehaviorTest.java';
const sortingTestBeforeAdaptation = 'for (int i = 0; i < 11; i++)';
const sortingTestAfterAdaptation = 'for (int i = 0; i < 6; i++)';
const sortingTestMessageBeforeAdaptation = 'more than 10 dates';
const sortingTestMessageAfterAdaptation = 'more than 5 dates';
const boundaryTestBeforeAdaptation = 'for (int i = 0; i < 3; i++)';
const boundaryTestAfterAdaptation = 'for (int i = 0; i < 5; i++)';
const boundaryTestMessageBeforeAdaptation = 'less or equal than 10 dates';
const boundaryTestMessageAfterAdaptation = 'less or equal than 5 dates';
const criticPromptMarker = 'reviewer for a generated programming exercise';

const verifierSafeJavaProblemStatement = javaProgrammingExerciseTemplate.problemStatement
    .replace('(testClass[MergeSort],testUseMergeSortForBigList)', '(testUseMergeSortForBigList)')
    .replace('(testClass[BubbleSort],testUseBubbleSortForSmallList)', '(testUseBubbleSortForSmallList)');

test.describe('Hyperion exercise generation browser UI', { tag: '@slow' }, () => {
    test.skip(process.env.HYPERION_LLM_MODE !== 'mock', 'Mocked Hyperion UI tests require HYPERION_LLM_MODE=mock.');
    // The suite deliberately validates a one-slot agent and a shared provider request counter.
    test.describe.configure({ mode: 'serial' });
    test.use({ serviceWorkers: 'block' });

    let exercise: ProgrammingExercise | undefined;
    let runningJobId: string | undefined;

    test.beforeEach('Create unreleased Java programming exercise', async ({ login, page, exerciseAPIRequests }) => {
        await requireHyperionGenerationAvailable(page);
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
            const cancelResponse = await page.request.delete(`api/hyperion/programming-exercises/${exercise.id}/generate-exercise/jobs/${runningJobId}`);
            expect([200, 404]).toContain(cancelResponse.status());
            await expect
                .poll(
                    async () => {
                        const statusResponse = await page.request.get(`api/hyperion/programming-exercises/${exercise!.id}/generate-exercise/status`);
                        return statusResponse.status() === 204 || !((await statusResponse.json()) as GenerationStatus).running;
                    },
                    { timeout: 60_000 },
                )
                .toBe(true);
            runningJobId = undefined;
        }
        if (exercise?.id) {
            await login(admin);
            await exerciseAPIRequests.deleteProgrammingExercise(exercise.id);
            exercise = undefined;
        }
    });

    test('drafts a problem statement and starts generation from the programming exercise form', async ({ page, login, exerciseAPIRequests, programmingExerciseCreation }) => {
        test.setTimeout(180_000);
        await exerciseAPIRequests.deleteProgrammingExercise(exercise!.id!);
        exercise = undefined;

        await login(instructor, `/course-management/${course.id}/programming-exercises/new`);
        await programmingExerciseCreation.waitForFormToLoad();
        await programmingExerciseCreation.changeEditMode();
        await programmingExerciseCreation.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        await programmingExerciseCreation.setPackageName('de.tum.cit.aet.temperature');
        await programmingExerciseCreation.setTitle(`hyperion-form-${generateUUID()}`);
        await programmingExerciseCreation.setShortName(`hyperionform${generateUUID()}`);
        await programmingExerciseCreation.setPoints(10);
        await page.locator('#field_bonusPoints').fill('0');
        await fillDateTimePicker(page.getByLabel('Release Date', { exact: true }), dayjs().add(2, 'days'));
        await programmingExerciseCreation.setDueDate(dayjs().add(3, 'days'));

        await page.locator('#userPrompt').fill('Classify temperatures with clear boundaries and all-or-nothing validation. Do not prescribe class or method names.');
        const draftResponsePromise = page.waitForResponse(
            (response) => response.request().method() === 'POST' && response.url().includes(`/api/hyperion/courses/${course.id}/problem-statements/generate`),
        );
        await page.getByRole('button', { name: 'Generate Draft Problem Statement' }).click();
        const draftResponse = await draftResponsePromise;
        expect(draftResponse.ok()).toBeTruthy();
        const draft = await readResponseJson<{ draftProblemStatement?: string }>(draftResponse);
        expect(draft.draftProblemStatement).toContain('# Temperature Alert Classification');
        await expect(page.getByText('Problem statement has been successfully generated.')).toBeVisible();

        const setupResponsePromise = page.waitForResponse(
            (response) => response.request().method() === 'POST' && response.url().includes('/api/programming/programming-exercises/setup?emptyRepositories=true'),
        );
        const startResponsePromise = page.waitForResponse(
            (response) => response.request().method() === 'POST' && /\/api\/hyperion\/programming-exercises\/\d+\/generate-exercise$/.test(response.url()),
        );
        await page.locator('#generate-with-ai').click();
        const setupResponse = await setupResponsePromise;
        expect(setupResponse.ok()).toBeTruthy();
        exercise = await readResponseJson<ProgrammingExercise>(setupResponse);
        expect(exercise.id).toBeDefined();
        await expect(page).toHaveURL(new RegExp(`/programming-exercises/${exercise.id}/code-editor/TEMPLATE/`));
        const startResponse = await startResponsePromise;
        expect(startResponse.status()).toBe(202);
        runningJobId = (await readResponseJson<{ jobId: string }>(startResponse)).jobId;
        await expectRunningGenerationStatus(page, exercise.id!, runningJobId, 'GENERATE');
        await expect(page.getByTestId('hyperion-generation-persistence-state')).toContainText('Agent working copy — not saved');
    });

    test('starts generation through the real Hyperion workflow, exposes admin slot usage, and cancels the running job', async ({ browser, page, login }) => {
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
        await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
        await expectEditorActionsLockedDuringGeneration(page);
        await expectRunningGenerationStatus(page, exercise!.id!, jobId, 'GENERATE');
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);
        const requestsAfterFirstStart = await getHyperionLlmMockRequestCount(page);
        await expectDuplicateGenerationStartRejectedWithoutNewLlmRequest(page, exercise!.id!, jobId, requestsAfterFirstStart);
        const generationBuildAgent = await expectAdminGenerationSandboxSlots(browser, '1 / 1');

        await cancelRunningJobFromAdminDetails(browser, generationBuildAgent.name, exercise!.id!, jobId);
        await expectCancelledGeneration(page, exercise!.id!, jobId);
        await expectCancelRejected(page, exercise!.id!, jobId);
        await expectAdminGenerationSandboxSlots(browser, '0 / 1', generationBuildAgent.address);
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

    test('keeps a non-owner editor locked until the owner stops and the exercise refreshes', async ({ browser, page, login }) => {
        test.setTimeout(180_000);
        await openEditor(page, login, exercise!);

        const observerPage = await newBrowserPage(browser);
        try {
            await Commands.login(
                observerPage,
                admin,
                `/course-management/${course.id}/programming-exercises/${exercise!.id}/code-editor/TEMPLATE/${exercise!.templateParticipation!.id}`,
            );
            await openHyperionTabWithKeyboard(observerPage);
            await expectHyperionTabSelected(observerPage);
            await expect(observerPage.getByTestId('hyperion-generation-activity')).toBeVisible({ timeout: 60_000 });
            await expect(observerPage.getByTestId('hyperion-generation-empty')).toBeVisible();

            const { jobId } = await startGenerationFromMenu(page, exercise!.id!);
            runningJobId = jobId;

            await expect(observerPage.getByTestId('hyperion-generation-cancel')).toBeHidden();
            await expect(observerPage.getByTestId('hyperion-generation-activity')).toContainText('another instructor', { timeout: 60_000 });
            await expectEditorActionsLockedDuringGeneration(observerPage);

            const observerStatusResponse = await observerPage.request.get(`api/hyperion/programming-exercises/${exercise!.id}/generate-exercise/status`);
            expect(observerStatusResponse.ok()).toBeTruthy();
            const observerStatus = (await observerStatusResponse.json()) as GenerationStatus;
            expect(observerStatus).toMatchObject({ jobId, running: true, ownedByCaller: false, cancellable: false });
            expect(observerStatus.events).toEqual([]);
            expect(observerStatus.fileSnapshots ?? []).toEqual([]);

            const observerCancel = await observerPage.request.delete(`api/hyperion/programming-exercises/${exercise!.id}/generate-exercise/jobs/${jobId}`);
            expect(observerCancel.status()).toBe(404);

            const refreshedResponses: string[] = [];
            observerPage.on('response', (response) => {
                if (response.ok() && response.request().method() === 'GET') {
                    refreshedResponses.push(response.url());
                }
            });
            await cancelRunningJobFromUi(page, exercise!.id!, jobId);
            runningJobId = undefined;

            const observerActivity = observerPage.getByTestId('hyperion-generation-activity');
            await expect(observerActivity.getByTestId('hyperion-generation-empty')).toBeVisible({ timeout: 60_000 });
            await expect(observerActivity).toContainText('No generation activity yet');
            await expect(observerPage.getByTestId('hyperion-ai-menu')).toBeEnabled();
            await expect(observerPage.locator('#submit_button')).toBeEnabled();
            await expect(observerPage.locator('#refresh_button')).toBeEnabled();
            expect(refreshedResponses.some((url) => url.includes(`/api/programming/programming-exercises/${exercise!.id}`))).toBeTruthy();
        } finally {
            await observerPage.context().close();
        }
    });

    test('starts adaptation through the real Hyperion workflow with instructor instructions', async ({ page, login }) => {
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
        await expect(page.getByTestId('hyperion-generation-activity')).toHaveAccessibleName('Adaptation activity');
        await expect(page.getByTestId('hyperion-generation-persistence-state')).toContainText('Agent working copy — not saved');
        await expectRunningGenerationStatus(page, exercise!.id!, jobId, 'ADAPT');
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);
        await expectLlmMockSawPrompt(page, 'Make the edge-case requirements explicit and keep the tests deterministic.', initialLlmRequests);

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        runningJobId = undefined;
    });

    test('completes a mocked accepted adaptation through the browser and real verifier', async ({ browser, page, login }) => {
        test.setTimeout(300_000);
        const initialLlmRequests = await getHyperionLlmMockRequestCount(page);
        await openEditor(page, login, exercise!);
        const initialProblemStatement = await getExerciseProblemStatement(page, exercise!.id!);
        const initialTemplateFiles = await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/template-files-content?omitBinaries=true`);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        await page.getByLabel('Additional instructions').fill('HYPERION_E2E_SUBMIT_SEEDED_EXERCISE: use merge sort for lists with more than 5 dates and update the matching test.');

        const startResponsePromise = waitForGenerationStart(page, exercise!.id!);
        await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
        const { jobId, request } = await startResponsePromise;
        runningJobId = jobId;

        expect(request).toEqual({
            mode: 'ADAPT',
            prompt: 'HYPERION_E2E_SUBMIT_SEEDED_EXERCISE: use merge sort for lists with more than 5 dates and update the matching test.',
        });
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toContainText('Checking the exercise builds and grades', { timeout: 180_000 });
        await expect(activity).toContainText('exercise review found requirements or quality issues', { timeout: 180_000 });
        const buildOutputTab = page.getByRole('tab', { name: 'Build Output' });
        await selectTabWithKeyboard(page.getByRole('tab', { name: 'AI activity' }), buildOutputTab, 'ArrowLeft');
        await expectSuccessfulGenerationStatus(page, exercise!.id!, jobId, 'ADAPT');
        await expect(buildOutputTab).toHaveAttribute('aria-selected', 'true');
        await expect(buildOutputTab).toBeFocused();

        const hyperionTab = page.getByRole('tab', { name: 'AI activity' });
        await selectTabWithKeyboard(buildOutputTab, hyperionTab, 'ArrowRight');
        await expect(activity).toContainText('The exercise was adapted and saved', { timeout: 60_000 });
        const verdict = page.getByTestId('hyperion-generation-verdict');
        await expect(verdict).toContainText('Build and grading consistency checks passed');
        await expect(verdict).toContainText('Instructor review required');
        await expect(verdict).toContainText('Reference solution passes');
        await expect(verdict).toContainText('Template fails tests as expected');
        await expect(verdict).toContainText('13 tests');
        await expect(page.getByTestId('hyperion-generation-cancel')).toBeHidden();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);
        await openPersistedChangedFileInNativeEditor(page, policyPath, policyThresholdAfterAdaptation);
        await expectExerciseProblemStatement(page, exercise!.id!, correctedSeedStatementMarker);
        await expectExerciseProblemStatement(page, exercise!.id!, 'less or equal 5 dates');
        await expectSemanticAdaptation(page, exercise!.id!, true, initialTemplateFiles);
        await expectLlmMockSawPrompt(page, 'HYPERION_E2E_SUBMIT_SEEDED_EXERCISE', initialLlmRequests);
        await expectLlmMockCriticRequests(page, initialLlmRequests, 'HYPERION_E2E_SUBMIT_SEEDED_EXERCISE', 4);
        await expectLlmMockAcknowledgedTools(page, initialLlmRequests, ['write_file', 'edit_file']);
        await expectLlmMockResponseTool(page, initialLlmRequests, 'submit');
        await expectAdminGenerationSandboxSlots(browser, '0 / 1');

        await revertAcceptedAdaptationFromUi(page, exercise!.id!);
        await expect.poll(() => getExerciseProblemStatement(page, exercise!.id!), { timeout: 60_000 }).toBe(initialProblemStatement);
        await expectSemanticAdaptation(page, exercise!.id!, false, initialTemplateFiles);
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

    test('preserves useful work after a late external LLM failure and leaves the live exercise unchanged', async ({ browser, page, login }) => {
        test.setTimeout(240_000);
        const initialLlmRequests = await getHyperionLlmMockRequestCount(page);
        await openEditor(page, login, exercise!);
        const initialSolutionFiles = await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/solution-files-content?omitBinaries=true`);

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
        await expect(activity.getByTestId('hyperion-generation-persistence-state')).toContainText('Draft saved — live exercise unchanged', { timeout: 120_000 });
        await expect(activity.getByTestId('hyperion-generation-file-static')).toContainText('HyperionRecovery.java');
        await expect(page.getByTestId('hyperion-generation-cancel')).toBeHidden();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
        await expectHyperionLlmMockRequestsIncreased(page, initialLlmRequests);
        await expect
            .poll(async () =>
                repositoryFilesEqual(
                    await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/solution-files-content?omitBinaries=true`),
                    initialSolutionFiles,
                ),
            )
            .toBe(true);
        await expect
            .poll(async () => {
                const status = await getGenerationStatus(page, exercise!.id!);
                const terminal = [...status.events].reverse().find((event) => event.type === 'DONE');
                return { jobId: status.jobId, running: status.running, completionStatus: terminal?.completionStatus, liveExerciseChanged: terminal?.liveExerciseChanged };
            })
            .toEqual({ jobId, running: false, completionStatus: 'NEEDS_REVIEW', liveExerciseChanged: false });

        const freshPage = await newBrowserPage(browser);
        try {
            await openEditor(freshPage, (credentials, url) => Commands.login(freshPage, credentials, url), exercise!);
            await openHyperionTabWithKeyboard(freshPage);
            const recovered = freshPage.getByTestId('hyperion-generation-activity');
            await expect(recovered.getByTestId('hyperion-generation-persistence-state')).toContainText('Draft saved — live exercise unchanged');
            await expect(recovered.getByTestId('hyperion-generation-file-static')).toContainText('HyperionRecovery.java');
        } finally {
            await freshPage.context().close();
        }
        await expectAdminGenerationSandboxSlots(browser, '0 / 1');
        runningJobId = undefined;
    });
});

async function requireHyperionGenerationAvailable(page: Page) {
    const response = await page.request.get('/management/info');
    expect(response.ok(), 'Hyperion generation E2E needs management info to detect active features.').toBeTruthy();
    const info = await response.json();
    expect(info.activeModuleFeatures, 'Hyperion generation must be enabled in the dedicated mocked E2E environment.').toContain('hyperion');
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
    const detailsToggle = activity.getByTestId('hyperion-generation-details-toggle');
    await expect(detailsToggle).toHaveAttribute('aria-expanded', /true|false/);
    if ((await detailsToggle.getAttribute('aria-expanded')) === 'false') {
        await detailsToggle.click();
    }
    await expect(detailsToggle).toHaveAttribute('aria-expanded', 'true');
    await expect(fileRow).toBeVisible();
    await expect(activity.getByRole('button', { name: fileName })).toHaveCount(0);
}

async function openPersistedChangedFileInNativeEditor(page: Page, fileName: string, expectedContent: string) {
    const activity = page.getByTestId('hyperion-generation-activity');
    const fileButton = activity.getByRole('button', { name: fileName });
    const detailsToggle = activity.getByTestId('hyperion-generation-details-toggle');
    await expect(detailsToggle).toHaveAttribute('aria-expanded', /true|false/);
    if ((await detailsToggle.getAttribute('aria-expanded')) === 'false') {
        await detailsToggle.click();
    }
    await expect(detailsToggle).toHaveAttribute('aria-expanded', 'true');
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
    await page.getByTestId('hyperion-generate-exercise').click();
    const confirmationDialog = page.getByRole('alertdialog', { name: 'Generate and automatically save this exercise?' });
    await expect(confirmationDialog).toBeVisible();
    const startResponsePromise = waitForGenerationStart(page, exerciseId);
    await confirmationDialog.getByRole('button', { name: 'Generate exercise', exact: true }).click();
    return startResponsePromise;
}

async function waitForGenerationStart(page: Page, exerciseId: number): Promise<{ jobId: string; request: GenerationRequest }> {
    const response = await page.waitForResponse(
        (candidate) => candidate.request().method() === 'POST' && candidate.url().includes(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise`),
        { timeout: 60_000 },
    );
    expect(response.status()).toBe(202);
    const request = response.request().postDataJSON() as GenerationRequest;
    const body = await readResponseJson<{ jobId: string }>(response);
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
            { timeout: 180_000 },
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

async function expectLlmMockSawPrompt(page: Page, expectedPromptSnippet: string, firstRequestIndex: number) {
    await expect
        .poll(async () => {
            const requests = (await getHyperionLlmMockRequests(page)).slice(firstRequestIndex);
            const matching = requests.find(
                (request) =>
                    request.promptText?.includes(expectedPromptSnippet) &&
                    ['write_file', 'edit_file', 'delete_file', 'bash', 'submit'].every((tool) => request.toolNames?.includes(tool)),
            );
            return matching
                ? {
                      hasPrompt: true,
                      hasMessages: (matching.messageCount ?? 0) > 0,
                      hasUserRole: matching.roles?.includes('user') ?? false,
                      hasWriteFileTool: matching.toolNames?.includes('write_file') ?? false,
                      hasEditFileTool: matching.toolNames?.includes('edit_file') ?? false,
                      hasDeleteFileTool: matching.toolNames?.includes('delete_file') ?? false,
                      hasBashTool: matching.toolNames?.includes('bash') ?? false,
                      hasSubmitTool: matching.toolNames?.includes('submit') ?? false,
                  }
                : { hasPrompt: false };
        })
        .toEqual({
            hasPrompt: true,
            hasMessages: true,
            hasUserRole: true,
            hasWriteFileTool: true,
            hasEditFileTool: true,
            hasDeleteFileTool: true,
            hasBashTool: true,
            hasSubmitTool: true,
        });
}

async function expectLlmMockCriticRequests(page: Page, firstRequestIndex: number, runMarker: string, expectedCount: number) {
    await expect
        .poll(
            async () =>
                (await getHyperionLlmMockRequests(page))
                    .slice(firstRequestIndex)
                    .filter((request) => request.promptText?.includes(runMarker) && request.promptText.includes(criticPromptMarker)).length,
        )
        .toBe(expectedCount);
}

async function expectLlmMockAcknowledgedTools(page: Page, firstRequestIndex: number, expectedTools: string[]) {
    await expect
        .poll(async () => [new Set((await getHyperionLlmMockRequests(page)).slice(firstRequestIndex).flatMap((request) => request.acknowledgedToolNames ?? []))])
        .toEqual([new Set(expectedTools)]);
}

async function expectLlmMockResponseTool(page: Page, firstRequestIndex: number, expectedTool: string) {
    await expect
        .poll(async () =>
            (await getHyperionLlmMockRequests(page))
                .slice(firstRequestIndex)
                .map((request) => request.responseToolName)
                .filter(Boolean),
        )
        .toContain(expectedTool);
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

async function getExerciseProblemStatement(page: Page, exerciseId: number): Promise<string> {
    const response = await page.request.get(`api/programming/programming-exercises/${exerciseId}`);
    expect(response.ok()).toBeTruthy();
    const exercise = (await response.json()) as { problemStatement?: string };
    return exercise.problemStatement ?? '';
}

async function expectSemanticAdaptation(page: Page, exerciseId: number, adapted: boolean, initialTemplateFiles: Record<string, string>) {
    const expectedPolicyThreshold = adapted ? policyThresholdAfterAdaptation : policyThresholdBeforeAdaptation;
    const stalePolicyThreshold = adapted ? policyThresholdBeforeAdaptation : policyThresholdAfterAdaptation;
    const expectedTestLoop = adapted ? sortingTestAfterAdaptation : sortingTestBeforeAdaptation;
    const staleTestLoop = adapted ? sortingTestBeforeAdaptation : sortingTestAfterAdaptation;
    const expectedTestMessage = adapted ? sortingTestMessageAfterAdaptation : sortingTestMessageBeforeAdaptation;
    const staleTestMessage = adapted ? sortingTestMessageBeforeAdaptation : sortingTestMessageAfterAdaptation;
    const expectedBoundaryLoop = adapted ? boundaryTestAfterAdaptation : boundaryTestBeforeAdaptation;
    const staleBoundaryLoop = adapted ? boundaryTestBeforeAdaptation : boundaryTestAfterAdaptation;
    const expectedBoundaryMessage = adapted ? boundaryTestMessageAfterAdaptation : boundaryTestMessageBeforeAdaptation;
    const staleBoundaryMessage = adapted ? boundaryTestMessageBeforeAdaptation : boundaryTestMessageAfterAdaptation;
    await expect
        .poll(
            async () => ({
                solutionUpdated: await repositoryFileContains(
                    page,
                    `api/programming/programming-exercises/${exerciseId}/solution-files-content?omitBinaries=true`,
                    policyPath,
                    expectedPolicyThreshold,
                ),
                solutionStale: await repositoryFileContains(
                    page,
                    `api/programming/programming-exercises/${exerciseId}/solution-files-content?omitBinaries=true`,
                    policyPath,
                    stalePolicyThreshold,
                ),
                templateUnchanged: repositoryFilesEqual(
                    await getRepositoryFiles(page, `api/programming/programming-exercises/${exerciseId}/template-files-content?omitBinaries=true`),
                    initialTemplateFiles,
                ),
                testLoopUpdated: await testRepositoryFileContains(page, exerciseId, sortingTestPath, expectedTestLoop),
                testLoopStale: await testRepositoryFileContains(page, exerciseId, sortingTestPath, staleTestLoop),
                testMessageUpdated: await testRepositoryFileContains(page, exerciseId, sortingTestPath, expectedTestMessage),
                testMessageStale: await testRepositoryFileContains(page, exerciseId, sortingTestPath, staleTestMessage),
                boundaryLoopUpdated: await testRepositoryFileContains(page, exerciseId, sortingTestPath, expectedBoundaryLoop),
                boundaryLoopStale: await testRepositoryFileContains(page, exerciseId, sortingTestPath, staleBoundaryLoop),
                boundaryMessageUpdated: await testRepositoryFileContains(page, exerciseId, sortingTestPath, expectedBoundaryMessage),
                boundaryMessageStale: await testRepositoryFileContains(page, exerciseId, sortingTestPath, staleBoundaryMessage),
            }),
            { timeout: 90_000 },
        )
        .toEqual({
            solutionUpdated: true,
            solutionStale: false,
            templateUnchanged: true,
            testLoopUpdated: true,
            testLoopStale: false,
            testMessageUpdated: true,
            testMessageStale: false,
            boundaryLoopUpdated: true,
            boundaryLoopStale: false,
            boundaryMessageUpdated: true,
            boundaryMessageStale: false,
        });
}

async function repositoryFileContains(page: Page, endpoint: string, path: string, expectedContent: string): Promise<boolean> {
    return (await getRepositoryFileContent(page, endpoint, path)).includes(expectedContent);
}

async function getRepositoryFileContent(page: Page, endpoint: string, path: string): Promise<string> {
    const files = await getRepositoryFiles(page, endpoint);
    if (!(path in files)) {
        throw new Error(`Repository response did not contain ${path}`);
    }
    return files[path];
}

async function getRepositoryFiles(page: Page, endpoint: string): Promise<Record<string, string>> {
    const response = await page.request.get(endpoint);
    if (!response.ok()) {
        throw new Error(`Could not read repository files: HTTP ${response.status()}`);
    }
    return (await response.json()) as Record<string, string>;
}

function repositoryFilesEqual(actual: Record<string, string>, expected: Record<string, string>): boolean {
    const actualPaths = Object.keys(actual).sort();
    const expectedPaths = Object.keys(expected).sort();
    return actualPaths.length === expectedPaths.length && actualPaths.every((path, index) => path === expectedPaths[index] && actual[path] === expected[path]);
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
        if (request.method() === 'POST' && request.url().includes(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/revert`)) {
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
        (response) => response.request().method() === 'POST' && response.url().includes(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/revert`),
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
    await expectCancelledGeneration(page, exerciseId, jobId);
}

async function expectCancelledGeneration(page: Page, exerciseId: number, jobId: string) {
    await expect(page.getByTestId('hyperion-generation-activity')).toContainText('Generation was cancelled', { timeout: 60_000 });
    await expect(page.getByTestId('hyperion-generation-cancel')).toBeHidden();
    await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
    await expectTerminalGenerationStatus(page, exerciseId, jobId, 'CANCELLED');
}

async function cancelRunningJobFromAdminDetails(browser: Browser, agentName: string, exerciseId: number, jobId: string) {
    const adminPage = await newBrowserPage(browser);
    try {
        await Commands.login(adminPage, admin, '/admin/build-overview');
        const generationSection = adminPage.locator('#active-hyperion-generations');
        await adminPage.setViewportSize({ width: 390, height: 844 });
        const fleetJobsRegion = adminPage.getByTestId('hyperion-generation-jobs-scroll');
        await fleetJobsRegion.focus();
        await expect(fleetJobsRegion).toBeFocused();
        expect(await adminPage.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
        const generationRow = generationSection.getByRole('row').filter({ hasText: exerciseId.toString() });
        await expect(generationRow).toContainText(agentName, { timeout: 60_000 });
        await generationRow.getByTestId('hyperion-generation-details').click();
        await expect(adminPage.getByTestId('admin-body').getByText(jobId, { exact: true })).toBeVisible({ timeout: 60_000 });
        await expect(adminPage.getByTestId('hyperion-container-id')).toHaveText(/\S+/);
        await adminPage.setViewportSize({ width: 1440, height: 1100 });

        const cancelResponsePromise = adminPage.waitForResponse(
            (response) => response.request().method() === 'DELETE' && response.url().includes(`/api/admin/exercises/${exerciseId}/hyperion-generation-jobs/${jobId}/cancel`),
            { timeout: 60_000 },
        );
        await adminPage.getByRole('button', { name: 'Cancel generation', exact: true }).click();
        const confirmationDialog = adminPage.getByRole('alertdialog', { name: 'Cancel Hyperion generation' });
        await confirmationDialog.getByRole('button', { name: 'Cancel generation', exact: true }).click();
        expect((await cancelResponsePromise).ok()).toBeTruthy();
        await expect(adminPage.getByText('Generation cancelled and sandbox released.', { exact: true })).toBeVisible({ timeout: 60_000 });
        await adminPage.goto('/admin/build-overview');
        const refreshedGenerationSection = adminPage.locator('#active-hyperion-generations');
        await expect(refreshedGenerationSection).toBeVisible({ timeout: 60_000 });
        const refreshResponsePromise = adminPage.waitForResponse((response) => response.request().method() === 'GET' && response.url().includes('/generation-sandboxes'), {
            timeout: 60_000,
        });
        await refreshedGenerationSection.getByRole('button', { name: 'Refresh' }).click();
        expect((await refreshResponsePromise).ok()).toBeTruthy();
        await expect(refreshedGenerationSection.getByRole('row').filter({ hasText: exerciseId.toString() })).toHaveCount(0, { timeout: 60_000 });
    } finally {
        await adminPage.context().close();
    }
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

async function expectAdminGenerationSandboxSlots(browser: Browser, expectedSlots: string, expectedBuildAgentAddress?: string) {
    const adminPage = await newBrowserPage(browser);
    const [expectedReservedSlots, expectedMaxSlots] = expectedSlots.split(' / ').map(Number);
    let matchingBuildAgentAddress: string | undefined;
    let matchingBuildAgentName: string | undefined;
    try {
        await Commands.login(adminPage, admin, '/admin/build-agents');
        await expect
            .poll(
                async () => {
                    const response = await adminPage.request.get('api/admin/build-agents');
                    if (!response.ok()) {
                        return undefined;
                    }
                    const agents = (await response.json()) as BuildAgentSlots[];
                    const matchingAgent = agents.find(
                        (agent) =>
                            (!expectedBuildAgentAddress || agent.buildAgent?.memberAddress === expectedBuildAgentAddress) &&
                            (agent.reservedGenerationSandboxSlots ?? 0) === expectedReservedSlots &&
                            (agent.maxGenerationSandboxSlots ?? 0) === expectedMaxSlots,
                    );
                    matchingBuildAgentAddress = matchingAgent?.buildAgent?.memberAddress;
                    matchingBuildAgentName = matchingAgent?.buildAgent?.name;
                    return matchingBuildAgentAddress;
                },
                { timeout: 60_000 },
            )
            .toBeDefined();
        expect(matchingBuildAgentAddress).toBeDefined();
        expect(matchingBuildAgentName).toBeDefined();
        await expect(adminPage.getByRole('columnheader', { name: /Hyperion sandbox slots/i })).toBeVisible({ timeout: 60_000 });
        const matchingBuildAgentRow = adminPage.getByRole('row').filter({
            has: adminPage.getByRole('cell', { name: matchingBuildAgentAddress, exact: true }),
        });
        await expect(matchingBuildAgentRow.getByRole('cell', { name: expectedSlots, exact: true })).toBeVisible({ timeout: 60_000 });
        return { address: matchingBuildAgentAddress!, name: matchingBuildAgentName! };
    } finally {
        await adminPage.context().close();
    }
}

async function expectRehydratedActivityOnFreshPage(browser: Browser, programmingExercise: ProgrammingExercise) {
    const exerciseId = programmingExercise.id;
    const repositoryId = programmingExercise.templateParticipation?.id;
    expect(exerciseId).toBeDefined();
    expect(repositoryId).toBeDefined();
    const freshPage = await newBrowserPage(browser);
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
        await freshPage.context().close();
    }
}
