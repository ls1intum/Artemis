import { OnDestroy, Service, computed, effect, inject, signal } from '@angular/core';
import { EMPTY, Observable, Subscription, catchError, concat, defer, finalize, ignoreElements, map, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import { CalendarEventFilterOption } from 'app/calendar/shared/util/calendar-util';
import { AlertService } from 'app/foundation/service/alert.service';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { CalendarApi } from 'app/openapi/api/calendar-api';
import { CalendarEvent } from 'app/openapi/model/calendar-event';
import { IdentifiableCalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

@Service()
export class CalendarService implements OnDestroy {
    private readonly apiService = inject(CalendarApi);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly accountService = inject(AccountService);

    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private currentCourseId?: number;
    private firstDayOfCurrentMonth?: Dayjs;
    private currentSubscriptionToken = signal<string | undefined>(undefined);
    private currentEventMap = signal<Map<string, IdentifiableCalendarEvent[]>>(new Map());
    /** The token replacement that is running, which is cleared by the one that owns it and when the user changes. */
    private activeRotation?: object;
    private readonly rotatingSubscriptionToken = signal(false);
    /** Whether a replacement of the subscription token is running, during which the current token must not be offered. */
    readonly subscriptionTokenRotating = this.rotatingSubscriptionToken.asReadonly();
    private readonly subscriptionTokenLoadFailedSignal = signal(false);
    /** Whether the subscription token could not be read, in which case the user can try again. */
    readonly subscriptionTokenLoadFailed = this.subscriptionTokenLoadFailedSignal.asReadonly();

    subscriptionToken = computed<string | undefined>(() => this.currentSubscriptionToken());
    eventMap = computed(() => this.filterEventMapByOptions(this.currentEventMap(), this.includedEventFilterOptions()));
    eventFilterOptions: CalendarEventFilterOption[] = this.buildEventFilterOptions();
    includedEventFilterOptions = signal<CalendarEventFilterOption[]>(this.eventFilterOptions);

    /**
     * Incremented every time {@link resetState} runs. In-flight HTTP responses capture the generation
     * at call time and short-circuit if it no longer matches, preventing them from repopulating cleared
     * state with the previous user's data.
     */
    private stateGeneration = 0;

    /**
     * Incremented for every events request. Only the response of the latest request may update the event map, so a slow
     * response for a previous course (or month) never overwrites the events of the course the user is looking at.
     */
    private latestEventsRequest = 0;

    private currentUserId?: number;
    private authenticationStateSubscription: Subscription;

    constructor() {
        const initialUser = this.accountService.userIdentity();
        this.currentUserId = initialUser?.id;
        // Load the subscription token for the initially-known user before subscribing to auth state,
        // so the synchronous replay emission from a BehaviorSubject doesn't trigger a duplicate request.
        if (initialUser) {
            this.loadSubscriptionToken();
        }
        this.authenticationStateSubscription = this.accountService.getAuthenticationState().subscribe((user) => {
            if (this.currentUserId !== user?.id) {
                this.currentUserId = user?.id;
                this.resetState();
                if (user) {
                    this.loadSubscriptionToken();
                }
            }
        });

        effect(() => {
            this.currentLocale();
            this.reloadEvents();
        });
    }

    ngOnDestroy(): void {
        this.authenticationStateSubscription?.unsubscribe();
    }

    /**
     * Clears all calendar state. Called on logout / user change to avoid leaking the previous user's
     * calendar events or subscription token.
     */
    private resetState(): void {
        this.stateGeneration++;
        this.activeRotation = undefined;
        this.rotatingSubscriptionToken.set(false);
        this.currentCourseId = undefined;
        this.firstDayOfCurrentMonth = undefined;
        this.currentSubscriptionToken.set(undefined);
        this.subscriptionTokenLoadFailedSignal.set(false);
        this.currentEventMap.set(new Map());
        this.includedEventFilterOptions.set(this.eventFilterOptions);
    }

    reloadEvents() {
        const currentCourseId = this.currentCourseId;
        const firstDayOfCurrentMonth = this.firstDayOfCurrentMonth;
        if (currentCourseId && firstDayOfCurrentMonth) {
            this.loadEventsForCurrentMonth(currentCourseId, firstDayOfCurrentMonth).subscribe();
        }
    }

    loadEventsForCurrentMonth(courseId: number, firstDayOfCurrentMonth: Dayjs): Observable<void> {
        const currentMonthKey = firstDayOfCurrentMonth.format('YYYY-MM');
        const previousMonthKey = firstDayOfCurrentMonth.subtract(1, 'month').format('YYYY-MM');
        const nextMonthKey = firstDayOfCurrentMonth.add(1, 'month').format('YYYY-MM');
        const monthKeys = [previousMonthKey, currentMonthKey, nextMonthKey];
        const timeZone = dayjs.tz.guess();
        const language = this.currentLocale() === 'de' ? 'GERMAN' : 'ENGLISH';

        const generation = this.stateGeneration;
        const requestId = ++this.latestEventsRequest;
        return this.apiService.getCalendarEventsOverlappingMonths(courseId, monthKeys, timeZone, language).pipe(
            map((res: { [key: string]: CalendarEvent[] }) => {
                if (this.stateGeneration !== generation || this.latestEventsRequest !== requestId) return;
                const parsed = this.createCalendarEventMap(res);
                this.currentEventMap.set(parsed);
                this.currentCourseId = courseId;
                this.firstDayOfCurrentMonth = firstDayOfCurrentMonth;
            }),
            catchError((error) => {
                if (this.stateGeneration !== generation || this.latestEventsRequest !== requestId) return EMPTY;
                this.alertService.addErrorAlert('artemisApp.calendar.eventsLoadingError');
                return throwError(() => error);
            }),
        );
    }

    toggleEventFilterOption(option: CalendarEventFilterOption): void {
        this.includedEventFilterOptions.update((currentOptions) => {
            if (currentOptions.includes(option)) {
                return currentOptions.filter((currentOption) => currentOption !== option);
            } else {
                return [...currentOptions, option];
            }
        });
    }

    /**
     * Replaces the subscription token with a new one. The subscription links with the old token stop working.
     * A request that arrives while another replacement is running does nothing: the responses of two replacements can arrive in another order than the server stored them,
     * so only one runs at a time.
     *
     * @returns the observable that completes when the new token is in use
     */
    rotateSubscriptionToken(): Observable<void> {
        return defer(() => {
            if (this.activeRotation) {
                return EMPTY;
            }
            const rotation = {};
            this.activeRotation = rotation;
            this.rotatingSubscriptionToken.set(true);
            const generation = this.stateGeneration;
            return this.apiService.rotateCalendarEventSubscriptionToken().pipe(
                map((token: string) => {
                    if (this.stateGeneration !== generation) return;
                    this.currentSubscriptionToken.set(token);
                }),
                catchError((error) => {
                    if (this.stateGeneration !== generation) return EMPTY;
                    // the global handler shows the rejections it knows, and showing the failure twice does not help the user
                    if (!this.alertService.showsHttpErrorAlert(error)) {
                        this.alertService.addErrorAlert('artemisApp.calendar.tokenRotationError');
                    }
                    if (this.mayHaveReplacedTheToken(error)) {
                        // The server may have replaced the token without the answer arriving, so the cached one is unknown until it is read again. The reading belongs to this
                        // operation, so no other replacement can start before it ends and its result cannot overwrite a later token.
                        this.currentSubscriptionToken.set(undefined);
                        return concat(
                            this.readSubscriptionToken().pipe(
                                catchError(() => EMPTY),
                                ignoreElements(),
                            ),
                            throwError(() => error),
                        );
                    }
                    return throwError(() => error);
                }),
                finalize(() => {
                    // a replacement of the previous user may end after the one of the current user started, and must not end that one
                    if (this.activeRotation === rotation) {
                        this.activeRotation = undefined;
                        this.rotatingSubscriptionToken.set(false);
                    }
                }),
            );
        });
    }

    /**
     * Whether a failed replacement may still have changed the token on the server. A rejection with a client error status did not, while a lost connection or a server error can
     * follow a change that was stored.
     */
    private mayHaveReplacedTheToken(error: unknown): boolean {
        return !(error instanceof HttpErrorResponse) || error.status === 0 || error.status >= 500;
    }

    /**
     * Reads the subscription token again, which is the action of the user when it could not be loaded.
     */
    reloadSubscriptionToken(): void {
        if (this.currentSubscriptionToken() === undefined && !this.activeRotation) {
            this.loadSubscriptionToken();
        }
    }

    private loadSubscriptionToken() {
        this.readSubscriptionToken().subscribe();
    }

    private readSubscriptionToken(): Observable<void> {
        const generation = this.stateGeneration;
        this.subscriptionTokenLoadFailedSignal.set(false);
        return this.apiService.getCalendarEventSubscriptionToken().pipe(
            map((token: string) => {
                if (this.stateGeneration !== generation) return;
                this.currentSubscriptionToken.set(token);
            }),
            catchError((error) => {
                if (this.stateGeneration !== generation) return throwError(() => error);
                this.subscriptionTokenLoadFailedSignal.set(true);
                this.alertService.addErrorAlert('artemisApp.calendar.tokenLoadingError');
                return throwError(() => error);
            }),
        );
    }

    private buildEventFilterOptions(): CalendarEventFilterOption[] {
        return [CalendarEventFilterOption.LectureEvents, CalendarEventFilterOption.ExerciseEvents, CalendarEventFilterOption.TutorialEvents, CalendarEventFilterOption.ExamEvents];
    }

    private createCalendarEventMap(dtoMap: Record<string, CalendarEvent[]>): Map<string, IdentifiableCalendarEvent[]> {
        const result = new Map<string, IdentifiableCalendarEvent[]>();
        for (const [dayKey, dtoList] of Object.entries(dtoMap)) {
            result.set(
                dayKey,
                dtoList.map(
                    (dto) => new IdentifiableCalendarEvent(dto.type, dto.title, dayjs(dto.startDate), dto.endDate ? dayjs(dto.endDate) : undefined, dto.location, dto.facilitator),
                ),
            );
        }
        return result;
    }

    private filterEventMapByOptions(eventMap: Map<string, IdentifiableCalendarEvent[]>, filterOptions: CalendarEventFilterOption[]): Map<string, IdentifiableCalendarEvent[]> {
        const filteredMap = new Map<string, IdentifiableCalendarEvent[]>();

        for (const [day, events] of eventMap) {
            const filteredEvents = events.filter((event) => this.eventMatchesFilter(event, filterOptions));
            if (filteredEvents.length > 0) {
                filteredMap.set(day, filteredEvents);
            }
        }

        return filteredMap;
    }

    private eventMatchesFilter(event: IdentifiableCalendarEvent, filterOptions: CalendarEventFilterOption[]): boolean {
        if (filterOptions.includes(CalendarEventFilterOption.ExamEvents) && event.isOfType('EXAM')) {
            return true;
        }
        if (filterOptions.includes(CalendarEventFilterOption.LectureEvents) && event.isOfType('LECTURE')) {
            return true;
        }
        if (filterOptions.includes(CalendarEventFilterOption.TutorialEvents) && event.isOfType('TUTORIAL')) {
            return true;
        }
        if (filterOptions.includes(CalendarEventFilterOption.ExerciseEvents) && event.isOfExerciseType()) {
            return true;
        }
        return false;
    }
}
