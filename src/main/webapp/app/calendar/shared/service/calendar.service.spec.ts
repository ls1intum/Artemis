import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject, distinctUntilChanged, firstValueFrom, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockService } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { IdentifiableCalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';
import { CalendarEventFilterOption } from 'app/calendar/shared/util/calendar-util';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';
import { CalendarApi } from 'app/openapi/api/calendar-api';
import { CalendarEventType } from 'app/openapi/model/calendar-event-type';
import { HttpErrorResponse } from '@angular/common/http';

describe('CalendarService', () => {
    let service: CalendarService;

    let calendarApiService: {
        getCalendarEventsOverlappingMonths: ReturnType<typeof vi.fn>;
        getCalendarEventSubscriptionToken: ReturnType<typeof vi.fn>;
        rotateCalendarEventSubscriptionToken: ReturnType<typeof vi.fn>;
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
            rotateCalendarEventSubscriptionToken: vi.fn(),
        };

        calendarApiService.getCalendarEventSubscriptionToken.mockReturnValue(of(testToken));

        TestBed.configureTestingModule({
            providers: [
                CalendarService,
                { provide: CalendarApi, useValue: calendarApiService },
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
            type: 'LECTURE',
            title: 'Object Design',
            startDate: '2025-10-01T08:00:00.000Z',
            endDate: '2025-10-01T10:00:00.000Z',
        });

        expectCalendarEventToMatch(eventsOnFirst![1], {
            type: 'TEXT_EXERCISE',
            title: 'Start: Exercise Session',
            startDate: '2025-10-01T10:00:00.000Z',
        });

        const eventsOnSecond = result.get('2025-10-02');
        expect(eventsOnSecond).toHaveLength(1);
        expectCalendarEventToMatch(eventsOnSecond![0], {
            type: 'EXAM',
            title: 'Final Exam',
            startDate: '2025-10-02T09:00:00.000Z',
            endDate: '2025-10-02T11:00:00.000Z',
            facilitator: 'Prof. Krusche',
        });

        const eventsOnThird = result.get('2025-10-03');
        expect(eventsOnThird).toHaveLength(1);
        expectCalendarEventToMatch(eventsOnThird![0], {
            type: 'TUTORIAL',
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
            type: 'LECTURE',
            title: 'Object Design',
            startDate: '2025-10-01T08:00:00.000Z',
            endDate: '2025-10-01T10:00:00.000Z',
        });

        const eventsOnSecond = result.get('2025-10-02');
        expect(eventsOnSecond).toHaveLength(1);
        expectCalendarEventToMatch(eventsOnSecond![0], {
            type: 'EXAM',
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
            type: 'LECTURE',
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

    it('should replace the subscription token with the rotated one', async () => {
        await Promise.resolve();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(of('rotatedToken'));

        await firstValueFrom(service.rotateSubscriptionToken());

        expect(calendarApiService.rotateCalendarEventSubscriptionToken).toHaveBeenCalledOnce();
        expect(service.subscriptionToken()).toBe('rotatedToken');
    });

    it('should keep the subscription token and show an alert if the rotation fails', async () => {
        await Promise.resolve();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        const addErrorAlert = vi.spyOn(TestBed.inject(AlertService), 'addErrorAlert');

        await expect(firstValueFrom(service.rotateSubscriptionToken())).rejects.toBeInstanceOf(HttpErrorResponse);

        expect(service.subscriptionToken()).toBe(testToken);
        expect(addErrorAlert).toHaveBeenCalledWith('artemisApp.calendar.tokenRotationError');
    });

    it('should report the running rotation and clear it when the rotation ends or fails', async () => {
        await Promise.resolve();
        const pending = new Subject<string>();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(pending);
        expect(service.subscriptionTokenRotating()).toBe(false);

        const done = firstValueFrom(service.rotateSubscriptionToken(), { defaultValue: undefined });
        expect(service.subscriptionTokenRotating()).toBe(true);
        pending.error(new HttpErrorResponse({ status: 500 }));
        await expect(done).rejects.toBeInstanceOf(HttpErrorResponse);

        expect(service.subscriptionTokenRotating()).toBe(false);
        // the token that was in use stays
        expect(service.subscriptionToken()).toBe(testToken);
    });

    it('should read the token again after a failure that may have replaced it', async () => {
        await Promise.resolve();
        // the server stored a new token, but the response of the replacement was lost
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
        calendarApiService.getCalendarEventSubscriptionToken.mockReturnValue(of('tokenStoredByTheServer'));

        await expect(firstValueFrom(service.rotateSubscriptionToken())).rejects.toBeInstanceOf(HttpErrorResponse);

        expect(calendarApiService.getCalendarEventSubscriptionToken).toHaveBeenCalledTimes(2);
        expect(service.subscriptionToken()).toBe('tokenStoredByTheServer');
    });

    it('should keep the replacement running until the token is read again after a failure that may have replaced it', async () => {
        await Promise.resolve();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
        const pendingRead = new Subject<string>();
        calendarApiService.getCalendarEventSubscriptionToken.mockReturnValue(pendingRead);
        const failures: unknown[] = [];
        service.rotateSubscriptionToken().subscribe({ error: (error) => failures.push(error) });

        // the token is unknown and the replacement has not ended, so no other replacement can start
        expect(service.subscriptionToken()).toBeUndefined();
        expect(service.subscriptionTokenRotating()).toBe(true);
        expect(failures).toHaveLength(0);
        await firstValueFrom(service.rotateSubscriptionToken(), { defaultValue: undefined });
        expect(calendarApiService.rotateCalendarEventSubscriptionToken).toHaveBeenCalledOnce();

        pendingRead.next('tokenStoredByTheServer');
        pendingRead.complete();

        expect(service.subscriptionToken()).toBe('tokenStoredByTheServer');
        expect(service.subscriptionTokenRotating()).toBe(false);
        expect(failures).toHaveLength(1);
        expect(failures[0]).toBeInstanceOf(HttpErrorResponse);

        // the next replacement runs and is the one that sets the token
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(of('nextToken'));
        await firstValueFrom(service.rotateSubscriptionToken());
        expect(service.subscriptionToken()).toBe('nextToken');
    });

    it('should report the failed replacement and offer a retry when the token cannot be read again either', async () => {
        await Promise.resolve();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
        calendarApiService.getCalendarEventSubscriptionToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

        await expect(firstValueFrom(service.rotateSubscriptionToken())).rejects.toBeInstanceOf(HttpErrorResponse);

        expect(service.subscriptionToken()).toBeUndefined();
        expect(service.subscriptionTokenLoadFailed()).toBe(true);
        expect(service.subscriptionTokenRotating()).toBe(false);

        calendarApiService.getCalendarEventSubscriptionToken.mockReturnValue(of('tokenAfterRetry'));
        service.reloadSubscriptionToken();

        expect(service.subscriptionToken()).toBe('tokenAfterRetry');
        expect(service.subscriptionTokenLoadFailed()).toBe(false);
    });

    it('should not read the token again while it is known', async () => {
        await Promise.resolve();
        const reads = calendarApiService.getCalendarEventSubscriptionToken.mock.calls.length;

        service.reloadSubscriptionToken();

        expect(calendarApiService.getCalendarEventSubscriptionToken).toHaveBeenCalledTimes(reads);
    });

    it('should keep the token after a rejection that cannot have replaced it', async () => {
        await Promise.resolve();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

        await expect(firstValueFrom(service.rotateSubscriptionToken())).rejects.toBeInstanceOf(HttpErrorResponse);

        expect(calendarApiService.getCalendarEventSubscriptionToken).toHaveBeenCalledOnce();
        expect(service.subscriptionToken()).toBe(testToken);
    });

    it('should not show a second alert for a failure that the global handler shows', async () => {
        await Promise.resolve();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
        const alertService = TestBed.inject(AlertService);
        vi.spyOn(alertService, 'showsHttpErrorAlert').mockReturnValue(true);
        const addErrorAlert = vi.spyOn(alertService, 'addErrorAlert');

        await expect(firstValueFrom(service.rotateSubscriptionToken())).rejects.toBeInstanceOf(HttpErrorResponse);

        expect(addErrorAlert).not.toHaveBeenCalled();
    });

    it('should run only one token rotation at a time', async () => {
        await Promise.resolve();
        const first = new Subject<string>();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(first);
        const firstDone = firstValueFrom(service.rotateSubscriptionToken(), { defaultValue: undefined });

        // a second request while the first runs does not call the server and does not change the token
        await firstValueFrom(service.rotateSubscriptionToken(), { defaultValue: undefined });
        expect(calendarApiService.rotateCalendarEventSubscriptionToken).toHaveBeenCalledOnce();

        first.next('firstRotation');
        first.complete();
        await firstDone;
        expect(service.subscriptionToken()).toBe('firstRotation');

        // after the first one finished, the next one runs
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(of('secondRotation'));
        await firstValueFrom(service.rotateSubscriptionToken());
        expect(calendarApiService.rotateCalendarEventSubscriptionToken).toHaveBeenCalledTimes(2);
        expect(service.subscriptionToken()).toBe('secondRotation');
    });

    it('should allow another rotation after a failed one', async () => {
        await Promise.resolve();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
        await expect(firstValueFrom(service.rotateSubscriptionToken())).rejects.toBeInstanceOf(HttpErrorResponse);

        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValue(of('afterFailure'));
        await firstValueFrom(service.rotateSubscriptionToken());

        expect(service.subscriptionToken()).toBe('afterFailure');
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
        rotateCalendarEventSubscriptionToken: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        authState = new BehaviorSubject<User | undefined>({ id: 99 } as User);
        const customAccountService = new MockAccountService();
        customAccountService.userIdentity.set({ id: 99 } as User);
        customAccountService.getAuthenticationState = () => authState.asObservable().pipe(distinctUntilChanged());

        calendarApiService = {
            getCalendarEventsOverlappingMonths: vi.fn(),
            getCalendarEventSubscriptionToken: vi.fn(),
            rotateCalendarEventSubscriptionToken: vi.fn(),
        };

        calendarApiService.getCalendarEventSubscriptionToken.mockReturnValue(of(testToken));

        TestBed.configureTestingModule({
            providers: [
                CalendarService,
                { provide: CalendarApi, useValue: calendarApiService },
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

    it('should let the next user replace the token while a replacement of the previous user is still running', async () => {
        const previousUsersRotation = new Subject<string>();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValueOnce(previousUsersRotation);
        const previousDone = firstValueFrom(scoped.rotateSubscriptionToken(), { defaultValue: undefined });
        expect(scoped.subscriptionTokenRotating()).toBe(true);

        authState.next({ id: 42 } as User);
        expect(scoped.subscriptionTokenRotating()).toBe(false);

        // the replacement of the new user runs, although the one of the previous user has not answered yet
        const currentUsersRotation = new Subject<string>();
        calendarApiService.rotateCalendarEventSubscriptionToken.mockReturnValueOnce(currentUsersRotation);
        const currentDone = firstValueFrom(scoped.rotateSubscriptionToken(), { defaultValue: undefined });
        expect(calendarApiService.rotateCalendarEventSubscriptionToken).toHaveBeenCalledTimes(2);
        expect(scoped.subscriptionTokenRotating()).toBe(true);

        // the end of the previous replacement neither changes the token nor ends the running one
        previousUsersRotation.next('tokenOfThePreviousUser');
        previousUsersRotation.complete();
        await previousDone;
        expect(scoped.subscriptionToken()).not.toBe('tokenOfThePreviousUser');
        expect(scoped.subscriptionTokenRotating()).toBe(true);

        currentUsersRotation.next('tokenOfTheCurrentUser');
        currentUsersRotation.complete();
        await currentDone;
        expect(scoped.subscriptionToken()).toBe('tokenOfTheCurrentUser');
        expect(scoped.subscriptionTokenRotating()).toBe(false);
    });

    it('should not reset state when the same user re-emits', () => {
        authState.next({ id: 99 } as User);
        expect(scoped.subscriptionToken()).toBe(testToken);
    });

    it('should ignore a slow response of a previously requested course after switching courses', async () => {
        const slowResponse = new Subject<Record<string, unknown[]>>();
        const fastResponse = of({ '2025-10-01': [{ type: 'LECTURE', title: 'Current course lecture', startDate: '2025-10-01T08:00:00Z' }] });
        calendarApiService.getCalendarEventsOverlappingMonths.mockReturnValueOnce(slowResponse).mockReturnValueOnce(fastResponse);

        const previousCourseLoad = firstValueFrom(scoped.loadEventsForCurrentMonth(1, date));
        await firstValueFrom(scoped.loadEventsForCurrentMonth(2, date));
        expect(scoped.eventMap().get('2025-10-01')?.[0].title).toBe('Current course lecture');

        slowResponse.next({ '2025-10-01': [{ type: 'LECTURE', title: 'Previous course lecture', startDate: '2025-10-01T08:00:00Z' }] });
        slowResponse.complete();
        await previousCourseLoad;

        expect(
            scoped
                .eventMap()
                .get('2025-10-01')
                ?.map((event) => event.title),
        ).toEqual(['Current course lecture']);
    });

    it('should neither alert nor fail when an outdated request errors after switching courses', async () => {
        const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'addErrorAlert');
        const slowResponse = new Subject<Record<string, unknown[]>>();
        calendarApiService.getCalendarEventsOverlappingMonths.mockReturnValueOnce(slowResponse).mockReturnValueOnce(of({}));

        const errors: unknown[] = [];
        scoped.loadEventsForCurrentMonth(1, date).subscribe({ error: (error) => errors.push(error) });
        await firstValueFrom(scoped.loadEventsForCurrentMonth(2, date));
        slowResponse.error(new Error('previous course failed'));

        expect(alertSpy).not.toHaveBeenCalled();
        expect(errors).toEqual([]);
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
