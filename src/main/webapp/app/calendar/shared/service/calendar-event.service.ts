import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import dayjs, { Dayjs } from 'dayjs/esm';
import timezone from 'dayjs/plugin/timezone';
import { CalendarEvent, CalendarEventDTO } from 'app/calendar/shared/entities/calendar-event.model';
import { CalendarEventFilterOption } from 'app/calendar/shared/util/calendar-util';

dayjs.extend(timezone);

type CalendarEventMapResponse = HttpResponse<Record<string, CalendarEventDTO[]>>;

@Injectable({
    providedIn: 'root',
})
export class CalendarEventService {
    private httpClient = inject(HttpClient);
    private readonly resourceUrl = '/api/core/calendar/courses';

    private currentMonthKey?: string;
    private currentEventMap = signal<Map<string, CalendarEvent[]>>(new Map());
    readonly eventMap = computed(() => this.filterEventMapByOptions(this.currentEventMap(), this.includedEventFilterOptions()));

    eventFilterOptions: CalendarEventFilterOption[] = ['lectureEvents', 'exerciseEvents', 'tutorialEvents', 'examEvents'];
    includedEventFilterOptions = signal<CalendarEventFilterOption[]>(this.eventFilterOptions);

    loadEventsForCurrentMonth(courseId: number, firstDayOfCurrentMonth: Dayjs): Observable<void> {
        const currentMonthKey = firstDayOfCurrentMonth.format('YYYY-MM');
        if (this.currentMonthKey === currentMonthKey) {
            return new Observable<void>((observer) => {
                observer.next();
                observer.complete();
            });
        }

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
                    this.currentMonthKey = currentMonthKey;
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
            result.set(dayKey, dtoList.map(this.createCalendarEvent));
        }
        return result;
    }

    private createCalendarEvent(dto: CalendarEventDTO): CalendarEvent {
        return new CalendarEvent(dto.id, dto.title, dayjs(dto.startDate), dto.endDate ? dayjs(dto.endDate) : undefined, dto.location ?? undefined, dto.facilitator ?? undefined);
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
        if (filterOptions.includes('examEvents') && event.isExamEvent()) {
            return true;
        }
        if (filterOptions.includes('lectureEvents') && event.isLectureEvent()) {
            return true;
        }
        if (filterOptions.includes('tutorialEvents') && event.isTutorialEvent()) {
            return true;
        }
        if (filterOptions.includes('exerciseEvents') && event.isExerciseEvent()) {
            return true;
        }
        return false;
    }
}
