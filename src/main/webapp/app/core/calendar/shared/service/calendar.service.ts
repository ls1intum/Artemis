import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, catchError, distinctUntilChanged, map, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import timezone from 'dayjs/esm/plugin/timezone';
import { CalendarEvent, CalendarEventDTO, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';
import { AlertService } from 'app/shared/service/alert.service';

dayjs.extend(timezone);

type CalendarEventMapResponse = HttpResponse<Record<string, CalendarEventDTO[]>>;

@Injectable({
    providedIn: 'root',
})
export class CalendarService {
    private httpClient = inject(HttpClient);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private readonly resourceUrl = '/api/core/calendar';

    private currentLanguage = toSignal(
        this.translateService.onLangChange.pipe(
            map((event) => (event.lang === 'de' ? 'GERMAN' : 'ENGLISH')),
            distinctUntilChanged(),
        ),
        {
            initialValue: this.translateService.currentLang === 'de' ? 'GERMAN' : 'ENGLISH',
        },
    );
    private currentCourseId?: number;
    private firstDayOfCurrentMonth?: Dayjs;
    private currentSubscriptionToken = signal<string | undefined>(undefined);
    private currentEventMap = signal<Map<string, CalendarEvent[]>>(new Map());
    readonly subscriptionToken = computed<string | undefined>(() => this.currentSubscriptionToken());
    readonly eventMap = computed(() => this.filterEventMapByOptions(this.currentEventMap(), this.includedEventFilterOptions()));

    eventFilterOptions: CalendarEventFilterOption[] = [
        CalendarEventFilterOption.LectureEvents,
        CalendarEventFilterOption.ExerciseEvents,
        CalendarEventFilterOption.TutorialEvents,
        CalendarEventFilterOption.ExamEvents,
    ];
    includedEventFilterOptions = signal<CalendarEventFilterOption[]>(this.eventFilterOptions);

    constructor() {
        effect(() => {
            this.currentLanguage();
            this.reloadEvents();
        });
    }

    reloadEvents() {
        const currentCourseId = this.currentCourseId;
        const firstDayOfCurrentMonth = this.firstDayOfCurrentMonth;
        if (currentCourseId && firstDayOfCurrentMonth) {
            this.loadEventsForCurrentMonth(currentCourseId, firstDayOfCurrentMonth).subscribe();
        }
    }

    loadSubscriptionToken(): Observable<void> {
        return this.httpClient
            .get(`${this.resourceUrl}/subscription-token`, {
                responseType: 'text',
            })
            .pipe(
                map((token: string) => {
                    this.currentSubscriptionToken.set(token);
                }),
                catchError((error) => {
                    this.alertService.addErrorAlert('artemisApp.calendar.tokenLoadingError');
                    return throwError(() => error);
                }),
            );
    }

    loadEventsForCurrentMonth(courseId: number, firstDayOfCurrentMonth: Dayjs): Observable<void> {
        const currentMonthKey = firstDayOfCurrentMonth.format('YYYY-MM');
        const previousMonthKey = firstDayOfCurrentMonth.subtract(1, 'month').format('YYYY-MM');
        const nextMonthKey = firstDayOfCurrentMonth.add(1, 'month').format('YYYY-MM');
        const monthKeys = `${previousMonthKey},${currentMonthKey},${nextMonthKey}`;
        const timeZone = dayjs.tz.guess();
        const language = this.currentLanguage() ?? 'ENGLISH';
        const parameters = new HttpParams().set('monthKeys', monthKeys).set('timeZone', timeZone).set('language', language);

        return this.httpClient
            .get<Record<string, CalendarEventDTO[]>>(`${this.resourceUrl}/courses/${courseId}/calendar-events`, {
                params: parameters,
                observe: 'response',
            })
            .pipe(
                map((res: CalendarEventMapResponse) => {
                    const parsed = this.createCalendarEventMap(res.body ?? {});
                    this.currentEventMap.set(parsed);
                    this.currentCourseId = courseId;
                    this.firstDayOfCurrentMonth = firstDayOfCurrentMonth;
                }),
                catchError((error) => {
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

    private createCalendarEventMap(dtoMap: Record<string, CalendarEventDTO[]>): Map<string, CalendarEvent[]> {
        const result = new Map<string, CalendarEvent[]>();
        for (const [dayKey, dtoList] of Object.entries(dtoMap)) {
            result.set(
                dayKey,
                dtoList.map((dto) => this.createCalendarEvent(dto)).filter((element): element is CalendarEvent => element !== undefined),
            );
        }
        return result;
    }

    private createCalendarEvent(dto: CalendarEventDTO): CalendarEvent | undefined {
        const type = this.createCalendarEventType(dto.type);

        if (!type) {
            return undefined;
        }

        return new CalendarEvent(type, dto.title, dayjs(dto.startDate), dto.endDate ? dayjs(dto.endDate) : undefined, dto.location, dto.facilitator);
    }

    private createCalendarEventType(value: string): CalendarEventType | undefined {
        return Object.values(CalendarEventType).includes(value as CalendarEventType) ? (value as CalendarEventType) : undefined;
    }

    private filterEventMapByOptions(eventMap: Map<string, CalendarEvent[]>, filterOptions: CalendarEventFilterOption[]): Map<string, CalendarEvent[]> {
        const filteredMap = new Map<string, CalendarEvent[]>();

        for (const [day, events] of eventMap) {
            const filteredEvents = events.filter((event) => this.eventMatchesFilter(event, filterOptions));
            if (filteredEvents.length > 0) {
                filteredMap.set(day, filteredEvents);
            }
        }

        return filteredMap;
    }

    private eventMatchesFilter(event: CalendarEvent, filterOptions: CalendarEventFilterOption[]): boolean {
        if (filterOptions.includes(CalendarEventFilterOption.ExamEvents) && event.isOfType(CalendarEventType.Exam)) {
            return true;
        }
        if (filterOptions.includes(CalendarEventFilterOption.LectureEvents) && event.isOfType(CalendarEventType.Lecture)) {
            return true;
        }
        if (filterOptions.includes(CalendarEventFilterOption.TutorialEvents) && event.isOfType(CalendarEventType.Tutorial)) {
            return true;
        }
        if (filterOptions.includes(CalendarEventFilterOption.ExerciseEvents) && event.isOfExerciseType()) {
            return true;
        }
        return false;
    }
}
