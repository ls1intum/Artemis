import { Injectable, signal } from '@angular/core';
import { CalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

@Injectable()
export class MockCalendarEventService {
    eventMap;

    constructor(eventMap: Map<string, CalendarEvent[]>) {
        this.eventMap = signal(eventMap);
    }
}
