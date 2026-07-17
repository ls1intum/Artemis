import { Mocked, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Injector, WritableSignal, runInInjectionContext, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import * as Y from 'yjs';
import { Awareness, encodeAwarenessUpdate } from 'y-protocols/awareness';
import { CodeEditorFileSyncService, DETERMINISTIC_SEED_CLIENT_ID, FileSyncState } from 'app/exercise/synchronization/services/code-editor-file-sync.service';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
    FileCreatedEvent,
    FileDeletedEvent,
    FileRenamedEvent,
} from 'app/exercise/synchronization/services/exercise-editor-sync.service';
import * as yjsUtils from 'app/exercise/synchronization/services/yjs-utils';

/**
 * Find the requestId of the most recently sent FILE_SYNC_FULL_CONTENT_REQUEST for `filePath` on a
 * mocked ExerciseEditorSyncService's `sendSynchronizationUpdate` spy.
 */
function captureRequestId(mock: { sendSynchronizationUpdate: ReturnType<typeof vi.fn> }, filePath: string): string {
    const call = mock.sendSynchronizationUpdate.mock.calls.find(
        ([, message]: [number, any]) => message.eventType === ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST && message.filePath === filePath,
    );
    return call?.[1].requestId as string;
}

/**
 * Seed a standalone Y.Doc exactly the way CodeEditorFileSyncService seeds a fresh per-file
 * document when no peer answers in time: using the same deterministic client id for the single
 * seed transaction. Used to simulate "another peer that also timed out and seeded identically".
 */
function seedPeerDocLikeProduction(content: string): Y.Doc {
    const doc = new Y.Doc();
    const text = doc.getText('file-content');
    const realClientId = doc.clientID;
    doc.clientID = DETERMINISTIC_SEED_CLIENT_ID;
    doc.transact(() => text.insert(0, content));
    doc.clientID = realClientId;
    return doc;
}

/**
 * A second, fully independent CodeEditorFileSyncService instance with its own mocked transport,
 * used to simulate a second real editor session opening the same file concurrently.
 */
type SyncPeer = {
    svc: CodeEditorFileSyncService;
    incoming: Subject<ExerciseEditorSyncEvent>;
    mockSync: {
        subscribeToUpdates: ReturnType<typeof vi.fn>;
        sendSynchronizationUpdate: ReturnType<typeof vi.fn>;
        unsubscribe: ReturnType<typeof vi.fn>;
        sessionId: string | undefined;
    };
};

function createPeer(sessionId: string): SyncPeer {
    const incoming = new Subject<ExerciseEditorSyncEvent>();
    const mockSync = {
        subscribeToUpdates: vi.fn().mockReturnValue(incoming.asObservable()),
        sendSynchronizationUpdate: vi.fn(),
        unsubscribe: vi.fn(),
        sessionId,
    };
    const injector = Injector.create({
        providers: [
            { provide: ExerciseEditorSyncService, useValue: mockSync },
            { provide: AccountService, useValue: { userIdentity: vi.fn().mockReturnValue(undefined) } },
            { provide: AlertService, useClass: MockAlertService },
        ],
    });
    const svc = runInInjectionContext(injector, () => new CodeEditorFileSyncService());
    return { svc, incoming, mockSync };
}

describe('CodeEditorFileSyncService', () => {
    let service: CodeEditorFileSyncService;
    let syncService: Mocked<ExerciseEditorSyncService>;
    let incomingMessages$: Subject<ExerciseEditorSyncEvent>;
    let userIdentitySignal: WritableSignal<any>;
    let alertService: AlertService;

    const EXERCISE_ID = 42;
    const TARGET = ExerciseEditorSyncTarget.TEMPLATE_REPOSITORY;
    const FILE_PATH = 'src/Main.java';

    beforeEach(() => {
        vi.useFakeTimers();
        incomingMessages$ = new Subject<ExerciseEditorSyncEvent>();
        userIdentitySignal = signal(undefined);

        TestBed.configureTestingModule({
            providers: [
                CodeEditorFileSyncService,
                {
                    provide: ExerciseEditorSyncService,
                    useValue: {
                        subscribeToUpdates: vi.fn().mockReturnValue(incomingMessages$.asObservable()),
                        sendSynchronizationUpdate: vi.fn(),
                        unsubscribe: vi.fn(),
                        sessionId: 'test-session-id',
                    },
                },
                {
                    provide: AccountService,
                    useValue: {
                        userIdentity: userIdentitySignal,
                    },
                },
                { provide: AlertService, useClass: MockAlertService },
            ],
        });

        service = TestBed.inject(CodeEditorFileSyncService);
        syncService = TestBed.inject(ExerciseEditorSyncService) as Mocked<ExerciseEditorSyncService>;
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        service?.reset();
        vi.useRealTimers();
        vi.clearAllMocks();
    });

    describe('uninitialized guards', () => {
        it('openFile returns undefined when not initialized', () => {
            expect(service.openFile(FILE_PATH, 'content')).toBeUndefined();
        });

        it('emitFileCreated does not send when not initialized', () => {
            service.emitFileCreated('src/New.java', 'FILE');
            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
        });

        it('emitFileDeleted does not send when not initialized', () => {
            service.emitFileDeleted('src/Old.java', 'FILE');
            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
        });

        it('emitFileRenamed does not send when not initialized', () => {
            service.emitFileRenamed('old.java', 'new.java', 'FILE');
            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();
        });

        it('incoming message before init is silently ignored', () => {
            // No init() call — currentTarget is undefined
            const doc = new Y.Doc();
            doc.getText('file-content').insert(0, 'should be ignored');
            expect(() =>
                incomingMessages$.next({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                    target: ExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                    filePath: FILE_PATH,
                    yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc)),
                    timestamp: 1,
                }),
            ).not.toThrow();
        });

        it('closeFile on never-opened file does not throw', () => {
            service.init(EXERCISE_ID, TARGET);
            expect(() => service.closeFile('nonexistent.java')).not.toThrow();
        });

        it('emitFileRenamed on a never-opened file does not throw and does not affect open files', () => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, 'content');
            vi.advanceTimersByTime(500);

            // Rename a file that was never opened — remapFileKey should return early
            expect(() => service.emitFileRenamed('ghost.java', 'ghost2.java', 'FILE')).not.toThrow();
            expect(service.isFileOpen(FILE_PATH)).toBe(true);
        });

        it('emitFileRenamed on a directory with no open files does not throw', () => {
            service.init(EXERCISE_ID, TARGET);
            // No files opened under 'src/empty/'
            expect(() => service.emitFileRenamed('src/empty', 'src/renamed', 'FOLDER')).not.toThrow();
        });

        it('emitFileDeleted on a directory with no open files does not throw', () => {
            service.init(EXERCISE_ID, TARGET);
            // No files opened under 'src/empty/'
            expect(() => service.emitFileDeleted('src/empty', 'FOLDER')).not.toThrow();
        });
    });

    describe('init and reset', () => {
        it('subscribes to websocket updates on init', () => {
            service.init(EXERCISE_ID, TARGET);
            expect(syncService.subscribeToUpdates).toHaveBeenCalled();
        });

        it('calling init() a second time cleans up the previous state', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'content')!;
            const destroySpy = vi.spyOn(state.doc, 'destroy');

            service.init(EXERCISE_ID, ExerciseEditorSyncTarget.SOLUTION_REPOSITORY);

            expect(destroySpy).toHaveBeenCalled();
            expect(service.isFileOpen(FILE_PATH)).toBe(false);
        });

        it('destroys all docs on reset', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'content')!;
            const destroySpy = vi.spyOn(state.doc, 'destroy');
            const clearStylesSpy = vi.spyOn(yjsUtils, 'clearRemoteSelectionStyles');

            service.reset();

            expect(destroySpy).toHaveBeenCalled();
            expect(clearStylesSpy).toHaveBeenCalled();
            clearStylesSpy.mockRestore();
        });
    });

    describe('expected repository updates', () => {
        const newCommitEvent = {
            eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: TARGET,
            timestamp: 1,
        } as ExerciseEditorSyncEvent;

        it('suppresses Hyperion-owned commit warnings only until the repository refresh completes', () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            service.init(EXERCISE_ID, TARGET);

            service.beginExpectedRepositoryUpdate();
            incomingMessages$.next(newCommitEvent);
            expect(addAlertSpy).not.toHaveBeenCalled();

            service.endExpectedRepositoryUpdate();
            incomingMessages$.next(newCommitEvent);
            expect(addAlertSpy).toHaveBeenCalledOnce();
        });

        it('dismisses an already delivered commit warning when the expected update begins', () => {
            const close = vi.fn();
            vi.spyOn(alertService, 'addAlert').mockReturnValue({ close } as any);
            service.init(EXERCISE_ID, TARGET);
            incomingMessages$.next(newCommitEvent);

            service.beginExpectedRepositoryUpdate();

            expect(close).toHaveBeenCalledOnce();
        });

        it('suppresses an expected commit event delivered after refresh completion without hiding a later external commit', () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            service.init(EXERCISE_ID, TARGET);

            service.beginExpectedRepositoryUpdate(100);
            service.endExpectedRepositoryUpdate();
            incomingMessages$.next({ ...newCommitEvent, timestamp: 99 } as ExerciseEditorSyncEvent);
            expect(addAlertSpy).not.toHaveBeenCalled();

            incomingMessages$.next({ ...newCommitEvent, timestamp: 101 } as ExerciseEditorSyncEvent);
            expect(addAlertSpy).toHaveBeenCalledOnce();
        });

        it('suppresses one late timestamp-less expected commit without hiding a subsequent unknown commit', () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            service.init(EXERCISE_ID, TARGET);
            const timestampLessEvent = { ...newCommitEvent, timestamp: undefined } as ExerciseEditorSyncEvent;

            service.beginExpectedRepositoryUpdate(100);
            service.endExpectedRepositoryUpdate();
            incomingMessages$.next(timestampLessEvent);
            expect(addAlertSpy).not.toHaveBeenCalled();

            incomingMessages$.next(timestampLessEvent);
            expect(addAlertSpy).toHaveBeenCalledOnce();
        });

        it('does not suppress an external timestamp-less commit when the expected one arrived during refresh', () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            service.init(EXERCISE_ID, TARGET);
            const timestampLessEvent = { ...newCommitEvent, timestamp: undefined } as ExerciseEditorSyncEvent;

            service.beginExpectedRepositoryUpdate(100);
            incomingMessages$.next(timestampLessEvent);
            service.endExpectedRepositoryUpdate();
            incomingMessages$.next(timestampLessEvent);

            expect(addAlertSpy).toHaveBeenCalledOnce();
        });

        it('does not suppress a newer external commit while the expected refresh is still active', () => {
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');
            service.init(EXERCISE_ID, TARGET);

            service.beginExpectedRepositoryUpdate(100);
            incomingMessages$.next({ ...newCommitEvent, timestamp: 101 } as ExerciseEditorSyncEvent);

            expect(addAlertSpy).toHaveBeenCalledOnce();
        });
    });

    describe('openFile and closeFile', () => {
        it('creates a Y.Doc and requests initial sync', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'initial content')!;

            expect(state.doc).toBeInstanceOf(Y.Doc);
            expect(state.text).toBeDefined();
            expect(state.awareness).toBeInstanceOf(Awareness);
            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST,
                    target: TARGET,
                    filePath: FILE_PATH,
                    requestId: expect.any(String),
                }),
            );
        });

        it('returns existing state if file is already open', () => {
            service.init(EXERCISE_ID, TARGET);
            const state1 = service.openFile(FILE_PATH, 'content')!;
            const state2 = service.openFile(FILE_PATH, 'different content')!;
            expect(state1.doc).toBe(state2.doc);
        });

        it('closes a file and destroys its doc', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'content')!;
            const destroySpy = vi.spyOn(state.doc, 'destroy');

            service.closeFile(FILE_PATH);

            expect(destroySpy).toHaveBeenCalled();
            expect(service.isFileOpen(FILE_PATH)).toBe(false);
        });
    });

    describe('initial sync protocol', () => {
        it('seeds fallback content when no peer responds', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'Fallback content')!;
            syncService.sendSynchronizationUpdate.mockClear();

            vi.advanceTimersByTime(500);

            expect(state.text.toString()).toBe('Fallback content');
            // Seed should NOT be rebroadcast
            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                }),
            );
        });

        it('emits initialSyncFinalized payload with non-divergent fallback content', () => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, 'Fallback content');
            const finalizedSpy = vi.fn();
            const sub = service.initialSyncFinalized$.subscribe(finalizedSpy);

            vi.advanceTimersByTime(500);

            expect(finalizedSpy).toHaveBeenCalledExactlyOnceWith({
                filePath: FILE_PATH,
                contentDivergedFromFallback: false,
                finalContent: 'Fallback content',
            });
            sub.unsubscribe();
        });

        it('uses earliest leader response during initial sync', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;

            const requestCall = syncService.sendSynchronizationUpdate.mock.calls.find(
                ([, msg]) => msg.eventType === ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST && (msg as any).filePath === FILE_PATH,
            );
            const requestId = (requestCall?.[1] as any).requestId as string;

            const laterDoc = new Y.Doc();
            laterDoc.getText('file-content').insert(0, 'Later leader');
            const earlierDoc = new Y.Doc();
            earlierDoc.getText('file-content').insert(0, 'Earlier leader');

            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(laterDoc)),
                leaderTimestamp: 200,
                timestamp: 1,
            });
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(earlierDoc)),
                leaderTimestamp: 100,
                timestamp: 2,
            });

            vi.advanceTimersByTime(500);
            expect(state.text.toString()).toBe('Earlier leader');
        });

        it('emits initialSyncFinalized payload with divergence when remote winner differs from fallback', () => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, 'Fallback');
            const finalizedSpy = vi.fn();
            const sub = service.initialSyncFinalized$.subscribe(finalizedSpy);

            const requestCall = syncService.sendSynchronizationUpdate.mock.calls.find(
                ([, msg]) => msg.eventType === ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST && (msg as any).filePath === FILE_PATH,
            );
            const requestId = (requestCall?.[1] as any).requestId as string;

            const remoteDoc = new Y.Doc();
            remoteDoc.getText('file-content').insert(0, 'Remote winner');
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(remoteDoc)),
                leaderTimestamp: 100,
                timestamp: 1,
            });

            vi.advanceTimersByTime(500);

            expect(finalizedSpy).toHaveBeenCalledExactlyOnceWith({
                filePath: FILE_PATH,
                contentDivergedFromFallback: true,
                finalContent: 'Remote winner',
            });
            sub.unsubscribe();
        });

        it('buffers incremental updates during initial sync', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;

            // Send an incremental update before timeout
            const doc = new Y.Doc();
            doc.getText('file-content').insert(0, 'Buffered text');
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                target: TARGET,
                filePath: FILE_PATH,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc)),
                timestamp: 1,
            });

            // Before timeout, text should be empty (update is buffered)
            expect(state.text.toString()).toBe('');

            vi.advanceTimersByTime(500);
            // After timeout, buffered update should be applied
            expect(state.text.toString()).toBe('Buffered text');
        });

        it('queues full-content requests while initializing and responds after finalize', () => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, 'Initial');
            syncService.sendSynchronizationUpdate.mockClear();

            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST,
                target: TARGET,
                filePath: FILE_PATH,
                requestId: 'queued-req',
                timestamp: 1,
            });

            // Should not respond while awaiting init
            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();

            vi.advanceTimersByTime(500);

            // After init finalized, should respond
            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                    responseTo: 'queued-req',
                    filePath: FILE_PATH,
                }),
            );
        });
    });

    describe('incremental sync', () => {
        it('sends yjs update for local doc changes', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;
            vi.advanceTimersByTime(500);
            syncService.sendSynchronizationUpdate.mockClear();

            state.text.insert(0, 'Local edit');

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                    target: TARGET,
                    filePath: FILE_PATH,
                    yjsUpdate: expect.any(String),
                }),
            );
        });

        it('applies incoming yjs updates to the doc', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;
            vi.advanceTimersByTime(500);

            const doc = new Y.Doc();
            doc.getText('file-content').insert(0, 'Remote edit');
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                target: TARGET,
                filePath: FILE_PATH,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc)),
                timestamp: 1,
            });

            expect(state.text.toString()).toBe('Remote edit');
        });
    });

    describe('sessionId tie-breaking', () => {
        it('uses sessionId tie-breaker when timestamps are equal during initial sync', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;

            const requestCall = syncService.sendSynchronizationUpdate.mock.calls.find(
                ([, msg]) => msg.eventType === ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST && (msg as any).filePath === FILE_PATH,
            );
            const requestId = (requestCall?.[1] as any).requestId as string;

            const doc1 = new Y.Doc();
            doc1.getText('file-content').insert(0, 'Response 1');
            const doc2 = new Y.Doc();
            doc2.getText('file-content').insert(0, 'Response 2');

            // Both responses have the same timestamp
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc1)),
                leaderTimestamp: 100,
                sessionId: 'session-bbb',
                timestamp: 1,
            });
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc2)),
                leaderTimestamp: 100,
                sessionId: 'session-aaa', // Lexicographically smaller
                timestamp: 2,
            });

            vi.advanceTimersByTime(500);
            // Should select 'session-aaa' due to tie-breaker
            expect(state.text.toString()).toBe('Response 2');
        });

        it('merges multiple late full-content responses into the file doc regardless of leaderTimestamp/sessionId ordering', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'Fallback')!;
            const requestId = captureRequestId(syncService, FILE_PATH);

            vi.advanceTimersByTime(500);
            expect(state.text.toString()).toBe('Fallback');

            // peerA forks from the same deterministic seed as this file entry and appends '-A'.
            const peerADoc = seedPeerDocLikeProduction('Fallback');
            peerADoc.getText('file-content').insert(peerADoc.getText('file-content').length, '-A');

            // peerB continues from peerA's already-edited state (causally after '-A') and appends '-B'.
            const peerBDoc = new Y.Doc();
            Y.applyUpdate(peerBDoc, Y.encodeStateAsUpdate(peerADoc));
            const peerBText = peerBDoc.getText('file-content');
            peerBText.insert(peerBText.length, '-B');

            // The first late response carries a WORSE (higher) leaderTimestamp and sessionId than
            // the second — under the old leader-election gate this ordering would have caused the
            // second, "better", response to win and the first to be rejected outright. Under the
            // merge-based design both must be merged in, regardless of ordering.
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(peerADoc)),
                leaderTimestamp: 999,
                sessionId: 'session-zzz',
                timestamp: 1,
            });
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(peerBDoc)),
                leaderTimestamp: 1,
                sessionId: 'session-aaa',
                timestamp: 2,
            });

            expect(state.text.toString()).toBe('Fallback-A-B');
        });
    });

    describe('late-winning response', () => {
        it('merges a late full-content response into the file doc instead of replacing it', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'Fallback')!;
            const requestId = captureRequestId(syncService, FILE_PATH);

            let replacedState: ({ filePath: string } & FileSyncState) | undefined;
            const sub = service.stateReplaced$.subscribe((s) => (replacedState = s));

            vi.advanceTimersByTime(500);
            expect(state.text.toString()).toBe('Fallback');

            // Simulate a peer that forked from the same deterministic seed (see
            // DETERMINISTIC_SEED_CLIENT_ID) and additionally appended its own edit on top of it.
            // Its full state arrives late, after this entry already finalized via its own seed.
            const peerDoc = seedPeerDocLikeProduction('Fallback');
            peerDoc.getText('file-content').insert(peerDoc.getText('file-content').length, ' from peer');

            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(peerDoc)),
                leaderTimestamp: 1,
                timestamp: 2,
            });

            // The peer's additional content is merged in (not discarded, and not used to replace
            // the file's doc/text identity — stateReplaced$ never fires under the merge design).
            expect(state.text.toString()).toBe('Fallback from peer');
            expect(replacedState).toBeUndefined();
            sub.unsubscribe();
        });

        it('does not wipe local edits made after initial sync finalized when a late full state merges in', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'Fallback')!;
            const requestId = captureRequestId(syncService, FILE_PATH);
            vi.advanceTimersByTime(500);
            state.text.insert(state.text.length, ' LOCAL');
            let replacedState: ({ filePath: string } & FileSyncState) | undefined;
            const sub = service.stateReplaced$.subscribe((replacement) => (replacedState = replacement));

            // A peer that seeded the identical fallback content independently (same deterministic
            // seed client id) and made no further edits of its own is only now, late, echoing that
            // state back. Merging it is a structural no-op for the shared seed.
            const peerDoc = seedPeerDocLikeProduction('Fallback');

            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(peerDoc)),
                leaderTimestamp: 1,
                timestamp: 2,
            });

            // The file's doc is never replaced, and the local edit survives untouched.
            expect(replacedState).toBeUndefined();
            expect(state.text.toString()).toBe('Fallback LOCAL');
            sub.unsubscribe();
        });
    });

    describe('awareness', () => {
        it('applies awareness updates and registers remote styles', () => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, '');
            vi.advanceTimersByTime(500);

            const ensureStyleSpy = vi.spyOn(yjsUtils, 'ensureRemoteSelectionStyle').mockImplementation(() => undefined);

            const remoteDoc = new Y.Doc();
            const remoteAwareness = new Awareness(remoteDoc);
            remoteAwareness.setLocalStateField('user', { name: 'Remote User', color: '#abcdef' });
            const update = encodeAwarenessUpdate(remoteAwareness, [remoteAwareness.clientID]);

            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_AWARENESS_UPDATE,
                target: TARGET,
                filePath: FILE_PATH,
                awarenessUpdate: yjsUtils.encodeUint8ArrayToBase64(update),
                timestamp: 1,
            });

            expect(ensureStyleSpy).toHaveBeenCalledWith(remoteAwareness.clientID, '#abcdef', 'Remote User');
            ensureStyleSpy.mockRestore();
        });

        it('updates local awareness name from user identity', () => {
            userIdentitySignal.set({ name: 'Ada Lovelace', login: 'ada' });

            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;

            expect(state.awareness.getLocalState()?.user?.name).toBe('Ada Lovelace');
        });
    });

    describe('file tree events', () => {
        it('emits FILE_CREATED event', () => {
            service.init(EXERCISE_ID, TARGET);
            service.emitFileCreated('src/New.java', 'FILE');

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_CREATED,
                    target: TARGET,
                    filePath: 'src/New.java',
                    fileType: 'FILE',
                }),
            );
        });

        it('emits FILE_DELETED event and closes local doc', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'content')!;
            vi.advanceTimersByTime(500);
            const destroySpy = vi.spyOn(state.doc, 'destroy');

            service.emitFileDeleted(FILE_PATH, 'FILE');

            expect(destroySpy).toHaveBeenCalled();
            expect(service.isFileOpen(FILE_PATH)).toBe(false);
            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_DELETED,
                    filePath: FILE_PATH,
                    fileType: 'FILE',
                }),
            );
        });

        it('emits FILE_RENAMED event and remaps doc key', () => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, 'content');
            vi.advanceTimersByTime(500);

            const newPath = 'src/Renamed.java';
            service.emitFileRenamed(FILE_PATH, newPath, 'FILE');

            expect(service.isFileOpen(FILE_PATH)).toBe(false);
            expect(service.isFileOpen(newPath)).toBe(true);
            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_RENAMED,
                    oldPath: FILE_PATH,
                    newPath,
                    fileType: 'FILE',
                }),
            );
        });

        it('handles remote FILE_CREATED by emitting on fileTreeChange$', () => {
            service.init(EXERCISE_ID, TARGET);

            let received: FileCreatedEvent | FileDeletedEvent | FileRenamedEvent | undefined;
            const sub = service.fileTreeChange$.subscribe((e) => (received = e));

            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_CREATED,
                target: TARGET,
                filePath: 'src/Remote.java',
                fileType: 'FILE',
                timestamp: 1,
            });

            expect(received).toBeDefined();
            expect(received?.eventType).toBe(ExerciseEditorSyncEventType.FILE_CREATED);
            sub.unsubscribe();
        });

        it('handles remote FILE_DELETED by closing doc and emitting on fileTreeChange$', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'content')!;
            vi.advanceTimersByTime(500);
            const destroySpy = vi.spyOn(state.doc, 'destroy');

            let received: FileCreatedEvent | FileDeletedEvent | FileRenamedEvent | undefined;
            const sub = service.fileTreeChange$.subscribe((e) => (received = e));

            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_DELETED,
                target: TARGET,
                filePath: FILE_PATH,
                fileType: 'FILE',
                timestamp: 1,
            });

            expect(destroySpy).toHaveBeenCalled();
            expect(received?.eventType).toBe(ExerciseEditorSyncEventType.FILE_DELETED);
            sub.unsubscribe();
        });

        it('handles remote FILE_RENAMED by remapping key and emitting on fileTreeChange$', () => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, 'content');
            vi.advanceTimersByTime(500);

            let received: FileCreatedEvent | FileDeletedEvent | FileRenamedEvent | undefined;
            const sub = service.fileTreeChange$.subscribe((e) => (received = e));

            const newPath = 'src/Renamed.java';
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_RENAMED,
                target: TARGET,
                oldPath: FILE_PATH,
                newPath,
                fileType: 'FILE',
                timestamp: 1,
            });

            expect(service.isFileOpen(FILE_PATH)).toBe(false);
            expect(service.isFileOpen(newPath)).toBe(true);
            expect(received?.eventType).toBe(ExerciseEditorSyncEventType.FILE_RENAMED);
            sub.unsubscribe();
        });
    });

    describe('rename handling', () => {
        it('remaps directory keys for all files under the directory', () => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile('src/pkg/A.java', 'A');
            service.openFile('src/pkg/B.java', 'B');
            vi.advanceTimersByTime(500);

            service.emitFileRenamed('src/pkg', 'src/newpkg', 'FOLDER');

            expect(service.isFileOpen('src/pkg/A.java')).toBe(false);
            expect(service.isFileOpen('src/pkg/B.java')).toBe(false);
            expect(service.isFileOpen('src/newpkg/A.java')).toBe(true);
            expect(service.isFileOpen('src/newpkg/B.java')).toBe(true);
        });

        it('applies late updates on old path via recentRenames', () => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, '');
            vi.advanceTimersByTime(500);

            const newPath = 'src/Renamed.java';
            service.emitFileRenamed(FILE_PATH, newPath, 'FILE');

            // Send an update addressed to the old path
            const doc = new Y.Doc();
            doc.getText('file-content').insert(0, 'Late update on old path');
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                target: TARGET,
                filePath: FILE_PATH,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc)),
                timestamp: 1,
            });

            // openFile returns the existing entry under the new key (remapped by rename)
            const state = service.openFile(newPath, '')!;
            expect(state.text.toString()).toBe('Late update on old path');
        });

        it('sends outgoing doc updates with the new path after a local rename', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;
            vi.advanceTimersByTime(500);

            const newPath = 'src/Renamed.java';
            service.emitFileRenamed(FILE_PATH, newPath, 'FILE');
            syncService.sendSynchronizationUpdate.mockClear();

            // Trigger a local doc update
            state.text.insert(0, 'Edit after rename');

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                    filePath: newPath,
                }),
            );
            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                    filePath: FILE_PATH,
                }),
            );
        });

        it('sends outgoing awareness updates with the new path after a local rename', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;
            vi.advanceTimersByTime(500);

            const newPath = 'src/Renamed.java';
            service.emitFileRenamed(FILE_PATH, newPath, 'FILE');
            syncService.sendSynchronizationUpdate.mockClear();

            // Trigger a local awareness update
            state.awareness.setLocalStateField('cursor', { line: 5 });

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_AWARENESS_UPDATE,
                    filePath: newPath,
                }),
            );
            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_AWARENESS_UPDATE,
                    filePath: FILE_PATH,
                }),
            );
        });

        it('merges a late full-content response addressed to the old path into the entry now living under the new path', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'Fallback')!;
            const requestId = captureRequestId(syncService, FILE_PATH);

            vi.advanceTimersByTime(500);
            expect(state.text.toString()).toBe('Fallback');

            const newPath = 'src/Renamed.java';
            service.emitFileRenamed(FILE_PATH, newPath, 'FILE');

            let replacedState: ({ filePath: string } & FileSyncState) | undefined;
            const sub = service.stateReplaced$.subscribe((s: { filePath: string } & FileSyncState) => (replacedState = s));

            // A peer forked from the same deterministic seed and appended its own edit; its full
            // state arrives late, addressed to the pre-rename path (the response was generated
            // from the original request, before the peer learned about the rename).
            const peerDoc = seedPeerDocLikeProduction('Fallback');
            peerDoc.getText('file-content').insert(peerDoc.getText('file-content').length, ' from peer');
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH, // old path, as the response was generated from the request
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(peerDoc)),
                leaderTimestamp: 1,
                timestamp: 2,
            });

            // The message is redirected via recentRenames and merged into the entry now living
            // under the new path — never replacing it, so stateReplaced$ stays inert.
            expect(replacedState).toBeUndefined();
            const stateAtNewPath = service.openFile(newPath, '')!;
            expect(stateAtNewPath.text.toString()).toBe('Fallback from peer');
            expect(service.isFileOpen(newPath)).toBe(true);
            expect(service.isFileOpen(FILE_PATH)).toBe(false);
            sub.unsubscribe();
        });

        it('handleFullContentRequest responds with the new path after a rename', () => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, 'content');
            vi.advanceTimersByTime(500);

            const newPath = 'src/Renamed.java';
            service.emitFileRenamed(FILE_PATH, newPath, 'FILE');
            syncService.sendSynchronizationUpdate.mockClear();

            // Peer requests full content using the old path (rename not yet processed by peer)
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST,
                target: TARGET,
                filePath: FILE_PATH,
                requestId: 'req-after-rename',
                timestamp: 1,
            });

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                    filePath: newPath,
                    responseTo: 'req-after-rename',
                }),
            );
        });
    });

    describe('responds to full-content requests', () => {
        it('responds with current document state after init', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;
            vi.advanceTimersByTime(500);
            state.text.insert(0, 'Current content');
            syncService.sendSynchronizationUpdate.mockClear();

            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST,
                target: TARGET,
                filePath: FILE_PATH,
                requestId: 'req-abc',
                timestamp: 1,
            });

            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                    target: TARGET,
                    filePath: FILE_PATH,
                    responseTo: 'req-abc',
                    yjsUpdate: expect.any(String),
                    leaderTimestamp: expect.any(Number),
                }),
            );

            const response = syncService.sendSynchronizationUpdate.mock.calls[0][1] as unknown as { yjsUpdate: string };
            const decoded = yjsUtils.decodeBase64ToUint8Array(response.yjsUpdate);
            const responseDoc = new Y.Doc();
            Y.applyUpdate(responseDoc, decoded);
            expect(responseDoc.getText('file-content').toString()).toBe('Current content');
        });
    });

    describe('message filtering', () => {
        it('ignores messages for a different auxiliary repository id', () => {
            service.init(EXERCISE_ID, ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 1);
            const state = service.openFile(FILE_PATH, '')!;
            vi.advanceTimersByTime(500);

            const doc = new Y.Doc();
            doc.getText('file-content').insert(0, 'Wrong aux repo');
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                target: ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY,
                filePath: FILE_PATH,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc)),
                auxiliaryRepositoryId: 999,
                timestamp: 1,
            });

            expect(state.text.toString()).toBe('');
        });

        it('ignores messages for auxiliary repository that are missing auxiliaryRepositoryId', () => {
            service.init(EXERCISE_ID, ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 1);
            const state = service.openFile(FILE_PATH, '')!;
            vi.advanceTimersByTime(500);

            const doc = new Y.Doc();
            doc.getText('file-content').insert(0, 'Missing aux id');
            // Message has no auxiliaryRepositoryId property at all
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                target: ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY,
                filePath: FILE_PATH,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc)),
                timestamp: 1,
            } as any);

            expect(state.text.toString()).toBe('');
        });

        it('ignores messages for a different target', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;
            vi.advanceTimersByTime(500);

            const doc = new Y.Doc();
            doc.getText('file-content').insert(0, 'Wrong target');
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                target: ExerciseEditorSyncTarget.SOLUTION_REPOSITORY,
                filePath: FILE_PATH,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc)),
                timestamp: 1,
            });

            expect(state.text.toString()).toBe('');
        });
    });

    describe('concurrent-join convergence (two real peers)', () => {
        it('converges when two peers open the same file simultaneously, both time out and independently seed identical content, then edit concurrently', () => {
            // Two fully independent CodeEditorFileSyncService instances, each with its own mocked
            // transport, simulate two editors opening the same file at nearly the same moment.
            // Neither answers the other's full-content request in time.
            const peerA = createPeer('peer-a');
            const peerB = createPeer('peer-b');

            peerA.svc.init(EXERCISE_ID, TARGET);
            peerB.svc.init(EXERCISE_ID, TARGET);

            const stateA = peerA.svc.openFile(FILE_PATH, 'Base')!;
            const stateB = peerB.svc.openFile(FILE_PATH, 'Base')!;

            const requestIdA = captureRequestId(peerA.mockSync, FILE_PATH);
            const requestIdB = captureRequestId(peerB.mockSync, FILE_PATH);
            expect(requestIdA).toBeDefined();
            expect(requestIdB).toBeDefined();

            // Both peers time out with no answer and independently seed the identical fallback
            // content into their own, structurally distinct Y.Doc instances.
            vi.advanceTimersByTime(500);
            expect(stateA.text.toString()).toBe('Base');
            expect(stateB.text.toString()).toBe('Base');

            // Each peer then edits locally, before either has heard from the other.
            stateA.text.insert(stateA.text.length, '-A');
            stateB.text.insert(stateB.text.length, '-B');

            // The network eventually catches up: each peer's full current state (seed + its own
            // edit) is delivered to the other as a late full-content response, as if the original
            // request had finally been answered.
            peerB.incoming.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestIdB,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(stateA.doc)),
                leaderTimestamp: 1,
                timestamp: 1,
            });
            peerA.incoming.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestIdA,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(stateB.doc)),
                leaderTimestamp: 1,
                timestamp: 1,
            });

            // Both documents must converge to the exact same final text, and neither peer's edit
            // may have been silently lost.
            expect(stateA.text.toString()).toBe(stateB.text.toString());
            expect(stateA.text.toString()).toContain('-A');
            expect(stateA.text.toString()).toContain('-B');

            peerA.svc.reset();
            peerB.svc.reset();
        });
    });
});
