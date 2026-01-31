import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Subject } from 'rxjs';
import * as Y from 'yjs';
import { ProblemStatementSyncService } from 'app/programming/manage/services/problem-statement-sync.service';
import { AccountService } from 'app/core/auth/account.service';
import {
    ProgrammingExerciseEditorSyncEvent,
    ProgrammingExerciseEditorSyncEventType,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import { encodeUint8ArrayToBase64 } from 'app/programming/manage/services/yjs-utils';

describe('ProblemStatementSyncService', () => {
    let service: ProblemStatementSyncService;
    let syncService: jest.Mocked<ProgrammingExerciseEditorSyncService>;
    let incomingMessages$: Subject<ProgrammingExerciseEditorSyncEvent>;

    beforeEach(() => {
        incomingMessages$ = new Subject<ProgrammingExerciseEditorSyncEvent>();

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
                    provide: AccountService,
                    useValue: {
                        identity: jest.fn().mockResolvedValue(undefined),
                    },
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
                eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
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
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
                yjsUpdate: expect.any(String),
            }),
        );
    });

    it('applies incoming yjs updates to the doc', fakeAsync(() => {
        const state = service.init(99, '');

        const doc = new Y.Doc();
        doc.getText('problem-statement').insert(0, 'Hello Artemis');
        const update = encodeUint8ArrayToBase64(Y.encodeStateAsUpdate(doc));
        incomingMessages$.next({
            eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            yjsUpdate: update,
            timestamp: 1,
        });

        tick(500);
        expect(state.text.toString()).toBe('Hello Artemis');
    }));
});
