import dayjs from 'dayjs';
import { Browser, Locator, expect, Page } from '@playwright/test';

import { test } from '../../../support/fixtures';
import { Commands } from '../../../support/commands';
import { admin, instructor, studentOne, UserCredentials } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';
import { ExerciseMode, ProgrammingLanguage } from '../../../support/constants';
import { fillDateTimePicker, generateUUID, newBrowserPage, readResponseJson, setMonacoEditorContentByLocator } from '../../../support/utils';
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
        // TODO: no mock scenario currently drives a PARTIAL completion; covering it needs dedicated mock-server surgery.
        completionStatus?: 'SUCCESS' | 'NEEDS_REVIEW' | 'PARTIAL';
        verdict?: { mechanicallyVerified?: boolean; solutionPassed?: boolean; templateFailed?: boolean; testCount?: number; reasons?: string[] };
        liveExerciseChanged?: boolean;
        savedRepositoryCommits?: Record<string, string>;
        savedExerciseVersionId?: number;
        timestamp?: string;
    }[];
    fileChanges?: { path: string; repo: string; action: string }[];
};

type BuildAgentSlots = {
    buildAgent?: {
        name?: string;
        memberAddress?: string;
    };
    reservedGenerationSandboxSlots?: number;
    maxGenerationSandboxSlots?: number;
};

type ReviewThread = {
    id: number;
    targetType: string;
    resolved: boolean;
    comments: { content: { contentType: string; text?: string } }[];
};

type ExerciseVersionSnapshot = {
    problemStatement?: string;
    programmingData?: {
        templateParticipation?: { commitId?: string };
        solutionParticipation?: { commitId?: string };
        testsCommitId?: string;
    };
};

const correctedSeedStatementMarker = 'more than 5 dates';
const policyPath = 'src/de/test/Policy.java';
const policyThresholdBeforeAdaptation = 'DATES_SIZE_THRESHOLD = 10';
const policyThresholdAfterAdaptation = 'DATES_SIZE_THRESHOLD = 5';

test.describe('Hyperion exercise generation browser UI', { tag: ['@slow', '@hyperion'] }, () => {
    test.describe.configure({ timeout: 300_000 });
    test.use({ serviceWorkers: 'block' });

    let exercise: ProgrammingExercise | undefined;

    test.beforeAll(() => {
        expect(process.env.HYPERION_LLM_MODE, 'The Hyperion browser suite requires the mocked provider.').toBe('mock');
    });

    test.beforeEach('Create unreleased Java programming exercise', async ({ login, page, exerciseAPIRequests }, testInfo) => {
        await resetHyperionLlmMockScenario(page);
        await requireHyperionGenerationAvailable(page);
        if (testInfo.tags.includes('@hyperion-form-creation')) {
            return;
        }
        await login(admin);
        exercise = await exerciseAPIRequests.createProgrammingExercise({
            course,
            programmingLanguage: ProgrammingLanguage.JAVA,
            mode: ExerciseMode.INDIVIDUAL,
            releaseDate: dayjs().add(2, 'days'),
            dueDate: dayjs().add(3, 'days'),
            assessmentDate: dayjs().add(4, 'days'),
            problemStatement: javaProgrammingExerciseTemplate.problemStatement,
            title: `hyperion-ui-${generateUUID()}`,
        });
        expect(exercise.id).toBeDefined();
        await exerciseAPIRequests.waitForSolutionBuild(exercise.id!);
    });

    test.afterEach('Cancel job and delete programming exercise', async ({ login, page, exerciseAPIRequests }) => {
        try {
            if (exercise?.id) {
                await login(instructor);
                const statusResponse = await page.request.get(`api/hyperion/programming-exercises/${exercise.id}/generate-exercise/status`);
                if (statusResponse.status() === 200) {
                    const status = (await statusResponse.json()) as GenerationStatus;
                    if (status.running) {
                        const cancelResponse = await page.request.delete(`api/hyperion/programming-exercises/${exercise.id}/generate-exercise/jobs/${status.jobId}`);
                        expect([200, 404]).toContain(cancelResponse.status());
                        await resetHyperionLlmMockScenario(page);
                        await expect
                            .poll(
                                async () => {
                                    const latestStatus = await page.request.get(`api/hyperion/programming-exercises/${exercise!.id}/generate-exercise/status`);
                                    if (latestStatus.status() === 204) {
                                        return true;
                                    }
                                    expect(latestStatus.status()).toBe(200);
                                    return !((await latestStatus.json()) as GenerationStatus).running;
                                },
                                { timeout: 60_000 },
                            )
                            .toBe(true);
                    }
                } else {
                    expect(statusResponse.status()).toBe(204);
                }
                // The Hyperion mutation slot is released asynchronously after a job terminalizes, so a delete racing
                // that release gets a legitimate 409 from the deletion guard; retry briefly instead of failing cleanup.
                const exerciseIdToDelete = exercise.id;
                await expect
                    .poll(
                        async () => {
                            const deleteResponse = await page.request.delete(
                                `api/programming/programming-exercises/${exerciseIdToDelete}?deleteStudentReposBuildPlans=true&deleteBaseReposBuildPlans=true`,
                            );
                            if (deleteResponse.status() === 409) {
                                return 409;
                            }
                            expect([200, 404]).toContain(deleteResponse.status());
                            return deleteResponse.status();
                        },
                        { timeout: 60_000, intervals: [1_000, 2_000, 5_000] },
                    )
                    .not.toBe(409);
                exercise = undefined;
            }
        } finally {
            await resetHyperionLlmMockScenario(page);
        }
    });

    test(
        'drafts a problem statement and completes generation from the programming exercise form',
        { tag: '@hyperion-form-creation' },
        async ({ page, login, exerciseAPIRequests, programmingExerciseCreation }) => {
            await login(instructor, `/course-management/${course.id}/programming-exercises/new`);
            await programmingExerciseCreation.waitForFormToLoad();
            await programmingExerciseCreation.changeEditMode();
            await programmingExerciseCreation.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            await page.locator('#field_projectType').getByText('Maven', { exact: true }).click();
            await programmingExerciseCreation.setShortName(`hyperionform${generateUUID()}`);
            await programmingExerciseCreation.setPoints(10);
            await page.locator('#field_bonusPoints').fill('0');
            await fillDateTimePicker(page.getByLabel('Release Date', { exact: true }), dayjs().add(2, 'days'));
            await programmingExerciseCreation.setDueDate(dayjs().add(3, 'days'));

            await page
                .locator('#userPrompt')
                .fill('HYPERION_E2E_SUBMIT_NEW_EXERCISE: classify temperatures with clear boundaries and all-or-nothing validation. Do not prescribe class or method names.');
            const draftResponsePromise = page.waitForResponse(
                (response) => response.request().method() === 'POST' && response.url().includes(`/api/hyperion/courses/${course.id}/problem-statements/generate`),
            );
            await page.getByRole('button', { name: 'Generate Draft Problem Statement' }).click();
            const draftResponse = await draftResponsePromise;
            expect(draftResponse.ok()).toBeTruthy();
            const draft = await readResponseJson<{ draftProblemStatement?: string }>(draftResponse);
            expect(draft.draftProblemStatement).toContain('# Temperature Alert Classification');
            await expectProblemStatementTextThroughUi(page, 'Create a small Java program that classifies temperature readings.');
            await expect(page.locator('#field_title')).toHaveValue('Temperature Alert Classification');
            await expect(page.locator('#field_packageName')).toHaveValue('temperaturealertclassification');
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
            expect(exercise.title).toBe('Temperature Alert Classification');
            expect(exercise.packageName).toBe('temperaturealertclassification');
            await expect(page).toHaveURL(new RegExp(`/programming-exercises/${exercise.id}/code-editor/TEMPLATE/`));
            const startResponse = await startResponsePromise;
            expect(startResponse.status()).toBe(202);
            const { jobId } = await readResponseJson<{ jobId: string }>(startResponse);
            await expectRunningGenerationStatus(page, exercise.id!, jobId, 'GENERATE');
            await expect(page.getByTestId('hyperion-generation-persistence-state')).toContainText('Agent working copy — not saved');
            await expectSuccessfulGenerationStatus(page, exercise.id!, jobId, 'GENERATE', 3);
            await expect(page.getByTestId('hyperion-generation-persistence-state')).toContainText('Saved to exercise');
            await expectExerciseProblemStatement(page, exercise.id!, 'TemperatureClassifier.classify');
            const solution = await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise.id}/solution-files-content?omitBinaries=true`);
            expect(solution['src/temperaturealertclassification/TemperatureClassifier.java']).toContain('labels.add');
            const template = await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise.id}/template-files-content?omitBinaries=true`);
            expect(template['src/temperaturealertclassification/TemperatureClassifier.java']).toContain('throw new UnsupportedOperationException("Not implemented")');
            await page.reload();
            await openHyperionTab(page);
            await expect(page.getByTestId('hyperion-generation-persistence-state')).toContainText('Saved to exercise');
        },
    );

    test('rejects a stale generation start after another user starts the exercise', async ({ browser, page, login }) => {
        await openEditor(page, login, exercise!);
        await page.getByTestId('hyperion-ai-menu').click();
        await expect(page.getByTestId('hyperion-generate-exercise')).toBeVisible();

        const studentPage = await newBrowserPage(browser);
        try {
            await Commands.login(studentPage, admin, '/');
            const exerciseResponse = await studentPage.request.get(`api/programming/programming-exercises/${exercise!.id}`);
            expect(exerciseResponse.ok()).toBeTruthy();
            const currentExercise = (await exerciseResponse.json()) as ProgrammingExercise;
            const updateResponse = await studentPage.request.put('api/programming/programming-exercises/timeline', {
                data: {
                    id: currentExercise.id,
                    releaseDate: dayjs().subtract(1, 'minute').toISOString(),
                    startDate: currentExercise.startDate,
                    dueDate: currentExercise.dueDate,
                    assessmentType: currentExercise.assessmentType,
                    assessmentDueDate: currentExercise.assessmentDueDate,
                    exampleSolutionPublicationDate: currentExercise.exampleSolutionPublicationDate,
                    buildAndTestStudentSubmissionsAfterDueDate: currentExercise.buildAndTestStudentSubmissionsAfterDueDate,
                },
            });
            expect(updateResponse.ok()).toBeTruthy();

            await Commands.login(studentPage, studentOne, '/');
            const participationResponse = await studentPage.request.post(`api/exercise/exercises/${exercise!.id}/participations`);
            expect(participationResponse.status()).toBe(201);

            await Commands.login(studentPage, admin, '/');
            const restoreReleaseDateResponse = await studentPage.request.put('api/programming/programming-exercises/timeline', {
                data: {
                    id: currentExercise.id,
                    releaseDate: currentExercise.releaseDate,
                    startDate: currentExercise.startDate,
                    dueDate: currentExercise.dueDate,
                    assessmentType: currentExercise.assessmentType,
                    assessmentDueDate: currentExercise.assessmentDueDate,
                    exampleSolutionPublicationDate: currentExercise.exampleSolutionPublicationDate,
                    buildAndTestStudentSubmissionsAfterDueDate: currentExercise.buildAndTestStudentSubmissionsAfterDueDate,
                },
            });
            expect(restoreReleaseDateResponse.ok()).toBeTruthy();
        } finally {
            await studentPage.context().close();
        }

        await page.getByTestId('hyperion-generate-exercise').click();
        const confirmationDialog = page.getByRole('alertdialog', { name: 'Generate and automatically save this exercise?' });
        await expect(confirmationDialog).toBeVisible();
        const rejectedStart = page.waitForResponse(
            (response) => response.request().method() === 'POST' && response.url().includes(`/api/hyperion/programming-exercises/${exercise!.id}/generate-exercise`),
        );
        await confirmationDialog.getByRole('button', { name: 'Generate exercise', exact: true }).click();
        const rejectedStartResponse = await rejectedStart;
        expect(rejectedStartResponse.status()).toBe(400);
        expect(await readResponseJson<{ errorKey?: string }>(rejectedStartResponse)).toMatchObject({ errorKey: 'exerciseHasParticipations' });
        await expect(page.getByText('Could not start the generation.')).toBeVisible();
        await expectNoRetainedGenerationStatus(page, exercise!.id!);

        await page.reload();
        await page.getByTestId('hyperion-ai-menu').click();
        await expect(page.getByTestId('hyperion-generate-exercise')).toHaveCount(0);
        await expect(page.getByTestId('hyperion-adapt-with-feedback')).toHaveCount(0);
    });

    test('starts generation through the real Hyperion workflow, exposes admin slot usage, and cancels the running job', async ({ browser, page, login }) => {
        await openEditor(page, login, exercise!);

        await holdUnmatchedHyperionLlmRequests(page);
        const { jobId, request } = await startGenerationFromMenu(page, exercise!.id!);

        expect(request).toEqual({ mode: 'GENERATE' });
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity.getByTestId('hyperion-generation-persistence-state')).toContainText('Agent working copy — not saved');
        await expectHyperionTabSelected(page);
        await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
        await expectEditorActionsLockedDuringGeneration(page);
        await page.getByTestId('hyperion-ai-menu').click();
        await expect(page.getByTestId('hyperion-generate-exercise')).toHaveCount(0);
        await expect(page.getByTestId('hyperion-adapt-with-feedback')).toHaveCount(0);
        await expectRunningGenerationStatus(page, exercise!.id!, jobId, 'GENERATE');
        await expectDuplicateGenerationStartRejected(page, exercise!.id!, jobId);
        const generationBuildAgent = await expectAdminGenerationSandboxSlots(browser, '1 / 1');

        await cancelRunningJobFromAdminDetails(browser, generationBuildAgent.name, exercise!.id!, jobId);
        await expectCancelledGeneration(page, exercise!.id!, jobId);
        await expectCancelRejected(page, exercise!.id!, jobId);
        await expectAdminGenerationSandboxSlots(browser, '0 / 1', generationBuildAgent.address);
    });

    test('preserves unsaved problem statement edits and refuses to start generation', async ({ page, login }) => {
        await openEditor(page, login, exercise!);
        await page.locator('jhi-code-editor-file-browser').getByText('Problem Statement', { exact: true }).click();
        const marker = 'HYPERION_E2E_UNSAVED_INSTRUCTOR_EDIT';
        await appendProblemStatementThroughUi(page, marker);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-generate-exercise').click();

        await expect(page.getByText('Save or discard your local edits before starting or applying AI generation.')).toBeVisible();
        await expect(page.getByRole('alertdialog', { name: 'Generate and automatically save this exercise?' })).toHaveCount(0);
        await expectNoRetainedGenerationStatus(page, exercise!.id!);
        await expect(page.locator('jhi-programming-exercise-editable-instructions').getByText('Unsaved.', { exact: true })).toBeVisible();
    });

    test('preserves unsaved problem statement edits and refuses to start adaptation', async ({ page, login }) => {
        await openEditor(page, login, exercise!);
        await page.locator('jhi-code-editor-file-browser').getByText('Problem Statement', { exact: true }).click();
        await appendProblemStatementThroughUi(page, 'HYPERION_E2E_UNSAVED_ADAPT_EDIT');

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();

        await expect(page.getByText('Save or discard your local edits before starting or applying AI generation.')).toBeVisible();
        await expect(page.getByLabel('Additional instructions')).toHaveCount(0);
        await expectNoRetainedGenerationStatus(page, exercise!.id!);
        await expect(page.locator('jhi-programming-exercise-editable-instructions').getByText('Unsaved.', { exact: true })).toBeVisible();
    });

    test('recovers from a failed status check via the retry affordance', async ({ page, login }) => {
        const exerciseId = exercise!.id!;
        const repositoryId = exercise!.templateParticipation!.id;
        let statusRequestCount = 0;
        // The activity component surfaces the retry affordance only after 3 failed status checks; fail exactly that many, then let requests through.
        await page.route(`**/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/status`, async (route) => {
            statusRequestCount++;
            if (statusRequestCount <= 3) {
                await route.fulfill({ status: 503, contentType: 'application/json', body: '{}' });
                return;
            }
            await route.continue();
        });

        await login(instructor, `/course-management/${course.id}/programming-exercises/${exerciseId}/code-editor/TEMPLATE/${repositoryId}`);
        await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
        // The activity card lives in the collapsed bottom-panel tab; reveal it before asserting the banner.
        await page.getByTestId('editor-bottom-panel-tab').click();
        await expect(page.getByText('The generation status could not be verified. AI actions and editing remain unavailable until the status check succeeds.')).toBeVisible({
            timeout: 30_000,
        });

        await page.getByRole('button', { name: 'Retry status check', exact: true }).click();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
        await expect(page.getByTestId('hyperion-generation-empty')).toHaveCount(1, { timeout: 60_000 });
    });

    test('rehydrates a terminal state that occurred while disconnected', async ({ browser, page, login }) => {
        await openEditor(page, login, exercise!);
        await holdUnmatchedHyperionLlmRequests(page);
        const { jobId } = await startGenerationFromMenu(page, exercise!.id!);
        await expectProviderResponseHeld(page);

        await page.context().setOffline(true);
        await expect.poll(() => page.evaluate(() => navigator.onLine)).toBe(false);
        const controllerPage = await newBrowserPage(browser);
        try {
            await Commands.login(controllerPage, instructor, '/');
            const cancelResponse = await controllerPage.request.delete(`api/hyperion/programming-exercises/${exercise!.id}/generate-exercise/jobs/${jobId}`);
            expect(cancelResponse.ok()).toBeTruthy();
            await expectTerminalGenerationStatus(controllerPage, exercise!.id!, jobId, 'CANCELLED');
            await expect(page.getByTestId('hyperion-generation-cancel')).toBeVisible();
            await expect(page.getByTestId('hyperion-generation-persistence-state')).toContainText('Agent working copy — not saved');
            const terminalStatus = await getGenerationStatus(controllerPage, exercise!.id!);
            const versionCount = await getExerciseVersionCount(controllerPage, exercise!.id!);
            await releaseHeldProviderResponseIfPresent(controllerPage);
            await expectLateProviderResponseFenced(controllerPage, exercise!.id!, terminalStatus, versionCount);

            await page.context().setOffline(false);
            await expectCancelledGeneration(page, exercise!.id!, jobId);
            await expect(page.getByTestId('hyperion-generation-persistence-state')).toContainText('Not saved — cancelled');
            await resetHyperionLlmMockScenario(controllerPage);
        } finally {
            await page.context().setOffline(false);
            await controllerPage.context().close();
        }
    });

    test('rehydrates real retained file changes after reload and from a fresh page', async ({ browser, page, login }) => {
        await openEditor(page, login, exercise!);

        const initialSolutionFiles = await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/solution-files-content?omitBinaries=true`);
        const initialVersionCount = await getExerciseVersionCount(page, exercise!.id!);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        await page.getByLabel('Additional instructions').fill('HYPERION_E2E_WRITE_SNAPSHOT: write the deterministic preview file, then keep running.');

        await holdUnmatchedHyperionLlmRequests(page);
        const startResponsePromise = waitForGenerationStart(page, exercise!.id!);
        await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
        const { jobId } = await startResponsePromise;
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toContainText('HyperionPreview.java', { timeout: 60_000 });
        await expectFileChangeNavigationDisabled(page, 'HyperionPreview.java');
        await expectFileChangeStatus(page, exercise!.id!, jobId);
        await expectRehydratedActivityOnFreshPage(browser, exercise!);

        await page.reload();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
        await openHyperionTab(page);
        await expectHyperionTabSelected(page);
        const rehydratedActivity = page.getByTestId('hyperion-generation-activity');
        await expect(rehydratedActivity).toContainText('Starting exercise generation');
        await expect(rehydratedActivity).toContainText('HyperionPreview.java');
        await expectFileChangeNavigationDisabled(page, 'HyperionPreview.java');
        await expectFileChangeStatus(page, exercise!.id!, jobId);

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        await expectFileChangeNavigationDisabled(page, 'HyperionPreview.java');
        await expect
            .poll(async () => ({
                solutionUnchanged: repositoryFilesEqual(
                    await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/solution-files-content?omitBinaries=true`),
                    initialSolutionFiles,
                ),
                versionCount: await getExerciseVersionCount(page, exercise!.id!),
            }))
            .toEqual({ solutionUnchanged: true, versionCount: initialVersionCount });
    });

    test('preserves a dirty observer code edit until the observer confirms reload', async ({ browser, page, login }) => {
        await openEditor(page, login, exercise!);

        const observerPage = await newBrowserPage(browser);
        try {
            await Commands.login(
                observerPage,
                admin,
                `/course-management/${course.id}/programming-exercises/${exercise!.id}/code-editor/TEMPLATE/${exercise!.templateParticipation!.id}`,
            );
            await expect(observerPage.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
            const observedTemplateFile = 'MergeSort.java';
            const localCodeEditMarker = 'HYPERION_E2E_DIRTY_OBSERVER_CODE_EDIT';
            await expect(observerPage.getByText('Submitted.', { exact: true })).toBeVisible();
            await editCodeFileThroughUi(observerPage, observedTemplateFile, `// ${localCodeEditMarker}`);

            await page.getByTestId('hyperion-ai-menu').click();
            await page.getByTestId('hyperion-adapt-with-feedback').click();
            const prompt = 'HYPERION_E2E_SUBMIT_SEEDED_EXERCISE: use merge sort for lists with more than 5 dates and update the matching test.';
            await page.getByLabel('Additional instructions').fill(prompt);
            const startResponsePromise = waitForGenerationStart(page, exercise!.id!);
            await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
            const { jobId, request } = await startResponsePromise;
            expect(request).toEqual({ mode: 'ADAPT', prompt });

            await openHyperionTab(observerPage);
            await expect(observerPage.getByTestId('hyperion-generation-activity')).toContainText('another instructor', { timeout: 60_000 });
            await expectEditorActionsLockedDuringGeneration(observerPage);
            await expect(observerPage.getByTestId('hyperion-generation-cancel')).toHaveCount(0);
            const observerStatus = await getGenerationStatus(observerPage, exercise!.id!);
            expect(observerStatus).toMatchObject({ jobId, running: true, ownedByCaller: false, cancellable: false, events: [], fileChanges: [] });
            const observerCancelResponse = await observerPage.request.delete(`api/hyperion/programming-exercises/${exercise!.id}/generate-exercise/jobs/${jobId}`);
            expect(observerCancelResponse.status()).toBe(404);
            await expectGenerationOutcomeStatus(page, exercise!.id!, jobId, 'ADAPT');
            const savedVersion = await expectSavedExerciseVersion(page, exercise!.id!, jobId, ['solution', 'tests']);
            expect(savedVersion.snapshot.problemStatement).toContain(correctedSeedStatementMarker);
            await expectExerciseProblemStatement(page, exercise!.id!, correctedSeedStatementMarker);
            await expectTerminalOutcomeOnFreshAuthorizedPage(browser, exercise!, jobId, savedVersion.id);

            const reloadSavedExercise = observerPage.getByRole('button', { name: 'Reload saved exercise', exact: true });
            await expect(reloadSavedExercise).toBeVisible({ timeout: 60_000 });
            await expectCodeFileTextThroughUi(observerPage, observedTemplateFile, localCodeEditMarker, true);

            const observerEditorUrl = observerPage.url();
            const reviewNavigation = observerPage.waitForURL(/\/version-history/, { timeout: 2_000 }).then(
                () => true,
                () => false,
            );
            await observerPage.getByTestId('hyperion-generation-review-action').filter({ hasText: 'Problem statement history' }).click();
            await expect(observerPage.getByText('Save or discard your local edits before opening the saved-change review.', { exact: true })).toBeVisible();
            expect(await reviewNavigation).toBe(false);
            await expect(observerPage).toHaveURL(observerEditorUrl);

            await reloadSavedExercise.click();
            const reloadDialog = observerPage.getByRole('alertdialog', { name: 'Reload the saved exercise?' });
            await expect(reloadDialog).toContainText('All unsaved local edits will be lost.');
            const cancelReload = reloadDialog.getByRole('button', { name: 'Cancel', exact: true });
            await expect(cancelReload).toBeFocused();
            await cancelReload.click();
            await expect(reloadDialog).toBeHidden();
            await expect(reloadSavedExercise).toBeVisible();
            await expectCodeFileTextThroughUi(observerPage, observedTemplateFile, localCodeEditMarker, true);

            await reloadSavedExercise.click();
            await expect(reloadDialog).toBeVisible();
            const navigationPromise = observerPage.waitForEvent('framenavigated', (frame) => frame === observerPage.mainFrame());
            await reloadDialog.getByRole('button', { name: 'Reload saved exercise', exact: true }).click();
            await navigationPromise;
            await expect(observerPage.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
            await expectCodeFileTextThroughUi(observerPage, observedTemplateFile, localCodeEditMarker, false);
            await openHyperionTab(observerPage);
            await expect(observerPage.getByTestId('hyperion-editor-refresh-retry')).toHaveCount(0);
            await expect(reloadSavedExercise).toHaveCount(0);
            await expect(observerPage.getByTestId('hyperion-generation-persistence-state')).toContainText('Saved to exercise');
        } finally {
            await observerPage.context().close();
        }
    });

    test('saves a mechanically valid adaptation for instructor review through the browser and real verifier', async ({ browser, page, login }) => {
        await openEditor(page, login, exercise!);
        const initialProblemStatement = await getExerciseProblemStatement(page, exercise!.id!);
        const initialReviewThreadIds = new Set((await getReviewThreads(page, exercise!.id!)).map((thread) => thread.id));
        const initialVersion = await getLatestExerciseVersion(page, exercise!.id!);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        const prompt = 'HYPERION_E2E_SUBMIT_SEEDED_EXERCISE HYPERION_E2E_REQUIRE_INSTRUCTOR_REVIEW: use merge sort for lists with more than 5 dates and update the matching test.';
        await page.getByLabel('Additional instructions').fill(prompt);

        const startResponsePromise = waitForGenerationStart(page, exercise!.id!);
        await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
        const { jobId, request } = await startResponsePromise;

        expect(request).toEqual({
            mode: 'ADAPT',
            prompt,
        });
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toContainText('Checking the exercise builds and grades', { timeout: 180_000 });
        await expect(activity).toContainText('exercise review found requirements or quality issues', { timeout: 180_000 });
        await expectGenerationOutcomeStatus(page, exercise!.id!, jobId, 'ADAPT', 'NEEDS_REVIEW');
        await expect(activity).toContainText('The exercise was adapted and saved', { timeout: 60_000 });
        await expect(activity.getByTestId('hyperion-generation-persistence-state')).toContainText('Saved to exercise — instructor review required');
        const verdict = page.getByTestId('hyperion-generation-verdict');
        await expect(verdict).toContainText('Build and grading consistency checks passed');
        await expect(verdict).toContainText('Instructor review required');
        await expect(verdict).toContainText('Reference solution passes');
        await expect(verdict).toContainText('Template fails tests as expected');
        await expect(verdict).toContainText('13 tests');
        await expect(page.getByTestId('hyperion-generation-cancel')).toBeHidden();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
        await expect(page.locator('jhi-programming-exercise-instructions#previewMonaco')).toContainText(correctedSeedStatementMarker, { timeout: 60_000 });
        await expect(page.getByTestId('hyperion-editor-refresh-retry')).toHaveCount(0);
        await openPersistedChangedFileInNativeEditor(page, policyPath, policyThresholdAfterAdaptation);
        await expectExerciseProblemStatement(page, exercise!.id!, correctedSeedStatementMarker);
        await expectExerciseProblemStatement(page, exercise!.id!, 'less or equal 5 dates');
        await expectSemanticAdaptation(page, exercise!.id!, true);
        await expectGenerationReviewThread(page, exercise!.id!, initialReviewThreadIds);
        await expect(page.locator('#file-browser-problem-statement .badge')).toBeVisible();
        const savedVersion = await expectSavedExerciseVersion(page, exercise!.id!, jobId, ['solution', 'tests']);
        expect(savedVersion.snapshot.problemStatement).toContain(correctedSeedStatementMarker);
        await reviewSavedProblemStatementVersion(page, savedVersion.id);
        await revertMechanicallyVerifiedAdaptationFromUi(page, exercise!.id!);
        await expect.poll(() => getExerciseProblemStatement(page, exercise!.id!), { timeout: 60_000 }).toBe(initialProblemStatement);
        await expectSemanticAdaptation(page, exercise!.id!, false);
        const revertedVersion = await getLatestExerciseVersion(page, exercise!.id!);
        expect(revertedVersion.id).not.toBe(savedVersion.id);
        expect(getSnapshotRepositoryCommits(revertedVersion.snapshot)).toEqual(getSnapshotRepositoryCommits(initialVersion.snapshot));
    });

    test('rejects mechanically invalid work without changing the exercise', async ({ page, login }) => {
        await openEditor(page, login, exercise!);
        const initialSolutionFiles = await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/solution-files-content?omitBinaries=true`);
        const initialTemplateFiles = await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/template-files-content?omitBinaries=true`);
        const initialTestFiles = await getTestRepositoryFiles(page, exercise!.id!);
        const initialMetadata = await getExerciseMetadata(page, exercise!.id!);
        const initialVersionCount = await getExerciseVersionCount(page, exercise!.id!);
        const initialReviewThreadIds = (await getReviewThreads(page, exercise!.id!)).map((thread) => thread.id).sort((a, b) => a - b);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        await page.getByLabel('Additional instructions').fill('HYPERION_E2E_MECHANICAL_REJECTION: exercise the authoritative verifier rejection path.');
        const startResponsePromise = waitForGenerationStart(page, exercise!.id!);
        await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
        const { jobId } = await startResponsePromise;

        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity.getByTestId('hyperion-generation-file-static')).toContainText('VerifierRejected.java', { timeout: 120_000 });
        await expect(activity.getByTestId('hyperion-generation-persistence-state')).toContainText('Not saved — failed', { timeout: 240_000 });
        await expect(activity.getByTestId('hyperion-generation-terminal-message')).toContainText('did not pass mechanical verification');
        await expect
            .poll(async () => {
                const status = await getGenerationStatus(page, exercise!.id!);
                const terminal = [...status.events].reverse().find((event) => event.type === 'ERROR');
                return {
                    jobId: status.jobId,
                    running: status.running,
                    terminalType: terminal?.type,
                    message: terminal?.message,
                };
            })
            .toEqual({ jobId, running: false, terminalType: 'ERROR', message: expect.stringContaining('did not pass mechanical verification') });
        await expect
            .poll(async () => ({
                solutionUnchanged: repositoryFilesEqual(
                    await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/solution-files-content?omitBinaries=true`),
                    initialSolutionFiles,
                ),
                templateUnchanged: repositoryFilesEqual(
                    await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/template-files-content?omitBinaries=true`),
                    initialTemplateFiles,
                ),
                testsUnchanged: repositoryFilesEqual(await getTestRepositoryFiles(page, exercise!.id!), initialTestFiles),
                metadata: await getExerciseMetadata(page, exercise!.id!),
                reviewThreadIds: (await getReviewThreads(page, exercise!.id!)).map((thread) => thread.id).sort((a, b) => a - b),
                versionCount: await getExerciseVersionCount(page, exercise!.id!),
            }))
            .toEqual({
                solutionUnchanged: true,
                templateUnchanged: true,
                testsUnchanged: true,
                metadata: initialMetadata,
                reviewThreadIds: initialReviewThreadIds,
                versionCount: initialVersionCount,
            });
    });

    test('retains diagnostics after a late external LLM failure without saving unverified work', async ({ browser, page, login }) => {
        await openEditor(page, login, exercise!);
        const initialSolutionFiles = await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/solution-files-content?omitBinaries=true`);
        const initialTemplateFiles = await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/template-files-content?omitBinaries=true`);
        const initialTestFiles = await getTestRepositoryFiles(page, exercise!.id!);
        const initialMetadata = await getExerciseMetadata(page, exercise!.id!);
        const initialVersionCount = await getExerciseVersionCount(page, exercise!.id!);
        const initialReviewThreadIds = (await getReviewThreads(page, exercise!.id!)).map((thread) => thread.id).sort((a, b) => a - b);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        await page.getByLabel('Additional instructions').fill('HYPERION_E2E_FAIL_LLM: fail the external model call for this unhappy-path browser test.');
        const startResponsePromise = waitForGenerationStart(page, exercise!.id!);
        await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
        const { jobId, request } = await startResponsePromise;

        expect(request).toEqual({
            mode: 'ADAPT',
            prompt: 'HYPERION_E2E_FAIL_LLM: fail the external model call for this unhappy-path browser test.',
        });

        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity.getByTestId('hyperion-generation-file-static')).toContainText('HyperionDiagnostic.java', { timeout: 120_000 });
        await expect.poll(() => getPendingLateFailureCount(page)).toBe(1);

        await page.reload();
        await openHyperionTab(page);
        await expectRunningGenerationStatus(page, exercise!.id!, jobId, 'ADAPT');
        await expect(activity.getByTestId('hyperion-generation-persistence-state')).toContainText('Agent working copy — not saved');
        await expect(activity.getByTestId('hyperion-generation-file-static')).toContainText('HyperionDiagnostic.java');
        await expect(page.getByTestId('hyperion-generation-cancel')).toBeVisible();
        await expectEditorActionsLockedDuringGeneration(page);
        await page.getByTestId('hyperion-ai-menu').click();
        await expect(page.getByTestId('hyperion-adapt-with-feedback')).toHaveCount(0);
        await expectHyperionTabSelected(page);
        await releasePendingLateFailure(page);

        await expect(activity.getByTestId('hyperion-generation-persistence-state')).toContainText('Not saved — failed', { timeout: 120_000 });
        await expect(activity.getByTestId('hyperion-generation-terminal-message')).toContainText('Nothing was saved');
        await expect.poll(async () => (await getReviewThreads(page, exercise!.id!)).map((thread) => thread.id).sort((a, b) => a - b)).toEqual(initialReviewThreadIds);
        await expect(page.getByTestId('hyperion-generation-cancel')).toBeHidden();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeEnabled();
        await expect
            .poll(async () => ({
                solutionUnchanged: repositoryFilesEqual(
                    await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/solution-files-content?omitBinaries=true`),
                    initialSolutionFiles,
                ),
                templateUnchanged: repositoryFilesEqual(
                    await getRepositoryFiles(page, `api/programming/programming-exercises/${exercise!.id}/template-files-content?omitBinaries=true`),
                    initialTemplateFiles,
                ),
                testsUnchanged: repositoryFilesEqual(await getTestRepositoryFiles(page, exercise!.id!), initialTestFiles),
                metadata: await getExerciseMetadata(page, exercise!.id!),
                versionCount: await getExerciseVersionCount(page, exercise!.id!),
            }))
            .toEqual({ solutionUnchanged: true, templateUnchanged: true, testsUnchanged: true, metadata: initialMetadata, versionCount: initialVersionCount });
        await expect
            .poll(async () => {
                const status = await getGenerationStatus(page, exercise!.id!);
                const terminal = [...status.events].reverse().find((event) => event.type === 'ERROR');
                return {
                    jobId: status.jobId,
                    running: status.running,
                    type: terminal?.type,
                    savedExerciseVersionId: terminal?.savedExerciseVersionId,
                    savedRepositoryCommits: terminal?.savedRepositoryCommits,
                };
            })
            .toEqual({ jobId, running: false, type: 'ERROR', savedExerciseVersionId: undefined, savedRepositoryCommits: undefined });

        const freshPage = await newBrowserPage(browser);
        try {
            await Commands.login(
                freshPage,
                instructor,
                `/course-management/${course.id}/programming-exercises/${exercise!.id}/code-editor/TEMPLATE/${exercise!.templateParticipation!.id}`,
            );
            await expect(freshPage.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
            await openHyperionTab(freshPage);
            const recovered = freshPage.getByTestId('hyperion-generation-activity');
            await expect(recovered.getByTestId('hyperion-generation-persistence-state')).toContainText('Not saved — failed');
            await expect(recovered.getByTestId('hyperion-generation-file-static')).toContainText('HyperionDiagnostic.java');
            const runAgainButton = recovered.getByTestId('hyperion-generation-run-again');
            await expect(runAgainButton).toBeVisible();

            // Run-again replays the job's own mode (ADAPT here), which reopens the adapt dialog rather than starting immediately.
            await holdUnmatchedHyperionLlmRequests(freshPage);
            await runAgainButton.click();
            await freshPage.getByLabel('Additional instructions').fill('HYPERION_E2E_RUN_AGAIN: verify a second generation actually starts.');
            const rerunStartPromise = waitForGenerationStart(freshPage, exercise!.id!);
            await freshPage.getByRole('button', { name: 'Adapt exercise', exact: true }).click();
            const { jobId: rerunJobId } = await rerunStartPromise;
            await expectRunningGenerationStatus(freshPage, exercise!.id!, rerunJobId, 'ADAPT');
        } finally {
            await freshPage.context().close();
        }
    });
});

async function requireHyperionGenerationAvailable(page: Page) {
    const response = await page.request.get('/management/info');
    expect(response.ok(), 'Hyperion generation E2E needs management info to detect active features.').toBeTruthy();
    const info = await response.json();
    expect(info.activeModuleFeatures, 'Hyperion generation must be enabled in the dedicated mocked E2E environment.').toContain('hyperion-exercise-generation');
}

async function getExerciseVersionCount(page: Page, exerciseId: number): Promise<number> {
    return (await getExerciseVersions(page, exerciseId)).length;
}

async function getExerciseVersions(page: Page, exerciseId: number): Promise<{ id: number }[]> {
    const response = await page.request.get(`api/exercise/exercises/${exerciseId}/versions?size=100`);
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as { id: number }[];
}

async function getExerciseVersionSnapshot(page: Page, exerciseId: number, versionId: number): Promise<ExerciseVersionSnapshot> {
    const response = await page.request.get(`api/exercise/exercises/${exerciseId}/versions/${versionId}`);
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as ExerciseVersionSnapshot;
}

async function getLatestExerciseVersion(page: Page, exerciseId: number): Promise<{ id: number; snapshot: ExerciseVersionSnapshot }> {
    const [latestVersion] = await getExerciseVersions(page, exerciseId);
    if (!latestVersion) {
        throw new Error(`Exercise ${exerciseId} has no saved version`);
    }
    return { id: latestVersion.id, snapshot: await getExerciseVersionSnapshot(page, exerciseId, latestVersion.id) };
}

async function getReviewThreads(page: Page, exerciseId: number): Promise<ReviewThread[]> {
    const response = await page.request.get(`api/exercise/exercises/${exerciseId}/review-threads`);
    expect(response.ok()).toBeTruthy();
    return (await response.json()) as ReviewThread[];
}

// Only proves a review thread was created for this generation; the exact CONSISTENCY_CHECK wording is an implementation
// detail of the review-comment content owned elsewhere, not a contract this browser test needs to pin down.
async function expectGenerationReviewThread(page: Page, exerciseId: number, initialThreadIds: ReadonlySet<number>) {
    await expect.poll(async () => (await getReviewThreads(page, exerciseId)).some((thread) => !initialThreadIds.has(thread.id))).toBe(true);
}

async function openEditor(page: Page, login: (credentials: UserCredentials, url?: string) => Promise<void>, programmingExercise: ProgrammingExercise) {
    const exerciseId = programmingExercise.id;
    const repositoryId = programmingExercise.templateParticipation?.id;
    expect(exerciseId).toBeDefined();
    expect(repositoryId).toBeDefined();
    await login(instructor, `/course-management/${course.id}/programming-exercises/${exerciseId}/code-editor/TEMPLATE/${repositoryId}`);
    await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('hyperion-generation-empty')).toHaveCount(1, { timeout: 60_000 });
}

async function appendProblemStatementThroughUi(page: Page, marker: string) {
    const instructions = page.locator('jhi-programming-exercise-editable-instructions');
    const editor = instructions.locator('.monaco-editor');
    await expect(editor).toBeVisible();
    // Wait for the statement to finish loading into the editor: typing before the async load lands gets
    // wiped when the loaded content replaces the buffer (observed as a marker-not-visible flake).
    await expect.poll(async () => (await editor.locator('.view-lines').innerText()).trim().length, { timeout: 15_000 }).toBeGreaterThan(0);
    await editor.click();
    await page.keyboard.press('Control+End');
    await page.keyboard.press('Enter');
    await page.keyboard.press('Enter');
    await page.keyboard.type(marker);
    await expect(instructions.getByText(marker, { exact: true })).toBeVisible();
    await expect(instructions.getByText('Unsaved.', { exact: true })).toBeVisible();
}

async function editCodeFileThroughUi(page: Page, fileName: string, content: string) {
    const editor = await openCodeFileThroughUi(page, fileName);
    await setMonacoEditorContentByLocator(page, editor, content);
    // The 'Unsubmitted.' status flip is the oracle here; a raw .view-lines content check is redundant and Monaco-virtualization-flaky.
    await expect(page.getByText('Unsubmitted.', { exact: true })).toBeVisible();
}

/**
 * Opens the Monaco find widget for `text` and returns the still-focused find input so the caller can assert a match (or its
 * absence) and dismiss it. Reading the match-count indicator instead of `.view-lines` avoids depending on Monaco having
 * virtualization-rendered the exact matching line.
 */
async function searchMonacoEditorText(page: Page, editor: Locator, text: string): Promise<Locator> {
    await editor.locator('.monaco-editor').click();
    await page.keyboard.press('Control+f');
    const findInput = editor.getByRole('textbox', { name: 'Find', exact: true });
    await findInput.fill(text);
    return findInput;
}

async function expectCodeFileTextThroughUi(page: Page, fileName: string, text: string, present: boolean) {
    const editor = await openCodeFileThroughUi(page, fileName);
    const findInput = await searchMonacoEditorText(page, editor, text);
    if (present) {
        await expect(editor.locator('.matchesCount')).toHaveText(/^\d+ of \d+$/);
    } else {
        await expect(editor.getByText('No results', { exact: true })).toBeVisible();
    }
    await findInput.press('Escape');
}

async function openCodeFileThroughUi(page: Page, fileName: string) {
    await page.locator('jhi-code-editor-file-browser').getByText(fileName, { exact: true }).click();
    const editor = page.locator('jhi-code-editor-monaco:visible');
    await expect(editor.locator('jhi-code-editor-header')).toContainText(fileName);
    await expect(editor.locator('.monaco-editor')).toBeVisible();
    return editor;
}

async function expectProblemStatementTextThroughUi(page: Page, text: string) {
    const instructions = page.locator('jhi-programming-exercise-editable-instructions');
    await expect(instructions.locator('.monaco-editor')).toBeVisible();
    // getByText (not .view-lines.toContainText) so this doesn't depend on Monaco having virtualized-rendered the exact line.
    await expect(instructions.getByText(text)).toBeVisible();
}

async function expectHyperionTabSelected(page: Page) {
    await expect(page.getByTestId('editor-bottom-panel-tab')).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByTestId('editor-bottom-panel')).toBeVisible();
}

async function openHyperionTab(page: Page) {
    const tab = page.getByRole('tab', { name: 'AI activity' });
    await tab.click();
    await expect(tab).toHaveAttribute('aria-selected', 'true');
}

async function expectFileChangeNavigationDisabled(page: Page, fileName: string) {
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
    await openHyperionTab(page);
    const activity = page.getByTestId('hyperion-generation-activity');
    const fileButton = activity.getByRole('button', { name: fileName });
    const detailsToggle = activity.getByTestId('hyperion-generation-details-toggle');
    await expect(detailsToggle).toHaveAttribute('aria-expanded', /true|false/);
    if ((await detailsToggle.getAttribute('aria-expanded')) === 'false') {
        await detailsToggle.click();
    }
    await expect(detailsToggle).toHaveAttribute('aria-expanded', 'true');
    await expect(fileButton).toBeEnabled();
    const fileResponsePromise = page.waitForResponse(
        (response) =>
            response.request().method() === 'GET' &&
            response.ok() &&
            new URL(response.url()).pathname.endsWith('/file') &&
            new URL(response.url()).searchParams.get('file') === fileName,
        { timeout: 60_000 },
    );
    await fileButton.click();
    await expect(page).toHaveURL(/\/code-editor\/SOLUTION\//);
    await fileResponsePromise;
    const nativeEditor = page.locator('jhi-code-editor-monaco:visible');
    await expect(nativeEditor).toHaveCount(1);
    await expect(nativeEditor.locator('jhi-code-editor-header')).toContainText(fileName);
    const findInput = await searchMonacoEditorText(page, nativeEditor, expectedContent);
    await expect(nativeEditor.locator('.matchesCount')).toHaveText(/^\d+ of \d+$/);
    await findInput.press('Escape');
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
    expect(response.status()).toBe(200);
    return (await response.json()) as GenerationStatus;
}

async function expectNoRetainedGenerationStatus(page: Page, exerciseId: number) {
    const response = await page.request.get(`api/hyperion/programming-exercises/${exerciseId}/generate-exercise/status`);
    expect(response.status()).toBe(204);
}

async function expectRunningGenerationStatus(page: Page, exerciseId: number, jobId: string, mode: 'GENERATE' | 'ADAPT') {
    await expect
        .poll(
            async () => {
                const status = await getGenerationStatus(page, exerciseId);
                return {
                    jobId: status.jobId,
                    mode: status.mode,
                    running: status.running,
                    hasStarted: status.events.some((event) => event.type === 'STARTED'),
                };
            },
            { timeout: 60_000 },
        )
        .toEqual({ jobId, mode, running: true, hasStarted: true });
}

async function expectFileChangeStatus(page: Page, exerciseId: number, jobId: string) {
    await expect
        .poll(async () => {
            const status = await getGenerationStatus(page, exerciseId);
            const change = status.fileChanges?.find((candidate) => candidate.path.endsWith('HyperionPreview.java'));
            return {
                jobId: status.jobId,
                mode: status.mode,
                path: change?.path,
                repo: change?.repo,
                action: change?.action,
            };
        })
        .toEqual({
            jobId,
            mode: 'ADAPT',
            path: 'solution/src/de/test/HyperionPreview.java',
            repo: 'solution',
            action: 'write',
        });
}

async function expectTerminalGenerationStatus(page: Page, exerciseId: number, jobId: string, terminalType: 'CANCELLED' | 'ERROR') {
    await expect
        .poll(async () => {
            const status = await getGenerationStatus(page, exerciseId);
            const terminalEvents = status.events.filter((event) => ['CANCELLED', 'DONE', 'ERROR'].includes(event.type));
            return {
                jobId: status.jobId,
                running: status.running,
                cancellable: status.cancellable,
                terminalCount: terminalEvents.length,
                terminalType: terminalEvents[0]?.type,
                terminalIsLast: status.events.at(-1) === terminalEvents[0],
            };
        })
        .toEqual({ jobId, running: false, cancellable: false, terminalCount: 1, terminalType, terminalIsLast: true });
}

async function expectSuccessfulGenerationStatus(
    page: Page,
    exerciseId: number,
    jobId: string,
    mode: 'GENERATE' | 'ADAPT',
    expectedTestCount = 13,
    expectedCompletionStatus: 'SUCCESS' | 'NEEDS_REVIEW' = 'SUCCESS',
) {
    await expect
        .poll(
            async () => {
                const status = await getGenerationStatus(page, exerciseId);
                const terminalEvents = status.events.filter((event) => ['CANCELLED', 'DONE', 'ERROR'].includes(event.type));
                const terminal = terminalEvents[0];
                return {
                    jobId: status.jobId,
                    mode: status.mode,
                    running: status.running,
                    terminalCount: terminalEvents.length,
                    terminalType: terminal?.type,
                    terminalIsLast: status.events.at(-1) === terminal,
                    completionStatus: terminal?.completionStatus,
                    mechanicallyVerified: terminal?.verdict?.mechanicallyVerified,
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
            terminalCount: 1,
            terminalType: 'DONE',
            terminalIsLast: true,
            completionStatus: expectedCompletionStatus,
            mechanicallyVerified: true,
            solutionPassed: true,
            templateFailed: true,
            testCount: expectedTestCount,
            revertAvailable: true,
        });
}

/**
 * Lighter-weight sibling of {@link expectSuccessfulGenerationStatus} for call sites where the verdict fields (mechanical
 * verification, test count, ...) are already asserted through the verdict panel in the UI, so re-checking them via the raw
 * status API would just be theatre. Confirms the job reached the expected terminal completion status and stopped running.
 */
async function expectGenerationOutcomeStatus(
    page: Page,
    exerciseId: number,
    jobId: string,
    mode: 'GENERATE' | 'ADAPT',
    expectedCompletionStatus: 'SUCCESS' | 'NEEDS_REVIEW' = 'SUCCESS',
) {
    await expect
        .poll(
            async () => {
                const status = await getGenerationStatus(page, exerciseId);
                const terminal = [...status.events].reverse().find((event) => event.type === 'DONE');
                return { jobId: status.jobId, mode: status.mode, running: status.running, completionStatus: terminal?.completionStatus };
            },
            { timeout: 180_000 },
        )
        .toEqual({ jobId, mode, running: false, completionStatus: expectedCompletionStatus });
}

async function expectSavedExerciseVersion(page: Page, exerciseId: number, jobId: string, expectedChangedRepositories: string[]) {
    const status = await getGenerationStatus(page, exerciseId);
    const terminal = [...status.events].reverse().find((event) => event.type === 'DONE');
    expect(status.jobId).toBe(jobId);
    expect(terminal?.savedExerciseVersionId).toBeDefined();
    expect(Object.keys(terminal?.savedRepositoryCommits ?? {}).sort()).toEqual([...expectedChangedRepositories].sort());
    if (!terminal?.savedExerciseVersionId || !terminal.savedRepositoryCommits) {
        throw new Error(`Generation job ${jobId} did not expose its saved version and repository commits`);
    }
    for (const commit of Object.values(terminal.savedRepositoryCommits)) {
        expect(commit).toMatch(/^[0-9a-f]{40}$/i);
    }

    const versionId = terminal.savedExerciseVersionId;
    const [latestVersion] = await getExerciseVersions(page, exerciseId);
    if (!latestVersion) {
        throw new Error(`Exercise ${exerciseId} has no saved version`);
    }
    expect(latestVersion.id).toBe(versionId);
    const snapshot = await getExerciseVersionSnapshot(page, exerciseId, versionId);
    const snapshotCommits = getSnapshotRepositoryCommits(snapshot);
    for (const [repository, commit] of Object.entries(terminal.savedRepositoryCommits)) {
        expect(snapshotCommits[repository]).toBe(commit);
    }
    return { id: versionId, snapshot };
}

function getSnapshotRepositoryCommits(snapshot: ExerciseVersionSnapshot): Record<string, string | undefined> {
    return {
        template: snapshot.programmingData?.templateParticipation?.commitId,
        solution: snapshot.programmingData?.solutionParticipation?.commitId,
        tests: snapshot.programmingData?.testsCommitId,
    };
}

async function reviewSavedProblemStatementVersion(page: Page, versionId: number) {
    await page.locator('[data-testid="hyperion-generation-review-action"][data-review-target="problem-statement"]').click();
    await expect(page).toHaveURL(new RegExp(`/version-history\\?versionId=${versionId}$`));
    await expect(page.getByRole('button', { name: `Version ${versionId}` })).toHaveClass(/timeline-card--selected/);
    await expect(page.locator('.metadata-section--problem')).toContainText(correctedSeedStatementMarker);

    await page.goBack();
    await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
    await openHyperionTab(page);
}

async function expectDuplicateGenerationStartRejected(page: Page, exerciseId: number, runningJobId: string) {
    const duplicateStart = await page.request.post(`api/hyperion/programming-exercises/${exerciseId}/generate-exercise`, {
        data: { mode: 'GENERATE' },
    });

    expect(duplicateStart.status()).toBe(409);
    await expectRunningGenerationStatus(page, exerciseId, runningJobId, 'GENERATE');
}

async function getPendingLateFailureCount(page: Page): Promise<number> {
    const port = process.env.HYPERION_LLM_MOCK_PORT ?? '1234';
    const response = await page.request.get(`http://127.0.0.1:${port}/health`);
    expect(response.ok()).toBeTruthy();
    return ((await response.json()) as { pendingLateFailureCount?: number }).pendingLateFailureCount ?? 0;
}

async function releasePendingLateFailure(page: Page) {
    const port = process.env.HYPERION_LLM_MOCK_PORT ?? '1234';
    const response = await page.request.post(`http://127.0.0.1:${port}/release-late-failure`);
    expect(response.ok()).toBeTruthy();
    expect((await response.json()) as { released?: number }).toEqual({ released: 1 });
}

async function resetHyperionLlmMockScenario(page: Page) {
    const port = process.env.HYPERION_LLM_MOCK_PORT ?? '1234';
    const response = await page.request.post(`http://127.0.0.1:${port}/reset`);
    expect(response.ok()).toBeTruthy();
}

async function releaseHeldProviderResponseIfPresent(page: Page) {
    const port = process.env.HYPERION_LLM_MOCK_PORT ?? '1234';
    const healthResponse = await page.request.get(`http://127.0.0.1:${port}/health`);
    expect(healthResponse.ok()).toBeTruthy();
    const pending = ((await healthResponse.json()) as { pendingProviderResponseCount?: number }).pendingProviderResponseCount ?? 0;
    expect([0, 1]).toContain(pending);
    if (pending === 0) {
        return;
    }
    const releaseResponse = await page.request.post(`http://127.0.0.1:${port}/release-held-provider-response`);
    expect([200, 409, 502]).toContain(releaseResponse.status());
}

async function holdUnmatchedHyperionLlmRequests(page: Page) {
    const port = process.env.HYPERION_LLM_MOCK_PORT ?? '1234';
    const response = await page.request.post(`http://127.0.0.1:${port}/scenario`, { data: { holdUnmatchedRequests: true } });
    expect(response.ok()).toBeTruthy();
}

async function expectProviderResponseHeld(page: Page) {
    const port = process.env.HYPERION_LLM_MOCK_PORT ?? '1234';
    await expect
        .poll(
            async () => {
                const response = await page.request.get(`http://127.0.0.1:${port}/health`);
                expect(response.ok()).toBeTruthy();
                return ((await response.json()) as { pendingProviderResponseCount?: number }).pendingProviderResponseCount ?? 0;
            },
            { timeout: 60_000 },
        )
        .toBe(1);
}

async function expectEditorActionsLockedDuringGeneration(page: Page) {
    await expect(page.locator('#submit_button')).toBeDisabled();
    await expect(page.locator('#refresh_button')).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Create file on root level' })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Create folder on root level' })).toBeDisabled();
    await expect(page.locator('#dropdownBasic1')).toBeDisabled();
    await expect(page.locator('jhi-programming-exercise-student-trigger-build-button')).toHaveCount(0);
    await expectProblemStatementEditingLocked(page);
}

async function expectProblemStatementEditingLocked(page: Page) {
    const instructions = page.locator('jhi-programming-exercise-editable-instructions');
    const editorSurface = instructions.locator('.monaco-editor').first();
    if (!(await editorSurface.isVisible().catch(() => false))) {
        // While a generation runs the container replaces/hides the instructions editor entirely —
        // there is no editable surface to reach, which is the lock from the user's perspective.
        return;
    }
    const marker = 'HYPERION_E2E_LOCKED_EDIT_ATTEMPT';
    await editorSurface.click({ force: true });
    await page.keyboard.type(marker);
    await expect(instructions.getByText(marker, { exact: true })).toHaveCount(0);
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
    return (await getExerciseMetadata(page, exerciseId)).problemStatement;
}

async function getExerciseMetadata(page: Page, exerciseId: number): Promise<{ problemStatement: string; title: string }> {
    const response = await page.request.get(`api/programming/programming-exercises/${exerciseId}`);
    expect(response.ok()).toBeTruthy();
    const exercise = (await response.json()) as { problemStatement?: string; title?: string };
    return { problemStatement: exercise.problemStatement ?? '', title: exercise.title ?? '' };
}

// Only checks the solution-file update/staleness pair; template and test-repository content diffing for the same adaptation
// is exercised by the server-side integration tests, so re-asserting it here through the browser would just be theatre.
async function expectSemanticAdaptation(page: Page, exerciseId: number, adapted: boolean) {
    const expectedPolicyThreshold = adapted ? policyThresholdAfterAdaptation : policyThresholdBeforeAdaptation;
    const stalePolicyThreshold = adapted ? policyThresholdBeforeAdaptation : policyThresholdAfterAdaptation;
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
            }),
            { timeout: 90_000 },
        )
        .toEqual({ solutionUpdated: true, solutionStale: false });
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

async function getTestRepositoryFiles(page: Page, exerciseId: number): Promise<Record<string, string>> {
    const listResponse = await page.request.get(`api/programming/programming-exercises/${exerciseId}/test-repository/files`);
    expect(listResponse.ok()).toBeTruthy();
    const listing = (await listResponse.json()) as Record<string, 'FILE' | 'FOLDER'>;
    const files = Object.entries(listing)
        .filter(([, type]) => type === 'FILE')
        .map(([path]) => path);
    const entries = await Promise.all(
        files.map(async (path) => {
            const response = await page.request.get(`api/programming/programming-exercises/${exerciseId}/test-repository/file?file=${encodeURIComponent(path)}`);
            expect(response.ok(), `Could not read test repository file ${path}: HTTP ${response.status()}`).toBeTruthy();
            return [path, await response.text()] as const;
        }),
    );
    return Object.fromEntries(entries);
}

function repositoryFilesEqual(actual: Record<string, string>, expected: Record<string, string>): boolean {
    const actualPaths = Object.keys(actual).sort();
    const expectedPaths = Object.keys(expected).sort();
    return actualPaths.length === expectedPaths.length && actualPaths.every((path, index) => path === expectedPaths[index] && actual[path] === expected[path]);
}

async function revertMechanicallyVerifiedAdaptationFromUi(page: Page, exerciseId: number) {
    await expect(page.getByTestId('hyperion-generation-revert')).toBeVisible({ timeout: 60_000 });
    const revertResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().includes(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/revert`),
        { timeout: 120_000 },
    );
    await page.getByTestId('hyperion-generation-revert').click();
    await page.getByRole('dialog', { name: 'Undo the most recent saved adaptation?' }).getByRole('button', { name: 'Undo adaptation', exact: true }).click();
    const revertResponse = await revertResponsePromise;
    expect(revertResponse.ok()).toBeTruthy();
    await expectNoRetainedGenerationStatus(page, exerciseId);
    await expect(page.getByTestId('hyperion-generation-activity')).toBeHidden();
    await expect(page.getByTestId('hyperion-generation-revert')).toBeHidden();
}

async function cancelRunningJobFromUi(page: Page, exerciseId: number, jobId: string) {
    await expectProviderResponseHeld(page);
    const cancelResponsePromise = page.waitForResponse(
        (response) => response.request().method() === 'DELETE' && response.url().includes(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/jobs/${jobId}`),
        { timeout: 60_000 },
    );
    await page.getByTestId('hyperion-generation-cancel').click();
    const cancelResponse = await cancelResponsePromise;
    expect(cancelResponse.ok()).toBeTruthy();
    await expectCancelledGeneration(page, exerciseId, jobId);
    const terminalStatus = await getGenerationStatus(page, exerciseId);
    const versionCount = await getExerciseVersionCount(page, exerciseId);
    await releaseHeldProviderResponseIfPresent(page);
    await expectLateProviderResponseFenced(page, exerciseId, terminalStatus, versionCount);
    await resetHyperionLlmMockScenario(page);
}

async function expectLateProviderResponseFenced(page: Page, exerciseId: number, terminalStatus: GenerationStatus, versionCount: number) {
    let stableSamples = 0;
    const expectedEvents = JSON.stringify(terminalStatus.events);
    const expectedFileChanges = JSON.stringify(terminalStatus.fileChanges ?? []);
    await expect
        .poll(
            async () => {
                const currentStatus = await getGenerationStatus(page, exerciseId);
                const solutionFiles = await getRepositoryFiles(page, `api/programming/programming-exercises/${exerciseId}/solution-files-content?omitBinaries=true`);
                const unchanged =
                    JSON.stringify(currentStatus.events) === expectedEvents &&
                    JSON.stringify(currentStatus.fileChanges ?? []) === expectedFileChanges &&
                    (await getExerciseVersionCount(page, exerciseId)) === versionCount &&
                    solutionFiles['src/de/test/LateAfterCancellation.java'] === undefined;
                stableSamples = unchanged ? stableSamples + 1 : 0;
                return stableSamples;
            },
            { timeout: 10_000, intervals: [250, 500, 1_000, 1_000] },
        )
        .toBeGreaterThanOrEqual(5);
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
        const generationRow = generationSection.getByRole('row').filter({ hasText: exerciseId.toString() });
        await expect(generationRow).toContainText(agentName, { timeout: 60_000 });
        await generationRow.getByTestId('hyperion-generation-details').click();
        await expect(adminPage.getByTestId('admin-body').getByText(jobId, { exact: true })).toBeVisible({ timeout: 60_000 });
        await expect(adminPage.getByTestId('hyperion-container-id')).toHaveText(/\S+/);
        await expectProviderResponseHeld(adminPage);

        const cancelResponsePromise = adminPage.waitForResponse(
            (response) => response.request().method() === 'DELETE' && response.url().includes(`/api/admin/exercises/${exerciseId}/hyperion-generation-jobs/${jobId}/cancel`),
            { timeout: 60_000 },
        );
        await adminPage.getByRole('button', { name: 'Cancel generation', exact: true }).click();
        const confirmationDialog = adminPage.getByRole('dialog', { name: 'Cancel Hyperion generation' });
        await confirmationDialog.getByRole('button', { name: 'Cancel generation', exact: true }).click();
        expect((await cancelResponsePromise).ok()).toBeTruthy();
        await expect(adminPage.getByText(/The job ended and its sandbox was released after cancellation was requested/)).toBeVisible({ timeout: 60_000 });
        await expect
            .poll(
                async () => {
                    const response = await adminPage.request.get(`api/admin/build-agents/${encodeURIComponent(agentName)}/generation-sandboxes`);
                    if (!response.ok()) {
                        return undefined;
                    }
                    const jobs = (await response.json()) as Array<{ jobId: string }>;
                    return jobs.some((job) => job.jobId === jobId);
                },
                { timeout: 60_000 },
            )
            .toBe(false);
        const terminalStatus = await getGenerationStatus(adminPage, exerciseId);
        const versionCount = await getExerciseVersionCount(adminPage, exerciseId);
        await releaseHeldProviderResponseIfPresent(adminPage);
        await expectLateProviderResponseFenced(adminPage, exerciseId, terminalStatus, versionCount);
        await adminPage.getByTestId('back-to-build-agent').click();
        await expect(adminPage).toHaveURL(/\/admin\/build-agents\/details\?agentName=/);
        const refreshedGenerationSection = adminPage.locator('#active-hyperion-generations');
        await expect(refreshedGenerationSection).toContainText('No active Hyperion generation jobs on this agent.', { timeout: 60_000 });
        await expect(refreshedGenerationSection.getByRole('row').filter({ hasText: exerciseId.toString() })).toHaveCount(0);
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
        await openHyperionTab(freshPage);
        await expectHyperionTabSelected(freshPage);
        const activity = freshPage.getByTestId('hyperion-generation-activity');
        await expect(activity).toContainText('HyperionPreview.java', { timeout: 60_000 });
        await expectFileChangeNavigationDisabled(freshPage, 'HyperionPreview.java');
        await expect(freshPage.getByTestId('hyperion-ai-menu')).toBeEnabled();
        await expect(freshPage.getByTestId('hyperion-generation-cancel')).toBeVisible();
    } finally {
        await freshPage.context().close();
    }
}

async function expectTerminalOutcomeOnFreshAuthorizedPage(browser: Browser, programmingExercise: ProgrammingExercise, jobId: string, savedVersionId: number) {
    const exerciseId = programmingExercise.id;
    const repositoryId = programmingExercise.templateParticipation?.id;
    expect(exerciseId).toBeDefined();
    expect(repositoryId).toBeDefined();
    const freshPage = await newBrowserPage(browser);
    try {
        await Commands.login(freshPage, admin, `/course-management/${course.id}/programming-exercises/${exerciseId}/code-editor/TEMPLATE/${repositoryId}`);
        await expect(freshPage.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
        // The invisible-side-effect oracle (saved version id + completion status) is enough here; the UI assertions below already
        // prove the terminal outcome is rendered correctly for a freshly-authorized, non-owning viewer.
        await expect
            .poll(async () => {
                const status = await getGenerationStatus(freshPage, exerciseId!);
                const terminal = [...status.events].reverse().find((event) => event.type === 'DONE');
                return { jobId: status.jobId, running: status.running, completionStatus: terminal?.completionStatus, savedExerciseVersionId: terminal?.savedExerciseVersionId };
            })
            .toEqual({ jobId, running: false, completionStatus: 'SUCCESS', savedExerciseVersionId: savedVersionId });
        await openHyperionTab(freshPage);
        const activity = freshPage.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible();
        await expect(activity.getByTestId('hyperion-generation-persistence-state')).toContainText('Saved to exercise');
        await expect(activity.getByTestId('hyperion-generation-terminal-message')).toContainText('adapted and saved');
        await expect(activity.getByTestId('hyperion-generation-verdict')).toBeVisible();
        await expect(activity.getByTestId('hyperion-generation-review')).toBeVisible();
        await expect(freshPage.getByTestId('hyperion-generation-cancel')).toHaveCount(0);
        await expect(freshPage.getByTestId('hyperion-generation-run-again')).toHaveCount(0);
    } finally {
        await freshPage.context().close();
    }
}
