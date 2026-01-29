import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject } from 'rxjs';

import {
    ProgrammingExerciseEditorSyncEvent,
    ProgrammingExerciseEditorSyncEventType,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';
import { AlertService } from 'app/shared/service/alert.service';

describe('ProgrammingExerciseEditorSyncService', () => {
    let service: ProgrammingExerciseEditorSyncService;
    let websocketService: jest.Mocked<WebsocketService>;
    let receiveSubject: Subject<ProgrammingExerciseEditorSyncEvent>;
    let alertService: jest.Mocked<AlertService>;

    beforeEach(() => {
        receiveSubject = new Subject<ProgrammingExerciseEditorSyncEvent>();
        window.sessionStorage.setItem('artemis.editor.sessionClientId', 'test-session-client-456');

        TestBed.configureTestingModule({
            providers: [
                ProgrammingExerciseEditorSyncService,
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
                        browserInstanceId: new BehaviorSubject<string | undefined>('test-client-instance-123'),
                    },
                },
                {
                    provide: AlertService,
                    useValue: {
                        info: jest.fn(),
                    },
                },
            ],
        });

        service = TestBed.inject(ProgrammingExerciseEditorSyncService);
        websocketService = TestBed.inject(WebsocketService) as jest.Mocked<WebsocketService>;
        alertService = TestBed.inject(AlertService) as jest.Mocked<AlertService>;
    });

    it('subscribes to websocket topic and forwards updates', () => {
        const received: ProgrammingExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncEvent) => received.push(message));

        expect(websocketService.subscribe).toHaveBeenCalledWith('/topic/programming-exercises/5/synchronization');

        const synchronizationMessage: ProgrammingExerciseEditorSyncEvent = {
            eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY,
            clientInstanceId: 'other-client',
            yjsUpdate: 'update',
        };
        receiveSubject.next(synchronizationMessage);

        expect(received).toContain(synchronizationMessage);
    });

    it('filters out messages from same client instance', () => {
        const received: ProgrammingExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncEvent) => received.push(message));

        const ownMessage: ProgrammingExerciseEditorSyncEvent = {
            eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY,
            clientInstanceId: 'test-session-client-456', // Same as our session
            yjsUpdate: 'update',
        };
        receiveSubject.next(ownMessage);

        expect(received).toHaveLength(0);
    });

    it('sends synchronization update with timestamp and client instance ID', () => {
        service.subscribeToUpdates(5);

        const message: ProgrammingExerciseEditorSyncEvent = {
            eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
            yjsUpdate: 'update',
        };

        service.sendSynchronizationUpdate(5, message);

        expect(websocketService.send).toHaveBeenCalledWith(
            '/topic/programming-exercises/5/synchronization',
            expect.objectContaining({
                eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                clientInstanceId: 'test-session-client-456',
                timestamp: expect.any(Number),
            }),
        );
    });

    it('throws error when sending without subscription', () => {
        const message: ProgrammingExerciseEditorSyncEvent = {
            eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
            yjsUpdate: 'update',
        };

        expect(() => service.sendSynchronizationUpdate(5, message)).toThrow('Cannot send synchronization message: not subscribed to websocket topic');
    });

    it('completes Subject when unsubscribing', () => {
        const received: ProgrammingExerciseEditorSyncEvent[] = [];
        let completed = false;

        service.subscribeToUpdates(5).subscribe({
            next: (message) => received.push(message),
            complete: () => (completed = true),
        });

        service.unsubscribe();

        expect(completed).toBeTrue();
    });

    it('stops receiving messages after unsubscribing', () => {
        const received: ProgrammingExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncEvent) => received.push(message));

        const message1: ProgrammingExerciseEditorSyncEvent = {
            eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY,
            clientInstanceId: 'other-client',
            yjsUpdate: 'update-1',
        };
        receiveSubject.next(message1);
        expect(received).toHaveLength(1);

        service.unsubscribe();

        const message2: ProgrammingExerciseEditorSyncEvent = {
            eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
            clientInstanceId: 'other-client',
            yjsUpdate: 'update-2',
        };
        receiveSubject.next(message2);

        expect(received).toHaveLength(1); // Still only has the first message
    });

    it('reuses same observable for multiple subscribers', () => {
        const received1: ProgrammingExerciseEditorSyncEvent[] = [];
        const received2: ProgrammingExerciseEditorSyncEvent[] = [];

        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncEvent) => received1.push(message));
        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncEvent) => received2.push(message));

        // Should only subscribe to websocket once
        expect(websocketService.subscribe).toHaveBeenCalledOnce();

        const message: ProgrammingExerciseEditorSyncEvent = {
            eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE,
            target: ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY,
            clientInstanceId: 'other-client',
            yjsUpdate: 'update',
        };
        receiveSubject.next(message);

        // Both subscribers should receive the message
        expect(received1).toEqual([message]);
        expect(received2).toEqual([message]);
    });

    it('shows new commit alert and does not forward the event', () => {
        const received: ProgrammingExerciseEditorSyncEvent[] = [];
        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncEvent) => received.push(message));

        receiveSubject.next({
            eventType: ProgrammingExerciseEditorSyncEventType.NEW_COMMIT_ALERT,
            target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
            clientInstanceId: 'other-client',
        });

        expect(alertService.info).toHaveBeenCalledWith('artemisApp.editor.synchronization.newCommitAlert');
        expect(received).toHaveLength(0);
    });
});
