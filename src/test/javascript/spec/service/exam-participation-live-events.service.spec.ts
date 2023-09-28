import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Subject, firstValueFrom } from 'rxjs';
import { ConnectionState, JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/participate/exam-participation-live-events.service';
import { LocalStorageService } from 'ngx-webstorage';
import dayjs from 'dayjs/esm';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';

describe('ExamParticipationLiveEventsService', () => {
    let service: ExamParticipationLiveEventsService;
    let httpMock: HttpTestingController;
    let mockWebsocketService: JhiWebsocketService;
    let mockExamParticipationService: ExamParticipationService;
    let mockLocalStorageService: LocalStorageService;
    let websocketConnectionStateSubject: Subject<ConnectionState>;
    let currentlyLoadedStudentExamSubject: Subject<any>;

    beforeEach(() => {
        currentlyLoadedStudentExamSubject = new Subject<any>();
        websocketConnectionStateSubject = new Subject<ConnectionState>();
        mockExamParticipationService = {
            currentlyLoadedStudentExam: currentlyLoadedStudentExamSubject,
        } as unknown as ExamParticipationService;

        mockLocalStorageService = {
            store: jest.fn(),
            retrieve: jest.fn(),
        } as unknown as LocalStorageService;

        mockWebsocketService = new MockWebsocketService() as any as JhiWebsocketService;
        mockWebsocketService['state'] = websocketConnectionStateSubject.asObservable();

        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: JhiWebsocketService, useValue: mockWebsocketService },
                { provide: ExamParticipationService, useValue: mockExamParticipationService },
                { provide: LocalStorageService, useValue: mockLocalStorageService },
            ],
        });

        service = TestBed.inject(ExamParticipationLiveEventsService);
        httpMock = TestBed.inject(HttpTestingController);
        mockWebsocketService = TestBed.inject(JhiWebsocketService);

        service['studentExamId'] = 1;
        service['examId'] = 1;
        service['courseId'] = 1;
    });

    afterEach(() => {
        httpMock.verify();
        jest.resetAllMocks();
    });

    it.each([
        [true, true],
        [true, false],
        [false, true],
        [false, false],
    ])(
        'should correctly react to websocket connection state and refetch events for connected=%s, wasEverConnectedBefore=%s',
        fakeAsync((connected: boolean, wasEverConnectedBefore: boolean) => {
            // @ts-ignore
            const fetchPreviousExamEventsSpy = jest.spyOn(service, 'fetchPreviousExamEvents');

            tick(6000);
            expect(fetchPreviousExamEventsSpy).not.toHaveBeenCalled();

            websocketConnectionStateSubject.next({ connected, wasEverConnectedBefore } as any as ConnectionState);

            const mockEvents: ExamLiveEvent[] = [
                {
                    id: 1,
                    createdBy: 'user',
                    createdDate: dayjs(),
                    eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
                },
            ];

            tick(6000);

            if (connected && wasEverConnectedBefore) {
                expect(fetchPreviousExamEventsSpy).toHaveBeenCalledOnce();
                const req = httpMock.expectOne({ method: 'GET', url: `/api/courses/1/exams/1/student-exams/live-events` });
                req.flush(mockEvents);
                expect(service['events']).toEqual(mockEvents);
                expect(service['allEventsSubject'].getValue()).toEqual(mockEvents);
            } else {
                expect(fetchPreviousExamEventsSpy).not.toHaveBeenCalled();
            }
        }),
    );

    it('should correctly react to a student exam change and refetch events and subscribe to ws', fakeAsync(async () => {
        // @ts-ignore
        const unsubscribeFromExamLiveEventsSpy = jest.spyOn(service, 'unsubscribeFromExamLiveEvents');
        const websocketSubscribeSpy = jest.spyOn(mockWebsocketService, 'subscribe');
        const websocketUnsubscribeSpy = jest.spyOn(mockWebsocketService, 'unsubscribe');

        // @ts-ignore
        const fetchPreviousExamEventsSpy = jest.spyOn(service, 'fetchPreviousExamEvents').mockReturnValue(undefined);

        // @ts-ignore
        const replayEventsSpy = jest.spyOn(service, 'replayEvents');

        const websocketStreamSubject = new Subject<any>();
        const websocketReceiveSpy = jest.spyOn(mockWebsocketService, 'receive').mockReturnValue(websocketStreamSubject.asObservable());

        const retrieveSpy = jest.spyOn(mockLocalStorageService, 'retrieve').mockReturnValue(
            JSON.stringify({
                2: {
                    lastChange: 1,
                    acknowledgedEvents: {
                        1: {
                            user: 1,
                            system: 1,
                        },
                    },
                },
            }),
        );

        service['currentWebsocketReceiveSubscriptions'] = [{ unsubscribe: jest.fn() } as any, { unsubscribe: jest.fn() } as any];
        service['currentWebsocketChannels'] = ['123', '456'];

        currentlyLoadedStudentExamSubject.next({ id: 2, exam: { id: 2, course: { id: 2 } } });

        // Check resets
        expect(service['events']).toEqual([]);
        expect(unsubscribeFromExamLiveEventsSpy).toHaveBeenCalledOnce();
        expect(websocketUnsubscribeSpy).toHaveBeenCalledTimes(2);
        expect(service['currentWebsocketReceiveSubscriptions']).toBeUndefined();
        expect(service['currentWebsocketChannels']).toBeUndefined();

        // Check Ids are set
        expect(service['studentExamId']).toBe(2);
        expect(service['examId']).toBe(2);
        expect(service['courseId']).toBe(2);

        // Check load ack status
        expect(retrieveSpy).toHaveBeenCalledTimes(2); // 1x in constructor, 1x in currentlyLoadedStudentExamSubject.next
        expect(service['lastAcknowledgedEventStatus']).toEqual({
            lastChange: 1,
            acknowledgedEvents: {
                1: {
                    user: 1,
                    system: 1,
                },
            },
        });

        // Wait for delay
        tick(6000);

        // Check fetch events called
        expect(fetchPreviousExamEventsSpy).toHaveBeenCalledOnce();

        // Check subscribe called
        expect(websocketSubscribeSpy).toHaveBeenCalledTimes(2);
        expect(websocketReceiveSpy).toHaveBeenCalledTimes(2);

        // Send event over ws
        const mockEvent: any = {
            id: 123,
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
            text: 'Test',
            createdDate: '2021-08-02T12:00:00.000Z',
        };

        websocketStreamSubject.next(mockEvent);

        // Check event is added
        expect(service['events']).toEqual([mockEvent]);

        // Expect emission
        expect(service['allEventsSubject'].getValue()).toEqual([mockEvent]);
        const userEvents = firstValueFrom(service.observeNewEventsAsUser());
        const systemEvents = firstValueFrom(service.observeNewEventsAsSystem());

        tick();
        await expect(userEvents).resolves.toEqual(mockEvent);
        await expect(systemEvents).resolves.toEqual(mockEvent);

        expect(replayEventsSpy).toHaveBeenCalledTimes(2);
    }));

    it('acknowledgeEvent should set the correct timestamp and store it', () => {
        // Mock initial status
        service['lastAcknowledgedEventStatus'] = {
            lastChange: 1,
            acknowledgedEvents: {},
        };

        const nowUnix = dayjs().unix();
        const mockEvent: ExamLiveEvent = {
            id: 1,
            createdBy: 'user',
            createdDate: dayjs(),
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
        };

        service.acknowledgeEvent(mockEvent, true);
        expect(service['lastAcknowledgedEventStatus']!.acknowledgedEvents['1'].user).toBeGreaterThanOrEqual(nowUnix);
        expect(mockLocalStorageService.store).toHaveBeenCalledWith(expect.any(String), expect.any(String));
    });
});
