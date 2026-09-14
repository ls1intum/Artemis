import { request as playwrightRequest, expect } from '@playwright/test';

import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

import cAllSuccessful from '../../fixtures/exercise/programming/c/all_successful/submission.json';
import { ProgrammingLanguage } from '../../support/constants';
import { test } from '../../support/fixtures';
import { ProgrammingExerciseOverviewPage } from '../../support/pageobjects/exercises/programming/ProgrammingExerciseOverviewPage';
import { SEED_COURSES } from '../../support/seedData';
import { admin, studentOne } from '../../support/users';

const course = { id: SEED_COURSES.programmingParticipation.id } as Course;
const ACCEPTANCE_TIMEOUT = 5 * 60_000;

interface BuildAgentInformation {
    buildAgent: { name: string };
    maxNumberOfConcurrentBuildJobs: number;
    buildAgentDetails?: {
        buildRunner?: string;
        buildRunnerVersion?: string;
        totalBuilds?: number;
    };
}

interface BuildJob {
    id: string;
    participationId: number;
    exerciseId: number;
    status?: string;
    jobTimingInfo?: {
        submissionDate?: string;
    };
}

test.describe('Kubernetes LocalCI execution', { tag: ['@multi-node', '@kubernetes'] }, () => {
    test('preserves queue estimates, results, and cancellation across native Jobs', async ({
        page,
        login,
        exerciseAPIRequests,
        programmingExerciseOverview,
        waitForParticipationBuildToFinish,
    }) => {
        test.setTimeout(10 * 60_000);
        await login(admin);

        const agentResponse = await page.request.get('/api/admin/build-agents');
        expect(agentResponse.ok(), `Could not read build agents: HTTP ${agentResponse.status()}`).toBeTruthy();
        const agents = (await agentResponse.json()) as BuildAgentInformation[];
        expect(agents).toHaveLength(2);
        expect(agents.every((agent) => agent.maxNumberOfConcurrentBuildJobs === 1)).toBeTruthy();
        expect(agents.every((agent) => agent.buildAgentDetails?.buildRunner === 'Kubernetes')).toBeTruthy();
        expect(agents.every((agent) => Boolean(agent.buildAgentDetails?.buildRunnerVersion))).toBeTruthy();

        const adminRequest = await playwrightRequest.newContext({
            baseURL: process.env.BASE_URL ?? 'http://127.0.0.1:8080',
            storageState: await page.context().storageState(),
        });
        let exercise: ProgrammingExercise | undefined;

        try {
            const buildPlan = {
                phases: [
                    {
                        name: 'KubernetesAcceptance',
                        script: [
                            'echo "Kubernetes LocalCI acceptance build started"',
                            'sleep 45',
                            'mkdir -p results',
                            'printf \'%s\\n\' \'<?xml version="1.0" encoding="UTF-8"?>\' \'<testsuite name="KubernetesLocalCI" tests="1" failures="0" errors="0" skipped="0"><testcase name="works" classname="KubernetesLocalCI" time="0.001"/></testsuite>\' > results/TEST-kubernetes-localci.xml',
                            'echo "Kubernetes LocalCI acceptance build finished"',
                        ].join('\n'),
                        condition: 'ALWAYS',
                        forceRun: false,
                        resultPaths: ['results/*.xml'],
                    },
                ],
                dockerImage: 'ubuntu:24.04',
            };
            exercise = await exerciseAPIRequests.createProgrammingExercise({
                course,
                programmingLanguage: ProgrammingLanguage.C,
                buildPlanConfiguration: JSON.stringify(buildPlan),
            });
            expect(exercise.id).toBeDefined();

            await expect
                .poll(
                    async () => {
                        const response = await adminRequest.get('/api/admin/running-jobs');
                        if (!response.ok()) {
                            return -1;
                        }
                        const jobs = (await response.json()) as BuildJob[];
                        return jobs.filter((job) => job.exerciseId === exercise!.id).length;
                    },
                    { timeout: ACCEPTANCE_TIMEOUT, intervals: [1_000, 2_000, 5_000] },
                )
                .toBe(2);

            const participationId = await programmingExerciseOverview.startParticipation(course.id!, exercise.id!, studentOne);
            await exerciseAPIRequests.makeProgrammingExerciseSubmission(participationId, cAllSuccessful);

            let queuedJob: BuildJob | undefined;
            await expect
                .poll(
                    async () => {
                        const response = await adminRequest.get('/api/admin/queued-jobs');
                        if (!response.ok()) {
                            return false;
                        }
                        const jobs = (await response.json()) as BuildJob[];
                        queuedJob = jobs
                            .filter((job) => job.participationId === participationId)
                            .sort((left, right) => (left.jobTimingInfo?.submissionDate ?? '').localeCompare(right.jobTimingInfo?.submissionDate ?? ''))
                            .at(-1);
                        return queuedJob !== undefined;
                    },
                    { timeout: 30_000, intervals: [500, 1_000, 2_000] },
                )
                .toBeTruthy();

            let estimatedStart = 0;
            await expect
                .poll(
                    async () => {
                        const response = await page.request.get('/api/localci/queued-jobs/queue-duration-estimation', {
                            params: { participationId },
                        });
                        if (!response.ok()) {
                            return -1;
                        }
                        estimatedStart = Date.parse((await response.json()) as string);
                        return estimatedStart - Date.now();
                    },
                    { timeout: 30_000, intervals: [500, 1_000, 2_000] },
                )
                .toBeGreaterThan(1_000);
            expect(estimatedStart).toBeGreaterThan(Date.now());

            const participation = await waitForParticipationBuildToFinish(participationId, 1_000, ACCEPTANCE_TIMEOUT, null);
            ProgrammingExerciseOverviewPage.verifyResultScore(participation, cAllSuccessful.expectedResult);

            await expect
                .poll(
                    async () => {
                        const response = await adminRequest.get(`/api/admin/build-jobs/${queuedJob!.id}`);
                        if (!response.ok()) {
                            return `HTTP_${response.status()}`;
                        }
                        return ((await response.json()) as BuildJob).status;
                    },
                    { timeout: 60_000, intervals: [500, 1_000, 2_000] },
                )
                .toBe('SUCCESSFUL');

            const buildLogResponse = await adminRequest.get(`/api/localci/build-jobs/${queuedJob!.id}/build-log`);
            expect(buildLogResponse.ok(), `Could not read Kubernetes build logs: HTTP ${buildLogResponse.status()}`).toBeTruthy();
            expect(await buildLogResponse.text()).toContain('Kubernetes LocalCI acceptance build finished');

            const triggerResponse = await page.request.post(`/api/programming/participations/${participationId}/trigger-build`, {
                params: { submissionType: 'MANUAL' },
                data: {},
            });
            expect(triggerResponse.ok(), `Could not trigger the cancellation build: HTTP ${triggerResponse.status()}`).toBeTruthy();

            let runningJob: BuildJob | undefined;
            await expect
                .poll(
                    async () => {
                        const response = await adminRequest.get('/api/admin/running-jobs');
                        if (!response.ok()) {
                            return false;
                        }
                        const jobs = (await response.json()) as BuildJob[];
                        runningJob = jobs.find((job) => job.participationId === participationId);
                        return runningJob !== undefined;
                    },
                    { timeout: 60_000, intervals: [500, 1_000, 2_000] },
                )
                .toBeTruthy();

            const cancellationResponse = await adminRequest.delete(`/api/admin/build-jobs/${runningJob!.id}/cancel`);
            expect(cancellationResponse.status()).toBe(204);
            await expect
                .poll(
                    async () => {
                        const response = await adminRequest.get(`/api/admin/build-jobs/${runningJob!.id}`);
                        if (!response.ok()) {
                            return `HTTP_${response.status()}`;
                        }
                        return ((await response.json()) as BuildJob).status;
                    },
                    { timeout: 60_000, intervals: [500, 1_000, 2_000] },
                )
                .toBe('CANCELLED');
        } finally {
            if (exercise?.id !== undefined) {
                await adminRequest.delete(`/api/programming/programming-exercises/${exercise.id}`, {
                    params: { deleteStudentReposBuildPlans: true, deleteBaseReposBuildPlans: true },
                });
            }
            await adminRequest.dispose();
        }
    });
});
