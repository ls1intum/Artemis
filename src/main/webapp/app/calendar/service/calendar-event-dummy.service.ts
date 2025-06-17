import { Injectable } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import { CalendarEvent } from '../entities/calendar-event.model';

@Injectable({
    providedIn: 'root',
})
export class CalendarEventDummyService {
    private dayToEventMap = new Map<string, CalendarEvent[]>();

    private readonly dummyEvents: CalendarEvent[] = [
        { id: '1', name: 'Meeting', start: dayjs('2025-05-02'), end: dayjs('2025-05-02') },
        { id: '2', name: 'Review', start: dayjs('2025-05-15'), end: dayjs('2025-05-15') },
        { id: '3', name: 'Call', start: dayjs('2025-05-15'), end: dayjs('2025-05-15') },
        { id: '4', name: 'Workshop', start: dayjs('2025-05-15'), end: dayjs('2025-05-15') },
        { id: '5', name: 'Workshop', start: dayjs('2025-05-15'), end: dayjs('2025-05-15') },
        { id: '6', name: 'Workshop', start: dayjs('2025-05-15'), end: dayjs('2025-05-15') },
        { id: '7', name: 'Deadline', start: dayjs('2025-05-26'), end: dayjs('2025-05-26') },
    ];

    constructor() {
        this.populateDayToEventMap();
    }

    private populateDayToEventMap(): void {
        for (const event of this.dummyEvents) {
            const dateKey = event.start.format('YYYY-MM-DD');
            const existing = this.dayToEventMap.get(dateKey);
            if (existing) {
                existing.push(event);
            } else {
                this.dayToEventMap.set(dateKey, [event]);
            }
        }
    }

    getEventsOfDay(day: Dayjs): CalendarEvent[] {
        return this.dayToEventMap.get(day.format('YYYY-MM-DD')) ?? [];
    }
}
