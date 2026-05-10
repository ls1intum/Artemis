import { Injectable, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { Observable, Subscription, catchError, map, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import { CalendarEventFilterOption } from 'app/calendar/shared/util/calendar-util';
import { AlertService } from 'app/shared/service/alert.service';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { AccountService } from 'app/core/auth/account.service';
import { CalendarApiService } from 'app/openapi/api/calendarApi.service';
import { CalendarEvent } from 'app/openapi/model/calendarEvent';
import { IdentifiableCalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

@Injectable({
    providedIn: 'root',
})
export class CalendarService implements OnDestroy {
    private readonly apiService = inject(CalendarApiService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly accountService = inject(AccountService);

    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private currentCourseId?: number;
    private firstDayOfCurrentMonth?: Dayjs;
    private currentSubscriptionToken = signal<string | undefined>(undefined);
    private currentEventMap = signal<Map<string, IdentifiableCalendarEvent[]>>(new Map());

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
        this.currentCourseId = undefined;
        this.firstDayOfCurrentMonth = undefined;
        this.currentSubscriptionToken.set(undefined);
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
        return this.apiService.getCalendarEventsOverlappingMonths(courseId, monthKeys, timeZone, language).pipe(
            map((res: { [key: string]: CalendarEvent[] }) => {
                if (this.stateGeneration !== generation) return;
                const parsed = this.createCalendarEventMap(res);
                this.currentEventMap.set(parsed);
                this.currentCourseId = courseId;
                this.firstDayOfCurrentMonth = firstDayOfCurrentMonth;
            }),
            catchError((error) => {
                if (this.stateGeneration !== generation) return throwError(() => error);
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

    private loadSubscriptionToken() {
        const generation = this.stateGeneration;
        this.apiService
            .getCalendarEventSubscriptionToken()
            .pipe(
                map((token: string) => {
                    if (this.stateGeneration !== generation) return;
                    this.currentSubscriptionToken.set(token);
                }),
                catchError((error) => {
                    if (this.stateGeneration !== generation) return throwError(() => error);
                    this.alertService.addErrorAlert('artemisApp.calendar.tokenLoadingError');
                    return throwError(() => error);
                }),
            )
            .subscribe();
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
        if (filterOptions.includes(CalendarEventFilterOption.ExamEvents) && event.isOfType(CalendarEvent.TypeEnum.Exam)) {
            return true;
        }
        if (filterOptions.includes(CalendarEventFilterOption.LectureEvents) && event.isOfType(CalendarEvent.TypeEnum.Lecture)) {
            return true;
        }
        if (filterOptions.includes(CalendarEventFilterOption.TutorialEvents) && event.isOfType(CalendarEvent.TypeEnum.Tutorial)) {
            return true;
        }
        if (filterOptions.includes(CalendarEventFilterOption.ExerciseEvents) && event.isOfExerciseType()) {
            return true;
        }
        return false;
    }
}
