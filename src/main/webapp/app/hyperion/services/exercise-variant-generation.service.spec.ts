import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { ExerciseVariantWebsocketService, VariantGenerationEvent } from 'app/hyperion/services/exercise-variant-websocket.service';
import { HyperionExerciseVariantApi } from 'app/openapi/api/hyperion-exercise-variant-api';
import { VariantJob } from 'app/openapi/model/variant-job';

/**
 * Vitest specs for ExerciseVariantGenerationService.
 */
describe('ExerciseVariantGenerationService', () => {
    setupTestBed({ zoneless: true });

    let service: ExerciseVariantGenerationService;
    let apiMock: {
        generateVariant: ReturnType<typeof vi.fn>;
        getJobsOfCurrentUser: ReturnType<typeof vi.fn>;
        getJobDetail: ReturnType<typeof vi.fn>;
        cancelJob: ReturnType<typeof vi.fn>;
    };
    let websocketMock: {
        subscribeToJob: ReturnType<typeof vi.fn>;
        unsubscribeFromJob: ReturnType<typeof vi.fn>;
    };
    let eventSubjects: Map<string, Subject<VariantGenerationEvent>>;

    beforeEach(() => {
        eventSubjects = new Map();
        apiMock = {
            generateVariant: vi.fn(),
            getJobsOfCurrentUser: vi.fn(),
            getJobDetail: vi.fn(),
            cancelJob: vi.fn(),
        };
        websocketMock = {
            subscribeToJob: vi.fn((jobId: string) => {
                if (!eventSubjects.has(jobId)) {
                    eventSubjects.set(jobId, new Subject<VariantGenerationEvent>());
                }
                return eventSubjects.get(jobId)!.asObservable();
            }),
            unsubscribeFromJob: vi.fn(),
        };
        TestBed.configureTestingModule({
            providers: [
                ExerciseVariantGenerationService,
                { provide: HyperionExerciseVariantApi, useValue: apiMock },
                { provide: ExerciseVariantWebsocketService, useValue: websocketMock },
            ],
        });
        service = TestBed.inject(ExerciseVariantGenerationService);
    });

    it('startGeneration posts the request, adds a running entry, and subscribes to the per-job topic', () => {
        apiMock.generateVariant.mockReturnValue(of({ jobId: 'job-1' }));
        const request = { domainText: 'space', placement: { type: 'STANDALONE' as const } };

        let returnedJobId: string | undefined;
        service.startGeneration(42, request, 'Sorting Basics').subscribe((jobId) => (returnedJobId = jobId));

        expect(returnedJobId).toBe('job-1');
        expect(apiMock.generateVariant).toHaveBeenCalledWith(42, request);
        expect(websocketMock.subscribeToJob).toHaveBeenCalledWith('job-1');
        expect(service.jobs()).toHaveLength(1);
        expect(service.jobs()[0]).toMatchObject({ jobId: 'job-1', sourceExerciseId: 42, sourceExerciseTitle: 'Sorting Basics', phase: 'ANALYZING' });
        expect(service.runningJobs()).toHaveLength(1);
    });

    it('PHASE_CHANGED and ATTEMPT events update the matching job entry', () => {
        apiMock.generateVariant.mockReturnValue(of({ jobId: 'job-1' }));
        service.startGeneration(42, {}).subscribe();

        eventSubjects.get('job-1')!.next({ type: 'PHASE_CHANGED', phase: 'TRANSFORMING' });
        expect(service.jobs()[0].phase).toBe('TRANSFORMING');

        eventSubjects.get('job-1')!.next({ type: 'ATTEMPT', phase: 'REPAIRING', attempt: 2, maxAttempts: 3 });
        expect(service.jobs()[0].attempt).toBe(2);
        expect(service.jobs()[0].maxAttempts).toBe(3);
    });

    it('DONE with warnings stores the terminal phase, variantExerciseId, and unsubscribes from the topic', async () => {
        apiMock.generateVariant.mockReturnValue(of({ jobId: 'job-1' }));
        service.startGeneration(42, {}).subscribe();

        eventSubjects.get('job-1')!.next({ type: 'DONE', phase: 'DRAFT_WITH_WARNINGS', variantExerciseId: 4711, warnings: ['FINALIZING: placement failed'] });

        expect(service.jobs()[0].phase).toBe('DRAFT_WITH_WARNINGS');
        expect(service.jobs()[0].variantExerciseId).toBe(4711);
        expect(service.jobs()[0].warnings).toEqual(['FINALIZING: placement failed']);
        expect(service.runningJobs()).toHaveLength(0);
        // Detach is deferred to a microtask so the terminal event reaches every subscriber first — flush it.
        await Promise.resolve();
        expect(websocketMock.unsubscribeFromJob).toHaveBeenCalledWith('job-1');
    });

    it('loadJobs re-syncs from REST and re-attaches only to running jobs', () => {
        const jobs: VariantJob[] = [
            { jobId: 'running-1', phase: 'VERIFYING' },
            { jobId: 'done-1', phase: 'COMPLETED' },
        ];
        apiMock.getJobsOfCurrentUser.mockReturnValue(of(jobs));

        service.loadJobs().subscribe();

        expect(service.jobs()).toHaveLength(2);
        expect(websocketMock.subscribeToJob).toHaveBeenCalledWith('running-1');
        expect(websocketMock.subscribeToJob).not.toHaveBeenCalledWith('done-1');
    });

    it('cancelJob issues the DELETE and the entry transitions to CANCELLED on the CANCELLED event', async () => {
        apiMock.generateVariant.mockReturnValue(of({ jobId: 'job-1' }));
        apiMock.cancelJob.mockReturnValue(of(undefined));
        service.startGeneration(42, {}).subscribe();

        service.cancelJob('job-1').subscribe();
        expect(apiMock.cancelJob).toHaveBeenCalledWith('job-1');
        // Entry stays until the server-side cleanup finished and the CANCELLED event arrives.
        expect(service.jobs()[0].phase).toBe('ANALYZING');

        eventSubjects.get('job-1')!.next({ type: 'CANCELLED', phase: 'CANCELLED' });
        expect(service.jobs()[0].phase).toBe('CANCELLED');
        // Detach is deferred to a microtask so the terminal event reaches every subscriber first — flush it.
        await Promise.resolve();
        expect(websocketMock.unsubscribeFromJob).toHaveBeenCalledWith('job-1');
    });

    it('supports several running jobs for the SAME exercise at once', () => {
        apiMock.generateVariant
            .mockReturnValueOnce(of({ jobId: 'job-1' }))
            .mockReturnValueOnce(of({ jobId: 'job-2' }))
            .mockReturnValueOnce(of({ jobId: 'job-3' }));

        service.startGeneration(42, {}).subscribe();
        service.startGeneration(42, {}).subscribe();
        service.startGeneration(42, {}).subscribe();

        expect(service.runningJobs()).toHaveLength(3);
        eventSubjects.get('job-2')!.next({ type: 'DONE', phase: 'COMPLETED', variantExerciseId: 7 });
        expect(service.runningJobs()).toHaveLength(2);
        expect(service.jobs()).toHaveLength(3);
    });
});
