import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Competency, CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';

import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
    ExerciseNewVersionAlertEvent,
} from 'app/exercise/services/exercise-editor-sync.service';
import { ExerciseMetadataSyncContext, ExerciseMetadataSyncService } from 'app/exercise/services/exercise-metadata-sync.service';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';
import { ExerciseMetadataConflictModalResult } from 'app/exercise/synchronization/exercise-metadata-conflict-modal.component';

interface Deferred<T> {
    promise: Promise<T>;
    resolve: (value: T) => void;
    reject: (reason?: unknown) => void;
}

const deferred = <T>(): Deferred<T> => {
    let resolve!: (value: T) => void;
    let reject!: (reason?: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
};

describe('ExerciseMetadataSyncService', () => {
    let service: ExerciseMetadataSyncService;
    let httpMock: HttpTestingController;
    let syncEvents$: Subject<ExerciseEditorSyncEvent>;
    let exerciseEditorSyncService: jest.Mocked<ExerciseEditorSyncService>;
    let modalService: { open: jest.Mock };
    let modalComponentInstance: {
        setConflicts: jest.Mock;
        setAuthor: jest.Mock;
        setVersionId: jest.Mock;
        setExerciseId: jest.Mock;
        setExerciseType: jest.Mock;
    };

    let currentExercise: ProgrammingExercise;
    let baselineExercise: ProgrammingExercise;
    let context: ExerciseMetadataSyncContext<ProgrammingExercise>;
    let setBaselineExercise: jest.Mock;

    const createAlert = (versionId: number, changedFields: string[]): ExerciseNewVersionAlertEvent => ({
        eventType: ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT,
        target: ExerciseEditorSyncTarget.EXERCISE_METADATA,
        exerciseVersionId: versionId,
        changedFields,
        author: { login: 'editor' },
    });

    const emitAlert = (alert: ExerciseNewVersionAlertEvent) => {
        syncEvents$.next(alert);
    };

    const flushSnapshot = (exerciseId: number, versionId: number, snapshot: ExerciseSnapshotDTO) => {
        const req = httpMock.expectOne(`api/exercise/${exerciseId}/version/${versionId}`);
        expect(req.request.method).toBe('GET');
        req.flush(snapshot);
    };

    const flushPromises = async (iterations = 6) => {
        for (let i = 0; i < iterations; i++) {
            await Promise.resolve();
        }
    };

    beforeEach(() => {
        syncEvents$ = new Subject<ExerciseEditorSyncEvent>();
        modalComponentInstance = {
            setConflicts: jest.fn(),
            setAuthor: jest.fn(),
            setVersionId: jest.fn(),
            setExerciseId: jest.fn(),
            setExerciseType: jest.fn(),
        };

        modalService = {
            open: jest.fn().mockReturnValue({
                componentInstance: modalComponentInstance,
                result: Promise.resolve({ decisions: [] } satisfies ExerciseMetadataConflictModalResult),
            }),
        };

        TestBed.configureTestingModule({
            providers: [
                ExerciseMetadataSyncService,
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: ExerciseEditorSyncService,
                    useValue: {
                        subscribeToUpdates: jest.fn().mockReturnValue(syncEvents$.asObservable()),
                        unsubscribe: jest.fn(),
                    },
                },
                { provide: NgbModal, useValue: modalService },
            ],
        });

        service = TestBed.inject(ExerciseMetadataSyncService);
        httpMock = TestBed.inject(HttpTestingController);
        exerciseEditorSyncService = TestBed.inject(ExerciseEditorSyncService) as jest.Mocked<ExerciseEditorSyncService>;

        currentExercise = new ProgrammingExercise(undefined, undefined);
        baselineExercise = new ProgrammingExercise(undefined, undefined);
        currentExercise.id = 1;
        baselineExercise.id = 1;
        currentExercise.title = 'baseline-title';
        baselineExercise.title = 'baseline-title';
        currentExercise.maxPoints = 10;
        baselineExercise.maxPoints = 10;

        setBaselineExercise = jest.fn((updated) => {
            baselineExercise = updated;
        });

        context = {
            exerciseId: 1,
            exerciseType: ExerciseType.PROGRAMMING,
            getCurrentExercise: () => currentExercise,
            getBaselineExercise: () => baselineExercise,
            setBaselineExercise,
        };
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('initializes only once and destroys subscriptions', () => {
        service.initialize(context);
        service.initialize(context);

        expect(exerciseEditorSyncService.subscribeToUpdates).toHaveBeenCalledOnce();
        expect(exerciseEditorSyncService.subscribeToUpdates).toHaveBeenCalledWith(1);

        service.destroy();

        expect(exerciseEditorSyncService.unsubscribe).toHaveBeenCalledOnce();
    });

    it('can destroy safely without prior initialization', () => {
        service.destroy();

        expect(exerciseEditorSyncService.unsubscribe).toHaveBeenCalledOnce();
    });

    it('ignores non-metadata synchronization events', () => {
        service.initialize(context);

        syncEvents$.next({
            eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: ExerciseEditorSyncTarget.TESTS_REPOSITORY,
        });

        httpMock.expectNone(() => true);
        expect(modalService.open).not.toHaveBeenCalled();
    });

    it('applies non-conflicting incoming changes to current and baseline without modal', async () => {
        service.initialize(context);
        emitAlert(createAlert(101, ['title']));

        flushSnapshot(1, 101, { id: 1, title: 'incoming-title' });
        await flushPromises();

        expect(currentExercise.title).toBe('incoming-title');
        expect(baselineExercise.title).toBe('incoming-title');
        expect(modalService.open).not.toHaveBeenCalled();
        expect(setBaselineExercise).toHaveBeenCalled();
    });

    it('updates only baseline when changed fields have no registered handlers', async () => {
        service.initialize(context);
        emitAlert(createAlert(102, ['does.not.exist']));

        flushSnapshot(1, 102, { id: 1, title: 'ignored-title' });
        await flushPromises();

        expect(currentExercise.title).toBe('baseline-title');
        expect(baselineExercise.title).toBe('baseline-title');
        expect(modalService.open).not.toHaveBeenCalled();
    });

    it('handles alerts with undefined changedFields as empty updates', async () => {
        service.initialize(context);
        emitAlert({
            eventType: ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT,
            target: ExerciseEditorSyncTarget.EXERCISE_METADATA,
            exerciseVersionId: 110,
            author: { login: 'editor' },
        });

        httpMock.expectNone(() => true);
        await flushPromises();

        expect(currentExercise.title).toBe('baseline-title');
        expect(baselineExercise.title).toBe('baseline-title');
        expect(modalService.open).not.toHaveBeenCalled();
    });

    it('ignores problem statement-only changes without fetching snapshots', async () => {
        service.initialize(context);
        emitAlert(createAlert(120, ['problemStatement']));

        httpMock.expectNone(() => true);
        await flushPromises();

        expect(modalService.open).not.toHaveBeenCalled();
    });

    it('opens conflict modal and applies selected incoming values', async () => {
        const resolution: ExerciseMetadataConflictModalResult = {
            decisions: [{ field: 'title', useIncoming: true }],
        };
        modalService.open.mockReturnValue({
            componentInstance: modalComponentInstance,
            result: Promise.resolve(resolution),
        });

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(103, ['title']));

        flushSnapshot(1, 103, { id: 1, title: 'incoming-title' });
        await flushPromises();
        await flushPromises();

        expect(modalService.open).toHaveBeenCalledOnce();
        expect(modalComponentInstance.setConflicts).toHaveBeenCalledWith([
            {
                field: 'title',
                labelKey: 'artemisApp.exercise.title',
                currentValue: 'local-title',
                incomingValue: 'incoming-title',
            },
        ]);
        expect(modalComponentInstance.setExerciseId).toHaveBeenCalledWith(1);
        expect(modalComponentInstance.setExerciseType).toHaveBeenCalledWith(ExerciseType.PROGRAMMING);
        expect(currentExercise.title).toBe('incoming-title');
        expect(baselineExercise.title).toBe('incoming-title');
    });

    it('keeps local values when conflict modal is dismissed', async () => {
        modalService.open.mockReturnValue({
            componentInstance: modalComponentInstance,
            result: Promise.reject(new Error('dismissed')),
        });

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(104, ['title']));

        flushSnapshot(1, 104, { id: 1, title: 'incoming-title' });
        await flushPromises();

        expect(modalService.open).toHaveBeenCalledOnce();
        expect(currentExercise.title).toBe('local-title');
        expect(baselineExercise.title).toBe('incoming-title');
    });

    it('keeps local values for decisions with useIncoming=false', async () => {
        const resolution: ExerciseMetadataConflictModalResult = {
            decisions: [{ field: 'title', useIncoming: false }],
        };
        modalService.open.mockReturnValue({
            componentInstance: modalComponentInstance,
            result: Promise.resolve(resolution),
        });

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(105, ['title']));

        flushSnapshot(1, 105, { id: 1, title: 'incoming-title' });
        await flushPromises();

        expect(currentExercise.title).toBe('local-title');
        expect(baselineExercise.title).toBe('incoming-title');
    });

    it('does not raise competency link conflicts when only exercise references differ', async () => {
        const competency = new Competency();
        competency.id = 99;
        competency.title = 'Comp 99';

        const currentExerciseRef = new ProgrammingExercise(undefined, undefined);
        const baselineExerciseRef = new ProgrammingExercise(undefined, undefined);

        currentExercise.course = { competencies: [competency], prerequisites: [] } as any;
        baselineExercise.course = currentExercise.course;

        currentExercise.competencyLinks = [new CompetencyExerciseLink(competency, currentExerciseRef, 0.5)];
        baselineExercise.competencyLinks = [new CompetencyExerciseLink(competency, baselineExerciseRef, 0.5)];

        service.initialize(context);
        emitAlert(createAlert(121, ['competencyLinks']));

        flushSnapshot(1, 121, {
            id: 1,
            competencyLinks: [{ competencyId: { competencyId: 99 }, weight: 0.5 }],
        });
        await flushPromises();

        expect(modalService.open).not.toHaveBeenCalled();
    });

    it('continues queue processing after a failed snapshot fetch', async () => {
        service.initialize(context);

        emitAlert(createAlert(106, ['title']));
        emitAlert(createAlert(107, ['title']));

        const first = httpMock.expectOne('api/exercise/1/version/106');
        first.flush('network-failure', { status: 500, statusText: 'Server Error' });
        await flushPromises();

        flushSnapshot(1, 107, { id: 1, title: 'incoming-after-failure' });
        await flushPromises();

        expect(currentExercise.title).toBe('incoming-after-failure');
        expect(baselineExercise.title).toBe('incoming-after-failure');
    });

    it('processes queued alerts sequentially while waiting for modal resolution', async () => {
        const firstResolution = deferred<ExerciseMetadataConflictModalResult>();
        const secondResolution = deferred<ExerciseMetadataConflictModalResult>();
        modalService.open
            .mockReturnValueOnce({
                componentInstance: modalComponentInstance,
                result: firstResolution.promise,
            })
            .mockReturnValueOnce({
                componentInstance: modalComponentInstance,
                result: secondResolution.promise,
            });

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(108, ['title']));
        emitAlert(createAlert(109, ['title']));

        flushSnapshot(1, 108, { id: 1, title: 'incoming-v108' });
        await flushPromises();

        httpMock.expectNone('api/exercise/1/version/109');
        expect(modalService.open).toHaveBeenCalledOnce();

        firstResolution.resolve({ decisions: [{ field: 'title', useIncoming: false }] });
        await flushPromises();

        flushSnapshot(1, 109, { id: 1, title: 'incoming-v109' });
        await flushPromises();
        await flushPromises();
        secondResolution.resolve({ decisions: [{ field: 'title', useIncoming: true }] });
        await flushPromises();

        expect(modalService.open).toHaveBeenCalledTimes(2);
        expect(currentExercise.title).toBe('incoming-v109');
        expect(baselineExercise.title).toBe('incoming-v109');
    });

    it('returns early when queue processing starts with no pending alerts', () => {
        service.initialize(context);

        (service as any).processQueue();

        httpMock.expectNone(() => true);
        expect(modalService.open).not.toHaveBeenCalled();
    });

    it('returns early from alert processing when context is missing', async () => {
        await (service as any).processAlert(createAlert(111, ['title']));

        httpMock.expectNone(() => true);
    });

    it('does not set exercise id/type on modal when context is missing', async () => {
        modalService.open.mockReturnValue({
            componentInstance: modalComponentInstance,
            result: Promise.resolve({ decisions: [] } satisfies ExerciseMetadataConflictModalResult),
        });

        const result = await (service as any).openConflictModal([], { login: 'editor' }, 123);

        expect(result).toEqual({ decisions: [] });
        expect(modalComponentInstance.setExerciseId).not.toHaveBeenCalled();
        expect(modalComponentInstance.setExerciseType).not.toHaveBeenCalled();
    });

    it('ignores conflict decisions for unknown fields', () => {
        currentExercise.title = 'local-title';

        (service as any).applyConflictResolution(context, { id: 1, title: 'incoming-title' }, { decisions: [{ field: 'not.a.handler', useIncoming: true }] });

        expect(currentExercise.title).toBe('local-title');
    });

    it('compares dayjs and serialized date values correctly', () => {
        const leftDayjsRightStringEqual = (service as any).valuesEqual(dayjs('2026-01-01T00:00:00.000Z'), '2026-01-01T00:00:00.000Z');
        const leftStringRightDayjsEqual = (service as any).valuesEqual('2026-01-01T00:00:00.000Z', dayjs('2026-01-01T00:00:00.000Z'));
        const invalidNormalizedValue = (service as any).valuesEqual(dayjs('2026-01-01T00:00:00.000Z'), undefined);

        expect(leftDayjsRightStringEqual).toBeTrue();
        expect(leftStringRightDayjsEqual).toBeTrue();
        expect(invalidNormalizedValue).toBeFalse();
    });

    it('resolves competency links using current exercise from the handler resolver', async () => {
        const competency = new Competency();
        competency.id = 42;
        competency.title = 'Comp 42';

        currentExercise.course = { competencies: [competency], prerequisites: [] } as any;
        baselineExercise.course = currentExercise.course;
        currentExercise.competencyLinks = [new CompetencyExerciseLink(competency, currentExercise, 1)];
        baselineExercise.competencyLinks = undefined;

        modalService.open.mockReturnValue({
            componentInstance: modalComponentInstance,
            result: Promise.resolve({ decisions: [{ field: 'competencyLinks', useIncoming: true }] } satisfies ExerciseMetadataConflictModalResult),
        });

        service.initialize(context);
        emitAlert(createAlert(112, ['competencyLinks']));

        flushSnapshot(1, 112, {
            id: 1,
            competencyLinks: [{ competencyId: { competencyId: 42 }, weight: 0.5 }],
        });
        await flushPromises();

        expect(modalService.open).toHaveBeenCalled();
        expect(currentExercise.competencyLinks).toHaveLength(1);
        expect(currentExercise.competencyLinks?.[0].competency?.id).toBe(42);
        expect(currentExercise.competencyLinks?.[0].weight).toBe(0.5);
    });
});
