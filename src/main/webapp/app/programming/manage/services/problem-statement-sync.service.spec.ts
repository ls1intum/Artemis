import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { DiffMatchPatch } from 'diff-match-patch-typescript';

import { ProblemStatementSyncService } from 'app/programming/manage/services/problem-statement-sync.service';
import { AlertService } from 'app/shared/service/alert.service';
import {
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';

describe('ProblemStatementSyncService', () => {
    let service: ProblemStatementSyncService;
    let syncService: jest.Mocked<ProgrammingExerciseEditorSyncService>;
    let incomingMessages$: Subject<ProgrammingExerciseEditorSyncMessage>;
    const dmp = new DiffMatchPatch();

    beforeEach(() => {
        incomingMessages$ = new Subject<ProgrammingExerciseEditorSyncMessage>();

        TestBed.configureTestingModule({
            providers: [
                ProblemStatementSyncService,
                {
                    provide: ProgrammingExerciseEditorSyncService,
                    useValue: {
                        subscribeToUpdates: jest.fn().mockReturnValue(incomingMessages$.asObservable()),
                        sendSynchronizationUpdate: jest.fn(),
                        unsubscribe: jest.fn(),
                    },
                },
                {
                    provide: AlertService,
                    useValue: { info: jest.fn(), error: jest.fn(), success: jest.fn() },
                },
            ],
        });

        service = TestBed.inject(ProblemStatementSyncService);
        syncService = TestBed.inject(ProgrammingExerciseEditorSyncService) as jest.Mocked<ProgrammingExerciseEditorSyncService>;
    });

    afterEach(() => {
        service?.reset();
        jest.clearAllMocks();
    });

    it('initializes synchronization and requests initial content', () => {
        service.init(42, 'Initial content');

        expect(syncService.subscribeToUpdates).toHaveBeenCalledWith(42);
        expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
            42,
            expect.objectContaining({
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                problemStatementRequest: true,
            }),
        );
    });

    it('sends debounced patch for local changes', fakeAsync(() => {
        service.init(42, 'Old content');

        service.queueLocalChange('Updated content');
        tick(300);

        expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
            42,
            expect.objectContaining({
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                problemStatementPatch: expect.any(String),
            }),
        );
    }));

    it('does not send patch if init was not called', () => {
        service.handleLocalChange('content without init');

        expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
    });

    it('emits full content updates received from other clients', () => {
        const received: string[] = [];
        service.init(99, 'Initial content').subscribe((content) => received.push(content));

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementFull: 'Remote content',
            timestamp: 1,
        });

        expect(received).toEqual(['Remote content']);
    });

    it('applies incoming patches and emits updated content', () => {
        const received: string[] = [];
        service.init(99, 'Hello World').subscribe((content) => received.push(content));

        const patchText = dmp.patch_toText(dmp.patch_make('Hello World', 'Hello Artemis'));

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementPatch: patchText,
            timestamp: 1,
        });

        expect(received).toEqual(['Hello Artemis']);
    });

    it('ignores patches that cannot be applied', () => {
        const received: string[] = [];
        service.init(99, 'Base content').subscribe((content) => received.push(content));

        // Build a patch against a different base so application fails
        const incompatiblePatch = dmp.patch_toText(dmp.patch_make('other base', 'new content'));
        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementPatch: incompatiblePatch,
            timestamp: 1,
        });

        expect(received).toEqual([]);
    });

    it('ignores non problem-statement messages', () => {
        const received: string[] = [];
        service.init(99, 'Initial').subscribe((content) => received.push(content));

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
            problemStatementFull: 'Should be ignored',
            timestamp: 1,
        });

        expect(received).toEqual([]);
    });

    it('ignores older messages based on timestamp', () => {
        const received: string[] = [];
        service.init(99, 'Initial').subscribe((content) => received.push(content));

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementFull: 'Newest',
            timestamp: 2,
        });

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementFull: 'Older content',
            timestamp: 1,
        });

        expect(received).toEqual(['Newest']);
    });

    it('responds to content requests with the last synced content', () => {
        service.init(99, 'Current content');

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementRequest: true,
            timestamp: 1,
        });

        expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
            99,
            expect.objectContaining({
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                problemStatementFull: 'Current content',
            }),
        );
    });

    it('cleans up subscriptions and completes observers on reset', () => {
        let completed = false;
        service.init(99, 'Initial').subscribe({ complete: () => (completed = true) });

        service.reset();
        expect(completed).toBeTrue();

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementFull: 'Should not be processed',
            timestamp: 3,
        });
    });
});
