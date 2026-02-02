import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject } from 'rxjs';

import { ExerciseEditorSyncEvent, ExerciseEditorSyncEventType, ExerciseEditorSyncService, ExerciseEditorSyncTarget } from 'app/exercise/services/exercise-editor-sync.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

describe('ExerciseEditorSyncService', () => {
    let service: ExerciseEditorSyncService;
    let websocketService: jest.Mocked<WebsocketService>;
    let receiveSubject: Subject<ExerciseEditorSyncEvent>;

    beforeEach(() => {
        receiveSubject = new Subject<ExerciseEditorSyncEvent>();

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
                        browserSessionId: new BehaviorSubject<string | undefined>('test-session-123'),
                    },
                },
            ],
        });

        service = TestBed.inject(ExerciseEditorSyncService);
        websocketService = TestBed.inject(WebsocketService) as jest.Mocked<WebsocketService>;
    });

    it('subscribes to websocket topic and forwards updates', () => {
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
});
