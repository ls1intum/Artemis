import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import {
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

describe('ProgrammingExerciseEditorSyncService', () => {
    let service: ProgrammingExerciseEditorSyncService;
    let websocketService: jest.Mocked<WebsocketService>;
    let receiveSubject: Subject<ProgrammingExerciseEditorSyncMessage>;

    beforeEach(() => {
        receiveSubject = new Subject<ProgrammingExerciseEditorSyncMessage>();

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
                        instanceIdentifier: { value: 'test-client-instance-123' },
                    },
                },
            ],
        });

        service = TestBed.inject(ProgrammingExerciseEditorSyncService);
        websocketService = TestBed.inject(WebsocketService) as jest.Mocked<WebsocketService>;
    });

    it('subscribes to websocket topic and forwards updates', () => {
        const received: ProgrammingExerciseEditorSyncMessage[] = [];
        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncMessage) => received.push(message));

        expect(websocketService.subscribe).toHaveBeenCalledWith('/topic/programming-exercises/5/synchronization');

        const synchronizationMessage: ProgrammingExerciseEditorSyncMessage = {
            target: ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY,
            clientInstanceId: 'other-client',
        };
        receiveSubject.next(synchronizationMessage);

        expect(received).toContain(synchronizationMessage);
    });

    it('filters out messages from same client instance', () => {
        const received: ProgrammingExerciseEditorSyncMessage[] = [];
        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncMessage) => received.push(message));

        const ownMessage: ProgrammingExerciseEditorSyncMessage = {
            target: ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY,
            clientInstanceId: 'test-client-instance-123', // Same as our instance
        };
        receiveSubject.next(ownMessage);

        expect(received).toHaveLength(0);
    });

    it('sends synchronization update with timestamp and client instance ID', () => {
        service.subscribeToUpdates(5);

        const message: ProgrammingExerciseEditorSyncMessage = {
            target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
        };

        service.sendSynchronizationUpdate(5, message);

        expect(websocketService.send).toHaveBeenCalledWith(
            '/topic/programming-exercises/5/synchronization',
            expect.objectContaining({
                target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
                clientInstanceId: 'test-client-instance-123',
                timestamp: expect.any(Number),
            }),
        );
    });

    it('throws error when sending without subscription', () => {
        const message: ProgrammingExerciseEditorSyncMessage = {
            target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
        };

        expect(() => service.sendSynchronizationUpdate(5, message)).toThrow('Cannot send synchronization message: not subscribed to websocket topic');
    });

    it('completes Subject when unsubscribing', () => {
        const received: ProgrammingExerciseEditorSyncMessage[] = [];
        let completed = false;

        service.subscribeToUpdates(5).subscribe({
            next: (message) => received.push(message),
            complete: () => (completed = true),
        });

        service.unsubscribe();

        expect(completed).toBeTrue();
    });

    it('stops receiving messages after unsubscribing', () => {
        const received: ProgrammingExerciseEditorSyncMessage[] = [];
        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncMessage) => received.push(message));

        const message1: ProgrammingExerciseEditorSyncMessage = {
            target: ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY,
            clientInstanceId: 'other-client',
        };
        receiveSubject.next(message1);
        expect(received).toHaveLength(1);

        service.unsubscribe();

        const message2: ProgrammingExerciseEditorSyncMessage = {
            target: ProgrammingExerciseEditorSyncTarget.TEMPLATE_REPOSITORY,
            clientInstanceId: 'other-client',
        };
        receiveSubject.next(message2);

        expect(received).toHaveLength(1); // Still only has the first message
    });

    it('reuses same observable for multiple subscribers', () => {
        const received1: ProgrammingExerciseEditorSyncMessage[] = [];
        const received2: ProgrammingExerciseEditorSyncMessage[] = [];

        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncMessage) => received1.push(message));
        service.subscribeToUpdates(5).subscribe((message: ProgrammingExerciseEditorSyncMessage) => received2.push(message));

        // Should only subscribe to websocket once
        expect(websocketService.subscribe).toHaveBeenCalledOnce();

        const message: ProgrammingExerciseEditorSyncMessage = {
            target: ProgrammingExerciseEditorSyncTarget.TESTS_REPOSITORY,
            clientInstanceId: 'other-client',
        };
        receiveSubject.next(message);

        // Both subscribers should receive the message
        expect(received1).toEqual([message]);
        expect(received2).toEqual([message]);
    });
});
