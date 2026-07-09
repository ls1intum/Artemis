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

type GenerationStatus = {
    jobId: string;
    running: boolean;
    mode?: 'GENERATE' | 'ADAPT';
    events: any[];
    fileSnapshots?: any[];
};

type HyperionRoutes = {
    startedRequests: GenerationRequest[];
    cancelledJobs: string[];
    revertRequests: number;
    setStatus: (status: GenerationStatus | undefined) => void;
};

test.describe('Hyperion exercise generation browser UI', { tag: '@slow' }, () => {
    test.use({ serviceWorkers: 'block' });
    let exercise: ProgrammingExercise | undefined;

    test.beforeEach('Create unreleased Java programming exercise', async ({ login, exerciseAPIRequests }) => {
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
    });

    test.afterEach('Delete programming exercise', async ({ login, exerciseAPIRequests }) => {
        if (exercise?.id) {
            await login(admin);
            await exerciseAPIRequests.deleteProgrammingExercise(exercise.id);
            exercise = undefined;
        }
    });

    test('starts generation from the real editor UI, locks the action menu, and cancels the running job', async ({ page, login }) => {
        test.setTimeout(180_000);
        const routes = await installHyperionRoutes(page, exercise!.id!, 'hyperion-generate-job');

        await openEditor(page, login, exercise!);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-generate-exercise').click();

        await expect.poll(() => routes.startedRequests).toHaveLength(1);
        expect(routes.startedRequests[0]).toEqual({ mode: 'GENERATE' });

        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible();
        await expect(activity).toContainText('Generating');
        await expect(page.getByTestId('hyperion-ai-menu')).toBeDisabled();

        await page.getByTestId('hyperion-generation-cancel').click();
        await expect.poll(() => routes.cancelledJobs).toEqual(['hyperion-generate-job']);
        await expect(page.getByTestId('hyperion-generation-cancel').locator('button')).toBeDisabled();
    });

    test('rehydrates retained progress after reload and keeps same-path snapshots separate per repository', async ({ page, login }) => {
        test.setTimeout(180_000);
        const routes = await installHyperionRoutes(page, exercise!.id!, 'unused');
        routes.setStatus(successfulGenerationStatus('retained-generate-job'));

        await openEditor(page, login, exercise!);

        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible();
        await expect(activity.getByTestId('hyperion-generation-completion-status')).toContainText('Saved');
        await expect(activity.getByTestId('hyperion-generation-verdict')).toContainText('Accepted');
        await expect(activity.getByText('Drafting Counter.java')).toBeVisible();
        await expect(activity.getByTestId('hyperion-generation-file')).toHaveCount(2);
        await expect(activity.getByTestId('hyperion-generation-repo').filter({ hasText: 'Solution' }).getByTestId('hyperion-generation-file')).toContainText(
            'src/main/java/de/test/Counter.java',
        );
        await expect(activity.getByTestId('hyperion-generation-repo').filter({ hasText: 'Template' }).getByTestId('hyperion-generation-file')).toContainText(
            'src/main/java/de/test/Counter.java',
        );
    });

    test('starts adaptation from the browser dialog with instructor instructions', async ({ page, login }) => {
        test.setTimeout(180_000);
        const routes = await installHyperionRoutes(page, exercise!.id!, 'hyperion-adapt-job');

        await openEditor(page, login, exercise!);

        await page.getByTestId('hyperion-ai-menu').click();
        await page.getByTestId('hyperion-adapt-with-feedback').click();
        await page.getByLabel('Additional instructions').fill('Make the edge-case requirements explicit and keep the tests deterministic.');
        await page.getByRole('button', { name: 'Adapt exercise', exact: true }).click();

        await expect.poll(() => routes.startedRequests).toHaveLength(1);
        expect(routes.startedRequests[0]).toEqual({
            mode: 'ADAPT',
            prompt: 'Make the edge-case requirements explicit and keep the tests deterministic.',
        });
        await expect(page.getByTestId('hyperion-generation-activity')).toContainText('Adapting');
    });

    test('shows accepted adaptation replay and reverts it from the browser UI', async ({ page, login }) => {
        test.setTimeout(180_000);
        const routes = await installHyperionRoutes(page, exercise!.id!, 'unused');
        routes.setStatus(successfulAdaptationStatus('retained-adapt-job'));

        await openEditor(page, login, exercise!);

        const activity = page.getByTestId('hyperion-generation-activity');
        await expect(activity).toBeVisible();
        await expect(activity.getByTestId('hyperion-generation-completion-status')).toContainText('Saved');
        await expect(activity.getByTestId('hyperion-generation-revert')).toBeVisible();

        await activity.getByTestId('hyperion-generation-revert').click();
        await expect.poll(() => routes.revertRequests).toBe(1);
        await expect(activity.getByTestId('hyperion-generation-reverted')).toBeVisible();
    });
});

async function openEditor(page: Page, login: (credentials: UserCredentials, url?: string) => Promise<void>, programmingExercise: ProgrammingExercise) {
    const exerciseId = programmingExercise.id;
    const repositoryId = programmingExercise.templateParticipation?.id;
    expect(exerciseId).toBeDefined();
    expect(repositoryId).toBeDefined();
    await login(instructor, `/course-management/${course.id}/programming-exercises/${exerciseId}/code-editor/TEMPLATE/${repositoryId}`);
    await expect(page.getByTestId('hyperion-ai-menu')).toBeVisible({ timeout: 60_000 });
}

async function installHyperionRoutes(page: Page, exerciseId: number, jobId: string): Promise<HyperionRoutes> {
    let currentStatus: GenerationStatus | undefined;
    const startedRequests: GenerationRequest[] = [];
    const cancelledJobs: string[] = [];
    let revertRequests = 0;

    await page.route('**/management/info', async (route) => {
        const response = await route.fetch();
        const body = await response.json();
        body.activeProfiles = withEntry(body.activeProfiles, 'localci');
        body.activeModuleFeatures = withEntry(body.activeModuleFeatures, 'hyperion');
        await route.fulfill({ response, json: body });
    });

    await page.route(`**/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/status`, async (route) => {
        if (route.request().method() !== 'GET') {
            throw new Error(`Unexpected Hyperion status method: ${route.request().method()}`);
        }
        if (!currentStatus) {
            await route.fulfill({ status: 204, body: '' });
            return;
        }
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(currentStatus) });
    });

    await page.route(`**/api/hyperion/programming-exercises/${exerciseId}/generate-exercise`, async (route) => {
        if (route.request().method() !== 'POST') {
            throw new Error(`Unexpected Hyperion start method: ${route.request().method()}`);
        }
        const request = route.request().postDataJSON() as GenerationRequest;
        startedRequests.push(request);
        currentStatus = runningStatus(jobId, request.mode ?? 'GENERATE');
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ jobId }) });
    });

    await page.route(`**/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/jobs/*`, async (route) => {
        if (route.request().method() !== 'DELETE') {
            throw new Error(`Unexpected Hyperion cancel method: ${route.request().method()}`);
        }
        cancelledJobs.push(route.request().url().split('/').at(-1)!);
        await route.fulfill({ status: 200, body: '' });
    });

    await page.route(`**/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/revert-adaptation`, async (route) => {
        if (route.request().method() !== 'POST') {
            throw new Error(`Unexpected Hyperion revert method: ${route.request().method()}`);
        }
        revertRequests++;
        await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ fullyReverted: true, revertedRepositories: ['template', 'solution', 'tests'] }),
        });
    });

    return {
        startedRequests,
        cancelledJobs,
        get revertRequests() {
            return revertRequests;
        },
        setStatus: (status) => {
            currentStatus = status;
        },
    };
}

function withEntry(values: string[] | undefined, entry: string): string[] {
    const merged = new Set(values ?? []);
    merged.add(entry);
    return [...merged];
}

function runningStatus(jobId: string, mode: 'GENERATE' | 'ADAPT'): GenerationStatus {
    return {
        jobId,
        running: true,
        mode,
        events: [
            {
                type: 'STARTED',
                message: mode === 'ADAPT' ? 'Starting adaptation' : 'Starting generation',
                timestamp: '2026-01-01T10:00:00Z',
            },
        ],
        fileSnapshots: [],
    };
}

function successfulGenerationStatus(jobId: string): GenerationStatus {
    return {
        jobId,
        running: false,
        mode: 'GENERATE',
        events: [
            { type: 'STARTED', message: 'Starting generation', timestamp: '2026-01-01T10:00:00Z' },
            { type: 'PROGRESS', message: 'Drafting Counter.java', timestamp: '2026-01-01T10:00:01Z' },
            {
                type: 'DONE',
                message: 'Saved generated exercise',
                completionStatus: 'SUCCESS',
                liveExerciseChanged: false,
                verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 4 },
                timestamp: '2026-01-01T10:00:02Z',
            },
        ],
        fileSnapshots: [
            snapshot('solution', 'public class Counter { int increment() { return 1; } }'),
            snapshot('template', 'public class Counter { int increment() { return 0; } }'),
        ],
    };
}

function successfulAdaptationStatus(jobId: string): GenerationStatus {
    return {
        ...successfulGenerationStatus(jobId),
        mode: 'ADAPT',
        events: [
            { type: 'STARTED', message: 'Starting adaptation', timestamp: '2026-01-01T10:00:00Z' },
            {
                type: 'DONE',
                message: 'Saved adapted exercise',
                completionStatus: 'SUCCESS',
                liveExerciseChanged: false,
                verdict: { accepted: true, solutionPassed: true, templateFailed: true, testCount: 4 },
                timestamp: '2026-01-01T10:00:02Z',
            },
        ],
    };
}

function snapshot(repo: 'solution' | 'template', content: string) {
    return {
        type: 'FILE_SNAPSHOT',
        repo,
        action: 'edit',
        path: 'src/main/java/de/test/Counter.java',
        content,
        sha256: `${repo}-sha`,
        bytes: content.length,
        truncated: false,
        turn: repo === 'solution' ? 1 : 2,
        timestamp: repo === 'solution' ? '2026-01-01T10:00:01Z' : '2026-01-01T10:00:02Z',
    };
}
