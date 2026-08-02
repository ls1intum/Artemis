import dayjs from 'dayjs';
import { expect, Page } from '@playwright/test';

import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { test } from '../../../support/fixtures';
import { admin, instructor } from '../../../support/users';
import { SEED_COURSES } from '../../../support/seedData';
import { ProgrammingLanguage } from '../../../support/constants';
import { fillDateTimePicker, generateUUID } from '../../../support/utils';

const courseId = SEED_COURSES.programmingManagement.id;
const brief = process.env.HYPERION_CHECKPOINT_BRIEF;

type GenerationEvent = {
    type: 'STARTED' | 'PROGRESS' | 'DONE' | 'CANCELLED' | 'ERROR';
    message?: string;
    completionStatus?: 'SUCCESS' | 'NEEDS_REVIEW' | 'PARTIAL';
};

type GenerationStatus = {
    jobId?: string;
    running?: boolean;
    events?: GenerationEvent[];
};

test.describe('Hyperion checkpoint driver', { tag: '@slow' }, () => {
    test.skip(process.env.HYPERION_LLM_MODE !== 'live', 'Checkpoint recording requires HYPERION_LLM_MODE=live.');
    test.describe.configure({ retries: 0 });
    test.use({ serviceWorkers: 'block' });

    test('records or replays one generation run', async ({ page, login, exerciseAPIRequests, programmingExerciseCreation }) => {
        test.setTimeout(3_600_000);
        if (!brief) {
            throw new Error('HYPERION_CHECKPOINT_BRIEF must contain the instructor brief.');
        }

        let exercise: ProgrammingExercise | undefined;
        let jobId: string | undefined;
        let runError: unknown;
        let cleanupError: unknown;
        try {
            await login(instructor, `/course-management/${courseId}/programming-exercises/new`);
            await programmingExerciseCreation.waitForFormToLoad();
            await programmingExerciseCreation.changeEditMode();
            await programmingExerciseCreation.setProgrammingLanguage(ProgrammingLanguage.JAVA);
            await page.locator('#field_projectType').getByText('Maven', { exact: true }).click();
            await programmingExerciseCreation.setTitle(process.env.HYPERION_CHECKPOINT_EXERCISE_TITLE ?? `hyp-checkpoint-${generateUUID()}`);
            await programmingExerciseCreation.setShortName(process.env.HYPERION_CHECKPOINT_EXERCISE_SHORT_NAME ?? `hypcp${generateUUID()}`);
            await programmingExerciseCreation.setPoints(100);
            await page.locator('#field_bonusPoints').fill('0');
            await fillDateTimePicker(page.getByLabel('Release Date', { exact: true }), dayjs().add(2, 'days'));
            await programmingExerciseCreation.setDueDate(dayjs().add(3, 'days'));

            const statement = process.env.HYPERION_CHECKPOINT_EXERCISE_STATEMENT;
            if (statement !== undefined) {
                await seedProblemStatement(page, statement);
            } else {
                await expect(page.locator('#userPrompt')).toBeVisible({ timeout: 90_000 });
                await page.locator('#userPrompt').fill(brief);
            }
            await programmingExerciseCreation.setPackageName(process.env.HYPERION_CHECKPOINT_EXERCISE_PACKAGE ?? 'de.tum.cit.aet.checkpoint');

            const setupResponsePromise = page.waitForResponse(
                (response) => response.request().method() === 'POST' && response.url().includes('/api/programming/programming-exercises/setup?emptyRepositories=true'),
                { timeout: 480_000 },
            );
            const startResponsePromise = page.waitForResponse(
                (response) => response.request().method() === 'POST' && /\/api\/hyperion\/programming-exercises\/\d+\/generate-exercise$/.test(response.url()),
                { timeout: 480_000 },
            );
            const generationEndpoint = '**/api/hyperion/programming-exercises/*/generate-exercise';
            if (statement !== undefined) {
                await page.route(generationEndpoint, async (route) => {
                    const body = route.request().postDataJSON() as { mode: string; prompt?: string };
                    await route.continue({ postData: JSON.stringify({ ...body, prompt: '' }) });
                });
            }

            await page.locator('#generate-with-ai').click();
            const setupResponse = await setupResponsePromise;
            expect(setupResponse.ok()).toBeTruthy();
            exercise = (await setupResponse.json()) as ProgrammingExercise;
            const startResponse = await startResponsePromise;
            await page.unroute(generationEndpoint).catch(() => undefined);
            expect(startResponse.status()).toBe(202);
            jobId = ((await startResponse.json()) as { jobId: string }).jobId;

            const terminal = await waitForTerminalStatus(page, exercise.id!, jobId);
            expect(terminal.type, terminal.message).toBe('DONE');
            expect(['SUCCESS', 'NEEDS_REVIEW']).toContain(terminal.completionStatus);
            jobId = undefined;
        } catch (error) {
            runError = error;
        } finally {
            try {
                if (!jobId && exercise?.id) {
                    const status = await getGenerationStatus(page, exercise.id);
                    if (status.running) {
                        jobId = status.jobId;
                    }
                }
                if (jobId && exercise?.id) {
                    const response = await page.request.delete(`/api/hyperion/programming-exercises/${exercise.id}/generate-exercise/jobs/${jobId}`);
                    expect([200, 204, 404]).toContain(response.status());
                }
                if (exercise?.id) {
                    await expect.poll(async () => (await getGenerationStatus(page, exercise!.id!)).running, { timeout: 90_000 }).toBe(false);
                    await login(admin);
                    await exerciseAPIRequests.deleteProgrammingExercise(exercise.id);
                    await expect.poll(async () => (await page.request.get(`/api/programming/programming-exercises/${exercise!.id}`)).status()).toBe(404);
                }
            } catch (error) {
                cleanupError = error;
            }
        }
        if (runError) {
            throw runError;
        }
        if (cleanupError) {
            throw cleanupError;
        }
    });
});

async function seedProblemStatement(page: Page, statement: string) {
    const endpoint = '**/api/hyperion/courses/*/problem-statements/generate';
    try {
        await page.route(endpoint, async (route) => {
            await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ draftProblemStatement: statement }) });
        });
        await expect(page.locator('#userPrompt')).toBeVisible({ timeout: 90_000 });
        await page.locator('#userPrompt').fill('Use the prepared problem statement.');
        await page.getByRole('button', { name: 'Generate Draft Problem Statement' }).click();
        await expect.poll(async () => (await readProblemStatementEditor(page))?.trim(), { timeout: 60_000 }).toBe(statement.trim());
    } finally {
        await page.unroute(endpoint);
    }
}

async function readProblemStatementEditor(page: Page): Promise<string | undefined> {
    const editor = page.locator('jhi-programming-exercise-editable-instructions .monaco-editor').first();
    await editor.waitFor({ state: 'visible' });
    const editorHandle = await editor.elementHandle();
    if (!editorHandle) {
        return undefined;
    }
    return page.evaluate((editorNode) => {
        const editors = (window as any).monaco?.editor?.getEditors?.() ?? [];
        const match = editors.find((candidate: any) => {
            const node = candidate.getDomNode?.();
            return node && (node === editorNode || node.contains(editorNode) || editorNode.contains(node));
        });
        return match?.getValue?.();
    }, editorHandle);
}

async function getGenerationStatus(page: Page, exerciseId: number): Promise<GenerationStatus> {
    const response = await page.request.get(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/status`);
    return response.status() === 404 ? { running: false } : ((await response.json()) as GenerationStatus);
}

async function waitForTerminalStatus(page: Page, exerciseId: number, jobId: string) {
    let terminal: GenerationEvent | undefined;
    await expect
        .poll(
            async () => {
                const response = await page.request.get(`/api/hyperion/programming-exercises/${exerciseId}/generate-exercise/status`);
                expect(response.ok()).toBeTruthy();
                const status = (await response.json()) as GenerationStatus;
                terminal = status.jobId === jobId ? [...(status.events ?? [])].reverse().find((event) => ['DONE', 'ERROR', 'CANCELLED'].includes(event.type)) : undefined;
                return terminal?.type;
            },
            { timeout: 3_600_000, intervals: [5_000, 10_000, 15_000] },
        )
        .toBeDefined();
    return terminal!;
}
