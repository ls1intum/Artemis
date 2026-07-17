import { Mocked, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Injector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import * as Y from 'yjs';
import { Awareness, encodeAwarenessUpdate } from 'y-protocols/awareness';
import { DETERMINISTIC_SEED_CLIENT_ID, ProblemStatementSyncService, ProblemStatementSyncState } from 'app/exercise/synchronization/services/problem-statement-sync.service';
import { AccountService } from 'app/core/auth/account.service';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
} from 'app/exercise/synchronization/services/exercise-editor-sync.service';
import * as yjsUtils from 'app/exercise/synchronization/services/yjs-utils';

/**
 * Find the requestId of the most recently sent PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST on a
 * mocked ExerciseEditorSyncService's `sendSynchronizationUpdate` spy.
 */
function captureRequestId(mock: { sendSynchronizationUpdate: ReturnType<typeof vi.fn> }): string {
    const call = mock.sendSynchronizationUpdate.mock.calls.find(
        ([, message]: [number, any]) => message.eventType === ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
    );
    return call?.[1].requestId as string;
}

/**
 * Seed a standalone Y.Doc exactly the way ProblemStatementSyncService seeds a fresh document when
 * no peer answers in time: using the same deterministic client id for the single seed
 * transaction. Used to simulate "another peer that also timed out and seeded identically".
 */
function seedPeerDocLikeProduction(content: string): Y.Doc {
    const doc = new Y.Doc();
    const text = doc.getText('problem-statement');
    const realClientId = doc.clientID;
    doc.clientID = DETERMINISTIC_SEED_CLIENT_ID;
    doc.transact(() => text.insert(0, content));
    doc.clientID = realClientId;
    return doc;
}

/**
 * A second, fully independent ProblemStatementSyncService instance with its own mocked transport,
 * used to simulate a second real editor session joining the same exercise concurrently.
 */
type SyncPeer = {
    svc: ProblemStatementSyncService;
    incoming: Subject<ExerciseEditorSyncEvent>;
    mockSync: {
        subscribeToUpdates: ReturnType<typeof vi.fn>;
        sendSynchronizationUpdate: ReturnType<typeof vi.fn>;
        connect: ReturnType<typeof vi.fn>;
        disconnect: ReturnType<typeof vi.fn>;
        sessionId: string | undefined;
    };
};

function createPeer(sessionId: string): SyncPeer {
    const incoming = new Subject<ExerciseEditorSyncEvent>();
    const mockSync = {
        subscribeToUpdates: vi.fn().mockReturnValue(incoming.asObservable()),
        sendSynchronizationUpdate: vi.fn(),
        connect: vi.fn(),
        disconnect: vi.fn(),
        sessionId,
    };
    const injector = Injector.create({
        providers: [
            { provide: ExerciseEditorSyncService, useValue: mockSync },
            { provide: AccountService, useValue: { userIdentity: vi.fn().mockReturnValue(undefined) } },
        ],
    });
    const svc = runInInjectionContext(injector, () => new ProblemStatementSyncService());
    return { svc, incoming, mockSync };
}

describe('ProblemStatementSyncService', () => {
    let service: ProblemStatementSyncService;
    let syncService: Mocked<ExerciseEditorSyncService>;
    let syncServiceMock: {
        subscribeToUpdates: ReturnType<typeof vi.fn>;
        sendSynchronizationUpdate: ReturnType<typeof vi.fn>;
        connect: ReturnType<typeof vi.fn>;
        disconnect: ReturnType<typeof vi.fn>;
        sessionId: string | undefined;
    };
    let incomingMessages$: Subject<ExerciseEditorSyncEvent>;

    beforeEach(() => {
        vi.useFakeTimers();
        incomingMessages$ = new Subject<ExerciseEditorSyncEvent>();
        syncServiceMock = {
            subscribeToUpdates: vi.fn().mockReturnValue(incomingMessages$.asObservable()),
            sendSynchronizationUpdate: vi.fn(),
            connect: vi.fn(),
            disconnect: vi.fn(),
            sessionId: undefined,
        };

        TestBed.configureTestingModule({
            providers: [
                ProblemStatementSyncService,
                {
                    provide: ExerciseEditorSyncService,
                    useValue: syncServiceMock,
                },
                {
                    provide: AccountService,
                    useValue: {
                        userIdentity: vi.fn().mockReturnValue(undefined),
                    },
                },
            ],
        });

        service = TestBed.inject(ProblemStatementSyncService);
        syncService = TestBed.inject(ExerciseEditorSyncService) as Mocked<ExerciseEditorSyncService>;
    });

    afterEach(() => {
        service?.reset();
        vi.useRealTimers();
        vi.clearAllMocks();
    });

    it('initializes synchronization and requests initial content', () => {
        service.init(42, 'Initial content');

        expect(syncService.subscribeToUpdates).toHaveBeenCalledWith();
        expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
            42,
            expect.objectContaining({
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                requestId: expect.any(String),
            }),
        );
    });

    it('sends yjs update for local doc changes', () => {
        const state = service.init(42, 'Old content');
        state.text.insert(0, 'Updated ');

        expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
            42,
            expect.objectContaining({
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
                yjsUpdate: expect.any(String),
            }),
        );
    });

    it('applies incoming yjs updates to the doc', () => {
        const state = service.init(99, '');

        const doc = new Y.Doc();
        doc.getText('problem-statement').insert(0, 'Hello Artemis');
        const update = yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc));
        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            yjsUpdate: update,
            timestamp: 1,
        });

        // Advance past the initial sync timeout, which finalizes initialization and applies
        // buffered updates. Updates arriving during the pending init window are held until then.
        vi.advanceTimersByTime(500);
        expect(state.text.toString()).toBe('Hello Artemis');
    });

    it('responds to full-content requests with the current document state', () => {
        const state = service.init(7, '');
        state.text.insert(0, 'Current content');
        syncService.sendSynchronizationUpdate.mockClear();

        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            requestId: 'req-123',
            timestamp: 1,
        });
        vi.advanceTimersByTime(500);

        expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
            7,
            expect.objectContaining({
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                responseTo: 'req-123',
                yjsUpdate: expect.any(String),
                leaderTimestamp: expect.any(Number),
            }),
        );

        const response = (syncService.sendSynchronizationUpdate as ReturnType<typeof vi.fn>).mock.calls[0][1] as { yjsUpdate: string };
        const decoded = yjsUtils.decodeBase64ToUint8Array(response.yjsUpdate);
        const responseDoc = new Y.Doc();
        Y.applyUpdate(responseDoc, decoded);
        expect(responseDoc.getText('problem-statement').toString()).toBe('Current content');
    });

    it('uses the earliest leader response during initial sync', () => {
        const state = service.init(11, '');
        const requestCall = (syncService.sendSynchronizationUpdate as ReturnType<typeof vi.fn>).mock.calls.find(
            ([, message]) => message.eventType === ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
        );
        const requestId = requestCall?.[1].requestId as string;
        expect(requestId).toBeDefined();

        const laterDoc = new Y.Doc();
        laterDoc.getText('problem-statement').insert(0, 'Later leader');
        const laterUpdate = yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(laterDoc));

        const earlierDoc = new Y.Doc();
        earlierDoc.getText('problem-statement').insert(0, 'Earlier leader');
        const earlierUpdate = yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(earlierDoc));

        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId,
            yjsUpdate: laterUpdate,
            leaderTimestamp: 200,
            timestamp: 1,
        });
        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId,
            yjsUpdate: earlierUpdate,
            leaderTimestamp: 100,
            timestamp: 2,
        });

        vi.advanceTimersByTime(500);
        expect(state.text.toString()).toBe('Earlier leader');
    });

    it('queues full-content requests while initializing and responds after finalize', () => {
        const state = service.init(12, '');
        const requestIdToQueue = 'queued-request';
        syncService.sendSynchronizationUpdate.mockClear();

        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            requestId: requestIdToQueue,
            timestamp: 1,
        });
        expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalled();

        vi.advanceTimersByTime(500);

        expect(state.text.toString()).toBe('');
        expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
            12,
            expect.objectContaining({
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                responseTo: requestIdToQueue,
            }),
        );
    });

    it('merges a late full-content response into the local doc instead of replacing it', () => {
        const state = service.init(14, 'Fallback statement');
        const requestId = captureRequestId(syncServiceMock);
        expect(requestId).toBeDefined();

        let replacedState: ProblemStatementSyncState | undefined;
        const subscription = service.stateReplaced$.subscribe((nextState) => {
            replacedState = nextState;
        });

        vi.advanceTimersByTime(500);
        expect(state.text.toString()).toBe('Fallback statement');

        // Simulate a peer that forked from the same deterministic seed (see
        // DETERMINISTIC_SEED_CLIENT_ID) and additionally appended its own edit on top of it. Its
        // full state arrives late, after this client already finalized via its own seed.
        const peerDoc = seedPeerDocLikeProduction('Fallback statement');
        peerDoc.getText('problem-statement').insert(peerDoc.getText('problem-statement').length, ' from peer');

        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId,
            yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(peerDoc)),
            leaderTimestamp: 1,
            timestamp: 2,
        });

        // The peer's additional content is merged in (not discarded, and not used to replace the
        // local doc/text identity — stateReplaced$ never fires under the merge-based design).
        expect(state.text.toString()).toBe('Fallback statement from peer');
        expect(replacedState).toBeUndefined();
        subscription.unsubscribe();
    });

    it('does not wipe local edits made after initial sync finalized when a late full state merges in', () => {
        const state = service.init(14, 'Fallback statement');
        const requestId = captureRequestId(syncServiceMock);
        vi.advanceTimersByTime(500);
        state.text.insert(state.text.length, ' LOCAL');
        let replacedState: ProblemStatementSyncState | undefined;
        const subscription = service.stateReplaced$.subscribe((replacement) => (replacedState = replacement));

        // A peer that seeded the identical fallback content independently (same deterministic
        // seed client id) and made no further edits of its own is only now, late, echoing that
        // state back. Merging it is a structural no-op for the shared seed.
        const peerDoc = seedPeerDocLikeProduction('Fallback statement');

        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId,
            yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(peerDoc)),
            leaderTimestamp: 1,
            timestamp: 2,
        });

        // The local doc is never replaced, and the local edit survives untouched.
        expect(replacedState).toBeUndefined();
        expect(state.text.toString()).toBe('Fallback statement LOCAL');
        subscription.unsubscribe();
    });

    it('seeds fallback content without rebroadcasting seed as sync update', () => {
        const state = service.init(13, 'Fallback statement');
        syncService.sendSynchronizationUpdate.mockClear();

        vi.advanceTimersByTime(500);

        expect(state.text.toString()).toBe('Fallback statement');
        expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalledWith(
            13,
            expect.objectContaining({
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            }),
        );
    });

    it('exposes awaitingInitialSync=false inside initialSyncFinalized subscribers', () => {
        const awaitingStateSeenInSubscriber: boolean[] = [];
        service.initialSyncFinalized$.subscribe(() => {
            awaitingStateSeenInSubscriber.push(service.isAwaitingInitialSync());
        });
        service.init(16, 'Fallback statement');

        expect(service.isAwaitingInitialSync()).toBe(true);
        vi.advanceTimersByTime(500);

        expect(awaitingStateSeenInSubscriber).toEqual([false]);
        expect(service.isAwaitingInitialSync()).toBe(false);
    });

    it('emits divergence=false when finalized content matches fallback', () => {
        const finalizedSpy = vi.fn();
        const sub = service.initialSyncFinalized$.subscribe(finalizedSpy);

        service.init(26, 'Fallback statement');
        vi.advanceTimersByTime(500);

        expect(finalizedSpy).toHaveBeenCalledWith({
            contentChangedDuringFinalize: true,
            contentDivergedFromFallback: false,
            finalContent: 'Fallback statement',
        });
        sub.unsubscribe();
    });

    it('emits divergence=true when finalized content differs from fallback', () => {
        const finalizedSpy = vi.fn();
        const sub = service.initialSyncFinalized$.subscribe(finalizedSpy);
        service.init(27, 'Fallback statement');

        const requestCall = (syncService.sendSynchronizationUpdate as ReturnType<typeof vi.fn>).mock.calls.find(
            ([, message]) => message.eventType === ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
        );
        const requestId = requestCall?.[1].requestId as string;
        expect(requestId).toBeDefined();

        const remoteDoc = new Y.Doc();
        remoteDoc.getText('problem-statement').insert(0, 'Remote unsaved statement');
        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId,
            yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(remoteDoc)),
            leaderTimestamp: 1,
            timestamp: 1,
        });

        vi.advanceTimersByTime(500);

        expect(finalizedSpy).toHaveBeenCalledWith({
            contentChangedDuringFinalize: true,
            contentDivergedFromFallback: true,
            finalContent: 'Remote unsaved statement',
        });
        sub.unsubscribe();
    });

    it('applies awareness updates and registers remote client styles', () => {
        service.init(15, '');

        const ensureStyleSpy = vi.spyOn(yjsUtils, 'ensureRemoteSelectionStyle').mockImplementation(() => undefined);

        const remoteDoc = new Y.Doc();
        const remoteAwareness = new Awareness(remoteDoc);
        remoteAwareness.setLocalStateField('user', { name: 'Remote User', color: '#123456' });
        const update = encodeAwarenessUpdate(remoteAwareness, [remoteAwareness.clientID]);
        const encoded = yjsUtils.encodeUint8ArrayToBase64(update);

        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_AWARENESS_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            awarenessUpdate: encoded,
            timestamp: 1,
        });

        expect(ensureStyleSpy).toHaveBeenCalledWith(remoteAwareness.clientID, '#123456', 'Remote User');
        ensureStyleSpy.mockRestore();
    });

    it('uses userIdentity directly without promises for awareness', () => {
        const accountService = TestBed.inject(AccountService) as Mocked<AccountService>;
        accountService.userIdentity.mockReturnValue({ name: 'Ada Lovelace', login: 'ada' } as any);

        const state = service.init(17, '');

        // Should use userIdentity synchronously (signal-based), not identity() promise
        expect(accountService.userIdentity).toHaveBeenCalled();
        expect(state.awareness.getLocalState()?.user?.name).toBe('Ada Lovelace');
    });

    it('uses fallback name when userIdentity returns undefined', () => {
        const accountService = TestBed.inject(AccountService) as Mocked<AccountService>;
        accountService.userIdentity.mockReturnValue(undefined);
        syncServiceMock.sessionId = 'abc123';

        const state = service.init(18, '');

        expect(state.awareness.getLocalState()?.user?.name).toContain('Editor');
    });

    it('uses sessionId tie-breaker when timestamps are equal', () => {
        const state = service.init(20, '');
        const requestCall = (syncService.sendSynchronizationUpdate as ReturnType<typeof vi.fn>).mock.calls.find(
            ([, message]) => message.eventType === ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
        );
        const requestId = requestCall?.[1].requestId as string;
        expect(requestId).toBeDefined();

        const doc1 = new Y.Doc();
        doc1.getText('problem-statement').insert(0, 'Response 1');
        const update1 = yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc1));

        const doc2 = new Y.Doc();
        doc2.getText('problem-statement').insert(0, 'Response 2');
        const update2 = yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc2));

        // Both responses have the same timestamp
        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId,
            yjsUpdate: update1,
            leaderTimestamp: 100,
            sessionId: 'session-bbb',
            timestamp: 1,
        });
        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId,
            yjsUpdate: update2,
            leaderTimestamp: 100,
            sessionId: 'session-aaa', // Lexicographically smaller
            timestamp: 2,
        });

        vi.advanceTimersByTime(500);
        // Should select 'session-aaa' due to tie-breaker
        expect(state.text.toString()).toBe('Response 2');
    });

    it('merges multiple late full-content responses regardless of leaderTimestamp/sessionId ordering', () => {
        const state = service.init(21, 'Fallback');
        const requestId = captureRequestId(syncServiceMock);
        expect(requestId).toBeDefined();

        vi.advanceTimersByTime(500);
        expect(state.text.toString()).toBe('Fallback');

        // peerA forks from the same deterministic seed as this client and appends '-A'.
        const peerADoc = seedPeerDocLikeProduction('Fallback');
        peerADoc.getText('problem-statement').insert(peerADoc.getText('problem-statement').length, '-A');

        // peerB continues from peerA's already-edited state (causally after '-A') and appends '-B'.
        const peerBDoc = new Y.Doc();
        Y.applyUpdate(peerBDoc, Y.encodeStateAsUpdate(peerADoc));
        const peerBText = peerBDoc.getText('problem-statement');
        peerBText.insert(peerBText.length, '-B');

        // The first late response carries a WORSE (higher) leaderTimestamp and sessionId than the
        // second — under the old leader-election gate this ordering would have caused the second,
        // "better", response to win and the first to be rejected outright. Under the merge-based
        // design both must be merged in, regardless of ordering.
        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId,
            yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(peerADoc)),
            leaderTimestamp: 999,
            sessionId: 'session-zzz',
            timestamp: 1,
        });
        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId,
            yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(peerBDoc)),
            leaderTimestamp: 1,
            sessionId: 'session-aaa',
            timestamp: 2,
        });

        expect(state.text.toString()).toBe('Fallback-A-B');
    });

    it('clears activeLeaderSessionId on reset so stale state does not persist across init cycles', () => {
        // First session: establish a leader with sessionId 'session-aaa'
        service.init(30, '');
        const requestCall1 = (syncService.sendSynchronizationUpdate as ReturnType<typeof vi.fn>).mock.calls.find(
            ([, message]) => message.eventType === ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
        );
        const requestId1 = requestCall1?.[1].requestId as string;
        expect(requestId1).toBeDefined();

        const doc1 = new Y.Doc();
        doc1.getText('problem-statement').insert(0, 'Session 1');
        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId1,
            yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc1)),
            leaderTimestamp: 50,
            sessionId: 'session-aaa',
            timestamp: 1,
        });
        vi.advanceTimersByTime(500);

        // Reset and start a new session
        service.reset();
        syncService.sendSynchronizationUpdate.mockClear();
        const state2 = service.init(31, '');
        const requestCall2 = (syncService.sendSynchronizationUpdate as ReturnType<typeof vi.fn>).mock.calls.find(
            ([, message]) => message.eventType === ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
        );
        const requestId2 = requestCall2?.[1].requestId as string;
        expect(requestId2).toBeDefined();

        // Response with a new sessionId should be accepted without interference from stale state
        const doc2 = new Y.Doc();
        doc2.getText('problem-statement').insert(0, 'Session 2');
        incomingMessages$.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: requestId2,
            yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc2)),
            leaderTimestamp: 50,
            sessionId: 'session-bbb',
            timestamp: 2,
        });
        vi.advanceTimersByTime(500);

        expect(state2.text.toString()).toBe('Session 2');
    });

    it('resets state and destroys the Yjs document', () => {
        const state = service.init(19, 'Seed');
        const destroySpy = vi.spyOn(state.doc, 'destroy');
        const clearRemoteStylesSpy = vi.spyOn(yjsUtils, 'clearRemoteSelectionStyles');

        service.reset();

        expect(destroySpy).toHaveBeenCalled();
        expect(clearRemoteStylesSpy).toHaveBeenCalledOnce();
        vi.advanceTimersByTime(500);
        clearRemoteStylesSpy.mockRestore();
    });

    describe('concurrent-join convergence (two real peers)', () => {
        it('converges when two peers join simultaneously, both time out and independently seed identical content, then edit concurrently', () => {
            // Two fully independent ProblemStatementSyncService instances, each with its own
            // mocked transport, simulate two editors opening the same exercise at nearly the
            // same moment. Neither answers the other's full-content request in time.
            const peerA = createPeer('peer-a');
            const peerB = createPeer('peer-b');

            const stateA = peerA.svc.init(50, 'Base');
            const stateB = peerB.svc.init(50, 'Base');

            const requestIdA = captureRequestId(peerA.mockSync);
            const requestIdB = captureRequestId(peerB.mockSync);
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
            // edit) is delivered to the other as a late full-content response, as if the
            // original request had finally been answered.
            peerB.incoming.next({
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                responseTo: requestIdB,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(stateA.doc)),
                leaderTimestamp: 1,
                timestamp: 1,
            });
            peerA.incoming.next({
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
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
