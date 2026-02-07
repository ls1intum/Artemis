import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject } from 'rxjs';

import { ExerciseEditorSyncEvent, ExerciseEditorSyncEventType, ExerciseEditorSyncService, ExerciseEditorSyncTarget } from 'app/exercise/services/exercise-editor-sync.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

describe('ExerciseEditorSyncService', () => {
    let service: ExerciseEditorSyncService;
    let websocketService: jest.Mocked<WebsocketService>;
    let receiveSubject: Subject<ExerciseEditorSyncEvent>;
    let browserSessionId: BehaviorSubject<string | undefined>;

    beforeEach(() => {
        receiveSubject = new Subject<ExerciseEditorSyncEvent>();
        browserSessionId = new BehaviorSubject<string | undefined>('test-session-123');

        TestBed.configureTestingModule({
            providers: [
                ExerciseEditorSyncService,
                {
                    provide: WebsocketService,
                    useValue: {
                        subscribe: jest.fn().mockReturnValue(receiveSubject.asObservable()),
                        send: jest.fn(),
                        unsubscribe: jest.fn(),
                    },
                },
                {
                    provide: BrowserFingerprintService,
                    useValue: {
                        browserSessionId,
                    },
                },
            ],
        });

        service = TestBed.inject(ExerciseEditorSyncService);
        websocketService = TestBed.inject(WebsocketService) as jest.Mocked<WebsocketService>;
    });

    it('subscribes to websocket topic and forwards NEW_COMMIT_ALERT updates', () => {
        const received: ExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received.push(message));

        expect(websocketService.subscribe).toHaveBeenCalledWith('/topic/exercises/5/synchronization');

        const synchronizationMessage: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: ExerciseEditorSyncTarget.TESTS_REPOSITORY,
            sessionId: 'other-session',
        };
        receiveSubject.next(synchronizationMessage);

        expect(received).toContain(synchronizationMessage);
    });

    it('forwards NEW_EXERCISE_VERSION_ALERT updates', () => {
        const received: ExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received.push(message));

        const metadataAlert: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT,
            target: ExerciseEditorSyncTarget.EXERCISE_METADATA,
            exerciseVersionId: 7,
            author: { login: 'editor' },
            changedFields: ['title'],
            sessionId: 'other-session',
        };
        receiveSubject.next(metadataAlert);

        expect(received).toEqual([metadataAlert]);
    });

    it('forwards problem-statement synchronization events', () => {
        const received: ExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received.push(message));

        const requestEvent: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            requestId: 'req-1',
            sessionId: 'other-session',
        };
        const responseEvent: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            responseTo: 'req-1',
            yjsUpdate: 'full',
            leaderTimestamp: 123,
            sessionId: 'other-session',
        };
        const updateEvent: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            yjsUpdate: 'delta',
            sessionId: 'other-session',
        };
        const awarenessEvent: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_AWARENESS_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            awarenessUpdate: 'awareness',
            sessionId: 'other-session',
        };

        receiveSubject.next(requestEvent);
        receiveSubject.next(responseEvent);
        receiveSubject.next(updateEvent);
        receiveSubject.next(awarenessEvent);

        expect(received).toEqual([requestEvent, responseEvent, updateEvent, awarenessEvent]);
    });

    it('filters out messages from the same session', () => {
        const received: ExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received.push(message));

        const ownMessage: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: ExerciseEditorSyncTarget.TESTS_REPOSITORY,
            sessionId: 'test-session-123',
        };
        receiveSubject.next(ownMessage);

        expect(received).toHaveLength(0);
    });

    it('does not filter messages without session ids', () => {
        const received: ExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received.push(message));

        const messageWithoutSession: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: ExerciseEditorSyncTarget.TESTS_REPOSITORY,
        };
        receiveSubject.next(messageWithoutSession);

        expect(received).toEqual([messageWithoutSession]);
    });

    it('does not filter messages when local session id is undefined', () => {
        browserSessionId.next(undefined);
        const received: ExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received.push(message));

        const sameSessionMessage: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: ExerciseEditorSyncTarget.TESTS_REPOSITORY,
            sessionId: 'test-session-123',
        };
        receiveSubject.next(sameSessionMessage);

        expect(received).toEqual([sameSessionMessage]);
    });

    it('sends synchronization update with timestamp and session ID', () => {
        service.subscribeToUpdates(5);

        const message: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            yjsUpdate: 'update',
        };

        service.sendSynchronizationUpdate(5, message);

        expect(websocketService.send).toHaveBeenCalledWith(
            '/topic/exercises/5/synchronization',
            expect.objectContaining({
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                sessionId: 'test-session-123',
                timestamp: expect.any(Number),
            }),
        );
    });

    it('preserves provided timestamp when sending synchronization update', () => {
        service.subscribeToUpdates(5);

        const message: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT,
            target: ExerciseEditorSyncTarget.EXERCISE_METADATA,
            exerciseVersionId: 4,
            author: { login: 'editor' },
            timestamp: 1337,
        };
        service.sendSynchronizationUpdate(5, message);

        expect(websocketService.send).toHaveBeenCalledWith(
            '/topic/exercises/5/synchronization',
            expect.objectContaining({
                eventType: ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT,
                target: ExerciseEditorSyncTarget.EXERCISE_METADATA,
                exerciseVersionId: 4,
                timestamp: 1337,
                sessionId: 'test-session-123',
            }),
        );
    });

    it('throws error when sending without subscription', () => {
        const message: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            yjsUpdate: 'update',
        };

        expect(() => service.sendSynchronizationUpdate(5, message)).toThrow('Cannot send synchronization message: not subscribed to websocket topic');
    });

    it('completes Subject when unsubscribing', () => {
        const received: ExerciseEditorSyncEvent[] = [];
        let completed = false;

        service.subscribeToUpdates(5).subscribe({
            next: (message) => received.push(message),
            complete: () => (completed = true),
        });

        service.unsubscribe();

        expect(completed).toBeTrue();
    });

    it('stops receiving messages after unsubscribing', () => {
        const received: ExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received.push(message));

        const message1: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: ExerciseEditorSyncTarget.TESTS_REPOSITORY,
            sessionId: 'other-session',
        };
        receiveSubject.next(message1);
        expect(received).toHaveLength(1);

        service.unsubscribe();

        const message2: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: ExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
            sessionId: 'other-session',
        };
        receiveSubject.next(message2);

        expect(received).toHaveLength(1);
    });

    it('reuses same observable for multiple subscribers', () => {
        const received1: ExerciseEditorSyncEvent[] = [];
        const received2: ExerciseEditorSyncEvent[] = [];

        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received1.push(message));
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received2.push(message));

        expect(websocketService.subscribe).toHaveBeenCalledOnce();

        const message: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: ExerciseEditorSyncTarget.TESTS_REPOSITORY,
            sessionId: 'other-session',
        };
        receiveSubject.next(message);

        expect(received1).toEqual([message]);
        expect(received2).toEqual([message]);
    });

    it('re-subscribes when subscribing to a different exercise id', () => {
        let firstCompleted = false;
        service.subscribeToUpdates(5).subscribe({ complete: () => (firstCompleted = true) });
        service.subscribeToUpdates(6).subscribe();

        expect(firstCompleted).toBeTrue();
        expect(websocketService.subscribe).toHaveBeenNthCalledWith(1, '/topic/exercises/5/synchronization');
        expect(websocketService.subscribe).toHaveBeenNthCalledWith(2, '/topic/exercises/6/synchronization');
    });
});
