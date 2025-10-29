import { Injectable, signal } from '@angular/core';
import { CalendarEvent } from 'app/core/calendar/shared/entities/calendar-event.model';

@Injectable()
export class MockCalendarService {
    eventMap;

    constructor(eventMap: Map<string, CalendarEvent[]>) {
        this.eventMap = signal(eventMap);
    }
}
