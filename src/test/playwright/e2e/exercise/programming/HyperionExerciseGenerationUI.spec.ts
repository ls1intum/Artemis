import dayjs from 'dayjs';
import { expect, Page } from '@playwright/test';

import { test } from '../../../support/fixtures';
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
    test.describe.configure({ mode: 'serial' });
    test.use({ serviceWorkers: 'block' });

    let exercise: ProgrammingExercise | undefined;
    let runningJobId: string | undefined;

    test.beforeEach('Create unreleased Java programming exercise', async ({ login, page, exerciseAPIRequests }) => {
        await assertHyperionGenerationEnabled(page);
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

    test('starts generation through the real Hyperion backend and cancels the running job', async ({ page, login }) => {
        test.setTimeout(180_000);
        await openEditor(page, login, exercise!);

        const { jobId, request } = await startGenerationFromMenu(page, exercise!.id!);
        runningJobId = jobId;

        expect(request).toEqual({ mode: 'GENERATE' });
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible();
        await expect(activity).toContainText('Generating');
        await expect(page.getByTestId('hyperion-ai-menu')).toBeDisabled();

        await cancelRunningJobFromUi(page, exercise!.id!, jobId);
        runningJobId = undefined;
    });

    test('rehydrates real retained progress after reload', async ({ page, login }) => {
        test.setTimeout(180_000);
        await openEditor(page, login, exercise!);

        const { jobId } = await startGenerationFromMenu(page, exercise!.id!);
        runningJobId = jobId;
        await expect(page.getByTestId('hyperion-generation-activity')).toBeVisible();

        await page.reload();
        await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible();
        await expect(activity).toContainText('Starting exercise generation');

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
});

async function assertHyperionGenerationEnabled(page: Page) {
    const response = await page.request.get('/management/info');
    expect(response.ok()).toBeTruthy();
    const info = await response.json();
    expect(info.activeProfiles).toContain('localci');
    expect(info.activeProfiles).toContain('core');
    expect(info.activeModuleFeatures).toContain('hyperion');
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
