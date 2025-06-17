import { CalendarEvent } from 'app/calendar/entities/calendar-event.model';

export interface PositionInfo {
    top: number;
    height: number;
    left: number;
    width: number;
}

export interface CalendarEventAndPositioning {
    event: CalendarEvent;
    position: PositionInfo;
}
