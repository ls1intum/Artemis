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
});
