import { Injectable } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

@Injectable({
    providedIn: 'root',
})
export class CalendarEventDummyService {
    private dayToEventMap = new Map<string, CalendarEvent[]>();

    private readonly dummyEvents: CalendarEvent[] = [
        { id: '1', title: 'Meeting', startDate: dayjs('2025-06-02 10:00'), endDate: dayjs('2025-06-02 12:00') },
        { id: '2', title: 'Review', startDate: dayjs('2025-06-15 13:00'), endDate: dayjs('2025-06-15 14:00') },
        { id: '3', title: 'Call', startDate: dayjs('2025-06-15 13:30'), endDate: dayjs('2025-06-15 16:00') },
        { id: '4', title: 'Workshop', startDate: dayjs('2025-06-15 15:00'), endDate: dayjs('2025-06-15 16:00') },
        { id: '5', title: 'Workshop', startDate: dayjs('2025-06-15 14:00'), endDate: undefined },
        { id: '6', title: 'Workshop', startDate: dayjs('2025-06-15 19:00'), endDate: dayjs('2025-06-15 20:00') },
        { id: '7', title: 'Deadline', startDate: dayjs('2025-06-26 11:30'), endDate: dayjs('2025-06-26 13:30') },
    ];

    constructor() {
        this.populateDayToEventMap();
    }

    private populateDayToEventMap(): void {
        for (const event of this.dummyEvents) {
            const dateKey = event.startDate.format('YYYY-MM-DD');
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
