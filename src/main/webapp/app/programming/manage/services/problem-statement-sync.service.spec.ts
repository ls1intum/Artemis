import { TestBed, fakeAsync, flushMicrotasks, tick } from '@angular/core/testing';
import { Subject } from 'rxjs';
import * as Y from 'yjs';
import { Awareness, encodeAwarenessUpdate } from 'y-protocols/awareness';
import { ProblemStatementSyncService } from 'app/programming/manage/services/problem-statement-sync.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExerciseEditorSyncEvent, ExerciseEditorSyncEventType, ExerciseEditorSyncService, ExerciseEditorSyncTarget } from 'app/exercise/services/exercise-editor-sync.service';
import * as yjsUtils from 'app/programming/manage/services/yjs-utils';

describe('ProblemStatementSyncService', () => {
    let service: ProblemStatementSyncService;
    let syncService: jest.Mocked<ExerciseEditorSyncService>;
    let incomingMessages$: Subject<ExerciseEditorSyncEvent>;

    beforeEach(() => {
        incomingMessages$ = new Subject<ExerciseEditorSyncEvent>();

        TestBed.configureTestingModule({
            providers: [
                ProblemStatementSyncService,
                {
                    provide: ExerciseEditorSyncService,
                    useValue: {
                        subscribeToUpdates: jest.fn().mockReturnValue(incomingMessages$.asObservable()),
                        sendSynchronizationUpdate: jest.fn(),
                        unsubscribe: jest.fn(),
                    },
                },
                {
                    provide: AccountService,
                    useValue: {
                        identity: jest.fn().mockResolvedValue(undefined),
                    },
                },
            ],
        });

        service = TestBed.inject(ProblemStatementSyncService);
        syncService = TestBed.inject(ExerciseEditorSyncService) as jest.Mocked<ExerciseEditorSyncService>;
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

    it('applies incoming yjs updates to the doc', fakeAsync(() => {
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

        tick(500);
        expect(state.text.toString()).toBe('Hello Artemis');
    }));

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

        const response = (syncService.sendSynchronizationUpdate as jest.Mock).mock.calls[0][1] as { yjsUpdate: string };
        const decoded = yjsUtils.decodeBase64ToUint8Array(response.yjsUpdate);
        const responseDoc = new Y.Doc();
        Y.applyUpdate(responseDoc, decoded);
        expect(responseDoc.getText('problem-statement').toString()).toBe('Current content');
    });

    it('uses the earliest leader response during initial sync', fakeAsync(() => {
        const state = service.init(11, '');
        const requestCall = (syncService.sendSynchronizationUpdate as jest.Mock).mock.calls.find(
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

        tick(500);
        expect(state.text.toString()).toBe('Earlier leader');
    }));

    it('seeds fallback content and broadcasts it when no response arrives', fakeAsync(() => {
        const state = service.init(13, 'Fallback statement');
        syncService.sendSynchronizationUpdate.mockClear();

        tick(500);

        expect(state.text.toString()).toBe('Fallback statement');
        expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
            13,
            expect.objectContaining({
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
                yjsUpdate: expect.any(String),
            }),
        );
    }));

    it('applies awareness updates and registers remote client styles', () => {
        service.init(15, '');

        const ensureStyleSpy = jest.spyOn(yjsUtils, 'ensureRemoteSelectionStyle').mockImplementation(() => undefined);

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

    it('updates local awareness name once identity resolves', fakeAsync(() => {
        const accountService = TestBed.inject(AccountService) as jest.Mocked<AccountService>;
        accountService.identity.mockResolvedValue({ name: 'Ada Lovelace', login: 'ada' } as any);

        const state = service.init(17, '');
        flushMicrotasks();

        expect(state.awareness.getLocalState()?.user?.name).toBe('Ada Lovelace');
    }));

    it('resets state and destroys the Yjs document', fakeAsync(() => {
        const state = service.init(19, 'Seed');
        const destroySpy = jest.spyOn(state.doc, 'destroy');

        service.reset();

        expect(destroySpy).toHaveBeenCalled();
        tick(500);
    }));
});
