import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { MockService } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';
import { CalendarEvent, CalendarEventSubtype, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';

describe('CalendarEventService', () => {
    let service: CalendarEventService;
    let httpMock: HttpTestingController;

    const courseId = 42;
    const date = dayjs('2025-10-01');
    const expectedUrl = `/api/core/calendar/courses/${courseId}/calendar-events`;

    const testRequestResponse = {
        '2025-10-01': [
            {
                type: 'LECTURE',
                subtype: 'START_AND_END_DATE',
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00Z',
                endDate: '2025-10-01T10:00:00Z',
            },
            {
                type: 'TEXT_EXERCISE',
                subtype: 'START_DATE',
                title: 'Exercise Session',
                startDate: '2025-10-01T10:00:00Z',
            },
        ],
        '2025-10-02': [
            {
                type: 'EXAM',
                subtype: 'START_AND_END_DATE',
                title: 'Final Exam',
                startDate: '2025-10-02T09:00:00Z',
                endDate: '2025-10-02T11:00:00Z',
                facilitator: 'Prof. Krusche',
            },
        ],
        '2025-10-03': [
            {
                type: 'TUTORIAL',
                subtype: 'START_AND_END_DATE',
                title: 'Tutorial Session',
                startDate: '2025-10-03T13:00:00Z',
                endDate: '2025-10-03T14:00:00Z',
                location: 'Zoom',
                facilitator: 'Marlon Nienaber',
            },
        ],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [CalendarEventService, provideHttpClient(), provideHttpClientTesting(), { provide: AlertService, useValue: MockService(AlertService) }],
        });

        service = TestBed.inject(CalendarEventService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should load events and create unfiltered event map', fakeAsync(() => {
        service.includedEventFilterOptions.set([
            CalendarEventFilterOption.LectureEvents,
            CalendarEventFilterOption.ExamEvents,
            CalendarEventFilterOption.TutorialEvents,
            CalendarEventFilterOption.ExerciseEvents,
        ]);

        service.loadEventsForCurrentMonth(courseId, date).subscribe(() => {
            const result = service.eventMap();
            expect(result.size).toBe(3);

            const eventsOnFirst = result.get('2025-10-01');
            expect(eventsOnFirst).toHaveLength(2);

            expectCalendarEventToMatch(eventsOnFirst![0], {
                type: CalendarEventType.Lecture,
                subtype: CalendarEventSubtype.StartAndEndDate,
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00.000Z',
                endDate: '2025-10-01T10:00:00.000Z',
            });

            expectCalendarEventToMatch(eventsOnFirst![1], {
                type: CalendarEventType.TextExercise,
                subtype: CalendarEventSubtype.StartDate,
                title: 'Exercise Session',
                startDate: '2025-10-01T10:00:00.000Z',
            });

            const eventsOnSecond = result.get('2025-10-02');
            expect(eventsOnSecond).toHaveLength(1);
            expectCalendarEventToMatch(eventsOnSecond![0], {
                type: CalendarEventType.Exam,
                subtype: CalendarEventSubtype.StartAndEndDate,
                title: 'Final Exam',
                startDate: '2025-10-02T09:00:00.000Z',
                endDate: '2025-10-02T11:00:00.000Z',
                facilitator: 'Prof. Krusche',
            });

            const eventsOnThird = result.get('2025-10-03');
            expect(eventsOnThird).toHaveLength(1);
            expectCalendarEventToMatch(eventsOnThird![0], {
                type: CalendarEventType.Tutorial,
                subtype: CalendarEventSubtype.StartAndEndDate,
                title: 'Tutorial Session',
                startDate: '2025-10-03T13:00:00.000Z',
                endDate: '2025-10-03T14:00:00.000Z',
                location: 'Zoom',
                facilitator: 'Marlon Nienaber',
            });
        });

        const testRequest = httpMock.expectOne((request) => request.url === expectedUrl);
        expect(testRequest.request.method).toBe('GET');
        testRequest.flush(testRequestResponse);
        tick();
    }));

    it('should load events and create filtered event map', fakeAsync(() => {
        service.includedEventFilterOptions.set([CalendarEventFilterOption.LectureEvents, CalendarEventFilterOption.ExamEvents]);

        service.loadEventsForCurrentMonth(courseId, date).subscribe(() => {
            const result = service.eventMap();
            expect(result.size).toBe(2);

            const eventsOnFirst = result.get('2025-10-01');
            expect(eventsOnFirst).toHaveLength(1);
            expectCalendarEventToMatch(eventsOnFirst![0], {
                type: CalendarEventType.Lecture,
                subtype: CalendarEventSubtype.StartAndEndDate,
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00.000Z',
                endDate: '2025-10-01T10:00:00.000Z',
            });

            const eventsOnSecond = result.get('2025-10-02');
            expect(eventsOnSecond).toHaveLength(1);
            expectCalendarEventToMatch(eventsOnSecond![0], {
                type: CalendarEventType.Exam,
                subtype: CalendarEventSubtype.StartAndEndDate,
                title: 'Final Exam',
                startDate: '2025-10-02T09:00:00.000Z',
                endDate: '2025-10-02T11:00:00.000Z',
                facilitator: 'Prof. Krusche',
            });
        });

        const testRequest = httpMock.expectOne((request) => request.url === expectedUrl);
        expect(testRequest.request.method).toBe('GET');
        expect(testRequest.request.params.get('monthKeys')).toBe('2025-09,2025-10,2025-11');
        expect(testRequest.request.params.get('timeZone')).toBeTruthy();

        testRequest.flush(testRequestResponse);
        tick();
    }));

    it('should return filtered map when toggling options', fakeAsync(() => {
        const smallHttpResponse = {
            '2025-10-01': [
                {
                    type: 'LECTURE',
                    subtype: 'START_AND_END_DATE',
                    title: 'Object Design',
                    startDate: '2025-10-01T08:00:00Z',
                    endDate: '2025-10-01T10:00:00Z',
                },
            ],
        };

        service.includedEventFilterOptions.set([CalendarEventFilterOption.ExamEvents]);

        service.loadEventsForCurrentMonth(courseId, dayjs('2025-10-01')).subscribe(() => {
            expect(service.eventMap().get('2025-10-01')).toBeUndefined();

            service.toggleEventFilterOption('lectureEvents');

            const filteredEvents = service.eventMap().get('2025-10-01');
            expect(filteredEvents).toBeDefined();
            expect(filteredEvents).toHaveLength(1);

            expectCalendarEventToMatch(filteredEvents![0], {
                type: CalendarEventType.Lecture,
                subtype: CalendarEventSubtype.StartAndEndDate,
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00.000Z',
                endDate: '2025-10-01T10:00:00.000Z',
            });
        });

        const testRequest = httpMock.expectOne((request) => request.url === expectedUrl);
        expect(testRequest.request.method).toBe('GET');
        testRequest.flush(smallHttpResponse);
        tick();
    }));

    it('should refresh', fakeAsync(() => {
        service.includedEventFilterOptions.set([CalendarEventFilterOption.LectureEvents, CalendarEventFilterOption.ExamEvents]);
        service.loadEventsForCurrentMonth(courseId, date).subscribe();

        const initialRequest = httpMock.expectOne((request) => request.url === expectedUrl);
        expect(initialRequest.request.method).toBe('GET');
        initialRequest.flush({});
        tick();

        service.refresh();

        const refreshRequest = httpMock.expectOne((request) => request.url === expectedUrl);
        expect(refreshRequest.request.method).toBe('GET');
        refreshRequest.flush({});
        tick();
    }));
});

function expectCalendarEventToMatch(
    event: CalendarEvent,
    expected: {
        type: CalendarEventType;
        subtype: CalendarEventSubtype;
        title: string;
        startDate: string;
        endDate?: string;
        location?: string;
        facilitator?: string;
    },
): void {
    const { type, subtype, title, startDate, endDate, location, facilitator } = expected;

    expect(event).toBeInstanceOf(CalendarEvent);
    expect(event.type).toBe(type);
    expect(event.subtype).toBe(subtype);
    expect(event.title).toBe(title);
    expect(event.startDate.toISOString()).toBe(dayjs(startDate).toISOString());
    if (endDate !== undefined) {
        expect(event.endDate?.toISOString()).toBe(dayjs(endDate).toISOString());
    } else {
        expect(event.endDate).toBeUndefined();
    }
    expect(event.location).toBe(location);
    expect(event.facilitator).toBe(facilitator);
}
