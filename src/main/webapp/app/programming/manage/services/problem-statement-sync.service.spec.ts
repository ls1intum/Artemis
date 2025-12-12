import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { DiffMatchPatch } from 'diff-match-patch-typescript';

import { ProblemStatementSyncService } from 'app/programming/manage/services/problem-statement-sync.service';
import {
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';

describe('ProblemStatementSyncService', () => {
    let service: ProblemStatementSyncService;
    let syncService: jest.Mocked<ProgrammingExerciseEditorSyncService>;
    let incomingMessages$: Subject<ProgrammingExerciseEditorSyncMessage>;

    beforeEach(() => {
        incomingMessages$ = new Subject<ProgrammingExerciseEditorSyncMessage>();

        TestBed.configureTestingModule({
            providers: [
                ProblemStatementSyncService,
                {
                    provide: ProgrammingExerciseEditorSyncService,
                    useValue: {
                        getSynchronizationUpdates: jest.fn().mockReturnValue(incomingMessages$.asObservable()),
                        sendSynchronization: jest.fn(),
                    },
                },
            ],
        });

        service = TestBed.inject(ProblemStatementSyncService);
        syncService = TestBed.inject(ProgrammingExerciseEditorSyncService) as jest.Mocked<ProgrammingExerciseEditorSyncService>;
    });

    afterEach(() => {
        service.dispose();
    });

    it('should send patch on local changes after initialization', fakeAsync(() => {
        service.init(42, 'Old content', 'client-a');

        service.queueLocalChange('Updated content');
        tick(1000);

        expect(syncService.sendSynchronization).toHaveBeenCalledWith(
            42,
            expect.objectContaining({
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                problemStatementPatch: expect.any(String),
                clientInstanceId: 'client-a',
            }),
        );
    }));

    it('should apply incoming updates from other clients and ignore own updates', () => {
        const received: string[] = [];
        service.init(99, 'Initial', 'client-a');
        service.updates$.subscribe((content) => received.push(content));

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementFull: 'Remote content',
            clientInstanceId: 'client-b',
            timestamp: 1,
        });

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementFull: 'Own content',
            clientInstanceId: 'client-a',
            timestamp: 2,
        });

        expect(received).toEqual(['Remote content']);
    });

    it('should request initial sync on patch application failure', () => {
        service.init(99, 'base content', 'client-a');

        // Send a patch that will fail (incompatible with baseline)
        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementPatch: '@@ -1,10 +1,10 @@\n-different base\n+new content\n',
            clientInstanceId: 'client-b',
            timestamp: 1,
        });

        // Should have requested initial sync as fallback
        expect(syncService.sendSynchronization).toHaveBeenCalledWith(
            99,
            expect.objectContaining({
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                problemStatementRequest: true,
            }),
        );
    });

    it('should handle malformed patch gracefully and request full sync', () => {
        service.init(99, 'base content', 'client-a');

        // Send a malformed patch
        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementPatch: 'invalid patch format @@@###',
            clientInstanceId: 'client-b',
            timestamp: 1,
        });

        // Should have requested initial sync as fallback
        expect(syncService.sendSynchronization).toHaveBeenCalledWith(
            99,
            expect.objectContaining({
                problemStatementRequest: true,
            }),
        );
    });

    it('should apply successful patches correctly', () => {
        const received: string[] = [];
        service.init(99, 'Hello World', 'client-a');
        service.updates$.subscribe((content) => received.push(content));

        // Create a valid patch using diff-match-patch
        const dmp = new DiffMatchPatch();
        const patches = dmp.patch_make('Hello World', 'Hello Universe');
        const patchText = dmp.patch_toText(patches);

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementPatch: patchText,
            clientInstanceId: 'client-b',
            timestamp: 1,
        });

        expect(received).toEqual(['Hello Universe']);
    });

    it('should respond to problem statement requests with current content', () => {
        service.init(99, 'Current content', 'client-a');

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementRequest: true,
            clientInstanceId: 'client-b',
            timestamp: 1,
        });

        expect(syncService.sendSynchronization).toHaveBeenCalledWith(
            99,
            expect.objectContaining({
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                problemStatementFull: 'Current content',
                clientInstanceId: 'client-a',
            }),
        );
    });

    it('should ignore messages with older timestamps', () => {
        const received: string[] = [];
        service.init(99, 'Initial', 'client-a');
        service.updates$.subscribe((content) => received.push(content));

        // Process newer message first
        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementFull: 'Newer content',
            clientInstanceId: 'client-b',
            timestamp: 2000,
        });

        // Try to process older message - should be ignored
        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementFull: 'Older content',
            clientInstanceId: 'client-c',
            timestamp: 1000,
        });

        expect(received).toEqual(['Newer content']);
    });

    it('should clean up subscriptions on dispose', () => {
        service.init(99, 'Initial', 'client-a');

        const syncSubscription = service['syncSubscription'];
        const outgoingSyncSubscription = service['outgoingSyncSubscription'];

        const unsubscribeSpy = jest.spyOn(syncSubscription!, 'unsubscribe');
        const outgoingUnsubscribeSpy = jest.spyOn(outgoingSyncSubscription!, 'unsubscribe');

        service.dispose();

        expect(unsubscribeSpy).toHaveBeenCalled();
        expect(outgoingUnsubscribeSpy).toHaveBeenCalled();
    });

    it('should debounce local changes', fakeAsync(() => {
        service.init(42, 'Old content', 'client-a');

        service.queueLocalChange('Change 1');
        tick(100);
        service.queueLocalChange('Change 2');
        tick(100);
        service.queueLocalChange('Final change');
        tick(1000);

        // Should only send the last change after debounce
        expect(syncService.sendSynchronization).toHaveBeenCalledOnce();
        expect(syncService.sendSynchronization).toHaveBeenCalledWith(
            42,
            expect.objectContaining({
                problemStatementPatch: expect.any(String),
            }),
        );
    }));

    it('should not emit updates for non-PROBLEM_STATEMENT targets', () => {
        const received: string[] = [];
        service.init(99, 'Initial', 'client-a');
        service.updates$.subscribe((content) => received.push(content));

        incomingMessages$.next({
            target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
            problemStatementFull: 'Should be ignored',
            clientInstanceId: 'client-b',
            timestamp: 1,
        });

        expect(received).toEqual([]);
    });

    it('should not send patch if content unchanged', fakeAsync(() => {
        service.init(42, 'Same content', 'client-a');

        service.queueLocalChange('Same content');
        tick(1000);

        // Should not send synchronization if content is the same
        expect(syncService.sendSynchronization).not.toHaveBeenCalled();
    }));

    it('should only request initial sync once', () => {
        service.init(99, 'Initial', 'client-a');

        service.requestInitialSync();
        service.requestInitialSync();
        service.requestInitialSync();

        // Should only send the request once
        expect(syncService.sendSynchronization).toHaveBeenCalledOnce();
        expect(syncService.sendSynchronization).toHaveBeenCalledWith(
            99,
            expect.objectContaining({
                problemStatementRequest: true,
            }),
        );
    });
});
