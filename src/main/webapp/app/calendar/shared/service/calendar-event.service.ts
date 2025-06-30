import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpParams, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { CalendarEvent, CalendarEventDTO } from 'app/calendar/shared/entities/calendar-event.model';
import { HttpClient } from '@angular/common/http';
import dayjs, { Dayjs } from 'dayjs/esm';
import timezone from 'dayjs/plugin/timezone';

dayjs.extend(timezone);

type CalendarEventMapResponse = HttpResponse<Record<string, CalendarEventDTO[]>>;

@Injectable({
    providedIn: 'root',
})
export class CalendarEventService {
    private httpClient = inject(HttpClient);

    private currentMonthKey?: string;
    private currentEventMap = signal<Map<string, CalendarEvent[]>>(new Map());

    readonly eventMap = computed(() => this.currentEventMap());

    private readonly resourceUrl = '/api/calendar/courses';

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
        const params = new HttpParams().set('monthKeys', monthKeys).set('timeZone', timeZone);

        return this.httpClient
            .get<Record<string, CalendarEventDTO[]>>(`${this.resourceUrl}/${courseId}/calendar-events`, {
                params,
                observe: 'response',
            })
            .pipe(
                map((res: CalendarEventMapResponse) => {
                    const parsed = this.mapCalendarEventDTOMap(res.body ?? {});
                    this.currentEventMap.set(parsed);
                    this.currentMonthKey = currentMonthKey;
                }),
            );
    }

    private mapCalendarEventDTOMap(dtoMap: Record<string, CalendarEventDTO[]>): Map<string, CalendarEvent[]> {
        const result = new Map<string, CalendarEvent[]>();
        for (const [dayKey, dtoList] of Object.entries(dtoMap)) {
            result.set(dayKey, dtoList.map(this.mapCalendarEventDTO));
        }
        return result;
    }

    private mapCalendarEventDTO(dto: CalendarEventDTO): CalendarEvent {
        return {
            ...dto,
            startDate: dayjs(dto.startDate),
            endDate: dto.endDate ? dayjs(dto.endDate) : undefined,
        };
    }
}
