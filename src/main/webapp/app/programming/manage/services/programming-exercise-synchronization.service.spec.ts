import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';

import {
    ProgrammingExerciseSynchronizationMessage,
    ProgrammingExerciseSynchronizationService,
    ProgrammingExerciseSynchronizationTarget,
} from 'app/programming/manage/services/programming-exercise-synchronization.service';
import { WebsocketService } from 'app/shared/service/websocket.service';

describe('ProgrammingExerciseSynchronizationService', () => {
    let service: ProgrammingExerciseSynchronizationService;
    let websocketService: WebsocketService;
    let receiveSubjects: Subject<ProgrammingExerciseSynchronizationMessage>[];

    beforeEach(() => {
        receiveSubjects = [];
        TestBed.configureTestingModule({
            providers: [
                ProgrammingExerciseSynchronizationService,
                {
                    provide: WebsocketService,
                    useValue: {
                        subscribe: jest.fn(),
                        receive: jest.fn().mockImplementation(() => {
                            const subject = new Subject<ProgrammingExerciseSynchronizationMessage>();
                            receiveSubjects.push(subject);
                            return subject.asObservable();
                        }),
                        unsubscribe: jest.fn(),
                    },
                },
            ],
        });

        service = TestBed.inject(ProgrammingExerciseSynchronizationService);
        websocketService = TestBed.inject(WebsocketService);
    });

    it('subscribes to websocket topic and forwards updates', () => {
        const received: ProgrammingExerciseSynchronizationMessage[] = [];
        service.getSynchronizationUpdates(5).subscribe((message) => received.push(message));

        expect(websocketService.subscribe).toHaveBeenCalledWith('/topic/programming-exercises/5/synchronization');

        const synchronizationMessage = { target: ProgrammingExerciseSynchronizationTarget.TESTS_REPOSITORY };
        receiveSubjects[0].next(synchronizationMessage);

        expect(received).toContain(synchronizationMessage);
    });

    it('unsubscribes from all topics on destroy', () => {
        service.getSynchronizationUpdates(3);
        service.getSynchronizationUpdates(4);

        service.ngOnDestroy();

        expect(websocketService.unsubscribe).toHaveBeenCalledTimes(2);
    });

    it('unsubscribes from specific exercise', () => {
        service.getSynchronizationUpdates(5);

        service.unsubscribeFromExercise(5);

        expect(websocketService.unsubscribe).toHaveBeenCalledWith('/topic/programming-exercises/5/synchronization');
    });

    it('completes Subject when unsubscribing from exercise', () => {
        const received: ProgrammingExerciseSynchronizationMessage[] = [];
        let completed = false;

        service.getSynchronizationUpdates(5).subscribe({
            next: (message) => received.push(message),
            complete: () => (completed = true),
        });

        service.unsubscribeFromExercise(5);

        expect(completed).toBeTrue();
    });

    it('stops receiving messages after unsubscribing from exercise', () => {
        const received: ProgrammingExerciseSynchronizationMessage[] = [];
        service.getSynchronizationUpdates(5).subscribe((message) => received.push(message));

        const message1 = { target: ProgrammingExerciseSynchronizationTarget.TESTS_REPOSITORY };
        receiveSubjects[0].next(message1);
        expect(received).toHaveLength(1);

        service.unsubscribeFromExercise(5);

        // After unsubscribing, internal subscription should be cleaned up
        // so pushing more messages should not affect the subscriber
        const message2 = { target: ProgrammingExerciseSynchronizationTarget.TEMPLATE_REPOSITORY };
        receiveSubjects[0].next(message2);

        expect(received).toHaveLength(1); // Still only has the first message
    });

    it('cleans up multiple exercises independently', () => {
        service.getSynchronizationUpdates(3);
        service.getSynchronizationUpdates(4);
        service.getSynchronizationUpdates(5);

        service.unsubscribeFromExercise(4);

        expect(websocketService.unsubscribe).toHaveBeenCalledWith('/topic/programming-exercises/4/synchronization');
        expect(websocketService.unsubscribe).toHaveBeenCalledOnce();
    });

    it('does nothing when unsubscribing from non-existent exercise', () => {
        service.unsubscribeFromExercise(999);

        expect(websocketService.unsubscribe).not.toHaveBeenCalled();
    });
});
