import { Injectable, signal } from '@angular/core';
import { IdentifiableCalendarEvent } from 'app/calendar/shared/entities/calendar-event.model';

@Injectable()
export class MockCalendarService {
    eventMap;

    constructor(eventMap: Map<string, IdentifiableCalendarEvent[]>) {
        this.eventMap = signal(eventMap);
    }
}
