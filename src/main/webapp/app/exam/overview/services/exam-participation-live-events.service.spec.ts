import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { Observable, Subject, firstValueFrom } from 'rxjs';
import { ConnectionState, WebsocketService } from 'app/shared/service/websocket.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { ExamLiveEvent, ExamLiveEventType, ExamParticipationLiveEventsService } from 'app/exam/overview/services/exam-participation-live-events.service';
import dayjs from 'dayjs/esm';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { provideHttpClient } from '@angular/common/http';

describe('ExamParticipationLiveEventsService', () => {
    let service: ExamParticipationLiveEventsService;
    let httpMock: HttpTestingController;
    let mockWebsocketService: MockWebsocketService;
    let mockExamParticipationService: ExamParticipationService;
    let mockLocalStorageService: LocalStorageService;
    let currentlyLoadedStudentExamSubject: Subject<any>;

    beforeEach(() => {
        currentlyLoadedStudentExamSubject = new Subject<any>();
        mockExamParticipationService = {
            currentlyLoadedStudentExam: currentlyLoadedStudentExamSubject,
        } as unknown as ExamParticipationService;

        mockLocalStorageService = {
            store: jest.fn(),
            retrieve: jest.fn(),
        } as unknown as LocalStorageService;

        mockWebsocketService = new MockWebsocketService();

        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: WebsocketService, useValue: mockWebsocketService },
                { provide: ExamParticipationService, useValue: mockExamParticipationService },
                { provide: LocalStorageService, useValue: mockLocalStorageService },
            ],
        });

        service = TestBed.inject(ExamParticipationLiveEventsService);
        httpMock = TestBed.inject(HttpTestingController);
        // @ts-ignore
        mockWebsocketService = TestBed.inject(WebsocketService) as MockWebsocketService;

        service['studentExamId'] = 1;
        service['examId'] = 1;
        service['courseId'] = 1;
    });

    afterEach(() => {
        httpMock.verify();
        jest.resetAllMocks();
    });

    // Tests the reconnection handler. The service should only fetch events when the WebSocket
    // reconnects (connected=true AND wasEverConnectedBefore=true). Initial connections and
    // disconnections should not trigger a fetch. The fetch happens immediately (no delay)
    // because on reconnection we want to backfill missed events as quickly as possible.
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

            // Simulate the connection state change (e.g., network drop then recovery)
            mockWebsocketService.setConnectionState(new ConnectionState(connected, wasEverConnectedBefore));

            const mockEvents: ExamLiveEvent[] = [
                {
                    id: 1,
                    createdDate: dayjs(),
                    eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
                },
            ];

            // The reconnection fetch happens synchronously (no setTimeout delay)
            tick(0);

            if (connected && wasEverConnectedBefore) {
                // Only on a genuine reconnection should we fetch events to backfill the gap
                expect(fetchPreviousExamEventsSpy).toHaveBeenCalledOnce();
                const req = httpMock.expectOne({ method: 'GET', url: `/api/exam/courses/1/exams/1/student-exams/live-events` });
                req.flush(mockEvents);
                expect(service['events']).toEqual(mockEvents);
                expect(service['allEventsSubject'].getValue()).toEqual(mockEvents);
            } else {
                // Initial connection, disconnection, or disconnected+wasEverConnected — no fetch
                expect(fetchPreviousExamEventsSpy).not.toHaveBeenCalled();
            }
        }),
    );

    // Verifies the two-phase initialization when a new student exam loads:
    //   Phase 1 (immediate): WebSocket subscription is set up so no real-time events are missed
    //   Phase 2 (after 2s): REST fetch backfills any events created before the subscription
    // Previously both phases were delayed by 5 seconds, creating a window for event loss.
    it('should subscribe to websocket immediately and fetch events after delay on student exam change', fakeAsync(async () => {
        // @ts-ignore
        const unsubscribeFromExamLiveEventsSpy = jest.spyOn(service, 'unsubscribeFromExamLiveEvents');
        const unsubscribeSpy = jest.fn();
        const websocketStreamSubject = new Subject<any>();
        const websocketSubscribeSpy = jest.spyOn(mockWebsocketService, 'subscribe').mockReturnValue(
            new Observable((observer) => {
                const innerSub = websocketStreamSubject.subscribe(observer);
                return () => {
                    unsubscribeSpy();
                    innerSub.unsubscribe();
                };
            }),
        );

        // @ts-ignore: accessing private method for testing
        const fetchPreviousExamEventsSpy = jest.spyOn(service, 'fetchPreviousExamEvents').mockReturnValue(undefined);

        // @ts-ignore: accessing private method for testing
        const replayEventsSpy = jest.spyOn(service, 'replayEvents');

        // Mock localStorage to return acknowledgement data for student exam 2
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

        // Set up existing subscriptions that should be cleaned up when a new student exam loads
        service['currentWebsocketReceiveSubscriptions'] = [{ unsubscribe: jest.fn() } as any, { unsubscribe: jest.fn() } as any];
        service['currentWebsocketChannels'] = ['123', '456'];

        // Emit a new student exam — this triggers the full initialization flow
        currentlyLoadedStudentExamSubject.next({ id: 2, exam: { id: 2, course: { id: 2 } } });

        // Verify state was reset: events array cleared and old subscriptions torn down
        expect(service['events']).toEqual([]);
        expect(unsubscribeFromExamLiveEventsSpy).toHaveBeenCalledOnce();

        // Verify the service captured the new student exam's identifiers
        expect(service['studentExamId']).toBe(2);
        expect(service['examId']).toBe(2);
        expect(service['courseId']).toBe(2);

        // Verify acknowledgement status was loaded from localStorage
        // (called 2x total: once in constructor for clearOldAcknowledgement, once for the new student exam)
        expect(retrieveSpy).toHaveBeenCalledTimes(2);
        expect(service['lastAcknowledgedEventStatus']).toEqual({
            lastChange: 1,
            acknowledgedEvents: {
                1: {
                    user: 1,
                    system: 1,
                },
            },
        });

        // CRITICAL ASSERTION: WebSocket subscription should happen IMMEDIATELY (not after a delay)
        // This is the fix for issues #12423/#12428/#12435 — previously this was delayed by 5s
        expect(websocketSubscribeSpy).toHaveBeenCalledTimes(2); // one per topic (student-specific + exam-wide)
        expect(service['currentWebsocketReceiveSubscriptions']).toHaveLength(2);
        expect(service['currentWebsocketChannels']).toHaveLength(2);

        // The historical REST fetch should NOT have fired yet — it uses a 2s delay
        // to spread server load when many students start the exam simultaneously
        expect(fetchPreviousExamEventsSpy).not.toHaveBeenCalled();

        // After 2 seconds, the historical fetch fires to backfill events
        tick(2000);
        expect(fetchPreviousExamEventsSpy).toHaveBeenCalledOnce();

        // Simulate a WebSocket event arriving after subscription is active
        const mockEvent: any = {
            id: 123,
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
            text: 'Test',
            createdDate: '2021-08-02T12:00:00.000Z',
        };

        websocketStreamSubject.next(mockEvent);

        // The event should be in the in-memory store
        expect(service['events']).toEqual([mockEvent]);

        // Verify the event is delivered through all three observer channels
        const userEvents = firstValueFrom(service.observeNewEventsAsUser([], dayjs()));
        const systemEvents = firstValueFrom(service.observeNewEventsAsSystem());
        const allEvents = firstValueFrom(service.observeAllEvents());

        tick(); // flush the microtask-deferred replayEvents() in observeNewEventsAs*
        await expect(userEvents).resolves.toEqual(mockEvent);
        await expect(systemEvents).resolves.toEqual(mockEvent);
        await expect(allEvents).resolves.toEqual([mockEvent]);

        // replayEvents was called 2x: once each by observeNewEventsAsUser and observeNewEventsAsSystem
        expect(replayEventsSpy).toHaveBeenCalledTimes(2);
    }));

    // Regression test: previously fetchPreviousExamEvents() overwrote the entire events array
    // with the HTTP response (this.events = fetchedEvents), which silently discarded any events
    // that had already arrived via WebSocket. This caused intermittent event loss: if an instructor
    // sent an announcement and it arrived via WebSocket before the REST fetch completed, the fetch
    // response would erase it. This test verifies that events from both sources are merged.
    it('should merge fetched events with existing websocket events instead of overwriting', fakeAsync(() => {
        const websocketStreamSubject = new Subject<any>();
        jest.spyOn(mockWebsocketService, 'subscribe').mockReturnValue(websocketStreamSubject.asObservable());

        // Loading a new student exam triggers: immediate WS subscription + 2s delayed REST fetch
        currentlyLoadedStudentExamSubject.next({ id: 3, exam: { id: 3, course: { id: 3 } } });

        // A WebSocket event arrives immediately (before the 2s REST fetch fires).
        // This simulates an instructor sending a working time update right as the student loads.
        const wsEvent: any = {
            id: 42,
            eventType: ExamLiveEventType.WORKING_TIME_UPDATE,
            createdDate: '2021-08-02T12:00:00.000Z',
            oldWorkingTime: 3600,
            newWorkingTime: 7200,
            courseWide: true,
        };
        websocketStreamSubject.next(wsEvent);
        expect(service['events']).toHaveLength(1);
        expect(service['events'][0].id).toBe(42);

        // After 2 seconds, the REST fetch fires
        tick(2000);
        const req = httpMock.expectOne({ method: 'GET', url: `/api/exam/courses/3/exams/3/student-exams/live-events` });

        // The server returns the same event (42) that we already got via WebSocket,
        // plus an older event (10) from before our subscription was active
        const fetchedEvents = [
            { id: 10, createdDate: '2021-08-02T11:00:00.000Z', eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT, text: 'Older event' },
            { id: 42, createdDate: '2021-08-02T12:00:00.000Z', eventType: ExamLiveEventType.WORKING_TIME_UPDATE },
        ];
        req.flush(fetchedEvents);

        // Both events must be present. Event 42 (from WebSocket) was preserved,
        // and event 10 (only in REST response) was merged in. No overwrite happened.
        expect(service['events']).toHaveLength(2);
        const eventIds = service['events'].map((e: any) => e.id);
        expect(eventIds).toContain(42);
        expect(eventIds).toContain(10);
    }));

    // Regression test for the race condition where a WebSocket event arrives while an HTTP
    // request is in flight. The HTTP response does NOT contain the WS event (it was created
    // after the server processed the GET request). Previously, the HTTP response would overwrite
    // this.events, erasing the WS event. With the merge fix, both events are retained.
    it('should not lose websocket events when fetch response arrives later', fakeAsync(() => {
        const websocketStreamSubject = new Subject<any>();
        jest.spyOn(mockWebsocketService, 'subscribe').mockReturnValue(websocketStreamSubject.asObservable());

        currentlyLoadedStudentExamSubject.next({ id: 4, exam: { id: 4, course: { id: 4 } } });

        // Advance to trigger the delayed REST fetch
        tick(2000);
        const req = httpMock.expectOne({ method: 'GET', url: `/api/exam/courses/4/exams/4/student-exams/live-events` });

        // While the HTTP request is in flight (sent but response not yet received),
        // a new announcement arrives via WebSocket. This is the critical race condition.
        const wsEvent: any = {
            id: 99,
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
            text: 'Late-breaking announcement',
            createdDate: '2021-08-02T13:00:00.000Z',
        };
        websocketStreamSubject.next(wsEvent);
        expect(service['events']).toHaveLength(1);

        // Now the HTTP response arrives. It does NOT contain event 99 because the server
        // processed the GET request before the announcement was created. It only has event 5.
        req.flush([{ id: 5, createdDate: '2021-08-02T10:00:00.000Z', eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT, text: 'Old event' }]);

        // Both events must be present: event 99 from WebSocket and event 5 from REST.
        // Previously, the REST response would have overwritten events, losing event 99.
        expect(service['events']).toHaveLength(2);
        const eventIds = service['events'].map((e: any) => e.id);
        expect(eventIds).toContain(99);
        expect(eventIds).toContain(5);
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
            createdDate: dayjs(),
            eventType: ExamLiveEventType.EXAM_WIDE_ANNOUNCEMENT,
        };

        service.acknowledgeEvent(mockEvent, true);
        expect(service['lastAcknowledgedEventStatus']!.acknowledgedEvents['1'].user).toBeGreaterThanOrEqual(nowUnix);
        expect(mockLocalStorageService.store).toHaveBeenCalledWith(expect.any(String), expect.any(String));
    });
});
