import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';

describe('CalendarEventService', () => {
    let service: CalendarEventService;
    let httpMock: HttpTestingController;

    const courseId = 42;
    const date = dayjs('2025-10-01');
    const expectedUrl = `/api/core/calendar/courses/${courseId}/calendar-events`;

    const testRequestResponse = {
        '2025-10-01': [
            {
                id: 'lecture-1-startAndEndDate',
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00Z',
                endDate: '2025-10-01T10:00:00Z',
            },
            {
                id: 'textExercise-1-startDate',
                title: 'Exercise Session',
                startDate: '2025-10-01T10:00:00Z',
            },
        ],
        '2025-10-02': [
            {
                id: 'exam-1-startAndEndDate',
                title: 'Final Exam',
                startDate: '2025-10-02T09:00:00Z',
                endDate: '2025-10-02T11:00:00Z',
                facilitator: 'Prof. Krusche',
            },
        ],
        '2025-10-03': [
            {
                id: 'tutorial-1',
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
            providers: [CalendarEventService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(CalendarEventService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should load events and create unfiltered event map', fakeAsync(() => {
        service.includedEventFilterOptions.set(['lectureEvents', 'examEvents', 'tutorialEvents', 'exerciseEvents']);

        service.loadEventsForCurrentMonth(courseId, date).subscribe(() => {
            const result = service.eventMap();
            expect(result.size).toBe(3);

            const eventsOnFirst = result.get('2025-10-01');
            expect(eventsOnFirst).toHaveLength(2);

            expectCalendarEventToMatch(eventsOnFirst![0], {
                id: 'lecture-1-startAndEndDate',
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00.000Z',
                endDate: '2025-10-01T10:00:00.000Z',
            });

            expectCalendarEventToMatch(eventsOnFirst![1], {
                id: 'textExercise-1-startDate',
                title: 'Exercise Session',
                startDate: '2025-10-01T10:00:00.000Z',
            });

            const eventsOnSecond = result.get('2025-10-02');
            expect(eventsOnSecond).toHaveLength(1);
            expectCalendarEventToMatch(eventsOnSecond![0], {
                id: 'exam-1-startAndEndDate',
                title: 'Final Exam',
                startDate: '2025-10-02T09:00:00.000Z',
                endDate: '2025-10-02T11:00:00.000Z',
                facilitator: 'Prof. Krusche',
            });

            const eventsOnThird = result.get('2025-10-03');
            expect(eventsOnThird).toHaveLength(1);
            expectCalendarEventToMatch(eventsOnThird![0], {
                id: 'tutorial-1',
                title: 'Tutorial Session',
                startDate: '2025-10-03T13:00:00.000Z',
                endDate: '2025-10-03T14:00:00.000Z',
                location: 'Zoom',
                facilitator: 'Marlon Nienaber',
            });
        });

        const testRequets = httpMock.expectOne((request) => request.url === expectedUrl);
        expect(testRequets.request.method).toBe('GET');
        expect(testRequets.request.params.get('monthKeys')).toBe('2025-09,2025-10,2025-11');
        expect(testRequets.request.params.get('timeZone')).toBeTruthy();

        testRequets.flush(testRequestResponse);
        tick();
    }));

    it('should load events and create filtered event map', fakeAsync(() => {
        service.includedEventFilterOptions.set(['lectureEvents', 'examEvents']);

        service.loadEventsForCurrentMonth(courseId, date).subscribe(() => {
            const result = service.eventMap();
            expect(result.size).toBe(2);

            const eventsOnFirst = result.get('2025-10-01');
            expect(eventsOnFirst).toHaveLength(1);

            expectCalendarEventToMatch(eventsOnFirst![0], {
                id: 'lecture-1-startAndEndDate',
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00.000Z',
                endDate: '2025-10-01T10:00:00.000Z',
            });

            const eventsOnSecond = result.get('2025-10-02');
            expect(eventsOnSecond).toHaveLength(1);
            expectCalendarEventToMatch(eventsOnSecond![0], {
                id: 'exam-1-startAndEndDate',
                title: 'Final Exam',
                startDate: '2025-10-02T09:00:00.000Z',
                endDate: '2025-10-02T11:00:00.000Z',
                facilitator: 'Prof. Krusche',
            });
        });

        const testRequets = httpMock.expectOne((request) => request.url === expectedUrl);
        expect(testRequets.request.method).toBe('GET');
        expect(testRequets.request.params.get('monthKeys')).toBe('2025-09,2025-10,2025-11');
        expect(testRequets.request.params.get('timeZone')).toBeTruthy();

        testRequets.flush(testRequestResponse);
        tick();
    }));

    it('should return filtered map when toggling options', fakeAsync(() => {
        const smallHttpResponse = {
            '2025-10-01': [
                {
                    id: 'lecture-1-startAndEndDate',
                    title: 'Object Design',
                    startDate: '2025-10-01T08:00:00Z',
                    endDate: '2025-10-01T10:00:00Z',
                    location: null,
                    facilitator: null,
                },
            ],
        };

        service.includedEventFilterOptions.set(['examEvents']);

        service.loadEventsForCurrentMonth(courseId, dayjs('2025-10-01')).subscribe(() => {
            expect(service.eventMap().get('2025-10-01')).toBeUndefined();

            service.toggleEventFilterOption('lectureEvents');

            const filteredEvents = service.eventMap().get('2025-10-01');
            expect(filteredEvents).toBeDefined();
            expect(filteredEvents).toHaveLength(1);

            expectCalendarEventToMatch(filteredEvents![0], {
                id: 'lecture-1-startAndEndDate',
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00.000Z',
                endDate: '2025-10-01T10:00:00.000Z',
            });
        });

        const testRequets = httpMock.expectOne((request) => request.url === expectedUrl);
        expect(testRequets.request.method).toBe('GET');
        testRequets.flush(smallHttpResponse);
        tick();
    }));

    it('should not reload for current month key', fakeAsync(() => {
        service['currentMonthKey'] = date.format('YYYY-MM');
        service.loadEventsForCurrentMonth(courseId, date).subscribe();
        httpMock.expectNone(expectedUrl);
        tick();
    }));
});

function expectCalendarEventToMatch(
    event: CalendarEvent,
    expected: {
        id: string;
        title: string;
        startDate: string;
        endDate?: string;
        location?: string;
        facilitator?: string;
    },
): void {
    const { id, title, startDate, endDate, location, facilitator } = expected;

    expect(event).toBeInstanceOf(CalendarEvent);
    expect(event.id).toBe(id);
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
