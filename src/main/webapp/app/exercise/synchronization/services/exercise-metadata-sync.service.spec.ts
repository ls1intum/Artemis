import { Mocked, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Competency, CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';

import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
    ExerciseNewVersionAlertEvent,
} from 'app/exercise/synchronization/services/exercise-editor-sync.service';
import { ExerciseMetadataSyncContext, ExerciseMetadataSyncService, metadataValuesEqual } from 'app/exercise/synchronization/services/exercise-metadata-sync.service';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { ExerciseMetadataConflictModalResult } from 'app/exercise/synchronization/metadata/exercise-metadata-conflict-modal.component';
import { AlertService } from 'app/shared/service/alert.service';

/**
 * Creates a mock DynamicDialogRef whose onClose Subject can be resolved or completed externally.
 */
const createMockDialogRef = (): { ref: DynamicDialogRef; onClose: Subject<ExerciseMetadataConflictModalResult | undefined> } => {
    const onClose = new Subject<ExerciseMetadataConflictModalResult | undefined>();
    const ref = { onClose } as unknown as DynamicDialogRef;
    return { ref, onClose };
};

describe('ExerciseMetadataSyncService', () => {
    setupTestBed({ zoneless: true });
    let service: ExerciseMetadataSyncService;
    let httpMock: HttpTestingController;
    let syncEvents$: Subject<ExerciseEditorSyncEvent>;
    let exerciseEditorSyncService: Mocked<ExerciseEditorSyncService>;
    let dialogService: { open: ReturnType<typeof vi.fn> };
    let alertService: { warning: ReturnType<typeof vi.fn> };

    let currentExercise: ProgrammingExercise;
    let baselineExercise: ProgrammingExercise;
    let context: ExerciseMetadataSyncContext<ProgrammingExercise>;
    let setBaselineExercise: ReturnType<typeof vi.fn<(exercise: ProgrammingExercise) => void>>;

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

    // Multiple microtask ticks are needed because the processing chain is composed of
    // chained awaits and signal updates: enqueueAlert → processQueue (reads signal) →
    // processAlert (await fetchSnapshot → collectConflicts → applyChanges) → finally
    // (updates signal, recurses). Each `await` and each signal read/write yields to the
    // microtask queue, so 6 iterations provides enough headroom for the deepest chain.
    const flushPromises = async (iterations = 6) => {
        for (let i = 0; i < iterations; i++) {
            await Promise.resolve();
        }
    };

    beforeEach(() => {
        syncEvents$ = new Subject<ExerciseEditorSyncEvent>();

        const defaultMock = createMockDialogRef();
        dialogService = {
            open: vi.fn().mockReturnValue(defaultMock.ref),
        };
        alertService = {
            warning: vi.fn(),
        };
        // Resolve the default mock immediately
        setTimeout(() => defaultMock.onClose.next({ decisions: [] } satisfies ExerciseMetadataConflictModalResult), 0);

        TestBed.configureTestingModule({
            providers: [
                ExerciseMetadataSyncService,
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: ExerciseEditorSyncService,
                    useValue: {
                        subscribeToUpdates: vi.fn().mockReturnValue(syncEvents$.asObservable()),
                        connect: vi.fn(),
                        disconnect: vi.fn(),
                    },
                },
                { provide: DialogService, useValue: dialogService },
                { provide: AlertService, useValue: alertService },
            ],
        });

        service = TestBed.inject(ExerciseMetadataSyncService);
        httpMock = TestBed.inject(HttpTestingController);
        exerciseEditorSyncService = TestBed.inject(ExerciseEditorSyncService) as Mocked<ExerciseEditorSyncService>;

        currentExercise = new ProgrammingExercise(undefined, undefined);
        baselineExercise = new ProgrammingExercise(undefined, undefined);
        currentExercise.id = 1;
        baselineExercise.id = 1;
        currentExercise.title = 'baseline-title';
        baselineExercise.title = 'baseline-title';
        currentExercise.maxPoints = 10;
        baselineExercise.maxPoints = 10;

        setBaselineExercise = vi.fn((updated: ProgrammingExercise) => {
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
        expect(exerciseEditorSyncService.subscribeToUpdates).toHaveBeenCalledWith();

        service.destroy();

        // destroy() no longer calls disconnect() — that's the parent component's responsibility
        expect(exerciseEditorSyncService.disconnect).not.toHaveBeenCalled();
    });

    it('can destroy safely without prior initialization', () => {
        service.destroy();

        // destroy() no longer calls disconnect() — that's the parent component's responsibility
        expect(exerciseEditorSyncService.disconnect).not.toHaveBeenCalled();
    });

    it('ignores non-metadata synchronization events', () => {
        service.initialize(context);

        syncEvents$.next({
            eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: ExerciseEditorSyncTarget.TESTS_REPOSITORY,
        });

        httpMock.expectNone(() => true);
        expect(dialogService.open).not.toHaveBeenCalled();
    });

    it('applies non-conflicting incoming changes to current and baseline without modal', async () => {
        service.initialize(context);
        emitAlert(createAlert(101, ['title']));

        flushSnapshot(1, 101, { id: 1, title: 'incoming-title' });
        await flushPromises();

        expect(currentExercise.title).toBe('incoming-title');
        expect(baselineExercise.title).toBe('incoming-title');
        expect(dialogService.open).not.toHaveBeenCalled();
        expect(setBaselineExercise).toHaveBeenCalled();
    });

    it('applies auxiliary repository metadata changes to current and baseline without modal', async () => {
        const existingRepository = new AuxiliaryRepository();
        existingRepository.id = 1;
        existingRepository.name = 'local';
        existingRepository.checkoutDirectory = 'local-dir';
        existingRepository.description = 'local-desc';
        existingRepository.repositoryUri = 'git@server:local.git';
        currentExercise.auxiliaryRepositories = [existingRepository];
        baselineExercise.auxiliaryRepositories = [existingRepository];

        service.initialize(context);
        emitAlert(createAlert(113, ['programmingData.auxiliaryRepositories']));

        flushSnapshot(1, 113, {
            id: 1,
            programmingData: {
                auxiliaryRepositories: [
                    {
                        id: 2,
                        name: 'incoming',
                        checkoutDirectory: 'incoming-dir',
                        description: 'incoming-desc',
                        repositoryUri: 'git@server:incoming.git',
                    },
                ],
            },
        });
        await flushPromises();

        expect(currentExercise.auxiliaryRepositories).toHaveLength(1);
        expect(currentExercise.auxiliaryRepositories?.[0].name).toBe('incoming');
        expect(currentExercise.auxiliaryRepositories?.[0].checkoutDirectory).toBe('incoming-dir');
        expect(currentExercise.auxiliaryRepositories?.[0].description).toBe('incoming-desc');
        expect(baselineExercise.auxiliaryRepositories).toHaveLength(1);
        expect(baselineExercise.auxiliaryRepositories?.[0].name).toBe('incoming');
        expect(dialogService.open).not.toHaveBeenCalled();
    });

    it('updates only baseline when changed fields have no registered handlers', async () => {
        service.initialize(context);
        emitAlert(createAlert(102, ['does.not.exist']));

        flushSnapshot(1, 102, { id: 1, title: 'ignored-title' });
        await flushPromises();

        expect(currentExercise.title).toBe('baseline-title');
        expect(baselineExercise.title).toBe('baseline-title');
        expect(dialogService.open).not.toHaveBeenCalled();
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
        expect(dialogService.open).not.toHaveBeenCalled();
    });

    it('ignores problem statement-only changes without fetching snapshots', async () => {
        service.initialize(context);
        emitAlert(createAlert(120, ['problemStatement']));

        httpMock.expectNone(() => true);
        await flushPromises();

        expect(dialogService.open).not.toHaveBeenCalled();
    });

    it('opens conflict modal and applies selected incoming values', async () => {
        const resolution: ExerciseMetadataConflictModalResult = {
            decisions: [{ field: 'title', useIncoming: true }],
        };
        const mock = createMockDialogRef();
        dialogService.open.mockReturnValue(mock.ref);

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(103, ['title']));

        flushSnapshot(1, 103, { id: 1, title: 'incoming-title' });
        await flushPromises();

        // Resolve dialog
        mock.onClose.next(resolution);
        mock.onClose.complete();
        await flushPromises();

        expect(dialogService.open).toHaveBeenCalledOnce();
        const openCallData = dialogService.open.mock.calls[0][1]?.data;
        expect(openCallData.conflicts).toEqual([
            {
                field: 'title',
                labelKey: 'artemisApp.exercise.title',
                currentValue: 'local-title',
                incomingValue: 'incoming-title',
            },
        ]);
        expect(openCallData.exerciseId).toBe(1);
        expect(openCallData.exerciseType).toBe(ExerciseType.PROGRAMMING);
        expect(currentExercise.title).toBe('incoming-title');
        expect(baselineExercise.title).toBe('incoming-title');
    });

    it('keeps local values when conflict modal is dismissed', async () => {
        const mock = createMockDialogRef();
        dialogService.open.mockReturnValue(mock.ref);

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(104, ['title']));

        flushSnapshot(1, 104, { id: 1, title: 'incoming-title' });
        await flushPromises();

        // Dismiss dialog (close without value)
        mock.onClose.next(undefined);
        mock.onClose.complete();
        await flushPromises();

        expect(dialogService.open).toHaveBeenCalledOnce();
        expect(currentExercise.title).toBe('local-title');
        expect(baselineExercise.title).toBe('incoming-title');
    });

    it('keeps local values for decisions with useIncoming=false', async () => {
        const resolution: ExerciseMetadataConflictModalResult = {
            decisions: [{ field: 'title', useIncoming: false }],
        };
        const mock = createMockDialogRef();
        dialogService.open.mockReturnValue(mock.ref);

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(105, ['title']));

        flushSnapshot(1, 105, { id: 1, title: 'incoming-title' });
        await flushPromises();

        mock.onClose.next(resolution);
        mock.onClose.complete();
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

        expect(dialogService.open).not.toHaveBeenCalled();
    });

    it('continues queue processing after a failed snapshot fetch and shows a warning', async () => {
        service.initialize(context);

        emitAlert(createAlert(106, ['title']));
        emitAlert(createAlert(107, ['title']));

        const first = httpMock.expectOne('api/exercise/1/version/106');
        first.flush('network-failure', { status: 500, statusText: 'Server Error' });
        await flushPromises();

        expect(alertService.warning).toHaveBeenCalledWith('artemisApp.exercise.metadataSync.snapshotFetchFailed');

        flushSnapshot(1, 107, { id: 1, title: 'incoming-after-failure' });
        await flushPromises();

        expect(currentExercise.title).toBe('incoming-after-failure');
        expect(baselineExercise.title).toBe('incoming-after-failure');
    });

    it('shows warning alert when a single snapshot fetch fails', async () => {
        service.initialize(context);
        emitAlert(createAlert(130, ['title']));

        const req = httpMock.expectOne('api/exercise/1/version/130');
        req.flush('error', { status: 404, statusText: 'Not Found' });
        await flushPromises();

        expect(alertService.warning).toHaveBeenCalledWith('artemisApp.exercise.metadataSync.snapshotFetchFailed');
        expect(currentExercise.title).toBe('baseline-title');
        expect(dialogService.open).not.toHaveBeenCalled();
    });

    it('processes queued alerts sequentially while waiting for modal resolution', async () => {
        const firstMock = createMockDialogRef();
        const secondMock = createMockDialogRef();
        dialogService.open.mockReturnValueOnce(firstMock.ref).mockReturnValueOnce(secondMock.ref);

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(108, ['title']));
        emitAlert(createAlert(109, ['title']));

        flushSnapshot(1, 108, { id: 1, title: 'incoming-v108' });
        await flushPromises();

        httpMock.expectNone('api/exercise/1/version/109');
        expect(dialogService.open).toHaveBeenCalledOnce();

        firstMock.onClose.next({ decisions: [{ field: 'title', useIncoming: false }] });
        firstMock.onClose.complete();
        await flushPromises();

        flushSnapshot(1, 109, { id: 1, title: 'incoming-v109' });
        await flushPromises();
        await flushPromises();

        secondMock.onClose.next({ decisions: [{ field: 'title', useIncoming: true }] });
        secondMock.onClose.complete();
        await flushPromises();

        expect(dialogService.open).toHaveBeenCalledTimes(2);
        expect(currentExercise.title).toBe('incoming-v109');
        expect(baselineExercise.title).toBe('incoming-v109');
    });

    it('does not trigger processing when initialized without any alerts', async () => {
        service.initialize(context);
        await flushPromises();

        httpMock.expectNone(() => true);
        expect(dialogService.open).not.toHaveBeenCalled();
    });

    it('ignores alerts emitted before initialization', async () => {
        // Service not initialized — emitting directly on the subject should do nothing
        syncEvents$.next(createAlert(111, ['title']));
        await flushPromises();

        httpMock.expectNone(() => true);
    });

    it('ignores conflict decisions for unknown fields via modal resolution', async () => {
        const resolution: ExerciseMetadataConflictModalResult = {
            decisions: [
                { field: 'title', useIncoming: false },
                { field: 'not.a.handler', useIncoming: true },
            ],
        };
        const mock = createMockDialogRef();
        dialogService.open.mockReturnValue(mock.ref);

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(111, ['title']));

        flushSnapshot(1, 111, { id: 1, title: 'incoming-title' });
        await flushPromises();

        mock.onClose.next(resolution);
        mock.onClose.complete();
        await flushPromises();

        // 'title' kept local, 'not.a.handler' silently ignored
        expect(currentExercise.title).toBe('local-title');
    });

    it('compares dayjs and serialized date values correctly', () => {
        expect(metadataValuesEqual(dayjs('2026-01-01T00:00:00.000Z'), '2026-01-01T00:00:00.000Z')).toBe(true);
        expect(metadataValuesEqual('2026-01-01T00:00:00.000Z', dayjs('2026-01-01T00:00:00.000Z'))).toBe(true);
        expect(metadataValuesEqual(dayjs('2026-01-01T00:00:00.000Z'), undefined)).toBe(false);
        expect(metadataValuesEqual(undefined, dayjs('2026-01-01T00:00:00.000Z'))).toBe(false);
        // dayjs('invalid') produces an actually invalid dayjs; dayjs(undefined) returns the current date (valid)
        expect(metadataValuesEqual(dayjs('invalid'), dayjs('invalid'))).toBe(true);
        expect(metadataValuesEqual(dayjs('invalid'), undefined)).toBe(true);
    });

    it('applies snapshot to baseline only for changed fields, leaving others unchanged', async () => {
        currentExercise.title = 'baseline-title';
        currentExercise.maxPoints = 10;
        baselineExercise.title = 'baseline-title';
        baselineExercise.maxPoints = 10;

        service.initialize(context);
        // Alert says only 'title' changed, but snapshot also has different maxPoints
        emitAlert(createAlert(140, ['title']));

        flushSnapshot(1, 140, { id: 1, title: 'incoming-title', maxPoints: 99 });
        await flushPromises();

        // title was in changedFields → applied to both current and baseline
        expect(currentExercise.title).toBe('incoming-title');
        expect(baselineExercise.title).toBe('incoming-title');
        // maxPoints was NOT in changedFields → unchanged
        expect(currentExercise.maxPoints).toBe(10);
        expect(baselineExercise.maxPoints).toBe(10);
    });

    it('handles destroy during pending snapshot fetch gracefully', async () => {
        service.initialize(context);
        emitAlert(createAlert(150, ['title']));
        await flushPromises(1);

        // HTTP request is in-flight; destroy before flushing the snapshot
        service.destroy();

        // Cancel the pending request to satisfy httpMock.verify()
        const pendingReq = httpMock.expectOne('api/exercise/1/version/150');
        pendingReq.flush('cancelled', { status: 0, statusText: 'Cancelled' });
        await flushPromises();

        // No modal should have been opened
        expect(dialogService.open).not.toHaveBeenCalled();
    });

    it('handles destroy during modal open gracefully', async () => {
        const mock = createMockDialogRef();
        dialogService.open.mockReturnValue(mock.ref);

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(151, ['title']));

        flushSnapshot(1, 151, { id: 1, title: 'incoming-title' });
        await flushPromises();

        expect(dialogService.open).toHaveBeenCalledOnce();

        // Destroy while modal is open
        service.destroy();

        // Complete the modal — service should handle the resolution gracefully
        mock.onClose.next({ decisions: [{ field: 'title', useIncoming: true }] });
        mock.onClose.complete();
        await flushPromises();

        // Exercise should retain its state since context was destroyed
        expect(currentExercise.title).toBe('local-title');
    });

    // The next two tests exercise a defensive guard against direct exercise-to-exercise
    // navigation (e.g. hypothetical tab-based editing). The current UI always navigates
    // through an intermediate page, so destroy() fully resets state before any new
    // initialize() could run — making the guard a no-op in practice. These tests verify
    // the guard works correctly should the navigation model change in the future.

    it('does not corrupt new session queue when context changes during snapshot fetch', async () => {
        service.initialize(context);
        emitAlert(createAlert(160, ['title']));
        await flushPromises(1);

        // HTTP request is in-flight; navigate to a different exercise
        service.destroy();

        const newExercise = new ProgrammingExercise(undefined, undefined);
        newExercise.id = 2;
        newExercise.title = 'other-title';
        const newBaseline = new ProgrammingExercise(undefined, undefined);
        newBaseline.id = 2;
        newBaseline.title = 'other-title';
        const newContext: ExerciseMetadataSyncContext<ProgrammingExercise> = {
            exerciseId: 2,
            exerciseType: ExerciseType.PROGRAMMING,
            getCurrentExercise: () => newExercise,
            getBaselineExercise: () => newBaseline,
            setBaselineExercise: vi.fn(),
        };
        service.initialize(newContext);

        // Emit an alert for the new exercise
        emitAlert(createAlert(161, ['title']));

        // Flush the stale snapshot for exercise 1
        const staleReq = httpMock.expectOne('api/exercise/1/version/160');
        staleReq.flush('cancelled', { status: 0, statusText: 'Cancelled' });
        await flushPromises();

        // Flush the new snapshot for exercise 2
        flushSnapshot(2, 161, { id: 2, title: 'incoming-other-title' });
        await flushPromises();

        // The new exercise should have been updated, not corrupted
        expect(newExercise.title).toBe('incoming-other-title');
    });

    it('does not apply stale modal resolution when context changes to a different exercise', async () => {
        const mock = createMockDialogRef();
        dialogService.open.mockReturnValue(mock.ref);

        currentExercise.title = 'local-title';
        baselineExercise.title = 'baseline-title';

        service.initialize(context);
        emitAlert(createAlert(170, ['title']));

        flushSnapshot(1, 170, { id: 1, title: 'incoming-title' });
        await flushPromises();

        expect(dialogService.open).toHaveBeenCalledOnce();

        // Navigate to a different exercise while the modal is open
        service.destroy();

        const newExercise = new ProgrammingExercise(undefined, undefined);
        newExercise.id = 2;
        newExercise.title = 'exercise-2-title';
        const newBaseline = new ProgrammingExercise(undefined, undefined);
        newBaseline.id = 2;
        newBaseline.title = 'exercise-2-title';
        const newContext: ExerciseMetadataSyncContext<ProgrammingExercise> = {
            exerciseId: 2,
            exerciseType: ExerciseType.PROGRAMMING,
            getCurrentExercise: () => newExercise,
            getBaselineExercise: () => newBaseline,
            setBaselineExercise: vi.fn(),
        };
        service.initialize(newContext);

        // Resolve the stale modal — should be ignored
        mock.onClose.next({ decisions: [{ field: 'title', useIncoming: true }] });
        mock.onClose.complete();
        await flushPromises();

        // Old exercise should not have been modified by the resolution
        expect(currentExercise.title).toBe('local-title');
        // New exercise should be untouched
        expect(newExercise.title).toBe('exercise-2-title');
    });

    it('resolves competency links using current exercise from the handler resolver', async () => {
        const competency = new Competency();
        competency.id = 42;
        competency.title = 'Comp 42';

        currentExercise.course = { competencies: [competency], prerequisites: [] } as any;
        baselineExercise.course = currentExercise.course;
        currentExercise.competencyLinks = [new CompetencyExerciseLink(competency, currentExercise, 1)];
        baselineExercise.competencyLinks = undefined;

        const mock = createMockDialogRef();
        dialogService.open.mockReturnValue(mock.ref);

        service.initialize(context);
        emitAlert(createAlert(112, ['competencyLinks']));

        flushSnapshot(1, 112, {
            id: 1,
            competencyLinks: [{ competencyId: { competencyId: 42 }, weight: 0.5 }],
        });
        await flushPromises();

        mock.onClose.next({ decisions: [{ field: 'competencyLinks', useIncoming: true }] });
        mock.onClose.complete();
        await flushPromises();

        expect(dialogService.open).toHaveBeenCalled();
        expect(currentExercise.competencyLinks).toHaveLength(1);
        expect(currentExercise.competencyLinks?.[0].competency?.id).toBe(42);
        expect(currentExercise.competencyLinks?.[0].weight).toBe(0.5);
    });
});
