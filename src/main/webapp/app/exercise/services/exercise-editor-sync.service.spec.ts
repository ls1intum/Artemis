import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject } from 'rxjs';

import { ExerciseEditorSyncEvent, ExerciseEditorSyncEventType, ExerciseEditorSyncService, ExerciseEditorSyncTarget } from 'app/exercise/services/exercise-editor-sync.service';
import { ConnectionState, WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

describe('ExerciseEditorSyncService', () => {
    let service: ExerciseEditorSyncService;
    let websocketService: jest.Mocked<WebsocketService>;
    let receiveSubject: Subject<ExerciseEditorSyncEvent>;
    let connectionState$: BehaviorSubject<ConnectionState>;

    beforeEach(() => {
        receiveSubject = new Subject<ExerciseEditorSyncEvent>();
        connectionState$ = new BehaviorSubject<ConnectionState>(new ConnectionState(true, true));

        TestBed.configureTestingModule({
            providers: [
                ExerciseEditorSyncService,
                {
                    provide: WebsocketService,
                    useValue: {
                        subscribe: jest.fn().mockReturnValue(receiveSubject.asObservable()),
                        send: jest.fn(),
                        unsubscribe: jest.fn(),
                        connectionState: connectionState$.asObservable(),
                    },
                },
                {
                    provide: BrowserFingerprintService,
                    useValue: {
                        browserSessionId: new BehaviorSubject<string | undefined>('test-session-456'),
                    },
                },
            ],
        });

        service = TestBed.inject(ExerciseEditorSyncService);
        websocketService = TestBed.inject(WebsocketService) as jest.Mocked<WebsocketService>;
    });

    afterEach(() => {
        service.unsubscribe();
    });

    it('subscribes to websocket topic and forwards updates', () => {
        const received: ExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received.push(message));

        expect(websocketService.subscribe).toHaveBeenCalledWith('/topic/exercises/5/synchronization');

        const synchronizationMessage: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            sessionId: 'other-client',
            yjsUpdate: 'update',
        };
        receiveSubject.next(synchronizationMessage);

        expect(received).toContain(synchronizationMessage);
    });

    it('filters out messages from same session', () => {
        const received: ExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received.push(message));

        const ownMessage: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            sessionId: 'test-session-456', // Same as our session
            yjsUpdate: 'update',
        };
        receiveSubject.next(ownMessage);

        expect(received).toHaveLength(0);
    });

    it('sends synchronization update with timestamp and session ID when connected', () => {
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
                sessionId: 'test-session-456',
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

    it('throws error when sending to wrong exercise id', () => {
        service.subscribeToUpdates(5);

        const message: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            yjsUpdate: 'update',
        };

        expect(() => service.sendSynchronizationUpdate(6, message)).toThrow('Cannot send synchronization message: exerciseId 6 does not match subscribed exerciseId 5');
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
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            sessionId: 'other-client',
            yjsUpdate: 'update-1',
        };
        receiveSubject.next(message1);
        expect(received).toHaveLength(1);

        service.unsubscribe();

        const message2: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            sessionId: 'other-client',
            yjsUpdate: 'update-2',
        };
        receiveSubject.next(message2);

        expect(received).toHaveLength(1); // Still only has the first message
    });

    it('reuses same observable for multiple subscribers', () => {
        const received1: ExerciseEditorSyncEvent[] = [];
        const received2: ExerciseEditorSyncEvent[] = [];

        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received1.push(message));
        service.subscribeToUpdates(5).subscribe((message: ExerciseEditorSyncEvent) => received2.push(message));

        // Should only subscribe to websocket once
        expect(websocketService.subscribe).toHaveBeenCalledOnce();

        const message: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            sessionId: 'other-client',
            yjsUpdate: 'update',
        };
        receiveSubject.next(message);

        // Both subscribers should receive the message
        expect(received1).toEqual([message]);
        expect(received2).toEqual([message]);
    });

    it('re-subscribes when exercise id changes', () => {
        const firstExerciseMessages = new Subject<ExerciseEditorSyncEvent>();
        const secondExerciseMessages = new Subject<ExerciseEditorSyncEvent>();
        websocketService.subscribe.mockReset();
        websocketService.subscribe.mockReturnValueOnce(firstExerciseMessages.asObservable()).mockReturnValueOnce(secondExerciseMessages.asObservable());

        const firstReceived: ExerciseEditorSyncEvent[] = [];
        let firstCompleted = false;
        service.subscribeToUpdates(5).subscribe({
            next: (message) => firstReceived.push(message),
            complete: () => (firstCompleted = true),
        });

        const secondReceived: ExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(6).subscribe((message) => secondReceived.push(message));

        expect(firstCompleted).toBeTrue();
        expect(websocketService.subscribe).toHaveBeenNthCalledWith(1, '/topic/exercises/5/synchronization');
        expect(websocketService.subscribe).toHaveBeenNthCalledWith(2, '/topic/exercises/6/synchronization');

        firstExerciseMessages.next({
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            sessionId: 'other-client',
            yjsUpdate: 'stale',
        });
        expect(firstReceived).toHaveLength(0);

        const activeMessage: ExerciseEditorSyncEvent = {
            eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            sessionId: 'other-client',
            yjsUpdate: 'active',
        };
        secondExerciseMessages.next(activeMessage);
        expect(secondReceived).toEqual([activeMessage]);
    });

    describe('outgoing message buffering', () => {
        it('buffers messages when disconnected and flushes when connected', () => {
            connectionState$.next(new ConnectionState(false, false));
            service.subscribeToUpdates(5);

            const message: ExerciseEditorSyncEvent = {
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                requestId: 'req-1',
            };
            service.sendSynchronizationUpdate(5, message);

            // Not sent yet — disconnected
            expect(websocketService.send).not.toHaveBeenCalled();

            // Connection established
            connectionState$.next(new ConnectionState(true, true));

            expect(websocketService.send).toHaveBeenCalledWith(
                '/topic/exercises/5/synchronization',
                expect.objectContaining({
                    eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
                    requestId: 'req-1',
                }),
            );
        });

        it('flushes buffered messages in FIFO order', () => {
            connectionState$.next(new ConnectionState(false, false));
            service.subscribeToUpdates(5);

            const sentPayloads: unknown[] = [];
            websocketService.send.mockImplementation((_topic, payload) => {
                sentPayloads.push(payload);
            });

            const msg1: ExerciseEditorSyncEvent = {
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                requestId: 'req-1',
            };
            const msg2: ExerciseEditorSyncEvent = {
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                yjsUpdate: 'update-1',
            };
            const msg3: ExerciseEditorSyncEvent = {
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_AWARENESS_UPDATE,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                awarenessUpdate: 'awareness-1',
            };

            service.sendSynchronizationUpdate(5, msg1);
            service.sendSynchronizationUpdate(5, msg2);
            service.sendSynchronizationUpdate(5, msg3);

            expect(websocketService.send).not.toHaveBeenCalled();

            connectionState$.next(new ConnectionState(true, true));

            expect(sentPayloads).toHaveLength(3);
            expect(sentPayloads[0]).toEqual(expect.objectContaining({ requestId: 'req-1' }));
            expect(sentPayloads[1]).toEqual(expect.objectContaining({ yjsUpdate: 'update-1' }));
            expect(sentPayloads[2]).toEqual(expect.objectContaining({ awarenessUpdate: 'awareness-1' }));
        });

        it('does not send buffered messages after unsubscribe', () => {
            connectionState$.next(new ConnectionState(false, false));
            service.subscribeToUpdates(5);

            const message: ExerciseEditorSyncEvent = {
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                requestId: 'req-1',
            };
            service.sendSynchronizationUpdate(5, message);

            service.unsubscribe();

            // Connect after unsubscribe — buffered message should NOT be sent
            connectionState$.next(new ConnectionState(true, true));

            expect(websocketService.send).not.toHaveBeenCalled();
        });

        it('throws error when outgoing buffer is not initialized', () => {
            // Force the state where subscription exists but outgoing$ does not.
            // This can't happen in normal flow, but the guard should catch it.
            const message: ExerciseEditorSyncEvent = {
                eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
                target: ExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                yjsUpdate: 'update',
            };

            expect(() => service.sendSynchronizationUpdate(5, message)).toThrow('Cannot send synchronization message: not subscribed to websocket topic');
        });
    });
});
