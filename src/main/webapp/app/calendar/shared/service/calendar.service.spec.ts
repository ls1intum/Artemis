import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { BehaviorSubject, distinctUntilChanged, firstValueFrom, of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockService } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { IdentifiableCalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import { CalendarEventFilterOption } from 'app/calendar/shared/util/calendar-util';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';

describe('CalendarService', () => {
    setupTestBed({ zoneless: true });

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
        getCurrentLang(): string {
            return this.currentLang;
        },
        onLangChange: of({ lang: 'en' }),
    };

    afterEach(() => {
        vi.restoreAllMocks();
        httpMock.verify();
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                CalendarService,
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AlertService, useValue: MockService(AlertService) },
                { provide: TranslateService, useValue: translateServiceMock },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });

        service = TestBed.inject(CalendarService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should load events and create unfiltered event map', async () => {
        const tokenRequest = httpMock.expectOne((request) => request.url === expectedTokenUrl);
        tokenRequest.flush(testToken);

        service.includedEventFilterOptions.set([
            CalendarEventFilterOption.LectureEvents,
            CalendarEventFilterOption.ExamEvents,
            CalendarEventFilterOption.TutorialEvents,
            CalendarEventFilterOption.ExerciseEvents,
        ]);

        const promise = firstValueFrom(service.loadEventsForCurrentMonth(courseId, date));

        const eventRequest = httpMock.expectOne((request) => request.url === expectedEventUrl);
        expect(eventRequest.request.method).toBe('GET');
        eventRequest.flush(testRequestResponse);

        await promise;

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

    it('should load events and create filtered event map', async () => {
        const tokenRequest = httpMock.expectOne((request) => request.url === expectedTokenUrl);
        tokenRequest.flush(testToken);

        service.includedEventFilterOptions.set([CalendarEventFilterOption.LectureEvents, CalendarEventFilterOption.ExamEvents]);

        const promise = firstValueFrom(service.loadEventsForCurrentMonth(courseId, date));

        const eventRequest = httpMock.expectOne((request) => request.url === expectedEventUrl);
        expect(eventRequest.request.method).toBe('GET');
        expect(eventRequest.request.params.get('monthKeys')).toBe('2025-09,2025-10,2025-11');
        expect(eventRequest.request.params.get('timeZone')).toBeTruthy();

        eventRequest.flush(testRequestResponse);

        await promise;

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

    it('should return filtered map when toggling options', async () => {
        const tokenRequest = httpMock.expectOne((request) => request.url === expectedTokenUrl);
        tokenRequest.flush(testToken);

        service.includedEventFilterOptions.set([CalendarEventFilterOption.ExamEvents]);

        const promise = firstValueFrom(service.loadEventsForCurrentMonth(courseId, dayjs('2025-10-01')));

        const eventRequest = httpMock.expectOne((request) => request.url === expectedEventUrl);
        expect(eventRequest.request.method).toBe('GET');
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
        eventRequest.flush(smallHttpResponse);

        await promise;

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

    it('should load subscription token and set token property', async () => {
        const tokenRequest = httpMock.expectOne((request) => request.url === expectedTokenUrl);
        tokenRequest.flush(testToken);
        // Allow microtasks to complete
        await Promise.resolve();
        expect(service.subscriptionToken()).toBe(testToken);
    });

    it('should refresh', async () => {
        const initialTokenRequest = httpMock.expectOne((request) => request.url === expectedTokenUrl);
        initialTokenRequest.flush(testToken);
        await Promise.resolve();

        service.includedEventFilterOptions.set([CalendarEventFilterOption.LectureEvents, CalendarEventFilterOption.ExamEvents]);
        const loadPromise = firstValueFrom(service.loadEventsForCurrentMonth(courseId, date));

        const initialEventRequest = httpMock.expectOne((request) => request.url === expectedEventUrl);
        expect(initialEventRequest.request.method).toBe('GET');
        initialEventRequest.flush({});
        await loadPromise;

        service.reloadEvents();

        const refreshEventRequest = httpMock.expectOne((request) => request.url === expectedEventUrl);
        expect(refreshEventRequest.request.method).toBe('GET');
        refreshEventRequest.flush({});
    });
});

describe('CalendarService - authentication state changes', () => {
    setupTestBed({ zoneless: true });

    const courseId = 42;
    const date = dayjs('2025-10-01');
    const expectedEventUrl = `/api/core/calendar/courses/${courseId}/calendar-events`;
    const expectedTokenUrl = '/api/core/calendar/subscription-token';
    const testToken = 'testToken';
    const translateServiceMock = {
        currentLang: 'en',
        getCurrentLang(): string {
            return this.currentLang;
        },
        onLangChange: of({ lang: 'en' }),
    };

    let authState: BehaviorSubject<User | undefined>;
    let scoped: CalendarService;
    let scopedHttpMock: HttpTestingController;

    beforeEach(() => {
        authState = new BehaviorSubject<User | undefined>({ id: 99 } as User);
        const customAccountService = new MockAccountService();
        customAccountService.userIdentity.set({ id: 99 } as User);
        customAccountService.getAuthenticationState = () => authState.asObservable().pipe(distinctUntilChanged());

        TestBed.configureTestingModule({
            providers: [
                CalendarService,
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AlertService, useValue: MockService(AlertService) },
                { provide: TranslateService, useValue: translateServiceMock },
                { provide: AccountService, useValue: customAccountService },
            ],
        });

        scoped = TestBed.inject(CalendarService);
        scopedHttpMock = TestBed.inject(HttpTestingController);
        scopedHttpMock.expectOne((request) => request.url === expectedTokenUrl).flush(testToken);
    });

    afterEach(() => {
        scopedHttpMock.verify();
        vi.restoreAllMocks();
    });

    it('should clear event map and reload subscription token on logout/login', async () => {
        const loadPromise = firstValueFrom(scoped.loadEventsForCurrentMonth(courseId, date));
        scopedHttpMock.expectOne((request) => request.url === expectedEventUrl).flush({});
        await loadPromise;
        expect(scoped.subscriptionToken()).toBe(testToken);

        authState.next(undefined);

        expect(scoped.eventMap().size).toBe(0);
        expect(scoped.subscriptionToken()).toBeUndefined();

        authState.next({ id: 42 } as User);
        scopedHttpMock.expectOne((request) => request.url === expectedTokenUrl).flush('newToken');
        expect(scoped.subscriptionToken()).toBe('newToken');
    });

    it('should not reset state when the same user re-emits', () => {
        authState.next({ id: 99 } as User);
        expect(scoped.subscriptionToken()).toBe(testToken);
    });

    it('should ignore in-flight HTTP responses after reset', async () => {
        const loadObservable = scoped.loadEventsForCurrentMonth(courseId, date);
        const loadPromise = firstValueFrom(loadObservable);
        const inFlightRequest = scopedHttpMock.expectOne((request) => request.url === expectedEventUrl);

        authState.next(undefined);

        // After reset, the in-flight response must not write back into the now-cleared event map.
        inFlightRequest.flush({ '2025-10-01': [] });
        await loadPromise;

        expect(scoped.eventMap().size).toBe(0);
    });

    it('should not show an error alert for in-flight events requests that fail after reset', async () => {
        const alertService = TestBed.inject(AlertService);
        const alertSpy = vi.spyOn(alertService, 'addErrorAlert');

        const loadPromise = firstValueFrom(scoped.loadEventsForCurrentMonth(courseId, date)).catch(() => undefined);
        const inFlightRequest = scopedHttpMock.expectOne((request) => request.url === expectedEventUrl);

        authState.next(undefined);

        // The request fails after the user logged out. The catchError must short-circuit before the
        // alert is shown so we don't pop a toast on the next user's screen.
        inFlightRequest.flush(null, { status: 500, statusText: 'Server Error' });
        await loadPromise;

        expect(alertSpy).not.toHaveBeenCalled();
    });
});

function expectCalendarEventToMatch(
    event: IdentifiableCalendarEvent,
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

    expect(event).toBeInstanceOf(IdentifiableCalendarEvent);
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
