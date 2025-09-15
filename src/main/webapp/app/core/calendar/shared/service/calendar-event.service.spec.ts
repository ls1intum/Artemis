import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockService } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';
import { CalendarEvent, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';

describe('CalendarService', () => {
    let service: CalendarService;
    let httpMock: HttpTestingController;

    const courseId = 42;
    const date = dayjs('2025-10-01');
    const expectedEventUrl = `/api/core/calendar/courses/${courseId}/calendar-events`;
    const expectedTokenUrl = '/api/core/calendar/subscription-token';
    const testToken = 'testToken';

    const testRequestResponse = {
        '2025-10-01': [
            {
                type: 'LECTURE',
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00Z',
                endDate: '2025-10-01T10:00:00Z',
            },
            {
                type: 'TEXT_EXERCISE',
                title: 'Start: Exercise Session',
                startDate: '2025-10-01T10:00:00Z',
            },
        ],
        '2025-10-02': [
            {
                type: 'EXAM',
                title: 'Final Exam',
                startDate: '2025-10-02T09:00:00Z',
                endDate: '2025-10-02T11:00:00Z',
                facilitator: 'Prof. Krusche',
            },
        ],
        '2025-10-03': [
            {
                type: 'TUTORIAL',
                title: 'Tutorial Session',
                startDate: '2025-10-03T13:00:00Z',
                endDate: '2025-10-03T14:00:00Z',
                location: 'Zoom',
                facilitator: 'Marlon Nienaber',
            },
        ],
    };

    const translateServiceMock = {
        currentLang: 'en',
        onLangChange: of({ lang: 'en' }),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                CalendarService,
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AlertService, useValue: MockService(AlertService) },
                { provide: TranslateService, useValue: translateServiceMock },
            ],
        });

        service = TestBed.inject(CalendarService);
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
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00.000Z',
                endDate: '2025-10-01T10:00:00.000Z',
            });

            expectCalendarEventToMatch(eventsOnFirst![1], {
                type: CalendarEventType.TextExercise,
                title: 'Start: Exercise Session',
                startDate: '2025-10-01T10:00:00.000Z',
            });

            const eventsOnSecond = result.get('2025-10-02');
            expect(eventsOnSecond).toHaveLength(1);
            expectCalendarEventToMatch(eventsOnSecond![0], {
                type: CalendarEventType.Exam,
                title: 'Final Exam',
                startDate: '2025-10-02T09:00:00.000Z',
                endDate: '2025-10-02T11:00:00.000Z',
                facilitator: 'Prof. Krusche',
            });

            const eventsOnThird = result.get('2025-10-03');
            expect(eventsOnThird).toHaveLength(1);
            expectCalendarEventToMatch(eventsOnThird![0], {
                type: CalendarEventType.Tutorial,
                title: 'Tutorial Session',
                startDate: '2025-10-03T13:00:00.000Z',
                endDate: '2025-10-03T14:00:00.000Z',
                location: 'Zoom',
                facilitator: 'Marlon Nienaber',
            });
        });

        const testRequest = httpMock.expectOne((request) => request.url === expectedEventUrl);
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
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00.000Z',
                endDate: '2025-10-01T10:00:00.000Z',
            });

            const eventsOnSecond = result.get('2025-10-02');
            expect(eventsOnSecond).toHaveLength(1);
            expectCalendarEventToMatch(eventsOnSecond![0], {
                type: CalendarEventType.Exam,
                title: 'Final Exam',
                startDate: '2025-10-02T09:00:00.000Z',
                endDate: '2025-10-02T11:00:00.000Z',
                facilitator: 'Prof. Krusche',
            });
        });

        const testRequest = httpMock.expectOne((request) => request.url === expectedEventUrl);
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
                    title: 'Object Design',
                    startDate: '2025-10-01T08:00:00Z',
                    endDate: '2025-10-01T10:00:00Z',
                },
            ],
        };

        service.includedEventFilterOptions.set([CalendarEventFilterOption.ExamEvents]);

        service.loadEventsForCurrentMonth(courseId, dayjs('2025-10-01')).subscribe(() => {
            expect(service.eventMap().get('2025-10-01')).toBeUndefined();

            service.toggleEventFilterOption(CalendarEventFilterOption.LectureEvents);

            const filteredEvents = service.eventMap().get('2025-10-01');
            expect(filteredEvents).toBeDefined();
            expect(filteredEvents).toHaveLength(1);

            expectCalendarEventToMatch(filteredEvents![0], {
                type: CalendarEventType.Lecture,
                title: 'Object Design',
                startDate: '2025-10-01T08:00:00.000Z',
                endDate: '2025-10-01T10:00:00.000Z',
            });
        });

        const testRequest = httpMock.expectOne((request) => request.url === expectedEventUrl);
        expect(testRequest.request.method).toBe('GET');
        testRequest.flush(smallHttpResponse);
        tick();
    }));

    it('should load subscription token and set token property', fakeAsync(() => {
        service.loadSubscriptionToken().subscribe(() => {
            const result = service.subscriptionToken();
            expect(result).toBe(testToken);
        });

        const tokenRequest = httpMock.expectOne((request) => request.url === expectedTokenUrl);
        tokenRequest.flush(testToken);
        tick();
    }));

    it('should refresh', fakeAsync(() => {
        service.includedEventFilterOptions.set([CalendarEventFilterOption.LectureEvents, CalendarEventFilterOption.ExamEvents]);
        service.loadEventsForCurrentMonth(courseId, date).subscribe();
        service.loadSubscriptionToken().subscribe();

        const initialEventRequest = httpMock.expectOne((request) => request.url === expectedEventUrl);
        expect(initialEventRequest.request.method).toBe('GET');
        initialEventRequest.flush({});
        const initialTokenRequest = httpMock.expectOne((request) => request.url === expectedTokenUrl);
        initialTokenRequest.flush(testToken);
        tick();

        service.reloadEvents();

        const refreshEventRequest = httpMock.expectOne((request) => request.url === expectedEventUrl);
        expect(refreshEventRequest.request.method).toBe('GET');
        refreshEventRequest.flush({});
        tick();
    }));
});

function expectCalendarEventToMatch(
    event: CalendarEvent,
    expected: {
        type: CalendarEventType;
        title: string;
        startDate: string;
        endDate?: string;
        location?: string;
        facilitator?: string;
    },
): void {
    const { type, title, startDate, endDate, location, facilitator } = expected;

    expect(event).toBeInstanceOf(CalendarEvent);
    expect(event.type).toBe(type);
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
