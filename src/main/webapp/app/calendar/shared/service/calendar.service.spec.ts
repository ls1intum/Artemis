import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject, distinctUntilChanged, firstValueFrom, of } from 'rxjs';
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
import { CalendarApiService } from 'app/openapi/api/calendarApi.service';
import { CalendarEvent } from 'app/openapi/model/calendarEvent';
import { HttpErrorResponse } from '@angular/common/http';

describe('CalendarService', () => {
    setupTestBed({ zoneless: true });

    let service: CalendarService;

    let calendarApiService: {
        getCalendarEventsOverlappingMonths: ReturnType<typeof vi.fn>;
        getCalendarEventSubscriptionToken: ReturnType<typeof vi.fn>;
    };

    const courseId = 42;
    const date = dayjs('2025-10-01');
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
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    beforeEach(() => {
        calendarApiService = {
            getCalendarEventsOverlappingMonths: vi.fn(),
            getCalendarEventSubscriptionToken: vi.fn(),
        };

        calendarApiService.getCalendarEventSubscriptionToken.mockReturnValue(of(testToken));

        TestBed.configureTestingModule({
            providers: [
                CalendarService,
                { provide: CalendarApiService, useValue: calendarApiService },
                { provide: AlertService, useValue: MockService(AlertService) },
                { provide: TranslateService, useValue: translateServiceMock },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });

        service = TestBed.inject(CalendarService);
    });

    it('should load events and create unfiltered event map', async () => {
        expect(calendarApiService.getCalendarEventSubscriptionToken).toHaveBeenCalledOnce();

        service.includedEventFilterOptions.set([
            CalendarEventFilterOption.LectureEvents,
            CalendarEventFilterOption.ExamEvents,
            CalendarEventFilterOption.TutorialEvents,
            CalendarEventFilterOption.ExerciseEvents,
        ]);

        calendarApiService.getCalendarEventsOverlappingMonths.mockReturnValue(of(testRequestResponse));

        const promise = firstValueFrom(service.loadEventsForCurrentMonth(courseId, date));

        expect(calendarApiService.getCalendarEventsOverlappingMonths).toHaveBeenCalledOnce();

        await promise;

        const result = service.eventMap();
        expect(result.size).toBe(3);

        const eventsOnFirst = result.get('2025-10-01');
        expect(eventsOnFirst).toHaveLength(2);

        expectCalendarEventToMatch(eventsOnFirst![0], {
            type: CalendarEvent.TypeEnum.Lecture,
            title: 'Object Design',
            startDate: '2025-10-01T08:00:00.000Z',
            endDate: '2025-10-01T10:00:00.000Z',
        });

        expectCalendarEventToMatch(eventsOnFirst![1], {
            type: CalendarEvent.TypeEnum.TextExercise,
            title: 'Start: Exercise Session',
            startDate: '2025-10-01T10:00:00.000Z',
        });

        const eventsOnSecond = result.get('2025-10-02');
        expect(eventsOnSecond).toHaveLength(1);
        expectCalendarEventToMatch(eventsOnSecond![0], {
            type: CalendarEvent.TypeEnum.Exam,
            title: 'Final Exam',
            startDate: '2025-10-02T09:00:00.000Z',
            endDate: '2025-10-02T11:00:00.000Z',
            facilitator: 'Prof. Krusche',
        });

        const eventsOnThird = result.get('2025-10-03');
        expect(eventsOnThird).toHaveLength(1);
        expectCalendarEventToMatch(eventsOnThird![0], {
            type: CalendarEvent.TypeEnum.Tutorial,
            title: 'Tutorial Session',
            startDate: '2025-10-03T13:00:00.000Z',
            endDate: '2025-10-03T14:00:00.000Z',
            location: 'Zoom',
            facilitator: 'Marlon Nienaber',
        });
    });

    it('should load events and create filtered event map', async () => {
        expect(calendarApiService.getCalendarEventSubscriptionToken).toHaveBeenCalledOnce();

        service.includedEventFilterOptions.set([CalendarEventFilterOption.LectureEvents, CalendarEventFilterOption.ExamEvents]);

        calendarApiService.getCalendarEventsOverlappingMonths.mockReturnValue(of(testRequestResponse));

        const promise = firstValueFrom(service.loadEventsForCurrentMonth(courseId, date));

        expect(calendarApiService.getCalendarEventsOverlappingMonths).toHaveBeenCalledWith(
            expect.anything(),
            ['2025-09', '2025-10', '2025-11'],
            expect.anything(),
            expect.anything(),
        );

        await promise;

        const result = service.eventMap();
        expect(result.size).toBe(2);

        const eventsOnFirst = result.get('2025-10-01');
        expect(eventsOnFirst).toHaveLength(1);
        expectCalendarEventToMatch(eventsOnFirst![0], {
            type: CalendarEvent.TypeEnum.Lecture,
            title: 'Object Design',
            startDate: '2025-10-01T08:00:00.000Z',
            endDate: '2025-10-01T10:00:00.000Z',
        });

        const eventsOnSecond = result.get('2025-10-02');
        expect(eventsOnSecond).toHaveLength(1);
        expectCalendarEventToMatch(eventsOnSecond![0], {
            type: CalendarEvent.TypeEnum.Exam,
            title: 'Final Exam',
            startDate: '2025-10-02T09:00:00.000Z',
            endDate: '2025-10-02T11:00:00.000Z',
            facilitator: 'Prof. Krusche',
        });
    });

    it('should return filtered map when toggling options', async () => {
        expect(calendarApiService.getCalendarEventSubscriptionToken).toHaveBeenCalledOnce();

        service.includedEventFilterOptions.set([CalendarEventFilterOption.ExamEvents]);

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
        calendarApiService.getCalendarEventsOverlappingMonths.mockReturnValue(of(smallHttpResponse));

        const promise = firstValueFrom(service.loadEventsForCurrentMonth(courseId, dayjs('2025-10-01')));

        expect(calendarApiService.getCalendarEventsOverlappingMonths).toHaveBeenCalledOnce();

        await promise;

        expect(service.eventMap().get('2025-10-01')).toBeUndefined();

        service.toggleEventFilterOption(CalendarEventFilterOption.LectureEvents);

        const filteredEvents = service.eventMap().get('2025-10-01');
        expect(filteredEvents).toBeDefined();
        expect(filteredEvents).toHaveLength(1);

        expectCalendarEventToMatch(filteredEvents![0], {
            type: CalendarEvent.TypeEnum.Lecture,
            title: 'Object Design',
            startDate: '2025-10-01T08:00:00.000Z',
            endDate: '2025-10-01T10:00:00.000Z',
        });
    });

    it('should load subscription token and set token property', async () => {
        expect(calendarApiService.getCalendarEventSubscriptionToken).toHaveBeenCalledOnce();
        // Allow microtasks to complete
        await Promise.resolve();
        expect(service.subscriptionToken()).toBe(testToken);
    });

    it('should refresh', async () => {
        expect(calendarApiService.getCalendarEventSubscriptionToken).toHaveBeenCalledOnce();
        await Promise.resolve();

        service.includedEventFilterOptions.set([CalendarEventFilterOption.LectureEvents, CalendarEventFilterOption.ExamEvents]);
        calendarApiService.getCalendarEventsOverlappingMonths.mockReturnValue(of({}));
        const loadPromise = firstValueFrom(service.loadEventsForCurrentMonth(courseId, date));

        expect(calendarApiService.getCalendarEventsOverlappingMonths).toHaveBeenCalledOnce();
        await loadPromise;

        service.reloadEvents();

        expect(calendarApiService.getCalendarEventsOverlappingMonths).toHaveBeenCalledTimes(2);
    });
});

describe('CalendarService - authentication state changes', () => {
    setupTestBed({ zoneless: true });

    const courseId = 42;
    const date = dayjs('2025-10-01');
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

    let calendarApiService: {
        getCalendarEventsOverlappingMonths: ReturnType<typeof vi.fn>;
        getCalendarEventSubscriptionToken: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        authState = new BehaviorSubject<User | undefined>({ id: 99 } as User);
        const customAccountService = new MockAccountService();
        customAccountService.userIdentity.set({ id: 99 } as User);
        customAccountService.getAuthenticationState = () => authState.asObservable().pipe(distinctUntilChanged());

        calendarApiService = {
            getCalendarEventsOverlappingMonths: vi.fn(),
            getCalendarEventSubscriptionToken: vi.fn(),
        };

        calendarApiService.getCalendarEventSubscriptionToken.mockReturnValue(of(testToken));

        TestBed.configureTestingModule({
            providers: [
                CalendarService,
                { provide: CalendarApiService, useValue: calendarApiService },
                { provide: AlertService, useValue: MockService(AlertService) },
                { provide: TranslateService, useValue: translateServiceMock },
                { provide: AccountService, useValue: customAccountService },
            ],
        });

        scoped = TestBed.inject(CalendarService);
        expect(calendarApiService.getCalendarEventSubscriptionToken).toHaveBeenCalledOnce();
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    it('should clear event map and reload subscription token on logout/login', async () => {
        calendarApiService.getCalendarEventsOverlappingMonths.mockReturnValue(of({}));
        const loadPromise = firstValueFrom(scoped.loadEventsForCurrentMonth(courseId, date));
        expect(calendarApiService.getCalendarEventsOverlappingMonths).toHaveBeenCalledOnce();
        await loadPromise;
        expect(scoped.subscriptionToken()).toBe(testToken);

        authState.next(undefined);

        expect(scoped.eventMap().size).toBe(0);
        expect(scoped.subscriptionToken()).toBeUndefined();

        calendarApiService.getCalendarEventSubscriptionToken.mockReturnValue(of('newToken'));

        authState.next({ id: 42 } as User);
        expect(calendarApiService.getCalendarEventSubscriptionToken).toHaveBeenCalledTimes(2);
        expect(scoped.subscriptionToken()).toBe('newToken');
    });

    it('should not reset state when the same user re-emits', () => {
        authState.next({ id: 99 } as User);
        expect(scoped.subscriptionToken()).toBe(testToken);
    });

    it('should ignore in-flight HTTP responses after reset', async () => {
        const response = new Subject<Record<string, never[]>>();
        calendarApiService.getCalendarEventsOverlappingMonths.mockReturnValue(response);
        const loadObservable = scoped.loadEventsForCurrentMonth(courseId, date);
        const loadPromise = firstValueFrom(loadObservable);
        expect(calendarApiService.getCalendarEventsOverlappingMonths).toHaveBeenCalledOnce();

        authState.next(undefined);

        // After reset, the in-flight response must not write back into the now-cleared event map.
        response.next({ '2025-10-01': [] });
        response.complete();
        await loadPromise;

        expect(scoped.eventMap().size).toBe(0);
    });

    it('should not show an error alert for in-flight events requests that fail after reset', async () => {
        const alertService = TestBed.inject(AlertService);
        const alertSpy = vi.spyOn(alertService, 'addErrorAlert');

        const response = new Subject<Record<string, never[]>>();
        calendarApiService.getCalendarEventsOverlappingMonths.mockReturnValue(response);
        const loadPromise = firstValueFrom(scoped.loadEventsForCurrentMonth(courseId, date)).catch(() => undefined);
        expect(calendarApiService.getCalendarEventsOverlappingMonths).toHaveBeenCalledOnce();

        authState.next(undefined);

        // The request fails after the user logged out. The catchError must short-circuit before the
        // alert is shown so we don't pop a toast on the next user's screen.
        response.error(
            new HttpErrorResponse({
                status: 500,
                statusText: 'Server Error',
            }),
        );
        await loadPromise;

        expect(alertSpy).not.toHaveBeenCalled();
    });
});

function expectCalendarEventToMatch(
    event: IdentifiableCalendarEvent,
    expected: {
        type: CalendarEvent.TypeEnum;
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
