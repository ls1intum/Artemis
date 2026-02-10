import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Subject } from 'rxjs';
import * as Y from 'yjs';
import { Awareness, encodeAwarenessUpdate } from 'y-protocols/awareness';
import { CodeEditorFileSyncService, FileSyncState } from 'app/programming/manage/services/code-editor-file-sync.service';
import { AccountService } from 'app/core/auth/account.service';
import {
    ExerciseEditorSyncEvent,
    ExerciseEditorSyncEventType,
    ExerciseEditorSyncService,
    ExerciseEditorSyncTarget,
    FileCreatedEvent,
    FileDeletedEvent,
    FileRenamedEvent,
} from 'app/exercise/services/exercise-editor-sync.service';
import * as yjsUtils from 'app/programming/manage/services/yjs-utils';

describe('CodeEditorFileSyncService', () => {
    let service: CodeEditorFileSyncService;
    let syncService: jest.Mocked<ExerciseEditorSyncService>;
    let incomingMessages$: Subject<ExerciseEditorSyncEvent>;

    const EXERCISE_ID = 42;
    const TARGET = ExerciseEditorSyncTarget.TEMPLATE_REPOSITORY;
    const FILE_PATH = 'src/Main.java';

    beforeEach(() => {
        incomingMessages$ = new Subject<ExerciseEditorSyncEvent>();

        TestBed.configureTestingModule({
            providers: [
                CodeEditorFileSyncService,
                {
                    provide: ExerciseEditorSyncService,
                    useValue: {
                        subscribeToUpdates: jest.fn().mockReturnValue(incomingMessages$.asObservable()),
                        sendSynchronizationUpdate: jest.fn(),
                        unsubscribe: jest.fn(),
                        sessionId: 'test-session-id',
                    },
                },
                {
                    provide: AccountService,
                    useValue: {
                        userIdentity: jest.fn().mockReturnValue(undefined),
                    },
                },
            ],
        });

        service = TestBed.inject(CodeEditorFileSyncService);
        syncService = TestBed.inject(ExerciseEditorSyncService) as jest.Mocked<ExerciseEditorSyncService>;
    });

    afterEach(() => {
        service?.reset();
        jest.clearAllMocks();
    });

    describe('init and reset', () => {
        it('subscribes to websocket updates on init', () => {
            service.init(EXERCISE_ID, TARGET);
            expect(syncService.subscribeToUpdates).toHaveBeenCalledWith(EXERCISE_ID);
        });

        it('calling init() a second time cleans up the previous state', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'content')!;
            const destroySpy = jest.spyOn(state.doc, 'destroy');

            service.init(EXERCISE_ID, ExerciseEditorSyncTarget.SOLUTION_REPOSITORY);

            expect(destroySpy).toHaveBeenCalled();
            expect(service.isFileOpen(FILE_PATH)).toBeFalse();
        });

        it('destroys all docs on reset', () => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'content')!;
            const destroySpy = jest.spyOn(state.doc, 'destroy');
            const clearStylesSpy = jest.spyOn(yjsUtils, 'clearRemoteSelectionStyles');

            service.reset();

            expect(destroySpy).toHaveBeenCalled();
            expect(clearStylesSpy).toHaveBeenCalled();
            clearStylesSpy.mockRestore();
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
            const destroySpy = jest.spyOn(state.doc, 'destroy');

            service.closeFile(FILE_PATH);

            expect(destroySpy).toHaveBeenCalled();
            expect(service.isFileOpen(FILE_PATH)).toBeFalse();
        });
    });

    describe('initial sync protocol', () => {
        it('seeds fallback content when no peer responds', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'Fallback content')!;
            syncService.sendSynchronizationUpdate.mockClear();

            tick(500);

            expect(state.text.toString()).toBe('Fallback content');
            // Seed should NOT be rebroadcast
            expect(syncService.sendSynchronizationUpdate).not.toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE,
                }),
            );
        }));

        it('uses earliest leader response during initial sync', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;

            const requestCall = (syncService.sendSynchronizationUpdate as jest.Mock).mock.calls.find(
                ([, msg]) => msg.eventType === ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST && msg.filePath === FILE_PATH,
            );
            const requestId = requestCall?.[1].requestId as string;

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

            tick(500);
            expect(state.text.toString()).toBe('Earlier leader');
        }));

        it('buffers incremental updates during initial sync', fakeAsync(() => {
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

            tick(500);
            // After timeout, buffered update should be applied
            expect(state.text.toString()).toBe('Buffered text');
        }));

        it('queues full-content requests while initializing and responds after finalize', fakeAsync(() => {
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

            tick(500);

            // After init finalized, should respond
            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                    responseTo: 'queued-req',
                    filePath: FILE_PATH,
                }),
            );
        }));
    });

    describe('incremental sync', () => {
        it('sends yjs update for local doc changes', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;
            tick(500);
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
        }));

        it('applies incoming yjs updates to the doc', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;
            tick(500);

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
        }));
    });

    describe('late-winning response', () => {
        it('replaces state when a better leader responds late', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'Fallback')!;

            const requestCall = (syncService.sendSynchronizationUpdate as jest.Mock).mock.calls.find(
                ([, msg]) => msg.eventType === ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST && msg.filePath === FILE_PATH,
            );
            const requestId = requestCall?.[1].requestId as string;

            let replacedState: ({ filePath: string } & FileSyncState) | undefined;
            const sub = service.stateReplaced$.subscribe((s) => (replacedState = s));

            tick(500);
            expect(state.text.toString()).toBe('Fallback');

            const lateDoc = new Y.Doc();
            lateDoc.getText('file-content').insert(0, 'Late winning content');
            incomingMessages$.next({
                eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE,
                target: TARGET,
                filePath: FILE_PATH,
                responseTo: requestId,
                yjsUpdate: yjsUtils.encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(lateDoc)),
                leaderTimestamp: 1,
                timestamp: 2,
            });

            expect(replacedState).toBeDefined();
            expect(replacedState?.filePath).toBe(FILE_PATH);
            expect(replacedState?.text.toString()).toBe('Late winning content');
            sub.unsubscribe();
        }));
    });

    describe('awareness', () => {
        it('applies awareness updates and registers remote styles', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, '');
            tick(500);

            const ensureStyleSpy = jest.spyOn(yjsUtils, 'ensureRemoteSelectionStyle').mockImplementation(() => undefined);

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
        }));

        it('updates local awareness name from user identity', () => {
            const accountService = TestBed.inject(AccountService);
            (accountService.userIdentity as jest.Mock).mockReturnValue({ name: 'Ada Lovelace', login: 'ada' } as any);

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

        it('emits FILE_DELETED event and closes local doc', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'content')!;
            tick(500);
            const destroySpy = jest.spyOn(state.doc, 'destroy');

            service.emitFileDeleted(FILE_PATH, 'FILE');

            expect(destroySpy).toHaveBeenCalled();
            expect(service.isFileOpen(FILE_PATH)).toBeFalse();
            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_DELETED,
                    filePath: FILE_PATH,
                    fileType: 'FILE',
                }),
            );
        }));

        it('emits FILE_RENAMED event and remaps doc key', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, 'content');
            tick(500);

            const newPath = 'src/Renamed.java';
            service.emitFileRenamed(FILE_PATH, newPath, 'FILE');

            expect(service.isFileOpen(FILE_PATH)).toBeFalse();
            expect(service.isFileOpen(newPath)).toBeTrue();
            expect(syncService.sendSynchronizationUpdate).toHaveBeenCalledWith(
                EXERCISE_ID,
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.FILE_RENAMED,
                    oldPath: FILE_PATH,
                    newPath,
                    fileType: 'FILE',
                }),
            );
        }));

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

        it('handles remote FILE_DELETED by closing doc and emitting on fileTreeChange$', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, 'content')!;
            tick(500);
            const destroySpy = jest.spyOn(state.doc, 'destroy');

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
        }));

        it('handles remote FILE_RENAMED by remapping key and emitting on fileTreeChange$', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, 'content');
            tick(500);

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

            expect(service.isFileOpen(FILE_PATH)).toBeFalse();
            expect(service.isFileOpen(newPath)).toBeTrue();
            expect(received?.eventType).toBe(ExerciseEditorSyncEventType.FILE_RENAMED);
            sub.unsubscribe();
        }));
    });

    describe('rename handling', () => {
        it('remaps directory keys for all files under the directory', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile('src/pkg/A.java', 'A');
            service.openFile('src/pkg/B.java', 'B');
            tick(500);

            service.emitFileRenamed('src/pkg', 'src/newpkg', 'FOLDER');

            expect(service.isFileOpen('src/pkg/A.java')).toBeFalse();
            expect(service.isFileOpen('src/pkg/B.java')).toBeFalse();
            expect(service.isFileOpen('src/newpkg/A.java')).toBeTrue();
            expect(service.isFileOpen('src/newpkg/B.java')).toBeTrue();
        }));

        it('applies late updates on old path via recentRenames', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            service.openFile(FILE_PATH, '');
            tick(500);

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
        }));
    });

    describe('responds to full-content requests', () => {
        it('responds with current document state after init', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;
            tick(500);
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

            const response = (syncService.sendSynchronizationUpdate as jest.Mock).mock.calls[0][1] as { yjsUpdate: string };
            const decoded = yjsUtils.decodeBase64ToUint8Array(response.yjsUpdate);
            const responseDoc = new Y.Doc();
            Y.applyUpdate(responseDoc, decoded);
            expect(responseDoc.getText('file-content').toString()).toBe('Current content');
        }));
    });

    describe('message filtering', () => {
        it('ignores messages for a different auxiliary repository id', fakeAsync(() => {
            service.init(EXERCISE_ID, ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY, 1);
            const state = service.openFile(FILE_PATH, '')!;
            tick(500);

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
        }));

        it('ignores messages for a different target', fakeAsync(() => {
            service.init(EXERCISE_ID, TARGET);
            const state = service.openFile(FILE_PATH, '')!;
            tick(500);

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
        }));
    });
});
