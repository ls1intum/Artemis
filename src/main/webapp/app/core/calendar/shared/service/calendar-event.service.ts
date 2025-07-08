import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import dayjs, { Dayjs } from 'dayjs/esm';
import timezone from 'dayjs/plugin/timezone';
import { CalendarEvent, CalendarEventDTO, CalendarEventSubtype, CalendarEventType } from 'app/core/calendar/shared/entities/calendar-event.model';
import { CalendarEventFilterOption } from 'app/core/calendar/shared/util/calendar-util';

dayjs.extend(timezone);

type CalendarEventMapResponse = HttpResponse<Record<string, CalendarEventDTO[]>>;

@Injectable({
    providedIn: 'root',
})
export class CalendarEventService {
    private httpClient = inject(HttpClient);
    private readonly resourceUrl = '/api/core/calendar/courses';

    private currentCourseId?: number;
    private firstDayOfCurrentMonth?: Dayjs;
    private currentEventMap = signal<Map<string, CalendarEvent[]>>(new Map());
    readonly eventMap = computed(() => this.filterEventMapByOptions(this.currentEventMap(), this.includedEventFilterOptions()));

    eventFilterOptions: CalendarEventFilterOption[] = ['lectureEvents', 'exerciseEvents', 'tutorialEvents', 'examEvents'];
    includedEventFilterOptions = signal<CalendarEventFilterOption[]>(this.eventFilterOptions);

    refresh() {
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
        const monthKeys = `${previousMonthKey},${currentMonthKey},${nextMonthKey}`;
        const timeZone = dayjs.tz.guess();
        const parameters = new HttpParams().set('monthKeys', monthKeys).set('timeZone', timeZone);

        return this.httpClient
            .get<Record<string, CalendarEventDTO[]>>(`${this.resourceUrl}/${courseId}/calendar-events`, {
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
        const subtype = this.createCalendarEventSubtype(dto.subtype);

        if (!type || !subtype) {
            return undefined;
        }

        return new CalendarEvent(type, subtype, dto.title, dayjs(dto.startDate), dto.endDate ? dayjs(dto.endDate) : undefined, dto.location, dto.facilitator);
    }

    private createCalendarEventType(value: string): CalendarEventType | undefined {
        return Object.values(CalendarEventType).includes(value as CalendarEventType) ? (value as CalendarEventType) : undefined;
    }

    private createCalendarEventSubtype(value: string): CalendarEventSubtype | undefined {
        return Object.values(CalendarEventSubtype).includes(value as CalendarEventSubtype) ? (value as CalendarEventSubtype) : undefined;
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
        if (filterOptions.includes('examEvents') && event.isOfType(CalendarEventType.Exam)) {
            return true;
        }
        if (filterOptions.includes('lectureEvents') && event.isOfType(CalendarEventType.Lecture)) {
            return true;
        }
        if (filterOptions.includes('tutorialEvents') && event.isOfType(CalendarEventType.Tutorial)) {
            return true;
        }
        if (filterOptions.includes('exerciseEvents') && event.isOfExerciseType()) {
            return true;
        }
        return false;
    }
}
